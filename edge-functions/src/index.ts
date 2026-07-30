import { Hono } from "hono";
import { serve } from "@hono/node-server";
import cron from "node-cron";
import { validateRequiredEnv } from "./validate-env.js";
import { log, logError } from "./logger.js";
import {
  rateLimit,
  corsMiddleware,
  bodyLimit,
  requestLogger,
  cronAuth,
} from "./middleware.js";
import { reminderDigest, sendReminderDigests } from "./reminder-digest.js";
import { revenuecatWebhook } from "./revenuecat-webhook.js";
import { exportData } from "./export-data.js";
import { cleanup, runCleanup } from "./cleanup.js";
import { deleteAccount } from "./delete-account.js";
import { authRateLimit } from "./auth-rate-limit.js";
import { initCrashReporting, captureError } from "./crash-reporting.js";
import { supabase } from "./supabase.js";

// Surfaced by /health so a deploy can be identified without shell access.
const APP_VERSION = process.env.APP_VERSION ?? "dev";

// First statement in the module body: ES imports are hoisted, so this is the
// earliest point at which anything can run, and it must precede
// validateRequiredEnv() (which exits the process on a bad config).
initCrashReporting();

// Validate environment before starting
validateRequiredEnv();

const app = new Hono();

// Any exception a route does not handle lands here. Without this Hono returns
// a bare 500 and the failure is invisible.
app.onError((err, c) => {
  captureError(err, `${c.req.method} ${new URL(c.req.url).pathname}`);
  return c.json({ error: "Internal server error" }, 500);
});

// ---- Global Middleware ----
app.use("*", requestLogger);
app.use("*", corsMiddleware);
app.use("*", bodyLimit(1_048_576)); // 1 MB max body

// ---- Rate Limits (per-route) ----
// Webhook: generous limit for RevenueCat retries
app.use(
  "/webhooks/*",
  rateLimit({ windowMs: 60_000, max: 60, keyPrefix: "webhook" })
);
// Export: stricter — authenticated, expensive query
app.use(
  "/export-data",
  rateLimit({ windowMs: 60_000, max: 10, keyPrefix: "export" })
);
// Digest/cleanup trigger: very strict — only cron/admin
app.use(
  "/send-reminder-digest",
  rateLimit({ windowMs: 60_000, max: 2, keyPrefix: "digest" })
);
app.use(
  "/cleanup-old-data",
  rateLimit({ windowMs: 60_000, max: 2, keyPrefix: "cleanup" })
);
// Default for everything else
app.use(
  "*",
  rateLimit({ windowMs: 15 * 60_000, max: 100, keyPrefix: "global" })
);

// Health check. Verifies Supabase connectivity rather than always reporting
// "ok" — a static 200 tells a load balancer nothing and kept a broken instance
// in rotation.
const startedAt = Date.now();

app.get("/health", async (c) => {
  const checks: Record<string, string> = {};
  let healthy = true;

  try {
    const { error } = await supabase
      .from("users")
      .select("id", { count: "exact", head: true })
      .limit(1);

    if (error) throw new Error(error.message);
    checks.database = "ok";
  } catch (err) {
    healthy = false;
    checks.database = err instanceof Error ? err.message : String(err);
    logError("health_check_failed", err);
  }

  const body = {
    status: healthy ? "ok" : "degraded",
    version: APP_VERSION,
    uptimeSeconds: Math.floor((Date.now() - startedAt) / 1000),
    checks,
  };

  return c.json(body, healthy ? 200 : 503);
});

// Liveness only — for platforms that need a probe which never touches the DB.
app.get("/health/live", (c) => c.json({ status: "ok" }));

// Reminder digest routes (manual trigger requires cron secret)
reminderDigest.use("/send-reminder-digest", cronAuth());
app.route("/", reminderDigest);

// RevenueCat subscription webhook
app.route("/", revenuecatWebhook);

// Data export
app.route("/", exportData);

// Auth rate limiting
app.use(
  "/auth/*",
  rateLimit({ windowMs: 60_000, max: 30, keyPrefix: "auth" })
);
app.route("/", authRateLimit);

// Account deletion
app.use(
  "/delete-account",
  rateLimit({ windowMs: 60_000, max: 3, keyPrefix: "delete-account" })
);
app.route("/", deleteAccount);

// Data cleanup (manual trigger requires cron secret)
cleanup.use("/cleanup-old-data", cronAuth());
app.route("/", cleanup);

// Schedule daily reminder digest at 8:00 AM
cron.schedule("0 8 * * *", async () => {
  log("cron_digest_start");
  try {
    const stats = await sendReminderDigests();
    log("cron_digest_complete", stats);
  } catch (err) {
    logError("cron_digest_error", err);
  }
});

const port = parseInt(process.env.PORT ?? "3000", 10);

// Schedule weekly cleanup: Sunday 3:00 AM
cron.schedule("0 3 * * 0", async () => {
  log("cron_cleanup_start");
  try {
    const stats = await runCleanup();
    log("cron_cleanup_complete", stats);
  } catch (err) {
    logError("cron_cleanup_error", err);
  }
});

log("server_starting", { port });
log("cron_scheduled", { schedule: "0 8 * * *", job: "reminder_digest" });
log("cron_scheduled", { schedule: "0 3 * * 0", job: "data_cleanup" });

// A rejected promise outside a request would otherwise terminate the process
// silently under Node's default behaviour.
process.on("unhandledRejection", (reason) => {
  captureError(reason, "unhandledRejection");
});

process.on("uncaughtException", (error) => {
  captureError(error, "uncaughtException");
});

serve({
  fetch: app.fetch,
  port,
});

export default app;
