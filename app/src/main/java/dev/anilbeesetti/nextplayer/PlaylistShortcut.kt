package dev.anilbeesetti.nextplayer

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary

internal object PlaylistShortcut {
    private const val ACTION_OPEN_PLAYLIST = "dev.anilbeesetti.nextplayer.action.OPEN_PLAYLIST"
    private const val EXTRA_PLAYLIST_ID = "playlist_id"

    fun isSupported(context: Context): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    fun requestPin(context: Context, playlist: PlaylistSummary): Boolean =
        ShortcutManagerCompat.requestPinShortcut(context, create(context, playlist), null)

    fun create(context: Context, playlist: PlaylistSummary): ShortcutInfoCompat =
        ShortcutInfoCompat.Builder(context, "playlist_${playlist.id}")
            .setShortLabel(playlist.name)
            .setLongLabel(playlist.name)
            .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = ACTION_OPEN_PLAYLIST
                    putExtra(EXTRA_PLAYLIST_ID, playlist.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                },
            )
            .build()

    fun playlistId(intent: Intent): Long? =
        if (intent.action == ACTION_OPEN_PLAYLIST) {
            intent.getLongExtra(EXTRA_PLAYLIST_ID, -1).takeIf { it > 0 }
        } else {
            null
        }
}
