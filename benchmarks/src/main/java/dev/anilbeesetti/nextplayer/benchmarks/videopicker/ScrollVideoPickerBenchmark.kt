package dev.anilbeesetti.nextplayer.benchmarks.videopicker

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner
import dev.anilbeesetti.nextplayer.benchmarks.PACKAGE_NAME
import dev.anilbeesetti.nextplayer.benchmarks.PERMISSION_MEDIA_VIDEO
import dev.anilbeesetti.nextplayer.benchmarks.grantPermission
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4ClassRunner::class)
class ScrollVideoPickerBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollFeedCompilationNone() = scrollVideoPicker(CompilationMode.None())

    @Test
    fun scrollFeedCompilationBaselineProfile() = scrollVideoPicker(CompilationMode.Partial())

    private fun scrollVideoPicker(compilationMode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 2,
        startupMode = StartupMode.COLD,
        setupBlock = {
            // Grant read external storage permission
            grantPermission(PERMISSION_MEDIA_VIDEO)
            // Start the app
            pressHome()
            startActivityAndWait()
        }
    ) {
        videoPickerWaitForContent()
        videoPickerSwitchToVideosView()
        videoPickerScrollDownUp()
    }
}
