package dev.anilbeesetti.nextplayer.core.common.service.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.service.SuspendActivityResultLauncher
import dev.anilbeesetti.nextplayer.core.common.service.registerForSuspendActivityResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalSystemService @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemService {
    private var pickDocumentTreeLauncher: SuspendActivityResultLauncher<Uri?, Uri?>? = null

    override fun initialize(activity: ComponentActivity) {
        pickDocumentTreeLauncher = activity.registerForSuspendActivityResult(ActivityResultContracts.OpenDocumentTree())
    }

    override suspend fun pickFolder(): Uri? {
        return pickDocumentTreeLauncher?.launch(null)?.also { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }
    }

    override fun getString(stringResId: Int): String = context.getString(stringResId)

    override fun getQuantityString(
        pluralsResId: Int,
        quantity: Int,
        vararg formatArgs: Any,
    ): String = context.resources.getQuantityString(pluralsResId, quantity, *formatArgs)

    override fun showToast(text: String, duration: Int) {
        Toast.makeText(context, text, duration).show()
    }
}
