# Playlist emulator QA — 2026-07-15

## Scope and target

- Worktree: `/Users/anil/.codex/worktrees/cd69/nextplayer`
- Implementation SHA at manual-QA start: `96dc8043a7c7acbc70f11cee9514658bd9fa901b`
- Instrumentation-harness fix SHA: `9543ea45d0b14c3fcf679b7c92142a01edc27079`
- Quoted-delete and artwork correction regression SHA: `9d54da466dbcb3d92e530c41da36bf5314e0bb14`
- Dedicated AVD: `nextplayer_playlist_qa_api37`
- Focused correction-regression AVD: `nextplayer_playlist_qa_api37_regression`
- Recorded serial: `emulator-5556`
- Hardware profile: Pixel 6 (`hw.device.name=pixel_6`, 1080 × 2400, 420 dpi)
- System image: `system-images/android-37.0/google_apis_ps16k/arm64-v8a`
- Runtime: API 37, ARM64 v8a, Google APIs, 16 KB page-size image
- APK: `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`
- Package: `dev.anilbeesetti.nextplayer.debug`

The AVD was created fresh, booted with `-no-snapshot -wipe-data`, and all `adb`, UI-tree, screenshot, and log commands were explicitly targeted to `emulator-5556`.

## Safety inventory

Before creation, `android emulator list --long`, raw `emulator -list-avds`, and `adb devices -l` showed:

- `Television_1080p` — offline
- `Pixel_6a` — online at `emulator-5554`
- `Pixel_7` — offline
- `Resizable_Experimental` — offline
- Raw AVD list: `Pixel_6a`, `Pixel_7`, `Resizable_Experimental`, `Television_1080p`
- Connected devices: only the pre-existing `emulator-5554`

The pre-existing `emulator-5554` was never selected for installation, instrumentation, UI interaction, screenshots, logcat, stop, or deletion. The dedicated AVD identity was checked with `adb -s emulator-5556 emu avd name` before use.

After the final instrumentation rerun, all three inventory commands returned to the exact pre-run state:

- CLI AVDs: `Television_1080p` offline, `Pixel_6a` online at `emulator-5554`, `Pixel_7` offline, and `Resizable_Experimental` offline
- Raw AVD list: `Pixel_6a`, `Pixel_7`, `Resizable_Experimental`, `Television_1080p`
- Connected devices: only the original `emulator-5554`

Both `~/.android/avd/nextplayer_playlist_qa_api37.ini` and its `.avd` directory were confirmed absent. No `http.server 8765` process remained.

The later focused correction regression retained its own PRE/POST CLI AVD, raw AVD, and adb inventories (`regression-00-*` and `regression-99-*`). All three pairs compare byte-for-byte equal after cleanup. Only `nextplayer_playlist_qa_api37_regression` was stopped and removed; its `.ini` and `.avd` paths are absent, while the pre-existing `Pixel_6a` remained online at `emulator-5554` and was never targeted.

## Setup

The required API 37 image was absent. Only these supporting SDK packages were installed:

- `system-images/android-37.0/google_apis_ps16k/arm64-v8a`
- `cmdline-tools/latest`, needed for a uniquely named Pixel 6 AVD

Two videos were created with the required emulator `screenrecord --time-limit 2` flow, media-scanned, and pulled to `/tmp/nextplayer-playlist-qa`. The initial non-verbose recordings were zero bytes, so they were replaced with verbose screen recordings; both replacements were valid 64,384-byte MP4 containers. Tracked source fixtures are:

- `qa/playlists/linked.m3u` — one entry
- `qa/playlists/linked-refreshed.m3u` — two entries

A temporary Python HTTP server served `/tmp/nextplayer-playlist-qa` on port 8765.

## Automated instrumentation

Command (dedicated serial only):

```text
ANDROID_SERIAL=emulator-5556 ./gradlew :core:database:connectedDebugAndroidTest :feature:playlist:connectedDebugAndroidTest --console=plain
```

Initial result:

| Module | Tests | Passed | Failed | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| `core:database` | 6 | 6 | 0 | 0 | 0 |
| `feature:playlist` | 9 | 7 | 2 | 0 | 0 |

Database coverage included migration 7→8 plus five DAO cases. The two playlist failures were test-harness expectations, not failures observed in the real app:

1. `moveInProgressRemovesTouchAndTvReorderAffordances` called `composeRule.setContent` twice in one test, which Compose rejects with “Activity has already set content.” The smallest correction is separate touch/TV tests or one content tree driven by mutable state.
2. `deleteRequiresConfirmationBeforeDispatchingAction` expected `Remove "Movies"?`. On the real app the confirmation was visible as `Remove Movies?` because the quote delimiters were not escaped in the Android string resource. Review determined that the intended quoted copy should be explicit: the resource now escapes the quotes and the instrumentation expectation is restored. The focused fresh-emulator regression below verifies the correction.

Playlist instrumentation rerun after harness fix `9543ea45` (before the later quoted-copy correction):

| Module | Tests | Passed | Failed | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| `feature:playlist` | 10 | 10 | 0 | 0 | 0 |

Together with the unchanged database XML, that connected coverage was 16 tests, 16 passed, zero failures/errors/skips. The playlist XML timestamp was `2026-07-15T14:38:28`, and the suite completed in 20.739 seconds.

### Focused correction regression

Commit `9d54da466dbcb3d92e530c41da36bf5314e0bb14` was installed and tested on the newly created `nextplayer_playlist_qa_api37_regression` AVD, booted with `-no-snapshot -wipe-data`. Every command targeted only `emulator-5556`.

```text
ANDROID_SERIAL=emulator-5556 ./gradlew :feature:playlist:connectedDebugAndroidTest --console=plain
```

The suite passed 10/10 with zero failures, errors, or skips in 23.326 seconds (`regression-01-instrumentation.txt`, `regression-01-instrumentation-results.xml`). `PlaylistDetailScreenTest` passed 8/8, `PlaylistListScreenTest` passed 2/2, and `deleteRequiresConfirmationBeforeDispatchingAction` passed in 2.866 seconds. This supersedes the quoted-copy rerun pending note above.

## Manual journeys

All tap/long-press/drag/swipe coordinates came from the current `android layout` or UI Automator tree. Every retained screenshot below was visually inspected.

| Journey assertion | Result | Evidence |
| --- | --- | --- |
| Media permission offered and “Allow all” granted through UI | PASS | `00-permission.png`, `00-launch-layout.json` |
| Playlists top-level tab is present and opens | PASS | `01-home-layout.json`, `02-empty-playlists.png` |
| Fresh Playlists screen has empty state and create FAB | PASS | `02-empty-playlists-layout.json`, `02-empty-playlists-ui.xml`, `02-empty-playlists.png` |
| FAB offers exactly empty, M3U URL, and M3U file creation | PASS | `03-create-choices-layout.json` |
| Empty-playlist details accept `QA Editable` | PASS | `04-empty-create-dialog-layout.json` |
| `QA Editable` is created with zero items | PASS | `05-editable-created-layout.json`, `05-editable-created-ui.xml`, `05-editable-created.png` |
| Long-press selection reaches 2/2 and exposes Add to playlist | PASS | selection tree inspected during run |
| Add dialog selects `QA Editable`; add succeeds and selection closes | PASS | `06-added-selection-closed-layout.json`, `06-added-selection-closed-ui.xml`, `06-added-selection-closed.png` |
| Editable playlist reports and displays both videos | PASS | `07-editable-two-items-layout.json`, `07-editable-two-items-ui.xml` |
| Starting second item sends second URI as current and full list as queue | PASS | `08-second-item-activity.txt`, `08-second-item-media-session.txt`, `08-second-item-logcat.txt` |
| Dragging second item above first changes order | PASS | live layout after drag |
| Reordered second→first order survives leaving and reopening details | PASS | `09-reorder-persisted-layout.json`, `09-reorder-persisted-ui.xml`, `09-reorder-persisted.png` |
| Linked playlist initially caches one item and exposes refresh, not reorder | PASS with host-alias fallback | `10-linked-initial-layout.json`, `10-linked-initial-ui.xml`, `10-linked-initial.png` |
| Pull-to-refresh replaces cache with both fixture entries | PASS | `11-linked-refreshed-layout.json`, `11-linked-refreshed-ui.xml`, `11-linked-refreshed.png`, `11-linked-refresh-logcat.txt` |
| Linked playlist is absent from Add to playlist targets | PASS | `12-linked-excluded-from-add-layout.json`, `12-linked-excluded-from-add-ui.xml`, `12-linked-excluded-from-add.png` |
| Editable delete requires a visible quoted confirmation before dispatch | PASS | `13-editable-delete-confirmation-layout.json`, `13-editable-delete-confirmation-ui.xml`, `13-editable-delete-confirmation.png`, `regression-08-quoted-delete-confirmation.png` |
| Editable and linked playlists delete through UI; empty state returns | PASS | `14-empty-after-deletes-layout.json`, `14-empty-after-deletes-ui.xml`, `14-empty-after-deletes.png` |

### Playback dispatch evidence

The second editable row was `content://media/external/video/media/18`. After tapping it:

- `dumpsys activity activities` showed `PlayerActivity` with Intent data `content://media/external/video/media/18` and extras.
- `dumpsys media_session` reported metadata `playlist-qa-two.mp4` and queue size `2`.

This proves both required routing properties: the selected second item is current, and both playlist items are supplied.

The focused correction regression repeated that route with valid four-second H.264/MP4 fixtures. `PlayerActivity` received the second item at `content://media/external/video/media/20`, while the media session reported `playlist-regression-two.mp4` and queue size `2` (`regression-13-playback-activity.txt`, `regression-14-playback-media-session.txt`). The API 37 goldfish decoder then failed its binder/memfd AVC input path, but only after the selected URI, full queue, notification metadata, and bitmap artwork were established.

### Linked-source fallback

The specified `http://10.0.2.2:8765/linked.m3u` was tried first. The app correctly surfaced its configured 10-second connection failure:

```text
failed to connect to /10.0.2.2 (port 8765) from /10.0.2.17 ... after 10000ms
```

The host server responded `200 OK` on localhost and the emulator network was validated, but this API 37 image could not reach the host alias. To complete product behavior coverage, the dedicated serial received only this reverse rule:

```text
adb -s emulator-5556 reverse tcp:8765 tcp:8765
```

The linked playlist was then created from `http://127.0.0.1:8765/Linked.m3u`. (`input text` capitalized the path’s first character, so the temporary server received a matching alias.) The parsed M3U entries themselves retained the requested `10.0.2.2` media URLs; source creation, caching, read-only behavior, and refresh were all exercised independently of video decoding.

## Log review and limitations

The post-deletion scan found no `FATAL EXCEPTION`, Room/SQLite failure, permission denial, `SecurityException`, StrictMode network-on-main-thread violation, or `NetworkOnMainThreadException` (`14-final-log-scan.txt`). Earlier playback evidence preserved in `08-second-item-logcat.txt` showed SystemUI receiving `EACCES (Permission denied)` while opening the app-private `file:///data/user/0/dev.anilbeesetti.nextplayer.debug/files/thumbnails/...` artwork URI. Root-cause tracing found that `PlayerService` published a private Coil cache file as `MediaMetadata.artworkUri`; the source correction now publishes compressed thumbnail bytes as in-process `artworkData`.

The fresh correction regression resolves that pending runtime check. Its complete playback log contains zero `files/thumbnails` references, zero app-private `file:///data/user/0/dev.anilbeesetti.nextplayer.debug` references, and zero `EACCES` entries (`regression-16-playback-logcat.txt`). Therefore there is no SystemUI/app `Permission denied` involving the private thumbnail path. `dumpsys notification --noredact` reports `android.largeIcon=Icon (Icon(typ=BITMAP size=126x94))`, and the expanded media card visibly renders the generated artwork (`regression-17-playback-notification.txt`, `regression-18-media-notification.png`). Android framework also emitted unrelated `ContentProviderHelper` warnings containing `assuming permission denied` while holding the window-manager lock; they reference neither SystemUI, Next Player, thumbnails, nor an app-private URI. The fresh crash buffer is empty (`regression-21-crash-buffer.txt`, zero lines).

All 12 retained `*-ui.xml` files have had UI Automator's non-XML `UI hierchary dumped to: /dev/tty` suffix removed and now validate with `xmllint`.

Known environment/fixture limitations and resolved follow-ups:

1. The API 37 image available from the SDK was the Google APIs 16 KB ARM64 variant (`google_apis_ps16k`), not a non-16 KB image.
2. The one-frame screen-recorded MP4 reached `PlayerActivity` and populated a two-item media queue, but API 37’s emulator MediaCodec rejected its AVC input. `08-second-item-player.png` and `08-second-item-logcat.txt` preserve that fixture/emulator decoder error. Playlist playback dispatch itself was verified through the activity Intent and media-session queue.
3. The emulator could not connect to the conventional `10.0.2.2:8765` host alias, so linked creation/refresh used the scoped `adb reverse` fallback described above.
4. Two initial playlist instrumentation assertions required harness correction; that suite passed 10/10 on the same dedicated AVD. The later intentionally quoted delete copy now also passes its focused fresh-emulator instrumentation and is visibly confirmed as `Remove "QA Regression"?`.
5. The original playback evidence contains the historical SystemUI thumbnail permission failure described above. The fresh correction regression resolves it at runtime: no private thumbnail path/EACCES remains, and SystemUI renders bitmap artwork in the media card.

## Cleanup

The app-level test playlists were deleted through their confirmation dialogs and the Playlists empty state returned. After the controller-authorized instrumentation rerun:

1. The Python fixture server was interrupted cleanly.
2. Final AVD identity/API/ABI were rechecked on `emulator-5556`; the crash buffer was empty.
3. `adb -s emulator-5556 emu kill` stopped only the dedicated serial, which disconnected on the third two-second poll.
4. `android --sdk=/Users/anil/Library/Android/sdk emulator remove nextplayer_playlist_qa_api37 --force` reported successful removal.
5. CLI AVD, raw AVD, and adb inventories matched the pre-run inventories exactly, and the dedicated AVD files were absent.

The installed API 37 system image and command-line tools were intentionally retained; the task required deleting the fresh AVD, not uninstalling shared SDK packages.

For the focused correction regression, the editable `QA Regression` playlist was deleted through its quoted confirmation dialog and the empty state returned (`regression-19-deleted-empty-list.png`). The crash buffer was empty, only `nextplayer_playlist_qa_api37_regression` was stopped and removed, all three PRE/POST inventories compare exactly, and both regression AVD paths are absent (`regression-20-final-device.txt`, `regression-21-crash-buffer.txt`, `regression-00-*`, `regression-99-*`).
