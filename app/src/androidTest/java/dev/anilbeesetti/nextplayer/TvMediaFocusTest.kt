package dev.anilbeesetti.nextplayer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.requestFocus
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.domain.MediaHolder
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.MediaLayoutMode
import dev.anilbeesetti.nextplayer.core.model.Video
import dev.anilbeesetti.nextplayer.core.ui.components.rememberRestorableFocusState
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.MediaView
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TvMediaFocusTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var videos by mutableStateOf(clips(48))
    private var showContent by mutableStateOf(true)

    @Before
    fun requireTv() = assumeTrue(composeRule.activity.isTelevision)

    @Test
    fun toolbarReturnsToPreviouslyFocusedGridItem() {
        composeRule.setContent { Content(MediaLayoutMode.GRID) }
        focused("Clip 01")
        press(Key.DirectionRight)
        focused("Clip 02")
        composeRule.onNodeWithText("Toolbar").requestFocus()
        press(Key.DirectionDown)
        focused("Clip 02")
    }

    @Test
    fun longListScrollsBeforeExitingToActionsAndReturnsToLastItem() {
        composeRule.setContent { Content() }
        focused("Clip 01")
        repeat(47) { index ->
            press(Key.DirectionDown)
            focused("Clip %02d".format(index + 2))
        }
        press(Key.DirectionDown)
        focused("Action")
        press(Key.DirectionRight)
        focused("Second action")
        press(Key.DirectionUp)
        focused("Clip 48")
    }

    @Test
    fun recreatedDestinationRestoresScrolledItem() {
        composeRule.setContent { Content() }
        focused("Clip 01")
        repeat(18) { press(Key.DirectionDown) }
        focused("Clip 19")
        composeRule.runOnIdle { showContent = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { showContent = true }
        focused("Clip 19")
    }

    @Test
    fun savedStateRestoresScrolledItem() {
        val restoration = StateRestorationTester(composeRule)
        restoration.setContent { Content() }
        focused("Clip 01")
        repeat(18) { press(Key.DirectionDown) }
        focused("Clip 19")
        restoration.emulateSavedInstanceStateRestore()
        focused("Clip 19")
    }

    @Test
    fun resumingAfterAnotherActivityRestoresTheMediaItem() {
        composeRule.setContent { Content() }
        focused("Clip 01")
        repeat(18) { press(Key.DirectionDown) }
        focused("Clip 19")
        composeRule.onNodeWithText("Toolbar").requestFocus()
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        focused("Clip 19")
    }

    @Test
    fun removedRestorationTargetFallsBackToAvailableContent() {
        composeRule.setContent { Content() }
        focused("Clip 01")
        press(Key.DirectionDown)
        focused("Clip 02")
        composeRule.runOnIdle { showContent = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            videos = videos.filterNot { it.id == 2L }
            showContent = true
        }
        focused("Clip 01")
    }

    @Test
    fun delayedContentGetsInitialFocusAndUpdatesDoNotStealIt() {
        videos = emptyList()
        composeRule.setContent { Content() }
        composeRule.runOnIdle { videos = clips(3) }
        focused("Clip 01")
        composeRule.onNodeWithText("Toolbar").requestFocus()
        composeRule.runOnIdle { videos = clips(6) }
        composeRule.onNodeWithText("Toolbar").assertIsFocused()
    }

    @Test
    fun searchResultsCanArriveWithoutTakingFocusFromTheQuery() {
        videos = emptyList()
        composeRule.setContent { Content(autoFocus = false) }
        composeRule.onNodeWithText("Toolbar").requestFocus()
        composeRule.runOnIdle { videos = clips(3) }
        composeRule.onNodeWithText("Toolbar").assertIsFocused()
        press(Key.DirectionDown)
        focused("Clip 01")
    }

    @Composable
    private fun Content(layout: MediaLayoutMode = MediaLayoutMode.LIST, autoFocus: Boolean = true) {
        val holder = rememberSaveableStateHolder()
        NextPlayerTheme {
            if (showContent) {
                holder.SaveableStateProvider("media") {
                    val focus = rememberRestorableFocusState()
                    val firstAction = remember { FocusRequester() }
                    Column(Modifier.fillMaxSize()) {
                        Button(
                            modifier = Modifier.focusProperties { down = focus.requester },
                            onClick = {},
                        ) { Text("Toolbar") }
                        Box(Modifier.weight(1f)) {
                            MediaView(
                                mediaHolder = MediaHolder(videos = videos, folders = emptyList()),
                                recentlyPlayedVideo = null,
                                recentlyPlayedFolder = null,
                                preferences = ApplicationPreferences(
                                    mediaLayoutMode = layout,
                                    showExtensionField = false,
                                ),
                                showHeaders = false,
                                focusState = focus,
                                autoFocus = autoFocus,
                                onFolderClick = {},
                                onVideoClick = {},
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().focusRestorer(fallback = firstAction).focusGroup()
                                .focusProperties { up = focus.requester },
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Button(modifier = Modifier.focusRequester(firstAction), onClick = {}) { Text("Action") }
                            Button(onClick = {}) { Text("Second action") }
                            Button(onClick = {}) { Text("Third action") }
                        }
                    }
                }
            } else {
                Button(onClick = {}) { Text("Other destination") }
            }
        }
    }

    private fun focused(text: String) {
        try {
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText(text) and isFocused()).fetchSemanticsNodes().size == 1
            }
        } catch (failure: Throwable) {
            throw AssertionError("Expected focus on $text\n${composeRule.onRoot().printToString()}", failure)
        }
    }

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key) }
        composeRule.waitForIdle()
    }

    private fun clips(count: Int) = (1..count).map { index ->
        Video.sample.copy(
            id = index.toLong(),
            nameWithExtension = "Clip %02d.mp4".format(index),
            uriString = "content://media/external/video/media/$index",
            path = "/storage/emulated/0/Movies/Clip $index.mp4",
        )
    }
}
