package dev.anilbeesetti.nextplayer

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabKey
import dev.anilbeesetti.nextplayer.core.ui.components.TopLevelFabState
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import dev.anilbeesetti.nextplayer.feature.player.service.stopPlayerSession
import dev.anilbeesetti.nextplayer.navigation.rememberTopLevelNavState
import java.util.concurrent.TimeUnit
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationLayoutFabFocusTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun requireTv() {
        assumeTrue(composeRule.activity.isTelevision)
    }

    @Test
    fun upEntersContentEvenWhenItsFocusGroupOverlapsFab() {
        showScreen()
        upFromFab()
        composeRule.onNodeWithText(text = "Second item").assertIsFocused()
    }

    @Test
    fun upRestoresThePreviousItemWithinContent() {
        showScreen()
        composeRule.onNodeWithText(text = "First item").requestFocus()
        upFromFab()
        composeRule.onNodeWithText(text = "First item").assertIsFocused()
    }

    @Test
    fun emptyContentFallsBackToTheScreenToolbar() {
        showScreen(empty = true)
        upFromFab()
        composeRule.onNodeWithText(text = "Settings").assertIsFocused()
    }

    @Test
    fun downFromTheLastItemCanStillReachFab() {
        showScreen()
        composeRule.onNodeWithText(text = "First item").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText(text = "Second item").assertIsFocused()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithTag(testTag = "top_level_fab").assertIsFocused()
    }

    @Test
    fun leftFromContentCanStillReachNavigationRail() {
        showScreen()
        composeRule.onNodeWithText(text = "Second item").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNode(
            matcher =
                isFocused() and SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Tab,
                ),
        ).assertExists()
    }

    @Test
    fun loadedMiniPlayerConnectsContentAndFabFocusInBothDirections() {
        showScreen()
        val context = composeRule.activity
        val future = composeRule.runOnIdle {
            MediaController.Builder(
                context,
                SessionToken(
                    context,
                    ComponentName(
                        context,
                        PlayerService::class.java,
                    ),
                ),
            ).buildAsync()
        }
        val player = future.get(
            15,
            TimeUnit.SECONDS,
        )
        try {
            composeRule.runOnIdle {
                player.setMediaItem(
                    MediaItem.Builder().setMediaId("file:///focus-mini.mp4")
                        .setUri("file:///focus-mini.mp4").build(),
                )
            }
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag("mini_player").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Second item").requestFocus()
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.onNodeWithTag("mini_player").assertIsFocused()
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            composeRule.onNodeWithTag("top_level_fab").assertIsFocused()
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            composeRule.onNodeWithTag("mini_player").assertIsFocused()
            composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            composeRule.onNodeWithText("Second item").assertIsFocused()
        } finally {
            composeRule.runOnIdle {
                player.stopPlayerSession()
                MediaController.releaseFuture(future)
            }
        }
    }

    private fun showScreen(empty: Boolean = false) {
        composeRule.setContent {
            NextPlayerTheme {
                val state = rememberTopLevelNavState()
                val fabs = remember {
                    mutableStateMapOf(
                        TopLevelFabKey.MEDIA to TopLevelFabState(
                            NextIcons.Play,
                            onClick = {},
                        ),
                    )
                }
                NavigationLayout(
                    state = state,
                    fabStates = fabs,
                    showBottomBar = true,
                ) {
                    Column(
                        Modifier.fillMaxSize().padding(
                            start = it.calculateStartPadding(LocalLayoutDirection.current),
                        ),
                    ) {
                        Button(onClick = {}) { Text(text = "Settings") }
                        Box(Modifier.fillMaxSize()) {
                            if (!empty) {
                                Column(Modifier.fillMaxSize().focusRestorer().focusGroup()) {
                                    Button(onClick = {}) { Text(text = "First item") }
                                    Button(onClick = {}) { Text(text = "Second item") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun upFromFab() {
        composeRule.onNodeWithTag(testTag = "top_level_fab").requestFocus()
        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
    }
}
