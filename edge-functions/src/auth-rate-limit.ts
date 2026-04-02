import { Hono } from "hono";
import { supabase } from "./supabase.js";
import { log, logWarn } from "./logger.js";

export const authRateLimit = new Hono();

const WINDOW_MS = 15 * 60 * 1000; // 15 minutes
const MAX_ATTEMPTS = 10;
const LOCKOUT_MS = 5 * 60 * 1000; // 5 minutes

interface RateLimitRow {
  ip_address: string;
  attempt_count: number;
  window_start: string;
  locked_until: string | null;
}

authRateLimit.post("/auth/check-rate-limit", async (c) => {
  const ip =
    c.req.header("x-forwarded-for")?.split(",")[0]?.trim() ??
    c.req.header("x-real-ip") ??
    "unknown";

  const now = new Date();

  // Look up existing entry
  const { data: existing } = await supabase
    .from("auth_rate_limits")
    .select("*")
    .eq("ip_address", ip)
    .single();

  const row = existing as RateLimitRow | null;

  // Check if currently locked out
  if (row?.locked_until) {
    const lockedUntil = new Date(row.locked_until);
    if (lockedUntil > now) {
      const retryAfter = Math.ceil(
        (lockedUntil.getTime() - now.getTime()) / 1000
      );
      logWarn("auth_rate_limit_locked", { ip, lockedUntil: row.locked_until }, c);
      c.header("X-Auth-RateLimit-Remaining", "0");
      c.header("X-Auth-RateLimit-Reset", String(Math.ceil(lockedUntil.getTime() / 1000)));
      return c.json(
        { error: "Too many authentication attempts. Please try again later.", retryAfter },
        429
      );
    }
    // Lockout expired — reset
  }

  // Check if window has expired
  if (row) {
    const windowStart = new Date(row.window_start);
    const windowAge = now.getTime() - windowStart.getTime();

    if (windowAge > WINDOW_MS) {
      // Window expired — reset
      await supabase
        .from("auth_rate_limits")
        .upsert({
          ip_address: ip,
          attempt_count: 1,
          window_start: now.toISOString(),
          locked_until: null,
        })
        .eq("ip_address", ip);

      log("auth_rate_limit_reset", { ip }, c);
      c.header("X-Auth-RateLimit-Remaining", String(MAX_ATTEMPTS - 1));
      c.header("X-Auth-RateLimit-Reset", String(Math.ceil((now.getTime() + WINDOW_MS) / 1000)));
      return c.json({ allowed: true, remaining: MAX_ATTEMPTS - 1 });
    }

    // Within window — increment
    const newCount = row.attempt_count + 1;

    if (newCount > MAX_ATTEMPTS) {
      // Lock out
      const lockedUntil = new Date(now.getTime() + LOCKOUT_MS);
      await supabase
        .from("auth_rate_limits")
        .update({
          attempt_count: newCount,
          locked_until: lockedUntil.toISOString(),
        })
        .eq("ip_address", ip);

      logWarn("auth_rate_limit_exceeded", { ip, attempts: newCount }, c);
      c.header("X-Auth-RateLimit-Remaining", "0");
      c.header("X-Auth-RateLimit-Reset", String(Math.ceil(lockedUntil.getTime() / 1000)));
      return c.json(
        { error: "Too many authentication attempts. Please try again later.", retryAfter: LOCKOUT_MS / 1000 },
        429
      );
    }

    // Allowed — increment count
    await supabase
      .from("auth_rate_limits")
      .update({ attempt_count: newCount })
      .eq("ip_address", ip);

    const remaining = MAX_ATTEMPTS - newCount;
    c.header("X-Auth-RateLimit-Remaining", String(remaining));
    c.header("X-Auth-RateLimit-Reset", String(Math.ceil((windowStart.getTime() + WINDOW_MS) / 1000)));
    return c.json({ allowed: true, remaining });
  }

  // No existing entry — create new
  await supabase.from("auth_rate_limits").upsert({
    ip_address: ip,
    attempt_count: 1,
    window_start: now.toISOString(),
    locked_until: null,
  });

  log("auth_rate_limit_new", { ip }, c);
  c.header("X-Auth-RateLimit-Remaining", String(MAX_ATTEMPTS - 1));
  c.header("X-Auth-RateLimit-Reset", String(Math.ceil((now.getTime() + WINDOW_MS) / 1000)));
  return c.json({ allowed: true, remaining: MAX_ATTEMPTS - 1 });
});
