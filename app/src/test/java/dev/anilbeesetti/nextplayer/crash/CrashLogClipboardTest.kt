package dev.anilbeesetti.nextplayer.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogClipboardTest {

    @Test
    fun reportAtClipboardLimitIsUnchanged() {
        val report = "x".repeat(n = MAX_CLIPBOARD_REPORT_CHARS)

        assertEquals(
            report,
            crashReportForClipboard(report = report),
        )
    }

    @Test
    fun oversizedReportRetainsPrefixAndAddsTruncationMarkerWithinLimit() {
        val prefix = "Device info\nException:\nPrimary hide failure\n"
        val report = prefix + "x".repeat(n = MAX_CLIPBOARD_REPORT_CHARS)

        val bounded = crashReportForClipboard(report = report)

        assertEquals(
            MAX_CLIPBOARD_REPORT_CHARS,
            bounded.length,
        )
        assertTrue(bounded.startsWith(prefix = prefix))
        assertTrue(bounded.endsWith(suffix = CLIPBOARD_REPORT_TRUNCATION_MARKER))
    }
}
