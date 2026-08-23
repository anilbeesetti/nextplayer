import re

with open('/app/app/src/main/java/com/graviton/navigation/TopLevelNavigation.kt', 'r') as f:
    content = f.read()

# Add MusicRoute placeholder to TopLevelNavigation if not added correctly
# It looks like it is already added but lets verify the imports
if 'import com.graviton.feature.music.MusicHomeScreen' not in content:
    # Not using it directly here, but let's check MainActivity
    pass

with open('/app/app/src/main/java/com/graviton/MainActivity.kt', 'r') as f:
    main_content = f.read()

# Insert feature:music composable integration
if 'com.graviton.feature.music.MusicHomeScreen' not in main_content:
    search_str = """                        entry<com.graviton.navigation.MusicRoute> {
                            androidx.compose.material3.Text(
                                "Music Integration Scaffold",
                                modifier = androidx.compose.ui.Modifier
                            )
                        }"""
    replace_str = """                        entry<com.graviton.navigation.MusicRoute> {
                            com.graviton.feature.music.MusicHomeScreen()
                        }"""
    main_content = main_content.replace(search_str, replace_str)

    with open('/app/app/src/main/java/com/graviton/MainActivity.kt', 'w') as f:
        f.write(main_content)
