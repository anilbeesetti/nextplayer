# LocalVaultRepository Hide Workflow Readability

## Context

The issue 1828 fix made hiding videos failure-safe, but `LocalVaultRepository.hideVideos`
now interleaves four responsibilities:

1. Creating unique vault destinations.
2. Reserving database rows.
3. Moving the source files.
4. Reconciling database rows after success, failure, or cancellation.

The behavior is covered by repository and media-operation instrumentation tests. This
refactor must improve readability without changing those guarantees.

## Goals

- Make `hideVideos` read as a short sequence of named workflow phases.
- Keep every implementation detail private to `LocalVaultRepository`.
- Make move completion, failure, and cancellation explicit rather than representing
  all unknown results with a nullable map at the orchestration level.
- Preserve the existing single batched media permission request.
- Preserve source files whenever their corresponding hide operation does not commit.

## Non-goals

- No database schema or DAO changes.
- No changes to `MediaOperationsService`.
- No new injected classes or public abstractions.
- No changes to vault restore behavior.
- No changes to user-visible behavior.

## Design

### Public orchestration

`hideVideos` will contain only the workflow:

1. Reserve the requested videos.
2. Return when no reservation succeeded.
3. Move all reserved videos in one batch.
4. Reconcile reservations from the explicit move outcome.
5. Rethrow cancellation only after reconciliation.

This keeps the safety ordering visible without exposing implementation details.

### Reservation phase

`reserveVideos` will own the reservation loop and cancellation cleanup.
`reserveVideo` will:

- create a UUID-backed destination,
- create the `HiddenVideoEntity`,
- insert the row,
- return a `HideReservation` on success, or
- remove a possibly committed row by vault path and skip the video on an ordinary
  insertion failure.

If reservation is cancelled, `reserveVideos` will remove every attempted reservation
in a non-cancellable cleanup block before rethrowing.

### Move phase

`moveReservedVideos` will call `MediaOperationsService.moveMedia` once for the entire
batch and convert its result into a private sealed `MoveOutcome`:

- `Completed` contains the per-URI move results.
- `Failed` represents an exception with an unknown partial result.
- `Cancelled` retains the original `CancellationException`.

The outcome makes control flow explicit while keeping exception handling out of the
public orchestration method.

### Reconciliation phase

`reconcileReservations` will accept `MoveOutcome`.

- For `Completed`, it will retain only rows whose exact destination was confirmed.
- For `Failed` or `Cancelled`, it will retain rows whose destination exists because a
  move may have committed before the exception became observable.
- It will delete all other reserved rows in a non-cancellable cleanup block.

After reconciliation, `Cancelled` will rethrow its original exception. Other outcomes
will return normally, matching current behavior.

## Verification

The existing repository instrumentation tests remain the behavioral contract:

- database insertion failure preserves the source,
- matching source names use different vault files,
- move failure preserves the source and removes the reservation,
- insertion cancellation removes a possibly committed reservation,
- move cancellation preserves uncommitted sources and removes reservations.

Run:

```text
:core:data:connectedDebugAndroidTest
:core:media:connectedDebugAndroidTest
ktlintCheck
testDebugUnitTest
:app:assembleDebug
```

No production behavior or public API is expected to change.
