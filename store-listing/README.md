# Store listing assets

Single source of truth for App Store Connect + Play Console metadata.

## Files

- `app-store.md` — App Store Connect metadata (description, keywords, support URL, etc.)
- `play-store.md` — Play Console metadata (short + full description, tags)
- `app-privacy.md` — App Privacy (App Store) + Data Safety (Play) answers, mapped to `PrivacyInfo.xcprivacy`
- `age-rating.md` — Age-rating questionnaire answers for both stores
- `whats-new.md` — Release notes templates
- `screenshots.md` — Screenshot shot list + device matrix

## Principles

- Copy lives here; upload happens in store consoles. When a wording question
  comes up on review, change it here first and re-upload.
- Keep App Store + Play text nearly identical so messaging is consistent. Any
  differences are flagged with `# IOS-ONLY` / `# ANDROID-ONLY` comments.
- When something here changes, confirm PrivacyInfo.xcprivacy + `AppSecrets` +
  edge-function env vars are still aligned.
