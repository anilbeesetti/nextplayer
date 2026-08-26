package com.graviton.feature.music.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AutoEqImporterTest {
    @Test fun parsesGraphicEqAndProjectsBands() {
        val profile = AutoEqImporter.parse("Headphones", "GraphicEQ: 20 -2.0; 100 1.0; 1000 3.0; 10000 -1.0")
        assertNotNull(profile)
        assertEquals(4, profile?.points?.size)
        val projected = AutoEqImporter.project(profile!!, intArrayOf(20, 100, 1_000, 10_000))
        assertArrayEquals(floatArrayOf(-2f, 1f, 3f, -1f), projected, 0.001f)
    }

    @Test fun rejectsMalformedProfile() {
        assertEquals(null, AutoEqImporter.parse("bad", "not an equalizer"))
    }
}
