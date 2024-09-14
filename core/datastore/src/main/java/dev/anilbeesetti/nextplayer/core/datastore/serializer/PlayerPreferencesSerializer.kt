package dev.anilbeesetti.nextplayer.core.datastore.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object PlayerPreferencesSerializer : Serializer<PlayerPreferences> {

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    override val defaultValue: PlayerPreferences
        get() = PlayerPreferences()

    override suspend fun readFrom(input: InputStream): PlayerPreferences {
        try {
            return jsonFormat.decodeFromString(
                deserializer = PlayerPreferences.serializer(),
                string = input.readBytes().decodeToString(),
            )
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read datastore", exception)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun writeTo(t: PlayerPreferences, output: OutputStream) {
        output.write(
            jsonFormat.encodeToString(
                serializer = PlayerPreferences.serializer(),
                value = t,
            ).encodeToByteArray(),
        )
    }
}
