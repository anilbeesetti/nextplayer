package dev.anilbeesetti.nextplayer.core.media.network

/** Video file extensions surfaced while browsing network locations. */
val networkVideoExtensions = setOf(
    "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "flv", "mpeg", "mpg",
    "3gp", "ts", "m2ts", "mts", "vob", "ogv", "rmvb", "asf", "divx", "f4v",
)

fun isNetworkVideoFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase() in networkVideoExtensions

fun networkVideoMimeType(name: String): String =
    when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "wmv" -> "video/x-ms-wmv"
        "flv" -> "video/x-flv"
        "mpeg", "mpg" -> "video/mpeg"
        "3gp" -> "video/3gpp"
        "ts", "m2ts", "mts" -> "video/mp2t"
        else -> "video/*"
    }
