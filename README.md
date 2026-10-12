<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="128" height="128" alt="Graviton Logo">
</p>

<h1 align="center">Graviton</h1>

<p align="center">
  <strong>A modern, high-performance Android media player built with Jetpack Compose & AndroidX Media3</strong>
</p>

<p align="center">
  Graviton offers a seamless, distraction-free video and audio playback experience for Android. Built from the ground up with modern Jetpack Compose UI and low-latency hardware decoding, Graviton combines performance, privacy, and extensive format support into a sleek, Material 3 Expressive interface.
</p>

<p align="center">
  <a href="https://github.com/librespark-crypto/graviton/releases/latest">
    <img src="https://img.shields.io/github/v/release/librespark-crypto/graviton?style=flat-square&color=6C5CE7&label=Release" alt="Latest Release">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-GPL_v3-blue.svg?style=flat-square" alt="License: GPL v3">
  </a>
  <a href="https://developer.android.com">
    <img src="https://img.shields.io/badge/Android-7.0%2B%20%28API%2024%2B%29-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android Version">
  </a>
  <a href="https://github.com/librespark-crypto/graviton/actions">
    <img src="https://img.shields.io/github/actions/workflow/status/librespark-crypto/graviton/build.yml?branch=main&style=flat-square&label=Build" alt="Build Status">
  </a>
</p>

<p align="center">
  <a href="#screenshots">Screenshots</a> •
  <a href="#key-features">Key Features</a> •
  <a href="#installation">Installation</a> •
  <a href="#building-from-source">Building from Source</a> •
  <a href="#architecture">Architecture</a> •
  <a href="#contributing">Contributing</a> •
  <a href="#credits--license">Credits & License</a>
</p>

---

## Screenshots

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="23%" alt="Media Library">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="23%" alt="Video Player">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="23%" alt="Settings">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width="23%" alt="Track Selector">
</p>

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="23%" alt="Folder View">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="23%" alt="Landscape Controls">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="23%" alt="Playlist Detail">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" width="23%" alt="Appearance Customization">
</p>

---

## Key Features

### 🎬 Playback & Engine
- **Flexible Hardware Decoding:** Native support for HW, HW+, and SW decoding modes powered by AndroidX Media3 (ExoPlayer) and custom FFmpeg native extensions.
- **Broad Format Support:** Smooth playback for popular video and audio formats including MP4, MKV, WebM, TS, MP3, FLAC, AAC, and OGG.
- **Variable Playback Speed:** Dynamic playback speed adjustment from 0.25x to 4.0x with pitch preservation and hold-to-boost temporary speed gestures.
- **Advanced Controls:** A-B segment repeat looping, skip silence, audio track selector, frame stepping, and background audio playback.
- **Picture-in-Picture (PiP):** Seamless floating window playback when switching apps or navigating away.

### 🎨 Material 3 Expressive UI
- **Modern Interface:** Built natively using Jetpack Compose and Material 3 design guidelines for fluid transitions and dark-theme aesthetics.
- **Dynamic Theming:** Supports Android Material You dynamic color palettes, OLED black dark theme, and high-contrast dark mode.
- **Glass UI Effect:** Optional translucent frosted glass style overlays for UI controls without altering video surface clarity.

### 👆 Intuitive Gestures
- **Volume & Brightness:** Smooth vertical edge swipe gestures for quick volume and screen brightness adjustments.
- **Precision Seeking:** Double-tap seeking, horizontal swipe seek, and long-press hold seek feedback indicators.
- **Zoom & Scale:** Pinch-to-zoom, fit-to-screen, stretch, crop, and custom aspect ratio controls.

### 💬 Subtitles & Audio
- **Subtitle Compatibility:** Native support for Embedded and external subtitles (SRT, ASS/SSA, VTT).
- **Customization & Sync:** Fine-grained subtitle delay adjustments, font styling, positioning, and online subtitle search integration.
- **Audio Routing:** Multi-audio track switching, volume boost, and audio/video sync offset fine-tuning.

### 🔐 Media Library & Privacy
- **Vault:** Secure PIN-protected media storage for hiding sensitive local video content.
- **Library Management:** Folder navigation, video grid view, custom playlists, search, and recently played history.
- **Music Player:** Integrated audio and music playback interface with playlist and queue management.

### 🌐 Network Streaming
- **Protocol Support:** Stream direct remote video URLs and network protocols including HTTP, HTTPS, SMB, SFTP, WebDAV, and FTP.

---

## Installation

### GitHub Releases (Recommended)

1. Download the latest release APK directly from [GitHub Releases](https://github.com/librespark-crypto/graviton/releases/latest).
2. Open the downloaded `.apk` file on your Android device.
3. If prompted, enable "Install from Unknown Sources" for your browser or file manager.
4. Follow the on-screen instructions to complete installation.

### Supported Architectures

Graviton supports device architectures across universal and split builds:
- `arm64-v8a` (64-bit ARM devices)
- `armeabi-v7a` (32-bit ARM devices)
- `x86_64` (64-bit Intel/AMD emulators & devices)
- `x86` (32-bit Intel emulators)

---

## Building from Source

### Prerequisites

- **JDK:** Java 17 (JDK 17) or higher
- **Android SDK:** Compile SDK 37, Target SDK 37, Min SDK 24 (Android 7.0+)
- **Build Tool:** Gradle Wrapper (included in repository)

### Build Commands

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/librespark-crypto/graviton.git
   cd graviton
   ```

2. **Build a Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   The generated APK will be available at:
   `app/build/outputs/apk/debug/app-debug.apk`

3. **Build a Release APK:**
   ```bash
   ./gradlew assembleRelease
   ```

4. **Run Unit Tests:**
   ```bash
   ./gradlew test
   ```

5. **Run Code Formatting & Linting:**
   ```bash
   ./gradlew ktlintCheck
   ```

---

## Architecture

Graviton follows modern Android development practices and a clean, modular architecture:

```
graviton/
├── app/                  # Application entry point, Hilt setup & top-level navigation
├── core/
│   ├── common/           # Core utilities, extensions, and dispatchers
│   ├── data/             # Repositories, Room Database & DataStore preferences
│   ├── media/            # Media3 ExoPlayer integration, decoder state & session management
│   ├── model/            # Shared data models and domain entities
│   └── ui/               # Reusable Material 3 Compose design system & components
└── feature/
    ├── player/           # Video playback controls, gestures, sheets & overlays
    ├── videopicker/      # Media library, folder browser, search & secure Vault
    ├── music/            # Integrated audio player screen & audio queue controls
    ├── playlist/         # Playlist management & playback queue state
    ├── network/          # Network stream dialogs & remote protocol support
    └── settings/         # Preferences screens (appearance, player, decoder, gestures)
```

### Technical Stack

- **Language:** 100% Kotlin
- **UI Framework:** Jetpack Compose with Material 3 & Navigation3
- **Media Engine:** AndroidX Media3 (ExoPlayer) & NextLib FFmpeg extensions
- **Dependency Injection:** Dagger Hilt
- **Persistence:** Room Database & AndroidX DataStore
- **Asynchronous Execution:** Kotlin Coroutines & StateFlow
- **Image Loading:** Coil 3

---

## Troubleshooting

- **Build Failure due to Java Version:** Ensure `JAVA_HOME` points to JDK 17. Verify your active JDK version with `java -version`.
- **Gradle Sync Issues:** Run `./gradlew clean` to reset build caches before running `./gradlew assembleDebug`.
- **Code Style Errors:** Run `./gradlew ktlintFormat` to fix formatting inconsistencies automatically.

---

## Contributing

Contributions are welcome! If you encounter a bug or have a feature request, please search existing issues or open a new issue on GitHub.

### Development Guidelines

1. Fork the repository and create a feature branch (`git checkout -b feature/amazing-feature`).
2. Make your changes and verify that all unit tests pass (`./gradlew test`).
3. Ensure code adheres to project formatting standards by running `./gradlew ktlintCheck`.
4. Open a concise Pull Request describing your changes and motivations.

---

## Credits & License

### Upstream Attribution

Graviton is derived from and builds upon the excellent work of [NextPlayer](https://github.com/anilbeesetti/NextPlayer) created by [Anil Beesetti](https://github.com/anilbeesetti) and its open-source contributors. We express our sincere gratitude to the upstream project and community.

### License

Graviton is free and open-source software distributed under the terms of the **GNU General Public License v3.0**. See the [LICENSE](LICENSE) file for details.

```text
Graviton - Android Video Player
Copyright (C) 2024-present Graviton Contributors

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

---

<p align="center">
  <sub>Built with ❤️ for Android • <a href="https://github.com/librespark-crypto/graviton/releases">Releases</a> • <a href="https://github.com/librespark-crypto/graviton/issues">Report Issue</a></sub>
</p>
