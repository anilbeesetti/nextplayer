package dev.anilbeesetti.nextplayer

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import dev.anilbeesetti.nextplayer.core.common.storagePermission
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.model.PlaylistSummary
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext

@RunWith(AndroidJUnit4::class)
class MainActivityPlaylistShortcutTest {
    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(storagePermission)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Test
    fun shortcutOpensPlaylistOnColdAndWarmLaunchAndSurvivesRecreation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = GlobalContext.get().get<PlaylistRepository>()
        val first = repository.create("Shortcut first")
        val second = repository.create("Shortcut second")
        try {
            val firstIntent = PlaylistShortcut.create(context, summary(first, "Shortcut first")).intent
            ActivityScenario.launch<MainActivity>(firstIntent).use { scenario ->
                awaitText("Shortcut first")
                composeRule.onNodeWithText("This playlist is empty").assertIsDisplayed()

                val secondIntent = PlaylistShortcut.create(context, summary(second, "Shortcut second")).intent
                context.startActivity(secondIntent)
                awaitText("Shortcut second")
                scenario.recreate()
                awaitText("Shortcut second")

                composeRule.onNodeWithContentDescription("Navigate up").performClick()
                awaitText("Playlists")
                // The consumed shortcut must not reopen when the activity is recreated on the list.
                scenario.recreate()
                awaitText("Playlists")

                context.startActivity(secondIntent)
                awaitText("Shortcut second")
                repository.delete(second)
                awaitText("This playlist is no longer available.")
            }
        } finally {
            repository.delete(first)
            repository.delete(second)
        }
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(30_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun summary(id: Long, name: String) = PlaylistSummary(id, name, PlaylistType.LOCAL, 0, null)
}
