package dev.anilbeesetti.nextplayer.feature.videopicker.composables

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.core.data.models.Folder

@Composable
fun FoldersPickerView(
    folders: List<Folder>,
    onFolderClick: (id: Long) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = 10.dp),
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
    ) {
        items(folders) { folder ->
            FolderView(
                folder = folder,
                onClick = { onFolderClick(folder.id) }
            )
        }
    }
}