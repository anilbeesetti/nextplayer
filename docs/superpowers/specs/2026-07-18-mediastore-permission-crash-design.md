# MediaStore Permission Crash Design

## Problem

`MediaStoreMediaService.fetchVideos()` calls `ContentResolver.query()` directly. The provider throws `SecurityException` when media permission is missing or has been revoked. The current UI-level permission gate does not protect every consumer, and it cannot prevent a permission-revocation race between checking permission and querying MediaStore.

The reported stack frame maps to the unguarded query in `fetchVideos()`. Because `fetchFolders()` derives its result by calling `fetchVideos()`, the same exception also terminates folder observation flows.

## Desired Behavior

When MediaStore access is unavailable, video and folder queries return empty lists until permission is restored. The app remains alive and its existing permission UI can guide the user. Exceptions unrelated to permission continue to propagate so programming and provider errors are not hidden.

## Design

Handle the failure at the MediaStore service boundary, which is shared by all current and future callers. Introduce a small internal query runner that executes a supplied MediaStore operation and converts only `SecurityException` into an empty list. `fetchVideos()` will execute the complete provider query and cursor traversal through this runner.

`fetchFolders()` requires no special handling because it already derives folders from the list returned by `fetchVideos()`. When access is unavailable, it naturally derives an empty folder list.

The existing permission checks in the media-picker feature remain useful for controlling requests and UI state, but they are not relied on as the final safety boundary.

## Error Handling

- Catch only `SecurityException` from the MediaStore operation.
- Return an empty immutable list for that exception.
- Allow all other exception types to propagate unchanged.
- Do not retry because permission restoration requires a user or system state change; existing reactive queries can run again after a later MediaStore signal or collection restart.

## Testing

Add focused JVM regression tests around the internal runner:

1. A MediaStore operation throwing `SecurityException` returns an empty list.
2. A successful operation returns its videos unchanged.
3. A non-security exception still propagates.

Run the focused core-media tests, then compile the app to verify integration.

## Scope

This change is limited to read queries performed by `MediaStoreMediaService.fetchVideos()`. It does not redesign permission UI, add retries, or suppress failures from unrelated media operations.
