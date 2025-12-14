package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.feature.player.isPortrait

@Composable
fun OverlayView(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable () -> Unit,
) {
    val configuration = LocalConfiguration.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .then(
                if (configuration.isPortrait) {
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.4f)
                } else {
                    Modifier
                        .fillMaxWidth(0.4f)
                        .fillMaxHeight()
                },
            ),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .padding(top = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                content()
            }
        }
    }
}