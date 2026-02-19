package dev.anilbeesetti.nextplayer.core.common

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoragePermissionTest {

    @Test
    fun `resolveStoragePermissions returns video and selected permissions on android 14 plus`() {
        val permissions = resolveStoragePermissions(sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)

        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ),
            permissions,
        )
    }

    @Test
    fun `hasFullStoragePermission returns true when read media video is granted`() {
        val hasFullAccess = hasFullStoragePermission(
            permissionGrants = mapOf(
                Manifest.permission.READ_MEDIA_VIDEO to true,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED to false,
            ),
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        )

        assertTrue(hasFullAccess)
    }

    @Test
    fun `hasLimitedStoragePermission returns true when only selected media access is granted`() {
        val hasLimitedAccess = hasLimitedStoragePermission(
            permissionGrants = mapOf(
                Manifest.permission.READ_MEDIA_VIDEO to false,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED to true,
            ),
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        )

        assertTrue(hasLimitedAccess)
    }

    @Test
    fun `hasLimitedStoragePermission returns false when full access is granted`() {
        val hasLimitedAccess = hasLimitedStoragePermission(
            permissionGrants = mapOf(
                Manifest.permission.READ_MEDIA_VIDEO to true,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED to true,
            ),
            sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
        )

        assertFalse(hasLimitedAccess)
    }
}
