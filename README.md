![Next player banner](fastlane/metadata/android/en-US/images/featureGraphic.png)

# Next Player

[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/anilbeesetti/nextplayer.svg?logo=github&label=GitHub&cacheSeconds=3600)](https://github.com/anilbeesetti/nextplayer/releases/latest)
[![Google Play](https://img.shields.io/endpoint?color=green&logo=google-play&url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Ddev.anilbeesetti.nextplayer%26l%3DGoogle%2520Play%26m%3Dv%24version)](https://play.google.com/store/apps/details?id=dev.anilbeesetti.nextplayer)
[![IzzyOnDroid](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/dev.anilbeesetti.nextplayer)](https://apt.izzysoft.de/fdroid/index/apk/dev.anilbeesetti.nextplayer)
[![F-Droid](https://img.shields.io/f-droid/v/dev.anilbeesetti.nextplayer?logo=f-droid&label=F-Droid&cacheSeconds=3600)](https://f-droid.org/packages/dev.anilbeesetti.nextplayer)
[![GitHub all releases](https://img.shields.io/github/downloads/anilbeesetti/nextplayer/total?logo=github&cacheSeconds=3600)](https://github.com/anilbeesetti/nextplayer/releases/latest)
[![Google Play](https://img.shields.io/endpoint?color=green&logo=google-play&url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Ddev.anilbeesetti.nextplayer%26l%3Ddownloads%26m%3D%24totalinstalls)](https://play.google.com/store/apps/details?id=dev.anilbeesetti.nextplayer)
[![Weblate project translated](https://img.shields.io/weblate/progress/next-player?logo=weblate&logoColor=white&cacheSeconds=36000)](https://hosted.weblate.org/engage/next-player/)

Next Player is an Android native video player written in Kotlin. It provides a simple and easy-to-use interface for users to play videos on their
Android devices

**This project is still in development and is expected to have bugs. Please report any bugs you find in
the [Issues](https://github.com/anilbeesetti/nextplayer/issues) section.**

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" alt="Get it on Google Play" height="80"/>](https://play.google.com/store/apps/details?id=dev.anilbeesetti.nextplayer)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/dev.anilbeesetti.nextplayer)
[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/dev.anilbeesetti.nextplayer/)

## Screenshots

### Media Picker

<div style="width:100%; display:flex; justify-content:space-between;">

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width=19% alt="Home Light">](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width=19% alt="Home Dark">](fastlane/metadata/android/en-US/images/phoneScreenshots/2.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width=19% alt="Sub Folder Light">](fastlane/metadata/android/en-US/images/phoneScreenshots/3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width=19% alt="Sub Folder Dark">](fastlane/metadata/android/en-US/images/phoneScreenshots/4.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width=19% alt="Quick Settings">](fastlane/metadata/android/en-US/images/phoneScreenshots/5.png)
</div>

### Player Ui

<div style="width:100%; display:flex; justify-content:space-between;">

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width=49% alt="Player">](fastlane/metadata/android/en-US/images/phoneScreenshots/6.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width=49% alt="Player">](fastlane/metadata/android/en-US/images/phoneScreenshots/7.png)
</div>

## Supported formats

- **Video**: H.263, H.264 AVC (Baseline Profile; Main Profile on Android 6+), H.265 HEVC, MPEG-4 SP, VP8, VP9, AV1
    - Support depends on Android device
- **Audio**: Vorbis, Opus, FLAC, ALAC, PCM/WAVE (μ-law, A-law), MP1, MP2, MP3, AMR (NB, WB), AAC (LC, ELD, HE; xHE on Android 9+), AC-3, E-AC-3, DTS,
  DTS-HD, TrueHD
    - Support provided by ExoPlayer FFmpeg extension
- **Subtitles**: SRT, SSA, ASS, TTML, VTT, DVB
    - SSA/ASS has limited styling support see [this issue](https://github.com/google/ExoPlayer/issues/8435)

## Features

- Native Android app with simple and easy-to-use interface
- Completely free and open source and without any ads or excessive permissions
- Software decoders for h264 and hevc
- Audio/Subtitle track selection
- Vertical swipe to change brightness (left) / volume (right)
- Horizontal swipe to seek through video
- [Material 3 (You)](https://m3.material.io/) support
- Media picker with tree, folder and file view modes
- Play videos from url
- Play videos from storage access framework (Android Document picker)
- Control playback speed
- External Subtitle support
- Zoom gesture
- Picture-in-picture mode

## Planned Features

- External Audio support
- Background playback
- Android TV version
- Search Functionality

## Contributing

Contributions are welcome!

### Translating

You can help translate Next Player on [Hosted Weblate](https://hosted.weblate.org/engage/next-player/).

[![Translate status](https://hosted.weblate.org/widgets/next-player/-/multi-auto.svg)](https://hosted.weblate.org/engage/next-player/)

## Credits

### Open Source Projects

- [Findroid](https://github.com/jarnedemeulemeester/findroid)
- [Just (Video) Player](https://github.com/moneytoo/Player)
- [LibreTube](https://github.com/libre-tube/LibreTube)
- [ReadYou](https://github.com/Ashinch/ReadYou)
- [Seal](https://github.com/JunkFood02/Seal)
- ...

### Special Thanks

[<img src="https://hosted.weblate.org/widgets/next-player/-/287x66-white.png"  width="200"/>](https://hosted.weblate.org/engage/next-player/)

Thanks to **Weblate** for providing free hosting for the project.

## License

Next Player is licensed under the GNU General Public License v3.0. See the [LICENSE](LICENSE) file for more information.
