package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPreferencesTest {
    @Test
    fun `existing preferences keep tunneled playback disabled`() {
        val preferences = Json.decodeFromString<PlayerPreferences>(
            string = """{"autoplay":false,"preferredAudioLanguage":"en"}""",
        )

        assertFalse(preferences.enableTunneledPlayback)
        assertFalse(preferences.autoplay)
        assertEquals(
            "en",
            preferences.preferredAudioLanguage,
        )
    }

    @Test
    fun `tunneled playback survives serialization`() {
        val preferences = PlayerPreferences(enableTunneledPlayback = true)
        val restored = Json.decodeFromString<PlayerPreferences>(
            Json.encodeToString(value = preferences),
        )

        assertTrue(restored.enableTunneledPlayback)
        assertEquals(
            preferences,
            restored,
        )
    }

    @Test
    fun `existing preferences keep the status bar visible`() {
        val preferences = Json.decodeFromString<PlayerPreferences>(
            string = """{"autoplay":false}""",
        )

        assertFalse(preferences.hideStatusBar)
    }

    @Test
    fun `hide status bar survives serialization`() {
        val preferences = PlayerPreferences(hideStatusBar = true)
        val restored = Json.decodeFromString<PlayerPreferences>(
            Json.encodeToString(value = preferences),
        )

        assertTrue(restored.hideStatusBar)
        assertEquals(
            preferences,
            restored,
        )
    }
}
