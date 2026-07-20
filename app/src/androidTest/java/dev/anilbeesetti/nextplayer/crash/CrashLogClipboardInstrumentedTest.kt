package dev.anilbeesetti.nextplayer.crash

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

private const val WINDOW_FOCUS_TIMEOUT_MS = 5_000L

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 36, maxSdkVersion = 36)
class CrashLogClipboardInstrumentedTest {

    @Test
    fun reportedFiveMegabytePayloadCopiesWhenCrashActivityHasWindowFocus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val prefix = "Primary hide failure\n"
        val report = prefix + "x".repeat(2_596_638)

        ActivityScenario.launch<CrashActivity>(
            Intent(context, CrashActivity::class.java)
                .putExtra("exception", "test")
        ).use { scenario ->
            scenario.awaitWindowFocus()

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                copyCrashReportToClipboard(clipboard, report)
            }

            val copied = clipboard.primaryClip?.getItemAt(0)?.text.toString()
            assertEquals(MAX_CLIPBOARD_REPORT_CHARS, copied.length)
            assertTrue(copied.startsWith(prefix))
            assertTrue(copied.endsWith(CLIPBOARD_REPORT_TRUNCATION_MARKER))
        }
    }
}

private fun ActivityScenario<CrashActivity>.awaitWindowFocus() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val deadline = SystemClock.uptimeMillis() + WINDOW_FOCUS_TIMEOUT_MS

    while (SystemClock.uptimeMillis() < deadline) {
        var hasWindowFocus = false
        onActivity { activity ->
            hasWindowFocus = activity.hasWindowFocus()
        }
        if (hasWindowFocus) return

        instrumentation.waitForIdleSync()
    }

    var hasWindowFocus = false
    onActivity { activity ->
        hasWindowFocus = activity.hasWindowFocus()
    }
    assertTrue("CrashActivity did not receive window focus", hasWindowFocus)
}
