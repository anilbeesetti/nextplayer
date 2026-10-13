package com.graviton.feature.videopicker.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.graviton.core.ui.R
import com.graviton.core.ui.designsystem.NextIcons

const val CIRCULAR_PROGRESS_INDICATOR_TEST_TAG = "circularProgressIndicator"

@Composable
fun CenterCircularProgressBar(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.testTag(CIRCULAR_PROGRESS_INDICATOR_TEST_TAG),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NoVideosFound(
    contentPadding: PaddingValues,
    onOpenLocalVideo: () -> Unit,
    onOpenNetworkStream: () -> Unit,
    firstActionFocusRequester: FocusRequester? = null,
) {
    val firstActionFocusModifier = if (firstActionFocusRequester != null) {
        Modifier.focusRequester(firstActionFocusRequester)
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(
                horizontal = 24.dp,
                vertical = 40.dp,
            ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = NextIcons.Video,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = stringResource(id = R.string.no_videos_found),
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Spacer(modifier = Modifier.height(24.dp))
        FilledTonalButton(
            onClick = onOpenLocalVideo,
            modifier = firstActionFocusModifier
                .widthIn(max = 280.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(
                imageVector = NextIcons.FileOpen,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = stringResource(id = R.string.open_local_video))
        }
        TextButton(
            onClick = onOpenNetworkStream,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            Icon(
                imageVector = NextIcons.Link,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = stringResource(id = R.string.open_network_stream))
        }
    }
}
