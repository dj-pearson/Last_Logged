import { Hono } from "hono";
import { supabase } from "./supabase.js";
import crypto from "node:crypto";

const REVENUECAT_WEBHOOK_SECRET =
  process.env.REVENUECAT_WEBHOOK_SECRET ?? "your-webhook-secret";

export const revenuecatWebhook = new Hono();

type SubscriptionTier = "free" | "premium" | "lifetime";

interface RevenueCatEvent {
  type: string;
  app_user_id: string;
  product_id?: string;
}

interface RevenueCatWebhookPayload {
  api_version: string;
  event: RevenueCatEvent;
}

function verifySignature(body: string, signature: string): boolean {
  const expected = crypto
    .createHmac("sha256", REVENUECAT_WEBHOOK_SECRET)
    .update(body)
    .digest("hex");
  return crypto.timingSafeEqual(
    Buffer.from(signature),
    Buffer.from(expected)
  );
}

function tierFromEvent(event: RevenueCatEvent): SubscriptionTier {
  switch (event.type) {
    case "INITIAL_PURCHASE":
    case "RENEWAL":
    case "PRODUCT_CHANGE":
    case "UNCANCELLATION":
      return event.product_id === "lifetime" ? "lifetime" : "premium";

    case "CANCELLATION":
    case "EXPIRATION":
      return "free";

    default:
      // Non-subscription events (e.g. SUBSCRIBER_ALIAS) — no tier change
      return "premium";
  }
}

revenuecatWebhook.post("/webhooks/revenuecat", async (c) => {
  const rawBody = await c.req.text();

  // Validate signature
  const signature = c.req.header("X-RevenueCat-Signature") ?? "";
  if (!signature) {
    return c.json({ error: "Missing webhook signature" }, 401);
  }

  try {
    if (!verifySignature(rawBody, signature)) {
      return c.json({ error: "Invalid webhook signature" }, 401);
    }
  } catch {
    return c.json({ error: "Invalid webhook signature" }, 401);
  }

  let payload: RevenueCatWebhookPayload;
  try {
    payload = JSON.parse(rawBody) as RevenueCatWebhookPayload;
  } catch {
    return c.json({ error: "Invalid JSON body" }, 400);
  }

  const { event } = payload;
  if (!event?.type || !event?.app_user_id) {
    return c.json({ error: "Missing event type or app_user_id" }, 400);
  }

  const newTier = tierFromEvent(event);

  // app_user_id from RevenueCat maps to auth_id in our users table
  const { error } = await supabase
    .from("users")
    .update({ subscription_tier: newTier })
    .eq("auth_id", event.app_user_id);

  if (error) {
    console.error(
      `Webhook: failed to update tier for ${event.app_user_id}:`,
      error.message
    );
    return c.json({ error: "Failed to update subscription" }, 500);
  }

  console.log(
    `Webhook: ${event.type} → ${event.app_user_id} tier set to ${newTier}`
  );
  return c.json({ success: true, tier: newTier });
});
