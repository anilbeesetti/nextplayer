# Playlists Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add persistent editable and linked M3U playlists, a top-level Playlists tab, add-to-playlist selection actions, editable reordering, safe linked refresh, and verified Android emulator flows.

**Architecture:** Room schema v8 stores playlist metadata and normalized ordered items behind `PlaylistRepository`. A pure M3U parser and replaceable source reader feed transactional linked refreshes. A new `feature:playlist` Compose/Navigation 3 module owns list/detail UI while the existing video picker resolves selected folders/videos and delegates additions to the repository.

**Tech Stack:** Kotlin 2.4.10, AGP 9.3.0, Room 2.8.4, Hilt 2.60.1, Navigation 3.1.4, Compose BOM 2026.06.01, Material 3 1.5.0-alpha17, coroutines 1.11.0, `sh.calvin.reorderable` 3.1.0, JUnit 4, Compose UI tests, adb/emulator QA.

## Global Constraints

- Android minimum SDK remains 23; compile and target SDK remain 37.
- Playlist names are trimmed and unique after case-folding; conflicts use “A playlist with this name already exists.”
- Playlist items are unique by playlist ID and URI and preserve first-seen order.
- Only editable playlists accept manual additions and reordering.
- Linked M3U playlists remain connected to their HTTP(S) URL or persisted document URI.
- Failed linked refreshes retain the last successful cached contents.
- Folder expansion must use the same conversion as the existing delete action for the active media view mode.
- Renaming playlists, removing individual entries, and editing linked contents remain out of scope.
- Do not modify or delete any pre-existing Android emulator; create and delete a dedicated QA emulator.

---

## File Structure

- `core/model/.../Playlist.kt`: public playlist domain types and typed playlist failures.
- `core/database/.../PlaylistEntity.kt`, `PlaylistItemEntity.kt`: normalized Room rows.
- `core/database/.../PlaylistDao.kt`: playlist observation and atomic add/replace/reorder operations.
- `core/database/MediaDatabase.kt`, `DatabaseModule.kt`, `DaoModule.kt`: schema v8 and Hilt wiring.
- `core/data/.../M3uParser.kt`: pure parsing and deduplication.
- `core/data/.../PlaylistSourceReader.kt`: HTTP(S)/document source I/O and entry resolution.
- `core/data/.../PlaylistRepository.kt`, `LocalPlaylistRepository.kt`: feature-facing rules and mapping.
- `feature/playlist/.../navigation/PlaylistNavigation.kt`: list/detail Navigation 3 keys and entries.
- `feature/playlist/.../screens/list/*`: playlist summaries and creation flows.
- `feature/playlist/.../screens/detail/*`: playback, refresh, drag/TV reorder, delete.
- `feature/playlist/.../composables/PlaylistDialogs.kt`: reusable creation and selection dialogs.
- `feature/videopicker/.../MediaPickerViewModel.kt`, `MediaPickerScreen.kt`: add-to-playlist selection integration.
- `app/.../PlaylistNavGraph.kt`, `TopLevelNavigation.kt`, `MainActivity.kt`: top-level destination and playback bridge.

---

### Task 1: Playlist domain model and Room schema v8

**Files:**
- Create: `core/model/src/main/java/dev/anilbeesetti/nextplayer/core/model/Playlist.kt`
- Create: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/entities/PlaylistEntity.kt`
- Create: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/entities/PlaylistItemEntity.kt`
- Create: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/dao/PlaylistDao.kt`
- Create: `core/database/src/androidTest/java/dev/anilbeesetti/nextplayer/core/database/PlaylistDaoTest.kt`
- Create: `core/database/src/androidTest/java/dev/anilbeesetti/nextplayer/core/database/Migration7To8Test.kt`
- Modify: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/MediaDatabase.kt`
- Modify: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/DatabaseModule.kt`
- Modify: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/DaoModule.kt`
- Modify: `core/database/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`

**Interfaces:**
- Produces: `PlaylistType`, `Playlist`, `PlaylistSummary`, `PlaylistItem`, `PlaylistItemInput`, `PlaylistDao`.
- Produces: `MediaDatabase.MIGRATION_7_8` and schema `8.json` after KSP runs.

- [ ] **Step 1: Add failing DAO and migration tests**

Create instrumentation tests that assert normalized-name uniqueness, URI deduplication, contiguous ordering after move, cascade deletion, and migration from checked-in schema 7:

```kotlin
@RunWith(AndroidJUnit4::class)
class PlaylistDaoTest {
    private lateinit var db: MediaDatabase
    private lateinit var dao: PlaylistDao

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MediaDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.playlistDao()
    }

    @After fun tearDown() = db.close()

    @Test fun duplicateNormalizedNameIsRejected() = runTest {
        dao.insertPlaylist(PlaylistEntity(name = "Movies", normalizedName = "movies", type = "EDITABLE"))
        assertFailsWith<SQLiteConstraintException> {
            dao.insertPlaylist(PlaylistEntity(name = " movies ", normalizedName = "movies", type = "EDITABLE"))
        }
    }

    @Test fun addAndMoveKeepUniqueContiguousOrder() = runTest {
        val id = dao.insertPlaylist(PlaylistEntity(name = "Movies", normalizedName = "movies", type = "EDITABLE"))
        dao.addItems(id, listOf(item(id, "content://1", 0), item(id, "content://2", 1), item(id, "content://1", 2)))
        dao.moveItem(id, "content://2", 0)
        assertEquals(listOf("content://2", "content://1"), dao.getItems(id).map { it.uri })
        assertEquals(listOf(0, 1), dao.getItems(id).map { it.position })
    }
}
```

`Migration7To8Test` must open schema 7, insert an existing `media_state` row, run `MIGRATION_7_8`, validate the database, and assert the old row remains plus both new tables exist.

- [ ] **Step 2: Run the tests and confirm RED**

Run: `./gradlew :core:database:compileDebugAndroidTestKotlin`

Expected: FAIL because playlist entities, DAO, and `MIGRATION_7_8` do not exist.

- [ ] **Step 3: Add domain types**

Create `Playlist.kt` with these exact public contracts:

```kotlin
enum class PlaylistType { EDITABLE, M3U_URL, M3U_FILE }

data class PlaylistItemInput(val uriString: String, val title: String? = null)
data class PlaylistItem(val uriString: String, val title: String?, val position: Int)
data class PlaylistSummary(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    val itemCount: Int,
    val lastRefreshedAt: Long?,
)
data class Playlist(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    val source: String?,
    val items: List<PlaylistItem>,
    val lastRefreshedAt: Long?,
)
```

- [ ] **Step 4: Implement entities, DAO transactions, and migration**

Use a unique `normalized_name` index and composite item primary key:

```kotlin
@Entity(tableName = "playlist", indices = [Index(value = ["normalized_name"], unique = true)])
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    val type: String,
    val source: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "last_refreshed_at") val lastRefreshedAt: Long? = null,
)

@Entity(
    tableName = "playlist_item",
    primaryKeys = ["playlist_id", "uri"],
    foreignKeys = [ForeignKey(
        entity = PlaylistEntity::class,
        parentColumns = ["id"], childColumns = ["playlist_id"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("playlist_id"), Index(value = ["playlist_id", "position"], unique = true)],
)
data class PlaylistItemEntity(
    @ColumnInfo(name = "playlist_id") val playlistId: Long,
    val uri: String,
    val title: String?,
    val position: Int,
)
```

`PlaylistDao` exposes `observeSummaries()`, `observePlaylist(id)`, `getPlaylist(id)`, `getItems(id)`, `insertPlaylist`, `insertItemsIgnore`, `deleteItems`, `updatePlaylist`, and `deletePlaylist`. Its `@Transaction` methods must:

```kotlin
suspend fun addItems(playlistId: Long, items: List<PlaylistItemEntity>): Int
suspend fun insertLinkedPlaylist(playlist: PlaylistEntity, items: List<PlaylistItemEntity>): Long
suspend fun replaceItems(playlistId: Long, items: List<PlaylistItemEntity>, refreshedAt: Long)
suspend fun moveItem(playlistId: Long, uri: String, toIndex: Int)
```

`addItems` loads existing URIs, assigns positions after the current last item, and ignores duplicates. `insertLinkedPlaylist` inserts metadata and parsed items atomically. `replaceItems` deletes then inserts positions `0..n-1` and updates timestamps. `moveItem` loads ordered items, moves the URI to a clamped index, rewrites positions in two phases (`position + itemCount`, then final positions) to avoid the unique position index colliding.

Bump `MediaDatabase` to version 8, add both entities and `playlistDao()`, register `MIGRATION_7_8`, and add exact SQL matching Room's generated column order and nullability. Add `androidx-room-testing` to the catalog and Android test dependencies, and expose `schemas` as Android-test assets.

- [ ] **Step 5: Compile database instrumentation tests and capture schema 8**

Run: `./gradlew :core:database:compileDebugAndroidTestKotlin :core:database:kspDebugKotlin`

Expected: instrumentation tests compile and `core/database/schemas/.../8.json` is generated. The tests run on the dedicated emulator in Task 9.

- [ ] **Step 6: Commit the persistence slice**

```bash
git add core/model core/database gradle/libs.versions.toml
git commit -m "feat: add playlist persistence"
```

---

### Task 2: Pure M3U parser and linked source reader

**Files:**
- Create: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/playlist/M3uParser.kt`
- Create: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/playlist/PlaylistSourceReader.kt`
- Create: `core/data/src/test/java/dev/anilbeesetti/nextplayer/core/data/playlist/M3uParserTest.kt`
- Modify: `core/data/build.gradle.kts`

**Interfaces:**
- Consumes: `PlaylistItemInput`, `PlaylistType`.
- Produces: `M3uParseResult`, `M3uParser.parse`, `PlaylistSourceReader.read`, `PlaylistSourceContent`.

- [ ] **Step 1: Write parser tests first**

Cover extended titles, comments, CRLF/BOM, duplicate first-seen order, remote relative resolution, absolute URI preservation, and skipped local-relative entries:

```kotlin
@Test fun parsesExtendedM3uAndDeduplicates() {
    val text = """#EXTM3U
        #EXTINF:12,First title
        videos/one.mp4
        #EXTINF:-1,Duplicate title
        videos/one.mp4
        https://cdn.example/two.mp4
    """.trimIndent()

    val result = parser.parse(text) { raw -> URI("https://example.test/list.m3u").resolve(raw).toString() }

    assertEquals(listOf("https://example.test/videos/one.mp4", "https://cdn.example/two.mp4"), result.entries.map { it.uriString })
    assertEquals("First title", result.entries.first().title)
    assertEquals(0, result.skippedEntries)
}
```

- [ ] **Step 2: Run parser tests and confirm RED**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*M3uParserTest'`

Expected: FAIL because `M3uParser` and result types are absent.

- [ ] **Step 3: Implement parser and source contracts**

Use these exact contracts:

```kotlin
data class M3uParseResult(val entries: List<PlaylistItemInput>, val skippedEntries: Int)

class M3uParser @Inject constructor() {
    fun parse(content: String, resolveEntry: (String) -> String?): M3uParseResult
}

data class PlaylistSourceContent(
    val text: String,
    val resolveEntry: (String) -> String?,
)

interface PlaylistSourceReader {
    suspend fun read(type: PlaylistType, source: String): PlaylistSourceContent
}
```

`M3uParser` strips a UTF-8 BOM, tracks the next `#EXTINF` title, ignores other comments/blanks, resolves each media line, deduplicates on resolved URI, and counts only nonblank media entries that cannot resolve.

Implement `LocalPlaylistSourceReader` with `@ApplicationContext Context` and an injected `@IoDispatcher CoroutineDispatcher`. HTTP(S) uses `HttpURLConnection` with 10-second connect/read timeouts, redirect support, 2xx validation, and `use { reader.readText() }`. Files use `ContentResolver.openInputStream(Uri.parse(source))`. Remote relative entries use `URI(source).resolve(raw)`; document entries keep absolute URIs and attempt a sibling document URI only when `DocumentsContract.getDocumentId` exposes a slash-delimited parent ID.

- [ ] **Step 4: Run parser tests and compile source reader**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*M3uParserTest' :core:data:compileDebugKotlin`

Expected: parser tests PASS and Android source compilation succeeds.

- [ ] **Step 5: Commit parser and I/O slice**

```bash
git add core/data
git commit -m "feat: parse linked m3u sources"
```

---

### Task 3: Playlist repository rules and transactional refresh

**Files:**
- Create: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/repository/PlaylistRepository.kt`
- Create: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalPlaylistRepository.kt`
- Create: `core/data/src/test/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalPlaylistRepositoryTest.kt`
- Modify: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/DataModule.kt`
- Modify: `core/data/build.gradle.kts`

**Interfaces:**
- Consumes: `PlaylistDao`, `M3uParser`, `PlaylistSourceReader`, playlist domain types.
- Produces: `PlaylistRepository`, `PlaylistRefreshResult`, `PlaylistNameConflictException`, `LinkedPlaylistReadOnlyException`.

- [ ] **Step 1: Write repository behavior tests with a fake DAO and reader**

Tests must prove trimmed/case-folded names, conflict mapping, editable addition, linked-add rejection, linked-reorder rejection, successful replacement, old-cache retention when reading throws, and same-playlist refresh serialization:

```kotlin
@Test fun failedRefreshKeepsCachedItems() = runTest {
    val id = repository.createLinked("News", PlaylistType.M3U_URL, "https://example.test/list.m3u").playlistId
    sourceReader.failure = IOException("offline")

    assertFailsWith<PlaylistSourceException> { repository.refresh(id) }

    assertEquals(listOf("https://example.test/one.mp4"), dao.getItems(id).map { it.uri })
}

@Test fun linkedPlaylistRejectsManualMutation() = runTest {
    val id = repository.createLinked("News", PlaylistType.M3U_URL, source).playlistId
    assertFailsWith<LinkedPlaylistReadOnlyException> {
        repository.addItems(id, listOf(PlaylistItemInput("content://video/1")))
    }
    assertFailsWith<LinkedPlaylistReadOnlyException> { repository.moveItem(id, "content://video/1", 0) }
}
```

- [ ] **Step 2: Run repository tests and confirm RED**

Run: `./gradlew :core:data:testDebugUnitTest --tests '*LocalPlaylistRepositoryTest'`

Expected: FAIL because repository contracts are absent.

- [ ] **Step 3: Implement typed repository API**

```kotlin
data class PlaylistRefreshResult(val playlistId: Long, val itemCount: Int, val skippedEntries: Int)

interface PlaylistRepository {
    fun observePlaylists(): Flow<List<PlaylistSummary>>
    fun observePlaylist(id: Long): Flow<Playlist?>
    suspend fun createEditable(name: String): Long
    suspend fun createLinked(name: String, type: PlaylistType, source: String): PlaylistRefreshResult
    suspend fun addItems(id: Long, items: List<PlaylistItemInput>): Int
    suspend fun moveItem(id: Long, uriString: String, toIndex: Int)
    suspend fun refresh(id: Long): PlaylistRefreshResult
    suspend fun delete(id: Long)
}

class PlaylistNameConflictException : IllegalArgumentException()
class LinkedPlaylistReadOnlyException : IllegalStateException()
class PlaylistSourceException(cause: Throwable) : IOException(cause)
```

`LocalPlaylistRepository` maps DAO flows to domain models, normalizes names with `trim().lowercase(Locale.ROOT)`, validates `M3U_URL`/`M3U_FILE`, and converts SQLite uniqueness failures to `PlaylistNameConflictException`. Use a `Mutex` map keyed by playlist ID so refreshes for different playlists may run concurrently while duplicate refreshes serialize. For linked creation, read and parse first, then call `dao.insertLinkedPlaylist` so no metadata row is ever observable after a failed initial read. For refresh, read and parse before `dao.replaceItems`.

Bind `LocalPlaylistRepository` as `PlaylistRepository` in `DataModule`; provide `LocalPlaylistSourceReader` as `PlaylistSourceReader`.

- [ ] **Step 4: Run all data tests**

Run: `./gradlew :core:data:testDebugUnitTest`

Expected: PASS, including parser and repository tests. Because the root build currently sets `ignoreFailures = true`, inspect `core/data/build/test-results/testDebugUnitTest/*.xml` and confirm `failures="0" errors="0"`.

- [ ] **Step 5: Commit repository slice**

```bash
git add core/data
git commit -m "feat: add playlist repository"
```

---

### Task 4: New playlist module and creation/list screen

**Files:**
- Create: `feature/playlist/build.gradle.kts`
- Create: `feature/playlist/src/main/AndroidManifest.xml`
- Create: `feature/playlist/src/main/res/values/strings.xml`
- Create: `feature/playlist/src/main/java/dev/anilbeesetti/nextplayer/feature/playlist/navigation/PlaylistNavigation.kt`
- Create: `feature/playlist/src/main/java/dev/anilbeesetti/nextplayer/feature/playlist/composables/PlaylistDialogs.kt`
- Create: `feature/playlist/src/main/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/list/PlaylistListViewModel.kt`
- Create: `feature/playlist/src/main/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/list/PlaylistListScreen.kt`
- Create: `feature/playlist/src/test/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/list/PlaylistListViewModelTest.kt`
- Create: `feature/playlist/src/androidTest/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/list/PlaylistListScreenTest.kt`
- Modify: `settings.gradle.kts`

**Interfaces:**
- Consumes: `PlaylistRepository.observePlaylists/createEditable/createLinked/delete`.
- Produces: `PlaylistListRoute`, `PlaylistDetailRoute`, list callbacks, and reusable `PlaylistNameDialog`/`AddM3uUrlDialog`/`PlaylistTargetDialog`.

- [ ] **Step 1: Add module and failing ViewModel/UI tests**

Mirror `feature:network` build configuration, add `libs.reorderable`, `kotlinx-coroutines-test`, Compose UI test dependencies, and `include(":feature:playlist")`.

Test that list state stops loading after its first repository emission, creation events contain the created ID, name conflicts stay in dialog state, linked creation reports progress/errors, file creation passes `M3U_FILE`, and delete delegates by ID. Compose tests assert the FAB opens all three choices and name-conflict text remains visible:

```kotlin
composeRule.onNodeWithContentDescription("Create playlist").performClick()
composeRule.onNodeWithText("Create empty playlist").assertIsDisplayed()
composeRule.onNodeWithText("Add M3U playlist from URL").assertIsDisplayed()
composeRule.onNodeWithText("Add M3U playlist from file").assertIsDisplayed()
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `./gradlew :feature:playlist:compileDebugUnitTestKotlin :feature:playlist:compileDebugAndroidTestKotlin`

Expected: FAIL because list state, ViewModel, and screen do not exist.

- [ ] **Step 3: Implement list ViewModel state/actions**

Use explicit contracts:

```kotlin
data class PlaylistListUiState(
    val playlists: List<PlaylistSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val formError: String? = null,
)

sealed interface PlaylistListAction {
    data class CreateEditable(val name: String) : PlaylistListAction
    data class CreateLinked(val name: String, val type: PlaylistType, val source: String) : PlaylistListAction
    data class Delete(val id: Long) : PlaylistListAction
    data object ClearFormError : PlaylistListAction
}

sealed interface PlaylistListEvent {
    data class Created(val playlistId: Long) : PlaylistListEvent
    data class Message(val text: String) : PlaylistListEvent
}
```

Collect summaries with `stateIn(WhileSubscribed(5_000))`. Serialize create actions, keep `isSaving` true during repository work, map name conflict to the exact copy, and emit `Created` only after success.

- [ ] **Step 4: Implement chooser, forms, document picker, and list**

`PlaylistListScreenRoute` observes state/events and uses `rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument())`. On file selection call:

```kotlin
context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
```

Then show a name form prefilled from `OpenableColumns.DISPLAY_NAME` without `.m3u`/`.m3u8`. Use `NextDialog`, `OutlinedTextField`, `NextTopAppBar`, `NextSegmentedListItem`, TV focus helpers, an Extended FAB with `NextIcons.Add`, an explicit empty state, source/item-count supporting text, and a delete confirmation dialog. Required string resources include all visible labels, validation/error copy, plurals for item counts, and accessibility descriptions.

- [ ] **Step 5: Run ViewModel tests and compile Compose instrumentation tests**

Run: `./gradlew :feature:playlist:testDebugUnitTest :feature:playlist:compileDebugAndroidTestKotlin`

Expected: all list ViewModel tests PASS and Compose tests compile. They run on the dedicated emulator in Task 9; inspect host test XML for zero failures/errors.

- [ ] **Step 6: Commit list/create slice**

```bash
git add settings.gradle.kts feature/playlist
git commit -m "feat: add playlist creation screen"
```

---

### Task 5: Playlist detail, playback, refresh, and editable reordering

**Files:**
- Create: `feature/playlist/src/main/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/detail/PlaylistDetailViewModel.kt`
- Create: `feature/playlist/src/main/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/detail/PlaylistDetailScreen.kt`
- Create: `feature/playlist/src/test/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/detail/PlaylistDetailViewModelTest.kt`
- Create: `feature/playlist/src/androidTest/java/dev/anilbeesetti/nextplayer/feature/playlist/screens/detail/PlaylistDetailScreenTest.kt`
- Modify: `feature/playlist/src/main/java/dev/anilbeesetti/nextplayer/feature/playlist/navigation/PlaylistNavigation.kt`
- Modify: `feature/playlist/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PlaylistRepository.observePlaylist/refresh/moveItem/delete`.
- Produces: detail route callback `onPlayPlaylist(uris: List<Uri>, startUri: Uri)` and reorder/refresh UI.

- [ ] **Step 1: Write failing detail ViewModel tests**

Assert Play All uses the first URI, item click keeps the full ordered list and selected start URI, refresh is rejected for editable lists, refresh state resets on failure, linked lists cannot move, and editable move delegates URI plus destination:

```kotlin
@Test fun playItemEmitsFullListAndSelectedStart() = runTest {
    repository.emit(playlist(items = listOf(item("content://1", 0), item("content://2", 1))))
    viewModel.onAction(PlaylistDetailAction.PlayItem("content://2"))
    assertEquals(
        PlaylistDetailEvent.Play(listOf("content://1", "content://2"), "content://2"),
        viewModel.events.first(),
    )
}
```

- [ ] **Step 2: Run detail tests and confirm RED**

Run: `./gradlew :feature:playlist:testDebugUnitTest --tests '*PlaylistDetailViewModelTest'`

Expected: FAIL because detail contracts are absent.

- [ ] **Step 3: Implement detail state and actions**

```kotlin
data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
)
sealed interface PlaylistDetailAction {
    data object PlayAll : PlaylistDetailAction
    data class PlayItem(val uri: String) : PlaylistDetailAction
    data object Refresh : PlaylistDetailAction
    data class MoveItem(val uri: String, val toIndex: Int) : PlaylistDetailAction
    data object Delete : PlaylistDetailAction
}
sealed interface PlaylistDetailEvent {
    data class Play(val uris: List<String>, val startUri: String) : PlaylistDetailEvent
    data class Message(val text: String) : PlaylistDetailEvent
    data object Deleted : PlaylistDetailEvent
}
```

Use an assisted `playlistId` ViewModel factory, observe the playlist, gate refresh/move by type, and always reset progress in `finally`.

- [ ] **Step 4: Implement detail UI and reorder interactions**

Use `PullToRefreshBox` only for linked types. Render items with stable URI keys and `rememberReorderableLazyListState`. Maintain an optimistic `SnapshotStateList`; update it in the reorder callback, then dispatch `MoveItem(movedUri, finalIndex)` from the handle's `onDragStopped`. Reconcile the optimistic list whenever the repository emits a different persisted order.

```kotlin
var pendingMove by remember { mutableStateOf<Pair<String, Int>?>(null) }
val displayedItems = remember { mutableStateListOf<PlaylistItem>() }
val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
    val moved = displayedItems.removeAt(from.index)
    displayedItems.add(to.index, moved)
    pendingMove = moved.uriString to to.index
}

ReorderableItem(reorderState, key = item.uriString) {
    PlaylistItemRow(
        item = item,
        modifier = Modifier.draggableHandle(
            dragGestureDetector = DragGestureDetector.LongPress,
            onDragStopped = {
                pendingMove?.let { (uri, index) ->
                    onAction(PlaylistDetailAction.MoveItem(uri, index))
                }
                pendingMove = null
            },
        ),
    )
}
```

On television/keyboard layouts, replace the drag handle with focusable Move up/Move down icon buttons, disabled at the edges. Linked items have no reorder controls. Provide Play All in the app bar, an empty state, last-refresh copy, delete confirmation, snackbar results, and callbacks that emit the full list with selected start URI.

- [ ] **Step 5: Run detail unit tests and compile UI tests**

Run: `./gradlew :feature:playlist:testDebugUnitTest :feature:playlist:compileDebugAndroidTestKotlin`

Expected: all list/detail ViewModel tests PASS and linked-screen/reorder Compose tests compile for Task 9.

- [ ] **Step 6: Commit detail slice**

```bash
git add feature/playlist
git commit -m "feat: add playlist playback and refresh"
```

---

### Task 6: Add selected media to editable playlists

**Files:**
- Create: `feature/videopicker/src/test/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/mediapicker/PlaylistSelectionMapperTest.kt`
- Modify: `feature/videopicker/src/main/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/mediapicker/MediaPickerViewModel.kt`
- Modify: `feature/videopicker/src/main/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/mediapicker/MediaPickerScreen.kt`
- Modify: `feature/videopicker/build.gradle.kts`
- Modify: `core/ui/src/main/java/dev/anilbeesetti/nextplayer/core/ui/designsystem/NextIcons.kt`
- Modify: `core/ui/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `PlaylistRepository`, `PlaylistSummary`, `PlaylistItemInput`.
- Produces: editable target state, add/create-and-add actions, and success event used to close selection mode.

- [ ] **Step 1: Add failing mapping and ViewModel behavior tests**

Extract the existing selected-item conversion to an internal suspend function and test the exact delete parity:

```kotlin
@Test fun folderSelectionUsesDirectChildrenInFolderMode() = runTest {
    val result = resolver.resolve(setOf(SelectionItem.Folder("/Movies", "Movies")), MediaViewMode.FOLDERS)
    assertEquals(listOf("content://direct"), result.map { it.uriString })
}

@Test fun overlappingFolderAndVideoSelectionDeduplicatesFirstSeenOrder() = runTest {
    val result = resolver.resolve(overlappingSelection, MediaViewMode.LIST)
    assertEquals(listOf("content://1", "content://2"), result.map { it.uriString })
}
```

Add ViewModel tests that only editable summaries enter `editablePlaylists`, successful add emits `PlaylistItemsAdded`, a repository failure keeps the add dialog state, and create-and-add creates first then adds the pending items.

- [ ] **Step 2: Run tests and confirm RED**

Run: `./gradlew :feature:videopicker:testDebugUnitTest --tests '*Playlist*'`

Expected: FAIL because playlist selection contracts are absent.

- [ ] **Step 3: Extend media picker state/actions/events**

Add `PlaylistRepository` to `MediaPickerViewModel`, collect editable summaries, and define:

```kotlin
data class AddToPlaylistState(
    val isVisible: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val completionToken: Long = 0,
)

data class MediaPickerUiState(
    // existing fields unchanged
    val editablePlaylists: List<PlaylistSummary> = emptyList(),
    val addToPlaylistState: AddToPlaylistState = AddToPlaylistState(),
)

// Add to MediaPickerAction:
data class ShowAddToPlaylist(val selectionItems: Set<SelectionItem>) : MediaPickerAction
data class AddSelectionToPlaylist(val playlistId: Long) : MediaPickerAction
data class CreatePlaylistAndAddSelection(val name: String) : MediaPickerAction
data object DismissAddToPlaylist : MediaPickerAction

// Add to MediaPickerEvent:
data class PlaylistItemsAdded(val count: Int) : MediaPickerEvent
```

Store resolved pending inputs privately in the ViewModel when `ShowAddToPlaylist` runs. Do not clear them on a failed add. After the repository completes, clear them, hide the dialog, increment `completionToken`, and emit success.

- [ ] **Step 4: Add selection action and target/create dialogs**

Add `NextIcons.PlaylistAdd`. Extend `SelectionActionsSheet` with `onAddToPlaylistAction`, placed after Play. It dispatches `ShowAddToPlaylist(selectionManager.selectionItems)` without exiting selection mode.

```kotlin
SelectionAction(
    modifier = actionUpModifier,
    isTv = isTv,
    imageVector = NextIcons.PlaylistAdd,
    title = stringResource(R.string.add_to_playlist),
    onClick = {
        onAction(MediaPickerAction.ShowAddToPlaylist(selectionManager.selectionItems))
    },
)
```

Render `PlaylistTargetDialog` with editable summaries plus “Create new playlist.” The nested name form dispatches `CreatePlaylistAndAddSelection`. In `MediaPickerRoute`, handle `PlaylistItemsAdded` by showing a quantity-aware toast/snackbar. In `MediaPickerScreen`, use `LaunchedEffect(uiState.addToPlaylistState.completionToken)` with an initial-token guard to call `selectionManager.exitSelectionMode()` only after a successful add; failure leaves the dialog and selection intact.

- [ ] **Step 5: Run video-picker tests and compile UI**

Run: `./gradlew :feature:videopicker:testDebugUnitTest :feature:videopicker:compileDebugKotlin`

Expected: all playlist mapper/ViewModel tests PASS and media selection UI compiles.

- [ ] **Step 6: Commit media selection slice**

```bash
git add feature/videopicker core/ui
git commit -m "feat: add selected media to playlists"
```

---

### Task 7: Top-level Playlists navigation and playback bridge

**Files:**
- Create: `app/src/main/java/dev/anilbeesetti/nextplayer/navigation/PlaylistNavGraph.kt`
- Modify: `app/src/main/java/dev/anilbeesetti/nextplayer/navigation/TopLevelNavigation.kt`
- Modify: `app/src/main/java/dev/anilbeesetti/nextplayer/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`
- Modify: `core/ui/src/main/java/dev/anilbeesetti/nextplayer/core/ui/designsystem/NextIcons.kt`
- Create: `app/src/test/java/dev/anilbeesetti/nextplayer/navigation/TopLevelNavigationTest.kt`

**Interfaces:**
- Consumes: `PlaylistListRoute`, `PlaylistDetailRoute`, playlist entry builders.
- Produces: independent Playlists tab stack and `Context.startPlayback(uris, startUri)`.

- [ ] **Step 1: Write failing top-level state test**

Assert destination order is Media, Playlists, Network and switching tabs preserves each playlist detail stack:

```kotlin
@Test fun playlistsIsASeparateMiddleTopLevelDestination() {
    assertEquals(
        listOf(TopLevelDestination.MEDIA, TopLevelDestination.PLAYLISTS, TopLevelDestination.NETWORK),
        TopLevelDestination.entries,
    )
}
```

- [ ] **Step 2: Run test and confirm RED**

Run: `./gradlew :app:testDebugUnitTest --tests '*TopLevelNavigationTest'`

Expected: FAIL because `PLAYLISTS` is absent.

- [ ] **Step 3: Integrate module, top-level route, and app graph**

Add `implementation(project(":feature:playlist"))`. Add `NextIcons.Playlist` using the rounded queue/music icon. Define:

```kotlin
PLAYLISTS(PlaylistListRoute, NextIcons.Playlist, PlaylistR.string.playlists)
```

Create `PlaylistNavGraph.kt` that opens details on the playlist stack, returns with `removeLastOrNull`, routes settings on the current stack, and starts playback with the complete list plus selected URI. Generalize playback without breaking existing callers:

```kotlin
internal fun Context.startPlayback(
    uris: List<Uri>,
    startUri: Uri = uris.first(),
    grantReadPermission: Boolean = false,
) {
    val intent = Intent(this, PlayerActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = startUri
        if (uris.size > 1) putParcelableArrayListExtra(PlayerApi.API_PLAYLIST, ArrayList(uris))
        if (grantReadPermission) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(intent)
}
```

In `MainActivity`, retrieve the playlist stack, register `playlistNavGraph`, and retain settings navigation on `navState.currentStack`.

Because URL playlists explicitly support both HTTP and HTTPS and the app already accepts user-entered network streams, set `android:usesCleartextTraffic="true"` on the application. HTTPS remains preferred in UI helper copy; this opt-in is required for user-owned LAN media servers and the isolated emulator fixture.

- [ ] **Step 4: Run navigation and app compilation tests**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`

Expected: top-level test PASS and debug APK builds.

- [ ] **Step 5: Commit navigation slice**

```bash
git add app core/ui
git commit -m "feat: add playlists navigation tab"
```

---

### Task 8: Full automated verification and focused fixes

**Files:**
- Modify only files already in scope when a failing test, lint finding, or generated schema requires a correction.

**Interfaces:**
- Consumes: all preceding feature slices.
- Produces: a clean debug build, test reports with zero failures, checked-in schema 8, and no new lint/format violations.

- [ ] **Step 1: Run formatting and static checks**

Run: `./gradlew ktlintCheck lintDebug`

Expected: BUILD SUCCESSFUL with no playlist-related lint findings.

- [ ] **Step 2: Run all host unit tests and inspect ignored-failure reports**

Run: `./gradlew testDebugUnitTest`

Then run: `rg -n 'failures="[1-9]|errors="[1-9]' . -g 'TEST-*.xml' -g '!**/build/intermediates/**'`

Expected: Gradle completes and `rg` returns no matching failing test suites.

- [ ] **Step 3: Assemble instrumentation APKs for the dedicated emulator**

Run: `./gradlew :core:database:assembleDebugAndroidTest :feature:playlist:assembleDebugAndroidTest`

Expected: database and playlist instrumentation APKs build. Migration, DAO, dialog, and detail/reorder tests run after Task 9 creates the dedicated emulator.

- [ ] **Step 4: Assemble final APK and verify generated schema**

Run: `./gradlew :app:assembleDebug && test -f core/database/schemas/dev.anilbeesetti.nextplayer.core.database.MediaDatabase/8.json`

Expected: `app/build/outputs/apk/debug/app-debug.apk` and schema `8.json` exist.

- [ ] **Step 5: Commit verification corrections if any**

```bash
git add app core feature gradle settings.gradle.kts
git commit -m "test: verify playlist feature"
```

Skip this commit when verification required no corrections.

---

### Task 9: Dedicated emulator creation, end-to-end QA, and deletion

**Files:**
- Create: `qa/playlists/linked.m3u`
- Create: `qa/playlists/linked-refreshed.m3u`
- Create: `docs/superpowers/qa/2026-07-15-playlists-emulator.md`

**Interfaces:**
- Consumes: debug APK, Android SDK tools, adb, controlled local HTTP source.
- Produces: UI-tree/screenshots/logcat evidence and removal of only the newly created AVD.

- [ ] **Step 1: Invoke Android QA skills and inventory existing devices**

Use `android-cli` and `test-android-apps:android-emulator-qa`. Record `emulator -list-avds` and `adb devices` before creation. Choose the dedicated name `nextplayer_playlist_qa_api37`; if that exact name already exists, use `nextplayer_playlist_qa_api37_<timestamp>` and record it.

- [ ] **Step 2: Create and boot a fresh API 37 emulator**

Use the installed API 37 Google APIs image matching the host ABI. If absent, request approval to install only that system image. Create the AVD with a Pixel 6 hardware profile, boot with snapshots disabled and wiped data, and wait for `sys.boot_completed=1`.

```bash
AVD_NAME=nextplayer_playlist_qa_api37
IMAGE='system-images;android-37;google_apis;arm64-v8a'
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd --name "$AVD_NAME" --package "$IMAGE" --device pixel_6 --force
"$ANDROID_HOME/emulator/emulator" -avd "$AVD_NAME" -no-snapshot -wipe-data
adb wait-for-device
adb shell getprop sys.boot_completed
```

Expected: the new emulator is the only target selected for subsequent adb commands; no pre-existing AVD is started or changed.

- [ ] **Step 3: Seed two valid local videos and controlled linked source**

Create two short MP4s on the emulator using `screenrecord --time-limit 2` under `/sdcard/Movies`, broadcast `android.intent.action.MEDIA_SCANNER_SCAN_FILE`, pull copies into `/tmp/nextplayer-playlist-qa/`, copy the two tracked M3U fixtures there, and serve that temporary directory on host port 8765. `linked.m3u` initially contains:

```bash
adb shell mkdir -p /sdcard/Movies
adb shell screenrecord --time-limit 2 /sdcard/Movies/playlist-qa-one.mp4
adb shell screenrecord --time-limit 2 /sdcard/Movies/playlist-qa-two.mp4
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Movies/playlist-qa-one.mp4
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Movies/playlist-qa-two.mp4
mkdir -p /tmp/nextplayer-playlist-qa
adb pull /sdcard/Movies/playlist-qa-one.mp4 /tmp/nextplayer-playlist-qa/
adb pull /sdcard/Movies/playlist-qa-two.mp4 /tmp/nextplayer-playlist-qa/
cp qa/playlists/linked.m3u /tmp/nextplayer-playlist-qa/linked.m3u
python3 -m http.server 8765 --directory /tmp/nextplayer-playlist-qa
```

```m3u
#EXTM3U
#EXTINF:-1,Playlist QA One
http://10.0.2.2:8765/playlist-qa-one.mp4
```

`linked-refreshed.m3u` contains the first entry plus `Playlist QA Two`. Copy the initial fixture to the served `linked.m3u` before launching the app.

- [ ] **Step 4: Run all instrumentation suites on the dedicated emulator**

Run: `./gradlew :core:database:connectedDebugAndroidTest :feature:playlist:connectedDebugAndroidTest`

Expected: migration, DAO, creation-dialog, detail, linked-read-only, and reorder tests PASS on the new API 37 emulator. Inspect the instrumentation XML and HTML reports for zero failures.

- [ ] **Step 5: Install and launch the app with clean data**

Run: `./gradlew :app:installDebug`

Then clear only `dev.anilbeesetti.nextplayer.debug`, launch `MainActivity`, grant requested media permissions through UI, and capture initial logcat baseline.

- [ ] **Step 6: Exercise editable playlist flow**

Using adb input plus UI-tree assertions:

1. Open Playlists and assert the empty state.
2. Create `QA Editable` and assert it appears with zero items.
3. Return Home, long-press both seeded videos, choose Add to playlist, and select `QA Editable`.
4. Assert success and that selection mode closes.
5. Open `QA Editable`, start playback from the second item, and verify PlayerActivity receives both playlist URIs with the second as current.
6. Return, reorder the second item above the first, leave details, reopen, and assert persisted order.

Capture a screenshot and UI XML after creation, addition, and persisted reorder.

- [ ] **Step 7: Exercise linked URL refresh and read-only behavior**

Create `QA Linked` from `http://10.0.2.2:8765/linked.m3u`. Assert one cached item and no reorder controls. Replace the served file contents with `linked-refreshed.m3u`, swipe to refresh, and assert two items plus an updated refresh result. Return Home, long-press a video, open Add to playlist, and assert `QA Linked` is absent.

Capture screenshots/UI XML before and after refresh and inspect logcat for `FATAL EXCEPTION`, Room migration failures, permission failures, or StrictMode/network-on-main-thread errors.

- [ ] **Step 8: Delete test data through the UI**

Delete `QA Editable` and `QA Linked` using their confirmation dialogs and assert the Playlists empty state returns.

- [ ] **Step 9: Write QA evidence**

Document AVD name/API/ABI, APK path, each assertion, screenshot paths, UI XML paths, relevant logcat result, and any limitations in `docs/superpowers/qa/2026-07-15-playlists-emulator.md`.

- [ ] **Step 10: Shut down and delete only the dedicated AVD**

Stop the host fixture server, issue `adb -s <dedicated-serial> emu kill`, wait for disconnect, delete the recorded dedicated AVD, and compare `emulator -list-avds` with the Step 1 inventory.

```bash
adb -s "$DEDICATED_SERIAL" emu kill
"$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" delete avd --name "$AVD_NAME"
"$ANDROID_HOME/emulator/emulator" -list-avds
```

Expected: the dedicated AVD is gone and every pre-existing AVD remains listed unchanged.

- [ ] **Step 11: Commit QA fixtures and report**

```bash
git add qa/playlists docs/superpowers/qa/2026-07-15-playlists-emulator.md
git commit -m "test: validate playlists on fresh emulator"
```

---

## Final Completion Check

Before claiming completion, invoke `superpowers:verification-before-completion`, rerun the relevant build/test checks, verify the dedicated emulator no longer exists, inspect `git status --short`, and report exact evidence plus any remaining limitations.
