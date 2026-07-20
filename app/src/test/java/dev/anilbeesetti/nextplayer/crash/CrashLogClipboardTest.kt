package dev.anilbeesetti.nextplayer.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogClipboardTest {

    @Test
    fun reportAtClipboardLimitIsUnchanged() {
        val report = "x".repeat(MAX_CLIPBOARD_REPORT_CHARS)

        assertEquals(report, crashReportForClipboard(report))
    }

    @Test
    fun oversizedReportRetainsPrefixAndAddsTruncationMarkerWithinLimit() {
        val prefix = "Device info\nException:\nPrimary hide failure\n"
        val report = prefix + "x".repeat(MAX_CLIPBOARD_REPORT_CHARS)

        val bounded = crashReportForClipboard(report)

        assertEquals(MAX_CLIPBOARD_REPORT_CHARS, bounded.length)
        assertTrue(bounded.startsWith(prefix))
        assertTrue(bounded.endsWith(CLIPBOARD_REPORT_TRUNCATION_MARKER))
    }
}
