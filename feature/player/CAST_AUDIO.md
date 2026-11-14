# Local Cast audio

Some receivers play an MKV's default audio without reporting its other tracks. Cast controls
then use Media3 Inspector to discover local audio tracks. Selecting one prepares a temporary MP4
with the original compressed video and the selected compressed audio; nothing is re-encoded.
Receivers that expose all tracks keep native track selection.

The converter sends the temporary file but retains the original media item for playback on the
phone. Switching keeps the queue, current position, play/pause state and external subtitle choice.
Copies are cached during casting and removed on disconnect, stop or release. Shared-storage input
is staged in larger reads to avoid slow per-packet FUSE access. Preparation requires cache space;
MP4/receiver-incompatible codecs still require transcoding, which is not implemented.

## Verification

- `./gradlew assembleDebug test ktlintCheck`
- Device tests: `LocalCastAudioTest` in the player module's play instrumentation APK.
- Hardware: OPPO Reno13 5G, Android API 36, casting to Living Room TV.
- Actual reported file: Death Note episode 01, HEVC video, English/Japanese/English-commentary AAC.
- Reproduced the empty Cast audio picker before the fix. Verified all three choices afterward,
  using both file and MediaStore content URIs.
- Switched English → Japanese → English → cached Japanese. The receiver resumed playback;
  switching while paused kept playback paused and the position within receiver seek precision.
- Probed the prepared stream: HEVC video plus Japanese AAC only. Instrumentation compared the
  compressed video and Japanese audio hashes against the original episode, and verified that the
  input file was unchanged. The committed fixture is synthetic, not the reported episode.
- Receiver playback/selection was checked; TV audio was not independently recorded.

To run the same instrumented hash checks on another reported file, put a copy in the test app's
cache directory and pass `-e castAudioSource <cache-filename>` to the instrumentation runner.
