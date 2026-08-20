
# Graviton

[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/anilbeesetti/graviton.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/graviton/graviton/releases/latest)
[![Google Play](https://img.shields.io/endpoint?color=green&logo=google-play&url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Dcom.graviton%26l%3DGoogle%2520Play%26m%3Dv%24version)](https://play.google.com/store/apps/details?id=com.graviton)
[![IzzyOnDroid](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/com.graviton)](https://apt.izzysoft.de/fdroid/index/apk/com.graviton)
[![F-Droid](https://img.shields.io/f-droid/v/com.graviton?logo=f-droid&label=F-Droid&cacheSeconds=3600)](https://f-droid.org/packages/com.graviton)
[![GitHub all releases](https://img.shields.io/github/downloads/anilbeesetti/graviton/total?logo=github&cacheSeconds=3600)](https://github.com/graviton/graviton/releases/latest)
[![Google Play](https://img.shields.io/endpoint?color=green&logo=google-play&url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Dcom.graviton%26l%3Ddownloads%26m%3D%24totalinstalls)](https://play.google.com/store/apps/details?id=com.graviton)
[![Weblate project translated](https://img.shields.io/weblate/progress/next-player?logo=weblate&logoColor=white&cacheSeconds=36000)](https://hosted.weblate.org/engage/next-player/)

Graviton is an Android native video player written in Kotlin. It provides a simple and easy-to-use interface for users to play videos on their
Android devices

**This project is still in development and is expected to have bugs. Please report any bugs you find in
the [Issues](https://github.com/graviton/graviton/issues) section.**

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80"/>](https://play.google.com/store/apps/details?id=com.graviton)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/com.graviton)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/com.graviton/)

## Overview

Graviton is an Android video player built for users who want reliable local video playback with fine-grained control over decoding, audio, subtitles, and playback behaviour. It is based on a forked and substantially modified open-source video-player codebase, extended with additional features and UI refinements to deliver a focused, modern playback experience.

Graviton is designed for:

- Everyday users who want a clean, no-nonsense local video player.
- Power users who need manual control over decoder selection and audio/subtitle tracks.
- Users who value gesture-based controls and a distraction-free player interface.

---

## Features

### Playback
- 📂 **Local video playback** — Browse and play video files stored on the device.
- ⏩ **Playback speed control** — Adjust playback speed to suit your preference.
- 🔇 **Skip silence** — Automatically skip silent segments during playback.
- 🔁 **Repeat & shuffle** — Loop a single video or shuffle a playlist.
- 🎵 **Background playback** — Continue audio playback when the app moves to the background.
- 🖼️ **Picture-in-Picture (PiP)** — Float the player over other apps on supported devices (Android 8.0+).

### Decoding
- 🤖 **Automatic decoder selection** — Graviton selects the best available decoder and falls back automatically on failure.
- ⚙️ **Hardware (HW) decoding** — GPU-accelerated decoding for smooth, battery-efficient playback.
- ⚙️ **Hardware+ (HW+) decoding** — Extended hardware decoding with additional codec support where available.
- 💻 **Software (SW) decoding** — CPU-based fallback for formats or devices where hardware decoding is unsupported.

### Audio & Subtitles
- 🎧 **Audio track selection** — Switch between multiple audio tracks in multi-audio files.
- 💬 **Subtitle support** — Load and display embedded subtitle tracks.

### Controls & Gestures
- 🔊 **Volume gestures** — Swipe vertically on one side of the screen to adjust volume.
- ☀️ **Brightness gestures** — Swipe vertically on the other side to adjust brightness.
- 🎛️ **Media notification controls** — Control playback from the system notification shade.

### Interface
- 🎨 **Theme customization** — Choose between available themes and appearance settings.
- 🌍 **External file opening** — Open video files from other apps or file managers via intent.

---

## Decoder Modes

Graviton exposes four decoder modes, selectable in the player settings:

| Mode | Description |
|------|-------------|
| **Auto** | Graviton automatically picks the most capable decoder. If hardware decoding fails for a given stream, it transparently falls back to software decoding without requiring user intervention. |
| **HW** | Forces standard hardware (codec) decoding using the device's MediaCodec pipeline. Offers the best performance and battery efficiency for supported formats. |
| **HW+** | Enables extended hardware decoding, which may unlock additional codec support on certain devices. Behaviour is device-dependent. |
| **SW** | Forces software (CPU) decoding via the bundled software codec. Use this when hardware decoding produces artefacts or fails entirely on a specific file. |

### Fallback Behaviour

In **Auto** mode, if the player encounters a decoding error — for example, an unsupported codec profile on the device's hardware decoder — it automatically retries playback using the software decoder. This ensures maximum compatibility without manual intervention.

---

## Skip Silence

Graviton includes a **Skip Silence** feature that detects and skips over audio segments that fall below a defined volume threshold during playback. When enabled, the player monitors the audio output level and advances playback past quiet or silent portions, reducing waiting time during pauses in speech or content.

> ℹ️ The exact threshold and detection window are determined by the underlying player implementation. Refer to the source code in the player module for precise configuration values.

---

## Player Controls

### On-Screen Controls

| Control | Action |
|---------|--------|
| Tap centre | Play / Pause |
| Drag seek bar | Seek to position |
| Double-tap left | Seek backward |
| Double-tap right | Seek forward |
| Speed menu | Select playback rate |
| Track menu | Switch audio or subtitle track |
| Decoder menu | Change active decoder mode |
| Repeat button | Toggle repeat (off / one / all) |
| Shuffle button | Toggle shuffle mode |

### Gestures

| Gesture | Action |
|---------|--------|
| Swipe up/down — left half of screen | Adjust brightness |
| Swipe up/down — right half of screen | Adjust volume |
| Double-tap left | Seek backward |
| Double-tap right | Seek forward |

### System Integration

- **Media notification** — Displays the current video title with transport controls (play, pause, previous, next).
- **Picture-in-Picture** — Enters PiP mode automatically or manually on Android 8.0+ devices.
- **External intent handling** — Responds to `ACTION_VIEW` intents so other apps can open videos directly in Graviton.

---

## Supported Formats

Graviton's format support is determined by the active decoder.

### Hardware Decoder

Support depends on the device's hardware MediaCodec capabilities. Commonly hardware-accelerated formats include:

- **Video:** H.264 (AVC), H.265 (HEVC), VP8, VP9, AV1 *(device-dependent)*
- **Audio:** AAC, MP3, Opus, Vorbis, FLAC, PCM

### Software Decoder

The bundled software decoder extends support to formats that hardware decoders may not handle, including less common codec profiles and containers.

### Containers

`MP4` · `MKV` · `AVI` · `MOV` · `WebM` · `TS` · `FLV` · `3GP`

> ℹ️ Exact format support depends on the Media3/ExoPlayer version included and the active decoder mode. Refer to the [ExoPlayer supported formats documentation](https://exoplayer.dev/supported-formats.html) for a comprehensive codec matrix.

---

## Architecture & Technology

| Technology | Usage |
|------------|-------|
| **Kotlin** | Primary development language |
| **Android SDK** | Target platform |
| **Media3 / ExoPlayer** | Core media playback engine |
| **Material 3** | UI components and theming |
| **Jetpack (Lifecycle, ViewBinding, etc.)** | Architecture components |
| **Kotlin Coroutines** | Asynchronous operations |
| **Gradle** | Build system |

> Graviton builds on top of Media3/ExoPlayer's `Player` and `MediaSession` APIs for playback, session management, and notification controls.

---

## Project Structure

```
com.graviton
├── ui
│   ├── player          # Video player screen, controls overlay, gesture handling
│   ├── browser         # Local file browser / media library
│   └── settings        # App and player settings screens
├── player
│   ├── decoder         # Decoder mode selection and fallback logic
│   ├── session         # MediaSession and background playback management
│   └── notification    # Media notification controller
├── data
│   ├── model           # Data models (MediaItem, Track, etc.)
│   └── repository      # Media scanning and data access
└── util                # Utility and extension functions
```

> ℹ️ Package names and structure shown above reflect the `com.graviton` namespace. Refer to the actual source tree for the authoritative layout.

---

## Requirements

| Property | Value |
|----------|-------|
| **Minimum SDK** | Android 5.0 (API 21) |
| **Target SDK** | Android 14+ (API 34) |
| **JDK** | 17 or later |
| **Build Tool** | Android Studio Hedgehog (2023.1.1)+ or Android SDK CLI |

> Verify the exact `minSdk` and `targetSdk` values in `app/build.gradle` or `app/build.gradle.kts`.

---

## Building

### Prerequisites

- Android Studio **Hedgehog (2023.1.1)** or later, **or** the Android SDK with command-line tools.
- **JDK 17** or later.
- An internet connection for the first build (Gradle downloads dependencies).

### Clone the Repository

```bash
git clone https://github.com/<your-username>/graviton.git
cd graviton
```

### Build a Debug APK

```bash
./gradlew assembleDebug
```

Output:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Build a Release APK

```bash
./gradlew assembleRelease
```

> A signing keystore is required for release builds. Configure `signingConfigs` in `app/build.gradle` or supply keystore properties via `local.properties` or environment variables.

### Run Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires a connected device or emulator)
./gradlew connectedAndroidTest
```

---

## Installation

### From a Built APK

1. Transfer the APK to your Android device via USB, cloud storage, or any file transfer method.
2. On the device, go to **Settings → Security** (or **Apps → Special App Access**) and enable **Install Unknown Apps** for your file manager or browser.
3. Open the APK file and follow the on-screen installation prompt.

### Via ADB

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Permissions

| Permission | Reason |
|------------|--------|
| `READ_EXTERNAL_STORAGE` / `READ_MEDIA_VIDEO` | Read local video files from device storage |
| `FOREGROUND_SERVICE` | Keep playback running in the background as a foreground service |
| `PICTURE_IN_PICTURE` | Enable PiP mode (declared via `android:supportsPictureInPicture`) |

> The exact permissions are declared in `app/src/main/AndroidManifest.xml`. Review that file for the authoritative and up-to-date permission list.

---

## Privacy

Graviton is a local video player. Based on the application's design:

- It reads video files from local device storage solely to enable playback.
- It does not include any network-based analytics, telemetry, or advertising SDK in the current codebase.
- No personal data is knowingly transmitted to remote servers by the application itself.

> Users should review the full source code and the privacy policies of any third-party libraries (e.g., Media3/ExoPlayer) to form their own conclusions. No absolute privacy guarantees are made here.

---

## Screenshots

> Screenshots will be added here as the project matures. To contribute screenshots, open a pull request updating this section.

---

## Contributing

Contributions are welcome. Please follow the steps below:

1. **Fork** the repository.
2. **Create a branch** for your feature or fix:
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. **Commit** your changes with a clear, descriptive message:
   ```bash
   git commit -m "feat: add XYZ functionality"
   ```
4. **Push** to your fork:
   ```bash
   git push origin feature/your-feature-name
   ```
5. **Open a Pull Request** against the `main` branch and describe your changes.

You can help translate Graviton on [Hosted Weblate](https://hosted.weblate.org/engage/next-player/).

- Follow existing code style and Kotlin conventions.
- Write or update tests where applicable.
- Keep pull requests focused — one feature or fix per PR.
- Ensure the build passes before submitting.

---

## License & Attribution

```
Copyright (c) Graviton Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

### Upstream Attribution

Graviton is derived from a forked Android video-player project. Substantial portions of the codebase originate from that upstream repository. The original authors retain copyright over their respective contributions. Graviton acknowledges and preserves all upstream license headers, copyright notices, and attribution requirements as mandated by the applicable open-source license(s).

Refer to the `LICENSE` file and individual source file headers in this repository for the full and authoritative licensing information.

---

## Disclaimer

Graviton is provided **"as is"**, without warranty of any kind, express or implied. The authors and contributors are not responsible for any damage, data loss, or other liability arising from the use of this software. Format and codec support depends on the device's hardware capabilities and the active decoder configuration.

---

## Credits

| Project / Library | Role | License |
|-------------------|------|---------|
| [AndroidX Media3 / ExoPlayer](https://github.com/androidx/media) | Core media playback engine | Apache 2.0 |
| [Material Components for Android](https://github.com/material-components/material-components-android) | UI components & theming | Apache 2.0 |
| Upstream video-player project | Foundational codebase | *(see LICENSE)* |
| Kotlin & Jetpack libraries | Language & architecture components | Apache 2.0 |

Graviton is licensed under the GNU General Public License v3.0. See the [LICENSE](LICENSE) file for more information.
