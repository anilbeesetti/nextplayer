package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.core.ui.components.CancelButton
import dev.anilbeesetti.nextplayer.core.ui.components.DoneButton
import dev.anilbeesetti.nextplayer.core.ui.components.ListItemComponent
import dev.anilbeesetti.nextplayer.core.ui.components.NextDialog
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.media.CIRCULAR_PROGRESS_INDICATOR_TEST_TAG

@Composable
fun MediaLazyList(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 10.dp),
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

@Composable
fun CenterCircularProgressBar() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.testTag(CIRCULAR_PROGRESS_INDICATOR_TEST_TAG),
        )
    }
}

@Composable
fun NoVideosFound() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 20.dp,
                vertical = 40.dp,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(id = R.string.no_videos_found),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

@Composable
fun DeleteConfirmationDialog(
    subText: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    fileNames: List<String>,
    modifier: Modifier = Modifier,
) {
    NextDialog(
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(R.string.delete), modifier = Modifier.fillMaxWidth()) },
        confirmButton = { DoneButton(onClick = onConfirm) },
        dismissButton = { CancelButton(onClick = onCancel) },
        modifier = modifier,
        content = {
            Text(
                text = subText,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(20.dp))
            LazyColumn {
                items(fileNames) {
                    Text(
                        text = it,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
    )
}

@Preview
@Composable
fun DeleteDialogPreview() {
    DeleteConfirmationDialog(
        subText = "The following files will be deleted permanently",
        onConfirm = { /*TODO*/ },
        onCancel = { /*TODO*/ },
        fileNames = listOf("Harry potter 1", "Harry potter 2", "Harry potter 3", "Harry potter 4"),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionsBottomSheet(
    title: String,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth(),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
fun BottomSheetItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItemComponent(
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
        ),
        leadingContent = { Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary) },
        headlineContent = { Text(text = text) },
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}
