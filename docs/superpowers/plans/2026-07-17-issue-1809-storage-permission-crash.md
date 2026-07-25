# Issue 1809 Storage Permission Crash Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent first launch from querying MediaStore before runtime storage permission is granted on Android 7.0.

**Architecture:** Keep permission ownership in the media-picker feature. `MainActivity` no longer starts media synchronization unconditionally; `MediaPickerViewModel` starts synchronization and media collection together only when storage permission is already granted or after `OnPermissionAccepted`. Pre-Android 13 devices request the read permission that MediaStore enforces instead of relying on a write grant to imply read access.

**Tech Stack:** Kotlin, Android runtime permissions, Hilt, coroutines/Flow, AndroidX instrumentation tests.

## Global Constraints

- Reproduce and verify on Android 7.0 (API 24) with app version 0.17.0 and storage permission denied.
- Do not suppress `SecurityException` inside the MediaStore service.
- Preserve the existing permission rationale and request UI.

---

### Task 1: Gate MediaStore Consumers Behind Permission

**Files:**
- Modify: `app/src/main/java/dev/anilbeesetti/nextplayer/MainActivity.kt`
- Modify: `feature/videopicker/src/main/java/dev/anilbeesetti/nextplayer/feature/videopicker/screens/mediapicker/MediaPickerViewModel.kt`
- Test: `app/src/androidTest/java/dev/anilbeesetti/nextplayer/MainActivityPermissionTest.kt`
- Modify: `core/common/src/main/java/dev/anilbeesetti/nextplayer/core/common/Utils.kt`

**Interfaces:**
- Consumes: `storagePermission`, `MediaSynchronizer.startSync()`, and `MediaPickerAction.OnPermissionAccepted`.
- Produces: `MediaPickerViewModel.startMediaCollection()`, which starts synchronization and media collection only after permission is available.

- [ ] **Step 1: Write the failing launch regression test**

```kotlin
@Test
fun launchWithoutStoragePermissionKeepsMainActivityAlive() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertEquals(
        PackageManager.PERMISSION_DENIED,
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE),
    )

    context.startActivity(
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    SystemClock.sleep(1_000)

    val activityManager = context.getSystemService(ActivityManager::class.java)
    val topActivity = activityManager.appTasks.first().taskInfo?.topActivity
    assertEquals(MainActivity::class.java.name, topActivity?.className)
}
```

- [ ] **Step 2: Verify the unfixed launch fails**

Run: `ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :app:connectedDebugAndroidTest --tests dev.anilbeesetti.nextplayer.MainActivityPermissionTest --console=plain`

Expected: FAIL because the uncaught MediaStore `SecurityException` terminates `MainActivity` and opens `CrashActivity`.

- [ ] **Step 3: Gate synchronization and collection**

```kotlin
init {
    if (ContextCompat.checkSelfPermission(context, storagePermission) == PackageManager.PERMISSION_GRANTED) {
        startMediaCollection()
    }
    collectPreferences()
}

private fun startMediaCollection() {
    mediaSynchronizer.startSync()
    collectMedia()
}
```

Remove the unconditional `synchronizer.startSync()` call and injection from `MainActivity`. Route `OnPermissionAccepted` through `startMediaCollection()`.

For API levels below Android 13, set `storagePermission` to `Manifest.permission.READ_EXTERNAL_STORAGE`, matching the permission enforced by the MediaStore video query.

- [ ] **Step 4: Verify the regression and emulator flow pass**

Run: `ANDROID_HOME=/Users/anil/Library/Android/sdk ./gradlew :app:connectedDebugAndroidTest --tests dev.anilbeesetti.nextplayer.MainActivityPermissionTest --console=plain`

Expected: PASS. On API 24, launching with permission denied displays the permission request without `SecurityException` or `CrashActivity`; accepting permission loads the media picker.
