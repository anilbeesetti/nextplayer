# Global playback controls

Status: shared understanding confirmed by the user on 2026-11-10. Implementation is authorized as the third PR in the stack and will continue in a fresh thread.

## Scope

This is the third layer above Cast support (#2086) and the home mini player (#2087), on `cast/global-playback-controls`.

The existing high-bitrate Camera casting limitation remains unresolved and is outside this UI layer's completion claim.

## Confirmed decisions

- Show the mini player on browsing and settings screens, including folders, search, playlists and network screens.
- Hide the compact view while the full-screen video player or expanded player is open.
- Tapping the mini player expands controls for both local and Cast playback. The local expanded player has a separate Watch video action; it does not embed another video surface.
- Use PixelPlayer's layout, UI structure and motion as a reference, with Next Player's colors, typography and icons. The visual refinement uses its filled circular compact buttons, large capsule transport controls, segmented secondary actions, square artwork and wavy seek track. Verify future UI changes on the existing Pixel_6a emulator, as requested by the user.
- Swiping the compact bar left or right stops playback and dismisses the session, including ending Cast playback on the receiver. Previous/next remain explicit buttons.
- Swipe dismissal offers Undo for four seconds to restore the queue and position. Undo of local playback restores its prior playing/paused state. Undo of Cast playback restores the queue on the phone paused at the last known position; it does not reconnect the receiver automatically.
- Selecting a Cast receiver immediately pauses phone playback, leaves full-screen video and opens the expanded player with the receiver name, Connecting indication and Cancel action.
- Cast controls become active only after the receiver acknowledges the selected video and its controls are available. A loaded, paused video counts as ready; receiver session connection alone does not.
- Failed or cancelled connections return playback to the phone at the same position and restore its previous playing/paused state.
- The compact bar has square artwork with softly rounded corners, video title, folder/device text and previous/play-pause/next buttons.
- The expanded player adds large artwork, seek/time, shuffle/repeat, queue, output/volume, Stop, and Watch video for local output or Play on phone for Cast. Music-specific lyrics and album navigation are outside this PR.
- Back from full-screen video opened through Watch video returns to the expanded player and keeps playback active. Direct video launches keep their existing Back/background-playback behavior.
- Videos opened from Files or another app also transition into Next Player's expanded Connecting/Cast controls when a receiver is selected.
- Tapping the active playback notification opens expanded controls for both local and Cast output.
- Unexpected Cast loss retains the queue and last known position, leaves phone playback paused, and presents connection-lost recovery with Retry and Play on phone.
- Playlist creation opens a full-screen form that animates in from below and covers the compact player and app navigation. Playback continues underneath, and the form handles its keyboard insets. The underlying browsing screen keeps the mini player above bottom navigation.
- The playback overlay persists above the browsing navigation host. It does not leave or move with individual screen navigation transitions.
- Follow PixelPlayer's visibility pattern rather than interpreting all screens literally. Visibility for Next Player-specific screens is delegated to implementation judgment. Do not impose a global policy that floats the mini player above every keyboard or modal form.
- Hide compact and expanded playback surfaces while the vault PIN/unlock screen is visible; retain existing vault playback and lock behavior otherwise.
- Swiping up from fully expanded controls opens the existing queue with select, reorder and remove actions. Keep the cannot-remove-final-item rule and leave new clear/save-as-playlist actions out of this PR.
- Horizontal swipes on expanded artwork select the previous/next video with a carousel animation. The compact bar's horizontal swipe remains stop-and-dismiss.

## Reference observations

PixelPlayer was inspected at `63cb59b97aff1d60c5aa46afdf5ce489e632b267`. These are reference facts, not automatically accepted Next Player requirements.

- Tap or upward drag expands the compact player. Downward drag, the collapse action and Back collapse the expanded player.
- The compact player shows circular artwork, title/secondary text and previous/play-pause/next buttons.
- Horizontal dragging in either direction dismisses the compact player and stops/clears the session. It does not select the previous or next queue item. A separate Undo action follows dismissal.
- The global sheet remains mounted when the keyboard appears.
- Music-specific features include artist/album navigation, lyrics and an artwork carousel.
- Library playlist creation is a full-window opaque dialog/form with an upward slide/fade. It covers the still-mounted player and app navigation independently of keyboard visibility. Other playlist entry points use different dialog/sheet presentations.
- The main player has no explicit IME lift or hide-on-keyboard policy. Search content applies keyboard padding separately; exact mini positioning also depends on Android window resizing.
- Undo records queue/current item/position without the old receiver or playing/paused intent. It dispatches ordinary playback and does not reconnect the previous receiver; the normal path after a completed Cast disconnect resumes on the phone.

Source and assets will not be copied. The reference repository's license restricts redistribution and derivative works:
https://github.com/PixelPlayerHQ/PixelPlayer/blob/63cb59b97aff1d60c5aa46afdf5ce489e632b267/LICENSE

## Approval

- The user confirmed the full design and requested implementation in a new thread with a focused context handoff. Do not restart the design interview or request the same confirmation again.

## Delegated presentation defaults

- Keep the current browsing screen and compact/expanded presentation during automatic Cast recovery. Expand for an explicit receiver selection, Retry or player/notification tap.
- Tap/upward drag expands, downward drag/collapse/Back minimizes, and gestures follow the finger before settling. Dismissing a child queue or output sheet precedes collapsing the expanded player.
- Keep the playback overlay stable through navigation; foreground forms can cover it. Use normal window/inset behavior in ordinary text-entry screens rather than an additional forced IME lift.

## Implementation and verification constraints

- Support portrait/landscape, window-size changes and both bottom navigation and navigation rail layouts.
- Keep controls reachable with accessibility services, large text, RTL and TV/D-pad input. Provide button equivalents for touch gestures and respect system motion settings.
- Reserve space on nested screens so the compact bar does not cover content or form controls.
- Preserve URI access and playback state across activity handoffs and external launches.
- Preserve existing external player API result behavior.
- Honor available playback commands; unavailable queue, seek and output controls must not issue unsupported commands.
- Verify behavior in both distributions, with Cast implementation confined to `play`.

## Acceptance coverage

- Navigate across root tabs, folders, search, playlists, network and settings while the overlay stays independent of screen transitions and content remains unobscured.
- Expand/collapse with touch and buttons; open the queue; distinguish compact dismiss gestures from expanded artwork selection; exercise Back and Watch video return behavior.
- Verify four-second Undo for previously playing and paused local sessions, and paused local restoration after Cast dismissal, without reviving an old receiver session.
- Exercise Cast selection, connecting cancellation/failure, acknowledged paused/playing media, unexpected disconnect, explicit Retry and Play on phone.
- Repeat Cast handoff for videos opened inside Next Player, external video intents and notification re-entry, preserving position and caller compatibility.
- Check playlist creation covering the overlay, text-entry insets, vault PIN exclusion, rotation/window changes, RTL, large text, motion settings and D-pad focus.
- Run applicable unit, integration, build and formatting checks for both distributions; validate flows on an emulator and on the connected phone/receiver when available.
- Keep the existing high-bitrate Camera buffering failure visible in verification results; do not claim this UI work fixes it.

The design interview is complete. Implement and verify the agreed behavior, then open the third PR above #2086 and #2087.

## Playback motion follow-up (2026-11-10)

The user requested these refinements after using the installed build. They supersede the earlier circular-artwork and pause-scale reference observations:

- Read seek progress at fine media-time resolution so 10–20-second videos advance smoothly; keep pause, seek and Cast connection-loss positions authoritative.
- Page between stable queue occurrences with adjacent artwork ready. Metadata enrichment must not restart a selection transition or show a blank artwork surface.
- Keep compact artwork square. Move one artwork image and coordinated text continuously into/out of the expanded layout, including interrupted drags, RTL and system motion settings.
- Keep compact playback mounted through browsing changes. Animate navigation/rail space and its anchor together without recreating browsing entry decorators on every frame.
- Use a single queue sheet with a Next up header, compact artwork rows, durations, selection, reorder and removal. Keep command gating and the final-item removal guard; no clear/save/music actions are added.
- Do not scale artwork when playing or pausing.

## Queue and dismissal follow-up (2026-11-11)

- Compact horizontal dismissal shows a rounded removal reveal, follows the finger, and settles offscreen; cancellation returns it without stopping playback. Retain both directions and four-second Undo. A replacement queue must survive an older dismissal animation.
- Expanded queue rows use the six-dot drag indicator, larger square artwork and a high-contrast duration badge on the image. Unknown durations keep the existing marker.
- End-to-start swipes remove queue occurrences, with the compact player's resistance, spring and single long-press haptic when the swipe breaks free. RTL reverses the gesture. Keep accessible button alternatives, command gating and the final-item guard; the drag handle must not start removal.
- Keep reorder feedback synchronized with the observed media queue before accepting another move. Removing the current final row selects the previous video first so earlier items remain active, preserving playing/paused intent; require the necessary seek command.
- Nested browsing/settings/form backgrounds remain full-height and scroll behind the compact overlay. Apply its reservation to scroll content and bottom actions, counting system bars once; hide the collapsed tab bar's gradient with its drawing, without changing the persistent overlay's mounting.
