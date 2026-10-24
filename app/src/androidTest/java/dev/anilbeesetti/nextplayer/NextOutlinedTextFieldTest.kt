package dev.anilbeesetti.nextplayer

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision
import dev.anilbeesetti.nextplayer.core.ui.components.NextOutlinedTextField
import dev.anilbeesetti.nextplayer.core.ui.theme.NextPlayerTheme
import dev.anilbeesetti.nextplayer.feature.videopicker.composables.RenameDialog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NextOutlinedTextFieldTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun dpadCanMoveThroughFieldsWithoutOpeningTheKeyboard() {
        assumeTrue(composeRule.activity.isTelevision)
        showForm()
        composeRule.onNodeWithText(text = "Before").requestFocus()
        press(Key.DirectionDown)
        composeRule.onNodeWithTag(testTag = "first").assertIsFocused()
        assertKeyboardStaysHidden()
        press(Key.DirectionDown)
        composeRule.onNodeWithTag(testTag = "second").assertIsFocused()
        assertKeyboardStaysHidden()
        press(Key.DirectionDown)
        composeRule.onNodeWithText(text = "After").assertIsFocused()
    }

    @Test
    fun centerAndEnterOpenTheKeyboardAndCanReopenItAfterBack() {
        assumeTrue(composeRule.activity.isTelevision)
        showForm()
        composeRule.onNodeWithTag(testTag = "first").requestFocus()
        assertKeyboardStaysHidden()
        listOf(
            Key.DirectionCenter,
            Key.Enter,
            Key.NumPadEnter,
        ).forEach { key ->
            press(key = key)
            waitForKeyboard(shown = true)
            pressBack()
            waitForKeyboard(shown = false)
            composeRule.onNodeWithTag(testTag = "first").assertIsFocused()
        }
        composeRule.onNodeWithTag(testTag = "first").assertTextEquals("")
    }

    @Test
    fun returningToAnEditedFieldRequiresAnotherClick() {
        assumeTrue(composeRule.activity.isTelevision)
        showForm()
        composeRule.onNodeWithTag(testTag = "first").requestFocus()
        press(Key.DirectionCenter)
        waitForKeyboard(shown = true)
        composeRule.onNodeWithTag(testTag = "first").performTextInput(text = "Clip")
        pressBack()
        waitForKeyboard(shown = false)
        press(Key.DirectionDown)
        composeRule.onNodeWithTag(testTag = "second").assertIsFocused()
        assertKeyboardStaysHidden()
        press(Key.DirectionUp)
        composeRule.onNodeWithTag(testTag = "first").assertIsFocused().assertTextEquals("Clip")
        assertKeyboardStaysHidden()
        press(Key.DirectionCenter)
        waitForKeyboard(shown = true)
    }

    @Test
    fun renameDialogAutofocusDoesNotOpenKeyboard() {
        assumeTrue(composeRule.activity.isTelevision)
        composeRule.setContent {
            NextPlayerTheme {
                RenameDialog(
                    name = "Original",
                    onDismiss = {},
                    onDone = {},
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(
                androidx.compose.ui.test.isFocused(),
            ).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text = "Original").assertIsFocused()
        assertKeyboardStaysHidden()
        composeRule.onNodeWithText(text = "Original").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        waitForKeyboard(shown = true)
        composeRule.onNodeWithText(text = "Original").performTextInput(text = " edited")
        composeRule.onNodeWithText(text = "Original edited").assertIsFocused()
        pressBack()
        waitForKeyboard(shown = false)
        composeRule.onNodeWithText(text = "Original edited").assertIsFocused()
    }

    @Test
    fun trailingButtonKeepsItsOwnDpadClick() {
        assumeTrue(composeRule.activity.isTelevision)
        var clicked = false
        composeRule.setContent {
            NextPlayerTheme {
                NextOutlinedTextField(
                    value = "Query",
                    onValueChange = {},
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                clicked = true
                            },
                            modifier = Modifier.testTag(tag = "clear"),
                        ) { Text(text = "Clear") }
                    },
                )
            }
        }
        composeRule.onNodeWithTag(testTag = "clear").requestFocus()
        press(Key.DirectionCenter)
        composeRule.runOnIdle { assertTrue(clicked) }
        assertKeyboardStaysHidden()
    }

    @Test
    fun phoneFocusStartsEditingWithoutConfirmation() {
        assumeFalse(composeRule.activity.isTelevision)
        showForm()
        composeRule.onNodeWithTag(testTag = "first").requestFocus()
        waitForKeyboard(shown = true)
        composeRule.onNodeWithTag(testTag = "first").performTextInput(text = "Phone input")
        composeRule.onNodeWithTag(testTag = "first").assertTextEquals("Phone input")
    }

    private fun showForm() {
        composeRule.setContent {
            NextPlayerTheme {
                var first by remember { mutableStateOf(value = "") }
                var second by remember { mutableStateOf(value = "") }
                Column {
                    Button(onClick = {}) { Text(text = "Before") }
                    NextOutlinedTextField(
                        value = first,
                        onValueChange = { first = it },
                        singleLine = true,
                        modifier = Modifier.testTag(tag = "first"),
                    )
                    NextOutlinedTextField(
                        value = second,
                        onValueChange = { second = it },
                        singleLine = true,
                        modifier = Modifier.testTag(tag = "second"),
                    )
                    Button(onClick = {}) { Text(text = "After") }
                }
            }
        }
    }

    private fun press(key: Key) {
        composeRule.onRoot().performKeyInput { pressKey(key = key) }
        composeRule.waitForIdle()
    }

    private fun assertKeyboardStaysHidden() {
        // IME visibility changes asynchronously, after Compose has become idle.
        SystemClock.sleep(500)
        assertFalse(
            "Focus alone must not open the TV keyboard",
            keyboardIsShown(),
        )
    }

    private fun waitForKeyboard(shown: Boolean) {
        composeRule.waitUntil(timeoutMillis = 5_000) { keyboardIsShown() == shown }
    }

    private fun keyboardIsShown(): Boolean {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val output = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand("dumpsys input_method"),
        )
            .bufferedReader().use { it.readText() }
        check(value = "mInputShown=" in output) {
            "Keyboard visibility missing from input_method dump"
        }
        return "mInputShown=true" in output
    }
}
