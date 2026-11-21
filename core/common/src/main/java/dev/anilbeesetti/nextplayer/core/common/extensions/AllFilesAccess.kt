package dev.anilbeesetti.nextplayer.core.common.extensions

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Whether the app may read all files, including drives MediaStore does not list. Below Android 11
 * there is no such permission, so this is true.
 */
fun hasAllFilesAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

/** Opens the system page where "All files access" is turned on or off for this app. */
fun Context.openAllFilesAccessSettings() {
    val appPage = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        "package:$packageName".toUri(),
    )
    try {
        startActivity(appPage)
    } catch (e: Exception) {
        startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}

/** Starts the app again in a new process, for changes Android only applies to a new process. */
fun Context.restartApp() {
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
    ) ?: return
    startActivity(launchIntent)
    Runtime.getRuntime().exit(0)
}
