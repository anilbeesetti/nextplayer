package dev.anilbeesetti.nextplayer.core.data.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.MediaStore

fun ContentResolver.getDataColumn(
    contentUri: Uri,
    selection: String?,
    selectionArgs: Array<String>?
): String? {
    var data: String? = null
    val cursor =
        query(contentUri, arrayOf(MediaStore.Video.Media.DATA), selection, selectionArgs, null)

    if (cursor != null && cursor.moveToFirst()) {
        data = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA))
    }
    cursor?.close()
    return data
}
