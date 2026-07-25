# LocalVaultRepository Readability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `LocalVaultRepository.hideVideos` into explicit reservation, movement, and reconciliation phases without changing vault safety behavior. Add deterministic coverage for a move that commits before cancellation is delivered.

**Architecture:** Keep the entire workflow private to `LocalVaultRepository`. The public override becomes a short orchestrator, while private helpers own reservation creation, entity mapping, batched movement, and outcome reconciliation. A private sealed `MoveOutcome` replaces the nullable move-map control flow at the orchestration boundary.

**Tech Stack:** Kotlin, coroutines, Room DAO, Android instrumentation tests, Gradle

## Global Constraints

- Keep every implementation detail private to `LocalVaultRepository`.
- Preserve the existing single batched media permission request.
- Preserve source files whenever their corresponding hide operation does not commit.
- Make no database schema or DAO changes.
- Make no changes to `MediaOperationsService`.
- Add no injected classes or public abstractions.
- Make no changes to vault restore behavior or user-visible behavior.

---

### Task 1: Refactor the hide workflow into named phases

**Files:**
- Modify: `core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalVaultRepository.kt:51-129`
- Test: `core/data/src/androidTest/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalVaultRepositoryTest.kt`

**Interfaces:**
- Consumes: `HiddenVideoDao.insert(HiddenVideoEntity): Long`, `HiddenVideoDao.deleteByIds(List<Long>)`, `HiddenVideoDao.deleteByVaultPaths(List<String>)`, and `MediaOperationsService.moveMedia(Map<Uri, File>): Map<Uri, File?>`
- Produces: private `reserveVideos`, `reserveVideo`, `moveReservedVideos`, `MoveOutcome`, `MoveOutcome.wasCommitted`, and `MoveOutcome.rethrowCancellation`

- [ ] **Step 1: Establish the characterization-test baseline**

Run the existing repository instrumentation tests before editing production code:

```bash
ANDROID_SERIAL=emulator-5582 \
ANDROID_HOME=/Users/anil/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk \
./gradlew :core:data:connectedDebugAndroidTest --console=plain
```

Expected: all 5 existing `LocalVaultRepositoryTest` tests pass.

- [ ] **Step 2: Replace `hideVideos` with phase-oriented orchestration**

Replace the current `hideVideos` body with:

```kotlin
override suspend fun hideVideos(videos: List<Video>) {
    val reservations = reserveVideos(videos)
    if (reservations.isEmpty()) return

    val moveOutcome = moveReservedVideos(reservations)
    reconcileReservations(reservations, moveOutcome)
    moveOutcome.rethrowCancellation()
}
```

This method must contain no DAO insertion loop, filesystem result inspection, or
exception-handling branches.

- [ ] **Step 3: Extract reservation creation and entity mapping**

Add these private helpers immediately after `hideVideos`:

```kotlin
private suspend fun reserveVideos(videos: List<Video>): List<HideReservation> {
    val attemptedVaultPaths = mutableListOf<String>()
    return try {
        videos.mapNotNull { video ->
            reserveVideo(video, attemptedVaultPaths)
        }
    } catch (e: CancellationException) {
        deleteReservationsByVaultPath(attemptedVaultPaths)
        throw e
    }
}

private suspend fun reserveVideo(
    video: Video,
    attemptedVaultPaths: MutableList<String>,
): HideReservation? {
    val destination = createVaultDestination(video.nameWithExtension)
    attemptedVaultPaths += destination.absolutePath
    val sourceUri = video.uriString.toUri()
    val entity = video.toHiddenVideoEntity(destination)
    val rowId = try {
        hiddenVideoDao.insert(entity)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        deleteReservationsByVaultPath(listOf(destination.absolutePath))
        return null
    }
    return HideReservation(
        rowId = rowId,
        sourceUri = sourceUri,
        destination = destination,
    )
}

private fun Video.toHiddenVideoEntity(destination: File): HiddenVideoEntity {
    return HiddenVideoEntity(
        vaultPath = destination.absolutePath,
        originalPath = path,
        displayName = nameWithExtension,
        duration = duration,
        size = size,
        width = width,
        height = height,
        hiddenAt = System.currentTimeMillis(),
    )
}
```

Keep `HideReservation` private and retain its existing fields.

- [ ] **Step 4: Introduce an explicit move outcome**

Add the private sealed outcome beside `HideReservation`:

```kotlin
private sealed interface MoveOutcome {
    data class Completed(val movedFiles: Map<Uri, File?>) : MoveOutcome
    data object Failed : MoveOutcome
    data class Cancelled(val exception: CancellationException) : MoveOutcome
}
```

Add the batched move helper:

```kotlin
private suspend fun moveReservedVideos(
    reservations: List<HideReservation>,
): MoveOutcome {
    return try {
        MoveOutcome.Completed(
            mediaOperationsService.moveMedia(
                reservations.associate { it.sourceUri to it.destination },
            ),
        )
    } catch (e: CancellationException) {
        MoveOutcome.Cancelled(e)
    } catch (e: Exception) {
        MoveOutcome.Failed
    }
}
```

This must invoke `moveMedia` once so Android 11+ still presents one batched permission
request.

- [ ] **Step 5: Make reconciliation outcome-driven**

Change `reconcileReservations` to accept `MoveOutcome`:

```kotlin
private suspend fun reconcileReservations(
    reservations: List<HideReservation>,
    moveOutcome: MoveOutcome,
) {
    val failedRowIds = reservations.mapNotNull { reservation ->
        reservation.rowId.takeUnless { moveOutcome.wasCommitted(reservation) }
    }
    if (failedRowIds.isEmpty()) return
    withContext(NonCancellable) {
        runCatching { hiddenVideoDao.deleteByIds(failedRowIds) }
    }
}
```

Add the outcome helpers:

```kotlin
private fun MoveOutcome.wasCommitted(reservation: HideReservation): Boolean {
    return when (this) {
        is MoveOutcome.Completed -> {
            movedFiles[reservation.sourceUri] == reservation.destination
        }
        MoveOutcome.Failed, is MoveOutcome.Cancelled -> reservation.destination.exists()
    }
}

private fun MoveOutcome.rethrowCancellation() {
    if (this is MoveOutcome.Cancelled) throw exception
}
```

For an unknown partial result, destination existence remains the evidence that the
media service committed the move before throwing.

- [ ] **Step 6: Run focused compilation and formatting checks**

```bash
ANDROID_HOME=/Users/anil/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk \
./gradlew \
  :core:data:compileDebugKotlin \
  :core:data:compileDebugAndroidTestKotlin \
  :core:data:ktlintCheck \
  --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Run the repository behavior tests after the refactor**

```bash
ANDROID_SERIAL=emulator-5582 \
ANDROID_HOME=/Users/anil/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk \
./gradlew :core:data:connectedDebugAndroidTest --console=plain
```

Expected: all 6 `LocalVaultRepositoryTest` tests pass, including cancellation after a committed move.

- [ ] **Step 8: Run the complete PR verification**

```bash
ANDROID_SERIAL=emulator-5582 \
ANDROID_HOME=/Users/anil/Library/Android/sdk \
ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk \
./gradlew \
  :core:data:connectedDebugAndroidTest \
  :core:media:connectedDebugAndroidTest \
  ktlintCheck \
  testDebugUnitTest \
  :app:assembleDebug \
  --console=plain
```

Expected: 6 data tests, 5 media tests, all debug unit tests, formatting checks, and
debug APK assembly pass.

- [ ] **Step 9: Commit and update the PR branch**

```bash
git add \
  core/data/src/main/java/dev/anilbeesetti/nextplayer/core/data/repository/LocalVaultRepository.kt \
  docs/superpowers/plans/2026-07-24-local-vault-repository-readability.md
git commit -m "Refactor vault hide workflow for readability"
git push origin codex/fix-issue-1828-vault-data-loss
```

Expected: the commit is added to pull request 1834 and the worktree is clean.
