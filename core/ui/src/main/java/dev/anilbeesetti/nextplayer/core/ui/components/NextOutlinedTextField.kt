package dev.anilbeesetti.nextplayer.core.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import dev.anilbeesetti.nextplayer.core.common.extensions.isTelevision

/** On TV, focus selects the field; a remote click starts editing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NextOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    shape: Shape = OutlinedTextFieldDefaults.shape,
    colors: TextFieldColors = OutlinedTextFieldDefaults.colors(),
) {
    val isTv = LocalContext.current.isTelevision
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isKeyboardVisible = WindowInsets.isImeVisible
    var isFocused by remember { mutableStateOf(false) }
    var isEditing by remember(enabled) { mutableStateOf(false) }
    val inputModifier = if (isTv) {
        Modifier
            .onFocusChanged {
                isFocused = it.isFocused
                if (!it.isFocused) isEditing = false
            }
            .onPreviewKeyEvent {
                if (!enabled || !isFocused) return@onPreviewKeyEvent false
                val isSelect = it.key == Key.DirectionCenter || it.key == Key.Enter || it.key == Key.NumPadEnter
                val direction = when (it.key) {
                    Key.DirectionUp -> FocusDirection.Up
                    Key.DirectionDown -> FocusDirection.Down
                    Key.DirectionLeft -> FocusDirection.Left
                    Key.DirectionRight -> FocusDirection.Right
                    else -> null
                }
                if (isSelect) {
                    if (it.type == KeyEventType.KeyUp) {
                        isEditing = true
                        keyboardController?.show()
                    }
                    true
                } else if (!isKeyboardVisible && direction != null) {
                    // A text field otherwise consumes arrows as cursor movement, even read-only.
                    if (it.type == KeyEventType.KeyDown) focusManager.moveFocus(direction)
                    true
                } else {
                    false
                }
            }
    } else {
        Modifier
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(inputModifier),
        enabled = enabled,
        // Value-based Compose fields ignore KeyboardOptions.showKeyboardOnFocus. Keep the
        // field selectable without starting an input session until the user activates it.
        readOnly = isTv && !isEditing,
        textStyle = textStyle,
        label = label,
        placeholder = placeholder,
        trailingIcon = trailingIcon,
        suffix = suffix,
        supportingText = supportingText,
        isError = isError,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        shape = shape,
        colors = colors,
    )
}
