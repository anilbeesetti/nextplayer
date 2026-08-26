# Music implementation audit

Date: 2026-08-26

## Repository architecture reviewed

Graviton has one application module, seven core modules (`common`, `data`, `database`, `datastore`, `domain`, `media`, `model`, `ui`) and six feature modules (`music`, `network`, `player`, `playlist`, `settings`, `videopicker`). It uses Kotlin 2.4, AGP 9.3, Hilt/KSP, Compose Material 3, Room, JSON DataStore, Coil 3 and Media3 1.11. Native codec support comes from nextlib's FFmpeg Media3 renderer; there is no libmpv/JNI backend in this checkout.

## Existing playback path

`LocalMusicRepository` queries MediaStore and emits stable `AudioTrack` URIs. `MusicPlayback` resolves tracks to Media3 `MediaItem`s. Music controls connect to `PlayerService` through a `MediaController`; UI code does not create or own an ExoPlayer. `PlayerService` owns one ExoPlayer and MediaSession, audio focus/noisy handling, decoder fallback, metadata enrichment and notification lifecycle. Network files are exposed through the existing seekable HTTP range proxy and URLs use the same Media3 path. Video remains handled by the existing player feature; there are no MPV-RX or MPV-REX implementations to preserve or select in this source tree.

A shared Media3 session was retained deliberately rather than introducing a second player that would race for audio focus and produce two notifications. Music-specific queue restoration and corrupt-track advancement are isolated by checking real music metadata.

## Storage

Room schema version 9 stores video state, network connections, vault state and existing playlists. No music schema reset or destructive migration was introduced. Music presentation/provider/queue state extends the existing forward-compatible JSON `ApplicationPreferences` DataStore. MediaStore remains the local music source of truth.

## Clean-room and licensing

Graviton and the behavioral reference are GPL-3.0. No Booming Music source was copied. The implementation is based on public behavior and Graviton's existing patterns. LRCLIB access uses its documented HTTP API behind `LyricsProvider`; it is not coupled to playback.

## Notable pre-existing constraints

* The root Gradle build globally sets `Test.ignoreFailures = true`, so a successful aggregate task alone does not prove all tests passed; individual test reports must be inspected.
* Android exposes no public generic embedded-lyrics metadata key. The embedded provider safely attempts a commonly exposed extractor key and falls through; sidecar LRC/TTML is reliable.
* No Android Auto browser service, Glance/AppWidget module, USB-exclusive output backend, scrobbling account infrastructure or writable tag library exists in this checkout.
* Existing `PlayerService` uses Media3/nextlib, not MPV. Documentation referring to MPV is historical/specification material only.
