# MediaStore Write Request Limit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent moves of more than 2,000 MediaStore items from crashing while requesting write access.

**Architecture:** Add a small write-access entry point around the existing `runMediaRequests()` batching engine, then route `LocalMediaOperationsService.requestWriteAccessR()` through it. The service launches one system consent request at a time and performs the existing file moves only after every batch succeeds.

**Tech Stack:** Kotlin, Android MediaStore, coroutines, JUnit 4, Gradle.

## Global Constraints

- Each `MediaStore.createWriteRequest()` receives at most 2,000 distinct URIs.
- Requests are sequential because the service owns one activity-result callback pair.
- Cancelling or rejecting any batch stops later requests and preserves the existing all-failed move result.
- Stale URI recovery must match the existing delete-request behavior.
- Do not change Storage Access Framework transfers, rename requests, or move result types.

---

### Task 1: Batch MediaStore Write-Access Requests

**Files:**
- Modify: `core/media/src/test/java/dev/anilbeesetti/nextplayer/core/media/services/MediaRequestRunnerTest.kt:9-90`
- Modify: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/services/MediaRequestRunner.kt:3-17`
- Modify: `core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/services/LocalMediaOperationsService.kt:252-259`

**Interfaces:**
- Consumes: `runMediaRequests(items, maxBatchSize, itemExists, request): Boolean`, `mediaExists(uri): Boolean`, and `launchWriteRequest(uris, onResultCanceled, onResultOk)`.
- Produces: `internal suspend fun <T> runMediaWriteRequests(uris: List<T>, itemExists: suspend (T) -> Boolean, request: suspend (List<T>) -> Boolean): Boolean` and a private single-batch `requestWriteR(uris: List<Uri>): Boolean`.

- [x] **Step 1: Write the failing write-access regression test**

Add this test to `MediaRequestRunnerTest`:

```kotlin
@Test
fun `splits write access requests at the platform uri limit`() = runBlocking {
    val requestedBatchSizes = mutableListOf<Int>()

    val result = runMediaWriteRequests(
        uris = (1..2_001).toList(),
        itemExists = { true },
        request = { batch ->
            require(batch.size <= MAX_MEDIA_REQUEST_URIS)
            requestedBatchSizes += batch.size
            true
        },
    )

    assertTrue(result)
    assertEquals(listOf(2_000, 1), requestedBatchSizes)
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```bash
./gradlew :core:media:testDebugUnitTest --tests 'dev.anilbeesetti.nextplayer.core.media.services.MediaRequestRunnerTest.splits write access requests at the platform uri limit'
```

Expected: Kotlin test compilation fails with `Unresolved reference 'runMediaWriteRequests'`, proving the regression test requires the missing write-request batching entry point.

- [x] **Step 3: Add the minimal write-request batching entry point**

Add to `MediaRequestRunner.kt`:

```kotlin
internal suspend fun <T> runMediaWriteRequests(
    uris: List<T>,
    itemExists: suspend (T) -> Boolean,
    request: suspend (List<T>) -> Boolean,
): Boolean = runMediaRequests(
    items = uris,
    itemExists = itemExists,
    request = request,
)
```

- [x] **Step 4: Route service write access through sequential batches**

Replace `requestWriteAccessR()` with:

```kotlin
@RequiresApi(Build.VERSION_CODES.R)
private suspend fun requestWriteAccessR(uris: List<Uri>): Boolean = runMediaWriteRequests(
    uris = uris,
    itemExists = ::mediaExists,
    request = ::requestWriteR,
)

@RequiresApi(Build.VERSION_CODES.R)
private suspend fun requestWriteR(uris: List<Uri>): Boolean = suspendCancellableCoroutine { continuation ->
    launchWriteRequest(
        uris = uris,
        onResultOk = { continuation.resume(true) },
        onResultCanceled = { continuation.resume(false) },
    )
}
```

- [x] **Step 5: Run the focused regression test and verify GREEN**

Run:

```bash
./gradlew :core:media:testDebugUnitTest --tests 'dev.anilbeesetti.nextplayer.core.media.services.MediaRequestRunnerTest.splits write access requests at the platform uri limit'
```

Expected: PASS; recorded batch sizes are exactly `2,000` and `1`.

- [x] **Step 6: Run core-media tests**

Run:

```bash
./gradlew :core:media:testDebugUnitTest
```

Expected: PASS with zero failed tests.

- [x] **Step 7: Compile the app integration**

Run:

```bash
ANDROID_HOME=/Users/anil/Library/Android/sdk ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk ./gradlew -q :app:compileDebugKotlin
```

Expected: exit code 0 with no Kotlin compilation errors.

- [x] **Step 8: Review and commit the fix**

Review `git diff --check` and the scoped diff, then run the focused test once more as fresh completion evidence.

```bash
git add core/media/src/test/java/dev/anilbeesetti/nextplayer/core/media/services/MediaRequestRunnerTest.kt core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/services/MediaRequestRunner.kt core/media/src/main/java/dev/anilbeesetti/nextplayer/core/media/services/LocalMediaOperationsService.kt docs/superpowers/plans/2026-07-20-mediastore-write-request-limit.md
git commit -m "fix(media): batch MediaStore write requests"
```
