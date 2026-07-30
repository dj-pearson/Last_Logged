import crypto from "node:crypto";
import { logWarn } from "./logger.js";

/**
 * Firebase Cloud Messaging HTTP v1 sender.
 *
 * The Android app registers FCM tokens through `PushTokenService`, but before
 * this module existed the reminder digest pushed every `user_devices` row —
 * Android included — through APNs, so Android users could never receive a
 * digest. Credentials come from a Firebase service account.
 *
 * Auth is a self-signed JWT exchanged for an OAuth access token, which avoids
 * pulling in googleapis just to mint one bearer token an hour.
 */

const FCM_PROJECT_ID = process.env.FCM_PROJECT_ID;
const FCM_CLIENT_EMAIL = process.env.FCM_CLIENT_EMAIL;

/**
 * Accepts either a literal PEM (with real newlines) or the `\n`-escaped form
 * that survives being pasted into a CI secret or .env file.
 */
function resolvePrivateKey(): string | undefined {
  const raw = process.env.FCM_PRIVATE_KEY;
  if (!raw) return undefined;
  return raw.includes("\\n") ? raw.replace(/\\n/g, "\n") : raw;
}

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

export function isFcmConfigured(): boolean {
  return Boolean(FCM_PROJECT_ID && FCM_CLIENT_EMAIL && resolvePrivateKey());
}

function base64url(input: Buffer | string): string {
  return Buffer.from(input)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

interface CachedToken {
  value: string;
  expiresAt: number;
}

let cachedToken: CachedToken | null = null;

/** Refresh a minute early so a token never expires mid-flight. */
const TOKEN_EXPIRY_SKEW_MS = 60_000;

async function getAccessToken(): Promise<string> {
  if (cachedToken && Date.now() < cachedToken.expiresAt - TOKEN_EXPIRY_SKEW_MS) {
    return cachedToken.value;
  }

  const privateKey = resolvePrivateKey();
  if (!FCM_PROJECT_ID || !FCM_CLIENT_EMAIL || !privateKey) {
    throw new Error("FCM is not configured");
  }

  const now = Math.floor(Date.now() / 1000);
  const header = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64url(
    JSON.stringify({
      iss: FCM_CLIENT_EMAIL,
      scope: SCOPE,
      aud: TOKEN_URL,
      iat: now,
      exp: now + 3600,
    })
  );

  const signer = crypto.createSign("RSA-SHA256");
  signer.update(`${header}.${claims}`);
  const signature = base64url(signer.sign(privateKey));
  const assertion = `${header}.${claims}.${signature}`;

  const response = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });

  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`FCM token exchange failed (${response.status}): ${detail}`);
  }

  const body = (await response.json()) as {
    access_token: string;
    expires_in: number;
  };

  cachedToken = {
    value: body.access_token,
    expiresAt: Date.now() + body.expires_in * 1000,
  };

  return cachedToken.value;
}

/** Exposed for tests — forces the next send to re-mint a token. */
export function resetFcmTokenCache(): void {
  cachedToken = null;
}

export interface PushResult {
  ok: boolean;
  /** The token is dead — delete the user_devices row rather than retrying. */
  unregistered: boolean;
  error?: string;
}

export interface PushPayload {
  title: string;
  body: string;
  /** Merged into FCM `data`; values must be strings per the FCM v1 schema. */
  data?: Record<string, string>;
}

/**
 * FCM reports a permanently dead token two ways: `UNREGISTERED` (uninstalled or
 * token rotated) and `INVALID_ARGUMENT` (malformed — often an APNs token that
 * was written to the wrong row). Both mean "stop sending here".
 */
function isUnregistered(status: number, payload: unknown): boolean {
  if (status === 404) return true;

  const error = (payload as { error?: { status?: string; details?: unknown[] } })
    ?.error;
  if (!error) return false;
  if (error.status === "UNREGISTERED" || error.status === "NOT_FOUND") return true;

  if (status === 400 && Array.isArray(error.details)) {
    return error.details.some(
      (detail) =>
        (detail as { errorCode?: string })?.errorCode === "UNREGISTERED" ||
        (detail as { errorCode?: string })?.errorCode === "INVALID_ARGUMENT"
    );
  }

  return false;
}

export async function sendFcmNotification(
  deviceToken: string,
  payload: PushPayload
): Promise<PushResult> {
  if (!isFcmConfigured()) {
    logWarn("fcm_not_configured", { reason: "missing FCM_* env vars" });
    return { ok: false, unregistered: false, error: "FCM is not configured" };
  }

  let accessToken: string;
  try {
    accessToken = await getAccessToken();
  } catch (err) {
    return {
      ok: false,
      unregistered: false,
      error: err instanceof Error ? err.message : String(err),
    };
  }

  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${FCM_PROJECT_ID}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token: deviceToken,
          notification: { title: payload.title, body: payload.body },
          data: payload.data,
          android: {
            priority: "high",
            notification: {
              // Must match the channel created by NotificationService.kt and
              // declared in AndroidManifest as the FCM default channel.
              channel_id: "tracker_reminders",
            },
          },
        },
      }),
    }
  );

  if (response.ok) {
    return { ok: true, unregistered: false };
  }

  let parsed: unknown;
  let raw = "";
  try {
    raw = await response.text();
    parsed = JSON.parse(raw);
  } catch {
    parsed = undefined;
  }

  return {
    ok: false,
    unregistered: isUnregistered(response.status, parsed),
    error: `FCM send failed (${response.status}): ${raw.slice(0, 500)}`,
  };
}

/**
 * Builds the digest copy. Kept identical in shape to the APNs version so both
 * platforms show the same wording.
 */
export function buildDigestPayload(
  overdueItems: { name: string; daysSinceCompletion: number }[]
): PushPayload {
  if (overdueItems.length === 1) {
    const item = overdueItems[0];
    return {
      title: "Last Logged Reminder",
      body: `It's been ${item.daysSinceCompletion} days since your last ${item.name}. Time to log it!`,
      data: { type: "reminder_digest" },
    };
  }

  const itemList = overdueItems
    .slice(0, 5)
    .map((i) => `${i.name} (${i.daysSinceCompletion}d)`)
    .join(", ");

  return {
    title: `${overdueItems.length} Overdue Items`,
    body: `You have overdue items: ${itemList}${overdueItems.length > 5 ? "..." : ""}`,
    data: { type: "reminder_digest" },
  };
}
