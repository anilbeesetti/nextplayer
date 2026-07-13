package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.feature.player.createAudioPickerInput
import dev.anilbeesetti.nextplayer.feature.player.usePersistedReadableUri
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenDocumentAtInitialUriTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val contract = OpenDocumentAtInitialUri()

    @Test
    fun inputCreatesOpenDocumentIntentWithMimeTypesAndInitialUri() {
        val initialUri = Uri.parse("content://video/initial")

        val intent = contract.createIntent(
            context,
            OpenDocumentAtInitialUri.Input(arrayOf("audio/*"), initialUri),
        )

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertArrayEquals(arrayOf("audio/*"), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(
                initialUri,
                IntentCompat.getParcelableExtra(intent, DocumentsContract.EXTRA_INITIAL_URI, Uri::class.java),
            )
        } else {
            assertFalse(intent.hasExtra(DocumentsContract.EXTRA_INITIAL_URI))
        }
    }

    @Test
    fun audioPickerInputUsesAudioMimeTypeAndInitialUri() {
        val initialUri = Uri.parse("content://video/initial")

        val intent = contract.createIntent(context, createAudioPickerInput(initialUri))

        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertArrayEquals(arrayOf("audio/*"), intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertEquals(
                initialUri,
                IntentCompat.getParcelableExtra(intent, DocumentsContract.EXTRA_INITIAL_URI, Uri::class.java),
            )
        } else {
            assertFalse(intent.hasExtra(DocumentsContract.EXTRA_INITIAL_URI))
        }
    }

    @Test
    fun persistedReadableUriRunsPermissionCheckBeforeReadAndCommand() = runBlocking {
        val uri = Uri.parse("content://audio/readable")
        val calls = mutableListOf<String>()

        usePersistedReadableUri(
            uri = uri,
            takePersistableReadPermission = { calls += "persist:$it" },
            isReadable = {
                calls += "read:$it"
                true
            },
            onFailure = { calls += "failure" },
            onReadableUri = { calls += "command:$it" },
        )

        assertEquals(
            listOf("persist:$uri", "read:$uri", "command:$uri"),
            calls,
        )
    }

    @Test
    fun permissionFailureStopsBeforeReadAndCommand() = runBlocking {
        val calls = mutableListOf<String>()

        usePersistedReadableUri(
            uri = Uri.parse("content://audio/no-permission"),
            takePersistableReadPermission = {
                calls += "persist"
                throw SecurityException("denied")
            },
            isReadable = {
                calls += "read"
                true
            },
            onFailure = { calls += "failure:${it::class.simpleName}" },
            onReadableUri = { calls += "command" },
        )

        assertEquals(listOf("persist", "failure:SecurityException"), calls)
    }

    @Test
    fun unreadableUriStopsBeforeCommand() = runBlocking {
        val calls = mutableListOf<String>()

        usePersistedReadableUri(
            uri = Uri.parse("content://audio/unreadable"),
            takePersistableReadPermission = { calls += "persist" },
            isReadable = {
                calls += "read"
                false
            },
            onFailure = { calls += "failure:${it::class.simpleName}" },
            onReadableUri = { calls += "command" },
        )

        assertEquals(listOf("persist", "read", "failure:${IOException::class.simpleName}"), calls)
    }
}
