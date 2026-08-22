import re

with open("core/model/src/main/java/com/graviton/core/model/DecoderMode.kt", "w") as f:
    f.write('''package com.graviton.core.model

enum class DecoderMode {
    AUTO,
    HARDWARE,
    SOFTWARE,
}
''')
