import re

with open('/app/core/ui/src/main/java/com/graviton/core/ui/theme/Color.kt', 'r') as f:
    content = f.read()

imports = """
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
"""

# add imports right after package declaration
content = content.replace("package com.graviton.core.ui.theme", f"package com.graviton.core.ui.theme\n{imports}")

with open('/app/core/ui/src/main/java/com/graviton/core/ui/theme/Color.kt', 'w') as f:
    f.write(content)
