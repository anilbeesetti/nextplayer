# Graviton

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
