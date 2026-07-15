package dev.anilbeesetti.nextplayer.core.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.database.dao.PlaylistDao
import dev.anilbeesetti.nextplayer.core.model.PlaylistType
import javax.inject.Inject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlaylistFileGrant(
    val uri: String,
    val token: Long,
)

interface PlaylistFileGrantRepository {
    suspend fun acquire(uri: String): PlaylistFileGrant?

    suspend fun reserve(grant: PlaylistFileGrant): PlaylistFileGrant?

    suspend fun retain(grant: PlaylistFileGrant)

    suspend fun release(grant: PlaylistFileGrant)

    suspend fun releaseIfUnused(uri: String)
}

interface PersistedPlaylistFileGrantAccess {
    fun takeReadPermission(uri: String)

    fun hasReadPermission(uri: String): Boolean

    fun releaseReadPermission(uri: String)
}

class AndroidPersistedPlaylistFileGrantAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) : PersistedPlaylistFileGrantAccess {
    override fun takeReadPermission(uri: String) {
        context.contentResolver.takePersistableUriPermission(
            Uri.parse(uri),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }

    override fun hasReadPermission(uri: String): Boolean {
        val parsedUri = Uri.parse(uri)
        return context.contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == parsedUri && permission.isReadPermission
        }
    }

    override fun releaseReadPermission(uri: String) {
        context.contentResolver.releasePersistableUriPermission(
            Uri.parse(uri),
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    }
}

class LocalPlaylistFileGrantRepository @Inject constructor(
    private val playlistDao: PlaylistDao,
    private val access: PersistedPlaylistFileGrantAccess,
) : PlaylistFileGrantRepository {
    private val mutex = Mutex()
    private val preparedGrants = mutableMapOf<Long, String>()
    private var nextToken = 1L

    override suspend fun acquire(uri: String): PlaylistFileGrant? = mutex.withLock {
        val hasPermission = try {
            access.takeReadPermission(uri)
            true
        } catch (_: SecurityException) {
            try {
                access.hasReadPermission(uri)
            } catch (_: SecurityException) {
                false
            }
        }
        if (!hasPermission) return@withLock null

        val token = nextToken++
        preparedGrants[token] = uri
        PlaylistFileGrant(uri, token)
    }

    override suspend fun reserve(grant: PlaylistFileGrant): PlaylistFileGrant? = mutex.withLock {
        if (preparedGrants[grant.token] != grant.uri) return@withLock null
        val token = nextToken++
        preparedGrants[token] = grant.uri
        PlaylistFileGrant(grant.uri, token)
    }

    override suspend fun retain(grant: PlaylistFileGrant) {
        mutex.withLock {
            if (preparedGrants[grant.token] == grant.uri) preparedGrants.remove(grant.token)
        }
    }

    override suspend fun release(grant: PlaylistFileGrant) {
        mutex.withLock {
            if (preparedGrants[grant.token] != grant.uri) return@withLock
            preparedGrants.remove(grant.token)
            releaseIfUnusedLocked(grant.uri)
        }
    }

    override suspend fun releaseIfUnused(uri: String) {
        mutex.withLock { releaseIfUnusedLocked(uri) }
    }

    private suspend fun releaseIfUnusedLocked(uri: String) {
        if (uri in preparedGrants.values) return
        if (playlistDao.countPlaylistsByTypeAndSource(PlaylistType.M3U_FILE.name, uri) > 0) return
        try {
            access.releaseReadPermission(uri)
        } catch (_: SecurityException) {
            // The provider may have already revoked the grant. Release is intentionally idempotent.
        }
    }
}
