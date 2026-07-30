/**
 * Values that make Universal Links (iOS) and App Links (Android) work.
 *
 * These were previously hardcoded placeholders in `public/.well-known/`, which
 * meant the association files served from production were invalid and every
 * `https://lastlogged.com/tracker/...` link opened the website instead of the
 * app. They are now injected at build time.
 *
 * Set in the deploy environment (GitHub Actions secrets -> Cloudflare Pages):
 *   APPLE_TEAM_ID        Apple Developer -> Membership -> Team ID (10 chars)
 *   ANDROID_SHA256_CERT  SHA-256 fingerprint of the Play App Signing cert,
 *                        colon-separated uppercase hex. Play Console ->
 *                        Release -> Setup -> App signing.
 */

export const BUNDLE_ID = 'com.pearsonmedia.lastlogged';
export const ANDROID_PACKAGE = 'com.pearsonmedia.lastlogged';

/** Paths both platforms hand off to the app. Keep in sync with DeepLinks.kt. */
export const APP_LINK_PATHS = ['/tracker/*', '/open', '/share/*'];

export const PLACEHOLDER_TEAM_ID = 'TEAMID';
export const PLACEHOLDER_FINGERPRINT = 'REPLACE_WITH_YOUR_SHA256_FINGERPRINT';

export const appleTeamId = process.env.APPLE_TEAM_ID?.trim() || PLACEHOLDER_TEAM_ID;
export const androidCertFingerprint =
  process.env.ANDROID_SHA256_CERT?.trim() || PLACEHOLDER_FINGERPRINT;

export const isAppleConfigured = appleTeamId !== PLACEHOLDER_TEAM_ID;
export const isAndroidConfigured = androidCertFingerprint !== PLACEHOLDER_FINGERPRINT;

/**
 * Build-time guard. Placeholders are tolerated for local/preview builds but must
 * never reach production silently — a deploy with them serves association files
 * that look valid to a crawler and are rejected by both platforms.
 *
 * Set `REQUIRE_APP_LINKS=true` in the production deploy to make this fatal.
 */
export function warnIfPlaceholders(): void {
  const missing: string[] = [];
  if (!isAppleConfigured) missing.push('APPLE_TEAM_ID (apple-app-site-association)');
  if (!isAndroidConfigured) missing.push('ANDROID_SHA256_CERT (assetlinks.json)');

  if (missing.length === 0) return;

  const message =
    `App association files are using PLACEHOLDER values: ${missing.join(', ')}. ` +
    'Universal Links and App Links will NOT work with this build.';

  if (process.env.REQUIRE_APP_LINKS === 'true') {
    throw new Error(message);
  }

  console.warn(`[app-association] ${message}`);
}
