# Global playback controls verification

Third stack layer: `cast/global-playback-controls`, based on `cast/home-mini-player` (#2087), above #2086. The approved requirements remain in [the design](../global-playback-controls.md).

## Automated checks

Run with JDK 17 launcher, the repository's JVM 21 daemon criteria, and `ANDROID_HOME=/Users/anil/Library/Android/sdk`:

```sh
./gradlew assembleDebug :app:assemblePlayDebugAndroidTest test ktlintCheck --max-workers=2 \
  '-Dorg.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8'
```

Final XML totals: **812 tests in 144 suites, zero failures, errors or skips**. XML is inspected because local tests use `ignoreFailures=true`.

Meaningful regressions cover:

- Service-owned four-second dismissal expiry, invalidation by new queues, paused/playing local restoration and playback settings.
- Paused receiver acknowledgment, mismatched/unsupported media, cancellation and a second explicit selection, failed automatic recovery without local autoplay, placeholder recovery, ongoing item/position snapshots and repeated late returns after Undo.
- Consumed resume hints on the current Cast item: restoring a dismissed queue after advancement retains live position and preserves other items' resume metadata.
- Persistent compact/expanded controls, denied commands, seek/output volume, artwork selection versus compact dismissal, slow expansion reversal, queue/Back hierarchy, PIN exclusion, RTL and 2× text.
- Existing local surface ownership, external launch/API compatibility, playback completion, subtitles and root D-pad traversal in both distributions.

## Device acceptance

Device: `NextPlayer_Cast_QA_API35`, Android 15 / API 35, arm64 emulator. UI actions and screenshots use the thread's Device panel and its returned `agent-device` CLI; instrumentation uses the project's AndroidX runner. The UI session is closed before instrumentation to avoid concurrent UiAutomation ownership.

Final applicable instrumentation runs:

| Distribution | Tests | Result |
| --- | ---: | --- |
| Play | 27 | Zero failures/errors/skips |
| Standard | 10 | Zero failures/errors/skips |

Play covers `MiniPlayerIntegrationTest`, `PlayerApiRequestRegressionTest`, `PlayerApiLaunchOptionsTest`, `BackgroundPlaybackCompletionTest` and `PlaylistCompletionTest`. Standard covers `MiniPlayerIntegrationTest` plus the external handoff/Undo/grants regression. These are focused acceptance runs, not the entire instrumentation suite.

Flows include root tabs and recreation, nested settings, compact/expanded entry, Watch video return with paused position, notification entry from direct fullscreen with background playback disabled, playing/paused Undo, Stop persistence, vault PIN exclusion, playlist-form text entry without stopping playback, external API results/grants, queue completion and autoplay/repeat compatibility.

The final one-line Cast snapshot-helper correction was verified by eight transfer tests and the complete root checks after these device runs. It changes Cast dismissal snapshots, not the local flows exercised by the emulator. The current Play APK was reinstalled afterward.

The external-caller regression runs under a different UID and checks result code, URI and position, then dismisses/restores the paused queue, verifies URI access throughout Undo, plays another queued item, and confirms media/subtitle grant revocation after all service controllers are released.

## Screenshots

All media is a generated test pattern. The before build is the exact parent commit `da80c29f23f3304775420b7a5869265410ba67bd`, built from an isolated archive; parent branches were not modified.

Before/after screenshots were not captured. The Device CLI's explicit activity launch normalized a fully qualified activity into an invalid component and returned `adb exited with code 1`. The permitted debug retry reproduced it, so manual device automation stopped under the device-tool rule. Ordinary baseline app navigation had worked; this was a launcher-tool failure, not a reported app crash.

Manual visual checks for rotation/resizing, IME positioning and direct D-pad interaction therefore remain incomplete. JVM/Compose coverage exercises RTL, 2× text, rail layouts, D-pad traversal and gestures; instrumentation covers activity recreation and form text entry.

## Limits

- No physical phone was connected during rediscovery, so no phone/receiver runtime claims are made. Cast connection/recovery timing still needs hardware verification.
- The prior original 4K/~59fps/~56 Mbps Camera buffering problem remains outside this UI layer. No transcoding or server-performance fix is claimed.
- No PixelPlayer source or assets were copied. No dependencies or `fastlane/` files changed.
