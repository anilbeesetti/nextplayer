package dev.anilbeesetti.nextplayer.core.datastore.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import dev.anilbeesetti.nextplayer.core.model.VaultPreferences
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object VaultPreferencesSerializer : Serializer<VaultPreferences> {

    private val jsonFormat = Json { ignoreUnknownKeys = true }

    override val defaultValue: VaultPreferences
        get() = VaultPreferences()

    override suspend fun readFrom(input: InputStream): VaultPreferences {
        try {
            return jsonFormat.decodeFromString(
                deserializer = VaultPreferences.serializer(),
                string = input.readBytes().decodeToString(),
            )
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read vault preferences datastore", exception)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun writeTo(t: VaultPreferences, output: OutputStream) {
        output.write(
            jsonFormat.encodeToString(
                serializer = VaultPreferences.serializer(),
                value = t,
            ).encodeToByteArray(),
        )
    }
}
