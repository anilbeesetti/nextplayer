package dev.anilbeesetti.nextplayer.benchmarks.baselineprofile

import androidx.benchmark.macro.ExperimentalBaselineProfilesApi
import androidx.benchmark.macro.junit4.BaselineProfileRule
import dev.anilbeesetti.nextplayer.benchmarks.PACKAGE_NAME
import dev.anilbeesetti.nextplayer.benchmarks.PERMISSION_MEDIA_VIDEO
import dev.anilbeesetti.nextplayer.benchmarks.grantPermission
import dev.anilbeesetti.nextplayer.benchmarks.videopicker.videoPickerScrollDownUp
import dev.anilbeesetti.nextplayer.benchmarks.videopicker.videoPickerSwitchToVideosView
import dev.anilbeesetti.nextplayer.benchmarks.videopicker.videoPickerWaitForContent
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalBaselineProfilesApi::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collectBaselineProfile(PACKAGE_NAME) {
        grantPermission(PERMISSION_MEDIA_VIDEO)
        pressHome()
        startActivityAndWait()

        videoPickerWaitForContent()
        videoPickerSwitchToVideosView()
        videoPickerScrollDownUp()
    }
}