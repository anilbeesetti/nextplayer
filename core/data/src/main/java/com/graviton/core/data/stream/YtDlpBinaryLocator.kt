package com.graviton.core.data.stream

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

fun interface YtDlpBinaryLocator {
    fun candidates(): List<String>
}

@Singleton
class AppYtDlpBinaryLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) : YtDlpBinaryLocator {
    override fun candidates(): List<String> = listOfNotNull(
        System.getenv("YT_DLP"),
        File(context.filesDir, "bin/yt-dlp").absolutePath,
        File(context.filesDir, "yt-dlp").absolutePath,
        "/data/local/tmp/yt-dlp",
        "/system/bin/yt-dlp",
        "/system/xbin/yt-dlp",
        "/usr/bin/yt-dlp",
        "/usr/local/bin/yt-dlp",
        "yt-dlp",
    )
}
