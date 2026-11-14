# Queue lifecycle retention

The expanded player's portrait half-open/full-open queue and landscape full-open queue retain their stage after background/foreground and same-orientation Activity recreation.

This stage-retention repair originally released the Activity controller on stop. The later [foreground-continuity repair](foreground-continuity.md) retains active background connections to prevent a browsing-screen flash; saved stage/layout restoration remains in use. Its nullable composition branch removed PlaybackOverlayContent, discarding the queue's remembered state. The fix hoists that UI state above the connection boundary. A small Compose Saver stores only the logical stage and layout mode; a fresh coroutine scope and new window anchors restore its geometry. The orientation effect now resets the queue only when the actual layout mode changes. Playback/controller ownership, queue actions and Back/collapse behavior stay unchanged.

## Regression evidence

On the existing Pixel_6a, API 37, arm64:

- Before the hoist, both portrait lifecycle regressions failed because playback_queue was no longer displayed after CREATED → RESUMED.
- With only the hoist, all three same-orientation recreation checks still failed because the new Activity lost the stage.
- The complete fix passes three cases in each distribution: portrait half-open, portrait fully open and landscape fully open. Each case verifies stop/resume and Activity recreation, matching the queue panel's top edge within one pixel and preserving paused intent. The half-open assertion also proves it is distinct from the full-open stage.

The project's AndroidJUnitRunner ran MiniPlayerIntegrationTest methods halfOpenQueueSurvivesBackgroundAndForeground, fullyOpenQueueSurvivesBackgroundAndForeground and landscapeQueueSurvivesBackgroundAndForeground for Play and standard. These are targeted lifecycle regressions, not the entire instrumentation suite.

Both debug app/test APKs and ktlint pass using the root command in [verification](verification.md). Parsed XML reports **981 local tests in 170 suites, zero failures/errors/skips**; Gradle ignoreFailures success alone was not used. An architect reviewed the completed source and found no blocker. The final Play APK was restored on Pixel_6a. No physical phone was connected; force-killing the app process and physical Cast were not tested.
