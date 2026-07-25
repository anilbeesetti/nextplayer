# Issue 1830: Vault Sorting Design

## Problem

The Secret Vault displays hidden videos using the sort value captured when its collection starts. Selecting another sort option updates `VaultUiState.sort`, but does not restart or reactively update that collection, so the visible order remains unchanged.

The vault also starts with a hard-coded date-descending sort instead of inheriting the app-wide sort stored in the preferences datastore.

## Design

`VaultViewModel` will distinguish between the app-wide default and a vault-session override:

- Before the user changes the vault sort, preference emissions set the vault sort from `ApplicationPreferences.sortBy` and `sortOrder`.
- Once the user selects a vault sort, that selection remains local to the current `VaultViewModel` session and later preference emissions do not replace it.
- Updating the vault sort while the vault is unlocked restarts the hidden-video collection with the new sort, immediately reordering the displayed list.
- Vault sort changes are not written back to the datastore.

This keeps the change inside the existing screen-logic boundary and preserves the current `GetHiddenVideosUseCase` contract.

## Data Flow

1. The preferences repository emits the app-wide preferences.
2. The view model copies the preferences into UI state and, if no vault-session override exists, derives `VaultUiState.sort` from them.
3. Unlocking the vault starts hidden-video collection with the current sort.
4. A `VaultAction.UpdateSort` marks the session as overridden, updates `VaultUiState.sort`, and restarts collection when the vault is unlocked.
5. `GetHiddenVideosUseCase` applies the requested comparator and emits the reordered list.

## Testing

A ViewModel regression test will use a repository flow containing videos whose title and size orders differ. It will verify that:

- the initial unlocked list uses the app-wide datastore sort; and
- dispatching `VaultAction.UpdateSort` changes the visible order without modifying app-wide preferences.

Targeted feature tests and Kotlin compilation will be run after implementation.
