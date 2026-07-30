# Last Logged — Production Deployment Runbook

Release checklist and operational runbook. Run top to bottom the first time,
then use it as a reference for each subsequent release.

---

## 0. Prerequisites

- [ ] Supabase production project created (URL + anon key + service role key recorded)
- [ ] RevenueCat project created with offering + entitlement `premium`
- [ ] Apple Developer account + App Store Connect app record
- [ ] Google Play Console app record
- [ ] Firebase project created (for Android FCM)
- [ ] Google Cloud project with OAuth Web Client ID (for Google Sign-In)
- [ ] TelemetryDeck app id (optional but wired)
- [ ] Cloudflare Pages project connected to `website/` (for lastlogged.com)
- [ ] GitHub repo secrets populated (see §6)

---

## 1. Supabase database

### 1.1 Apply migrations (one-time per env)

Migrations live in `supabase/migrations/`. Apply in order:

```
20260324000001_initial_schema.sql
20260401000001_audit_log_and_soft_delete.sql
20260401000002_user_devices.sql
20260401000003_agreed_to_terms.sql
20260402000001_auth_rate_limits.sql
```

Either use Supabase CLI:

```bash
supabase link --project-ref <your-ref>
supabase db push
```

Or paste each file into the Supabase SQL editor in order.

### 1.2 Verify

- [ ] `users`, `tracker_categories`, `tracker_items`, `completion_logs`, `user_devices`, `audit_log`, `auth_rate_limits` tables exist
- [ ] RLS enabled on every user-data table
- [ ] `get_user_id()` function present
- [ ] `delete_user_account` RPC present
- [ ] `completion_logs.deleted_at` column present with index

---

## 2. Edge functions

### 2.1 Environment variables

All listed in `edge-functions/.env.example`. Required by `validate-env.ts`:

| Variable | Where to get it |
|----------|-----------------|
| `SUPABASE_URL` | Supabase dashboard → Settings → API |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase dashboard → Settings → API (service_role) |
| `REVENUECAT_WEBHOOK_SECRET` | RevenueCat → Integrations → Webhooks → Authorization header |
| `APNS_KEY_ID` | Apple Developer → Keys |
| `APNS_TEAM_ID` | Apple Developer → Membership |

Strongly recommended:

| Variable | Notes |
|----------|-------|
| `APNS_KEY_CONTENT` | Base64-encoded `.p8` contents — preferred over `APNS_KEY_PATH` |
| `APNS_TOPIC` | Bundle id: `com.pearsonmedia.lastlogged` |
| `FCM_PROJECT_ID` | Firebase Console → Project Settings → General. **Android digests are skipped without this.** |
| `FCM_CLIENT_EMAIL` | `client_email` from the Firebase service-account JSON |
| `FCM_PRIVATE_KEY` | `private_key` from the same JSON; `\n`-escaped newlines are handled |
| `CRON_SECRET` | Random string; guards `/cleanup-old-data` + `/reminder-digest` cron endpoints |
| `APP_ENV` | `production` |
| `APNS_ENVIRONMENT` | `production` once released (use `sandbox` for TestFlight-only testing) |

### 2.2 Deploy

Workflow `deploy-edge-functions.yml` handles deployment on merge to `main`.
Manual: `gh workflow run deploy-edge-functions.yml`.

### 2.3 Verify

- [ ] `POST /revenuecat-webhook` with an invalid signature returns 401 (not 500)
- [ ] `GET /export-data` with a valid Bearer token returns the user's data as JSON
- [ ] `POST /auth/check-rate-limit` returns `X-Auth-RateLimit-Remaining` header
- [ ] `POST /delete-account` with a valid JWT deletes + signs out

### 2.4 Cron jobs

Use Supabase scheduled functions, GitHub Actions cron, or your platform cron:

- **Daily 09:00 UTC** → `POST /reminder-digest` with `Authorization: Bearer $CRON_SECRET`
- **Weekly Sunday 03:00 UTC** → `POST /cleanup-old-data` with same bearer

---

## 3. RevenueCat

### 3.1 Apple App Store

- [ ] App ID registered in Apple Developer portal
- [ ] In-app purchase products created in App Store Connect:
  - `premium_monthly` — auto-renewable subscription, $3.99/mo
  - `premium_annual` — auto-renewable subscription, $24.99/yr
  - `lifetime` — non-consumable, $59.99
- [ ] Products added to RevenueCat iOS app
- [ ] App Store shared secret recorded in RevenueCat
- [ ] `premium` entitlement attached to all three products

### 3.2 Google Play Store

- [ ] App created in Play Console
- [ ] Billing products created with the same identifiers as iOS
- [ ] Products added to RevenueCat Android app
- [ ] Service Account JSON uploaded to RevenueCat
- [ ] `premium` entitlement attached

### 3.3 Webhook

- [ ] Webhook URL set to `<edge-function-host>/revenuecat-webhook`
- [ ] Authorization header matches `REVENUECAT_WEBHOOK_SECRET` env var
- [ ] Test event delivered successfully (RevenueCat → Webhooks → Send test)

---

## 4. APNs + FCM

### 4.1 iOS (APNs)

- [ ] APNs auth key (`.p8`) generated in Apple Developer → Keys
- [ ] `APNS_KEY_ID` + `APNS_TEAM_ID` recorded
- [ ] `.p8` content base64-encoded into `APNS_KEY_CONTENT` env var
- [ ] `APNS_ENVIRONMENT` set (`sandbox` for TestFlight, `production` for App Store)
- [ ] App entitlements file includes `aps-environment`

### 4.2 Android (FCM)

- [ ] Firebase project has an Android app registered with package `com.pearsonmedia.lastlogged`
- [ ] `google-services.json` downloaded and placed at `android/app/google-services.json`
  (gitignored; CI must inject from `GOOGLE_SERVICES_JSON` secret if ever used)
- [ ] FCM server key available for server-side sends (store in edge-function env later if needed)
- [ ] `reminder-digest.ts` confirmed to fan out to both APNs and FCM tokens in `user_devices`

### 4.3 Verify

- [ ] iOS device logs into the app → new row appears in `user_devices` with `platform='ios'`
- [ ] Android device logs into the app → new row appears with `platform='android'`
- [ ] Trigger `/reminder-digest` manually → notifications arrive on both

---

## 5. Google Sign-In (Android)

- [ ] OAuth Web Client ID created in Google Cloud Console
  (Credentials → Create → OAuth client ID → Web application)
- [ ] Same Web Client ID added to Supabase → Auth → Providers → Google
- [ ] Authorized redirect URI in Google Cloud includes
      `https://<project-ref>.supabase.co/auth/v1/callback`
- [ ] Put the Web Client ID into `android/local.properties` as `GOOGLE_WEB_CLIENT_ID` (or into GitHub Actions secret)

---

## 6. GitHub Actions secrets

For `android-ci.yml` + `ios-ci.yml` + the existing deploy workflows, set:

| Secret | Consumed by |
|--------|-------------|
| `SUPABASE_URL` | android-ci, ios-deploy |
| `SUPABASE_ANON_KEY` | android-ci, ios-deploy |
| `REVENUECAT_IOS_API_KEY` | ios-deploy |
| `REVENUECAT_ANDROID_API_KEY` | android-ci |
| `TELEMETRYDECK_APP_ID` | both |
| `GOOGLE_WEB_CLIENT_ID` | android-ci |
| `GOOGLE_SERVICES_JSON` | (future) android-release workflow |
| `ANDROID_KEYSTORE_BASE64` | (future) android-release workflow |
| `ANDROID_KEYSTORE_PASSWORD` | (future) android-release workflow |
| `ANDROID_KEY_ALIAS` | (future) android-release workflow |
| `ANDROID_KEY_PASSWORD` | (future) android-release workflow |
| `APPLE_ID` | ios-deploy |
| `APPLE_APP_SPECIFIC_PASSWORD` | ios-deploy |
| `APPLE_TEAM_ID` | ios-deploy |
| `APP_STORE_CONNECT_API_KEY_ID` | ios-deploy |
| `APP_STORE_CONNECT_API_ISSUER_ID` | ios-deploy |
| `APP_STORE_CONNECT_API_PRIVATE_KEY` | ios-deploy |
| `DEVELOPER_CERTIFICATE_P12_BASE64` | ios-deploy |
| `DEVELOPER_CERTIFICATE_PASSWORD` | ios-deploy |
| `PROVISIONING_PROFILE_BASE64` | ios-deploy |

---

## 7. Website

- [ ] `PUBLIC_APP_STORE_URL` env var set in Cloudflare Pages env
- [ ] `PUBLIC_PLAY_STORE_URL` env var set
- [ ] `/coming-soon` page returns 200
- [ ] Privacy policy URL (`/privacy`) + Terms URL (`/terms`) return 200
- [ ] Security headers present (CSP, HSTS, X-Frame-Options, etc. — see `website/public/_headers`)

---

## 8. Release cut

### 8.1 iOS (TestFlight → App Store)

1. Bump version: `git tag v1.0.0 && git push origin v1.0.0` OR workflow_dispatch `deploy-ios`.
2. Wait for build to appear in App Store Connect.
3. Distribute to internal TestFlight, smoke-test on device.
4. Submit for App Store review.
5. On approval, release.

### 8.2 Android (Play Console)

1. Run `./gradlew bundleRelease` (once a release signing workflow exists; manual for now).
2. Upload `.aab` to Play Console → Internal testing.
3. Promote to Closed / Open beta as appetite demands.
4. Submit for production review.

### 8.3 Post-release smoke tests (day 0)

- [ ] Sign up from a fresh install → email confirmation arrives
- [ ] Sign in via Google (Android) + Apple (iOS)
- [ ] Create 3 trackers → 4th is blocked by paywall
- [ ] Purchase monthly → paywall closes, 4th tracker succeeds
- [ ] Restore purchases on a second device
- [ ] Log a tracker → completion appears in detail view
- [ ] Widget shows most-overdue tracker on both platforms
- [ ] Export data from Settings → email arrives with JSON attachment
- [ ] Delete account → all data gone, auth user removed

---

## 9. Rollback

- **Edge function**: redeploy previous commit via `deploy-edge-functions.yml` workflow_dispatch with target SHA.
- **iOS**: App Store Connect → remove from sale or expedite a hotfix via TestFlight.
- **Android**: Play Console → halt rollout or roll back to prior track.
- **Database**: each migration is additive; rollback = manual SQL. No destructive migrations exist today.
