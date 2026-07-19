# Finished Video Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore a full progress bar on `VideoItem` after a video has been watched to the end.

**Architecture:** Keep the player's finished sentinel and the Compose rendering condition unchanged. Normalize a negative persisted playback position to `1f` in the `Video` model, where persisted playback state is already translated into UI-ready progress.

**Tech Stack:** Kotlin/JVM, JUnit 4, Gradle

## Global Constraints

- `null` playback position means never played and must continue to produce `null` progress.
- A non-negative position with a positive duration must continue to produce the position-to-duration ratio.
- A negative position with a positive duration must produce `1f` progress.
- A non-positive duration must continue to produce `null` progress.
- Do not change player persistence or Compose rendering behavior.

---

### Task 1: Restore completed-video progress translation

**Files:**
- Modify: `core/model/build.gradle.kts`
- Create: `core/model/src/test/java/dev/anilbeesetti/nextplayer/core/model/VideoTest.kt`
- Modify: `core/model/src/main/java/dev/anilbeesetti/nextplayer/core/model/Video.kt:28`

**Interfaces:**
- Consumes: `Video.playbackPosition: Long?`, where a negative value is the persisted finished marker.
- Produces: `Video.playedPercentage: Float?`, with `1f` representing completed playback and `null` representing no displayable progress.

- [x] **Step 1: Add the model test dependency and failing regression test**

Add JUnit 4 to `core/model/build.gradle.kts`:

```kotlin
dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit4)
}
```

Create `core/model/src/test/java/dev/anilbeesetti/nextplayer/core/model/VideoTest.kt`:

```kotlin
package dev.anilbeesetti.nextplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTest {

    @Test
    fun finishedVideoHasFullPlayedPercentage() {
        val video = Video.sample.copy(
            duration = 1_000L,
            playbackPosition = -1L,
        )

        assertEquals(1f, video.playedPercentage)
    }
}
```

- [x] **Step 2: Run the focused test and verify the regression**

Run:

```bash
./gradlew :core:model:test --tests dev.anilbeesetti.nextplayer.core.model.VideoTest.finishedVideoHasFullPlayedPercentage
```

Expected: FAIL because `video.playedPercentage` is `null` instead of `1f`.

- [x] **Step 3: Implement the minimal model fix**

Replace the `playedPercentage` calculation in `core/model/src/main/java/dev/anilbeesetti/nextplayer/core/model/Video.kt` with:

```kotlin
val playedPercentage: Float? = playbackPosition?.let { playbackPosition ->
    when {
        duration <= 0 -> null
        playbackPosition < 0 -> 1f
        else -> playbackPosition.toFloat() / duration.toFloat()
    }
}
```

- [x] **Step 4: Run the focused model suite and compile the affected UI module**

Run:

```bash
./gradlew :core:model:test :feature:videopicker:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL, including a passing `VideoTest`.

- [x] **Step 5: Run repository formatting and diff checks**

Run:

```bash
./gradlew ktlintCheck
git diff --check
```

Expected: both commands succeed with no formatting or whitespace errors.

- [x] **Step 6: Commit the regression test and fix**

```bash
git add core/model/build.gradle.kts core/model/src/test/java/dev/anilbeesetti/nextplayer/core/model/VideoTest.kt core/model/src/main/java/dev/anilbeesetti/nextplayer/core/model/Video.kt docs/superpowers/plans/2026-07-19-finished-video-progress.md
git commit -m "fix: show progress for completed videos"
```
