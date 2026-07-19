package dev.anilbeesetti.nextplayer.navigation

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NavBackStackTest {

    @Test
    fun removeLastIfNotRoot_removesTopEntryFromNestedStack() {
        val backStack = NavBackStack<NavKey>(Root, Detail)

        val removed = backStack.removeLastIfNotRoot()

        assertEquals(Detail, removed)
        assertEquals(listOf(Root), backStack)
    }

    @Test
    fun removeLastIfNotRoot_keepsRootEntry() {
        val backStack = NavBackStack<NavKey>(Root)

        val removed = backStack.removeLastIfNotRoot()

        assertNull(removed)
        assertEquals(listOf(Root), backStack)
    }

    @Test
    fun ensureRoot_restoresRootToEmptyStack() {
        val backStack = NavBackStack<NavKey>()

        backStack.ensureRoot(Root)

        assertEquals(listOf(Root), backStack)
    }

    private data object Root : NavKey

    private data object Detail : NavKey
}
