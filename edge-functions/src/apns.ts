import apn from "@parse/node-apn";
import type { PushResult } from "./fcm.js";

// APNs key can be provided as base64 content (preferred) or file path (fallback)
const APNS_KEY_CONTENT = process.env.APNS_KEY_CONTENT;
const APNS_KEY_PATH = process.env.APNS_KEY_PATH ?? "./certs/AuthKey.p8";
const APNS_KEY_ID = process.env.APNS_KEY_ID!;
const APNS_TEAM_ID = process.env.APNS_TEAM_ID!;
const APNS_BUNDLE_ID = process.env.APNS_BUNDLE_ID ?? "com.pearsonmedia.lastlogged";
const APNS_PRODUCTION = process.env.APNS_PRODUCTION === "true";

function resolveApnsKey(): string | Buffer {
  if (APNS_KEY_CONTENT) {
    return Buffer.from(APNS_KEY_CONTENT, "base64");
  }
  return APNS_KEY_PATH;
}

let provider: apn.Provider | null = null;

export function getApnProvider(): apn.Provider {
  if (!provider) {
    provider = new apn.Provider({
      token: {
        key: resolveApnsKey(),
        keyId: APNS_KEY_ID,
        teamId: APNS_TEAM_ID,
      },
      production: APNS_PRODUCTION,
    });
  }
  return provider;
}

export interface OverdueItem {
  name: string;
  daysSinceCompletion: number;
}

export function buildDigestNotification(
  overdueItems: OverdueItem[]
): apn.Notification {
  const notification = new apn.Notification();
  notification.topic = APNS_BUNDLE_ID;
  notification.sound = "default";

  if (overdueItems.length === 1) {
    const item = overdueItems[0];
    notification.alert = {
      title: "Last Logged Reminder",
      body: `It's been ${item.daysSinceCompletion} days since your last ${item.name}. Time to log it!`,
    };
  } else {
    const itemList = overdueItems
      .slice(0, 5)
      .map((i) => `${i.name} (${i.daysSinceCompletion}d)`)
      .join(", ");
    notification.alert = {
      title: `${overdueItems.length} Overdue Items`,
      body: `You have overdue items: ${itemList}${overdueItems.length > 5 ? "..." : ""}`,
    };
  }

  return notification;
}

/**
 * APNs reports a permanently dead token as `Unregistered` (HTTP 410) or
 * `BadDeviceToken` (HTTP 400 — commonly an FCM token stored against an iOS
 * row, or a sandbox token sent to production). Both mean stop sending.
 */
const DEAD_TOKEN_REASONS = new Set(["Unregistered", "BadDeviceToken", "DeviceTokenNotForTopic"]);

/**
 * Sends one notification and normalises the result into the same shape as
 * `sendFcmNotification`, so the digest can treat both platforms uniformly.
 */
export async function sendApnsNotification(
  deviceToken: string,
  notification: apn.Notification
): Promise<PushResult> {
  try {
    const result = await getApnProvider().send(notification, deviceToken);

    if (result.failed.length === 0) {
      return { ok: true, unregistered: false };
    }

    const failure = result.failed[0];
    const reason = failure.response?.reason ?? failure.error?.message ?? "unknown";
    const status = Number(failure.status ?? 0);

    return {
      ok: false,
      unregistered: status === 410 || DEAD_TOKEN_REASONS.has(reason),
      error: `APNs send failed (${status || "?"}): ${reason}`,
    };
  } catch (err) {
    return {
      ok: false,
      unregistered: false,
      error: err instanceof Error ? err.message : String(err),
    };
  }
}
