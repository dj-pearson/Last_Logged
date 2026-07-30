import { Hono } from "hono";
import type apn from "@parse/node-apn";
import { supabase } from "./supabase.js";
import { log, logError, logWarn } from "./logger.js";
import {
  buildDigestNotification,
  sendApnsNotification,
  OverdueItem,
} from "./apns.js";
import { buildDigestPayload, sendFcmNotification, PushResult } from "./fcm.js";

const LOCAL_NOTIFICATION_LIMIT = 64;

export const reminderDigest = new Hono();

interface DeviceRow {
  id: string;
  user_id: string;
  device_token: string;
  device_name: string | null;
  platform: string | null;
  last_seen_at: string;
}

interface TrackerItemRow {
  id: string;
  name: string;
  last_completed_at: string | null;
  reminder_interval_days: number | null;
  is_archived: boolean;
}

const STALE_DEVICE_DAYS = 90;

async function getActiveDevices(): Promise<DeviceRow[]> {
  const staleCutoff = new Date();
  staleCutoff.setDate(staleCutoff.getDate() - STALE_DEVICE_DAYS);

  const { data, error } = await supabase
    .from("user_devices")
    .select("id, user_id, device_token, device_name, platform, last_seen_at")
    .gte("last_seen_at", staleCutoff.toISOString());

  if (error) {
    logError("digest_fetch_devices_failed", error.message);
    return [];
  }

  return data ?? [];
}

async function getOverdueItemsForUser(userId: string): Promise<OverdueItem[]> {
  const { data, error } = await supabase
    .from("tracker_items")
    .select("id, name, last_completed_at, reminder_interval_days, is_archived")
    .eq("user_id", userId)
    .eq("is_archived", false)
    .not("reminder_interval_days", "is", null)
    .not("last_completed_at", "is", null);

  if (error) {
    logError("digest_fetch_items_failed", error.message, { userId });
    return [];
  }

  const now = Date.now();
  const overdueItems: OverdueItem[] = [];

  for (const item of (data ?? []) as TrackerItemRow[]) {
    if (!item.last_completed_at || !item.reminder_interval_days) continue;

    const lastCompleted = new Date(item.last_completed_at).getTime();
    const daysSince = Math.floor((now - lastCompleted) / (1000 * 60 * 60 * 24));

    if (daysSince > item.reminder_interval_days) {
      overdueItems.push({
        name: item.name,
        daysSinceCompletion: daysSince,
      });
    }
  }

  // Sort by most overdue first
  overdueItems.sort((a, b) => b.daysSinceCompletion - a.daysSinceCompletion);

  return overdueItems;
}

async function countUserTrackers(userId: string): Promise<number> {
  const { count, error } = await supabase
    .from("tracker_items")
    .select("id", { count: "exact", head: true })
    .eq("user_id", userId)
    .eq("is_archived", false)
    .not("reminder_interval_days", "is", null);

  if (error) return 0;
  return count ?? 0;
}

/**
 * Deletes a device row whose token the push provider reported as permanently
 * dead. Left in place, these accumulate forever and every future digest run
 * burns a request on them.
 */
async function pruneDeadDevice(deviceId: string, userId: string): Promise<void> {
  const { error } = await supabase.from("user_devices").delete().eq("id", deviceId);
  if (error) {
    logError("digest_prune_device_failed", error.message, { userId, deviceId });
    return;
  }
  log("digest_pruned_dead_device", { userId, deviceId });
}

export async function sendReminderDigests(): Promise<{
  sent: number;
  skipped: number;
  errors: number;
  pruned: number;
  unsupportedPlatform: number;
}> {
  const stats = {
    sent: 0,
    skipped: 0,
    errors: 0,
    pruned: 0,
    unsupportedPlatform: 0,
  };

  const devices = await getActiveDevices();

  // Group devices by user_id
  const devicesByUser = new Map<string, DeviceRow[]>();
  for (const device of devices) {
    const existing = devicesByUser.get(device.user_id) ?? [];
    existing.push(device);
    devicesByUser.set(device.user_id, existing);
  }

  for (const [userId, userDevices] of devicesByUser) {
    try {
      const totalTrackers = await countUserTrackers(userId);

      // Only send digest to users who exceed the local notification limit
      if (totalTrackers <= LOCAL_NOTIFICATION_LIMIT) {
        stats.skipped++;
        continue;
      }

      const overdueItems = await getOverdueItemsForUser(userId);
      if (overdueItems.length === 0) {
        stats.skipped++;
        continue;
      }

      // Built lazily per platform so an all-Android user never touches APNs.
      let apnsNotification: apn.Notification | null = null;

      // Send to all of the user's active devices, routed by platform. Sending
      // an FCM token to APNs (the previous behaviour) always fails.
      for (const device of userDevices) {
        const platform = (device.platform ?? "").toLowerCase();
        let result: PushResult;

        if (platform === "ios") {
          apnsNotification ??= buildDigestNotification(overdueItems);
          result = await sendApnsNotification(device.device_token, apnsNotification);
        } else if (platform === "android") {
          result = await sendFcmNotification(
            device.device_token,
            buildDigestPayload(overdueItems)
          );
        } else {
          // Unknown platform: skip rather than guessing a provider, which
          // would guarantee a failure and could prune a valid token.
          logWarn("digest_unsupported_platform", {
            userId,
            deviceId: device.id,
            platform: device.platform,
          });
          stats.unsupportedPlatform++;
          continue;
        }

        if (result.ok) {
          log("digest_send_success", {
            userId,
            deviceId: device.id,
            platform,
            overdueCount: overdueItems.length,
          });
          stats.sent++;
          continue;
        }

        logError("digest_send_failed", result.error, {
          userId,
          deviceId: device.id,
          platform,
        });
        stats.errors++;

        if (result.unregistered) {
          await pruneDeadDevice(device.id, userId);
          stats.pruned++;
        }
      }
    } catch (err) {
      logError("digest_user_error", err, { userId });
      stats.errors++;
    }
  }

  return stats;
}

reminderDigest.post("/send-reminder-digest", async (c) => {
  log("digest_start", {}, c);
  const stats = await sendReminderDigests();
  log("digest_complete", stats, c);
  return c.json({ success: true, stats });
});
