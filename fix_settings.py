import re

with open("feature/settings/src/main/java/com/graviton/settings/extensions/DecoderMode.kt", "r") as f:
    content = f.read()

content = re.sub(r'DecoderMode\.HARDWARE_PLUS -> R\.string\.hardware_plus\s*', '', content)

with open("feature/settings/src/main/java/com/graviton/settings/extensions/DecoderMode.kt", "w") as f:
    f.write(content)
