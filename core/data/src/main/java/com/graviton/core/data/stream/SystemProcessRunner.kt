package com.graviton.core.data.stream

import android.os.Build
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemProcessRunner @Inject constructor() : ProcessRunner {
    override fun run(command: List<String>, timeoutMs: Long): ProcessResult {
        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .directory(File("."))
            .start()

        val finished = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } else {
            var isFinished = false
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    process.exitValue()
                    isFinished = true
                    break
                } catch (e: IllegalThreadStateException) {
                    Thread.sleep(50)
                }
            }
            isFinished
        }

        if (!finished) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.destroyForcibly()
            } else {
                process.destroy()
            }
            return ProcessResult(
                exitCode = -1,
                stdout = "",
                stderr = "yt-dlp timed out after ${timeoutMs}ms",
            )
        }
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        return ProcessResult(exitCode = process.exitValue(), stdout = stdout, stderr = stderr)
    }
}
