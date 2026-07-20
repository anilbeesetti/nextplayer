# Task 1 Report: Bound crash reports before clipboard IPC

## Implementation

- Added `CrashLogClipboard.kt` with a 100,000 UTF-16-character clipboard boundary and the required truncation marker: `\n\n[Crash report truncated. Use Share for full logs.]`.
- Added `crashReportForClipboard(report)` to preserve reports at or below the boundary and preserve the report prefix plus marker for oversized reports.
- Added `copyCrashReportToClipboard(clipboard, report)` to create the bounded `ClipData` payload.
- Routed only `CrashActivity`'s Copy callback through that helper. The existing Share callback and `concatLogs` implementation remain unchanged, so Share continues to write the complete report to a cache file and expose it through a content URI.
- Configured `androidx.test.runner.AndroidJUnitRunner` in the app default config.

## Files

- `app/build.gradle.kts`
- `app/src/main/java/dev/anilbeesetti/nextplayer/crash/CrashActivity.kt`
- `app/src/main/java/dev/anilbeesetti/nextplayer/crash/CrashLogClipboard.kt`
- `app/src/test/java/dev/anilbeesetti/nextplayer/crash/CrashLogClipboardTest.kt`
- `app/src/androidTest/java/dev/anilbeesetti/nextplayer/crash/CrashLogClipboardInstrumentedTest.kt`
- `docs/superpowers/specs/2026-07-20-issue-1828-crash-log-clipboard-design.md`
- `docs/superpowers/plans/2026-07-20-issue-1828-crash-log-clipboard.md`

## TDD evidence

### RED

1. `ANDROID_SERIAL=emulator-5554 ANDROID_HOME=/Users/anil/Library/Android/sdk ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests dev.anilbeesetti.nextplayer.crash.CrashLogClipboardTest`
   - Failed as expected at `:app:compileDebugUnitTestKotlin` because `MAX_CLIPBOARD_REPORT_CHARS`, `CLIPBOARD_REPORT_TRUNCATION_MARKER`, and `crashReportForClipboard` were unresolved.
2. `ANDROID_SERIAL=emulator-5554 ANDROID_HOME=/Users/anil/Library/Android/sdk ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.anilbeesetti.nextplayer.crash.CrashLogClipboardInstrumentedTest`
   - Failed as expected at `:app:compileDebugAndroidTestKotlin` because `copyCrashReportToClipboard`, `MAX_CLIPBOARD_REPORT_CHARS`, and `CLIPBOARD_REPORT_TRUNCATION_MARKER` were unresolved.

### GREEN

1. `ANDROID_SERIAL=emulator-5554 ANDROID_HOME=/Users/anil/Library/Android/sdk ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests dev.anilbeesetti.nextplayer.crash.CrashLogClipboardTest`
   - Passed: 2/2 JVM tests.
2. `ANDROID_SERIAL=emulator-5554 ANDROID_HOME=/Users/anil/Library/Android/sdk ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.anilbeesetti.nextplayer.crash.CrashLogClipboardInstrumentedTest`
   - Passed on `emulator-5554` (API 36): the 2,596,638-character source report copied without `TransactionTooLargeException` and read back as exactly 100,000 characters with the required prefix and marker.

The initial implementation-test read-back failed with `expected:<100000> but was:<4>` because Android 16's `ClipboardService` denied reads from the non-focused test app. The emulator log confirmed: `Denying clipboard access to dev.anilbeesetti.nextplayer.debug, application is not in focus nor is it a system service`. Launching `MainActivity` did not address this because its runtime-permission dialog took focus. The final test launches `CrashActivity` with a synthetic exception before copying; that provides an in-app foreground host without invoking the permission dialog. This is test setup only and does not alter production behavior.

## Regression/build verification

`ANDROID_SERIAL=emulator-5554 ANDROID_HOME=/Users/anil/Library/Android/sdk ANDROID_SDK_ROOT=/Users/anil/Library/Android/sdk ./gradlew :app:testDebugUnitTest :feature:videopicker:testDebugUnitTest :core:domain:testDebugUnitTest :core:media:testDebugUnitTest :core:model:test :app:assembleDebug`

- Result: `BUILD SUCCESSFUL`.
- The output includes both `CrashLogClipboardTest` cases passing; all requested module test tasks and `:app:assembleDebug` completed successfully.

## Self-review

- Confirmed the helper has the exact required limit and marker, and includes the marker in the 100,000-character limit.
- Confirmed the oversized path retains the beginning of the report and ends with the marker; the exact-boundary path returns the original string.
- Confirmed only the Copy callback changed; `shareLogs` and `concatLogs` are unchanged.
- Confirmed the instrumentation test sends the supplied approximately 5.2 MB payload through the real API-36 clipboard and verifies bounded content.
- Ran `git diff --check`: no whitespace errors.

## Concerns

- Gradle emits existing configuration-time dependency-resolution warnings (Gradle issue 2298) during all test/build commands; they did not affect task outcomes.
- The API-36 instrumentation assertion requires a foreground app because Android blocks background clipboard reads. The test launches `CrashActivity` solely for that platform requirement.
