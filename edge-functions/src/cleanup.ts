import { Hono } from "hono";
import { supabase } from "./supabase.js";
import { log, logError } from "./logger.js";

export const cleanup = new Hono();

// Retention periods
const SOFT_DELETE_RETENTION_DAYS = 90;
const ARCHIVED_ITEM_RETENTION_DAYS = 180;

export async function runCleanup(): Promise<{
  deletedSoftDeletedLogs: number;
  deletedArchivedItems: number;
}> {
  const stats = { deletedSoftDeletedLogs: 0, deletedArchivedItems: 0 };

  // 1. Permanently delete soft-deleted completion_logs older than 90 days
  const softDeleteCutoff = new Date();
  softDeleteCutoff.setDate(softDeleteCutoff.getDate() - SOFT_DELETE_RETENTION_DAYS);

  const { data: deletedLogs, error: logsError } = await supabase
    .from("completion_logs")
    .delete()
    .not("deleted_at", "is", null)
    .lt("deleted_at", softDeleteCutoff.toISOString())
    .select("id");

  if (logsError) {
    logError("cleanup_soft_deleted_logs_failed", logsError.message);
  } else {
    stats.deletedSoftDeletedLogs = deletedLogs?.length ?? 0;
  }

  // 2. Permanently delete archived tracker_items with no recent completion_logs
  const archivedCutoff = new Date();
  archivedCutoff.setDate(archivedCutoff.getDate() - ARCHIVED_ITEM_RETENTION_DAYS);

  // Find archived items with no completion logs newer than the cutoff
  const { data: archivedItems, error: archivedError } = await supabase
    .from("tracker_items")
    .select("id")
    .eq("is_archived", true)
    .lt("updated_at", archivedCutoff.toISOString());

  if (archivedError) {
    logError("cleanup_fetch_archived_items_failed", archivedError.message);
  } else if (archivedItems && archivedItems.length > 0) {
    // For each archived item, check if it has recent completion logs
    for (const item of archivedItems) {
      const { count } = await supabase
        .from("completion_logs")
        .select("id", { count: "exact", head: true })
        .eq("tracker_item_id", item.id)
        .gte("completed_at", archivedCutoff.toISOString());

      if (count === 0) {
        // No recent logs — safe to delete
        const { error: deleteError } = await supabase
          .from("tracker_items")
          .delete()
          .eq("id", item.id);

        if (deleteError) {
          logError("cleanup_delete_archived_item_failed", deleteError.message, { itemId: item.id });
        } else {
          stats.deletedArchivedItems++;
        }
      }
    }
  }

  return stats;
}

cleanup.post("/cleanup-old-data", async (c) => {
  log("cleanup_start", {}, c);
  try {
    const stats = await runCleanup();
    log("cleanup_complete", stats, c);
    return c.json({ success: true, stats });
  } catch (err) {
    logError("cleanup_error", err, {}, c);
    return c.json({ error: "Cleanup failed" }, 500);
  }
});
