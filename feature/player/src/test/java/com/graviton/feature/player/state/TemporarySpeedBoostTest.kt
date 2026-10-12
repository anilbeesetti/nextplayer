package com.graviton.feature.player.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporarySpeedBoostTest {

    private val applied = mutableListOf<Float>()
    private val boost = TemporarySpeedBoost(applySpeed = { applied += it })

    @Test
    fun releaseRestoresTheUsersConfiguredSpeedNotOne() {
        boost.start(currentSpeed = 1.25f, boostSpeed = 2f)
        assertTrue(boost.isActive)
        assertEquals(2f, boost.activeSpeed, 0f)

        boost.stop()
        assertFalse(boost.isActive)
        assertEquals(listOf(2f, 1.25f), applied)
    }

    @Test
    fun aSecondStartCannotOverwriteTheSpeedToRestore() {
        boost.start(currentSpeed = 1.5f, boostSpeed = 2f)
        boost.start(currentSpeed = 2f, boostSpeed = 2f)
        boost.stop()
        assertEquals(1.5f, applied.last(), 0f)
    }

    @Test
    fun stopWithoutAnActiveBoostDoesNothing() {
        boost.stop()
        assertTrue(applied.isEmpty())
    }

    @Test
    fun stopIsIdempotent() {
        boost.start(currentSpeed = 1f, boostSpeed = 2f)
        boost.stop()
        boost.stop()
        assertEquals(listOf(2f, 1f), applied)
    }

    @Test
    fun changingTheBoostSpeedAppliesOnlyWhileActiveAndOnlyWhenItChanges() {
        assertFalse(boost.changeSpeed(3f))
        boost.start(currentSpeed = 1f, boostSpeed = 2f)
        assertTrue(boost.changeSpeed(3f))
        assertFalse(boost.changeSpeed(3f))
        boost.stop()
        assertEquals(listOf(2f, 3f, 1f), applied)
    }
}
