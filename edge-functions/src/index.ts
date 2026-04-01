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

// Validate environment before starting
validateRequiredEnv();

const app = new Hono();

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

// Health check (no auth required, lightweight)
app.get("/health", (c) => {
  return c.json({ status: "ok" });
});

// Reminder digest routes (manual trigger requires cron secret)
reminderDigest.use("/send-reminder-digest", cronAuth());
app.route("/", reminderDigest);

// RevenueCat subscription webhook
app.route("/", revenuecatWebhook);

// Data export
app.route("/", exportData);

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

serve({
  fetch: app.fetch,
  port,
});

export default app;
