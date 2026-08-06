# Local Audio Tracks Design

## Goal

Allow a user to attach one or more local audio files to the currently playing video, select those files alongside embedded audio tracks, and have the attachments restored whenever the same video is opened again.

## User Experience

The audio-track selector keeps its existing embedded-track radio buttons and Disable option. A full-width **Open local audio** button appears below them, matching the existing **Open local subtitle** interaction.

Tapping the button closes the selector and opens Android's document picker filtered to `audio/*`. The picker starts in the current video's directory when Android's storage provider can represent that location. Cancelling the picker changes nothing.

After a successful pick, the app retains read access to the document, attaches it to the current video, and resumes playback at the same position. Every distinct picked file remains available as an audio-track choice. Picking a URI already attached to the video is idempotent and does not add a duplicate. Attachments are restored on subsequent openings of the video.

## Architecture

### Picker and UI

`AudioTrackSelectorView` receives an `onSelectAudioClick` callback and displays the new button. The callback travels through `OverlayShowView` and `MediaPlayerScreen` to `PlayerActivity`.

`PlayerActivity` owns a dedicated suspend activity-result launcher using the existing `OpenDocumentAtInitialUri` contract. It requests `audio/*`, computes the same initial directory used by the subtitle picker, takes persistable read permission, and sends the selected URI to `PlayerService` through a new `ADD_AUDIO_TRACK` MediaSession custom command. While either file picker is awaiting a result, `onStop` pauses playback just as it currently does for subtitle picking.

### Persistence

Room database version 8 adds a non-null `external_audio_tracks` text column with an empty-string default to `media_state`. A `MIGRATION_7_8` migration preserves all existing playback state.

The new column uses the existing `UriListConverter` representation. `MediumStateEntity`, `VideoState`, the entity-to-model mapper, `MediaRepository`, `LocalMediaRepository`, and `FakeMediaRepository` expose a list of external audio URIs. Repository insertion preserves pick order and ignores duplicate URIs.

### Playback

Media3 supports external subtitles directly on `MediaItem`, but has no equivalent external-audio configuration. Playback therefore uses an external-audio-aware `MediaSource.Factory` around the existing default factory.

When `PlayerService` enriches media items from stored video state, it writes the external audio URI list into a private, parcelable metadata representation on the `MediaItem`. The custom factory reads that representation. With no external audio it returns the normal source unchanged; otherwise it creates one progressive source per external audio URI and combines them with the normal video source in a `MergingMediaSource`. This preserves embedded audio while exposing each external file as another selectable audio group.

The `ADD_AUDIO_TRACK` command validates its URI argument, persists the distinct URI, updates the current item's external-audio metadata, and replaces the current item while retaining its index, position, play state, subtitle configuration, and other metadata. The same media-source factory path is therefore used for both immediate attachment and later restoration.

Track selection continues through the existing `rememberTracksState` and `switchTrack` code. The existing audio-track index persistence applies to the combined embedded and external list.

## Error Handling

- A cancelled picker performs no command or database write.
- Missing command arguments return Media3's bad-value session result.
- Failure to acquire persistable permission or read the chosen URI leaves playback and stored attachments unchanged and is logged.
- A source that later proves unreadable or unsupported is reported through the existing player error UI and log path; existing video metadata and other attached URI entries remain intact.
- Duplicate picks are successful no-ops at the repository layer.

## Data and Compatibility

The migration only appends a nullable-safe, non-null text column with `DEFAULT ''`; no existing rows are rewritten or deleted. Existing users therefore retain playback position, track selections, subtitles, and other state.

The feature accepts audio documents supplied through Android's Storage Access Framework. It does not copy, transcode, remux, trim, offset, or delete user media. External tracks are aligned from media time zero; synchronization controls are outside this feature's scope.

## Testing

Automated coverage will follow red-green-refactor and include:

- repository tests for ordered insertion and URI deduplication;
- mapper tests proving stored external audio URIs reach `VideoState`;
- a Room migration test proving a version-7 database upgrades without losing existing state and receives an empty external-audio list;
- media-source factory tests for the unchanged no-audio case and merged-source construction for multiple audio URIs;
- custom-command tests for missing URI rejection and successful attachment behavior where practical within the existing service test seams;
- Compose/UI tests or focused callback tests for the audio selector's open-local-audio action;
- the relevant module test suites plus a full debug build.

End-to-end QA will create a temporary Android emulator, install the debug app, and load generated fixtures consisting of a video and at least two distinguishable audio tracks. The run will verify document picking, immediate appearance of both external tracks, switching between them, persistence after reopening the video, absence of crashes in logcat, and expected UI state in layout dumps/screenshots. The emulator will then be stopped and removed, even if QA fails.

## Out of Scope

- Removing an attached external audio URI from the selector.
- Audio delay or synchronization adjustment.
- Automatic discovery of neighboring audio files.
- Transcoding unsupported formats.
- Translation updates beyond adding the default English resource; untranslated locales use Android's normal resource fallback.
