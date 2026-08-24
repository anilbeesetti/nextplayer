package dev.anilbeesetti.nextplayer.core.media.services

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class MediaStoreMediaServicePermissionTest {

    @Test
    fun fetchVideosThrowsWhenProviderDeniesAccess() = runBlocking {
        val provider = object : ContentProvider() {
            override fun onCreate(): Boolean = true

            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?,
            ): Cursor? = throw SecurityException("Permission denial")

            override fun getType(uri: Uri): String? = null

            override fun insert(uri: Uri, values: ContentValues?): Uri? = null

            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

            override fun update(
                uri: Uri,
                values: ContentValues?,
                selection: String?,
                selectionArgs: Array<out String>?,
            ): Int = 0
        }
        val contentResolver = ContentResolver.wrap(provider)
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(targetContext) {
            override fun getContentResolver(): ContentResolver = contentResolver
        }
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            try {
                MediaStoreMediaService(context, applicationScope).fetchVideos()
                fail("Expected MediaStore permission denial to be preserved")
            } catch (_: SecurityException) {
                // Expected: callers decide how to handle an unavailable MediaStore snapshot.
            }
        } finally {
            applicationScope.cancel()
        }
    }

    @Test
    fun fetchVideosFailsWhenProviderReturnsNullCursor() = runBlocking {
        val provider = object : ContentProvider() {
            override fun onCreate(): Boolean = true

            override fun query(
                uri: Uri,
                projection: Array<out String>?,
                selection: String?,
                selectionArgs: Array<out String>?,
                sortOrder: String?,
            ): Cursor? = null

            override fun getType(uri: Uri): String? = null

            override fun insert(uri: Uri, values: ContentValues?): Uri? = null

            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

            override fun update(
                uri: Uri,
                values: ContentValues?,
                selection: String?,
                selectionArgs: Array<out String>?,
            ): Int = 0
        }
        val contentResolver = ContentResolver.wrap(provider)
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val context = object : ContextWrapper(targetContext) {
            override fun getContentResolver(): ContentResolver = contentResolver
        }
        val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            try {
                MediaStoreMediaService(context, applicationScope).fetchVideos()
                fail("Expected a null MediaStore cursor to fail the strict snapshot")
            } catch (_: IllegalStateException) {
                // Expected: reconciliation must not treat a null cursor as an empty library.
            }
        } finally {
            applicationScope.cancel()
        }
    }
}
