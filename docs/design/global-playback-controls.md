# Global playback controls

Status: design interview in progress. Application implementation has not started.

## Scope

This is the third layer above Cast support (#2086) and the home mini player (#2087), on `cast/global-playback-controls`.

The existing high-bitrate Camera casting limitation remains unresolved and is outside this UI layer's completion claim.

## Confirmed decisions

- Show the mini player on browsing and settings screens, including folders, search, playlists and network screens.
- Hide the compact view while the full-screen video player or expanded player is open.
- Tapping the mini player expands controls for both local and Cast playback. The local expanded player has a separate Watch video action; it does not embed another video surface.
- Use PixelPlayer's layout, UI structure and motion as a reference, with Next Player's colors, typography and icons.
- Swiping the compact bar left or right stops playback and dismisses the session, including ending Cast playback on the receiver. Previous/next remain explicit buttons. Undo is still an open decision.
- Selecting a Cast receiver immediately pauses phone playback, leaves full-screen video and opens the expanded player with the receiver name, Connecting indication and Cancel action.
- Cast controls become active only after the receiver acknowledges the selected video and its controls are available. A loaded, paused video counts as ready; receiver session connection alone does not.
- Failed or cancelled connections return playback to the phone at the same position and restore its previous playing/paused state.
- The compact bar has circular artwork, video title, folder/device text and previous/play-pause/next buttons.
- The expanded player adds large artwork, seek/time, shuffle/repeat, queue, output/volume, Stop, and Watch video for local output or Play on phone for Cast. Music-specific lyrics and album navigation are outside this PR.
- Back from full-screen video opened through Watch video returns to the expanded player and keeps playback active. Direct video launches keep their existing Back/background-playback behavior.

## Reference observations

PixelPlayer was inspected at `63cb59b97aff1d60c5aa46afdf5ce489e632b267`. These are reference facts, not automatically accepted Next Player requirements.

- Tap or upward drag expands the compact player. Downward drag, the collapse action and Back collapse the expanded player.
- The compact player shows circular artwork, title/secondary text and previous/play-pause/next buttons.
- Horizontal dragging in either direction dismisses the compact player and stops/clears the session. It does not select the previous or next queue item. A separate Undo action follows dismissal.
- The global sheet remains mounted when the keyboard appears.
- Music-specific features include artist/album navigation, lyrics and an artwork carousel.

Source and assets will not be copied. The reference repository's license restricts redistribution and derivative works:
https://github.com/PixelPlayerHQ/PixelPlayer/blob/63cb59b97aff1d60c5aa46afdf5ce489e632b267/LICENSE

## Open decisions

- Undo availability; restoration details are subsequent decisions if Undo is selected.
- Cast handoff from externally launched video playback, notification re-entry and reconnection.
- Queue interactions, keyboard placement, locked vault screens and adaptive/accessibility behavior.

The interview must finish with a shared-understanding confirmation before application implementation.
