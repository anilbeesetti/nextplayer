package dev.anilbeesetti.nextplayer.feature.player.utils

import android.content.ClipData
import android.content.Intent
import android.net.Uri

internal fun createVideoShareIntent(uri: Uri): Intent = Intent.createChooser(
    Intent(Intent.ACTION_SEND).apply {
        type = "video/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri(
            null,
            uri,
        )
        putExtra(
            Intent.EXTRA_STREAM,
            uri,
        )
    },
    null,
)
