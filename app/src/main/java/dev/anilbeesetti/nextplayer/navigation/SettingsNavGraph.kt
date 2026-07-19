package dev.anilbeesetti.nextplayer.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.settings.Setting
import dev.anilbeesetti.nextplayer.settings.navigation.aboutPreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.appearancePreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.audioPreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.decoderPreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.folderPreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.generalPreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.gesturePreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.librariesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.mediaLibraryPreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAboutPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAppearancePreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToAudioPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToDecoderPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToFolderPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToGeneralPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToGesturePreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToLibraries
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToMediaLibraryPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToPlayerPreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToSubtitlePreferences
import dev.anilbeesetti.nextplayer.settings.navigation.navigateToThumbnailPreferencesScreen
import dev.anilbeesetti.nextplayer.settings.navigation.playerPreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.settingsEntry
import dev.anilbeesetti.nextplayer.settings.navigation.subtitlePreferencesEntry
import dev.anilbeesetti.nextplayer.settings.navigation.thumbnailPreferencesEntry

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
