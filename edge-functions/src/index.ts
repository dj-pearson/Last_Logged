import { Hono } from "hono";
import { serve } from "@hono/node-server";
import cron from "node-cron";
import { reminderDigest, sendReminderDigests } from "./reminder-digest.js";
import { revenuecatWebhook } from "./revenuecat-webhook.js";
import { exportData } from "./export-data.js";

const app = new Hono();

// Health check
app.get("/health", (c) => {
  return c.json({ status: "ok", timestamp: new Date().toISOString() });
});

// Reminder digest routes
app.route("/", reminderDigest);

// RevenueCat subscription webhook
app.route("/", revenuecatWebhook);

// Data export
app.route("/", exportData);

// Schedule daily reminder digest at 8:00 AM
cron.schedule("0 8 * * *", async () => {
  console.log(`[${new Date().toISOString()}] Running scheduled reminder digest...`);
  try {
    const stats = await sendReminderDigests();
    console.log(`[${new Date().toISOString()}] Digest complete:`, stats);
  } catch (err) {
    console.error(`[${new Date().toISOString()}] Digest failed:`, err);
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
