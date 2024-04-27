package dev.anilbeesetti.nextplayer.core.model

import kotlin.comparisons.reversed as kotlinReversed

data class Sort(
    val by: SortBy,
    val order: SortOrder
) {
    private val stringComparator = Comparator<String> { str1, str2 ->
        var str1Marker = 0
        var str2Marker = 0
        val str1Length = str1.length
        val str2Length = str2.length

        while (str1Marker < str1Length && str2Marker < str2Length) {
            val thisChunk = getChunk(str1, str1Length, str1Marker)
            str1Marker += thisChunk.length

            val thatChunk = getChunk(str2, str2Length, str2Marker)
            str2Marker += thatChunk.length

            // If both chunks contain numeric characters, sort them numerically.
            var result: Int
            if (thisChunk[0].isDigit() && thatChunk[0].isDigit()) {
                // Simple chunk comparison by length.
                val thisChunkLength = thisChunk.length
                result = thisChunkLength - thatChunk.length
                // If equal, the first different number counts.
                if (result == 0) {
                    for (i in 0 until thisChunkLength) {
                        result = thisChunk[i] - thatChunk[i]
                        if (result != 0) {
                            return@Comparator result
                        }
                    }
                }
            } else {
                result = thisChunk.compareTo(thatChunk)
            }

            if (result != 0) {
                return@Comparator result
            }
        }

        return@Comparator str1Length - str2Length
    }


    fun videoComparator(): Comparator<Video> {

        val videoTitleComparator: Comparator<Video> = Comparator { video1, video2 ->
            return@Comparator stringComparator.compare(video1.displayName, video2.displayName)
        }

        val videoPathComparator: Comparator<Video> = Comparator { video1, video2 ->
            return@Comparator stringComparator.compare(video1.path, video2.path)
        }

        val comparator = when (by) {
            SortBy.TITLE -> videoTitleComparator
            SortBy.LENGTH -> compareBy<Video> { it.duration }.then(videoTitleComparator)
            SortBy.PATH -> videoPathComparator
            SortBy.SIZE -> compareBy<Video> { it.size }.then(videoTitleComparator)
            SortBy.DATE -> compareBy<Video> { it.dateModified }.then(videoTitleComparator)
        }

        return when (order) {
            SortOrder.ASCENDING -> comparator
            SortOrder.DESCENDING -> comparator.reversedCompat()
        }
    }

    fun folderComparator(): Comparator<Folder> {
        val folderNameComparator: Comparator<Folder> = Comparator { folder1, folder2 ->
            return@Comparator stringComparator.compare(folder1.name.lowercase(), folder2.name.lowercase())
        }

        val folderPathComparator: Comparator<Folder> = Comparator { folder1, folder2 ->
            return@Comparator stringComparator.compare(folder1.path, folder2.path)
        }

        val comparator = when (by) {
            SortBy.TITLE -> folderNameComparator
            SortBy.LENGTH -> compareBy<Folder> { it.mediaCount }.then(folderNameComparator)
            SortBy.PATH -> folderPathComparator
            SortBy.SIZE -> compareBy<Folder> { it.mediaSize }.then(folderNameComparator)
            SortBy.DATE -> compareBy<Folder> { it.dateModified }.then(folderNameComparator)
        }

        return when (order) {
            SortOrder.ASCENDING -> comparator
            SortOrder.DESCENDING -> comparator.reversedCompat()
        }
    }

    private fun getChunk(string: String, length: Int, marker: Int): String {
        var current = marker
        val chunk = StringBuilder()
        var c = string[current]
        chunk.append(c)
        current++
        if (c.isDigit()) {
            while (current < length) {
                c = string[current]
                if (!c.isDigit()) {
                    break
                }
                chunk.append(c)
                current++
            }
        } else {
            while (current < length) {
                c = string[current]
                if (c.isDigit()) {
                    break
                }
                chunk.append(c)
                current++
            }
        }
        return chunk.toString()
    }
}

fun <T> Comparator<T>.reversedCompat(): Comparator<T> = kotlinReversed()