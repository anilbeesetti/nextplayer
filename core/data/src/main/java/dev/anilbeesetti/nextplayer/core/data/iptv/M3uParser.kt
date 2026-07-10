package dev.anilbeesetti.nextplayer.core.data.iptv

import dev.anilbeesetti.nextplayer.core.model.IptvChannel

/**
 * A minimal, dependency-free parser for extended M3U / M3U8 playlists as used by IPTV providers.
 *
 * It understands the common directives:
 * ```
 * #EXTM3U
 * #EXTINF:-1 tvg-id="id" tvg-logo="http://logo.png" group-title="News",Channel Name
 * http://host/stream.m3u8
 * ```
 * Attributes are parsed loosely (any `key="value"` pair on the `#EXTINF` line), the text after the
 * last comma is the channel name, and the following non-directive line is the stream url. A leading
 * `#EXTVLCOPT`/`#KODIPROP` block between the `#EXTINF` and its url is tolerated and skipped.
 *
 * The `duration` field of `#EXTINF` is used as a live-stream hint: a value of `-1` (or `0`) marks a
 * live channel, anything positive is treated as an on-demand item.
 */
object M3uParser {

    private val attrRegex = Regex("""([\w-]+)\s*=\s*"([^"]*)"""")

    fun parse(content: String): List<IptvChannel> {
        val lines = content.lineSequence().map { it.trim() }.iterator()
        val channels = mutableListOf<IptvChannel>()

        var pending: PendingChannel? = null
        while (lines.hasNext()) {
            val line = lines.next()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> Unit
                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    pending = parseExtInf(line)
                }
                // Provider option lines that sit between #EXTINF and the url; ignore them.
                line.startsWith("#EXTVLCOPT", ignoreCase = true) -> Unit
                line.startsWith("#KODIPROP", ignoreCase = true) -> Unit
                line.startsWith("#EXTGRP", ignoreCase = true) -> {
                    val group = line.substringAfter(':', "").trim().ifEmpty { null }
                    val current = pending
                    if (group != null && current != null) {
                        pending = current.copy(groupTitle = current.groupTitle ?: group)
                    }
                }
                line.startsWith("#") -> Unit
                else -> {
                    // A url line. Attach it to the pending #EXTINF, or, for bare playlists without
                    // metadata, synthesize a channel named after the url.
                    val p = pending ?: PendingChannel(name = line.substringAfterLast('/').ifEmpty { line })
                    channels += IptvChannel(
                        name = p.name.ifBlank { line.substringAfterLast('/') },
                        url = line,
                        logoUrl = p.logoUrl,
                        groupTitle = p.groupTitle,
                        tvgId = p.tvgId,
                        isLive = p.isLive,
                    )
                    pending = null
                }
            }
        }
        return channels
    }

    private fun parseExtInf(line: String): PendingChannel {
        val payload = line.substringAfter(':', "")
        val name = payload.substringAfterLast(',').trim()
        val attrsSection = payload.substringBeforeLast(',', payload)

        val attrs = attrRegex.findAll(attrsSection)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }

        val duration = attrsSection.trimStart().takeWhile { it == '-' || it.isDigit() }.toIntOrNull()
        val isLive = duration == null || duration <= 0

        return PendingChannel(
            name = name,
            logoUrl = attrs["tvg-logo"]?.ifBlank { null },
            groupTitle = attrs["group-title"]?.ifBlank { null },
            tvgId = attrs["tvg-id"]?.ifBlank { null },
            isLive = isLive,
        )
    }

    private data class PendingChannel(
        val name: String,
        val logoUrl: String? = null,
        val groupTitle: String? = null,
        val tvgId: String? = null,
        val isLive: Boolean = true,
    )
}
