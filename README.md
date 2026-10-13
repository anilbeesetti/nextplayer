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

[![Build Status](https://github.com/librespark-crypto/graviton/actions/workflows/build.yml/badge.svg)](https://github.com/librespark-crypto/graviton/actions/workflows/build.yml)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/librespark-crypto/graviton.svg)](https://github.com/librespark-crypto/graviton/releases/latest)

> Graviton is a fork of Next Player focused on true hardware decoding and Glass UI.

## Screenshots

![Home](docs/screenshots/home.png)
![Player](docs/screenshots/player.png)
![Settings](docs/screenshots/settings.png)

## What changed vs Next Player
- True HW-only mode (no silent SW fallback)
- Glass UI blur on Android 12+
- Hold-to-speed with horizontal slide (like mpv)
- 12 Material 3 themes + diagnostics overlay
- New G-orbit icon

## Decoder Modes
| Mode | Behavior |
| --- | --- |
| Auto | HW+ -> HW -> SW |
| HW | HW only, error if fails |
| HW+ | HW+ only |
| SW | Software only |

## Architecture
Multi-module: app, core:ui, core:model, core:datastore, feature:player, etc.

## Build
```bash
./gradlew assembleDebug
```

## Credits
Original Next Player by Anil Singh. Licensed GPL-3.0.
