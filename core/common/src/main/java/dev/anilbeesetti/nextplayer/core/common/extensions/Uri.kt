package dev.anilbeesetti.nextplayer.core.common.extensions

import android.net.Uri

/**
 * Whether the Uri authority is ExternalStorageProvider.
 */
val Uri.isExternalStorageDocument: Boolean
    get() = "com.android.externalstorage.documents" == authority

/**
 * Whether the Uri authority is DownloadsProvider.
 */
val Uri.isDownloadsDocument: Boolean
    get() = "com.android.providers.downloads.documents" == authority

/**
 * Whether the Uri authority is MediaProvider.
 */
val Uri.isMediaDocument: Boolean
    get() = "com.android.providers.media.documents" == authority

/**
 * Whether the Uri authority is Google Photos.
 */
val Uri.isGooglePhotosUri: Boolean
    get() = "com.google.android.apps.photos.content" == authority

/**
 * Whether the Uri authority is PhotoPicker.
 */
val Uri.isLocalPhotoPickerUri: Boolean
    get() = toString().contains("com.android.providers.media.photopicker")

/**
 * Whether the Uri authority is PhotoPicker.
 */
val Uri.isCloudPhotoPickerUri: Boolean
    get() = toString().contains("com.google.android.apps.photos.cloudpicker")
