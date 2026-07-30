# App Privacy (App Store) + Data Safety (Play) answers

These answers must match `LastLogged/PrivacyInfo.xcprivacy`. If that file
changes, revisit this document.

## Data collection summary

Last Logged collects the minimum data needed to make the app work and to
process purchases. We don't sell data, we don't share it with advertising
networks, and we don't fingerprint devices.

---

## App Store — App Privacy questionnaire

### Data Types

**Contact Info → Email Address**
- Collected: Yes
- Linked to user: Yes
- Used for tracking: No
- Purposes: App Functionality (account creation + authentication)

**Identifiers → User ID**
- Collected: Yes (Supabase auth UUID, internal user_id)
- Linked to user: Yes
- Used for tracking: No
- Purposes: App Functionality

**Purchases → Purchase History**
- Collected: Yes (via RevenueCat — subscription + lifetime status only)
- Linked to user: Yes
- Used for tracking: No
- Purposes: App Functionality

**Usage Data → Product Interaction**
- Collected: Yes (tracker created, tracker logged, paywall shown/dismissed/converted — via TelemetryDeck, which does not use identifiers)
- Linked to user: No
- Used for tracking: No
- Purposes: Analytics, App Functionality

**Diagnostics → Crash Data**
- Collected: Yes (Apple crash reports + Sentry)
- Sentry is configured with `sendDefaultPii = false`, screenshots and view
  hierarchies disabled, and a `beforeSend` hook that strips the user object,
  server name, extras, request headers/cookies/bodies, and breadcrumb content.
  Stack traces and device model only — no tracker names, notes, or emails.
- Disabled entirely when no `SENTRY_DSN` is configured.
- Linked to user: No
- Used for tracking: No
- Purposes: App Functionality

**Diagnostics → Performance Data**
- Collected: Yes (TelemetryDeck aggregate signals only)
- Linked to user: No
- Used for tracking: No
- Purposes: Analytics

### Tracking

- Does this app track users? **No.**
- Tracking domains: **None.**

---

## Play Store — Data Safety form

### Data collection

| Data type | Collected | Shared | Required | Purpose |
|-----------|-----------|--------|----------|---------|
| Email address | Yes | No | Required | Account management |
| User ID | Yes | No | Required | Account management, app functionality |
| Purchase history | Yes | No | Required | Account management, app functionality |
| App interactions (events logged) | Yes | No | Optional | Analytics |
| Crash logs | Yes | No | Optional | App performance |
| Diagnostics | Yes | No | Optional | App performance |

Everything else: **No**. Notably:
- Name, phone, address, photos/videos, audio files, files and docs, calendar, contacts, precise location, approximate location, web browsing history, search history: **not collected**.
- SMS, contacts, health and fitness, financial info: **not collected**.

### Data sharing

All "shared" answers: **No**.

### Data security

- Data is encrypted in transit (TLS)
- Users can request data deletion via in-app Settings → Account → Delete Account

### Third-party partners

| Partner | Data | Purpose | SDK disclosure |
|---------|------|---------|----------------|
| Supabase | Email, User ID, tracker data | Auth + sync backend | Private cloud backend |
| RevenueCat | User ID, purchase receipts | Subscription management | Billing SDK |
| TelemetryDeck | Aggregate signals (SHA-256 hashed user id on Android; no raw id or email) | Analytics | Privacy-preserving analytics |
| Sentry | Crash stack traces, OS/device model, app version | Diagnostics | Crash reporting; PII scrubbed before send |
| Firebase Cloud Messaging | Device token | Push notifications | Google service |

### Data deletion

- Account deletion triggered in-app wipes everything (tracker data, logs, auth user) via the `delete-account` edge function.
- Support email `support@lastlogged.com` handles any out-of-band requests within 30 days.
