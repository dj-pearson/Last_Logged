# Marketing screenshots

Drop real app screenshots here. Until they exist the hero renders a labelled
device skeleton and the build prints a warning — it no longer ships an empty
grey box captioned "App Screenshot".

## Required

| File | Used by | Notes |
|------|---------|-------|
| `hero.webp` | Landing hero (preferred) | 1170x2532 (iPhone 14 Pro @3x) or any 9:19.5 |
| `hero.png`  | Landing hero (fallback) | Same dimensions; served to browsers without WebP |

If only one of the two is present the component uses it; `hero.webp` is
preferred when both exist.

## Capturing

Use the same Home screen shot as store listing #1 (see
`store-listing/screenshots.md`) so the site and the stores match:

- Populated tracker list grouped by category
- A mix of urgency colours visible (on-schedule, due-soon, overdue)
- No real personal data

```bash
# iOS simulator
xcrun simctl io booted screenshot hero.png

# Android emulator
adb exec-out screencap -p > hero.png

# Then produce the WebP
cwebp -q 82 hero.png -o hero.webp
```

## Why dimensions matter

`HeroScreenshot.astro` sets intrinsic `width`/`height` so the browser reserves
space before decode. Committing an image with a different aspect ratio will
letterbox it — update the constants in that component if the ratio changes.
