import re

with open('/app/feature/music/build.gradle.kts', 'r') as f:
    content = f.read()

content = content.replace("""    kotlinOptions {
        jvmTarget = "17"
    }""", "")

with open('/app/feature/music/build.gradle.kts', 'w') as f:
    f.write(content)
