# Runtime decoder switching

NextPlayer uses nextlib to change video and audio decoders without replacing the active
`ExoPlayer`. NextPlayer owns the player controls, MediaSession commands, fallback policy, dialogs,
and analytics logs. Nextlib only creates the renderers and switches which decoder category is
eligible.

## Decoder modes

Video and audio use the same nextlib `DecoderMode`, but each track type has an independent selected
mode.

| Mode | Player label | Eligible decoder |
| --- | --- | --- |
| `null` | Automatic, internal only | Hardware MediaCodec, then system software MediaCodec, then bundled FFmpeg |
| `HARDWARE` | HW | MediaCodec decoders reported as hardware accelerated |
| `SOFTWARE` | SW+ | MediaCodec decoders reported as software-only |
| `APP_SOFTWARE` | SW | The FFmpeg renderer bundled with nextlib |

Both tracks start in automatic mode for every new media item. The decoder selector exposes only the
three explicit modes. While a track remains automatic, the controls show the decoder category that
Media3 actually initialized. Choosing a row changes that track from automatic selection to an
explicit mode. Modes are session state and are not stored in preferences.

## Player setup

`PlayerService` creates all three objects and attaches them after building the player:

```kotlin
val decoderManager = DecoderManager()
val renderersFactory = NextRenderersFactory(applicationContext).apply {
    setDecoderManager(decoderManager)
}
val trackSelector = DefaultTrackSelector(applicationContext)

val player = ExoPlayer.Builder(applicationContext)
    .setRenderersFactory(renderersFactory)
    .setTrackSelector(trackSelector)
    .build()

decoderManager.attach(player, trackSelector)
```

The manager must be set on the factory before the player is built. `PlayerService.onDestroy`
detaches the manager before releasing the player.

## Selection flow

The decoder overlay uses a segmented Video/Audio control and shows one decoder group at a time. Each
group contains HW, SW+, and SW options. Selecting a row keeps the overlay open and changes only the
visible track type:

1. `DecoderState` sends `SET_VIDEO_DECODER_MODE` or `SET_AUDIO_DECODER_MODE`.
2. `PlayerService` records that selection for app-owned recovery.
3. `PlayerService` calls `DecoderManager.selectVideoDecoder` or `selectAudioDecoder`.
4. nextlib updates renderer capabilities, codec filtering, and track selection.
5. nextlib's analytics listener updates `DecoderManager.videoMode` or `audioMode` with the decoder
   category that was initialized.
6. NextPlayer's `decoderAnalyticsListener` logs the requested mode, active mode, and decoder name.
7. `DecoderState` reads `GET_DECODER_STATE` and displays the active category in each decoder group.

Switching keeps the player instance, playlist, position, and `playWhenReady`. A change between
MediaCodec categories (automatic, `HARDWARE`, and `SOFTWARE`) may stop and prepare the same player
so an already-created codec is released. Moving between MediaCodec and `APP_SOFTWARE` normally
remaps the track to a different renderer.

There is no special SW-audio fallback preference. Video and audio remain independent, so selecting
SW video does not change an audio track that is still using automatic selection.

## Recovery ownership

nextlib does not listen for playback errors and has no fallback policy. `DecoderRecoveryManager` in
NextPlayer tracks video and audio attempts independently.

The policy for the affected track type is:

| Failed selection | Result |
| --- | --- |
| Automatic (`null`) | Silently try `APP_SOFTWARE` |
| Explicit `HARDWARE` | Show the unsupported dialog, then try `SOFTWARE` after OK; silently try `APP_SOFTWARE` if that fails |
| Explicit `SOFTWARE` | Show the unsupported dialog, then try `APP_SOFTWARE` after OK |
| Explicit `APP_SOFTWARE` | Show the unsupported dialog, then return to automatic mode after OK |
| Final fallback mode | Show the existing player error dialog |

A video failure does not consume the audio fallback attempt, and an audio failure does not consume
the video attempt.

`PlayerService` starts recovery for decoder-related `PlaybackException` values and for a present
video or audio track that none of the active renderers supports. Audio-only media therefore cannot
start video recovery, and video-only media cannot start audio recovery. Duplicate reports for the
same track and mode attempt are ignored.

After an actual player error, NextPlayer selects the fallback and calls `prepare()` because the
player is already idle with an error. For an unsupported track mapping, selecting the fallback is
enough for nextlib to invalidate track selection. Both paths keep the existing `ExoPlayer`.

The first decoder initialization for the recovering track clears the user-facing recovery state.
Non-decoder errors bypass decoder recovery and use the normal player error dialog.

## Media changes and analytics

Recovery uses the playlist index, media ID, and local URI as the media identity. A genuine new item
resets both requested modes to automatic; metadata replacement for the same item does not erase an
explicit selection. Clearing the playlist also allows the same URI to start with fresh decoder
state when it is opened again.

nextlib maps initialized decoder names to HW, SW+, or SW and exposes the active categories through
`DecoderManager`. NextPlayer's `decoderAnalyticsListener` records requested and active modes,
decoder names, track support, first-frame rendering, and player errors.

## Verification

Run focused automated checks:

```shell
./gradlew :feature:player:ktlintCheck :feature:player:testDebugUnitTest :app:assembleDebug
```

On a device or emulator, verify that automatic mode is not selectable, automatic tracks show the
initialized decoder category, video/audio selections remain independent, playback position is
unchanged, one player instance remains active, fallback dialogs work, and decoder names appear in
logcat.
Emulator-only FFmpeg visual corruption is not a release blocker when decoder selection, playback
state, recovery, and crash logs are otherwise correct.
