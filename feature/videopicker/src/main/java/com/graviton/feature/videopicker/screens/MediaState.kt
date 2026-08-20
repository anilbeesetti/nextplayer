package com.graviton.feature.videopicker.screens

import com.graviton.core.model.Folder

sealed interface MediaState {
    data object Loading : MediaState
    data class Success(val data: Folder?) : MediaState
}
