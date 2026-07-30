import { Context, Next } from "hono";
import { logWarn } from "./logger.js";
import { supabase } from "./supabase.js";

// ============================================================
// Rate Limiting Middleware (shared store, per-IP)
// ============================================================
//
// Counters live in Postgres (`rate_limits` + `increment_rate_limit()`) so every
// instance sees the same budget. A process-local Map is kept as an automatic
// fallback: if the database is unreachable we still enforce *something* rather
// than failing open, and we recover without a restart once it comes back.

interface RateLimitEntry {
  count: number;
  resetAt: number;
}

const rateLimitStore = new Map<string, RateLimitEntry>();

// Clean up expired entries every 5 minutes
setInterval(() => {
  const now = Date.now();
  for (const [key, entry] of rateLimitStore) {
    if (entry.resetAt <= now) {
      rateLimitStore.delete(key);
    }
  }
}, 5 * 60 * 1000);

interface RateLimitOptions {
  windowMs: number;
  max: number;
  keyPrefix?: string;
}

interface BudgetResult {
  count: number;
  /** Epoch millis. */
  resetAt: number;
  /** False when the shared store was unavailable and the local Map was used. */
  shared: boolean;
}

/**
 * Set to true after a shared-store failure so we stop paying the round trip on
 * every request while the database is down. Cleared by [SHARED_RETRY_MS].
 */
let sharedStoreDownUntil = 0;
const SHARED_RETRY_MS = 30_000;

function consumeLocal(key: string, windowMs: number): BudgetResult {
  const now = Date.now();
  let entry = rateLimitStore.get(key);

  if (!entry || entry.resetAt <= now) {
    entry = { count: 0, resetAt: now + windowMs };
    rateLimitStore.set(key, entry);
  }

  entry.count++;
  return { count: entry.count, resetAt: entry.resetAt, shared: false };
}

async function consumeBudget(key: string, windowMs: number): Promise<BudgetResult> {
  if (process.env.RATE_LIMIT_STORE === "memory") {
    return consumeLocal(key, windowMs);
  }

  if (Date.now() < sharedStoreDownUntil) {
    return consumeLocal(key, windowMs);
  }

  try {
    const { data, error } = await supabase.rpc("increment_rate_limit", {
      p_bucket_key: key,
      p_window_ms: windowMs,
    });

    if (error) throw new Error(error.message);

    // The RPC returns a single row; supabase-js gives an array for TABLE returns.
    const row = Array.isArray(data) ? data[0] : data;
    if (!row) throw new Error("increment_rate_limit returned no row");

    return {
      count: Number(row.request_count),
      resetAt: new Date(row.reset_at).getTime(),
      shared: true,
    };
  } catch (err) {
    sharedStoreDownUntil = Date.now() + SHARED_RETRY_MS;
    logWarn("rate_limit_store_unavailable", {
      error: err instanceof Error ? err.message : String(err),
      fallback: "in-memory",
    });
    return consumeLocal(key, windowMs);
  }
}

export function rateLimit(options: RateLimitOptions) {
  const { windowMs, max, keyPrefix = "global" } = options;

  return async (c: Context, next: Next) => {
    const ip =
      c.req.header("x-forwarded-for")?.split(",")[0]?.trim() ??
      c.req.header("x-real-ip") ??
      "unknown";

    const key = `${keyPrefix}:${ip}`;
    const { count, resetAt, shared } = await consumeBudget(key, windowMs);

    c.header("X-RateLimit-Limit", String(max));
    c.header("X-RateLimit-Remaining", String(Math.max(0, max - count)));
    c.header("X-RateLimit-Reset", String(Math.ceil(resetAt / 1000)));

    if (count > max) {
      logWarn("rate_limit_exceeded", { ip, keyPrefix, count, max, shared }, c);
      return c.json(
        { error: "Too many requests. Please try again later." },
        429
      );
    }

    await next();
  };
}

// ============================================================
// CORS Middleware
// ============================================================

const ALLOWED_ORIGINS = new Set([
  "https://lastlogged.com",
  "https://www.lastlogged.com",
]);

export async function corsMiddleware(c: Context, next: Next) {
  const origin = c.req.header("origin") ?? "";

  // Allow requests with no origin (mobile apps, server-to-server)
  if (origin && !ALLOWED_ORIGINS.has(origin)) {
    // Still process the request (mobile apps don't send Origin),
    // but don't set CORS headers for unknown browser origins
    await next();
    return;
  }

  if (origin) {
    c.header("Access-Control-Allow-Origin", origin);
    c.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    c.header("Access-Control-Allow-Headers", "Authorization, Content-Type");
    c.header("Access-Control-Max-Age", "86400");
  }

  if (c.req.method === "OPTIONS") {
    return new Response(null, { status: 204, headers: c.res.headers });
  }

  await next();
}

// ============================================================
// Body Size Limit Middleware
// ============================================================

export function bodyLimit(maxBytes: number) {
  return async (c: Context, next: Next) => {
    const contentLength = c.req.header("content-length");
    if (contentLength && parseInt(contentLength, 10) > maxBytes) {
      return c.json({ error: "Request body too large" }, 413);
    }
    await next();
  };
}

// ============================================================
// Request ID & Structured Logging Middleware
// ============================================================

let requestCounter = 0;

export async function requestLogger(c: Context, next: Next) {
  const requestId = `req_${Date.now()}_${++requestCounter}`;
  c.set("requestId", requestId);
  c.header("X-Request-Id", requestId);

  const start = Date.now();
  const method = c.req.method;
  const path = new URL(c.req.url).pathname;

  await next();

  const duration = Date.now() - start;
  const status = c.res.status;
  console.log(
    JSON.stringify({
      timestamp: new Date().toISOString(),
      level: "info",
      event: "http_request",
      requestId,
      method,
      path,
      status,
      durationMs: duration,
    })
  );
}

// ============================================================
// Cron Secret Authentication (for manual digest triggers)
// ============================================================

export function cronAuth() {
  return async (c: Context, next: Next) => {
    const cronSecret = process.env.CRON_SECRET;
    if (!cronSecret) {
      // If no cron secret configured, block manual triggers
      return c.json({ error: "Manual trigger not configured" }, 403);
    }

    const authHeader = c.req.header("Authorization") ?? "";
    if (authHeader !== `Bearer ${cronSecret}`) {
      return c.json({ error: "Unauthorized" }, 401);
    }

    await next();
  };
}
