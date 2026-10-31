package dev.anilbeesetti.nextplayer.settings.backup

import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupCodecTest {
    private val codec = SettingsBackupCodec(
        subtitleEncodings = setOf(
            "",
            "UTF-8",
            "UTF-16",
        ),
    )
    private val application = ApplicationPreferences(useHighContrastDarkTheme = true)
    private val player = PlayerPreferences(
        autoplay = false,
        defaultPlaybackSpeed = 1.5f,
    )

    @Test
    fun exportIncludesDefaultsAndOmitsDeprecatedAndSensitiveFields() {
        val root = Json.parseToJsonElement(
            codec.encode(
                application,
                player,
            ),
        ).jsonObject

        assertTrue(
            root.getValue(
                "applicationPreferences",
            ).jsonObject.getValue("useHighContrastDarkTheme").jsonPrimitive.boolean,
        )
        assertFalse(
            root.getValue(
                "playerPreferences",
            ).jsonObject.getValue("autoplay").jsonPrimitive.boolean,
        )
        assertTrue(root.getValue("playerPreferences").jsonObject.containsKey("subtitleTextSize"))
        assertFalse(root.getValue("playerPreferences").jsonObject.containsKey("useSwipeControls"))
        assertFalse(root.containsKey("networkConnections"))
        assertFalse(root.containsKey("sshKeys"))
    }

    @Test
    fun allSettingsRoundTrip() {
        val restored = decode(
            codec.encode(
                application,
                player,
            ),
        )

        assertEquals(
            application,
            restored.applicationPreferences,
        )
        assertEquals(
            player,
            restored.playerPreferences,
        )
    }

    @Test
    fun missingUnknownAndDeprecatedFieldsPreserveCurrentSettings() {
        val restored = decode(
            """{"version":999,"playerPreferences":{"autoplay":true,"futureSetting":42,"useSwipeControls":false}}""",
        )

        assertEquals(
            application,
            restored.applicationPreferences,
        )
        assertEquals(
            player.copy(autoplay = true),
            restored.playerPreferences,
        )
    }

    @Test
    fun invalidTypesEnumsAndListsDoNotDiscardValidSiblings() {
        val restored = decode(
            """{
                "applicationPreferences":{"themeConfig":"FUTURE","excludeFolders":["/Movies",1],"showSizeField":true},
                "playerPreferences":{"autoplay":"true","seekIncrement":null,"defaultPlaybackSpeed":"2.0","subtitleFont":false,"rememberSelections":false}
            }""",
        )

        assertEquals(
            application.copy(showSizeField = true),
            restored.applicationPreferences,
        )
        assertEquals(
            player.copy(rememberSelections = false),
            restored.playerPreferences,
        )
    }

    @Test
    fun outOfRangeValuesAndUnknownEncodingsAreIgnoredIndividually() {
        val restored = decode(
            """{
                "applicationPreferences":{"newVideoThresholdDays":-1,"thumbnailFramePosition":2},
                "playerPreferences":{"defaultPlaybackSpeed":100,"seekIncrement":0,"controllerAutoHideTimeout":-1,"subtitleTextSize":1000,"subtitleTextEncoding":"not-a-charset","autoPip":false}
            }""",
        )

        assertEquals(
            application,
            restored.applicationPreferences,
        )
        assertEquals(
            player.copy(autoPip = false),
            restored.playerPreferences,
        )
    }

    @Test
    fun numericFloatsAcceptIntegralJsonValues() {
        val restored = decode("""{"playerPreferences":{"defaultPlaybackSpeed":2}}""")

        assertEquals(
            2f,
            restored.playerPreferences.defaultPlaybackSpeed,
        )
    }

    @Test
    fun invalidSectionDoesNotDiscardOtherSections() {
        val restored = decode(
            """{"applicationPreferences":[],"playerPreferences":{"autoplay":true}}""",
        )

        assertEquals(
            application,
            restored.applicationPreferences,
        )
        assertTrue(restored.playerPreferences.autoplay)
    }

    @Test
    fun explicitlyIncludedNetworkCredentialsRoundTripWithoutPortableDatabaseIds() {
        val connection = NetworkConnection(
            id = 99,
            name = "NAS",
            protocol = NetworkProtocol.SMB,
            host = "nas.local",
            password = "secret",
        )
        val content = codec.encode(
            application,
            player,
            listOf(connection),
            mapOf("key.key" to "data"),
        )
        val restored = decode(content)

        assertFalse(
            Json.parseToJsonElement(
                content,
            ).jsonObject.getValue("networkConnections").toString().contains("\"id\""),
        )
        assertEquals(
            listOf(connection.copy(id = 0)),
            restored.networkConnections,
        )
        assertEquals(
            mapOf("key.key" to "data"),
            restored.sshKeys,
        )
    }

    @Test
    fun importingConnectionsPreservesLocalIdsAndInvalidOrMissingCredentials() {
        val existing = NetworkConnection(
            id = 8,
            name = "NAS",
            protocol = NetworkProtocol.SMB,
            host = "nas.local",
            username = "alice",
            password = "existing",
            port = 445,
        )
        val restored = codec.decode(
            codec.parse(
                """{"networkConnections":[{"id":123,"name":"NAS","protocol":"SMB","host":"nas.local","password":123,"port":-1,"path":"Media"}]}""",
            ),
            application,
            player,
            listOf(existing),
        )

        assertEquals(
            listOf(existing.copy(path = "Media")),
            restored.networkConnections,
        )
    }

    @Test
    fun invalidConnectionsAreSkippedWithoutDiscardingValidSettings() {
        val restored = decode(
            """{"playerPreferences":{"autoplay":true},"networkConnections":[{"name":"NAS","protocol":"FUTURE","host":"host"},{"name":"NAS","protocol":"SMB"},42],"sshKeys":{"invalid":42}}""",
        )

        assertTrue(restored.playerPreferences.autoplay)
        assertEquals(
            emptyList<NetworkConnection>(),
            restored.networkConnections,
        )
        assertTrue(restored.sshKeys.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unrelatedJsonIsRejected() {
        codec.parse("""{"other":"data"}""")
    }

    @Test
    fun duplicateConnectionEntriesMergeOverTheLatestValidFields() {
        val restored = decode(
            """{"networkConnections":[
                {"name":"NAS","host":"nas.local","protocol":"SFTP","username":"alice","privateKeyFileName":"replacement","privateKeyPassphrase":"replacement-passphrase"},
                {"name":"NAS","host":"nas.local","protocol":"SFTP","password":123}
            ]}""",
        )

        assertEquals(
            1,
            restored.networkConnections!!.size,
        )
        assertEquals(
            "replacement",
            restored.networkConnections.single().privateKeyFileName,
        )
        assertEquals(
            "replacement-passphrase",
            restored.networkConnections.single().privateKeyPassphrase,
        )
    }

    @Test
    fun differentPortsAtTheSameNamedHostRemainSeparateConnections() {
        val restored = decode(
            """{"networkConnections":[
                {"name":"NAS","host":"nas.local","protocol":"SFTP","port":22},
                {"name":"NAS","host":"nas.local","protocol":"SFTP","port":2222}
            ]}""",
        )

        assertEquals(
            listOf(
                22,
                2222,
            ),
            restored.networkConnections!!.map { it.port },
        )
    }

    @Test
    fun differentImportedPortsMatchTheirOwnExistingLocalIds() {
        val current = listOf(
            22,
            2222,
        ).mapIndexed { index, port ->
            NetworkConnection(
                id = index.toLong() + 8,
                name = "NAS",
                host = "nas.local",
                protocol = NetworkProtocol.SFTP,
                port = port,
            )
        }
        val restored = codec.decode(
            codec.parse(
                """{"networkConnections":[
                {"name":"NAS","host":"nas.local","protocol":"SFTP","port":22},
                {"name":"NAS","host":"nas.local","protocol":"SFTP","port":2222}
            ]}""",
            ),
            application,
            player,
            current,
        )

        assertEquals(
            listOf(
                8L,
                9L,
            ),
            restored.networkConnections!!.map { it.id },
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun malformedJsonIsRejected() {
        codec.parse("{broken")
    }

    private fun decode(content: String) = codec.decode(
        codec.parse(content),
        application,
        player,
        emptyList(),
    )
}
