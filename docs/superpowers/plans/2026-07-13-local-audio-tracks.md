# Local Audio Tracks Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users attach multiple local audio documents to a video, select them beside embedded tracks, and restore them on later playback.

**Architecture:** Persist ordered external-audio URIs in `media_state`, serialize them into each restored `MediaItem`, and wrap Media3's normal `MediaSource.Factory` with a factory that merges the video source and one progressive source per external audio URI. Mirror the existing subtitle picker and MediaSession-command flow for the UI and immediate attachment.

**Tech Stack:** Kotlin 2.4, Android SDK 37, Room 2.8.4, Media3 1.10.1, Jetpack Compose, JUnit4/AndroidX Test, Android CLI/adb.

## Global Constraints

- Every distinct picked audio URI remains selectable for that video; duplicate URI picks are idempotent.
- Persistable Storage Access Framework read permission is required before storing an attachment.
- Existing playback state must survive Room migration 7 to 8.
- External audio begins at media time zero; delay, remuxing, transcoding, removal, and automatic discovery are out of scope.
- Production behavior is added only after a focused test has failed for the expected missing-feature reason.
- The test AVD is temporary and must be stopped and removed on success or failure.

---

### Task 1: Create the temporary test device

**Files:** None.

**Interfaces:**
- Produces: a booted `small_phone` AVD visible through `adb devices` for red/green instrumentation tests and final QA.

- [ ] **Step 1: Confirm the name is unused**

Run: `android emulator list`

Expected: no device named `small_phone`.

- [ ] **Step 2: Create and start the AVD**

Run: `android emulator create small_phone`

Run: `android emulator start --cold small_phone`

Expected: the command returns only after Android has booted.

- [ ] **Step 3: Record the serial and baseline state**

Run: `adb devices`

Expected: exactly one new `emulator-*` entry in state `device`. Save that serial as `DEVICE` for every adb command.

---

### Task 2: Persist ordered external audio URIs and migrate Room

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/database/build.gradle.kts`
- Modify: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/MediaDatabase.kt`
- Modify: `core/database/src/main/java/dev/anilbeesetti/nextplayer/core/database/entities/MediumStateEntity.kt`
- Create: `core/database/src/androidTest/java/dev/anilbeesetti/nextplayer/core/database/Migration7To8Test.kt`
- Modify: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/models/VideoState.kt`
- Modify: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/mappers/ToVideoState.kt`
- Modify: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/repository/MediaRepository.kt`
- Modify: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalMediaRepository.kt`
- Create: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/repository/ExternalAudioPersistence.kt`
- Modify: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/repository/fake/FakeMediaRepository.kt`
- Create: `core/data/src/androidTest/java/dev/anilbeesetti/nextplayer/core/data/repository/ExternalAudioPersistenceTest.kt`

**Interfaces:**
- Produces: `VideoState.externalAudioTracks: List<Uri>` and `MediaRepository.addExternalAudioTrackToMedium(uri: String, audioUri: Uri)`.
- Produces: `MediaDatabase.MIGRATION_7_8` and schema version 8 with `media_state.external_audio_tracks TEXT NOT NULL DEFAULT ''`.

- [ ] **Step 1: Add Room's migration-test artifact**

Add to `gradle/libs.versions.toml`:

```toml
androidx-room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

Add to `core/database/build.gradle.kts`:

```kotlin
androidTestImplementation(libs.androidx.room.testing)
```

Also expose the exported schemas to the migration helper:

```kotlin
sourceSets {
    getByName("androidTest").assets.srcDir("$projectDir/schemas")
}
```

- [ ] **Step 2: Write the failing 7-to-8 migration test**

Create `Migration7To8Test.kt` with a `MigrationTestHelper`, create version 7, insert a `media_state` row containing non-default playback and subtitle values, run `MediaDatabase.MIGRATION_7_8`, and assert both:

```kotlin
assertEquals("content://video/1", cursor.getString(cursor.getColumnIndexOrThrow("uri")))
assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("external_audio_tracks")))
```

Also assert the inserted `playback_position`, `audio_track_index`, `subtitle_track_index`, and `external_subs` values are unchanged.

- [ ] **Step 3: Run the migration test to verify RED**

Run: `./gradlew :core:database:connectedDebugAndroidTest --console=plain`

Expected: FAIL because database version 8 and `MIGRATION_7_8` do not exist.

- [ ] **Step 4: Implement schema version 8 and migration**

Change `MediaDatabase` to version 8 and add:

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `media_state` ADD COLUMN `external_audio_tracks` TEXT NOT NULL DEFAULT ''",
        )
    }
}
```

Register it in `DatabaseModule`, and add to `MediumStateEntity`:

```kotlin
@ColumnInfo(name = "external_audio_tracks")
val externalAudioTracks: String = "",
```

- [ ] **Step 5: Run the migration test to verify GREEN and export schema 8**

Run: `./gradlew :core:database:connectedDebugAndroidTest --console=plain`

Expected: PASS and `core/database/schemas/dev.anilbeesetti.nextplayer.core.database.MediaDatabase/8.json` is generated.

- [ ] **Step 6: Write the failing ordered/deduplicated persistence test**

Create `ExternalAudioPersistenceTest.kt` with an in-memory Room database. Call `MediumStateDao.addExternalAudioTrack` three times for `audio/one`, `audio/two`, and `audio/one`, then assert:

```kotlin
assertEquals(
    listOf(Uri.parse("content://audio/one"), Uri.parse("content://audio/two")),
    UriListConverter.fromStringToList(database.mediumStateDao().get(videoUri)!!.externalAudioTracks),
)
```

- [ ] **Step 7: Run the persistence test to verify RED**

Run: `./gradlew :core:data:connectedDebugAndroidTest --console=plain`

Expected: FAIL because the external-audio API and state field do not exist.

- [ ] **Step 8: Implement the repository and model path**

Add `externalAudioTracks: List<Uri>` to `VideoState`, map it with `UriListConverter`, and add this repository API:

```kotlin
suspend fun addExternalAudioTrackToMedium(uri: String, audioUri: Uri)
```

Implement the storage operation as an internal DAO extension in `ExternalAudioPersistence.kt` so it can be tested with a real in-memory Room database:

```kotlin
internal suspend fun MediumStateDao.addExternalAudioTrack(uri: String, audioUri: Uri) {
    val state = get(uri) ?: MediumStateEntity(uriString = uri)
    val current = UriListConverter.fromStringToList(state.externalAudioTracks)
    if (audioUri in current) return
    upsert(
        state.copy(
            externalAudioTracks = UriListConverter.fromListToString(current + audioUri),
            lastPlayedTime = System.currentTimeMillis(),
        ),
    )
}
```

Delegate the public repository method to this extension:

```kotlin
override suspend fun addExternalAudioTrackToMedium(uri: String, audioUri: Uri) {
    mediumStateDao.addExternalAudioTrack(uri, audioUri)
}
```

Add a no-op override to `FakeMediaRepository`.

- [ ] **Step 9: Verify the persistence path**

Run: `./gradlew :core:data:connectedDebugAndroidTest :core:database:connectedDebugAndroidTest --console=plain`

Expected: PASS.

- [ ] **Step 10: Commit the persistence slice**

```bash
git add gradle/libs.versions.toml core/database core/data
git commit -m "feat: persist external audio tracks"
```

---

### Task 3: Encode external audio on media items and merge sources

**Files:**
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/extensions/MediaItem.kt`
- Create: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/service/ExternalAudioMediaSourceFactory.kt`
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/service/PlayerService.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `feature/player/build.gradle.kts`
- Create: `feature/player/src/androidTest/java/dev/anilbeesetti/nextplayer/feature/player/service/ExternalAudioMediaSourceFactoryTest.kt`

**Interfaces:**
- Consumes: `VideoState.externalAudioTracks`.
- Produces: `MediaMetadata.externalAudioTrackUris: List<Uri>` and an `externalAudioTrackUris` parameter on the existing media metadata/copy helpers.
- Produces: `ExternalAudioMediaSourceFactory(delegate: MediaSource.Factory)`.

- [ ] **Step 1: Write failing media-item/factory tests**

Add this version-catalog entry:

```toml
androidx-media3-test-utils = { group = "androidx.media3", name = "media3-test-utils", version.ref = "androidxMedia3" }
```

Add `androidTestImplementation(libs.androidx.media3.test.utils)` to `feature/player`, then use `FakeMediaSource` in the recording factory.

Test that two URI strings round-trip through a built `MediaMetadata`, that a media item without external audio delegates unchanged, and that a media item with two URIs asks the delegate to create three child sources in this exact order:

```kotlin
assertEquals(
    listOf(videoUri, firstAudioUri, secondAudioUri),
    recordingFactory.createdItems.map { it.localConfiguration!!.uri },
)
assertTrue(result is MergingMediaSource)
```

Use a small recording `MediaSource.Factory` backed by Media3 test utilities so the test exercises the real wrapper rather than a mock.

- [ ] **Step 2: Run the factory test to verify RED**

Run: `./gradlew :feature:player:connectedDebugAndroidTest --console=plain`

Expected: FAIL because metadata helpers and `ExternalAudioMediaSourceFactory` do not exist.

- [ ] **Step 3: Implement metadata serialization**

In `MediaItem.kt`, store URI strings through the existing bundle helper so every metadata value is written together:

```kotlin
private const val MEDIA_METADATA_EXTERNAL_AUDIO_TRACK_URIS_KEY = "external_audio_track_uris"

val MediaMetadata.externalAudioTrackUris: List<Uri>
    get() = extras
        ?.getStringArrayList(MEDIA_METADATA_EXTERNAL_AUDIO_TRACK_URIS_KEY)
        .orEmpty()
        .map(Uri::parse)

private fun Bundle.setExternalAudioTrackUris(uris: List<Uri>) = apply {
    putStringArrayList(
        MEDIA_METADATA_EXTERNAL_AUDIO_TRACK_URIS_KEY,
        ArrayList(uris.map(Uri::toString)),
    )
}
```

Add `externalAudioTrackUris: List<Uri> = emptyList()` to `MediaMetadata.Builder.setExtras`, and `externalAudioTrackUris: List<Uri> = mediaMetadata.externalAudioTrackUris` to `MediaItem.copy`. Both paths call `Bundle.setExternalAudioTrackUris`, ensuring unrelated metadata survives item replacement.

- [ ] **Step 4: Implement the merging factory**

Create a `MediaSource.Factory` wrapper whose `createMediaSource` is:

```kotlin
override fun createMediaSource(mediaItem: MediaItem): MediaSource {
    val primary = delegate.createMediaSource(mediaItem)
    val audioSources = mediaItem.mediaMetadata.externalAudioTrackUris.map { uri ->
        delegate.createMediaSource(MediaItem.fromUri(uri))
    }
    return if (audioSources.isEmpty()) {
        primary
    } else {
        MergingMediaSource(primary, *audioSources.toTypedArray())
    }
}
```

Forward `supportedTypes`, DRM provider, and load-error policy methods to the delegate and return the wrapper for fluent calls.

- [ ] **Step 5: Wire restored state into PlayerService**

Construct the player with:

```kotlin
.setMediaSourceFactory(
    ExternalAudioMediaSourceFactory(DefaultMediaSourceFactory(applicationContext)),
)
```

When enriching each media item, pass `videoState?.externalAudioTracks.orEmpty()` into its metadata extras.

- [ ] **Step 6: Verify the factory and compile the player**

Run: `./gradlew :feature:player:connectedDebugAndroidTest :feature:player:testDebugUnitTest :feature:player:assembleDebug --console=plain`

Expected: PASS.

- [ ] **Step 7: Commit the playback foundation**

```bash
git add feature/player
git commit -m "feat: merge external audio media sources"
```

---

### Task 4: Add the MediaSession attachment command

**Files:**
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/service/CustomCommands.kt`
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/service/PlayerService.kt`
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/extensions/Player.kt`
- Create: `feature/player/src/androidTest/java/dev/anilbeesetti/nextplayer/feature/player/extensions/ExternalAudioAttachmentTest.kt`

**Interfaces:**
- Produces: `CustomCommands.ADD_AUDIO_TRACK`, `AUDIO_TRACK_URI_KEY`, and `MediaController.addAudioTrack(uri: Uri)`.
- Produces: `Player.addAdditionalAudioTrack(uri: Uri)` that replaces the current item without losing position or play state.

- [ ] **Step 1: Write failing attachment-state tests**

Using Media3's test player, start a current item at a non-zero position with one existing external URI. Call the desired helper with a second URI and assert:

```kotlin
assertEquals(positionBefore, player.currentPosition)
assertEquals(playWhenReadyBefore, player.playWhenReady)
assertEquals(
    listOf(firstAudioUri, secondAudioUri),
    player.currentMediaItem!!.mediaMetadata.externalAudioTrackUris,
)
```

Add a duplicate test proving the current item is unchanged when the URI is already present.

- [ ] **Step 2: Run the attachment test to verify RED**

Run: `./gradlew :feature:player:connectedDebugAndroidTest --console=plain`

Expected: FAIL because the helper and command do not exist.

- [ ] **Step 3: Implement controller command arguments**

Add the enum value/key and:

```kotlin
fun MediaController.addAudioTrack(uri: Uri) {
    val args = Bundle().apply {
        putString(CustomCommands.AUDIO_TRACK_URI_KEY, uri.toString())
    }
    sendCustomCommand(CustomCommands.ADD_AUDIO_TRACK.sessionCommand, args)
}
```

- [ ] **Step 4: Implement current-item attachment**

Add a focused player extension that copies the current metadata with the appended distinct URI, replaces only the current playlist item, seeks back to the captured index/position, and restores `playWhenReady`. Do not modify sibling playlist entries.

- [ ] **Step 5: Handle the service command**

In `onCustomCommand`, reject a missing/blank URI with `SessionError.ERROR_BAD_VALUE`. For a valid URI, capture the currently supported audio group count, persist position, persist the new selected audio index, persist the URI with `addExternalAudioTrackToMedium`, and call `player.addAdditionalAudioTrack(audioUri)`. Return `SessionResult.RESULT_SUCCESS`.

- [ ] **Step 6: Verify command and attachment behavior**

Run: `./gradlew :feature:player:connectedDebugAndroidTest :feature:player:testDebugUnitTest --console=plain`

Expected: PASS.

- [ ] **Step 7: Commit the command slice**

```bash
git add feature/player core/data
git commit -m "feat: attach audio tracks during playback"
```

---

### Task 5: Add the local-audio picker to the selector

**Files:**
- Modify: `core/ui/src/main/res/values/strings.xml`
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/ui/AudioTrackSelectorView.kt`
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/ui/OverlayShowView.kt`
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/MediaPlayerScreen.kt`
- Modify: `feature/player/src/main/java/dev/anilbeesetti/nextplayer/feature/player/PlayerActivity.kt`
- Create: `feature/player/src/androidTest/java/dev/anilbeesetti/nextplayer/feature/player/extensions/OpenDocumentAtInitialUriTest.kt`

**Interfaces:**
- Consumes: `MediaController.addAudioTrack(Uri)`.
- Produces: `onSelectAudioClick: () -> Unit` from selector through screen to activity.
- Produces: default English string `open_audio` with value `Open local audio`.

- [ ] **Step 1: Write the failing picker-contract test**

Build `OpenDocumentAtInitialUri.Input(arrayOf("audio/*"), initialUri)` and assert the generated intent has `ACTION_OPEN_DOCUMENT`, `Intent.EXTRA_MIME_TYPES == arrayOf("audio/*")`, and the initial URI extra on supported API levels.

- [ ] **Step 2: Run the picker test to establish the RED/coverage baseline**

Run: `./gradlew :feature:player:connectedDebugAndroidTest --console=plain`

Expected: the existing generic contract assertions pass; add an assertion against `createAudioPickerInput(initialUri)` that fails to compile until the audio MIME helper exists, preventing a hard-coded mismatch between test and activity.

- [ ] **Step 3: Add selector UI and callback plumbing**

Below Disable in `AudioTrackSelectorView`, add the same spacing/button pattern used by subtitles:

```kotlin
Spacer(modifier = Modifier.size(16.dp))
FilledTonalButton(
    modifier = Modifier.fillMaxWidth(),
    onClick = {
        onSelectAudioClick()
        onDismiss()
    },
) {
    Text(text = stringResource(R.string.open_audio))
}
```

Thread `onSelectAudioClick` through `OverlayShowView` and `MediaPlayerScreen`.

- [ ] **Step 4: Implement PlayerActivity picker behavior**

Add the tested top-level helper:

```kotlin
internal fun createAudioPickerInput(initialUri: Uri?) = OpenDocumentAtInitialUri.Input(
    mimeTypes = arrayOf(MimeTypes.BASE_TYPE_AUDIO + "/*"),
    initialUri = initialUri,
)
```

Create `audioFileSuspendLauncher`. On selection, compute the video's initial directory, launch with `createAudioPickerInput(initialUri)`, take persistable read permission, initialize the controller if needed, and call `addAudioTrack(uri)`. Catch permission/read failures, log them, and do not send the command. Update `onStop` to pause when either audio or subtitle launcher awaits a result.

- [ ] **Step 5: Verify UI compilation and picker tests**

Run: `./gradlew :feature:player:connectedDebugAndroidTest :feature:player:testDebugUnitTest :app:assembleDebug --console=plain`

Expected: PASS.

- [ ] **Step 6: Commit the user-facing slice**

```bash
git add core/ui feature/player
git commit -m "feat: pick local audio tracks"
```

---

### Task 6: Full automated verification and end-to-end emulator QA

**Files:**
- Create test artifacts only under `/tmp/nextplayer-local-audio-qa`.
- Capture evidence under `/tmp/nextplayer-local-audio-qa/evidence`.

**Interfaces:**
- Consumes: debug package `dev.anilbeesetti.nextplayer.debug` and the `DEVICE` serial from Task 1.
- Produces: fresh automated-test output, UI dumps, screenshots, logcat, and proof of persistence.

- [ ] **Step 1: Run all relevant automated checks fresh**

Run:

```bash
./gradlew \
  :core:database:connectedDebugAndroidTest \
  :core:data:connectedDebugAndroidTest \
  :feature:player:testDebugUnitTest \
  :feature:player:connectedDebugAndroidTest \
  :app:assembleDebug \
  --console=plain
```

Expected: BUILD SUCCESSFUL with zero failed tests.

- [ ] **Step 2: Generate deterministic QA media**

Use an installed `ffmpeg` to generate a 20-second color/test-pattern MP4 with a quiet 440 Hz embedded audio track plus two 20-second AAC/M4A files at 660 Hz and 880 Hz. If `ffmpeg` is unavailable, generate equivalent fixtures with another installed media encoder; do not download copyrighted media.

- [ ] **Step 3: Install fixtures and app**

Run `adb -s "$DEVICE" push` for all three fixtures into `/sdcard/Download/`, then run:

```bash
./gradlew :app:installDebug --console=plain
```

Clear logcat and launch the video through a VIEW intent using its MediaStore/content URI so persisted URI permissions and video identity match normal app usage.

- [ ] **Step 4: Attach the first external track**

Use `android layout --device "$DEVICE" --pretty` as the primary UI inspection method. Show controls, open Select audio track using UI-tree-derived centers, tap Open local audio, select the 660 Hz document from the system picker, and return to playback. Reopen the selector and assert its UI tree contains one more audio radio row than the baseline.

- [ ] **Step 5: Attach and select the second track**

Repeat for 880 Hz. Assert the selector now contains the embedded track plus both external tracks and select the second external track. Capture `audio-two-selected.png`, a UI tree, and logcat. Verify playback continues and the crash buffer is empty.

- [ ] **Step 6: Verify persistence after process restart**

Force-stop `dev.anilbeesetti.nextplayer.debug`, relaunch the same video URI, reopen the audio selector, and assert both external entries remain selectable. Select each entry once, checking that playback remains active and no player/source exception is logged. Capture `audio-restored.png` and `audio-restored.xml`.

- [ ] **Step 7: Check repository diff and rerun critical verification**

Run: `git diff --check`

Run: `./gradlew :core:database:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest :feature:player:connectedDebugAndroidTest :app:assembleDebug --console=plain`

Expected: no whitespace errors; BUILD SUCCESSFUL.

- [ ] **Step 8: Stop and remove the temporary AVD in a finally-style cleanup**

Run:

```bash
android emulator stop small_phone
android emulator remove small_phone
android emulator list
```

Expected: `small_phone` is absent. Perform this step even if any earlier QA assertion fails.

- [ ] **Step 9: Commit final test adjustments, if any**

```bash
git add .
git commit -m "test: cover local audio track workflow"
```
