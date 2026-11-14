package dev.anilbeesetti.nextplayer.feature.player.cast

import java.io.InputStream
import java.util.zip.CRC32

/** Same-length replacement: Cluster, SeekHead and Cue offsets remain valid. */
internal class MatroskaTrackPatch(val offset: Long, private val bytes: ByteArray) {
    val length: Int get() = bytes.size

    init {
        require(offset >= 0 && offset <= Long.MAX_VALUE - bytes.size)
    }

    fun wrap(input: InputStream, start: Long = 0): InputStream = object : InputStream() {
        private var position = start
        private val singleByte = ByteArray(1)

        override fun read(): Int = if (read(
                singleByte,
                0,
                1,
            ) <
            0
        ) {
            -1
        } else {
            singleByte[0].toInt() and 0xff
        }

        override fun read(buffer: ByteArray, bufferOffset: Int, length: Int): Int {
            val count = input.read(
                buffer,
                bufferOffset,
                length,
            )
            if (count > 0) {
                val left = maxOf(
                    position,
                    offset,
                )
                val right = minOf(
                    position + count,
                    offset + bytes.size,
                )
                if (left < right) {
                    bytes.copyInto(
                        buffer,
                        bufferOffset + (left - position).toInt(),
                        (left - offset).toInt(),
                        (
                            right -
                                offset
                            ).toInt(),
                    )
                }
                position += count
            }
            return count
        }

        override fun skip(count: Long): Long = input.skip(count).also { position += it }
        override fun available(): Int = input.available()
        override fun close() = input.close()
    }
}

/** A receiver which cannot enumerate MKV audio plays the first supported audio TrackEntry. */
internal class MatroskaTracks private constructor(
    private val offset: Int,
    private val payload: ByteArray,
    private val children: List<Element>,
    val audioTracks: List<Track>,
) {
    data class Track(
        val number: Long,
        val name: String?,
        val language: String,
        internal val elementIndex: Int,
    )

    fun selectAudio(number: Long): MatroskaTrackPatch {
        val selected = audioTracks.single { it.number == number }
        val chunks = children.map {
            payload.copyOfRange(
                it.start,
                it.end(payload.size),
            )
        }.toMutableList()
        val firstIndex = audioTracks.first().elementIndex
        val selectedIndex = selected.elementIndex
        val first = chunks[firstIndex]
        chunks[firstIndex] = chunks[selectedIndex]
        chunks[selectedIndex] = first
        val crcIndex = children.indexOfFirst { it.id == CRC }
        if (crcIndex >= 0) {
            val checksum = CRC32()
            chunks.forEachIndexed { index, chunk -> if (index != crcIndex) checksum.update(chunk) }
            val data = children[crcIndex].data - children[crcIndex].start
            repeat(4) { chunks[crcIndex][data + it] = (checksum.value ushr (8 * it)).toByte() }
        }
        val replacement = ByteArray(payload.size)
        var position = 0
        for (chunk in chunks) {
            chunk.copyInto(
                replacement,
                position,
            )
            position += chunk.size
        }
        check(position == payload.size)
        return MatroskaTrackPatch(
            offset.toLong(),
            replacement,
        )
    }

    companion object {
        // shortcut: bounded header prefix only, follow SeekHead if late Tracks need support.
        const val MAX_HEADER_BYTES = 8 * 1024 * 1024
        private const val MAX_TRACK_BYTES = 1024 * 1024
        private const val MAX_ELEMENTS = 4096
        private const val EBML = 0x1A45DFA3L
        private const val SEGMENT = 0x18538067L
        private const val TRACKS = 0x1654AE6BL
        private const val TRACK_ENTRY = 0xAEL
        private const val CRC = 0xBFL

        fun parse(prefix: ByteArray): MatroskaTracks? {
            require(prefix.size <= MAX_HEADER_BYTES)
            if (prefix.size < 4 || prefix[0] != 0x1a.toByte() || prefix[1] != 0x45.toByte() ||
                prefix[2] != 0xdf.toByte() || prefix[3] != 0xa3.toByte()
            ) {
                return null
            }
            val header = element(
                prefix,
                0,
                prefix.size,
            )
            require(header.id == EBML)
            val segment = element(
                prefix,
                header.end(prefix.size),
                prefix.size,
            )
            require(segment.id == SEGMENT)
            val limit = minOf(
                segment.end ?: prefix.size.toLong(),
                prefix.size.toLong(),
            ).toInt()
            var position = segment.data
            var count = 0
            while (position < limit) {
                require(++count <= MAX_ELEMENTS)
                val item = element(
                    prefix,
                    position,
                    limit,
                )
                // A Segment-level checksum would require reading the whole film to recompute it.
                if (item.id == CRC) return null
                if (item.id == TRACKS) {
                    val end = item.end(limit)
                    require(end - item.data <= MAX_TRACK_BYTES)
                    return fromPayload(
                        item.data,
                        prefix.copyOfRange(
                            item.data,
                            end,
                        ),
                    )
                }
                val next = item.end ?: return null
                if (next > limit) return null
                position = item.end(limit)
            }
            return null
        }

        private fun fromPayload(offset: Int, payload: ByteArray): MatroskaTracks {
            val children = children(
                payload,
                0,
                payload.size,
            )
            val crc = children.filter { it.id == CRC }
            require(crc.size <= 1)
            crc.singleOrNull()?.let { entry ->
                require(entry.end(payload.size) - entry.data == 4)
                val checksum = CRC32()
                children.filter { it.id != CRC }.forEach {
                    checksum.update(
                        payload,
                        it.start,
                        it.end(payload.size) - it.start,
                    )
                }
                var stored = 0L
                repeat(4) {
                    stored =
                        stored or ((payload[entry.data + it].toLong() and 0xff) shl (8 * it))
                }
                require(stored == checksum.value) { "Invalid Tracks CRC" }
            }
            val numbers = mutableSetOf<Long>()
            val audio = children.mapIndexedNotNull { index, entry ->
                if (entry.id != TRACK_ENTRY) return@mapIndexedNotNull null
                val fields = children(
                    payload,
                    entry.data,
                    entry.end(payload.size),
                )
                val number = uint(
                    payload,
                    fields.single { it.id == 0xD7L },
                )
                require(number > 0 && numbers.add(number)) { "Invalid or duplicate track number" }
                val type = uint(
                    payload,
                    fields.single { it.id == 0x83L },
                )
                if (type != 2L) return@mapIndexedNotNull null
                val name = fields.singleOrNull { it.id == 0x536EL }?.let {
                    text(
                        payload,
                        it,
                    )
                }
                val language = fields.singleOrNull { it.id == 0x22B59DL }
                    ?: fields.singleOrNull { it.id == 0x22B59CL }
                Track(
                    number,
                    name,
                    language?.let {
                        text(
                            payload,
                            it,
                        )
                    } ?: "eng",
                    index,
                )
            }
            return MatroskaTracks(
                offset,
                payload,
                children,
                audio,
            )
        }

        private fun children(bytes: ByteArray, start: Int, limit: Int): List<Element> {
            val result = mutableListOf<Element>()
            var position = start
            while (position < limit) {
                require(result.size < MAX_ELEMENTS)
                val child = element(
                    bytes,
                    position,
                    limit,
                )
                result += child
                position = child.end(limit)
            }
            require(position == limit)
            return result
        }

        private fun uint(bytes: ByteArray, entry: Element): Long {
            val end = entry.end(bytes.size)
            require(end - entry.data in 1..8)
            var value = 0L
            for (index in entry.data until end) {
                value =
                    (value shl 8) or (bytes[index].toLong() and 0xff)
            }
            require(value >= 0)
            return value
        }

        private fun text(bytes: ByteArray, entry: Element): String = bytes.copyOfRange(
            entry.data,
            entry.end(bytes.size),
        ).toString(Charsets.UTF_8).trimEnd('\u0000')

        private fun element(bytes: ByteArray, position: Int, limit: Int): Element {
            fun vint(start: Int, id: Boolean): Pair<Long, Int> {
                require(start < limit)
                val first = bytes[start].toInt() and 0xff
                require(first != 0)
                var width = 1
                while (first and (0x80 ushr (width - 1)) == 0) width++
                require(width <= (if (id) 4 else 8) && width <= limit - start)
                var value = (if (id) first else first and (0xff ushr width)).toLong()
                for (index in 1 until width) {
                    value =
                        (value shl 8) or (bytes[start + index].toLong() and 0xff)
                }
                return value to width
            }
            val (id, idWidth) = vint(
                position,
                true,
            )
            val (size, sizeWidth) = vint(
                position + idWidth,
                false,
            )
            val data = position + idWidth + sizeWidth
            val unknown = size == (1L shl (7 * sizeWidth)) - 1
            return Element(
                id,
                position,
                data,
                if (unknown) null else data.toLong() + size,
            )
        }
    }

    private data class Element(val id: Long, val start: Int, val data: Int, val end: Long?) {
        fun end(limit: Int): Int {
            require(end != null && end in data.toLong()..limit.toLong()) {
                "Truncated or unknown-size element"
            }
            return end.toInt()
        }
    }
}
