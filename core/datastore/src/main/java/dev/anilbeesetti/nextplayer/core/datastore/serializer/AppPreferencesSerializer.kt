package dev.anilbeesetti.nextplayer.core.datastore.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import dev.anilbeesetti.nextplayer.core.datastore.AppPreferences
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

object AppPreferencesSerializer : Serializer<AppPreferences> {
    override val defaultValue: AppPreferences
        get() = AppPreferences()

    override suspend fun readFrom(input: InputStream): AppPreferences {
        try {
            return Json.decodeFromString(
                deserializer = AppPreferences.serializer(),
                string = input.readBytes().decodeToString()
            )
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read datastore", exception)
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    override suspend fun writeTo(t: AppPreferences, output: OutputStream) {
        output.write(
            Json.encodeToString(
                serializer = AppPreferences.serializer(),
                value = t
            ).encodeToByteArray()
        )
    }
}
