# MediaStore Permission Crash Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent `MediaStoreMediaService` from crashing when video read permission is missing or revoked.

**Architecture:** Reproduce the provider failure through the real Android service with a deterministic test `ContentProvider` that denies its `ContentResolver.query()` call. Add a small internal query runner at the core-media boundary that converts only `SecurityException` to an empty list, then route the complete video query and cursor traversal through it. Folder queries inherit the safe behavior because they derive their results from `fetchVideos()`.

**Tech Stack:** Kotlin, Android MediaStore, coroutines, JUnit 4, AndroidX instrumentation tests, Gradle.

## Global Constraints

- Missing or revoked media permission returns empty video and folder lists until access is restored.
- Catch only `SecurityException`; unrelated exceptions must continue to propagate.
- Keep the existing media-picker permission request and rationale UI unchanged.
- Do not add retries or new dependencies.

---

### Task 1: Reproduce and Contain the MediaStore SecurityException

**Files:**
- Create: `core/media/src/androidTest/java/dev/anilbeesetti/nextplayer/core/media/services/MediaStoreMediaServicePermissionTest.kt`
- Create: `core/media/src/test/java/dev/anilbeesetti/nextplayer/core/media/services/MediaStoreQueryRunnerTest.kt`
- Create: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/services/MediaStoreQueryRunner.kt`
- Modify: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/services/MediaStoreMediaService.kt:116-138`

**Interfaces:**
- Consumes: `MediaStoreMediaService.fetchVideos(folderPath: String? = null): List<MediaVideo>` and the existing Android `ContentResolver.query()` call.
- Produces: `internal inline fun <T> runMediaStoreQuery(query: () -> List<T>): List<T>`.

- [x] **Step 1: Write the provider-denied instrumentation regression test**

```kotlin
package dev.anilbeesetti.nextplayer.core.media.services

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MediaStoreMediaServicePermissionTest {

    @Test
    fun fetchVideosReturnsEmptyListWhenProviderDeniesAccess() = runBlocking {
        val provider = object : ContentProvider() {
            override fun onCreate(): Boolean = true

            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?,
            ): Cursor? = throw SecurityException("Permission denial")

            override fun getType(uri: Uri): String? = null

            override fun insert(uri: Uri, values: ContentValues?): Uri? = null

            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

            override fun update(
                uri: Uri,
                values: ContentValues?,
                selection: String?,
                selectionArgs: Array<out String>?,
            ): Int = 0
        }
        val contentResolver = ContentResolver.wrap(provider)
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(targetContext) {
            override fun getContentResolver(): ContentResolver = contentResolver
        }
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            val videos = MediaStoreMediaService(context, applicationScope).fetchVideos()

            assertTrue(videos.isEmpty())
        } finally {
            applicationScope.cancel()
        }
    }
}
```

- [x] **Step 2: Run the instrumentation test against the unfixed code to reproduce the crash**

Run:

```bash
ANDROID_HOME=/Users/anil/Library/Android/sdk ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk ./gradlew :core:media:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.anilbeesetti.nextplayer.core.media.services.MediaStoreMediaServicePermissionTest
```

Expected: FAIL with `java.lang.SecurityException: Permission denial` originating from `ContentResolver.query()` inside `MediaStoreMediaService.fetchVideos()`.

- [x] **Step 3: Write focused JVM tests for the exception boundary**

```kotlin
package dev.anilbeesetti.nextplayer.core.media.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreQueryRunnerTest {

    @Test
    fun `returns empty list when MediaStore denies access`() {
        val result = runMediaStoreQuery<String> {
            throw SecurityException("Permission denial")
        }

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns successful query result unchanged`() {
        val result = runMediaStoreQuery { listOf("video") }

        assertEquals(listOf("video"), result)
    }

    @Test(expected = IllegalStateException::class)
    fun `propagates exceptions unrelated to permission`() {
        runMediaStoreQuery<String> {
            throw IllegalStateException("Provider failure")
        }
    }
}
```

- [x] **Step 4: Run the JVM test to verify it fails before implementation**

Run: `./gradlew :core:media:testDebugUnitTest --tests dev.anilbeesetti.nextplayer.core.media.services.MediaStoreQueryRunnerTest`

Expected: FAIL to compile with `Unresolved reference 'runMediaStoreQuery'`.

- [x] **Step 5: Add the minimal query runner**

```kotlin
package dev.anilbeesetti.nextplayer.core.media.services

internal inline fun <T> runMediaStoreQuery(query: () -> List<T>): List<T> {
    return try {
        query()
    } catch (_: SecurityException) {
        emptyList()
    }
}
```

- [x] **Step 6: Route the complete video query through the runner**

Replace `fetchVideos()` with:

```kotlin
override suspend fun fetchVideos(folderPath: String?): List<MediaVideo> = withContext(Dispatchers.IO) {
    return@withContext runMediaStoreQuery {
        val mediaVideos = mutableListOf<MediaVideo>()

        // A null folderPath scans every storage volume (e.g. SD cards / USB OTG). For a specific
        // folder, match it and its descendants, escaping LIKE metacharacters ('%', '_') in the path.
        val selection = if (folderPath == null) null else "${MediaStore.Video.Media.DATA} LIKE ? ESCAPE '\\'"
        val selectionArgs = if (folderPath == null) null else arrayOf("${folderPath.escapeLike()}/%")
        val sortOrder = "${MediaStore.Video.Media.DISPLAY_NAME} ASC"

        context.contentResolver.query(
            VIDEO_COLLECTION_URI,
            VIDEO_PROJECTION,
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val video = cursor.toMediaVideo() ?: continue
                mediaVideos.add(video)
            }
        }
        mediaVideos
    }
}
```

- [x] **Step 7: Run focused JVM tests to verify the boundary**

Run: `./gradlew :core:media:testDebugUnitTest --tests dev.anilbeesetti.nextplayer.core.media.services.MediaStoreQueryRunnerTest`

Expected: PASS; security failures return empty, successful results are preserved, and unrelated exceptions propagate.

- [x] **Step 8: Re-run the instrumentation reproduction**

Run:

```bash
ANDROID_HOME=/Users/anil/Library/Android/sdk ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk ./gradlew :core:media:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.anilbeesetti.nextplayer.core.media.services.MediaStoreMediaServicePermissionTest
```

Expected: PASS with the same denying provider; `fetchVideos()` returns an empty list instead of throwing.

- [x] **Step 9: Run module tests and app compilation**

Run: `./gradlew :core:media:testDebugUnitTest`

Expected: PASS.

Run: `ANDROID_HOME=/Users/anil/Library/Android/sdk ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk ./gradlew -q :app:compileDebugKotlin`

Expected: PASS with no Kotlin compilation errors.

- [x] **Step 10: Commit the implementation**

```bash
git add core/media/src/androidTest/java/dev/anilbeesetti/nextplayer/core/media/services/MediaStoreMediaServicePermissionTest.kt core/media/src/test/java/dev/anilbeesetti/nextplayer/core/media/services/MediaStoreQueryRunnerTest.kt core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/services/MediaStoreQueryRunner.kt core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/services/MediaStoreMediaService.kt docs/superpowers/plans/2026-07-18-mediastore-permission-crash.md
git commit -m "Fix MediaStore permission query crash"
```
