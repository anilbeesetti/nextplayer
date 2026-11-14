package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPreferencesTest {
    @Test
    fun `old preferences default to video artwork and styles persist independently`() {
        val defaults = Json.decodeFromString<PlayerPreferences>("{}")
        assertEquals(
            ArtworkStyle.VIDEO,
            defaults.controlsArtworkStyle,
        )
        assertEquals(
            ArtworkStyle.VIDEO,
            defaults.queueArtworkStyle,
        )
        for (controls in ArtworkStyle.entries) {
            for (queue in ArtworkStyle.entries) {
                val preferences = defaults.copy(
                    controlsArtworkStyle = controls,
                    queueArtworkStyle = queue,
                )
                assertEquals(
                    preferences,
                    Json.decodeFromString<PlayerPreferences>(Json.encodeToString(preferences)),
                )
            }
        }
    }

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
}
