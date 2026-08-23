import re

with open('/app/app/build.gradle.kts', 'r') as f:
    content = f.read()

deps_to_add = "    implementation(project(\":feature:music\"))\n"

if "implementation(project(\":feature:videopicker\"))" in content:
    content = content.replace("implementation(project(\":feature:videopicker\"))", "implementation(project(\":feature:videopicker\"))\n" + deps_to_add)

with open('/app/app/build.gradle.kts', 'w') as f:
    f.write(content)
