import re

with open("feature/player/src/main/java/com/graviton/feature/player/service/PlayerService.kt", "r") as f:
    content = f.read()

content = re.sub(r'DecoderMode\.HARDWARE_PLUS -> DefaultRenderersFactory\.EXTENSION_RENDERER_MODE_OFF\s*', '', content)
content = re.sub(r'if \(playerPreferences\.decoderMode == DecoderMode\.HARDWARE_PLUS\) \{\s*\.setEnableDecoderFallback\(true\)\s*\}\s*', '', content)

with open("feature/player/src/main/java/com/graviton/feature/player/service/PlayerService.kt", "w") as f:
    f.write(content)
