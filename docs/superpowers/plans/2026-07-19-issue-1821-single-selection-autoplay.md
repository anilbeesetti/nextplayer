# Issue 1821 Single-Selection Autoplay Regression Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the pre-0.17 behavior where playing one explicitly selected video stops after that video, while a normal tap still uses the autoplay-generated folder playlist.

**Architecture:** Keep the existing intent contract in which `PlayerApi.API_PLAYLIST` marks an explicit playlist. Separate the direct-video and explicit-playlist launch paths with overloads so a one-item explicit playlist retains its semantic meaning instead of being inferred from list size.

**Tech Stack:** Kotlin, Android intents, JUnit 4, Robolectric, Gradle version catalog

## Global Constraints

- Preserve normal single-video autoplay behavior.
- Preserve explicit multi-selection and vault URI-permission behavior.
- Add a regression test that fails on commit `700f5986` and passes after the fix.
- Do not refactor unrelated playback or navigation code.

---

### Task 1: Prove and fix explicit one-item playlist intent creation

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/test/java/dev/anilbeesetti/nextplayer/navigation/MediaNavGraphTest.kt`
- Modify: `app/src/main/java/dev/anilbeesetti/nextplayer/navigation/MediaNavGraph.kt`
- Modify: `app/src/main/java/dev/anilbeesetti/nextplayer/navigation/NetworkNavGraph.kt`

**Interfaces:**
- Consumes: `Context.startPlayback(uris: List<Uri>, grantReadPermission: Boolean)` and `PlayerApi.API_PLAYLIST`
- Produces: distinct `Context.startPlayback(uri: Uri, ...)` and `Context.startPlayback(uris: List<Uri>, ...)` launch semantics

- [x] **Step 1: Add Robolectric JVM-test support**

Add the cached Robolectric `4.16.1` dependency to the version catalog and the app module's `testImplementation` dependencies.

- [x] **Step 2: Write the failing regression test**

```kotlin
@RunWith(RobolectricTestRunner::class)
class MediaNavGraphTest {
    @Test
    fun `single item explicit playlist is included in playback intent`() {
        val context = Robolectric.buildActivity(Activity::class.java).setup().get()
        val uri = "content://media/external/video/media/1821".toUri()

        context.startPlayback(listOf(uri))

        val intent = shadowOf(context).nextStartedActivity
        assertEquals(
            arrayListOf(uri),
            IntentCompat.getParcelableArrayListExtra(intent, PlayerApi.API_PLAYLIST, Uri::class.java),
        )
    }
}
```

- [x] **Step 3: Run the focused test to verify the regression exists**

Run: `./gradlew :app:testDebugUnitTest --tests '*MediaNavGraphTest*'`

Expected: FAIL because the current `uris.size > 1` guard omits `PlayerApi.API_PLAYLIST` for the single selected URI.

- [x] **Step 4: Separate direct-video and explicit-playlist launches**

Add a `Uri` overload that launches without `API_PLAYLIST`, update all `onPlayVideo` callbacks to use it, and make the existing `List<Uri>` overload always write `API_PLAYLIST`. Keep read-permission handling identical in both overloads.

- [x] **Step 5: Run the focused test to verify the fix**

Run: `./gradlew :app:testDebugUnitTest --tests '*MediaNavGraphTest*'`

Expected: PASS.

- [x] **Step 6: Run relevant verification**

Run: `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`

Expected: BUILD SUCCESSFUL with no test failures or compilation errors.

- [x] **Step 7: Review the final diff**

Run: `git diff --check && git diff -- app/build.gradle.kts gradle/libs.versions.toml app/src/main app/src/test docs/superpowers/plans`

Expected: only the focused regression test, test dependency, playback-launch fix, and this plan are changed.

### Task 2: Preserve playback intent through vault events

**Files:**
- Modify: `feature/videopicker/build.gradle.kts`
- Create: `feature/videopicker/src/test/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/vault/VaultPlaybackEventTest.kt`
- Modify: `feature/videopicker/src/main/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/vault/VaultViewModel.kt`
- Modify: `feature/videopicker/src/main/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/vault/VaultScreen.kt`

**Interfaces:**
- Consumes: `VaultAction.PlayVideo`, `VaultAction.PlaySelected`, `VaultRoute` playback callbacks
- Produces: distinct `VaultEvent.PlayVideo(Uri)` and `VaultEvent.PlayVideos(List<Uri>)` events

- [x] **Step 1: Write the failing vault event regression test**

Drive `VaultAction.PlayVideo` through a real `VaultViewModel` with lightweight repository fakes and verify that it emits `VaultEvent.PlayVideo` rather than `PlayVideos`.

- [x] **Step 2: Run the focused test to verify the distinction is missing**

Run: `./gradlew :feature:videopicker:testDebugUnitTest --tests '*VaultPlaybackEventTest*'`

Expected: compilation fails because `VaultEvent.PlayVideo` does not exist.

- [x] **Step 3: Separate direct and selected vault events**

Emit `PlayVideo` for an ordinary vault item tap, retain `PlayVideos` for explicit selection, and dispatch each event directly to its matching route callback without checking list size.

- [x] **Step 4: Run the focused test to verify the fix**

Run: `./gradlew :feature:videopicker:testDebugUnitTest --tests '*VaultPlaybackEventTest*'`

Expected: PASS.

### Task 3: Harden and verify the complete playback boundary

**Files:**
- Modify: `app/src/test/java/dev/anilbeesetti/nextplayer/navigation/MediaNavGraphTest.kt`
- Modify: `app/src/main/java/dev/anilbeesetti/nextplayer/navigation/MediaNavGraph.kt`

- [x] **Step 1: Add an empty explicit-playlist regression test**

Verify that a stale or empty resolved selection does not launch playback or throw.

- [x] **Step 2: Run the test and observe the existing crash**

Expected: FAIL with `NoSuchElementException` from `uris.first()`.

- [x] **Step 3: Ignore empty explicit playlists at the playback boundary**

Use `firstOrNull() ?: return` before constructing the playback intent.

- [x] **Step 4: Run combined final verification**

Run app and videopicker unit tests, compile both changed modules, run their formatting checks, inspect XML results for zero failures, and run `git diff --check`.

## Self-Review

- Spec coverage: root cause, failing tests, media-picker and vault fixes, and broader verification are all covered.
- Placeholder scan: no TODO/TBD or deferred implementation steps.
- Type consistency: both overloads consume Android `Uri`; only the list overload produces `API_PLAYLIST`.
