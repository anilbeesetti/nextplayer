package dev.anilbeesetti.nextplayer.core.common

import android.content.res.Resources
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Utility functions.
 */
object Utils {

    /**
     * Converts px to dp.
     */
    fun pxToDp(px: Float): Float {
        return px / Resources.getSystem().displayMetrics.density
    }

    /**
     * Formats the given duration in milliseconds to a string in the format of `mm:ss` or `hh:mm:ss`.
     */
    fun formatDurationMillis(millis: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(millis)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) - TimeUnit.HOURS.toMinutes(hours)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) -
            TimeUnit.MINUTES.toSeconds(minutes) -
            TimeUnit.HOURS.toSeconds(hours)
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats the given duration in milliseconds to a string in the format of
     * `+mm:ss` or `+hh:mm:ss` or `-mm:ss` or `-hh:mm:ss`.
     */
    fun formatDurationMillisSign(millis: Long): String {
        return if (millis >= 0) {
            "+${formatDurationMillis(millis)}"
        } else {
            "-${formatDurationMillis(abs(millis))}"
        }
    }

    fun formatFileSize(size: Long): String {
        val kb = 1024
        val mb = kb * 1024
        val gb = mb * 1024

        return when {
            size < kb -> "$size B"
            size < mb -> "%.2f KB".format(size / kb.toDouble())
            size < gb -> "%.2f MB".format(size / mb.toDouble())
            else -> "%.2f GB".format(size / gb.toDouble())
        }
    }

    fun formatBitrate(bitrate: Long): String {
        if (bitrate <= 0) {
            return "0 bps"
        }

        val kiloBitrate = bitrate.toDouble() / 1000.0
        val megaBitrate = kiloBitrate / 1000.0
        val gigaBitrate = megaBitrate / 1000.0

        return when {
            gigaBitrate >= 1.0 -> String.format("%.2f Gbits/sec", gigaBitrate)
            megaBitrate >= 1.0 -> String.format("%.2f Mbits/sec", megaBitrate)
            kiloBitrate >= 1.0 -> String.format("%.2f kbits/sec", kiloBitrate)
            else -> String.format("%d bits/sec", bitrate)
        }
    }
}
