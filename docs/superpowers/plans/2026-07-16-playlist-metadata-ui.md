# Playlist Metadata and UI Refinement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make large linked M3U playlists load with artwork and titles, refine playlist list/detail presentation, preserve local display paths, and build database-backed Media3 queues without large Intent payloads.

**Architecture:** The final unpublished Room schema v8 stores nullable artwork and local display paths on playlist items. After that shared contract lands, three independent slices can run in parallel: bounded/metadata-aware M3U parsing, playlist/local-selection UI, and player queue metadata. Playlist playback passes only playlist ID plus selected URI, and `PlayerActivity` reconstructs the ordered Media3 queue from `PlaylistRepository`.

**Tech Stack:** Kotlin 2.4.10, AGP 9.3.0, Room 2.8.4, Hilt 2.60.1, Navigation 3.1.4, Compose BOM 2026.06.01, Material 3 1.5.0-alpha17, Media3 1.10.1, Coil 3.5.0 with OkHttp network support, coroutines 1.11.0, JUnit 4, Compose UI tests, adb/emulator QA.

## Global Constraints

- Android minimum SDK remains 23; compile and target SDK remain 37.
- Room remains at schema version 8; modify `MIGRATION_7_8` and replace exported `8.json`; do not create schema 9 or `MIGRATION_8_9`.
- Development installs created with the previous unpublished schema 8 clear app data; published schema 7 still migrates to the final schema 8.
- Source limits are exactly 4,194,304 bytes, 4,194,304 decoded characters, and 20,000 unique resolved playable entries.
- Duplicate media URIs keep the first title, artwork, display path, and position.
- Invalid or failed artwork never removes a playable item and never fails playback or refresh.
- Editable playlists are user-facing “Local”; linked playlists remain read-only and source-ordered.
- Playlist list rows contain exactly two text lines and use `NextIcons.Playlist` for every type.
- Playlist deletion exists only in the playlist-list gear/overflow flow; the detail screen has no delete UI or delete state.
- Local detail rows show the same parent path as the normal video browser and fall back to the media URI.
- Playlist detail thumbnails are 16:10, capped at 100 dp and 30% of screen width.
- M3U playback metadata uses `MediaMetadata.title` and `MediaMetadata.artworkUri`; artwork is not a player-surface loading/fallback image.
- Failed linked refreshes retain the prior cached items, metadata, and successful-refresh timestamp.
- Do not modify or delete any pre-existing Android emulator; create and delete one dedicated QA AVD.

---

## File Structure

- `core/model/.../Playlist.kt`: shared playlist input/item contracts with `imageUrl` and `displayPath`.
- `core/database/.../PlaylistItemEntity.kt`: final version-8 Room row.
- `core/database/MediaDatabase.kt`, `schemas/.../8.json`: final unpublished schema and version-7 migration.
- `core/data/.../LocalPlaylistRepository.kt`: lossless mapping between domain and Room metadata.
- `core/data/.../PlaylistLimits.kt`: 4 MiB / 20,000 bounds.
- `core/data/.../M3uParser.kt`: quote-aware title/artwork parsing and first-seen deduplication.
- `feature/videopicker/.../MediaPickerViewModel.kt`: persists the normal video-browser parent path for Local items.
- `feature/playlist/.../PlaylistListScreen.kt`: two-line, common-icon playlist summaries and list-only deletion.
- `feature/playlist/.../PlaylistDetailScreen.kt`: compact artwork rows, path/URL supporting text, refresh, and reorder.
- `feature/playlist/.../PlaylistDetailViewModel.kt`: scalar playlist playback event and no deletion state.
- `feature/player/.../PlaylistPlaybackContract.kt`: public scalar Intent-extra contract.
- `feature/player/.../PlaylistMediaQueue.kt`: pure playlist-to-MediaItem queue builder.
- `feature/player/PlayerActivity.kt`: cancellable repository lookup and queue application.
- `feature/player/.../MediaItem.kt`, `PlaylistView.kt`, `PlayerService.kt`: artwork-model selection and bounded notification artwork.
- `app/.../PlaylistNavGraph.kt`: launches the player with playlist ID and selected URI only.
- `gradle/libs.versions.toml`, `app/build.gradle.kts`: Coil network module.

---

### Task 1: Final schema-v8 playlist item metadata contract

**Files:**
- Modify: `core/model/src/main/java/dev/anilbeesetti/nextplayer/core/model/Playlist.kt`
- Modify: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/entities/PlaylistItemEntity.kt`
- Modify: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/MediaDatabase.kt`
- Modify: `core/database/schemas/dev.anilbeesetti.nextplayer.core.database.MediaDatabase/8.json`
- Modify: `core/database/src/androidTest/java/dev/anilbeesetti/nextplayer/core/database/PlaylistDaoTest.kt`
- Modify: `core/database/src/androidTest/java/dev/anilbeesetti/nextplayer/core/database/Migration7To8Test.kt`
- Modify: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalPlaylistRepository.kt`
- Modify: `core/data/src/test/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalPlaylistRepositoryTest.kt`

**Interfaces:**
- Produces: `PlaylistItemInput(uriString, title = null, imageUrl = null, displayPath = null)`.
- Produces: `PlaylistItem(uriString, title, position, imageUrl = null, displayPath = null)`.
- Produces: nullable Room columns `image_url` and `display_path` in the final schema 8.
- Produces: repository round trips that preserve both fields.

- [ ] **Step 1: Write failing DAO and migration assertions**

Extend `PlaylistDaoTest` with an item round trip:

```kotlin
@Test
fun itemMetadataRoundTrips() = runTest {
    val id = dao.insertPlaylist(
        PlaylistEntity(name = "Movies", normalizedName = "movies", type = "EDITABLE"),
    )
    dao.addItems(
        id,
        listOf(
            PlaylistItemEntity(
                playlistId = id,
                uri = "content://video/1",
                title = "First",
                position = 0,
                imageUrl = "https://images.example/first.png",
                displayPath = "/storage/emulated/0/Movies",
            ),
        ),
    )

    val item = dao.getItems(id).single()
    assertEquals("https://images.example/first.png", item.imageUrl)
    assertEquals("/storage/emulated/0/Movies", item.displayPath)
}
```

After `runMigrationsAndValidate` in `Migration7To8Test`, inspect the final table:

```kotlin
val columns = buildMap<String, Int> {
    migrated.query("PRAGMA table_info(`playlist_item`)").use { cursor ->
        while (cursor.moveToNext()) put(cursor.getString(1), cursor.getInt(3))
    }
}
assertEquals(0, columns.getValue("image_url"))
assertEquals(0, columns.getValue("display_path"))
```

- [ ] **Step 2: Compile the instrumentation tests and verify RED**

Run:

```bash
./gradlew :core:database:compileDebugAndroidTestKotlin --console=plain
```

Expected: FAIL because `PlaylistItemEntity.imageUrl` and `displayPath` do not exist.

- [ ] **Step 3: Add the nullable domain and entity fields**

Use appended defaults so current positional call sites continue compiling:

```kotlin
data class PlaylistItemInput(
    val uriString: String,
    val title: String? = null,
    val imageUrl: String? = null,
    val displayPath: String? = null,
)

data class PlaylistItem(
    val uriString: String,
    val title: String?,
    val position: Int,
    val imageUrl: String? = null,
    val displayPath: String? = null,
)
```

Append to `PlaylistItemEntity`:

```kotlin
@ColumnInfo(name = "image_url") val imageUrl: String? = null,
@ColumnInfo(name = "display_path") val displayPath: String? = null,
```

- [ ] **Step 4: Update only the existing version-7-to-8 table creation**

In `MIGRATION_7_8`, make the final table columns match the entity:

```sql
CREATE TABLE IF NOT EXISTS `playlist_item` (
    `playlist_id` INTEGER NOT NULL,
    `uri` TEXT NOT NULL,
    `title` TEXT,
    `position` INTEGER NOT NULL,
    `image_url` TEXT,
    `display_path` TEXT,
    PRIMARY KEY(`playlist_id`, `uri`),
    FOREIGN KEY(`playlist_id`) REFERENCES `playlist`(`id`)
        ON UPDATE NO ACTION ON DELETE CASCADE
)
```

Leave `@Database(version = 8)`, `DatabaseModule`, and the migration list unchanged.

- [ ] **Step 5: Map both fields through the repository**

Update input-to-entity mapping:

```kotlin
PlaylistItemEntity(
    playlistId = playlistId,
    uri = item.uriString,
    title = item.title,
    position = position,
    imageUrl = item.imageUrl,
    displayPath = item.displayPath,
)
```

Update entity-to-domain mapping:

```kotlin
PlaylistItem(
    uriString = item.uri,
    title = item.title,
    position = item.position,
    imageUrl = item.imageUrl,
    displayPath = item.displayPath,
)
```

- [ ] **Step 6: Add repository metadata round-trip and refresh-preservation tests**

Add an editable-playlist item test that submits non-null `imageUrl` and `displayPath` and asserts both round-trip; this isolates repository mapping before Task 2 adds parser artwork. On a forced linked-refresh failure, assert the previously observed item is unchanged as a whole:

```kotlin
val cachedItem = repository.observePlaylist(id).first()!!.items.single()
sourceReader.failure = IOException("offline")
assertFailsWith<PlaylistSourceException> { repository.refresh(id) }
assertEquals(cachedItem, repository.observePlaylist(id).first()!!.items.single())
```

- [ ] **Step 7: Regenerate schema 8 and run focused verification**

Run:

```bash
./gradlew :core:model:compileKotlin :core:database:kspDebugKotlin :core:database:compileDebugAndroidTestKotlin :core:data:testDebugUnitTest --tests '*LocalPlaylistRepositoryTest' --console=plain
```

Expected: compilation and repository tests PASS. Confirm `8.json` contains nullable `image_url` and `display_path`, and `rg --files core/database/schemas | rg '/9.json$'` returns no result.

- [ ] **Step 8: Commit the shared contract**

```bash
git add core/model core/database core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalPlaylistRepository.kt core/data/src/test/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalPlaylistRepositoryTest.kt
git commit -m "feat: persist playlist item metadata"
```

---

### Task 2: Bounded M3U artwork and title parsing

**Files:**
- Modify: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/playlist/PlaylistLimits.kt`
- Modify: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/playlist/M3uParser.kt`
- Modify: `core/data/src/test/java/dev/anilbeesetti/nextplayer/core/data/playlist/M3uParserTest.kt`
- Modify: `core/data/src/test/java/dev/anilbeesetti/nextplayer/core/data/playlist/PlaylistSourceReaderTest.kt`
- Modify: `core/data/src/test/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalPlaylistRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 1's `PlaylistItemInput.imageUrl`.
- Preserves: `M3uParser.parse(content, resolveEntry)` and `PlaylistSourceContent` signatures.
- Produces: quote-aware `#EXTINF` title/artwork metadata and `#EXTIMG` fallback.
- Produces: 4 MiB source and 20,000 unique-playable-entry limits.

- [ ] **Step 1: Raise test expectations and add source-boundary tests**

Set test constants to:

```kotlin
private const val SOURCE_LIMIT_BYTES = 4_194_304
private const val IPTV_OBSERVED_BYTES = 2_822_004
```

Keep the exact-boundary, plus-one, declared-length, chunked, continuous-stream, and closure tests. Add:

```kotlin
@Test
fun acceptsSourceAtObservedIptvSize() = withServer(
    status = 200,
    response = "a".repeat(IPTV_OBSERVED_BYTES),
) { source ->
    runTest {
        val content = remoteReader(StandardTestDispatcher(testScheduler))
            .read(PlaylistType.M3U_URL, source)
        assertEquals(IPTV_OBSERVED_BYTES, content.text.length)
    }
}
```

- [ ] **Step 2: Add failing parser metadata tests**

Add tests for case-insensitive attributes, quoted commas, relative artwork resolution, title precedence, aliases, invalid artwork, and duplicate first metadata. The central case is:

```kotlin
@Test
fun parsesTitleAndArtworkWithQuoteAwareAttributes() {
    val result = parser.parse(
        """#EXTM3U
            #EXTINF:-1 TVG-NAME="Fallback" tvg-logo="images/logo,one.png" logo="legacy.png",Channel One
            streams/one.m3u8
        """.trimIndent(),
    ) { raw -> URI("https://example.test/lists/index.m3u").resolve(raw).toString() }

    assertEquals("Channel One", result.entries.single().title)
    assertEquals("https://example.test/lists/images/logo,one.png", result.entries.single().imageUrl)
}
```

Also assert `tvg-logo > logo > #EXTIMG`, blank comma-title falls back to `tvg-name`, invalid artwork produces null without increasing `skippedEntries`, and a duplicate URI retains the first title/artwork.

- [ ] **Step 3: Add failing 20,000-entry boundary tests**

Build synthetic unique resolved entries and assert 20,000 succeeds, 20,001 throws `PlaylistEntryLimitExceededException(20_000)`, and 13,276 succeeds. Enforce the limit on unique resolved playable output, not invalid or duplicate raw lines.

- [ ] **Step 4: Run focused tests and verify RED**

Run:

```bash
./gradlew :core:data:testDebugUnitTest --tests '*PlaylistSourceReaderTest' --tests '*M3uParserTest' --console=plain
```

Expected: FAIL on old 1 MiB/10,000 constants and absent artwork metadata.

- [ ] **Step 5: Update exact bounds**

Use:

```kotlin
internal object PlaylistLimits {
    const val MAX_SOURCE_BYTES = 4_194_304
    const val MAX_SOURCE_CHARS = 4_194_304
    const val MAX_ENTRIES = 20_000
}
```

Do not loosen or remove streaming probes in `PlaylistSourceReader`.

- [ ] **Step 6: Implement quote-aware pending metadata**

Add a private parser result and quote-aware comma finder:

```kotlin
private data class ExtInfMetadata(val title: String?, val imageReference: String?)

private fun String.firstUnquotedComma(): Int {
    var quote: Char? = null
    forEachIndexed { index, char ->
        when {
            quote == null && (char == '\'' || char == '"') -> quote = char
            quote == char -> quote = null
            quote == null && char == ',' -> return index
        }
    }
    return -1
}
```

Parse attributes with a case-normalized map and this exact precedence:

```kotlin
private val extInfAttribute = Regex(
    """([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""",
)

private fun String.extInfAttributes(): Map<String, String> =
    extInfAttribute.findAll(this).associate { match ->
        val value = match.groupValues.drop(2).firstOrNull(String::isNotEmpty).orEmpty()
        match.groupValues[1].lowercase(Locale.ROOT) to value
    }

val title = commaTitle.ifBlank { attributes["tvg-name"].orEmpty() }.ifBlank { null }
val imageReference = attributes["tvg-logo"]
    ?.takeIf(String::isNotBlank)
    ?: attributes["logo"]?.takeIf(String::isNotBlank)
```

Track a following `#EXTIMG:<reference>` only when `imageReference` is still null. When a media line resolves and its URI is newly added, construct:

```kotlin
PlaylistItemInput(
    uriString = resolvedUri,
    title = pending.title,
    imageUrl = pending.imageReference?.let(resolveEntry),
)
```

Clear all pending metadata after every media candidate. Increment the entry limit only when a resolved URI is newly added.

- [ ] **Step 7: Prove repository refresh carries and replaces artwork**

Extend the repository fake source/parser fixtures so linked creation persists the parsed image and successful refresh replaces it. Retain the existing failure-cache assertion from Task 1.

- [ ] **Step 8: Run the data slice and inspect XML results**

Run:

```bash
./gradlew :core:data:testDebugUnitTest --console=plain
```

Expected: all data unit tests PASS. Because unit-test failures are globally ignored, inspect `core/data/build/test-results/testDebugUnitTest/TEST-*.xml` and require every suite to report `failures="0" errors="0"`.

- [ ] **Step 9: Commit the M3U slice**

```bash
git add core/data
git commit -m "feat: parse artwork from large m3u playlists"
```

---

### Task 3: Local path mapping and refined playlist UI

**Files:**
- Modify: `feature/videopicker/src/main/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/mediapicker/MediaPickerViewModel.kt`
- Modify: `feature/videopicker/src/test/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/mediapicker/PlaylistSelectionMapperTest.kt`
- Modify: `feature/playlist/build.gradle.kts`
- Modify: `feature/playlist/src/main/res/values/strings.xml`
- Modify: `feature/playlist/src/main/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/list/PlaylistListScreen.kt`
- Modify: `feature/playlist/src/androidTest/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/list/PlaylistListScreenTest.kt`
- Modify: `feature/playlist/src/main/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/detail/PlaylistDetailViewModel.kt`
- Modify: `feature/playlist/src/test/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/detail/PlaylistDetailViewModelTest.kt`
- Modify: `feature/playlist/src/main/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/detail/PlaylistDetailScreen.kt`
- Modify: `feature/playlist/src/androidTest/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/detail/PlaylistDetailScreenTest.kt`
- Modify: `feature/playlist/src/main/java/dev/anilbeesetti/nextplayer/feature/playlist/navigation/PlaylistNavigation.kt`

**Interfaces:**
- Consumes: Task 1's `imageUrl` and `displayPath` fields.
- Produces: local inputs with `displayPath = video.path.substringBeforeLast("/")`.
- Produces: `PlaylistDetailEvent.Play(playlistId: Long, startUri: String)`.
- Produces: `playlistDetailEntry(onPlayPlaylist: (playlistId: Long, startUri: Uri) -> Unit)`.

- [ ] **Step 1: Add a failing local display-path mapping assertion**

In `PlaylistSelectionMapperTest`, resolve a video with path `/Movies/video.mp4`, submit it, and assert:

```kotlin
assertEquals(
    PlaylistItemInput(
        uriString = video.uriString,
        title = video.displayName,
        displayPath = "/Movies",
    ),
    repository.addedItems.single(),
)
```

- [ ] **Step 2: Add failing list and detail UI assertions**

Add list summaries for all types and assert exact supporting text:

```kotlin
composeRule.onNodeWithText("Local · 1 item").assertIsDisplayed()
composeRule.onNodeWithText("M3U URL · 2 items").assertIsDisplayed()
composeRule.onNodeWithText("M3U File · 3 items").assertIsDisplayed()
composeRule.onAllNodesWithText("Editable").assertCountEquals(0)
```

Exercise the list gear/overflow Delete action and confirmation. In detail tests, assert no `Delete playlist` node, linked supporting text is the stream URL, and a Local item displays its persisted path.

- [ ] **Step 3: Change playback ViewModel tests to the scalar event**

Replace full-list expectations with:

```kotlin
assertEquals(
    PlaylistDetailEvent.Play(playlistId = 42, startUri = "content://2"),
    event.await(),
)
```

Replace the pending-drag playback-order tests with assertions that Play All and Play Item emit nothing while `PlaylistReorderSnapshot.isDragging` or `isMoving` is true. This prevents a transient displayed order from disagreeing with the authoritative Room snapshot loaded by the player. Delete the detail-ViewModel deletion test and fake `deletedIds`; repository deletion remains covered by the list ViewModel tests.

- [ ] **Step 4: Run focused UI and mapping tests and verify RED**

Run:

```bash
./gradlew :feature:videopicker:testDebugUnitTest --tests '*PlaylistSelectionMapperTest' :feature:playlist:testDebugUnitTest --tests '*PlaylistDetailViewModelTest' :feature:playlist:compileDebugAndroidTestKotlin --console=plain
```

Expected: FAIL on absent display path, old playback event, old detail deletion, and old list/detail content.

- [ ] **Step 5: Persist the normal browser parent path**

Use named arguments in `PlaylistSelectionController`:

```kotlin
pendingItems = videos.map { video ->
    PlaylistItemInput(
        uriString = video.uriString,
        title = video.displayName,
        displayPath = video.path.substringBeforeLast('/'),
    )
}
```

- [ ] **Step 6: Make playlist list rows exactly two lines**

Change `editable_playlist` to `Local` and `m3u_file_playlist` to `M3U File`. In `PlaylistItem`:

```kotlin
leadingContent = {
    Icon(
        imageVector = NextIcons.Playlist,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}
content = {
    Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
supportingContent = {
    Text(
        text = "${playlist.typeLabel()} · $itemCount",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
```

Remove `overlineContent`, `sourceStatus()`, and unused date imports. Preserve the list overflow confirmation and add `Modifier.tvFocusRing()` to its gear `IconButton`.

- [ ] **Step 7: Remove all detail deletion state and emit scalar playback**

Remove `PlaylistDetailAction.Delete`, `PlaylistDetailEvent.Deleted`, `delete()`, the detail trash action, local confirmation state/dialog, and route handling for `Deleted`. Define:

```kotlin
sealed interface PlaylistDetailEvent {
    data class Play(val playlistId: Long, val startUri: String) : PlaylistDetailEvent
    data class Message(val text: String) : PlaylistDetailEvent
}
```

Read `reorderState.state.value`; if `isDragging` or `isMoving` is true, do not emit playback. Otherwise validate the URI belongs to the displayed list, then send `Play(playlistId, startUri)`. Change navigation to:

```kotlin
fun EntryProviderScope<NavKey>.playlistDetailEntry(
    onNavigateUp: () -> Unit,
    onPlayPlaylist: (playlistId: Long, startUri: Uri) -> Unit,
)
```

- [ ] **Step 8: Add the compact artwork row**

Add `implementation(libs.coil.compose)` to `feature/playlist`. For each item derive:

```kotlin
val supportingText = if (playlist.type == PlaylistType.EDITABLE) {
    item.displayPath?.takeIf(String::isNotBlank) ?: item.uriString
} else {
    item.uriString
}
val artworkModel = item.imageUrl?.takeIf(String::isNotBlank)
    ?: item.uriString.takeIf { playlist.type == PlaylistType.EDITABLE }
```

Make leading content a row containing the unchanged reorder handle and this thumbnail:

```kotlin
Box(
    modifier = Modifier
        .width(min(100.dp, LocalConfiguration.current.screenWidthDp.dp * 0.30f))
        .aspectRatio(16f / 10f)
        .clip(MaterialTheme.shapes.small)
        .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
) {
    Icon(
        imageVector = NextIcons.Video,
        contentDescription = null,
        modifier = Modifier.align(Alignment.Center).fillMaxSize(0.5f),
    )
    artworkModel?.let { model ->
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
```

Keep existing URI keys, drag callbacks, TV move buttons, linked read-only behavior, and pull-to-refresh.

- [ ] **Step 9: Run the playlist/video-picker slice**

Run:

```bash
./gradlew :feature:videopicker:testDebugUnitTest :feature:playlist:testDebugUnitTest :feature:playlist:compileDebugAndroidTestKotlin :feature:playlist:assembleDebug --console=plain
```

Expected: unit tests PASS and Compose instrumentation sources compile. Inspect both unit-test XML directories for zero failures/errors.

- [ ] **Step 10: Commit the UI slice**

```bash
git add feature/videopicker feature/playlist
git commit -m "feat: refine playlist presentation"
```

---

### Task 4: Database-backed Media3 playlist queues and artwork

**Files:**
- Create: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/utils/PlaylistPlaybackContract.kt`
- Create: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/utils/PlaylistMediaQueue.kt`
- Create: `feature/player/src/test/java/dev/anilbeesetti/nextplayer/feature/player/utils/PlaylistMediaQueueTest.kt`
- Create: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/utils/LatestPlaybackRequestRunner.kt`
- Create: `feature/player/src/test/java/dev/anilbeesetti/nextplayer/feature/player/utils/LatestPlaybackRequestRunnerTest.kt`
- Modify: `feature/player/build.gradle.kts`
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/PlayerActivity.kt`
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/extensions/MediaItem.kt`
- Modify: `feature/player/src/test/java/dev/anilbeesetti/nextplayer/feature/player/extensions/MediaItemArtworkTest.kt`
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/ui/PlaylistView.kt`
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/service/PlayerService.kt`
- Modify: `app/src/main/java/dev/anilbeesetti/nextplayer/navigation/PlaylistNavGraph.kt`
- Modify: `app/src/test/java/dev/anilbeesetti/nextplayer/navigation/PlaybackLaunchSpecTest.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: Task 1's persisted `PlaylistItem.title` and `imageUrl`.
- Consumes: Task 3's `(playlistId, startUri)` navigation callback.
- Produces: `PlaylistPlaybackContract.EXTRA_PLAYLIST_ID`.
- Produces: `PlaybackRequestIdentity(playlistId, selectedUriString)` for queue-replacement decisions.
- Produces: `Playlist.toMediaQueue(selectedUri): PlaylistMediaQueue?`.
- Produces: `LatestPlaybackRequestRunner.submit(block)` with latest-request cancellation.
- Produces: queue MediaItems with title and artwork URI on every linked entry.

- [ ] **Step 1: Write failing pure queue tests**

Create tests for source order, selected index, missing selected URI, title, and artwork:

```kotlin
@Test
fun playlistBuildsOrderedMetadataQueueAtSelectedItem() {
    val playlist = playlist(
        items = listOf(
            PlaylistItem("https://stream/1", "News One", 0, "https://img/1.png"),
            PlaylistItem("https://stream/2", "News Two", 1, "https://img/2.png"),
        ),
    )

    val queue = playlist.toMediaQueue("https://stream/2")!!

    assertEquals(1, queue.startIndex)
    assertEquals(listOf("News One", "News Two"), queue.mediaItems.map { it.mediaMetadata.title })
    assertEquals("https://img/2.png", queue.mediaItems[1].mediaMetadata.artworkUri.toString())
}
```

Add an identity assertion proving the same selected URI from playlist 42 and playlist 84 are different requests and therefore cannot reuse the current queue.

- [ ] **Step 2: Write failing artwork-selection tests**

In `MediaItemArtworkTest`, assert the UI/request model prefers published bytes, then `artworkUri`, then media URI:

```kotlin
assertSame(bytes, itemWithArtworkData.artworkModel)
assertEquals(artworkUri, itemWithArtworkUri.artworkModel)
assertEquals(mediaUri, itemWithoutArtwork.artworkModel)
```

Also assert `artworkRequestUri` prefers `artworkUri` over `mediaId` for the service loader.

- [ ] **Step 3: Write a failing latest-request cancellation test**

Add `testImplementation(libs.kotlinx.coroutines.test)` to `feature/player`, then write:

```kotlin
@Test
fun secondSubmissionCancelsTheFirst() = runTest {
    val firstStarted = CompletableDeferred<Unit>()
    val firstCancelled = CompletableDeferred<Unit>()
    val completed = mutableListOf<String>()
    val runner = LatestPlaybackRequestRunner(backgroundScope)

    runner.submit {
        firstStarted.complete(Unit)
        try {
            awaitCancellation()
        } finally {
            firstCancelled.complete(Unit)
        }
    }
    firstStarted.await()
    runner.submit { completed += "second" }
    advanceUntilIdle()

    firstCancelled.await()
    assertEquals(listOf("second"), completed)
}
```

- [ ] **Step 4: Replace playlist launch-spec expectations**

Keep ordinary media-launch tests unchanged. Add a playlist-specific spec/assertion proving only scalar values are needed:

```kotlin
val spec = playlistPlaybackLaunchSpec(playlistId = 42, startItem = "https://stream/2")
assertEquals(42L, spec.playlistId)
assertEquals("https://stream/2", spec.startItem)
```

- [ ] **Step 5: Run focused tests and verify RED**

Run:

```bash
./gradlew :feature:player:testDebugUnitTest --tests '*PlaylistMediaQueueTest' --tests '*MediaItemArtworkTest' --tests '*LatestPlaybackRequestRunnerTest' :app:testDebugUnitTest --tests '*PlaybackLaunchSpecTest' --console=plain
```

Expected: FAIL because the queue, artwork helpers, and scalar playlist launch spec do not exist.

- [ ] **Step 6: Implement the scalar contract and pure MediaItem queue**

Use:

```kotlin
object PlaylistPlaybackContract {
    const val EXTRA_PLAYLIST_ID = "dev.anilbeesetti.nextplayer.extra.PLAYLIST_ID"
}

internal data class PlaybackRequestIdentity(
    val playlistId: Long?,
    val selectedUriString: String,
)

internal data class PlaylistMediaQueue(
    val mediaItems: List<MediaItem>,
    val startIndex: Int,
)

internal fun Playlist.toMediaQueue(selectedUri: String): PlaylistMediaQueue? {
    val startIndex = items.indexOfFirst { it.uriString == selectedUri }
    if (startIndex == -1) return null
    return PlaylistMediaQueue(
        mediaItems = items.map { item ->
            MediaItem.Builder()
                .setUri(item.uriString)
                .setMediaId(item.uriString)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.title)
                        .setArtworkUri(item.imageUrl?.takeIf(String::isNotBlank)?.toUri())
                        .build(),
                )
                .build()
        },
        startIndex = startIndex,
    )
}
```

- [ ] **Step 7: Implement latest-request cancellation**

Use:

```kotlin
internal class LatestPlaybackRequestRunner(
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    fun submit(block: suspend () -> Unit) {
        job?.cancel()
        job = scope.launch { block() }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}
```

The submitted block must include repository lookup, queue building, and Main-thread `setMediaItems`, so cancellation covers the entire stale-request window.

- [ ] **Step 8: Launch playlist playback with ID plus selected URI only**

In `PlaylistNavGraph`, define and use:

```kotlin
internal data class PlaylistPlaybackLaunchSpec<T>(val playlistId: Long, val startItem: T)

internal fun <T> playlistPlaybackLaunchSpec(
    playlistId: Long,
    startItem: T,
) = PlaylistPlaybackLaunchSpec(playlistId, startItem)
```

Create the player Intent with `data = startUri` and `putExtra(PlaylistPlaybackContract.EXTRA_PLAYLIST_ID, playlistId)`. Do not call `startPlayback(uris, ..., forcePlaylistExtra = true)` and do not add `PlayerApi.API_PLAYLIST`.
Remove the now-unused `forcePlaylistExtra` parameter from `startPlayback`/`playbackLaunchSpec` and replace its one-item-array test with the scalar playlist launch test.

- [ ] **Step 9: Load and apply the playlist snapshot cancellably**

Inject `PlaylistRepository` into `PlayerActivity`, create `LatestPlaybackRequestRunner(lifecycleScope)`, and read the optional positive long extra. Submit each fresh/new Intent through the runner. For a playlist launch:

```kotlin
val storedPlaylist = playlistRepository.observePlaylist(playlistId).first()
val queue = storedPlaylist?.toMediaQueue(uri.toString())
```

Apply `queue.mediaItems`, `queue.startIndex`, and `C.TIME_UNSET` on Main. If the playlist or selected item is unavailable, fall back to one explicit MediaItem for the selected URI. Preserve the current non-playlist `resolvePlaybackQueue` path and external API title/position/subtitle behavior.

Track the active `PlaybackRequestIdentity`. Treat a request as already active only when both current URI and playlist ID match; equality is false for the same URI launched from another playlist, so that request replaces the queue. Parse the extra only when `intent.hasExtra(EXTRA_PLAYLIST_ID)` and its value is positive.

- [ ] **Step 10: Make queue and notification artwork prefer MediaMetadata**

Add:

```kotlin
val MediaItem.artworkModel: Any
    get() = mediaMetadata.artworkData
        ?: mediaMetadata.artworkUri
        ?: mediaId.toUri()

val MediaItem.artworkRequestUri: Uri
    get() = mediaMetadata.artworkUri ?: mediaId.toUri()
```

Use `mediaItem.artworkModel` in `PlaylistView.ThumbnailView`. In `PlayerService.prepareMediaItems`, retain `mediaItem.mediaMetadata.artworkUri` before falling back to default artwork. In `loadArtworkForMediaItem`, use `mediaItem.artworkRequestUri`; continue using `.size(512, 512)`, `allowHardware(false)`, `encodePublishedArtwork`, and the 256 KiB guard. Do not add artwork to the video surface.

- [ ] **Step 11: Enable Coil network fetching**

Add to the version catalog:

```toml
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
```

Add `implementation(libs.coil.network.okhttp)` to `app/build.gradle.kts`. Keep the existing singleton `ImageLoader`; the network module supplies HTTP(S) fetching to playlist detail, queue, and service requests.

- [ ] **Step 12: Run player/app verification and inspect XML**

Run:

```bash
./gradlew :feature:player:testDebugUnitTest :feature:player:assembleDebug :app:testDebugUnitTest :app:assembleDebug --console=plain
```

Expected: player/app tests and builds PASS. Inspect `feature/player/build/test-results/testDebugUnitTest/TEST-*.xml` and `app/build/test-results/testDebugUnitTest/TEST-*.xml` for zero failures/errors.

- [ ] **Step 13: Commit the player slice**

```bash
git add feature/player app gradle/libs.versions.toml
git commit -m "feat: load playlist metadata into player queues"
```

---

### Task 5: Integration, database instrumentation, and disposable-emulator acceptance

**Files:**
- Modify only if a verification failure demonstrates a defect in the files above.
- Create: `docs/qa/2026-07-16-playlist-metadata-emulator-qa.md`

**Interfaces:**
- Consumes: all previous tasks.
- Produces: automated verification evidence and disposable-emulator QA evidence.

- [ ] **Step 1: Run formatting and scoped static checks**

Run:

```bash
./gradlew ktlintCheck :core:data:lintDebug :core:database:lintDebug :feature:videopicker:lintDebug :feature:playlist:lintDebug :feature:player:lintDebug :app:lintDebug --console=plain
```

Expected: no new ktlint or scoped lint failures. Diagnose any failure with `superpowers:systematic-debugging`; do not suppress it without establishing the cause.

- [ ] **Step 2: Run the complete relevant unit/build matrix**

Run:

```bash
./gradlew :core:data:testDebugUnitTest :feature:videopicker:testDebugUnitTest :feature:playlist:testDebugUnitTest :feature:player:testDebugUnitTest :app:testDebugUnitTest :core:database:assembleDebugAndroidTest :feature:playlist:assembleDebugAndroidTest :app:assembleDebug --console=plain
```

Expected: Gradle completes and every generated unit-test XML suite has `failures="0" errors="0"`.

- [ ] **Step 3: Inventory AVDs and create one dedicated QA AVD**

Record `emulator -list-avds` in the QA report before creation. Set `QA_AVD=NextPlayer_Playlist_Metadata_QA_20260716`, assert that exact name is absent, and if it is present choose a new timestamp-suffixed `QA_AVD` before continuing. Select the highest installed Google APIs x86_64 system image from `sdkmanager --list_installed`, then create `QA_AVD` without `--force`. Record the selected system-image package and resulting serial as `QA_SERIAL`. Do not start, stop, alter, or delete any recorded pre-existing AVD.

- [ ] **Step 4: Boot the dedicated emulator and run instrumentation**

Boot the new AVD, wait for `adb -s "$QA_SERIAL" shell getprop sys.boot_completed` to return `1`, and run:

```bash
ANDROID_SERIAL="$QA_SERIAL" ./gradlew :core:database:connectedDebugAndroidTest :feature:playlist:connectedDebugAndroidTest --console=plain
```

Expected: migration, DAO, playlist list, and playlist detail instrumentation tests PASS. Inspect connected-test XML for zero failures/errors.

- [ ] **Step 5: Install and exercise Local playlist behavior**

Install the debug APK on `QA_SERIAL`, grant required media permissions, seed or copy two playable local videos, then verify with adb-driven UI inspection:

1. Playlists list shows the common playlist icon and `Local · 0 items` on two lines.
2. Long-press local videos and add them to the Local playlist.
3. Detail rows show compact thumbnails and the browser-style parent path rather than content URI.
4. Reorder items, leave detail, reopen it, and confirm the persisted order.
5. Confirm no detail top-bar delete action exists.
6. Delete the Local playlist from its list-row gear and confirmation.

Capture screenshots, UI hierarchy, and relevant logcat excerpts in the QA report.

- [ ] **Step 6: Exercise the supplied linked IPTV source**

Create an M3U URL playlist using `https://iptv-org.github.io/iptv/index.m3u`. Verify:

1. creation succeeds without a byte/character/entry-limit error;
2. the row reads `M3U URL · n items` with the actual loaded count on exactly two lines;
3. detail rows show stream titles, stream URLs, and available `tvg-logo` artwork;
4. swipe-to-refresh completes while cached items remain visible;
5. linked rows have no reorder controls and detail has no delete action;
6. tapping an entry opens the player at that entry;
7. player title and artwork match the playlist item;
8. the queue shows playlist titles/artwork; and
9. notification/current-item artwork is published through the bounded artwork path.

If a particular stream is offline, select another item; source loading, metadata, queue construction, and UI artwork remain the acceptance targets.

- [ ] **Step 7: Check logs and preserve failure evidence**

Inspect logcat for `FATAL EXCEPTION`, `TransactionTooLargeException`, Room schema errors, Coil network failures that escape fallback handling, and player errors caused by queue construction. A failed remote logo may log a handled image-load failure but must not crash or prevent playback.

- [ ] **Step 8: Stop and delete only the dedicated QA AVD**

Stop `QA_SERIAL`, delete only the recorded `QA_AVD`, and rerun `emulator -list-avds`. Compare with the pre-test inventory and assert every pre-existing AVD remains.

- [ ] **Step 9: Commit verification evidence**

```bash
git add docs/qa/2026-07-16-playlist-metadata-emulator-qa.md
git commit -m "test: verify playlist metadata on emulator"
```

- [ ] **Step 10: Run final verification before claiming completion**

Use `superpowers:verification-before-completion`. Confirm `git status --short`, relevant test XML, connected-test results, app build, QA report, and post-test AVD inventory. Request an independent final code review with `superpowers:requesting-code-review` and resolve only evidence-backed findings.

---

## Parallel Execution Order

1. Execute Task 1 alone because it establishes the shared model and schema contract.
2. After Task 1 passes review, dispatch Tasks 2, 3, and 4 concurrently to separate agents; their production file sets do not overlap.
3. Review each slice for specification compliance and code quality before accepting its commit.
4. Integrate all accepted slices, run Task 5, and keep the QA emulator lifecycle exclusive to the primary agent.
