import { Hono } from "hono";
import { supabase } from "./supabase.js";
import { getApnProvider, buildDigestNotification, OverdueItem } from "./apns.js";

const LOCAL_NOTIFICATION_LIMIT = 64;

export const reminderDigest = new Hono();

interface UserRow {
  id: string;
  auth_id: string;
  display_name: string | null;
  device_token: string | null;
}

interface TrackerItemRow {
  id: string;
  name: string;
  last_completed_at: string | null;
  reminder_interval_days: number | null;
  is_archived: boolean;
}

async function getUsersWithDeviceTokens(): Promise<UserRow[]> {
  const { data, error } = await supabase
    .from("users")
    .select("id, auth_id, display_name, device_token")
    .not("device_token", "is", null);

  if (error) {
    console.error("Error fetching users:", error.message);
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
    console.error(`Error fetching items for user ${userId}:`, error.message);
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

export async function sendReminderDigests(): Promise<{
  sent: number;
  skipped: number;
  errors: number;
}> {
  const stats = { sent: 0, skipped: 0, errors: 0 };

  const users = await getUsersWithDeviceTokens();
  const provider = getApnProvider();

  for (const user of users) {
    try {
      const totalTrackers = await countUserTrackers(user.id);

      // Only send digest to users who exceed the local notification limit
      if (totalTrackers <= LOCAL_NOTIFICATION_LIMIT) {
        stats.skipped++;
        continue;
      }

      const overdueItems = await getOverdueItemsForUser(user.id);
      if (overdueItems.length === 0) {
        stats.skipped++;
        continue;
      }

      const notification = buildDigestNotification(overdueItems);
      const result = await provider.send(notification, user.device_token!);

      if (result.failed.length > 0) {
        console.error(
          `Failed to send to user ${user.id}:`,
          result.failed[0].response
        );
        stats.errors++;
      } else {
        stats.sent++;
      }
    } catch (err) {
      console.error(`Error processing user ${user.id}:`, err);
      stats.errors++;
    }
  }

  return stats;
}

reminderDigest.post("/send-reminder-digest", async (c) => {
  console.log("Starting reminder digest...");
  const stats = await sendReminderDigests();
  console.log("Reminder digest complete:", stats);
  return c.json({ success: true, stats });
});
