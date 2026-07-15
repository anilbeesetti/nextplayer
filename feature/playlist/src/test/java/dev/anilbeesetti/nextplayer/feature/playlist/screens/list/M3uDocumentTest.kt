package dev.anilbeesetti.nextplayer.feature.playlist.screens.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uDocumentTest {
    @Test
    fun failedPersistablePermissionDoesNotCreateDocument() {
        val document = persistM3uDocument(
            uri = "content://documents/news.m3u",
            fallbackDisplayName = "news.m3u",
            persistPermission = { throw SecurityException("Permission denied") },
            queryDisplayName = { "News.m3u" },
        )

        assertNull(document)
    }

    @Test
    fun persistedDocumentUsesFallbackWhenDisplayNameQueryFails() {
        val document = persistM3uDocument(
            uri = "content://documents/news.m3u",
            fallbackDisplayName = "news.m3u",
            persistPermission = {},
            queryDisplayName = { error("Provider failure") },
        )

        assertEquals(M3uDocument("content://documents/news.m3u", "news.m3u"), document)
    }
}
