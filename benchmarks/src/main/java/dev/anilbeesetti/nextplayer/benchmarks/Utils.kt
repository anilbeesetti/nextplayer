package dev.anilbeesetti.nextplayer.benchmarks

import android.Manifest
import android.os.Build
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2

/**
 * Convenience parameter to use proper package name with regards to build type.
 */
val PACKAGE_NAME = buildString {
    append("dev.anilbeesetti.nextplayer")
    append(".benchmark")
}

val PERMISSION_MEDIA_VIDEO = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    Manifest.permission.READ_MEDIA_VIDEO
} else {
    Manifest.permission.READ_EXTERNAL_STORAGE
}

fun UiDevice.flingElementDownUp(element: UiObject2) {
    // Set some margin from the sides to prevent triggering system navigation
    element.setGestureMargin(displayWidth / 5)

    element.fling(Direction.DOWN)
    waitForIdle()
    element.fling(Direction.UP)
}

fun MacrobenchmarkScope.grantPermission(permission: String) {
    device.executeShellCommand("pm grant $PACKAGE_NAME $permission")
}
