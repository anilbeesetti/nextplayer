package dev.anilbeesetti.nextplayer.settings.backup

import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.SerialKind
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer

internal data class ImportedSettings(
    val applicationPreferences: ApplicationPreferences,
    val playerPreferences: PlayerPreferences,
    val networkConnections: List<NetworkConnection>? = null,
    val sshKeys: Map<String, String> = emptyMap(),
)

@OptIn(ExperimentalSerializationApi::class)
internal class SettingsBackupCodec(private val subtitleEncodings: Set<String>) {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(
        applicationPreferences: ApplicationPreferences,
        playerPreferences: PlayerPreferences,
        networkConnections: List<NetworkConnection>? = null,
        sshKeys: Map<String, String> = emptyMap(),
    ): String = buildJsonObject {
        put(
            "version",
            1,
        )
        put(
            "applicationPreferences",
            json.encodeToJsonElement(applicationPreferences),
        )
        put(
            "playerPreferences",
            JsonObject(json.encodeToJsonElement(playerPreferences).jsonObject - "useSwipeControls"),
        )
        if (networkConnections != null) {
            put(
                "networkConnections",
                JsonArray(
                    networkConnections.map { connection ->
                        JsonObject(json.encodeToJsonElement(connection).jsonObject - "id")
                    },
                ),
            )
            put(
                "sshKeys",
                json.encodeToJsonElement(sshKeys),
            )
        }
    }.let {
        json.encodeToString(
            JsonObject.serializer(),
            it,
        )
    }

    fun parse(content: String): JsonObject {
        val root = json.parseToJsonElement(content) as? JsonObject
            ?: throw IllegalArgumentException("Settings must be a JSON object")
        require(
            root["applicationPreferences"] is JsonObject ||
                root["playerPreferences"] is JsonObject ||
                root["networkConnections"] is JsonArray,
        ) { "No supported settings found" }
        return root
    }

    fun decode(
        content: JsonObject,
        applicationPreferences: ApplicationPreferences,
        playerPreferences: PlayerPreferences,
        networkConnections: List<NetworkConnection>,
    ): ImportedSettings = ImportedSettings(
        applicationPreferences = merge(
            applicationPreferences,
            content["applicationPreferences"],
        ),
        playerPreferences = merge(
            playerPreferences,
            content["playerPreferences"],
        ),
        networkConnections = mergeConnections(
            content["networkConnections"],
            networkConnections,
        ),
        sshKeys = (content["sshKeys"] as? JsonObject).orEmpty().mapNotNull { (name, value) ->
            (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.let { name to it }
        }.toMap(),
    )

    private fun mergeConnections(
        fields: JsonElement?,
        current: List<NetworkConnection>,
    ): List<NetworkConnection>? {
        if (fields !is JsonArray) return null
        val connections = current.toMutableList()
        val updated = linkedSetOf<Int>()
        fields.forEach { element ->
            val entry = element as? JsonObject ?: return@forEach
            val name = entry.string("name")?.takeIf(String::isNotBlank) ?: return@forEach
            val host = entry.string("host")?.takeIf(String::isNotBlank) ?: return@forEach
            val protocol = entry.string("protocol")?.let { value ->
                NetworkProtocol.entries.firstOrNull { it.name == value }
            } ?: return@forEach
            val candidates = connections.indices.filter { index ->
                connections[index].let {
                    it.name == name && it.host == host &&
                        it.protocol == protocol
                }
            }
            val port = if (entry["port"] == JsonNull) {
                protocol.defaultPort
            } else {
                (entry["port"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
                    ?.takeIf { it in 1..65535 }
            }
            val path = entry.string("path")
            val username = entry.string("username")
            val matching = candidates.filter { index ->
                val connection = connections[index]
                (port == null || connection.effectivePort == port) &&
                    (path == null || connection.path == path) &&
                    (username == null || connection.username == username)
            }
            if (matching.size > 1) return@forEach
            val index = matching.singleOrNull() ?: candidates.singleOrNull()?.takeIf {
                it < current.size && it !in updated
            }
            val base = index?.let(connections::get) ?: NetworkConnection(
                name = name,
                host = host,
                protocol = protocol,
            )
            val connection = merge(
                base,
                JsonObject(entry - "id"),
            )
            if (index == null) {
                updated.add(connections.size)
                connections.add(connection)
            } else {
                updated.add(index)
                connections[index] = connection
            }
        }
        return updated.map(connections::get)
    }

    private inline fun <reified T> merge(current: T, fields: JsonElement?): T {
        if (fields !is JsonObject) return current
        val serializer = serializer<T>()
        var result = current
        fields.forEach { (name, value) ->
            val index = serializer.descriptor.getElementIndex(name)
            if (index < 0 || name == "useSwipeControls") return@forEach
            if (!isValidValue(
                    value,
                    serializer.descriptor.getElementDescriptor(index),
                ) ||
                !isValidSetting(
                    name,
                    value,
                )
            ) {
                return@forEach
            }
            val updated = json.encodeToJsonElement(
                serializer,
                result,
            ).jsonObject.toMutableMap()
            updated[name] = value
            try {
                result = json.decodeFromJsonElement(
                    serializer,
                    JsonObject(updated),
                )
            } catch (_: SerializationException) {
                // A single unsupported field must not discard the other imported preferences.
            }
        }
        return result
    }

    private fun isValidValue(value: JsonElement, descriptor: SerialDescriptor): Boolean {
        if (value == JsonNull) return descriptor.isNullable
        val primitive = value as? JsonPrimitive
        return when (descriptor.kind) {
            PrimitiveKind.BOOLEAN -> primitive?.let { !it.isString && it.booleanOrNull != null } ==
                true

            PrimitiveKind.STRING, PrimitiveKind.CHAR -> primitive?.isString == true

            PrimitiveKind.BYTE, PrimitiveKind.SHORT, PrimitiveKind.INT, PrimitiveKind.LONG ->
                primitive?.let { !it.isString && it.longOrNull != null } == true

            PrimitiveKind.FLOAT, PrimitiveKind.DOUBLE -> primitive?.let {
                !it.isString && it.doubleOrNull?.isFinite() == true &&
                    (
                        descriptor.kind != PrimitiveKind.FLOAT ||
                            it.doubleOrNull!!.toFloat().isFinite()
                        )
            } == true

            SerialKind.ENUM -> primitive?.let {
                it.isString && descriptor.getElementIndex(it.content) >= 0
            } == true

            StructureKind.LIST ->
                value is JsonArray &&
                    value.all {
                        isValidValue(
                            it,
                            descriptor.getElementDescriptor(0),
                        )
                    }

            else -> false
        }
    }

    private fun isValidSetting(name: String, value: JsonElement): Boolean {
        val number = (value as? JsonPrimitive)?.doubleOrNull
        return when (name) {
            "playerBrightness", "thumbnailFramePosition" -> number != null && number in 0.0..1.0

            "defaultPlaybackSpeed", "longPressControlsSpeed" -> number != null && number in 0.2..4.0

            "seekSensitivity", "volumeGestureSensitivity", "brightnessGestureSensitivity" ->
                number != null && number in 0.1..2.0

            "seekIncrement" -> number != null && number in 1.0..60.0

            "controllerAutoHideTimeout" -> number != null && number in 0.0..600.0

            "subtitleTextSize" -> number != null && number in 10.0..60.0

            "newVideoThresholdDays" -> number != null && number in 0.0..30.0

            "minDurationForFastSeek" -> number != null && number >= 0

            "port" -> value == JsonNull || (number != null && number in 1.0..65535.0)

            "subtitleTextEncoding" -> (value as? JsonPrimitive)?.content in subtitleEncodings

            else -> true
        }
    }

    private fun JsonObject.string(name: String): String? =
        (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content
}
