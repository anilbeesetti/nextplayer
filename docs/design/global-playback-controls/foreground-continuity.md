# Foreground playback continuity

Returning to an expanded player or queue briefly exposed browsing because its UI MediaController was released on stop. The queue stage had been saved, but its rendered controls still disappeared until a fresh service connection completed.

The browsing overlay now retains its live controller while playback, Cast connection/recovery or Undo needs it. A stopped Activity releases idle/ended sessions through player/session listeners, without waiting for UI recomposition. The service holds that binding through four-second Undo and pending position persistence, then allows release. Fullscreen video retains its existing release-on-stop policy; Activity destruction always releases either controller. Superseded connection futures cannot publish a stale controller.

A restored expanded view or explicit expansion request covers browsing with the player's background and a progress indicator while a genuinely new connection loads. PIN exclusion remains in force. Pending expansion is applied on the first connected frame.

## Verification

A real MediaSession/Robolectric root-overlay regression failed when backgrounding removed expanded_player; it now keeps that surface and never reports expanded=false through stop/start. The same test checks the cold reconnecting cover. Other regressions cover paused sessions, transient empty Cast timelines, connection loss, idle and local-ended release, foreground reconnect, destruction and suspended Stop/expiry position saves.

Two native external-URI cases keep MainActivity backgrounded and alive. Stop releases media/subtitle grants; dismissal preserves them through Undo and releases them after expiry. Readiness uses the project's recursive accessibility walk because Compose does not implement the platform find-by-text search.

The broad Play run exposed an order-dependent queue-focus test: it requested keyboard focus after pointer paging while still in Touch mode. The unchanged case passed alone. The test now explicitly enters keyboard mode and drains layout before asserting focused Remove; no product focus code changed.

Root builds and ktlint pass. Parsed XML reports **987 local tests in 170 suites, zero failures/errors/skips**. The final Pixel_6a API 37 arm64 run passes **43 Play acceptance cases** across MiniPlayerIntegrationTest, PlayerApiRequestRegressionTest, PlayerApiLaunchOptionsTest, BackgroundPlaybackCompletionTest and PlaylistCompletionTest. **Nine standard-flavor checks** pass: queue stages across lifecycle/recreation, Watch video return, notification entry, Stop and external handoff/grant cleanup. Both native runs have only success/start status codes; these are focused acceptance groups, not the entire instrumentation suite.

[Machine-readable totals](foreground-test-results.json). Final architect review found no production blocker. Play was restored on Pixel_6a and installed on the connected OPPO CPH2689 / Android 16 API 36, preserving data and leaving the phone app closed. No phone interaction or physical Cast testing was performed; force-killed process recovery is outside this round.
