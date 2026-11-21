package dev.anilbeesetti.nextplayer.core.media.services

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ExternalVolumeMediaServiceTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun findsVideosInNestedFolders() {
        folder.newFolder(
            "Movies",
            "Holiday",
        )
        val root = folder.root
        File(
            root,
            "a.mp4",
        ).writeText("x")
        File(
            root,
            "Movies/b.MKV",
        ).writeText("x")
        File(
            root,
            "Movies/Holiday/c.avi",
        ).writeText("x")

        val names = findVideoFiles(root).map { it.name }.sorted()

        assertEquals(
            listOf(
                "a.mp4",
                "b.MKV",
                "c.avi",
            ),
            names,
        )
    }

    @Test
    fun ignoresNonVideoFiles() {
        File(
            folder.root,
            "notes.txt",
        ).writeText("x")
        File(
            folder.root,
            "song.mp3",
        ).writeText("x")

        assertEquals(
            emptyList<File>(),
            findVideoFiles(folder.root),
        )
    }

    @Test
    fun skipsHiddenFolders() {
        folder.newFolder(".Trash")
        File(
            folder.root,
            ".Trash/deleted.mp4",
        ).writeText("x")

        assertEquals(
            emptyList<File>(),
            findVideoFiles(folder.root),
        )
    }

    @Test
    fun displayPathUsesStorageUuidInsteadOfTheRealMountPoint() {
        assertEquals(
            "/storage/6650-5A1F/Movies/a.mp4",
            volumeDisplayPath(
                File("/mnt/media_rw/6650-5A1F"),
                "6650-5A1F",
                File("/mnt/media_rw/6650-5A1F/Movies/a.mp4"),
            ),
        )
    }
}
