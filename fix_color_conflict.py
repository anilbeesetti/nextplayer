import re

with open('/app/core/ui/src/main/java/com/graviton/core/ui/theme/Color.kt', 'r') as f:
    content = f.read()

# The dummy code was added but maybe it was already there?
# Let's remove the dummy ones we added at the end.
search_str = """val oceanLightScheme = lightColorScheme()
val oceanDarkScheme = darkColorScheme()
val blueLightScheme = lightColorScheme()
val blueDarkScheme = darkColorScheme()
val purpleLightScheme = lightColorScheme()
val purpleDarkScheme = darkColorScheme()
val greenLightScheme = lightColorScheme()
val greenDarkScheme = darkColorScheme()
val redLightScheme = lightColorScheme()
val redDarkScheme = darkColorScheme()
val orangeLightScheme = lightColorScheme()
val orangeDarkScheme = darkColorScheme()
val pinkLightScheme = lightColorScheme()
val pinkDarkScheme = darkColorScheme()
val cyanLightScheme = lightColorScheme()
val cyanDarkScheme = darkColorScheme()
val monochromeLightScheme = lightColorScheme()
val monochromeDarkScheme = darkColorScheme()
val graphiteLightScheme = lightColorScheme()
val graphiteDarkScheme = darkColorScheme()"""

content = content.replace(search_str, "")

with open('/app/core/ui/src/main/java/com/graviton/core/ui/theme/Color.kt', 'w') as f:
    f.write(content)
