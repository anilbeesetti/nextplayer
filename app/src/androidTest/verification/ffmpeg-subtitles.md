# FFmpeg subtitles

NextPlayer targets `1.11.1-0.18.0`. Until that artifact is published, build against
[nextlib PR #214](https://github.com/anilbeesetti/nextlib/pull/214), including its
startup, timing and buffered-seek fixes at `f0e59cd`:

```sh
./gradlew test ktlintCheck assembleDebug :app:assembleDebugAndroidTest \
  -PnextlibPath=/path/to/nextlib
```

The existing composite-build option substitutes both nextlib modules. No Maven
Local publication or dependency edits are needed for local testing.

## Coverage

`FfmpegSubtitlePlaybackTest` uses the real `PlayerService`, source factories,
renderer selection, and production buffering configuration. Each case uses a
fresh media URI so saved playback position and track selections cannot leak
between cases.

| Input | Output | Position behavior |
| --- | --- | --- |
| Embedded ASS/SSA, ASS/SSA sidecars, Matroska font attachments, ASS embedded fonts | libass bitmap | Authored placement; dragging disabled |
| PGS, VobSub, DVB | FFmpeg bitmap | Authored placement; dragging disabled |
| SRT, WebM WebVTT (including caption tracks), tx3g, external SRT/WebVTT/TTML | Media3 text | User position and dragging retained |

The 17-case playback matrix checks rendering, small seeks inside a visible cue,
subtitle disable/re-enable with backward seeking, track changes, and ASS cue gaps.
One ASS sidecar requires an HTTP request header: missing it produces HTTP 403.
This checks that native sidecars use the same per-item request headers as video.
`LocalSubtitleTest` additionally covers adding subtitles during playback and
preserving audio/video decoder selections through reloads and playlist navigation.

`SubtitleInteractionTest` swipes the actual painted subtitle bounds on the player
screen, with transport controls hidden. Authored ASS/SSA/PGS must preserve a saved
nonzero text offset without moving. ASS is also checked with embedded styles off.
The SRT control case must change and save its initial offset. Gesture setup waits
for paused playback, a focused landscape window, painted subtitle pixels and a
completed draw. It reads Compose's test semantics directly to verify that controls
have disappeared after their exit animation. A dropped setup tap can be retried
once; subtitle swipes and saved-offset assertions are never retried.

The production `PlayerGestures` Compose regression starts a real drag, changes
eligibility to an authored track while the pointer is down, and verifies rollback,
no highlight, and no saved offset. Policy tests cover text fallback, transcoded
ASS, empty cue intervals, bitmap cues, and ordinary text.

Nextlib's native device regressions cover consumed ASS events, held future
packets, cue gaps, backward and same-time seeks, static cues after final-stream
EOS, bitmap packet replay and clears, viewport changes, and WebM extraction.

## Results (2026-11-06)

- 510 JVM tests passed, with zero failures, errors or skipped tests in the XML reports.
- `ktlintCheck`, `assembleDebug` and `:app:assembleDebugAndroidTest` passed.
- ARM64 API 35 (4 KiB pages): [four app tests](ffmpeg-subtitles/player-api35.txt),
  including the 17 playback cases, and [16 native tests](ffmpeg-subtitles/nextlib-api35.txt) passed.
- ARM64 Pixel_6a API 37 (16 KiB pages): [four app tests](ffmpeg-subtitles/player-api37.txt)
  and [16 native tests](ffmpeg-subtitles/nextlib-api37.txt) passed.
- Both nextlib release AARs passed native dependency, export and 16 KiB alignment
  checks for all four packaged ABIs.

### Recheck — 2026-11-07

Against nextlib `f0e59cd`, on a fresh ARM64 API 35 emulator:

- All four app instrumentation tests passed together, including the 17-case playback
  matrix, authored ASS/SSA/PGS drag locks, the SRT drag control and local subtitles.
- All 27 nextlib subtitle device tests passed, including initialization failures,
  abandoned initialization, seeks during initialization with delay, bitmap timing
  changes, fonts, fallback formats and viewport resizing.
- All 15 nextlib JVM tests, setup regression checks, debug/release builds and native
  package checks passed. Both AARs contain all four ABIs with 16 KiB alignment.
- The final app run had no crash-log entries. One dropped setup tap required the
  bounded retry; every actual subtitle drag assertion passed on its first attempt.

This recheck ran on API 35. The API 37 results above are from the earlier run.

## Visual evidence

On Pixel_6a, API 37, 2400 × 1080, the red ASS drawing remains inside its cyan video
outline before and after an attempted drag. Both captures pass nextlib's
`check_bitmap_alignment.py` pixel check. The bitmap overlay shares the video's
size, display-aspect-ratio override, zoom and pan modifiers.

- [ASS before drag](ffmpeg-subtitles/ass-before-drag.png)
- [ASS after drag](ffmpeg-subtitles/ass-after-drag.png)
- [SRT before drag](ffmpeg-subtitles/srt-before-drag.png)
- [SRT after drag](ffmpeg-subtitles/srt-after-drag.png)

In the SRT captures, the text moves from y=389–428 to y=173–212. These manual
captures use extended synthetic versions of the checked-in fixtures so controls
can be inspected without the clip ending.

## Release notes

The ordinary Maven build requires `1.11.1-0.18.0` to be published before merging.
Native ASS styling, fonts and authored positions are rasterized by libass;
NextPlayer's text-style controls cannot restyle those bitmaps. Subtitle timing
controls continue to belong to nextlib's renderers.

This verification covers the synthetic formats and lifecycle cases above. It
does not establish support for arbitrary malformed files, live ASS, DRM subtitle
tracks, or multi-period ASS sidecars; these are outside nextlib's documented
verified scope.
