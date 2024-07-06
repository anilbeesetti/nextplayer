package dev.anilbeesetti.nextplayer.core.datastore.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object ApplicationPreferencesSerializer : Serializer<ApplicationPreferences> {

    private val jsonFormat = Json { ignoreUnknownKeys = true }
    override val defaultValue: ApplicationPreferences
        get() = ApplicationPreferences()

    override suspend fun readFrom(input: InputStream): ApplicationPreferences {
        try {
            return jsonFormat.decodeFromString(
                deserializer = ApplicationPreferences.serializer(),
                string = input.readBytes().decodeToString(),
            )
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read datastore", exception)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun writeTo(t: ApplicationPreferences, output: OutputStream) {
        output.write(
            jsonFormat.encodeToString(
                serializer = ApplicationPreferences.serializer(),
                value = t,
            ).encodeToByteArray(),
        )
    }
}
