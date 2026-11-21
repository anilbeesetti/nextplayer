package dev.anilbeesetti.nextplayer.core.common.extensions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import dev.anilbeesetti.nextplayer.core.common.storagePermission

/**
 * Whether the app may read all files, including drives MediaStore does not list. Below Android 11
 * there is no such permission, so this is true.
 */
fun hasAllFilesAccess(): Boolean = try {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
} catch (e: Exception) {
    // If the permission cannot be read, treat it as not granted.
    false
}

/**
 * Whether videos can be listed: with the media permission (limited access) or with All files
 * access (full access).
 */
fun Context.hasStorageAccess(): Boolean = ContextCompat.checkSelfPermission(
    this,
    storagePermission,
) == PackageManager.PERMISSION_GRANTED || hasAllFilesAccess()

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

private const val PROMPT_PREFERENCES = "permission_prompts"
private const val KEY_ALL_FILES_ACCESS_PROMPTED = "all_files_access_prompted"

/** Whether the "grant all files access" choice was already shown, so it is only shown once. */
var Context.allFilesAccessPrompted: Boolean
    get() = getSharedPreferences(
        PROMPT_PREFERENCES,
        Context.MODE_PRIVATE,
    )
        .getBoolean(
            KEY_ALL_FILES_ACCESS_PROMPTED,
            false,
        )
    set(value) = getSharedPreferences(
        PROMPT_PREFERENCES,
        Context.MODE_PRIVATE,
    )
        .edit {
            putBoolean(
                KEY_ALL_FILES_ACCESS_PROMPTED,
                value,
            )
        }
