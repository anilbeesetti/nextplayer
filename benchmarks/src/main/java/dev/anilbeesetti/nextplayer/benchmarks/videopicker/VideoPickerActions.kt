package dev.anilbeesetti.nextplayer.benchmarks.videopicker

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import dev.anilbeesetti.nextplayer.benchmarks.flingElementDownUp

fun MacrobenchmarkScope.videoPickerWaitForContent() {
    // Wait until content is loaded by checking if topics are loaded
    device.wait(Until.gone(By.res("circularProgressIndicator")), 5_000)
}

fun MacrobenchmarkScope.videoPickerSwitchToVideosView() {
    device.findObject(By.res("videoPicker:quickSettings")).click()
    device.wait(Until.hasObject(By.text("Quick Settings")), 5000)
    val groupVideosToggle = device.findObject(By.text("Group videos by folder"))
    groupVideosToggle.click()
    device.waitForIdle()
    device.findObject(By.text("Done")).click()
}

fun MacrobenchmarkScope.videoPickerScrollDownUp() {
    val feedList = device.findObject(By.res("videoPicker:feed"))
    device.flingElementDownUp(feedList)
}
