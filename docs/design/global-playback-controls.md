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

- Q11: Four-second Undo for swipe dismissal. Output restoration is a subsequent decision if Undo is selected.
- Q12: Open expanded Connecting/Cast controls when playback originated in another app.
- Q13: Open expanded controls when the playback notification is tapped.
- Q14: Unexpected Cast loss keeps the last queue/position paused locally and offers recovery, or immediately resumes on the phone.
- Q15: Keep the mini player above the keyboard, or hide it while the keyboard is open.
- Q16: Hide playback surfaces while the vault PIN/unlock screen is visible, or keep them visible there.
- Q17: Swipe up from fully expanded controls opens the existing queue with select/reorder/remove, retaining the cannot-remove-final-item rule and excluding new clear/save actions.
- Q18: Horizontal swipes on expanded artwork select adjacent videos with a carousel animation, or remain button-only.
- Recovery/reconnection presentation follows the selected disconnect behavior.

## Implementation and verification constraints

- Support portrait/landscape, window-size changes and both bottom navigation and navigation rail layouts.
- Keep controls reachable with accessibility services, large text, RTL and TV/D-pad input. Provide button equivalents for touch gestures and respect system motion settings.
- Reserve space on nested screens so the compact bar does not cover content or form controls.
- Preserve URI access and playback state across activity handoffs and external launches.
- Verify behavior in both distributions, with Cast implementation confined to `play`.

The interview must finish with a shared-understanding confirmation before application implementation.
