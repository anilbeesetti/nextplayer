package com.graviton.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.graviton.settings.Setting
import com.graviton.settings.navigation.aboutPreferencesEntry
import com.graviton.settings.navigation.appearancePreferencesEntry
import com.graviton.settings.navigation.audioPreferencesEntry
import com.graviton.settings.navigation.decoderPreferencesEntry
import com.graviton.settings.navigation.folderPreferencesEntry
import com.graviton.settings.navigation.generalPreferencesEntry
import com.graviton.settings.navigation.gesturePreferencesEntry
import com.graviton.settings.navigation.librariesEntry
import com.graviton.settings.navigation.mediaLibraryPreferencesEntry
import com.graviton.settings.navigation.navigateToAboutPreferences
import com.graviton.settings.navigation.navigateToAppearancePreferences
import com.graviton.settings.navigation.navigateToAudioPreferences
import com.graviton.settings.navigation.navigateToDecoderPreferences
import com.graviton.settings.navigation.navigateToFolderPreferencesScreen
import com.graviton.settings.navigation.navigateToGeneralPreferences
import com.graviton.settings.navigation.navigateToGesturePreferences
import com.graviton.settings.navigation.navigateToLibraries
import com.graviton.settings.navigation.navigateToMediaLibraryPreferencesScreen
import com.graviton.settings.navigation.navigateToPlayerPreferences
import com.graviton.settings.navigation.navigateToSubtitlePreferences
import com.graviton.settings.navigation.navigateToThumbnailPreferencesScreen
import com.graviton.settings.navigation.playerPreferencesEntry
import com.graviton.settings.navigation.settingsEntry
import com.graviton.settings.navigation.subtitlePreferencesEntry
import com.graviton.settings.navigation.thumbnailPreferencesEntry

fun EntryProviderScope<NavKey>.settingsNavGraph(
    backStack: NavBackStack<NavKey>,
) {
    settingsEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
        onItemClick = { setting ->
            when (setting) {
                Setting.APPEARANCE -> backStack.navigateToAppearancePreferences()
                Setting.MEDIA_LIBRARY -> backStack.navigateToMediaLibraryPreferencesScreen()
                Setting.PLAYER -> backStack.navigateToPlayerPreferences()
                Setting.GESTURES -> backStack.navigateToGesturePreferences()
                Setting.DECODER -> backStack.navigateToDecoderPreferences()
                Setting.AUDIO -> backStack.navigateToAudioPreferences()
                Setting.SUBTITLE -> backStack.navigateToSubtitlePreferences()
                Setting.GENERAL -> backStack.navigateToGeneralPreferences()
                Setting.ABOUT -> backStack.navigateToAboutPreferences()
            }
        },
    )
    appearancePreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    mediaLibraryPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
        onFolderSettingClick = backStack::navigateToFolderPreferencesScreen,
        onThumbnailSettingClick = backStack::navigateToThumbnailPreferencesScreen,
    )
    thumbnailPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    folderPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    playerPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    gesturePreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    decoderPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    audioPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    subtitlePreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    generalPreferencesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    aboutPreferencesEntry(
        onLibrariesClick = backStack::navigateToLibraries,
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
    librariesEntry(
        onNavigateUp = { backStack.removeLastIfNotRoot() },
    )
}
