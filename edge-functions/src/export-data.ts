import { Hono } from "hono";
import { supabase } from "./supabase.js";
import { log, logError } from "./logger.js";

export const exportData = new Hono();

interface ExportRow {
  [key: string]: unknown;
}

async function getUserIdFromAuth(authId: string): Promise<string | null> {
  const { data, error } = await supabase
    .from("users")
    .select("id")
    .eq("auth_id", authId)
    .single();

  if (error || !data) return null;
  return data.id as string;
}

async function fetchUserData(userId: string) {
  const [categories, items, logs] = await Promise.all([
    supabase
      .from("tracker_categories")
      .select("*")
      .eq("user_id", userId)
      .order("sort_order", { ascending: true }),
    supabase
      .from("tracker_items")
      .select("*")
      .eq("user_id", userId)
      .order("sort_order", { ascending: true }),
    supabase
      .from("completion_logs")
      .select("*")
      .eq("user_id", userId)
      .is("deleted_at", null)
      .order("completed_at", { ascending: false }),
  ]);

  return {
    tracker_categories: categories.data ?? [],
    tracker_items: items.data ?? [],
    completion_logs: logs.data ?? [],
  };
}

function rowsToCsv(rows: ExportRow[]): string {
  if (rows.length === 0) return "";
  const headers = Object.keys(rows[0]);
  const lines = [headers.join(",")];
  for (const row of rows) {
    const values = headers.map((h) => {
      const val = row[h];
      if (val === null || val === undefined) return "";
      const str = String(val);
      // Escape fields that contain commas, quotes, or newlines
      if (str.includes(",") || str.includes('"') || str.includes("\n")) {
        return `"${str.replace(/"/g, '""')}"`;
      }
      return str;
    });
    lines.push(values.join(","));
  }
  return lines.join("\n");
}

function buildCsvExport(data: {
  tracker_categories: ExportRow[];
  tracker_items: ExportRow[];
  completion_logs: ExportRow[];
}): string {
  const sections: string[] = [];

  sections.push("# tracker_categories");
  sections.push(rowsToCsv(data.tracker_categories));
  sections.push("");
  sections.push("# tracker_items");
  sections.push(rowsToCsv(data.tracker_items));
  sections.push("");
  sections.push("# completion_logs");
  sections.push(rowsToCsv(data.completion_logs));

  return sections.join("\n");
}

exportData.post("/export-data", async (c) => {
  // Authenticate via Authorization header (Supabase JWT)
  const authHeader = c.req.header("Authorization") ?? "";
  if (!authHeader.startsWith("Bearer ")) {
    logError("export_auth_failed", "Missing or invalid Authorization header", {}, c);
    return c.json({ error: "Missing or invalid Authorization header" }, 401);
  }

  const token = authHeader.slice(7);

  // Verify JWT and get the user
  const {
    data: { user },
    error: authError,
  } = await supabase.auth.getUser(token);

  if (authError || !user) {
    logError("export_auth_failed", authError ?? "Invalid token", {}, c);
    return c.json({ error: "Invalid or expired token" }, 401);
  }

  const userId = await getUserIdFromAuth(user.id);
  if (!userId) {
    logError("export_user_not_found", "User not found", { authId: user.id }, c);
    return c.json({ error: "User not found" }, 404);
  }

  const data = await fetchUserData(userId);
  const format = c.req.query("format") ?? "json";

  if (!["json", "csv"].includes(format)) {
    logError("export_invalid_format", `Invalid format: ${format}`, {}, c);
    return c.json({ error: "Invalid format. Use 'json' or 'csv'." }, 400);
  }

  log("export_success", {
    userId,
    format,
    categories: data.tracker_categories.length,
    items: data.tracker_items.length,
    logs: data.completion_logs.length,
  }, c);

  if (format === "csv") {
    const csv = buildCsvExport(data);
    return new Response(csv, {
      status: 200,
      headers: {
        "Content-Type": "text/csv; charset=utf-8",
        "Content-Disposition": 'attachment; filename="lastlogged-export.csv"',
      },
    });
  }

  // Default: JSON
  return c.json({
    exported_at: new Date().toISOString(),
    ...data,
  });
});
