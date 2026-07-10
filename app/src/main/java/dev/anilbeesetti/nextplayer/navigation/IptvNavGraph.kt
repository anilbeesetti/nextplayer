package dev.anilbeesetti.nextplayer.navigation

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.anilbeesetti.nextplayer.core.model.IptvChannel
import dev.anilbeesetti.nextplayer.feature.iptv.navigation.iptvEntry
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import dev.anilbeesetti.nextplayer.feature.player.utils.PlayerApi

fun EntryProviderScope<NavKey>.iptvNavGraph(
    context: Context,
    backStack: NavBackStack<NavKey>,
) {
    iptvEntry(
        onNavigateUp = { backStack.removeLastOrNull() },
        onPlayChannel = { channel -> context.playChannel(channel) },
    )
}

private fun Context.playChannel(channel: IptvChannel) {
    val intent = Intent(this, PlayerActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = channel.url.toUri()
        putExtra(PlayerApi.API_TITLE, channel.name)
        // Hint the container for extension-less HLS streams, which are the norm for IPTV.
        guessMimeType(channel.url)?.let { putExtra(PlayerApi.API_MIME_TYPE, it) }
    }
    startActivity(intent)
}

// Media3 MimeTypes string constants, inlined so the app module needn't depend on media3.
private const val MIME_HLS = "application/x-mpegURL"
private const val MIME_DASH = "application/dash+xml"
private const val MIME_SS = "application/vnd.ms-sstr+xml"

private fun guessMimeType(url: String): String? {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".m3u8") || path.endsWith(".m3u") -> MIME_HLS
        path.endsWith(".mpd") -> MIME_DASH
        path.endsWith(".ism") || path.contains("/manifest") -> MIME_SS
        // Many IPTV providers serve HLS from extension-less endpoints (…/live/1234); default to HLS.
        !path.substringAfterLast('/').contains('.') -> MIME_HLS
        else -> null
    }
}
