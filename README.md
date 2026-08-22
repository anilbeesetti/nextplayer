# Orbiton

[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/anilbeesetti/graviton.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/graviton/graviton/releases/latest)

Orbiton is a native Android video player written in Kotlin. Built with Jetpack Compose and Media3/ExoPlayer, it offers a robust, modern, and highly customizable media playback experience.

**This project is currently in development. Please report any bugs or feature requests in the Issues section.**

## Overview

Orbiton is designed to deliver a premium, distraction-free video playback experience for Android users. Whether you're watching local files, network streams, or customized playlists, Orbiton provides the tools you need for optimal viewing. It leverages hardware decoding for efficiency and offers deep integration with modern Android features.

## Key Features

- 📁 **Extensive Format Support:** Plays most popular video and audio formats (MP4, MKV, WebM, TS, MP3, FLAC, and more).
- 🚀 **Hardware Acceleration:** Efficient playback using HW and HW+ decoding modes, falling back to SW when necessary.
- 🎛️ **Advanced Controls:** Playback speed adjustment, skip silence, A-B repeat, and fine-grained subtitle synchronization.
- 👆 **Intuitive Gestures:** Swipe to control brightness and volume, and double-tap to seek.
- 🎨 **Modern UI:** A clean, minimal interface built entirely with Material 3 and Jetpack Compose.
- 📺 **Picture-in-Picture (PiP):** Seamless background and floating window playback on supported devices.
- 🌐 **Network Playback:** Support for various streaming protocols and network shares.

## Architecture

Orbiton is built using modern Android development practices:
- **Language:** 100% Kotlin
- **UI Toolkit:** Jetpack Compose (Material 3)
- **Media Engine:** AndroidX Media3 (ExoPlayer)
- **Architecture:** MVVM, Coroutines & Flow, Hilt for Dependency Injection
- **Modularization:** Multi-module architecture separating core features (player, browser, settings) for better maintainability.

## Requirements

- **Minimum SDK:** Android 8.0 (API level 26)
- **Target SDK:** Android 14+ (API level 34+)
- **JDK:** 17 or higher
- **IDE:** Android Studio Hedgehog (2023.1.1) or newer

## Building from Source

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/orbiton.git
   cd orbiton
   ```

2. **Build a Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   The APK will be available in `app/build/outputs/apk/debug/app-debug.apk`.

3. **Run Tests:**
   ```bash
   ./gradlew test
   ./gradlew connectedDebugAndroidTest
   ```

## Contributing

We welcome contributions! Please feel free to submit pull requests, open issues, or suggest new features. Ensure you run `./gradlew ktlintCheck` before committing to maintain code style.

## License

This project is licensed under the GNU General Public License v3.0. See the [LICENSE](LICENSE) file for more details.
