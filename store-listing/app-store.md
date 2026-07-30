# App Store Connect metadata

## Basics

| Field | Value |
|-------|-------|
| App Name | Last Logged |
| Subtitle (30 char) | When did I last…? |
| Primary Category | Productivity |
| Secondary Category | Lifestyle |
| Bundle ID | com.pearsonmedia.lastlogged |
| SKU | lastlogged-ios-001 |
| Price | Free (with IAP) |
| Availability | All territories |

## Promotional Text (170 char — editable without review)

Track recurring life events with one tap. Smart reminders for everything you keep meaning to get around to — so nothing slips through the cracks.

## Description (4,000 char max)

When did I last change the HVAC filter? Call my parents? Get the car serviced?

Last Logged answers those questions in one tap.

Instead of digging through calendars, receipts, or memory, you keep a running log of the small things that matter. Tap once to record a completion. The app tracks how long it's been and reminds you when it's time again.

**Track anything recurring:**
• Home maintenance — filters, smoke detectors, gutters, water heater flushing
• Car care — oil changes, tire rotations, registration renewals, inspections
• Health — doctor visits, dental cleanings, prescription refills, vision checks
• Pet care — vet appointments, flea medication, grooming, vaccinations
• Personal — haircuts, calls to family, date nights, backups

**What makes Last Logged different:**
• Single-tap logging from the home screen or a widget — no forms, no friction
• Smart urgency colors so overdue items surface instantly
• Customizable reminders (daily, weekly, monthly, or any interval you pick)
• Home screen widgets in Small / Medium / Large sizes
• Live Activities and Dynamic Island support for recent logs (iOS 16.1+)
• Cloud sync across your iPhone, iPad, and Mac (via iCloud-equivalent sync)
• Full completion history so you can look back years
• Offline-first — log anywhere, sync when you're back online
• Private by design — your data is yours; we don't sell anything

**Free forever includes:**
• Up to 3 trackers
• Core reminders
• One widget size
• 100 most-recent completion entries

**Premium unlocks ($3.99/mo, $24.99/yr, or $59.99 lifetime):**
• Unlimited trackers
• Full completion history (forever)
• All widget sizes
• Priority sync
• Export to JSON
• Support a tiny indie team

No ads. No tracking across apps. Face ID lock for your data if you want it.

Ready to stop wondering "when did I last…?" — download Last Logged.

## Keywords (100 char — comma-separated, no spaces)

tracker,reminder,habits,maintenance,car,home,health,hvac,oil,vet,log,recurring,todo,schedule

## URLs

| Field | Value |
|-------|-------|
| Support URL | https://lastlogged.com/support |
| Marketing URL | https://lastlogged.com |
| Privacy Policy URL | https://lastlogged.com/privacy |
| Terms of Use URL | https://lastlogged.com/terms |

## Copyright

© 2026 Pearson Media

## App Review notes (internal)

- Test account: `review@lastlogged.com` / (set fresh password before submission)
- IAP may be tested without payment using StoreKit testing; no sandbox
  account needed for review reviewer.
- All data entered by the reviewer can be deleted via Settings → Account →
  Delete Account (typing DELETE).
- Contact: support@lastlogged.com

## Submission answers

Recorded here so every release answers App Store Connect the same way.

| Question | Answer | Source of truth |
|----------|--------|-----------------|
| Does your app use encryption? | **No** (exempt) | `ITSAppUsesNonExemptEncryption = false` in `LastLogged/Info.plist`. The app only uses HTTPS/TLS and Apple's Keychain — both exempt under the standard exemption, so no annual self-classification report is required. |
| Export compliance documentation | Not required | Follows from the above. |
| Content rights — third-party content | No | All copy and imagery is original. |
| Advertising identifier (IDFA) | No | No ad SDKs. TelemetryDeck receives a salted hash, never the IDFA. |
| Third-party analytics | Yes — TelemetryDeck | See `app-privacy.md`. |
| Account required to use the app? | No | Sign-in is optional and only enables cross-device sync. |
| Account deletion available in-app? | Yes | Settings → Account → Delete Account (requires typing `DELETE`). |

### Capabilities the provisioning profile must include

Keep this list in sync with `LastLogged/LastLogged.entitlements`:

- App Groups — `group.com.pearsonmedia.lastlogged` (widget + Live Activity share the store)
- Push Notifications — `aps-environment` (server reminder digest)
- Associated Domains — `applinks:lastlogged.com`, `webcredentials:lastlogged.com`

A missing capability on the profile fails the archive in `deploy-ios.yml`, not
at review time.

### Declared background modes

- `remote-notification` — the reminder digest delivers alerts to a backgrounded app.
