import { Hono } from "hono";
import { serve } from "@hono/node-server";
import cron from "node-cron";
import { validateRequiredEnv } from "./validate-env.js";
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
// Digest trigger: very strict — only cron/admin
app.use(
  "/send-reminder-digest",
  rateLimit({ windowMs: 60_000, max: 2, keyPrefix: "digest" })
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

// Schedule daily reminder digest at 8:00 AM
cron.schedule("0 8 * * *", async () => {
  console.log(
    JSON.stringify({
      event: "cron_digest_start",
      timestamp: new Date().toISOString(),
    })
  );
  try {
    const stats = await sendReminderDigests();
    console.log(
      JSON.stringify({
        event: "cron_digest_complete",
        ...stats,
        timestamp: new Date().toISOString(),
      })
    );
  } catch (err) {
    console.error(
      JSON.stringify({
        event: "cron_digest_error",
        error: String(err),
        timestamp: new Date().toISOString(),
      })
    );
  }
});

const port = parseInt(process.env.PORT ?? "3000", 10);

console.log(`Last Logged edge functions starting on port ${port}...`);
console.log("Reminder digest cron scheduled for daily 8:00 AM");

serve({
  fetch: app.fetch,
  port,
});

export default app;
