package dev.anilbeesetti.nextplayer.feature.player

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutedMetadataBoundsTest {
    @Test
    fun titleBesideTheGrowingArtworkStaysInsideTheSheet() {
        val compactArtwork = Rect(
            40f,
            2000f,
            160f,
            2120f,
        )
        val compactMetadata = Rect(
            190f,
            2010f,
            800f,
            2110f,
        )
        val expandedArtwork = Rect(
            64f,
            300f,
            1016f,
            1252f,
        )
        val expandedMetadata = Rect(
            64f,
            1320f,
            1016f,
            1440f,
        )
        for (step in 0..20) {
            val progress = step / 20f
            val artwork = Rect(
                lerp(
                    compactArtwork.left,
                    expandedArtwork.left,
                    progress,
                ),
                lerp(
                    compactArtwork.top,
                    expandedArtwork.top,
                    progress,
                ),
                lerp(
                    compactArtwork.right,
                    expandedArtwork.right,
                    progress,
                ),
                lerp(
                    compactArtwork.bottom,
                    expandedArtwork.bottom,
                    progress,
                ),
            )
            val title = routedMetadataBounds(
                compactArtwork,
                expandedArtwork,
                compactMetadata,
                expandedMetadata,
                artwork,
                progress,
                travel = 1800f,
            )
            assertTrue(
                "progress $progress: $title",
                title.right <= expandedMetadata.right && title.left >= expandedMetadata.left,
            )
        }
    }
}
