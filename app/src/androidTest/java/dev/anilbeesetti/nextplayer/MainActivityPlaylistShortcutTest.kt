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
        val first = repository.create(name = "Shortcut first")
        val second = repository.create(name = "Shortcut second")
        try {
            val firstIntent = PlaylistShortcut.create(
                context = context,
                playlist = summary(
                    id = first,
                    name = "Shortcut first",
                ),
            ).intent
            ActivityScenario.launch<MainActivity>(firstIntent).use { scenario ->
                awaitText(text = "Shortcut first")
                composeRule.onNodeWithText(text = "This playlist is empty").assertIsDisplayed()

                val secondIntent = PlaylistShortcut.create(
                    context = context,
                    playlist = summary(
                        id = second,
                        name = "Shortcut second",
                    ),
                ).intent
                context.startActivity(secondIntent)
                awaitText(text = "Shortcut second")
                scenario.recreate()
                awaitText(text = "Shortcut second")

                composeRule.onNodeWithContentDescription(label = "Navigate up").performClick()
                awaitText(text = "Playlists")
                // The consumed shortcut must not reopen when the activity is recreated on the list.
                scenario.recreate()
                awaitText(text = "Playlists")

                context.startActivity(secondIntent)
                awaitText(text = "Shortcut second")
                repository.delete(playlistId = second)
                awaitText(text = "This playlist is no longer available.")
            }
        } finally {
            repository.delete(playlistId = first)
            repository.delete(playlistId = second)
        }
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(text = text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text = text).assertIsDisplayed()
    }

    private fun summary(id: Long, name: String) = PlaylistSummary(
        id = id,
        name = name,
        PlaylistType.LOCAL,
        itemCount = 0,
        lastRefreshedAt = null,
    )
}
