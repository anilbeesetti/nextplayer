import re

with open("feature/player/src/main/java/com/graviton/feature/player/service/PlayerService.kt", "r") as f:
    content = f.read()

# Replace any remaining references to HARDWARE_PLUS
content = re.sub(r'DecoderMode\.HARDWARE_PLUS -> DefaultRenderersFactory\.EXTENSION_RENDERER_MODE_OFF\s*', '', content)

with open("feature/player/src/main/java/com/graviton/feature/player/service/PlayerService.kt", "w") as f:
    f.write(content)
