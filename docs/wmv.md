# WMV/ASF support

Next Player plays WMV/ASF by using:

- `third_party/nextlib` (vendored): builds FFmpeg for Android and provides
  - software decoders (VC-1/WMV* video, WMA audio, etc)
  - an ASF/WMV extractor (FFmpeg `avformat` demux)
- Media3/ExoPlayer: rendering, track selection, UI, etc.

## Build requirements

- Android SDK installed (Gradle must be able to locate it)
- Android NDK installed (the build uses `ndkVersion` from `third_party/nextlib`)
- Android SDK commandline tools installed (`sdkmanager`), because `third_party/nextlib/ffmpeg/setup.sh` installs CMake.

The first build will download and compile FFmpeg for multiple ABIs via `third_party/nextlib/ffmpeg/setup.sh`.

If you previously built Next Player before enabling extra WMV/ASF codecs, delete:

- `third_party/nextlib/ffmpeg/output`
- `third_party/nextlib/ffmpeg/build`

and rebuild to regenerate FFmpeg with the new decoder set.

## Troubleshooting

- If a WMV plays video but no audio, confirm the audio codec is WMA and `Decoder priority` is not `Device only`.
- If a WMV doesn't show a thumbnail or media info, verify the codec is supported and that FFmpeg is built successfully.
