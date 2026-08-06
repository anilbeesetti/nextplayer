package dev.anilbeesetti.nextplayer.core.data.repository

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.database.converter.UriListConverter
import dev.anilbeesetti.nextplayer.core.database.dao.MediumStateDao
import dev.anilbeesetti.nextplayer.core.database.entities.MediumStateEntity

internal suspend fun MediumStateDao.addExternalAudioTrack(uri: String, audioUri: Uri) {
    val state = get(uri) ?: MediumStateEntity(uriString = uri)
    val current = UriListConverter.fromStringToList(state.externalAudioTracks)
    if (audioUri in current) return
    upsert(
        state.copy(
            externalAudioTracks = UriListConverter.fromListToString(current + audioUri),
            lastPlayedTime = System.currentTimeMillis(),
        ),
    )
}
