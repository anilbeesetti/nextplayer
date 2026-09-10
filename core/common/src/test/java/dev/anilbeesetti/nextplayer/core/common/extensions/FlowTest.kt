package dev.anilbeesetti.nextplayer.core.common.extensions

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowTest {
    @Test
    fun `collectors stop after five seconds and resume with current data`() = runTest {
        val source = MutableStateFlow(1)
        val state = MutableStateFlow(0)
        val exposedState = state.asStateFlow()
        source.collectWhileSubscribed(backgroundScope, state) { state.value = it }
        runCurrent()
        assertEquals(0, source.subscriptionCount.value)

        val subscriber = backgroundScope.launch { exposedState.collect {} }
        runCurrent()
        assertEquals(1, source.subscriptionCount.value)
        assertEquals(1, state.value)
        subscriber.cancel()
        runCurrent()
        advanceTimeBy(4_999)
        runCurrent()
        assertEquals(1, source.subscriptionCount.value)
        source.value = 2
        runCurrent()
        assertEquals(2, state.value)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(0, source.subscriptionCount.value)
        source.value = 3
        runCurrent()
        assertEquals(2, state.value)

        backgroundScope.launch { exposedState.collect {} }
        runCurrent()
        assertEquals(1, source.subscriptionCount.value)
        assertEquals(3, state.value)
    }

    @Test
    fun `returning within the grace period does not restart the source`() = runTest {
        val state = MutableStateFlow(0)
        var starts = 0
        var stops = 0
        flow {
            starts++
            try {
                emit(1)
                awaitCancellation()
            } finally {
                stops++
            }
        }.collectWhileSubscribed(backgroundScope, state) { state.value = it }
        val first = backgroundScope.launch { state.asStateFlow().collect {} }
        runCurrent()
        first.cancel()
        runCurrent()
        advanceTimeBy(4_999)
        backgroundScope.launch { state.asStateFlow().collect {} }
        runCurrent()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(1, starts)
        assertEquals(0, stops)
    }

    @Test
    fun `multiple state subscribers share one source collection`() = runTest {
        val source = MutableStateFlow(1)
        val state = MutableStateFlow(0)
        val observation = source.collectWhileSubscribed(backgroundScope, state) { state.value = it }
        val first = backgroundScope.launch { state.asStateFlow().collect {} }
        backgroundScope.launch { state.asStateFlow().collect {} }
        runCurrent()
        assertEquals(1, source.subscriptionCount.value)

        first.cancel()
        advanceTimeBy(5_001)
        runCurrent()
        assertEquals(1, source.subscriptionCount.value)

        observation.cancel()
        runCurrent()
        assertEquals(0, source.subscriptionCount.value)
        assertFalse(observation.isActive)
    }
}
