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
- Selecting a Cast receiver pauses local playback and presents a connecting indication. The connecting presentation, successful handoff threshold and failure behavior still need decisions.

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

- The horizontal swipe action; Undo behavior is a subsequent decision if dismissal is selected.
- Connecting UI placement, readiness, cancellation and failure recovery.
- Cast handoff from externally launched video playback, notification re-entry and reconnection.
- Expanded controls appropriate to video playback and queue interactions.
- Full-screen return behavior, keyboard placement, locked vault screens and adaptive/accessibility behavior.

The interview must finish with a shared-understanding confirmation before application implementation.
