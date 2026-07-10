# Runtime video decoder switching

The player exposes three video decoder modes:

| Mode | Video renderer | Codec selection |
| --- | --- | --- |
| HW+ | `MediaCodecVideoRenderer` | Hardware and Android system software codecs |
| HW | `MediaCodecVideoRenderer` | Codecs reported as hardware accelerated |
| SW | `FfmpegVideoRenderer` | App-bundled FFmpeg codecs |

Changing this mode affects video only. The audio renderer is selected from the decoder preference
when the player service starts and remains unchanged for that player session.

## Request flow

1. `ControlsTopView` displays the current mode and opens `DecoderSelectorView`.
2. `DecoderState` sends `SET_DECODER_MODE` through the activity's `MediaController`.
3. `PlayerService` validates the command and delegates it to `DecoderSwitcher`.
4. `DecoderSwitcher` updates renderer capabilities and `DefaultTrackSelector` parameters.
5. Media3 remaps the video track to the enabled system or FFmpeg renderer.
6. `DecoderState` updates the UI after the service reports success.

The selected mode is session-scoped. It does not overwrite the decoder preference in DataStore.
That preference only determines the initial video and audio renderer choices for a new service.

## Why both capabilities and disabled renderers change

Media3 maps each track group to a renderer before applying renderer-disabled flags. Disabling a
renderer alone can therefore leave a video track mapped to a renderer that cannot be selected.
`ModeAwareRenderer` makes inactive renderers report the format as unsupported, while the track
selector flags provide a second explicit guard. Updating the track selector parameters triggers a
fresh mapping pass without creating another `ExoPlayer`.

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

On-device verification should confirm that video decoder initialization changes after selecting a
new mode, audio decoder initialization does not repeat, playback position advances, and only one
`ExoPlayer` instance is initialized per session.
