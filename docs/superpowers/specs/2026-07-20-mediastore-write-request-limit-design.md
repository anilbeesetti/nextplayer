# MediaStore Write Request Limit Design

## Problem

`LocalMediaOperationsService.moveMedia()` collects every MediaStore URI in a move and passes the complete list to `MediaStore.createWriteRequest()`. Android rejects requests containing more than 2,000 URIs with `IllegalArgumentException`, so a move of 2,001 or more MediaStore items crashes before the system consent dialog can be shown.

The same service already avoids this platform limit for delete requests through `runMediaRequests()`, but write-access requests bypass that runner.

## Desired Behavior

Moves containing more than 2,000 MediaStore URIs request write access in sequential batches of at most 2,000. Once every batch is approved, the existing file move runs unchanged. If any batch is cancelled or rejected, the operation stops requesting further batches and reports every requested move as unsuccessful, matching the current cancellation behavior.

Duplicate URIs should not produce duplicate consent entries. Media items that disappear before the request is created should be removed from the affected batch and the request retried, matching delete-request handling.

## Approaches Considered

1. **Reuse `runMediaRequests()` for write access (selected).** This applies the existing, tested 2,000-item batching, deduplication, stale-item filtering, and early-stop behavior to write requests with a small service change.
2. **Add write-specific batching inside `requestWriteAccessR()`.** This would work but duplicate the request runner's batching and error-handling logic.
3. **Reject moves over 2,000 items.** This avoids the crash but needlessly prevents a supported operation and provides worse behavior than sequential system dialogs.

## Design

Route `requestWriteAccessR()` through `runMediaRequests()`. The runner receives the requested URIs, uses the existing `mediaExists()` provider query when recovering from `IllegalArgumentException`, and invokes a new single-batch suspending function that launches one `MediaStore.createWriteRequest()` consent dialog.

The runner processes each batch sequentially, so the service's existing single pair of activity-result callbacks cannot be overwritten by concurrent dialogs. `moveMedia()` continues to wait for the aggregate Boolean result before renaming any files.

No changes are needed to `launchWriteRequest()`, delete handling, rename handling, or the move result type.

## Error Handling

- A cancelled consent dialog returns `false`, stops later batches, and preserves the current all-failed move result.
- An `IllegalArgumentException` caused by stale URIs triggers the runner's existing existence check and retries only the remaining items.
- An `IllegalArgumentException` where every URI still exists returns `false` rather than crashing.
- Coroutine cancellation continues to cancel the suspended request through `suspendCancellableCoroutine`.

## Testing

Extend the JVM tests for `runMediaRequests()` with a write-access-shaped regression test proving that 2,001 URIs produce sequential batches of 2,000 and 1, and that both approvals are required for success. Existing runner tests continue to cover deduplication-adjacent batching, stale-item recovery, and provider rejection.

Run the focused runner tests, the complete core-media unit test suite, and app Kotlin compilation.

## Scope

This change is limited to write access requested by `moveMedia()` on Android 11 and later. It does not redesign the consent UI, partially move files after cancellation, or alter copy/move behavior through the Storage Access Framework.
