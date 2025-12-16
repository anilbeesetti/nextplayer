package dev.anilbeesetti.nextplayer.core.common.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object NextLogger {
    private const val LOG_DIR_NAME = "logs"
    private const val LOG_FILE_NAME = "nextplayer.log"
    private const val MAX_LOG_BYTES = 5L * 1024L * 1024L

    private val initialized = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "NextLogger").apply { isDaemon = true }
    }

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        if (initialized.getAndSet(true)) return
        val dir = File(context.filesDir, LOG_DIR_NAME).apply { mkdirs() }
        logFile = File(dir, LOG_FILE_NAME)
        i("NextLogger", "Initialized. file=${logFile?.absolutePath}")
    }

    fun d(tag: String, message: String, throwable: Throwable? = null) = log("D", tag, message, throwable)

    fun i(tag: String, message: String, throwable: Throwable? = null) = log("I", tag, message, throwable)

    fun w(tag: String, message: String, throwable: Throwable? = null) = log("W", tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) = log("E", tag, message, throwable)

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val file = logFile ?: return
        val timestamp = synchronized(TIMESTAMP_FORMAT) {
            TIMESTAMP_FORMAT.format(Date())
        }
        val thread = Thread.currentThread().name
        val line = buildString {
            append(timestamp)
            append(' ')
            append(level)
            append('/')
            append(tag)
            append(" [")
            append(thread)
            append("] ")
            append(message)
            if (throwable != null) {
                append('\n')
                append(throwable.stackTraceToString())
            }
            append('\n')
        }

        executor.execute {
            runCatching {
                rotateIfNeeded(file)
                file.appendText(line)
            }
        }
    }

    private fun rotateIfNeeded(file: File) {
        if (!file.exists()) return
        if (file.length() <= MAX_LOG_BYTES) return
        val rotated = File(file.parentFile, "$LOG_FILE_NAME.1")
        rotated.delete()
        file.renameTo(rotated)
    }

    private val TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
}
