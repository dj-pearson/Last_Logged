# Audio assets

## `success_chime.ogg`

Optional asset for the opt-in "Success sound" feature.

Drop a file named exactly `success_chime.ogg` in this directory to replace the
ToneGenerator fallback used by `SuccessSoundService`.

**Required specs:**
- Format: Ogg Vorbis (`.ogg`)
- Duration: 300–500 ms
- File size: < 50 KB
- Loudness: peaks no hotter than -6 dBFS (to sit pleasantly on top of app audio)
- License: Royalty-free, commercial-use-allowed (CC0 / CC-BY / purchased). Record
  the license and attribution (if any) in `THIRD_PARTY_LICENSES.md` at the repo
  root.

`SuccessSoundService.kt` already wires this file via `resources.getIdentifier`,
so no Kotlin changes are required when adding the asset — the fallback
`ToneGenerator.TONE_PROP_ACK` simply stops being used.

Good sources:
- https://freesound.org (filter by CC0)
- https://pixabay.com/sound-effects
- Purchased from envato/soundsnap
