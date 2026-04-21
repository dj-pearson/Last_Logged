# Screenshot shot list

We submit the same 5 shots on both platforms, in the same order, with
consistent copy. The design template lives in `/design/store-screenshots`
(not checked in — Figma).

## Shot list

### 1. Home screen (hero)

Shows a populated tracker list grouped by category with urgency colors.

- **Overlay copy (top):** "Stop wondering when you last did it."
- **Overlay copy (bottom):** "One tap to log. Smart reminders take it from there."

### 2. Quick-log sheet (iOS long-press / Android long-press)

- **Overlay copy:** "Log with notes. Backdate anything."

### 3. Widget + lock screen

Small, medium, and large home-screen widgets plus an iOS Live Activity
with a recent log.

- **Overlay copy:** "Your most overdue items — always one glance away."

### 4. Detail view with streak + timeline

Tracker detail with stats row (streak, total logs, avg interval) and
vertical timeline of completions.

- **Overlay copy:** "See every completion. Build real streaks."

### 5. Paywall

Feature carousel + social proof + package cards.

- **Overlay copy:** "Go Premium for unlimited trackers."

---

## Device matrix

### iOS (App Store Connect)

Required sizes:

| Device | Resolution | Source render |
|--------|-----------|---------------|
| 6.9" iPhone (e.g. 16 Pro Max) | 1290 × 2796 | Required |
| 6.7" iPhone (e.g. 15 Pro Max) | 1320 × 2868 | Required (older) |
| 6.5" iPhone (e.g. 11 Pro Max) | 1284 × 2778 | Required for older bucket |
| 5.5" iPhone (e.g. 8 Plus) | 1242 × 2208 | Optional but helpful for older-device browsers |
| iPad 13" | 2064 × 2752 | Required if iPad support is ON |

Submit all 5 shots at the largest size; App Store Connect upscales and
generates intermediate sizes.

### Android (Play Console)

- Phone screenshots: 1080 × 1920 (min) to 3840 × 7680 (max), 16:9–9:16 range
- 7" tablet: optional, 1200 × 1920
- 10" tablet: optional, 1600 × 2560
- **Feature graphic: 1024 × 500, JPEG/PNG, no alpha** — required for store listing

## Capture + polish process

1. Record in simulator / emulator with a pre-seeded demo dataset (use
   `DataSeeder` with reviewer-friendly copy)
2. Use the same Light theme across both platforms for consistency
3. Add status-bar time = 9:41 (iOS) / 09:41 (Android)
4. Annotate via Figma; keep overlay color tokens `#4F46E5` primary / white
5. Export 2x PNG per size, compress with `pngquant --quality 85-95`
6. Upload in the order above

## Accessibility checklist

- All screenshot copy legible at the smallest rendered size in the store
- Contrast ratio ≥ 4.5:1 between overlay text and background
- No text-heavy screens (stores crop aggressively on some devices)
