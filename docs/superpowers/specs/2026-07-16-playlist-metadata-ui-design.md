# Playlist Metadata and UI Refinement Design

## Goal

Refine the unpublished playlist feature so linked IPTV playlists of realistic size load successfully, M3U artwork and titles flow through the playlist UI and player, local playlists display useful filesystem paths, and playlist management remains concentrated on the playlist list screen.

This design amends the playlist design dated 2026-07-15. Unchanged behavior from that design remains in effect.

## User Experience

### Playlist list

Every playlist row uses the same playlist icon. The row is limited to exactly two text lines:

- line 1: the user-provided playlist name;
- line 2 for an editable playlist: `Local · <count> items`;
- line 2 for a URL-backed playlist: `M3U URL · <count> items`; and
- line 2 for a document-backed playlist: `M3U File · <count> items`.

The internal `EDITABLE` type is presented to users as **Local**. Source URLs and refresh timestamps remain available in the detail screen rather than adding a third line to list rows.

The existing gear/overflow action beside each list item remains the only place to delete a playlist. It opens the existing confirmation flow. The detail top app bar has no delete icon or delete action, and the detail ViewModel does not maintain duplicate deletion state.

### Playlist details

Playlist video rows visually follow the app's normal video rows but use a smaller 16:10 thumbnail, approximately 100 dp wide and no more than 30% of the available row width. The thumbnail source priority is:

1. parsed M3U artwork URL for linked entries;
2. the local media URI for local entries; and
3. the existing video fallback icon.

The primary text is the item title. Local rows show the stored local video path as supporting text, falling back to the media URI only when a path is unavailable. Linked M3U rows show the stream URL as supporting text.

Editable/local playlists retain drag reordering and accessible move actions. Linked playlists remain read-only and derive their order from the source. Pull-to-refresh remains available only for linked playlists.

## Source Limits and M3U Parsing

The bounded source reader accepts at most 4 MiB of decoded source data and the parser accepts at most 20,000 playable entries. These bounds accommodate `https://iptv-org.github.io/iptv/index.m3u` while still limiting memory, parsing time, and malicious inputs. The reader enforces the byte/character bound while streaming even when `Content-Length` is absent or false; the parser independently enforces the entry bound.

For each media entry, the parser produces:

- resolved media URI;
- display title;
- nullable resolved artwork URL; and
- stable source order.

`#EXTINF` attribute names are matched case-insensitively. Attribute parsing is quote-aware so commas inside quoted values do not terminate the metadata section. Artwork is selected from `tvg-logo`, then the legacy `logo` attribute, then a following `#EXTIMG` directive. The title after the `#EXTINF` comma has priority, with `tvg-name` as a fallback before the existing URI-derived title.

Relative media and artwork references are resolved against the linked source using the existing source-specific rules. Only supported, resolvable artwork URIs are persisted. Invalid artwork is stored as null and never causes an otherwise valid media entry to be discarded. Duplicate media URIs keep the first occurrence, including its title and artwork.

Image download or decode failure is a presentation failure only: the UI displays the fallback icon and playback continues.

## Persistence and Migration

`PlaylistItemEntity` gains two nullable columns:

- `image_url`, containing parsed M3U artwork; and
- `display_path`, containing the user-friendly local video path.

The corresponding domain playlist item and insertion input carry nullable `imageUrl` and `displayPath` values. Local media-picker conversion stores the same path already displayed by the video browser. Linked M3U conversion stores `imageUrl`, leaves `displayPath` null, and uses the stream URI as detail supporting text.

Because the playlist schema has not been published, Room remains at version 8. The existing `MIGRATION_7_8` playlist-item table definition is updated to include both new nullable columns, and the exported version-8 schema is regenerated/replaced. No `8 -> 9` migration is added. Published version-7 installations still migrate directly to the final version-8 layout. Development installations created with the previous unpublished version-8 schema must clear app data.

Repository invariants remain unchanged: linked items are replaced transactionally only after successful read and parse, manually added items are accepted only by local playlists, and first-seen ordering wins.

## Player Metadata Flow

Playlist playback is database-backed rather than passing an entire playlist through Intent arrays. The detail screen launches the player with two scalar values:

- playlist ID; and
- selected media URI.

`PlayerActivity` loads the current ordered playlist snapshot through the playlist repository, finds the selected item, and builds the complete Media3 queue. Every `MediaItem` receives:

- its media URI;
- the persisted playlist item title as `MediaMetadata.title`; and
- the persisted M3U `imageUrl` as `MediaMetadata.artworkUri` when present.

This avoids Android transaction-size failures for large IPTV playlists and makes Media3 the single metadata source for the current item, controls, queue, and notification. The existing playlist queue displays `MediaItem` title and artwork. Missing or failed artwork uses its current fallback. The current-item/system notification loads `artworkUri` through the existing bounded artwork pipeline, which retains its 512-pixel and 256-KiB output bounds. Artwork is not added as a player-surface loading image or playback fallback.

A single-item launch remains explicit and does not require a playlist lookup. Launching the same media URI from a different playlist replaces the queue with that playlist's snapshot. When a new player Intent arrives while a playlist lookup is running, the older lookup is cancelled so stale results cannot replace the newer queue.

Network artwork support is added to the app's Coil configuration using the project-compatible Coil network module. It is used by both playlist thumbnails and the existing artwork loading path rather than creating a second image stack.

## Refresh and Failure Behavior

Pull-to-refresh reads and parses the source off the main thread while keeping cached entries visible. A successful refresh atomically replaces items, metadata, and the last-successful-refresh timestamp.

Network, document permission, size-limit, entry-limit, parser, or database failures leave the previous cache and timestamp intact and show a concise error. An initial linked-playlist creation failure leaves no partial playlist. A malformed image reference is handled per item and does not fail the source. A remote image failure shows the row/queue fallback and does not fail refresh or playback.

## Test Strategy

Implementation follows test-driven development. Automated tests cover:

- streaming byte/character enforcement at, below, and above 4 MiB, including missing or false declared lengths and stream closure;
- acceptance of the referenced IPTV playlist's observed size and count bounds;
- the 20,000-entry boundary and over-limit error;
- case-insensitive and quote-aware `#EXTINF` parsing;
- `tvg-logo`, legacy `logo`, `#EXTIMG`, relative artwork resolution, invalid artwork, title precedence, and duplicate first-metadata wins;
- repository mapping and transactional refresh preservation;
- local `displayPath` persistence and URI fallback;
- Room migration from exported schema 7 to the final schema 8, including both nullable columns;
- two-line playlist summaries, common icon, Local/M3U type labels, and list-only deletion;
- compact playlist video rows, thumbnail fallback, and editable-only reordering;
- scalar playlist launch arguments, ordered MediaItem construction, title/artwork metadata, selected index, different-playlist replacement, and cancellation of stale new-Intent loads; and
- relevant module builds, unit tests, lint, and existing regression suites.

Final acceptance uses a newly created disposable emulator. The app is installed and exercised for local playlist creation, adding local media, displayed paths, reordering, list-screen deletion, creation from the supplied IPTV URL, linked refresh, M3U thumbnails, playback title/artwork, queue metadata, and absence of deletion/reordering in linked details. Screenshots, UI hierarchy, and logcat are inspected. Only the emulator created for this verification is stopped and deleted; pre-existing AVDs are not changed.
