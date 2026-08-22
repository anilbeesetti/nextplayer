import re

with open("feature/player/src/main/java/com/graviton/feature/player/service/PlayerService.kt", "r") as f:
    content = f.read()

content = re.sub(r'if\s*\(playerPreferences\.decoderMode\s*==\s*DecoderMode\.HARDWARE_PLUS\)\s*\{\s*setMediaCodecSelector\(.*?\)\s*\}', '', content)

with open("feature/player/src/main/java/com/graviton/feature/player/service/PlayerService.kt", "w") as f:
    f.write(content)
