# Issue 1828 Crash-Log Clipboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent oversized crash reports from crashing `CrashActivity` when copied while retaining full file-based sharing.

**Architecture:** Add a small app-module helper that bounds clipboard-bound report text before creating `ClipData`. Keep report collection/composition and file-based Share unchanged, and wire only the Copy callback through the helper.

**Tech Stack:** Kotlin, Android ClipboardManager/ClipData, JUnit 4, AndroidX Test, Android 16/API 36 emulator.

## Global Constraints

- Clipboard-bound crash reports must contain at most 100,000 UTF-16 characters, including the truncation marker.
- The exact truncation marker is `\n\n[Crash report truncated. Use Share for full logs.]`.
- The report prefix must be retained so device information and the primary exception remain available.
- Reports at or below the limit must remain byte-for-byte unchanged as Kotlin strings.
- Full, untruncated logs must remain available through the existing file-based Share action.
- Vault move behavior, Hide UI behavior, and logcat collection are out of scope.

---

### Task 1: Bound crash reports before clipboard IPC

**Files:**
- Create: `app/src/main/java/dev/anilbeesetti/nextplayer/crash/CrashLogClipboard.kt`
- Modify: `app/src/main/java/dev/anilbeesetti/nextplayer/crash/CrashActivity.kt:142-149`
- Modify: `app/build.gradle.kts:15-21`
- Create: `app/src/test/java/dev/anilbeesetti/nextplayer/crash/CrashLogClipboardTest.kt`
- Create: `app/src/androidTest/java/dev/anilbeesetti/nextplayer/crash/CrashLogClipboardInstrumentedTest.kt`

**Interfaces:**
- Consumes: the complete crash-report `String` already returned by `CrashActivity.concatLogs(...)` and `android.content.ClipboardManager`.
- Produces: `internal fun crashReportForClipboard(report: String): String` and `internal fun copyCrashReportToClipboard(clipboard: ClipboardManager, report: String)`.

- [ ] **Step 1: Configure and write the failing regression tests**

Add `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"` to `app/build.gradle.kts` `defaultConfig` so the existing and new Android tests can run.

Create `CrashLogClipboardTest.kt`:

```kotlin
package dev.anilbeesetti.nextplayer.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogClipboardTest {

    @Test
    fun reportAtClipboardLimitIsUnchanged() {
        val report = "x".repeat(MAX_CLIPBOARD_REPORT_CHARS)

        assertEquals(report, crashReportForClipboard(report))
    }

    @Test
    fun oversizedReportRetainsPrefixAndAddsTruncationMarkerWithinLimit() {
        val prefix = "Device info\nException:\nPrimary hide failure\n"
        val report = prefix + "x".repeat(MAX_CLIPBOARD_REPORT_CHARS)

        val bounded = crashReportForClipboard(report)

        assertEquals(MAX_CLIPBOARD_REPORT_CHARS, bounded.length)
        assertTrue(bounded.startsWith(prefix))
        assertTrue(bounded.endsWith(CLIPBOARD_REPORT_TRUNCATION_MARKER))
    }
}
```

Create `CrashLogClipboardInstrumentedTest.kt`:

```kotlin
package dev.anilbeesetti.nextplayer.crash

import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
class CrashLogClipboardInstrumentedTest {

    @Test
    fun reportedFiveMegabytePayloadDoesNotCrashClipboardCopy() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val prefix = "Primary hide failure\n"
        val report = prefix + "x".repeat(2_596_638)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            copyCrashReportToClipboard(clipboard, report)
        }

        val copied = clipboard.primaryClip?.getItemAt(0)?.text.toString()
        assertEquals(MAX_CLIPBOARD_REPORT_CHARS, copied.length)
        assertTrue(copied.startsWith(prefix))
        assertTrue(copied.endsWith(CLIPBOARD_REPORT_TRUNCATION_MARKER))
    }
}
```

- [ ] **Step 2: Run tests to verify RED**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests dev.anilbeesetti.nextplayer.crash.CrashLogClipboardTest
```

Expected: compilation fails because `MAX_CLIPBOARD_REPORT_CHARS`, `CLIPBOARD_REPORT_TRUNCATION_MARKER`, and `crashReportForClipboard` do not exist.

Run:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.anilbeesetti.nextplayer.crash.CrashLogClipboardInstrumentedTest
```

Expected: compilation fails because the clipboard helper symbols do not exist; the test must not pass before implementation.

- [ ] **Step 3: Implement the minimal clipboard bound**

Create `CrashLogClipboard.kt`:

```kotlin
package dev.anilbeesetti.nextplayer.crash

import android.content.ClipData
import android.content.ClipboardManager

internal const val MAX_CLIPBOARD_REPORT_CHARS = 100_000
internal const val CLIPBOARD_REPORT_TRUNCATION_MARKER =
    "\n\n[Crash report truncated. Use Share for full logs.]"

internal fun crashReportForClipboard(report: String): String {
    if (report.length <= MAX_CLIPBOARD_REPORT_CHARS) return report

    val prefixLength = MAX_CLIPBOARD_REPORT_CHARS - CLIPBOARD_REPORT_TRUNCATION_MARKER.length
    return report.take(prefixLength) + CLIPBOARD_REPORT_TRUNCATION_MARKER
}

internal fun copyCrashReportToClipboard(
    clipboard: ClipboardManager,
    report: String,
) {
    clipboard.setPrimaryClip(
        ClipData.newPlainText(null, crashReportForClipboard(report)),
    )
}
```

Replace the direct clipboard call in `CrashActivity` with:

```kotlin
copyCrashReportToClipboard(
    clipboard = clipboard.nativeClipboard,
    report = concatLogs(collectDeviceInfo(), exceptionString, logcat),
)
```

Do not change the Share callback or `concatLogs`.

- [ ] **Step 4: Run focused tests to verify GREEN**

Run:

```bash
./gradlew :app:testDebugUnitTest --tests dev.anilbeesetti.nextplayer.crash.CrashLogClipboardTest
```

Expected: both JVM tests pass.

Run on the disposable API-36 emulator:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.anilbeesetti.nextplayer.crash.CrashLogClipboardInstrumentedTest
```

Expected: the real clipboard accepts the bounded form of the reported approximately 5.2 MB payload and the instrumentation test passes without `TransactionTooLargeException`.

- [ ] **Step 5: Run regression suites and compile checks**

Run:

```bash
./gradlew :app:testDebugUnitTest :feature:videopicker:testDebugUnitTest :core:domain:testDebugUnitTest :core:media:testDebugUnitTest :core:model:test :app:assembleDebug
```

Expected: all tests and the debug build pass.

- [ ] **Step 6: Commit**

```bash
git add app/build.gradle.kts app/src/main/java/dev/anilbeesetti/nextplayer/crash/CrashActivity.kt app/src/main/java/dev/anilbeesetti/nextplayer/crash/CrashLogClipboard.kt app/src/test/java/dev/anilbeesetti/nextplayer/crash/CrashLogClipboardTest.kt app/src/androidTest/java/dev/anilbeesetti/nextplayer/crash/CrashLogClipboardInstrumentedTest.kt docs/superpowers/specs/2026-07-20-issue-1828-crash-log-clipboard-design.md docs/superpowers/plans/2026-07-20-issue-1828-crash-log-clipboard.md
git commit -m "Fix oversized crash log clipboard copy"
```
