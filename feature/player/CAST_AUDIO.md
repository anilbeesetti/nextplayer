# Local Cast audio

Some receivers play an MKV's first audio stream without reporting the other audio tracks.
Cast controls inspect the local Matroska Tracks header and expose those choices. Selecting one
makes the existing HTTP server serve that audio TrackEntry first. The original file is untouched.

The overlay has the same length as the original Tracks element. Encoded packets, TrackNumbers,
Cluster positions, SeekHead and Cue offsets remain unchanged; a Tracks CRC is recomputed when
present. Each selection has its own registered URL, so an in-flight request keeps its original
header. Full downloads, byte ranges and HEAD retain the original content length.

There is no remux, re-encoding, source staging or movie cache. Only the small Tracks payload is
retained in memory. Switching still reloads the receiver at its current position, keeping the
queue, play/pause state and external subtitle choice. The converter keeps the original media item
for playback on the phone. Receivers that expose all tracks continue using native selection.

## Limits

- This fallback is for local Matroska/WebM files. Other containers require receiver-native tracks.
- Header probing is bounded to the first 8 MiB, with a 1 MiB Tracks limit and bounded element counts.
- A Segment-level CRC is not patched: recomputing it would require reading the entire movie.
- The selected codec must already be supported by the receiver; there is no transcoding.
- Native Cast track IDs and language requests were rejected or ignored for the reported MKV,
  including while playing and when AUDIO tracks were explicitly advertised at load time.

## Verification

- `./gradlew assembleDebug test ktlintCheck`
- `MatroskaTracksTest`: CRC/no-CRC, unknown Segment size, non-sequential TrackNumbers, variable-size
  entries, preserved padding, range boundaries, malformed/truncated headers and duplicate numbers.
- `LocalCastServerTest`: same-length overlays for GET/HEAD/ranges and immutable previously issued URLs.
- Play instrumentation: `LocalCastAudioTest`; synthetic fixture plus the actual reported episode.
  Verifies no movie/cache files are created, unchanged source hash and unchanged bytes outside
  Tracks, plus identical initial encoded video and Japanese audio packets after overlaying.
- Hardware: OPPO Reno13 5G, API 36, casting to Living Room TV (ExoPlayer Default Receiver A12D4273).
- Death Note episode 01: HEVC with English, Japanese and English-commentary AAC. The user confirmed
  Japanese dialogue from the header-only prototype. The phone-served implementation was verified
  by reading its actual Cast HTTP URL: Japanese TrackNumber 3 first, English 2 first after switching
  back, unchanged length, successful playback and paused switching. No laptop server is needed.

To run the instrumented checks on another reported file, put a copy in the test app's cache directory
and pass `-e castAudioSource <cache-filename>` to the instrumentation runner. Only the test materializes
an overlaid movie for platform demuxer verification; production never writes one.
