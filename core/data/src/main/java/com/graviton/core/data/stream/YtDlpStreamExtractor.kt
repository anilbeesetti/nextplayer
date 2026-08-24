package com.graviton.core.data.stream

import com.graviton.core.model.ExtractedStream
import com.graviton.core.model.StreamUrls
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Resolves website URLs to a playable stream via yt-dlp (`-J --no-download`).
 * Direct media URLs are returned as-is so the player can buffer them immediately.
 */
@Singleton
class YtDlpStreamExtractor @Inject constructor(
    private val processRunner: ProcessRunner,
    private val binaryLocator: YtDlpBinaryLocator,
) : StreamExtractor {

    override suspend fun resolve(url: String): ExtractedStream {
        val trimmed = url.trim()
        require(trimmed.isNotEmpty()) { "URL is empty" }
        if (!StreamUrls.needsExtraction(trimmed)) {
            return ExtractedStream(
                sourceUrl = trimmed,
                playableUrl = trimmed,
                extracted = false,
                extension = StreamUrls.pathExtension(trimmed),
            )
        }
        val binary = findYtDlpBinary()
            ?: throw IllegalStateException(
                "yt-dlp was not found. Install yt-dlp on this device or place the binary in the app files directory.",
            )
        var lastError: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return extract(binary, trimmed)
            } catch (error: Throwable) {
                lastError = error
                if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
        throw lastError ?: IllegalStateException("yt-dlp failed for $trimmed")
    }

    private suspend fun extract(binary: String, url: String): ExtractedStream = withContext(Dispatchers.IO) {
        val jsonAttempt = processRunner.run(
            command = listOf(
                binary,
                "-J",
                "--no-download",
                "--no-playlist",
                "--no-warnings",
                "--no-call-home",
                "--socket-timeout",
                "20",
                url,
            ),
            timeoutMs = EXTRACT_TIMEOUT_MS,
        )
        if (jsonAttempt.exitCode == 0 && jsonAttempt.stdout.isNotBlank()) {
            return@withContext YtDlpJsonParser.parse(url, jsonAttempt.stdout)
        }
        val urlAttempt = processRunner.run(
            command = listOf(
                binary,
                "-g",
                "-f",
                "b/bv*+ba/ba",
                "--no-playlist",
                "--no-warnings",
                "--socket-timeout",
                "20",
                url,
            ),
            timeoutMs = EXTRACT_TIMEOUT_MS,
        )
        val playable = urlAttempt.stdout.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("http") }
        if (urlAttempt.exitCode == 0 && !playable.isNullOrBlank()) {
            return@withContext ExtractedStream(
                sourceUrl = url,
                playableUrl = playable,
                extracted = true,
            )
        }
        val detail = jsonAttempt.stderr.ifBlank { urlAttempt.stderr }.ifBlank { jsonAttempt.stdout }
        throw IllegalStateException(detail.ifBlank { "yt-dlp could not extract a playable URL" })
    }

    private fun findYtDlpBinary(): String? = binaryLocator.candidates().firstOrNull { candidate ->
        val file = File(candidate)
        when {
            file.isFile && file.canExecute() -> true
            candidate == "yt-dlp" || candidate.endsWith("/yt-dlp") -> canRun(candidate)
            else -> false
        }
    }

    private fun canRun(command: String): Boolean = runCatching {
        val result = processRunner.run(listOf(command, "--version"), timeoutMs = 4_000)
        result.exitCode == 0
    }.getOrDefault(false)

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 400L
        const val EXTRACT_TIMEOUT_MS = 45_000L
    }
}
