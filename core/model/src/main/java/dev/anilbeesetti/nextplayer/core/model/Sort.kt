package dev.anilbeesetti.nextplayer.core.model

import kotlin.comparisons.reversed as kotlinReversed

data class Sort(
    val by: By,
    val order: Order,
) {
    enum class By {
        TITLE,
        LENGTH,
        PATH,
        SIZE,
        DATE,
    }

    enum class Order {
        ASCENDING,
        DESCENDING,
    }

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
            return@Comparator stringComparator.compare(
                video1.displayName.lowercase(),
                video2.displayName.lowercase(),
            )
        }

        val videoPathComparator: Comparator<Video> = Comparator { video1, video2 ->
            return@Comparator stringComparator.compare(
                video1.path.lowercase(),
                video2.path.lowercase(),
            )
        }

        val comparator = when (by) {
            By.TITLE -> videoTitleComparator
            By.LENGTH -> compareBy<Video> { it.duration }.then(videoTitleComparator)
            By.PATH -> videoPathComparator
            By.SIZE -> compareBy<Video> { it.size }.then(videoTitleComparator)
            By.DATE -> compareBy<Video> { it.dateModified }.then(videoTitleComparator)
        }

        return when (order) {
            Order.ASCENDING -> comparator
            Order.DESCENDING -> comparator.reversedCompat()
        }
    }

    fun folderComparator(): Comparator<Folder> {
        val folderNameComparator: Comparator<Folder> = Comparator { folder1, folder2 ->
            return@Comparator stringComparator.compare(
                folder1.name.lowercase(),
                folder2.name.lowercase(),
            )
        }

        val folderPathComparator: Comparator<Folder> = Comparator { folder1, folder2 ->
            return@Comparator stringComparator.compare(
                folder1.path.lowercase(),
                folder2.path.lowercase(),
            )
        }

        val comparator = when (by) {
            By.TITLE -> folderNameComparator
            By.LENGTH -> compareBy<Folder> { it.mediaList.size }.then(folderNameComparator)
            By.PATH -> folderPathComparator
            By.SIZE -> compareBy<Folder> { it.mediaSize }.then(folderNameComparator)
            By.DATE -> compareBy<Folder> { it.dateModified }.then(folderNameComparator)
        }

        return when (order) {
            Order.ASCENDING -> comparator
            Order.DESCENDING -> comparator.reversedCompat()
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
