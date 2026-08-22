with open("feature/player/src/main/java/com/graviton/feature/player/PlayerContentFrame.kt", "r") as f:
    content = f.read()
import re
content = re.sub(r'SURFACE_TYPE_SURFACE_VIEW', 'SURFACE_TYPE_TEXTURE_VIEW', content)
content = re.sub(r'import androidx\.media3\.ui\.compose\.SURFACE_TYPE_SURFACE_VIEW', 'import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW', content)
with open("feature/player/src/main/java/com/graviton/feature/player/PlayerContentFrame.kt", "w") as f:
    f.write(content)
