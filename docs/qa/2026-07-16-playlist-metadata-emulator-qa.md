# Playlist metadata emulator QA — 2026-07-16

## Environment

- Disposable AVD: `NextPlayer_Playlist_Metadata_QA_20260716`
- System image: Android 16 / API 36 (`android-36.1;google_apis;arm64-v8a`)
- Serial: `emulator-5554`
- Existing AVDs recorded before the run: `Pixel_6a`, `Pixel_7`, `Resizable_Experimental`, `Television_1080p`
- Existing physical device was not targeted; every device command used `-s emulator-5554`.

## Automated verification

- `:core:database:connectedDebugAndroidTest`: 7 tests, 0 failures, 0 errors.
  - Room migration 7→8: 1 test.
  - Playlist DAO: 6 tests.
- `:feature:playlist:connectedDebugAndroidTest`: 13 tests, 0 failures, 0 errors.
  - Playlist detail screen: 9 tests.
  - Playlist list screen: 4 tests.
- Relevant JVM unit-test matrix: 19 XML suites with no failures or errors.
- Relevant debug APK and Android-test APK assembly completed successfully.
- Ktlint and all scoped lint modules passed except `:feature:player:lintDebug`, which still reports the pre-existing Media3 `UnsafeOptInUsageError` calls in unchanged player state files. `:app:lintDebug` passed independently.

## Manual flows

1. Granted full photo/video access on first launch and opened the Playlists navigation tab.
2. Created an empty playlist named `Road Trip`.
   - Creation opened the detail screen.
   - Detail top bar contained Navigate up and Play all only; no delete action.
   - List row rendered as exactly two lines: `Road Trip` and `Local · 0 items`.
3. Imported `https://iptv-org.github.io/iptv/index.m3u` as `IPTV Org`.
   - The previous maximum-size error did not occur.
   - Import completed with 13,270 resolved/deduplicated items.
   - List row rendered as exactly two lines: `IPTV Org` and `M3U URL · 13270 items`.
   - Local and M3U rows used the same playlist icon and exposed the gear action.
4. Opened the linked playlist.
   - Compact video rows showed parsed titles, stream URLs, and remote artwork.
   - Thumbnails rendered at the smaller playlist-row size.
   - Detail top bar exposed Play all and Refresh, with no delete action.
5. Pulled down on the linked playlist to refresh.
   - Refresh indicators were visible in the content and top-bar action.
   - Cached rows and artwork remained visible while refresh ran.
6. Opened `00s Replay` in the player.
   - Player title displayed `00s Replay`.
   - The stream produced video before the upstream source later reported a source error.
   - Advancing updated Android media-session metadata to `1+1 International`.
   - `dumpsys media_session` reported a queue size of 13,270.
   - The media notification carried the item title and a bitmap `largeIcon`, confirming artwork propagation through MediaItem metadata.
7. Generated and indexed two local QA videos under `/storage/emulated/0/Movies/Road Trip QA`.
   - Long-pressing the folder exposed `Add to playlist`.
   - The chooser listed only `Road Trip` and `Create new playlist`; the read-only `IPTV Org` playlist was correctly excluded.
   - Adding the folder updated the list row to `Local · 2 items`.
8. Opened `Road Trip`.
   - Rows showed generated thumbnails, video titles, and the readable parent path `/storage/emulated/0/Movies/Road Trip QA` instead of a content URI.
   - Both rows exposed reorder handles.
   - Dragged `roadtrip-one` below `roadtrip-two`; the order remained `roadtrip-two`, `roadtrip-one` after navigating away and reopening.
9. Deleted `Road Trip` from its gear menu on the playlist list screen.
   - The confirmation named the selected playlist.
   - The list updated immediately and retained `IPTV Org`.

## Runtime health

- Android crash buffer was empty.
- Final log scan found no app FATAL exception, ANR, `TransactionTooLarge`, Room migration/schema failure, maximum-size error, or out-of-memory error.
- Individual public IPTV streams can become unavailable independently; one tested stream later returned a normal player `Source error` after metadata and initial playback were verified.

## Cleanup

- The disposable AVD was stopped and deleted after QA.
- Post-run AVD inventory matched the four pre-existing AVDs exactly.
