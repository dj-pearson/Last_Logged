/**
 * Validates that all required environment variables are set.
 * Throws at startup if any are missing, preventing silent failures
 * with placeholder defaults.
 */
export function validateRequiredEnv(): void {
  const required: { key: string; description: string }[] = [
    { key: "SUPABASE_URL", description: "Supabase project URL" },
    {
      key: "SUPABASE_SERVICE_ROLE_KEY",
      description: "Supabase service role key",
    },
    { key: "REVENUECAT_WEBHOOK_SECRET", description: "RevenueCat webhook HMAC secret" },
    { key: "APNS_KEY_ID", description: "APNs auth key ID" },
    { key: "APNS_TEAM_ID", description: "Apple Developer Team ID" },
  ];

  const missing: string[] = [];

  for (const { key, description } of required) {
    const value = process.env[key];
    if (!value || value.startsWith("your-") || value.startsWith("YOUR_")) {
      missing.push(`  ${key} — ${description}`);
    }
  }

  if (missing.length > 0) {
    console.error(
      `\n[FATAL] Missing or placeholder environment variables:\n${missing.join("\n")}\n`
    );
    console.error(
      "Set these in your .env file or deployment environment before starting.\n"
    );
    process.exit(1);
  }

  warnOptionalEnv();

  console.log("[startup] All required environment variables validated.");
}

/**
 * Variables the server can boot without, but whose absence silently disables a
 * user-visible feature. Warn loudly rather than failing — an iOS-only
 * deployment is a legitimate configuration.
 */
function warnOptionalEnv(): void {
  const fcmKeys = ["FCM_PROJECT_ID", "FCM_CLIENT_EMAIL", "FCM_PRIVATE_KEY"];
  const missingFcm = fcmKeys.filter((key) => {
    const value = process.env[key];
    return !value || value.startsWith("your-") || value.includes("YOUR_KEY_HERE");
  });

  if (missingFcm.length === fcmKeys.length) {
    console.warn(
      "[startup] FCM is not configured — Android devices will be SKIPPED by the " +
        "reminder digest. Set FCM_PROJECT_ID, FCM_CLIENT_EMAIL, and FCM_PRIVATE_KEY."
    );
  } else if (missingFcm.length > 0) {
    console.warn(
      `[startup] FCM is partially configured; missing: ${missingFcm.join(", ")}. ` +
        "Android pushes will fail until all three are set."
    );
  }
}
