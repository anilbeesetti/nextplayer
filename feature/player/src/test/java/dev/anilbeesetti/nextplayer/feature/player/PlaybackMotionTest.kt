package dev.anilbeesetti.nextplayer.feature.player

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 36])
class PlaybackMotionTest {
    @Test fun respectsDisabledAndReducedAnimationScalesOnSupportedAndroidVersions() {
        val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
        val original = Settings.Global.getString(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
        )
        try {
            Settings.Global.putFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                0f,
            )
            assertFalse(playbackAnimationsEnabled(resolver))
            Settings.Global.putFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                .5f,
            )
            assertTrue(playbackAnimationsEnabled(resolver))
            Settings.Global.putString(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                null,
            )
            assertTrue(playbackAnimationsEnabled(resolver))
        } finally {
            Settings.Global.putString(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                original,
            )
        }
    }
}
