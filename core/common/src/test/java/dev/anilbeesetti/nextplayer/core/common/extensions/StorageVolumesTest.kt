package dev.anilbeesetti.nextplayer.core.common.extensions

import android.content.Context
import android.content.ContextWrapper
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.StorageVolumeBuilder

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 35], manifest = Config.NONE)
class StorageVolumesTest {
    private val application = RuntimeEnvironment.getApplication()
    private val storageManager = application.getSystemService(StorageManager::class.java)
    private val context = object : ContextWrapper(application) {
        override fun getExternalFilesDirs(type: String?): Array<File?> = arrayOf(application.getExternalFilesDir(type))
    }

    @Test
    fun `mounted USB without an app-specific directory is discovered`() {
        val usb = addVolume("USB", Environment.MEDIA_MOUNTED)
        assertTrue(context.getStorageVolumes().contains(usb))
    }

    @Test
    fun `read-only mounted volumes are included and unmounted volumes are excluded`() {
        val readOnly = addVolume("read-only", Environment.MEDIA_MOUNTED_READ_ONLY)
        val unmounted = addVolume("unmounted", Environment.MEDIA_UNMOUNTED)
        val volumes = context.getStorageVolumes()
        assertTrue(volumes.contains(readOnly))
        assertTrue(!volumes.contains(unmounted))
    }

    @Test
    fun `null legacy directory entries do not discard other volumes`() {
        val legacy = File(application.cacheDir, "legacy/Android/data/test/files").apply { mkdirs() }
        val legacyContext = object : ContextWrapper(application) {
            override fun getExternalFilesDirs(type: String?): Array<File?> = arrayOf(null, legacy)
        }
        assertTrue(legacyContext.getStorageVolumes().contains(File(application.cacheDir, "legacy")))
    }

    @Test
    @Config(sdk = [24, 29])
    fun `legacy storage roots remain available`() {
        val root = File(application.cacheDir, "legacy").apply { mkdirs() }
        val legacyContext = object : ContextWrapper(application) {
            override fun getExternalFilesDirs(type: String?): Array<File?> = arrayOf(File(root, "Android/data/test/files"))
        }
        assertEquals(listOf(root), legacyContext.getStorageVolumes())
    }

    private fun addVolume(name: String, state: String): File {
        val root = File(application.cacheDir, name).apply { mkdirs() }
        shadowOf(storageManager).addStorageVolume(
            StorageVolumeBuilder(name, root, name, Process.myUserHandle(), state)
                .setIsPrimary(false)
                .setIsRemovable(true)
                .build(),
        )
        return root
    }
}
