# Issue 1828 Crash-Log Clipboard Design

## Problem

Issue #1828 reports that hiding a large video freezes and crashes the app. The attached exception is a `TransactionTooLargeException` from `ClipboardManager.setPrimaryClip()` with a 5,193,276-byte Binder parcel. The touch-dispatch frames and the repository's only full-report clipboard call show that this is a secondary crash after the user presses Copy on `CrashActivity`, not a vault operation sending video bytes through Binder.

A clean Android 16 (API 36) reproduction hid a synthetic 1 GiB video successfully, with no crash or ANR. Logcat showed Android recovering a failed direct rename by copying across storage boundaries, which can keep the non-dismissible progress UI visible for large physical files, but it did not produce the reported exception. The primary failure that originally opened `CrashActivity` is absent from the issue.

## Approved Scope

Fix the reproducible crash without speculating about an unobserved vault failure:

- Bound only the crash report sent to the system clipboard.
- Preserve the beginning of the report, which contains device information and the primary exception before logcat.
- Append a visible truncation marker directing users to Share for complete logs.
- Keep Share unchanged so it continues writing the full report to a cache file and sending a content URI.
- Do not change vault move behavior, the Hide UI, or log collection in this fix.

## Design

Add a focused crash-clipboard helper in the app module. It will limit clipboard text to 100,000 UTF-16 characters, including the truncation marker. This is conservatively below Android's 1 MB process-wide Binder transaction buffer: plain text is parcelled as UTF-16, so the bounded payload is approximately 200 KB plus small metadata.

`CrashActivity` will continue composing the full report in its existing order. The Copy action will pass that report through the helper before creating `ClipData`; the Share action will continue using the unbounded report file.

The helper owns two behaviors:

1. Reports at or below the limit are unchanged.
2. Oversized reports retain their prefix, end with `\n\n[Crash report truncated. Use Share for full logs.]`, and never exceed 100,000 characters.

## Error Handling

The fix prevents the known Binder failure by keeping the transaction well below the documented limit. It does not swallow unrelated clipboard exceptions. If an unrelated platform failure occurs, existing crash handling remains observable rather than silently claiming that Copy succeeded.

## Testing

- JVM tests cover unchanged in-limit reports and exact prefix/marker/length behavior for oversized reports.
- An API-36 instrumentation regression sends the issue's approximately 5.2 MB UTF-16 payload through the real `ClipboardManager` and verifies that Copy completes and the clipboard contains bounded, marked text.
- Final emulator QA repeats the 1 GiB Hide journey using UI-tree-derived coordinates and captures a screenshot, logcat, crash buffer, exit info, and ANR state without pressing the unsafe legacy Copy action.

## Non-Goals

- Reworking vault storage or adding transfer progress.
- Treating file size as Binder data; Hide passes URI and metadata, not video bytes.
- Truncating the full report written by Share.
