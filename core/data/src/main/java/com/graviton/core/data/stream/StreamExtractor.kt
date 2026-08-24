package com.graviton.core.data.stream

import com.graviton.core.model.ExtractedStream

/**
 * Turns a user-supplied URL into a URL the existing Media3 player can stream.
 * Implementations must not download the media body.
 */
interface StreamExtractor {
    suspend fun resolve(url: String): ExtractedStream
}

data class ProcessResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

fun interface ProcessRunner {
    fun run(command: List<String>, timeoutMs: Long): ProcessResult
}
