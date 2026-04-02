import { Hono } from "hono";
import { supabase } from "./supabase.js";
import { log, logError } from "./logger.js";

export const deleteAccount = new Hono();

async function getUserIdFromAuth(authId: string): Promise<string | null> {
  const { data, error } = await supabase
    .from("users")
    .select("id")
    .eq("auth_id", authId)
    .single();

  if (error || !data) return null;
  return data.id as string;
}

async function deleteAllUserData(userId: string, authId: string): Promise<void> {
  // Delete in order respecting foreign key constraints
  // 1. Completion logs
  await supabase
    .from("completion_logs")
    .delete()
    .eq("user_id", userId);

  // 2. Tracker items
  await supabase
    .from("tracker_items")
    .delete()
    .eq("user_id", userId);

  // 3. Tracker categories
  await supabase
    .from("tracker_categories")
    .delete()
    .eq("user_id", userId);

  // 4. User devices
  await supabase
    .from("user_devices")
    .delete()
    .eq("user_id", userId);

  // 5. Audit log entries
  await supabase
    .from("audit_log")
    .delete()
    .eq("user_id", userId);

  // 6. Users table
  await supabase
    .from("users")
    .delete()
    .eq("id", userId);

  // 7. Delete auth user (requires service role)
  await supabase.auth.admin.deleteUser(authId);
}

deleteAccount.post("/delete-account", async (c) => {
  // Authenticate via Authorization header (Supabase JWT)
  const authHeader = c.req.header("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) {
    logError("delete_account_auth_failed", "Missing or invalid Authorization header", {}, c);
    return c.json({ error: "Missing or invalid Authorization header" }, 401);
  }

  const token = authHeader.slice(7);

  // Verify JWT and get the user
  const {
    data: { user },
    error: authError,
  } = await supabase.auth.getUser(token);

  if (authError || !user) {
    logError("delete_account_auth_failed", authError ?? "Invalid token", {}, c);
    return c.json({ error: "Invalid or expired token" }, 401);
  }

  const userId = await getUserIdFromAuth(user.id);
  if (!userId) {
    logError("delete_account_user_not_found", "User not found", { authId: user.id }, c);
    return c.json({ error: "User not found" }, 404);
  }

  try {
    await deleteAllUserData(userId, user.id);

    log("account_deleted", { userId, authId: user.id }, c);

    return c.json({ success: true, message: "Account and all data permanently deleted." });
  } catch (err) {
    logError("delete_account_failed", err, { userId, authId: user.id }, c);
    return c.json({ error: "Failed to delete account. Please try again." }, 500);
  }
});
