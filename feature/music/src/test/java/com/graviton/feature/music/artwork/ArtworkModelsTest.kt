package com.graviton.feature.music.artwork

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkModelsTest {

    @Test
    fun prefersEmbeddedBytesThenArtworkUriThenMediaUri() {
        val bytes = byteArrayOf(1, 2, 3)
        assertEquals(
            listOf(bytes, "content://album/1", "content://media/5"),
            artworkModels("content://album/1", "content://media/5", bytes),
        )
    }

    @Test
    fun skipsBlankAndDuplicateUris() {
        assertEquals(
            listOf("content://media/5"),
            artworkModels("  ", "content://media/5", byteArrayOf()),
        )
        assertEquals(
            listOf("content://same"),
            artworkModels("content://same", "content://same", null),
        )
    }
}
