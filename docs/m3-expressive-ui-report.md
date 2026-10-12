# Material 3 Expressive player UI — implementation report

Date: 2026-10-12
Branch: `arena/f311a343-graviton` (based on `70bf4c7`)
Verified head: `388c3e2` — Gradle exit `0`, 62 unit tests run / 0 failed, ktlint clean.

Scope honoured: UI/UX and playback-control interactions only. No player engine, engine abstraction or
playback-architecture change was made; the existing Media3 session, queue, repository and navigation
code paths are untouched. No dependency was added or bumped.

## 1. Mini player — dismissal and reopen

**Visible dismiss button.** The bar now ends in a Material `IconButton` with `NextIcons.Close` and
the string `mini_player_dismiss`. It is always present, at the standard 48 dp touch target, so
dismissal is never a swipe-only secret.

**Swipe down to dismiss.** `MiniPlayerSwipeToDismiss` (`NowPlayingMiniBar.kt`) runs in its own
`pointerInput` and only claims the pointer after the vertical touch slop has been passed, so taps
reach the row's buttons and horizontal drags stay with whatever is underneath. While claimed:

* the bar follows the finger one-to-one (`graphicsLayer.translationY`),
* it fades with the distance travelled, down to 45 % opacity,
* upward drags are never consumed — pulling up still scrolls the content behind the bar,
* a release settles back with a low-bounce spring (`DampingRatioLowBouncy`, `StiffnessMediumLow`),
* a tap or a cancelled drag leaves no offset behind.

`MiniPlayerDismissGesture` is the pure-arithmetic part of the decision: dismiss when the bar has
travelled **more than 42 % of its height**, or was **thrown faster than 900 dp/s** after at least
10 % of travel (the second rule makes a quick flick work, the travel floor keeps a stray fast tap
from dismissing it).

**Hiding is UI-only.** `MiniPlayerVisibilityState` (`isDismissed`, `dismiss(mediaId)`, `show()`,
`syncWithMedia(mediaId)`) is remembered by `ResponsiveNavigationSceneDecorator`, so it survives
navigation and is shared by all three layouts. Nothing in it touches the session, the queue or the
position: playback keeps running and a resume/next item brings the bar back on its own.

**Getting it back.** A `MiniPlayerReopenButton` (M3 `SmallFloatingActionButton`, `mini_player_reopen`,
music/film icon depending on the item) fades and scales in while the bar is hidden and something is
playing. It is placed in the content pane's bottom-end corner with `navigationBarsPadding()`, so it
clears system bars and never covers the navigation suite.

**Surfaces and spacing.** `surfaceContainerHigh`, 16 dp top corners with square bottom corners so the
bar and a `NavigationBar` read as one stack, tonal elevation 3 dp, 44 dp artwork with a 10 dp corner,
a 2 dp progress line, and 8–12 dp inner spacing. In the phone layout the bar sits directly above the
navigation bar; with a rail it anchors to the bottom of the content pane.

## 2. Long-press seeking

**Preference.** `PlayerPreferences.longPressGesture` (`SEEK`, `SPEED_BOOST`, `NONE`) plus the
`longPressAction` extension, which keeps the existing `useLongPressControls` master switch working:
turning long press off ignores the action. `SEEK` is the default, `SPEED_BOOST` preserves the
original hold-to-speed-up behaviour, and the gesture settings screen gained a radio dialog
(hold speed slider appears only for `SPEED_BOOST`). The tutorial sheet and the settings description
follow the chosen action.

**Maths (`HoldSeek`).** Holding a side seeks by the configured increment immediately, then ramps:
3 → 5 → 10 → 20 → 40 media-seconds per second of holding, one step every 800 ms, capped at
`MAX_DELTA_MS` (30 minutes) per hold. Presses inside the middle 20 % never seek, and an unmeasurable
surface (width 0) does not either.

**State (`LongPressSeekState`).** Moves the player every 120 ms with Media3 scrubbing mode enabled,
so the picture tracks the gesture instead of jumping on release; `stop()` lands exactly on the
timestamp the overlay showed, and `cancel()` ends a hold that was stolen (PiP, media change) without
moving further. The last values are deliberately kept so the exit animation has something to draw.

**Overlay.** `LongPressSeekIndicator` places the shared `SeekFeedbackCard` on the side being held
(`CenterEnd` forward, `CenterStart` rewind) — never under the finger. The card is a 22 dp rounded
translucent black surface (55 %) with a hairline white border for legibility over bright frames, and
it carries

* three animated directional chevrons (phase-staggered, mirrored for a rewind),
* the signed amount in `titleMedium` (`+10 seconds` / `−10 seconds`),
* the resulting timestamp in `labelMedium`, updated continuously while held.

It fades and scales in/out, is inset by `safeDrawingPadding()` plus 48/64 dp so it clears cutouts,
system bars, the control strips and the subtitle band, and its two lines merge into one TalkBack
announcement. Under reduced motion (animator duration scale 0) the chevrons hold still.

**Haptics.** One `LongPress` tick when the hold starts and a `TextHandleMove` tick per acceleration
tier, so the rising speed is felt as well as seen. (All haptics run through `LocalHapticFeedback`,
so they follow the system's touch-feedback setting.)

**Non-conflicts.** A hold never starts on a tap, a scroll or a double-tap burst; locked controls
disable it; `onPress` finalises via `tryAwaitRelease()` so a stolen gesture cannot leave a hold live;
a hold that just ended does not toggle the controls; and every scrub/volume-brightness/pinch callback
re-reads `isLongPressGestureInAction || longPressSeekState.isActive` at call time, so a hold owns the
finger for its whole life.

## 3. Double-tap seeking

**Maths (`DoubleTapSeek`).** The outer 40 % of the width on each side seeks, the middle 20 % stays
for play/pause and the controls. Taps inside an 800 ms window accumulate from the position the burst
started at, so five forward taps land five increments ahead — not drifting by one increment per tap.
Turning around mid-burst re-anchors at the current position. Targets are clamped to
`[0, duration]`, and an unknown duration only bounds the start.

**Increments.** Driven by the existing seek-increment preference (slider 1–60 s, default 10 s), which
includes the 5/10/15/30 s stops; changing it applies to the next tap without recreating any state.

**Indicator (`DoubleTapIndicator`).**

* a drawn radial ripple (primary tint + white ring) that restarts at the point actually tapped,
* a translucent card with the same visual language as the hold overlay, aligned to the matching side
  (`CenterEnd` forward, `CenterStart` rewind) so subtitles and the picture centre stay clear,
* a 0.92 → 1.0 scale/fade in over 120–180 ms and a 0.96 scale/fade out over 220 ms,
* the readout outlives the burst by 1.3 s (`INDICATOR_DWELL_MS`) so the final amount is readable;
  another tap restarts that dwell instead of flickering,
* a burst that a boundary clamped to zero shows nothing rather than a meaningless “0 seconds”.

A double tap with the gesture disabled (or an unbound centre) now leaves no trace at all.

## 4. Loading, buffering and errors

`BufferingIndicator` was rebuilt around a 44 dp Material 3 `CircularProgressIndicator` (4 dp rounded
stroke) on a 72 dp translucent disc: the disc — not a full-screen scrim — is what keeps the ring
readable over a bright frame, so the video is never dimmed and controls/subtitles stay visible. The
pill label below optionally carries the real buffered percentage; progress is still only shown when
the pipeline can actually measure it.

* 180 ms show delay, so a stall shorter than a blink never draws anything;
* 200 ms minimum on-screen time, so repeated stalls do not strobe;
* `INITIAL_LOADING` (item still opening) fades in over 320 ms; `BUFFERING` (mid-playback rebuffer)
  rises with a 160 ms fade + scale from 0.9;
* reduced motion drops both transitions entirely;
* glass theme: the disc and pill use `glassContainerColor()`/`glassBorderColor()`.

Errors never leave a spinner behind: the indicator is gated on `errorState.error == null`, and the
player's error dialog offers **Retry** (`prepare()` + `play()`), **Play next video** when the queue
has one, and exit. Underlying buffering/playback state logic was not changed.

## 5. Material 3 Expressive consistency

* Control strips: the scrim fades over 200 ms; the top and bottom bars fade in over 180 ms while
  sliding 10 % of their height (220 ms, `FastOutSlowInEasing`) and fade out over 140 ms.
* `PlayerButton`'s Material You branch now uses the M3 `FilledTonalIconButton` defaults — the
  hard-coded 40 dp size is gone, so the container keeps its intended size and the 48 dp minimum
  touch target applies. The classic branch is unchanged.
* The D-pad seek overlay (TV) reuses `SeekFeedbackCard` instead of its own ad-hoc dark box, so every
  seek — gesture, tap burst or D-pad — looks and reads the same.
* Strings live in `core/ui/src/main/res/values/strings.xml`; icon-only controls carry
  `contentDescription`s and decorative elements pass `null`; the seek overlays are inset for
  cutouts/system bars and work in portrait, landscape and immersive fullscreen.

## Files

| Module | File | Change |
| --- | --- | --- |
| app | `navigation/ResponsiveNavigationSceneDecorator.kt` | owns the mini-player visibility state, hosts the bar and the reopen FAB in all three layouts |
| core:model | `model/LongPressGesture.kt` | new enum + `longPressAction` extension |
| core:model | `model/PlayerPreferences.kt` | `longPressGesture` preference |
| core:ui | `components/BufferingIndicator.kt` | rewritten indicator (kinds, timing, reduced motion, glass) |
| core:ui | `res/values/strings.xml` | new strings (dismiss/reopen, long-press seek, seek amounts, tap/long-press actions) |
| feature:music | `NowPlayingMiniBar.kt` | rewritten bar, visibility state, swipe-to-dismiss, reopen FAB, `MiniPlayerDismissGesture` |
| feature:music | `test/.../MiniPlayerDismissGestureTest.kt` | new unit tests for the dismiss thresholds |
| feature:player | `MediaPlayerScreen.kt` | indicator wiring, buffering kinds and error gating, error retry, control transitions, D-pad overlay |
| feature:player | `PlayerContentFrame.kt` | passes the hold-seek state and action through |
| feature:player | `ui/PlayerGestures.kt` | long-press action dispatch, hold ownership, release finalisation, tier haptics |
| feature:player | `ui/DoubleTapIndicator.kt` | rewritten (ripple + card + dwell) |
| feature:player | `ui/LongPressSeekIndicator.kt` | new hold overlay |
| feature:player | `ui/SeekFeedback.kt` | new shared card/chevrons/labels |
| feature:player | `state/LongPressSeekState.kt` | new hold-seek state |
| feature:player | `state/HoldSeek.kt` | new seek-ramp maths |
| feature:player | `state/DoubleTapSeek.kt` | new region/burst maths |
| feature:player | `state/TapGestureState.kt` | burst accumulation from the burst anchor, target clamping, disabled-gesture short circuit |
| feature:player | `buttons/PlayerButton.kt` | M3 touch-target fix |
| feature:player | `ui/sheets/TutorialSheet.kt` | long-press entry follows the configured action |
| feature:player | `PlayerActivity.kt` | unresolved-reference fix on the network-stream path |
| feature:player | `test/.../HoldSeekTest.kt`, `test/.../DoubleTapSeekTest.kt` | new unit tests |
| feature:settings | `screens/gesture/GesturePreferences{Screen,ViewModel}.kt`, `extensions/LongPressGesture.kt` | long-press action dialog and labelling |

## Verification

The sandbox this work was produced in has no JDK, no Android SDK and no route to Maven Central, so
`./gradlew` cannot run locally. Verification was done through a temporary GitHub Actions workflow on
the branch (`.github/workflows/arena-verify.yml`, commit `5817d40`) that runs

```
./gradlew :app:assembleDebug \
          :feature:player:testDebugUnitTest \
          :feature:music:testDebugUnitTest \
          :core:ui:testDebugUnitTest \
          ktlintCheck
```

and publishes the result as check annotations plus a commit comment
(`.github/scripts/arena_verify_digest.py`), which now also reports unit-test totals parsed from the
JUnit XML reports — necessary because the root build sets `Test.ignoreFailures = true`, so a zero
exit status alone does not prove the tests passed.

Result on the final head `388c3e2`: **Gradle exit 0, 62 unit tests run, 0 failed, 0 errored,
0 skipped, ktlint clean.**

The loop caught and fixed real defects before this report:

* unresolved references in `MediaPlayerScreen.kt` and `PlayerActivity.playNetworkStream()`,
* `Restricted suspending functions can invoke member or extension suspending functions only on their
  restricted coroutine scope` — the swipe handler now drives its offset through a plain animated
  `Float` state instead of a foreign `Animatable`,
* a hold-seek unit test that caught a units bug in the acceleration ramp (rate applied per
  millisecond rather than per second).

New tests: `HoldSeekTest` (first step equals the increment, monotonic acceleration, tier boundaries,
cap, direction/dead-zone), `DoubleTapSeekTest` (regions, clamping, unknown duration),
`MiniPlayerDismissGestureTest` (distance, flick, minimum travel, upward and unmeasured cases).
Existing `PlayerActivityTest` and `HoldSpeedGestureTest` still pass.

## Unresolved issues / follow-ups

* **No on-device verification was possible here** (no emulator or device). Gesture feel, haptics and
  the TV focus order should be checked on a phone, a tablet in landscape and a TV before release;
  the unit tests pin the arithmetic, not the feel.
* The seek increment stays a continuous 1–60 s slider (default 10 s) rather than four fixed preset
  buttons; 5/10/15/30 s are all reachable, but a preset row was left out to avoid reworking a shared
  settings component without visual review.
* The mini player keeps its previous/next buttons for music; on very narrow screens the title
  ellipsizes sooner than before. Hiding those buttons behind a width breakpoint is a possible
  follow-up.
* `main`'s own CI was already failing before this work (run 37208273453, "Build Release APK"); the
  cause was not reproduced here and is unrelated to these changes.
* The `Arena Verify` workflow and digest script were added for this sandbox's verification loop and
  can be deleted before merging if the regular CI is preferred.
