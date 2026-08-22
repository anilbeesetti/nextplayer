import re

with open("feature/player/src/main/java/com/graviton/feature/player/ui/DecoderSelectorView.kt", "r") as f:
    content = f.read()

# Replace HARDWARE_PLUS
content = re.sub(r'\s*DecoderMode\.HARDWARE_PLUS -> "HW\+"\s*', '', content)

with open("feature/player/src/main/java/com/graviton/feature/player/ui/DecoderSelectorView.kt", "w") as f:
    f.write(content)
