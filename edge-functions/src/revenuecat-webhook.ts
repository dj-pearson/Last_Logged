import { Hono } from "hono";
import { supabase } from "./supabase.js";
import { log, logError, logWarn } from "./logger.js";
import crypto from "node:crypto";

const REVENUECAT_WEBHOOK_SECRET = process.env.REVENUECAT_WEBHOOK_SECRET!;
// Validated at startup by validate-env.ts — no fallback default

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
    logWarn("webhook_signature_missing", { ip: c.req.header("x-forwarded-for") ?? "unknown" }, c);
    return c.json({ error: "Missing webhook signature" }, 401);
  }

  try {
    if (!verifySignature(rawBody, signature)) {
      logWarn("webhook_signature_invalid", { ip: c.req.header("x-forwarded-for") ?? "unknown" }, c);
      return c.json({ error: "Invalid webhook signature" }, 401);
    }
  } catch {
    logWarn("webhook_signature_invalid", { ip: c.req.header("x-forwarded-for") ?? "unknown" }, c);
    return c.json({ error: "Invalid webhook signature" }, 401);
  }

  // Validate request freshness (prevent replay attacks)
  const requestTimestamp = c.req.header("X-RevenueCat-Request-Timestamp");
  if (requestTimestamp) {
    const timestampMs = parseInt(requestTimestamp, 10) * 1000;
    const now = Date.now();
    const maxAgeMs = 5 * 60 * 1000; // 5 minutes

    if (isNaN(timestampMs) || Math.abs(now - timestampMs) > maxAgeMs) {
      const ip = c.req.header("x-forwarded-for") ?? "unknown";
      logWarn("webhook_replay_rejected", {
        ip,
        requestTimestamp,
        serverTime: new Date().toISOString(),
        ageMs: isNaN(timestampMs) ? "invalid" : Math.abs(now - timestampMs),
      }, c);
      return c.json({ error: "Request timestamp too old or invalid" }, 403);
    }
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
    logError("webhook_tier_update_failed", error.message, {
      appUserId: event.app_user_id,
      eventType: event.type,
      newTier,
    }, c);
    return c.json({ error: "Failed to update subscription" }, 500);
  }

  log("webhook_tier_updated", {
    appUserId: event.app_user_id,
    eventType: event.type,
    newTier,
  }, c);
  return c.json({ success: true, tier: newTier });
});
