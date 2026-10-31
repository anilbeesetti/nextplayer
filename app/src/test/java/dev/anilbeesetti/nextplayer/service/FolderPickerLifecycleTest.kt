package dev.anilbeesetti.nextplayer.service

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import dev.anilbeesetti.nextplayer.core.common.service.registerForSuspendActivityResult
import dev.anilbeesetti.nextplayer.core.common.service.system.LocalSystemService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FolderPickerLifecycleTest {
    @Test
    fun `destroyed activity is not used and returning activity can pick`(): Unit = runBlocking {
        val first = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val service = LocalSystemService(first.get().applicationContext)
        service.initialize(first.get())
        first.start().resume()
        val second = Robolectric.buildActivity(ComponentActivity::class.java).create()
        service.initialize(second.get())
        second.start().resume().pause().stop().destroy()

        assertNull(service.pickFolder())
        first.pause().resume()
        val result = async(start = CoroutineStart.UNDISPATCHED) { service.pickFolder() }
        val request = shadowOf(first.get()).nextStartedActivityForResult
        assertEquals(
            "android.intent.action.OPEN_DOCUMENT_TREE",
            request.intent.action,
        )
        first.get().activityResultRegistry.dispatchResult(
            request.requestCode,
            Activity.RESULT_CANCELED,
            null,
        )
        assertNull(result.await())
        first.pause().stop().destroy()
    }

    @Test
    fun `destroying activity cancels pending request`(): Unit = runBlocking {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val launcher = controller.get().registerForSuspendActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        )
        controller.start().resume()
        val result = async(start = CoroutineStart.UNDISPATCHED) { launcher.launch(null) }
        assertTrue(launcher.isAwaitingResult)

        controller.pause().stop().destroy()

        result.join()
        assertTrue(result.isCancelled)
        assertFalse(launcher.isAwaitingResult)
    }

    @Test
    fun `canceled request cannot deliver its result to a later request`(): Unit = runBlocking {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val launcher = controller.get().registerForSuspendActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        )
        controller.start().resume()
        val first = async(start = CoroutineStart.UNDISPATCHED) { launcher.launch(null) }
        val request = shadowOf(controller.get()).nextStartedActivityForResult
        first.cancel()
        assertFalse(launcher.isAwaitingResult)
        try {
            launcher.launch(null)
            fail("An outstanding platform request must not be replaced")
        } catch (_: CancellationException) {
        }

        controller.get().activityResultRegistry.dispatchResult(
            request.requestCode,
            Activity.RESULT_CANCELED,
            null,
        )
        val next = async(start = CoroutineStart.UNDISPATCHED) { launcher.launch(null) }
        val nextRequest = shadowOf(controller.get()).nextStartedActivityForResult
        controller.get().activityResultRegistry.dispatchResult(
            nextRequest.requestCode,
            Activity.RESULT_CANCELED,
            null,
        )
        assertNull(next.await())
        controller.pause().stop().destroy()
    }

    @Test
    fun `launch after owner destruction cancels without using registry`(): Unit = runBlocking {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        val launcher = controller.get().registerForSuspendActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        )
        controller.start().resume().pause().stop().destroy()

        val result = async(start = CoroutineStart.UNDISPATCHED) { launcher.launch(null) }
        result.join()

        assertTrue(result.isCancelled)
        assertNull(shadowOf(controller.get()).nextStartedActivityForResult)
    }

    @Test
    fun `launch failure does not leave a pending request`(): Unit = runBlocking {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create()
        var attempts = 0
        val launcher = controller.get().registerForSuspendActivityResult(
            object : ActivityResultContract<Unit, Unit>() {
                override fun createIntent(context: Context, input: Unit): Intent {
                    attempts++
                    throw IllegalArgumentException("Cannot create picker intent")
                }

                override fun parseResult(resultCode: Int, intent: Intent?) = Unit
            },
        )
        controller.start().resume()

        repeat(times = 2) {
            try {
                launcher.launch(Unit)
                fail("Intent creation must fail")
            } catch (_: IllegalArgumentException) {
            }
            assertFalse(launcher.isAwaitingResult)
        }
        assertEquals(
            2,
            attempts,
        )
        controller.pause().stop().destroy()
    }
}
