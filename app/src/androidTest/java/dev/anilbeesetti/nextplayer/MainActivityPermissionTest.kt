package dev.anilbeesetti.nextplayer

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityPermissionTest {

    @Test
    fun api24RequestsReadStoragePermission() {
        assertEquals(Manifest.permission.READ_EXTERNAL_STORAGE, storagePermission)
    }

    @Test
    fun launchWithoutStoragePermissionKeepsMainActivityAlive() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE),
        )

        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        SystemClock.sleep(1_000)

        val activityManager = context.getSystemService(ActivityManager::class.java)
        val topActivity = activityManager.appTasks.first().taskInfo?.topActivity
        assertEquals(MainActivity::class.java.name, topActivity?.className)
    }
}
