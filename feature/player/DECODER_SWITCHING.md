# Runtime decoder switching

The player exposes three decoder modes:

| Mode | Video renderer | Audio renderer |
| --- | --- | --- |
| HW+ | `MediaCodecVideoRenderer`, using hardware and Android system software codecs | `MediaCodecAudioRenderer` |
| HW | `MediaCodecVideoRenderer`, using hardware-accelerated codecs | `MediaCodecAudioRenderer` |
| SW | `FfmpegVideoRenderer` | `FfmpegAudioRenderer` |

Video and audio follow the selected mode. The **HW+ audio on SW video** setting changes only SW
mode: FFmpeg audio stays first, while `MediaCodecAudioRenderer` is kept second as a fallback when
FFmpeg does not support the audio format.

## Request flow

1. `ControlsTopView` displays the current mode and opens `DecoderSelectorView`.
2. `DecoderState` sends `SET_DECODER_MODE` through the activity's `MediaController`.
3. `PlayerService` validates the command and delegates it to `DecoderSwitcher`.
4. `DecoderSwitcher` updates renderer capabilities and `DefaultTrackSelector` parameters.
5. Media3 remaps video and audio tracks to the enabled system or FFmpeg renderers.
6. `DecoderState` updates the UI after the service reports success.

The selected video mode is session-scoped and starts as HW+ for every new player service. It is not
written to DataStore. The audio fallback setting is read when the player service is created.

## Why both capabilities and disabled renderers change

Media3 maps each track group to a renderer before applying renderer-disabled flags. Disabling a
renderer alone can therefore leave a video track mapped to a renderer that cannot be selected.
`ModeAwareRenderer` makes inactive renderers report the format as unsupported, while the track
selector flags provide a second explicit guard. Updating the track selector parameters triggers a
fresh mapping pass without creating another `ExoPlayer`.

FFmpeg extension renderers are ordered before system renderers. In SW mode, when both audio
renderers are enabled, `FfmpegAudioRenderer` is therefore selected first and
`MediaCodecAudioRenderer` remains available for unsupported formats. In HW and HW+ modes, FFmpeg
audio is disabled and system audio is selected directly.

`ModeAwareRenderer` extends Media3's `ForwardingRenderer`. This is important because every renderer
lifecycle and timing method, including Java interface default methods, must reach the actual
MediaCodec or FFmpeg renderer.

## HW+ to HW

HW+ may already be using an Android system software codec through `MediaCodec`. Moving to HW must
release that codec and query the filtered hardware-only list again. The switcher calls `stop()` and
`prepare()` on the same `ExoPlayer`; Media3 retains the playlist and playback position. Other mode
changes only require track reselection.

During this reset, `PlayerService` suppresses its normal idle-state parameter reset so preferred
tracks and renderer flags are preserved.

## Verification

Run the focused checks with:

```shell
./gradlew :feature:player:ktlintCheck :feature:player:testDebugUnitTest :app:assembleDebug
```

On-device verification should confirm that both video and audio follow the selected mode, playback
position advances, and only one `ExoPlayer` instance is initialized per session. In SW mode, also
verify an audio format unsupported by FFmpeg uses the system audio renderer only when HW+ audio
fallback is enabled.
