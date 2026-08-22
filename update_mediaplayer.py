import re

with open("feature/player/src/main/java/com/graviton/feature/player/MediaPlayerScreen.kt", "r") as f:
    content = f.read()

# The current code in MediaPlayerScreen.kt for long press overlay is around line 321
# It shows a top center text "Fast playback speed (2.0x)". Let's remove that, because mpvRex shows the overlay in the center and we want to use SpeedOverlayView. Wait, SpeedOverlayView is already used at the end!
# SpeedOverlayView only shows temporarily.
# The prompt says: "Long-press/fast-forward gesture activates temporary 2× playback. Display a compact overlay indicating '2×'. The overlay should have the same visual style/position/animation behavior as the reference implementation. Playback becomes 2× while the gesture is active. When the gesture ends, restore the previous playback speed."
# mpvRex shows an overlay in the center with "2x >>" or similar.
