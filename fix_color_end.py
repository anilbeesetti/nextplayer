import re

with open('/app/core/ui/src/main/java/com/graviton/core/ui/theme/Color.kt', 'r') as f:
    content = f.read()

content = content.replace("import androidx.compose.material3.darkColorScheme", "")
content = content.replace("import androidx.compose.material3.lightColorScheme", "")
content = content.replace("import androidx.compose.ui.graphics.Color", "")

with open('/app/core/ui/src/main/java/com/graviton/core/ui/theme/Color.kt', 'w') as f:
    f.write(content)
