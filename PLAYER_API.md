# Player intent API

Launch Next Player from another Android app with an `ACTION_VIEW` intent. You can
supply a video or queue, subtitles, HTTP headers, and playback options, and request
an activity result when the player closes.

Next Player supports the [MX Player VIEW intent contract](https://mx.j2inter.com/api)
and adds `playback_speed` and `background` extensions. The behavior below describes
Next Player; it does not imply that MX Player supports those extensions.

- [Launch a video](#launch-a-video)
- [URI permissions](#uri-permissions)
- [Queues and Autoplay](#queues-and-autoplay)
- [Titles and starting position](#titles-and-starting-position)
- [Subtitles](#subtitles)
- [HTTP headers](#http-headers)
- [Playback and display options](#playback-and-display-options)
- [Next Player extensions](#next-player-extensions)
- [Playback results](#playback-results)
- [New requests and saved preferences](#new-requests-and-saved-preferences)
- [Compatibility limits](#compatibility-limits)

## Launch a video

Use these intent fields:

| Field | Value |
| --- | --- |
| Action | `Intent.ACTION_VIEW` (`android.intent.action.VIEW`) |
| Data | The media `Uri` to start playing |
| Package | `dev.anilbeesetti.nextplayer`; debug builds use `dev.anilbeesetti.nextplayer.debug` |
| Activity | `dev.anilbeesetti.nextplayer.feature.player.PlayerActivity` |
| MIME type | An appropriate type such as `video/mp4`, `video/*`, or `application/vnd.apple.mpegurl` |

Set the package or component explicitly when you want Next Player. Intents addressed
to `com.mxtech.videoplayer.ad` or `com.mxtech.videoplayer.pro` launch MX Player.
An implicit VIEW intent with a matching URI and MIME type can also offer Next Player
in Android's app chooser.

Register an activity result launcher in your Activity or Fragment before it starts:

```kotlin
private val playerLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult(),
) { result ->
    val payload = result.data
    when (result.resultCode) {
        Activity.RESULT_OK -> {
            val lastUri = payload?.data
            val endBy = payload?.getStringExtra("end_by")
            // Use the returned URI and completion reason.
        }
        Activity.RESULT_CANCELED -> {
            // Playback was cancelled before it became ready.
        }
        Activity.RESULT_FIRST_USER -> {
            // Playback or the requested handoff failed.
        }
    }
}
```

Launch the request from that Activity or Fragment:

```kotlin
val intent = Intent(Intent.ACTION_VIEW).apply {
    setClassName(
        "dev.anilbeesetti.nextplayer",
        "dev.anilbeesetti.nextplayer.feature.player.PlayerActivity",
    )
    setDataAndType(videoUri, "video/mp4")
    putExtra("title", "Example video")
    putExtra("position", 30_000) // Int, milliseconds
    putExtra("return_result", true)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

try {
    playerLauncher.launch(intent)
} catch (error: ActivityNotFoundException) {
    // Offer an installation prompt or another player when Next Player is unavailable.
}
```

These examples use `android.app.Activity`, `android.content.Intent`,
`android.content.ActivityNotFoundException`, and
`androidx.activity.result.contract.ActivityResultContracts`.

Pass the MIME type with `setDataAndType()`. A concrete MIME type is applied to the
initially selected item. Wildcard types such as `video/*` leave format detection
to the player. Every queued item must still be a supported media source.

If your app queries `PackageManager` to check installation before launching, account
for Android's package visibility rules. Add this inside the caller's manifest:

```xml
<queries>
    <package android:name="dev.anilbeesetti.nextplayer" />
</queries>
```

Use the debug package instead when checking a debug build. Directly launching the
activity and handling `ActivityNotFoundException` does not require a preflight
package query. See Android's [package visibility guidance](https://developer.android.com/training/package-visibility/use-cases).

## URI permissions

For `content://` sources, the caller must grant read access to **every video and
subtitle URI** the request needs. Extras such as `video_list` and `subs` alone do
not grant permissions. Put additional URIs in `ClipData` and add
`Intent.FLAG_GRANT_READ_URI_PERMISSION`:

```kotlin
val readableUris = (videoUris + subtitleUris).distinct()
intent.clipData = ClipData.newRawUri("Playback media", readableUris.first()).apply {
    readableUris.drop(1).forEach { addItem(ClipData.Item(it)) }
}
intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
```

Here `videoUris` includes the intent's data URI, so the list is nonempty. `ClipData`
is `android.content.ClipData`. The caller must hold access that Android allows it
to forward; the player cannot obtain access to a private provider on its own.

For a successful `background=true` handoff, Next Player forwards read access for
the effective content media and subtitle URIs to its playback service before the
launch activity finishes. The service retains temporary access for its lifetime.
The handoff does not request persistable, write, or prefix permissions. A provider
can still revoke access or make a source unavailable later.

Use `content://` URIs for sharing local files across apps. Android can reject
cross-app `file://` sharing, and a raw filesystem path does not grant access.
HTTP(S) sources do not need Android URI grants; they may need request headers.

## Queues and Autoplay

| Extra | Type | Default and behavior |
| --- | --- | --- |
| `video_list` | `Uri[]`; legacy `ArrayList<Uri>` also accepted | Ordered queue. Without a supplied queue, an API request plays the data URI. |
| `video_list_is_explicit` | `Boolean` | Absent or `false`: Autoplay preferences apply. `true`: play the selected queue in full even when Autoplay is off. |
| `video_list.name` | `String[]` | Optional titles corresponding to the original `video_list` entries. |

The data URI selects the starting item in `video_list`. For the documented
contract, include it in the array. Next Player also tolerates a data URI omitted
from `video_list` by prepending it to a nonempty supplied queue. If an identical URI
occurs more than once, playback starts at its first occurrence. Duplicate entries retain
their order and their corresponding names.

An available queue and an explicit selection have different meanings:

- A file browser can provide surrounding videos for Next/Previous navigation and
  leave `video_list_is_explicit` absent or `false`. With Autoplay off, playback
  ends after the current item instead of automatically advancing.
- A caller implementing “play these selected videos” should set
  `video_list_is_explicit=true`. Next Player's own media picker does this for
  selected-video queues. Repeat behavior still follows the user's repeat mode.

For example, play two selected videos starting with the first:

```kotlin
intent.setDataAndType(firstVideoUri, "video/*")
intent.putExtra("video_list", arrayOf(firstVideoUri, secondVideoUri))
intent.putExtra("video_list.name", arrayOf("First video", "Second video"))
intent.putExtra("video_list_is_explicit", true)
```

Grant both URIs through `ClipData` when they come from a content provider. A plain
single-video launch from Next Player can build its usual folder queue; API requests
with recognized extras use the supplied queue or data URI instead.

## Titles and starting position

| Extra | Type | Default and behavior |
| --- | --- | --- |
| `title` | `String` | Optional title for the initially selected video. Otherwise media metadata or the filename supplies the title. |
| `position` | `Int` | Optional starting position in milliseconds. A nonnegative value overrides the saved resume position for the selected video. |
| `return_result` | `Boolean` | `false`. Set `true` to receive the API result payload described below. |

`position=0` explicitly starts at the beginning. A negative value or a value of
the wrong type is ignored, leaving normal resume behavior in effect. A `Long`
extra is not an accepted substitute for the `Int` position extra.

When `video_list.name` is supplied, it takes precedence over `title` for entries
in that supplied list. Keep the array aligned with `video_list`; use null entries
when you want filename or metadata fallback. A missing or null name in this array
does not fall back to `title`. If Next Player prepends the data URI because it was
absent from the list, the original names remain associated with their original
URI entries, and `title` can name the prepended item.

The initial position and API subtitle list apply only to the selected starting
item, rather than every item in the queue.

## Subtitles

| Extra | Type | Default and behavior |
| --- | --- | --- |
| `subs` | `Uri[]`; legacy `ArrayList<Uri>` also accepted | Additional subtitle sources for the selected video. |
| `subs.name` | `String[]` | Optional display labels, aligned with `subs`. |
| `subs.filename` | `String[]` | Optional filename hints, aligned with `subs`, used for labels and subtitle format detection. |
| `subs.enable` | `Uri[]`; legacy `ArrayList<Uri>` also accepted | Subtitle URIs from `subs` to mark for initial selection. |

Use the same URI values in `subs.enable` as in `subs`. Media3 displays one text
track at a time; marking several entries does not enable simultaneous rendering
of several subtitle tracks. Without an enabled entry, normal track-selection
preferences apply.

```kotlin
intent.putExtra("subs", arrayOf(englishSubtitleUri, spanishSubtitleUri))
intent.putExtra("subs.name", arrayOf("English", "Español"))
intent.putExtra("subs.filename", arrayOf("captions-en.srt", "captions-es.vtt"))
intent.putExtra("subs.enable", arrayOf(englishSubtitleUri))
```

Supply filename hints for sources whose URI does not identify their format, such
as `https://example.com/captions?id=42`. The current extension mapping recognizes
lowercase `.ass`/`.ssa`, `.vtt`, and `.ttml`/`.xml`/`.dfxp`; other extensions fall
back to SRT. Labels use `subs.name`, then `subs.filename`, then the source filename.

HTTP(S) API subtitles load through Media3 and receive the request's HTTP headers.
Local subtitles use Next Player's subtitle encoding conversion. The app can also
load subtitles from its usual local discovery and saved selections.

## HTTP headers

| Extra | Type | Default and behavior |
| --- | --- | --- |
| `headers` | `String[]` | Alternating header names and values; absent means no API-supplied headers. |

```kotlin
intent.putExtra(
    "headers",
    arrayOf(
        "User-Agent", "ExampleApp/1.0",
        "Authorization", "Bearer $accessToken",
    ),
)
```

Headers accompany HTTP(S) requests for the queue's media, manifests, segments, and
API subtitle sources. They are scoped to these media items; a later unrelated
request does not inherit them. Local sources do not receive these headers.
Data-source-specific headers, such as range headers, take precedence over an
API-supplied header with the same map key.

Supply valid HTTP header names and values. The parser ignores incomplete or
non-string pairs, blank names, names containing control characters, spaces,
non-ASCII characters or a colon, and values containing CR, LF, or NUL. The HTTP stack may reject other
invalid header syntax during playback. Prefer setting a header once per request.

## Playback and display options

These options apply to the request rather than changing the user's saved defaults.
Omitted options normally use the app's settings or standard behavior.

| Extra | Type | Values and behavior |
| --- | --- | --- |
| `decode_mode` | `Byte` | `1`: hardware (HW); `2`: FFmpeg software (SW); `4`: hardware (HW+ compatibility alias). Next Player does not implement a separate MX HW+ engine. Unsupported values leave normal decoder selection in effect. |
| `video` | `Boolean` | Defaults to `true`. `false` omits the video surface while playback and the foreground player controls remain available. |
| `video_zoom` | `Int` | `0`: stretch; `1`: best fit; `2`: original size (100%); `3`: crop. Other values use the normal zoom setting. |
| `DAR_horz`, `DAR_vert` | `Float`, both required | Override display aspect ratio with `DAR_horz / DAR_vert`, for example `16f` and `9f`. Both components must be positive and the resulting ratio must be positive and finite. |
| `orientation` | `Int` | Android orientation value from the table below, or `99999` to follow the video's orientation. Unsupported values use normal orientation policy. |
| `sticky` | `Boolean` | `true` keeps playback running when the activity goes into the background; `false` disables that automatic background-play behavior for this request. If absent, the saved background-play setting applies. Back from the foreground player still stops playback. |
| `keys.dpad_up_down` | `Int` | With controls hidden, `0`: Up/Down adjusts volume; `1`: Up selects the next item and Down selects the previous item. Supported on phones as well as TV. If absent or invalid, normal device key handling applies. |
| `suppress_error_message` | `Boolean` | Defaults to `false`. In the foreground, `true` skips a failed item if another exists, or closes the player if no next item exists, without an error dialog. |
| `secure_uri` | `Boolean` | Accepted for compatibility. Next Player has no MX-style URI property dialog, so this adds no property-dialog behavior. It does not encrypt media or change URI permissions. |

The current parser also accepts other numeric types for `decode_mode` by
converting them to an integer; callers should use the documented `Byte` type.
Decoder availability and recovery depend on the device and stream. A requested
decoder is not a guarantee that decoding will succeed; inspect the reported
decoder when a foreground result is available.

```kotlin
intent.putExtra("decode_mode", 2.toByte())
intent.putExtra("DAR_horz", 16f)
intent.putExtra("DAR_vert", 9f)
intent.putExtra("keys.dpad_up_down", 1)
```

Supported orientation values:

| Value | Android constant or policy |
| --- | --- |
| `-1` | `SCREEN_ORIENTATION_UNSPECIFIED` (system default) |
| `0` | `SCREEN_ORIENTATION_LANDSCAPE` |
| `1` | `SCREEN_ORIENTATION_PORTRAIT` |
| `6` | `SCREEN_ORIENTATION_SENSOR_LANDSCAPE` |
| `7` | `SCREEN_ORIENTATION_SENSOR_PORTRAIT` |
| `8` | `SCREEN_ORIENTATION_REVERSE_LANDSCAPE` |
| `9` | `SCREEN_ORIENTATION_REVERSE_PORTRAIT` |
| `10` | `SCREEN_ORIENTATION_FULL_SENSOR` |
| `99999` | Follow the video dimensions as they change; not an Android orientation constant. |

Android and the device's window configuration can constrain orientation requests.
DPAD queue navigation applies while the player controls are hidden; visible
controls and open player overlays retain their normal interaction behavior.

## Next Player extensions

### Playback speed

`playback_speed` accepts a positive finite number, normally passed as a `Float`:

```kotlin
intent.putExtra("playback_speed", 1.5f)
```

The value is a multiplier: `1f` is normal speed, `0.5f` is half speed, and `2f` is
double speed. Numeric extras are converted to `Float`; zero, negative values,
NaN, infinities, and values that overflow to infinity are ignored. The supported
output range and playback success depend on Media3, the device, and the stream.

The speed is applied once when this request starts. It survives queue transitions
and repeats until the user or another request changes the session speed. Omitting
the extra preserves the existing session speed; a newly created session uses the
saved default speed. The extra does not update that saved default.

### Direct background playback

`background` is a `Boolean` that defaults to `false`:

```kotlin
intent.putExtra("background", true)
intent.putExtra("playback_speed", 1.5f)
intent.putExtra("return_result", true)
```

With `background=true`, Next Player starts the requested queue in its playback
service and closes the launch activity without creating the player screen or a
video surface. A data URI is required. This request starts playback even if the
existing session was paused, and takes precedence over `sticky=false` or a
disabled saved background-play preference. Playback controls remain available
through the service's normal media controls.

The activity waits for the service to accept the queue before returning success.
Acceptance is a handoff acknowledgment, not a guarantee that every source can be
opened or decoded. Errors that occur later cannot be returned through the closed
activity. `return_result=true` returns immediately after the accepted handoff,
with `RESULT_OK` and `end_by=user`, rather than waiting for playback completion.
The actual decoder is not reported in this background handoff result.

Neither `background` nor `sticky` changes the saved background-play preference.
For `background=true`, video-surface, zoom, orientation, and DPAD options have no
player screen to operate on. Queue, speed, headers, subtitles, and URI permission
requirements still apply.

This option still uses an activity launch. Android's
[background activity launch restrictions](https://developer.android.com/guide/components/activities/secure-bal)
apply to the caller even when `background=true`; the extra controls Next Player's
handoff and UI behavior, rather than granting permission to launch from any
background component.

## Playback results

Set `return_result=true` and launch through an Activity Result launcher (or
`startActivityForResult` in a legacy integration) to request the API payload.
Without that extra, the caller must not expect the API action or extras in an
activity result.

The result intent uses action `com.mxtech.intent.result.VIEW`. Its data is the
current or last media URI, which may differ from the initial data URI after queue
navigation or an error.

| Result code | Meaning |
| --- | --- |
| `Activity.RESULT_OK` (`-1`) | Foreground playback became ready or completed, or the service accepted a direct background handoff. |
| `Activity.RESULT_CANCELED` (`0`) | The activity closed before playback became ready, without a reported playback failure. |
| `Activity.RESULT_FIRST_USER` (`1`) | Playback or the requested handoff failed. |

| Result extra | Type | When present |
| --- | --- | --- |
| `end_by` | `String` | `user` for an exit or background handoff; `playback_completion` for playback ending under the current queue and Autoplay policy. An error also uses `user`; use the result code to distinguish failure. |
| `position` | `Int` | Known current position in milliseconds for incomplete playback. Omitted on completion or when unknown. |
| `duration` | `Int` | Known current duration in milliseconds for incomplete playback. Omitted on completion or when unknown. |
| `decode_mode` | `Byte` | Actual video decoder when known for a foreground result: `1` for hardware, `2` for software. HW+ requests report hardware as `1`, not `4`. Omitted when unknown, including direct background handoffs. |

Times larger than `Int.MAX_VALUE` milliseconds are capped at that value instead
of overflowing. For a background handoff, position and duration reflect the
information available when the queue was accepted; duration may not be known yet.

Within the result callback, check whether optional extras exist before reading them:

```kotlin
val lastPositionMs = payload?.takeIf { it.hasExtra("position") }
    ?.getIntExtra("position", 0)
val actualDecoder = payload?.takeIf { it.hasExtra("decode_mode") }
    ?.getByteExtra("decode_mode", 0.toByte())
```

## New requests and saved preferences

The player activity uses `singleTop`. A new VIEW intent can be delivered to an
existing topmost player activity; otherwise Android may create another instance.
A new API request with a data URI replaces the request options and requested queue.
Omitted titles, subtitles, headers, orientation overrides, and DPAD overrides are
not inherited from the preceding request. Playback speed has the session-level
preservation behavior described above.

A plain VIEW intent with no recognized API extras can follow the app's normal
resume and folder-queue behavior instead.

Switching between foreground and direct background modes can recreate the launch
activity. Newer accepted requests supersede pending older metadata work, and an
explicit stop cancels pending playback so that old metadata cannot restart it.
Callers should rely on results and media state rather than Activity instance identity.

Most extras use their exact Android extra types. For example, use a Boolean for
`background`, an Int for `position`, and Floats for DAR components. Wrong types or
unsupported option values generally fall back to normal behavior; malformed or
unreadable media can still produce a playback error. Unknown extras are ignored.
The numeric tolerance for `decode_mode` and `playback_speed` is described above.

Request overrides leave saved defaults unchanged. Normal application behavior,
such as recording a media resume position or a user changing settings in the UI,
can still update the app's stored state.

## Compatibility limits

- `fast_mode` is deprecated in MX and is ignored by Next Player.
- MX's OpenSubtitles lookup hints `size`, `filename`, `hash.opensubtitles`,
  `video_list.size`, `video_list.filename`, and `video_list.hash.opensubtitles`
  are ignored by this intent API. `subs.filename` is supported separately as a
  subtitle format and label hint.
- SAMI multi-track URI fragments do not add SAMI support. Subtitle decoding must
  use a format that Media3 supports.
- HW+ is a hardware-decoding alias, not a separate decoder implementation.
- The intent API launches playback and returns results; it is not a general
  remote-control, library-query, thumbnail-extraction, or settings API.
- MX package names are not Next Player aliases. Change the intent's package or
  component when reusing an MX-compatible integration.
- `playback_speed` and `background` are Next Player extensions; other players may
  ignore them.

For the underlying MX extra names and types, see the
[MX Player API](https://mx.j2inter.com/api) and its
[archived intent reference](https://sites.google.com/site/mxvpen/api).
