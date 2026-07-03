package dev.anilbeesetti.nextplayer.feature.player.extensions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/**
 * Variant of [androidx.activity.result.contract.ActivityResultContracts.OpenDocument] that also
 * lets the caller seed the picker's starting location via [DocumentsContract.EXTRA_INITIAL_URI].
 */
class OpenDocumentAtInitialUri : ActivityResultContract<OpenDocumentAtInitialUri.Input, Uri?>() {

    class Input(
        val mimeTypes: Array<String>,
        val initialUri: Uri? = null,
    )

    override fun createIntent(context: Context, input: Input): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES, input.mimeTypes)
            .apply {
                if (input.initialUri != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, input.initialUri)
                }
            }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return intent.takeIf { resultCode == Activity.RESULT_OK }?.data
    }
}
