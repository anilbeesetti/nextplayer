package dev.anilbeesetti.nextplayer.feature.player.cast

import android.content.Context
import android.widget.Toast
import com.google.android.gms.cast.framework.CastContext
import dev.anilbeesetti.nextplayer.core.ui.R

internal fun castDeviceName(context: Context): String? = runCatching {
    CastContext.getSharedInstance(
        context,
    ).sessionManager.currentCastSession?.castDevice?.friendlyName
}.getOrNull()

internal fun playOnPhone(context: Context) {
    try {
        // End receiver playback as Media3 transfers the cached live state back to the phone.
        CastContext.getSharedInstance(context).sessionManager.endCurrentSession(true)
    } catch (_: RuntimeException) {
        Toast.makeText(
            context,
            R.string.cast_transfer_failed,
            Toast.LENGTH_LONG,
        ).show()
    }
}
