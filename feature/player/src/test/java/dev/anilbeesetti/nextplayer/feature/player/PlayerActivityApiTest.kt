package dev.anilbeesetti.nextplayer.feature.player

import android.app.Activity
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlayerActivityApiTest {
    @Test
    fun newIntentReplacesResultPreferenceAndMediaUri() {
        val controller = Robolectric.buildActivity(PlayerActivity::class.java)
        val activity = controller.get()
        controller.newIntent(
            request("one").putExtra(
                "return_result",
                false,
            ),
        )
        controller.newIntent(
            request("two").putExtra(
                "return_result",
                true,
            ),
        )
        activity.finish()
        assertEquals(
            Uri.parse("https://host/two.mp4"),
            shadowOf(activity).resultIntent.data,
        )
        assertEquals(
            Activity.RESULT_CANCELED,
            shadowOf(activity).resultCode,
        )
    }

    @Test
    fun laterRequestDoesNotInheritResultOptIn() {
        val controller = Robolectric.buildActivity(PlayerActivity::class.java)
        val activity = controller.get()
        controller.newIntent(
            request("one").putExtra(
                "return_result",
                true,
            ),
        )
        controller.newIntent(request("two"))
        activity.finish()
        assertNull(shadowOf(activity).resultIntent)
    }

    @Test
    fun backgroundLaunchWithoutMediaReturnsErrorWithoutCreatingPlayerUi() {
        val intent = Intent().putExtra(
            "background",
            true,
        ).putExtra(
            "return_result",
            true,
        )
        val controller = Robolectric.buildActivity(
            PlayerActivity::class.java,
            intent,
        ).create()
        try {
            val activity = controller.get()
            assertTrue(activity.isFinishing)
            assertEquals(
                Activity.RESULT_FIRST_USER,
                shadowOf(activity).resultCode,
            )
            assertNull(shadowOf(activity).resultIntent.data)
            activity.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent)).use {
                assertTrue(
                    it.getBoolean(
                        0,
                        false,
                    ),
                )
            }
        } finally {
            controller.destroy()
        }
    }

    private fun request(name: String) = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("https://host/$name.mp4"),
    )
}
