# Playlists Feature Design

## Goal

Add persistent editable and linked M3U playlists to NextPlayer. Users can create playlists from a new top-level tab, add selected local folders or videos to editable playlists, play playlist contents, and refresh linked URL or document sources without losing cached contents when refresh fails.

## Scope

This first version includes:

- a Playlists top-level destination in the bottom navigation bar and navigation rail;
- empty editable playlist creation;
- linked M3U/M3U8 playlist creation from an HTTP(S) URL or an Android document;
- ordered playlist details and playback from any item;
- manual addition of selected media to editable playlists;
- persisted reordering of editable playlist entries;
- pull-to-refresh for linked playlists;
- playlist deletion; and
- local caching of linked playlist contents.

Renaming playlists, editing linked playlist contents, and removing individual entries are outside this version.

## Persistence Model

Room schema version 8 adds normalized playlist and playlist-item tables.

`PlaylistEntity` stores:

- generated playlist ID;
- user-visible name;
- normalized name used for uniqueness;
- type: `EDITABLE`, `M3U_URL`, or `M3U_FILE`;
- nullable source URL or persisted document URI;
- creation/update timestamps; and
- nullable last-successful-refresh timestamp.

Names are trimmed before saving. The normalized value is case-folded, and a unique index prevents names that differ only by case or surrounding whitespace. A conflict is reported inline as “A playlist with this name already exists.”

`PlaylistItemEntity` stores:

- owning playlist ID with a cascading foreign key;
- media URI;
- optional M3U title;
- zero-based position; and
- a composite playlist-ID/media-URI primary key.

The composite key deduplicates media within one playlist. Insertions preserve the first-seen order, including overlapping folder selections and repeated M3U entries. Refresh replaces all items for one linked playlist inside a single transaction only after the new source has been read and parsed successfully.

## Domain and Repository Boundaries

`PlaylistRepository` is the feature-facing boundary. It observes playlist summaries and details, creates editable or linked playlists, adds ordered items, reorders editable items, refreshes linked playlists, and deletes playlists.

Repository rules enforce that:

- only editable playlists accept manually added items;
- only editable playlists accept manual reordering;
- playlist names are normalized and unique;
- duplicate media URIs are ignored without changing existing order;
- linked sources remain connected to their URL or document URI;
- refresh cannot run concurrently for the same playlist; and
- a failed refresh never deletes the last successful cache.

Reordering updates the affected positions in one transaction and compacts them back to a contiguous zero-based sequence. The operation identifies the moved entry by URI and uses its destination index, so an observed list refresh cannot silently move the wrong item.

The repository accepts domain-level playlist item inputs rather than video-picker selection types. The video-picker ViewModel resolves selected folders and videos with its existing delete/play conversion behavior before calling the repository. In folder view this includes videos directly in the selected folder; in list view it follows the existing recursive sorted-video result. This keeps “Add to playlist” consistent with delete as requested.

An independently testable M3U parser converts text to ordered entries. A source reader handles HTTP(S) and `ContentResolver` document reads. URL and document I/O run off the main thread and are replaceable by fakes in tests.

## M3U Semantics

The parser supports `.m3u` and UTF-8 `.m3u8`, with or without `#EXTM3U`.

- Blank lines and unsupported comments are ignored.
- `#EXTINF` supplies the title for the next media entry.
- Entries without titles derive a display name from their URI or path.
- Absolute `http`, `https`, `content`, and `file` URIs are preserved.
- Relative URL entries resolve against the remote playlist URL.
- Relative entries in a document playlist resolve only when its document provider exposes a usable sibling document URI. Unresolvable entries are skipped and counted in the result rather than saved as broken items.

URL sources accept only valid HTTP(S) URLs. File sources use `ActivityResultContracts.OpenDocument`, request `.m3u`/`.m3u8`-appropriate MIME types with a permissive fallback, and persist read permission for the selected URI.

Creation reads and parses a linked source before inserting the playlist. If reading or parsing fails, no playlist is created. A successfully read source with zero playable entries creates or refreshes an empty playlist. Refresh updates the last-successful-refresh timestamp and source cache only after the transaction succeeds.

## Navigation and Screens

The new `feature:playlist` module owns playlist navigation, screens, ViewModels, and playlist-specific dialogs. It follows the current Navigation 3, Hilt, Compose Material 3, TV-focus, and responsive layout patterns.

`TopLevelDestination` gains Playlists between Home and Network. Like the other tabs, it has an independent remembered back stack. The top-level screen shows playlist name, type/source status, item count, and last refresh state. An empty state explains how to create the first playlist.

Tapping a playlist opens its ordered details. Play All starts at the first item. Tapping an item passes the complete URI list plus that item as the intent data, allowing the existing player to start at the selected index. Linked details use Material 3 pull-to-refresh; editable details do not. A linked playlist shows its last successful refresh time and retains cached items while a refresh is in progress or after it fails.

Editable details reuse the app's existing `sh.calvin.reorderable` drag-handle pattern. A long press on the drag handle moves an item and persists the final order when the drag ends. TV and keyboard users receive focused Move up and Move down actions for the same repository operation. Linked M3U details expose neither interaction because their order is controlled by the source and would be replaced on refresh.

An overflow action deletes a playlist after confirmation. This deletes its items through the cascading foreign key.

## Creation Flow

The Playlists screen has a `+` floating action button. Pressing it opens a chooser with:

1. Create empty playlist
2. Add M3U playlist from URL
3. Add M3U playlist from file

Empty creation opens a name dialog. URL creation opens a dialog containing name and URL fields. File creation first launches the Android document picker, then opens a name dialog prefilled from the selected filename and showing the chosen document. Confirm buttons remain disabled until required fields are nonblank and valid. Repository validation errors remain attached to the dialog rather than dismissing it.

## Add Selected Media Flow

The existing long-press multi-selection action sheet gains “Add to playlist.” It is available for selected folders, videos, or a mixture of both.

Pressing it opens a dialog containing only editable playlists and a “Create new playlist” action. Linked M3U playlists are never shown as targets. If no editable playlist exists, the dialog presents the create action as its primary empty state.

Choosing an existing playlist adds the resolved videos in the same stable order used by the current selection conversion. Choosing “Create new playlist” opens the name dialog, creates an editable playlist, and immediately adds the pending selection. Duplicate URIs are ignored. Selection mode closes only after a successful add; failures keep the dialog and selection available for retry.

## Refresh and Error Handling

Only one refresh job may update a playlist at a time. Pull-to-refresh shows progress without clearing cached items.

On success, the detail screen displays the replacement content and a concise result including skipped/unresolvable entries when relevant. On network failure, malformed URL, revoked document permission, parser failure, or database failure, the cached items remain visible and a concise snackbar explains the failure. Initial linked creation uses the same error mapping but leaves no partial playlist behind.

## Testing and Acceptance

Implementation follows test-driven development. Automated coverage includes:

- M3U/M3U8 parsing, `#EXTINF`, comments, duplicates, and relative URI resolution;
- normalized unique names;
- stable item ordering and deduplication;
- persisted editable reordering, contiguous positions, and rejection of linked reordering;
- rejection of manual additions to linked playlists;
- transactional replacement and cache retention on refresh failure;
- Room migration from schema 7 to 8;
- playlist and media-picker ViewModel actions;
- creation, selection, and add-to-playlist Compose interactions; and
- focused unit tests plus the project’s relevant build and lint tasks.

Final acceptance uses a newly created Android emulator. The app will be installed and the following flows exercised with UI inspection, screenshots, and logcat checks:

1. open the Playlists tab;
2. create an editable playlist;
3. long-press a video and add it to the playlist;
4. open and play the playlist from the added item;
5. reorder the editable playlist and confirm the order survives leaving and reopening it;
6. create a linked M3U playlist from a controlled test source;
7. pull to refresh it and confirm updated cached contents;
8. confirm linked playlists are absent from manual-add targets and cannot be reordered;
9. delete the test playlists; and
10. shut down and delete the newly created emulator.

If host networking or the Android SDK prevents a controlled URL source, the file-linked flow will exercise the same parser/refresh pipeline and the limitation will be reported explicitly. No pre-existing emulator will be modified or deleted.
