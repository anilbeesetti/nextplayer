package com.graviton.core.datastore.serializer

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplicationPreferencesGlassUiTest {

    @Test
    fun `preferences written before glass UI existed decode with glass off`() = runTest {
        // Stored JSON from an older app version has no useGlassUi key.
        val legacyJson = """{"sortBy":"TITLE","themeConfig":"SYSTEM"}"""
        val decoded = ApplicationPreferencesSerializer.readFrom(ByteArrayInputStream(legacyJson.toByteArray()))

        assertFalse(decoded.useGlassUi)
    }

    @Test
    fun `glass UI preference survives a write and read round trip`() = runTest {
        val enabled = ApplicationPreferencesSerializer.defaultValue.copy(useGlassUi = true)
        val output = ByteArrayOutputStream()
        ApplicationPreferencesSerializer.writeTo(enabled, output)

        val decoded = ApplicationPreferencesSerializer.readFrom(ByteArrayInputStream(output.toByteArray()))

        assertTrue(decoded.useGlassUi)
    }
}
