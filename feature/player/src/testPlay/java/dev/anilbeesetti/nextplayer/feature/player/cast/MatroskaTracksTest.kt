package dev.anilbeesetti.nextplayer.feature.player.cast

import java.util.zip.CRC32
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MatroskaTracksTest {
    @Test
    fun reordersWholeEntriesAndPreservesOffsetsOtherChildrenAndChecksums() {
        for (crc in listOf(
            false,
            true,
        )) {
            val original = movie(crc = crc)
            val unchanged = original.copyOf()
            val tracks = checkNotNull(MatroskaTracks.parse(original))
            assertEquals(
                listOf(
                    2L,
                    9L,
                    12L,
                ),
                tracks.audioTracks.map { it.number },
            )
            assertEquals(
                listOf(
                    "eng",
                    "jpn",
                    "en",
                ),
                tracks.audioTracks.map { it.language },
            )
            assertEquals(
                "Japanese",
                tracks.audioTracks[1].name,
            )
            val patch = tracks.selectAudio(9)
            val result = patch.wrap(original.inputStream()).use { it.readBytes() }
            assertEquals(
                original.size,
                result.size,
            )
            assertArrayEquals(
                unchanged,
                original,
            )
            assertArrayEquals(
                original.copyOfRange(
                    0,
                    patch.offset.toInt(),
                ),
                result.copyOfRange(
                    0,
                    patch.offset.toInt(),
                ),
            )
            val end = patch.offset.toInt() + patch.length
            assertArrayEquals(
                original.copyOfRange(
                    end,
                    original.size,
                ),
                result.copyOfRange(
                    end,
                    result.size,
                ),
            )
            val reordered = checkNotNull(MatroskaTracks.parse(result))
            assertEquals(
                listOf(
                    9L,
                    2L,
                    12L,
                ),
                reordered.audioTracks.map { it.number },
            )
            assertEquals(
                tracks.audioTracks.map {
                    it.name
                }.toSet(),
                reordered.audioTracks.map { it.name }.toSet(),
            )
            // Swapping back must restore every header byte, including padding and the CRC.
            assertArrayEquals(
                original,
                reordered.selectAudio(2).wrap(result.inputStream()).use {
                    it.readBytes()
                },
            )
        }
    }

    @Test
    fun unknownSegmentSizeAndDifferentLanguageSelectionsWork() {
        val original = movie(unknownSegment = true)
        val tracks = checkNotNull(MatroskaTracks.parse(original))
        val result = tracks.selectAudio(12).wrap(original.inputStream()).use { it.readBytes() }
        assertEquals(
            listOf(
                12L,
                9L,
                2L,
            ),
            checkNotNull(MatroskaTracks.parse(result)).audioTracks.map {
                it.number
            },
        )
    }

    @Test
    fun partialReadsSingleByteReadsAndSkippingUseAbsoluteSourceOffsets() {
        val original = movie()
        val patch = checkNotNull(MatroskaTracks.parse(original)).selectAudio(9)
        val expected = patch.wrap(original.inputStream()).use { it.readBytes() }
        val edge = patch.offset.toInt() + patch.length
        for ((start, end) in listOf(
            0 to expected.size,
            0 to 4,
            patch.offset.toInt() - 1 to edge + 1,
            patch.offset.toInt() + 5 to edge - 5,
            edge to expected.size,
        )) {
            val source = original.copyOfRange(
                start,
                end,
            ).inputStream()
            assertArrayEquals(
                expected.copyOfRange(
                    start,
                    end,
                ),
                patch.wrap(
                    source,
                    start.toLong(),
                ).use {
                    it.readBytes()
                },
            )
        }
        patch.wrap(original.inputStream()).use { input ->
            assertEquals(
                patch.offset - 1,
                input.skip(patch.offset - 1),
            )
            for (index in patch.offset.toInt() - 1 until expected.size) {
                assertEquals(
                    expected[index].toInt() and 0xff,
                    input.read(),
                )
            }
            assertEquals(
                -1,
                input.read(),
            )
        }
    }

    @Test
    fun nonMatroskaAndLateTracksAreNotAdvertisedAsSwitchable() {
        assertNull(MatroskaTracks.parse("not a movie".toByteArray()))
        assertNull(MatroskaTracks.parse(byteArrayOf()))
        val prefix =
            element(
                0x1A45DFA3,
                byteArrayOf(),
            ) + id(0x18538067) + byteArrayOf(0xff.toByte()) +
                id(0xEC) + size(20_000_000) + byteArrayOf(0)
        assertNull(MatroskaTracks.parse(prefix))
    }

    @Test
    fun segmentChecksumIsNotSilentlyInvalidated() {
        assertNull(
            MatroskaTracks.parse(
                element(
                    0x1A45DFA3,
                    byteArrayOf(),
                ) +
                    element(
                        0x18538067,
                        element(
                            0xBF,
                            ByteArray(4),
                        ) + movie(),
                    ),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadTracksChecksum() {
        MatroskaTracks.parse(movie(badCrc = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateTrackNumbers() {
        MatroskaTracks.parse(movie(duplicateNumber = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedTrackPayload() {
        val original = movie()
        val tracks = checkNotNull(MatroskaTracks.parse(original))
        val offset = tracks.selectAudio(9).offset.toInt()
        MatroskaTracks.parse(original.copyOf(offset + 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOversizedHeader() {
        MatroskaTracks.parse(ByteArray(MatroskaTracks.MAX_HEADER_BYTES + 1))
    }

    @Test
    fun rejectsInvalidElementSizesAndMissingAudioSelection() {
        for (malformed in listOf(
            byteArrayOf(
                0x1a,
                0x45,
                0xdf.toByte(),
                0xa3.toByte(),
                0,
            ),
            byteArrayOf(
                0x1a,
                0x45,
                0xdf.toByte(),
                0xa3.toByte(),
                0x81.toByte(),
            ),
            element(
                0x1A45DFA3,
                byteArrayOf(),
            ) + id(0x18538067) + byteArrayOf(
                0xff.toByte(),
                0,
            ),
        )) {
            assertTrue(runCatching { MatroskaTracks.parse(malformed) }.isFailure)
        }
        assertTrue(
            runCatching {
                checkNotNull(MatroskaTracks.parse(movie())).selectAudio(999)
            }.isFailure,
        )
    }

    private fun movie(
        crc: Boolean = true,
        unknownSegment: Boolean = false,
        badCrc: Boolean = false,
        duplicateNumber: Boolean = false,
    ): ByteArray {
        val entries = track(
            1,
            1,
            "Video",
        ) + element(
            0xEC,
            byteArrayOf(
                4,
                5,
                6,
            ),
        ) +
            track(
                2,
                2,
                "English with a longer name",
            ) +
            track(
                if (duplicateNumber) 2 else 9,
                2,
                "Japanese",
                "jpn",
            ) +
            track(
                12,
                2,
                "Commentary",
                "en",
            )
        val checksum = CRC32().apply { update(entries) }.value
        val crcBytes = ByteArray(4) { if (badCrc) 0 else (checksum ushr (8 * it)).toByte() }
        val payload = (
            if (crc) {
                element(
                    0xBF,
                    crcBytes,
                )
            } else {
                byteArrayOf()
            }
            ) + entries
        val segment = element(
            0xEC,
            ByteArray(7),
        ) + element(
            0x1654AE6B,
            payload,
        ) +
            element(
                0x1F43B675,
                ByteArray(128) { (it % 97).toByte() },
            )
        return element(
            0x1A45DFA3,
            byteArrayOf(),
        ) + id(0x18538067) +
            (if (unknownSegment) byteArrayOf(0xff.toByte()) else size(segment.size)) + segment
    }

    private fun track(number: Int, type: Int, name: String, language: String? = null): ByteArray =
        element(
            0xAE,
            element(
                0xD7,
                byteArrayOf(number.toByte()),
            ) + element(
                0x83,
                byteArrayOf(type.toByte()),
            ) +
                element(
                    0x536E,
                    name.toByteArray(),
                ) +
                (
                    language?.let {
                        element(
                            0x22B59C,
                            it.toByteArray(),
                        )
                    } ?: byteArrayOf()
                    ) +
                element(
                    0x63A2,
                    byteArrayOf(
                        11,
                        22,
                        33,
                    ),
                ),
        )

    private fun element(kind: Long, payload: ByteArray): ByteArray =
        id(kind) + size(payload.size) + payload
    private fun id(value: Long): ByteArray {
        val width = (1..4).first { value ushr (8 * it) == 0L }
        return ByteArray(width) { (value ushr (8 * (width - 1 - it))).toByte() }
    }
    private fun size(value: Int): ByteArray {
        val width = (1..8).first { value.toLong() < (1L shl (7 * it)) - 1 }
        val encoded = value.toLong() or (1L shl (7 * width))
        return ByteArray(width) { (encoded ushr (8 * (width - 1 - it))).toByte() }
    }
}
