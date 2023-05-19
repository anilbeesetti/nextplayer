# Next Player

Next Player is an Android native video player written in Kotlin. It provides a simple and easy-to-use interface for users to play videos on their Android devices

**This project is still in development and is expected to have bugs. Please report any bugs you find in the [Issues](https://github.com/anilbeesetti/nextplayer/issues) section.**

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
- Audio/Subtitle track selection
- Vertical swipe to change brightness (left) / volume (right)
- Horizontal swipe to seek through video
- [Material 3 (You)](https://m3.material.io/) support
- Media picker with folder and file view

## Planned Features
- Zoom gesture
- Control playback speed
- External Subtitle support
- External Audio support
- Picture-in-picture mode

## License
Next Player is licensed under the GNU General Public License v3.0. See the [LICENSE](LICENSE) file for more information.
