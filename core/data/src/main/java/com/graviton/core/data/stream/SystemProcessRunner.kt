package com.graviton.core.data.stream

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
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
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
