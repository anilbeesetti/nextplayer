# Finished Video Progress Design

## Context

Issue #1817 reports that the media picker no longer shows a progress bar after a video has been watched to the end. Partial playback progress is still displayed.

The player persists `C.TIME_UNSET`, a negative value, when playback finishes. This is intentional: the absence of a resumable position makes a completed video start from the beginning when it is played again. In v0.16.3, `Video.playedPercentage` translated a negative persisted position to `1f`, so `VideoItem` rendered a full progress bar. The v0.17 media-state refactor made playback positions nullable and accidentally translated the finished sentinel to `null`; `VideoItem` interprets `null` as no progress to display.

## Design

Keep the persistence and Compose layers unchanged. Restore the finished-state translation in `Video.playedPercentage`:

- `null` playback position means the video has never been played and produces `null` progress.
- A non-negative playback position with a positive duration produces the existing position-to-duration ratio.
- A negative playback position with a positive duration represents completed playback and produces `1f` progress.
- A non-positive duration cannot produce meaningful progress and continues to produce `null`.

Because `VideoItem` already renders the progress bar whenever `playedPercentage` is non-null, a completed video will render a full-width bar without exposing the player sentinel to the UI.

## Testing

Add a JVM unit test in `core:model` that creates a video with a negative finished position and a valid duration, then asserts that `playedPercentage` is `1f`. Run it before the production change to confirm that it fails by returning `null`, then rerun it after the model change.

The focused module test suite and relevant compilation checks must pass. Existing behavior for never-played and partially played videos remains unchanged by the implementation.

## Alternatives Considered

- Persist the media duration at completion. Rejected because a completed video could be treated as resumable at its end instead of restarting from the beginning.
- Handle negative positions in `VideoItem`. Rejected because it would leak the player persistence sentinel into the Compose layer and duplicate model interpretation in UI code.
