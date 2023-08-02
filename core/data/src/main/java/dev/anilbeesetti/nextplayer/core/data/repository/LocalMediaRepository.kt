package dev.anilbeesetti.nextplayer.core.data.repository

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.common.extensions.deleteFiles
import dev.anilbeesetti.nextplayer.core.data.mappers.toDirectory
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideo
import dev.anilbeesetti.nextplayer.core.data.mappers.toVideoState
import dev.anilbeesetti.nextplayer.core.data.models.VideoState
import dev.anilbeesetti.nextplayer.core.database.dao.DirectoryDao
import dev.anilbeesetti.nextplayer.core.database.dao.MediumDao
import dev.anilbeesetti.nextplayer.core.database.entities.DirectoryEntity
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.model.Directory
import dev.anilbeesetti.nextplayer.core.model.Video
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

class LocalMediaRepository @Inject constructor(
    private val mediumDao: MediumDao,
    private val directoryDao: DirectoryDao,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val applicationScope: CoroutineScope
) : MediaRepository {

    override fun getVideosFlow(): Flow<List<Video>> {
        return mediumDao.getAll().map { it.map(MediumEntity::toVideo) }
    }

    override fun getVideosFlowFromDirectory(directoryPath: String): Flow<List<Video>> {
        return mediumDao.getAllFromDirectory(directoryPath).map { it.map(MediumEntity::toVideo) }
    }

    override fun getDirectoriesFlow(): Flow<List<Directory>> {
        return directoryDao.getAll().map { it.map(DirectoryEntity::toDirectory) }
    }

    override suspend fun getVideoState(path: String): VideoState? {
        return mediumDao.get(path)?.toVideoState()
    }

    override suspend fun deleteVideos(videoUris: List<String>, intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>) {
        val mediaUrisToDelete = videoUris.map { Uri.parse(it) }
        context.deleteFiles(mediaUrisToDelete, intentSenderLauncher)
    }

    override suspend fun deleteFolders(folderPaths: List<String>, intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>) {
        val mediumEntitiesToDelete = mutableListOf<MediumEntity>()
        for (path in folderPaths) {
            mediumEntitiesToDelete += mediumDao.getAllFromDirectory(path).first()
        }
        val mediaUrisToDelete = mediumEntitiesToDelete.map { Uri.parse(it.uriString) }
        context.deleteFiles(mediaUrisToDelete, intentSenderLauncher)
    }

    override suspend fun saveVideoState(
        path: String,
        position: Long,
        audioTrackIndex: Int?,
        subtitleTrackIndex: Int?,
        playbackSpeed: Float?
    ) {
        Timber.d(
            "save state for [$path]: [$position, $audioTrackIndex, $subtitleTrackIndex, $playbackSpeed]"
        )

        applicationScope.launch {
            mediumDao.updateMediumState(
                path = path,
                position = position,
                audioTrackIndex = audioTrackIndex,
                subtitleTrackIndex = subtitleTrackIndex,
                playbackSpeed = playbackSpeed
            )
        }
    }
}
