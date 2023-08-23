# Next Player

Next Player is an Android native video player written in Kotlin. It provides a simple and easy-to-use interface for users to play videos on their Android devices

**This project is still in development and is expected to have bugs. Please report any bugs you find in the [Issues](https://github.com/anilbeesetti/nextplayer/issues) section.**

<a href='https://play.google.com/store/apps/details?id=dev.anilbeesetti.nextplayer'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height="80"/></a><a href='https://apt.izzysoft.de/fdroid/index/apk/dev.anilbeesetti.nextplayer'><img alt='Get it on IzzyOnDroid' src='https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png' height="80"/></a>

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
- **Audio**: Vorbis, Opus, FLAC, ALAC, PCM/WAVE (μ-law, A-law), MP1, MP2, MP3, AMR (NB, WB), AAC (LC, ELD, HE; xHE on Android 9+), AC-3, E-AC-3, DTS, DTS-HD, TrueHD
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
- Media picker with folder and file view
- Play videos from url
- Play videos from storage access framework (Android Document picker)
- Control playback speed
- External Subtitle support
- Zoom gesture

## Planned Features
- External Audio support
- Picture-in-picture mode

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
