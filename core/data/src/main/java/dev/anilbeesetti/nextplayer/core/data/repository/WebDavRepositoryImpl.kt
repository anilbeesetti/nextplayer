package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.data.webdav.SardineWebDavClient
import dev.anilbeesetti.nextplayer.core.database.dao.WebDavServerDao
import dev.anilbeesetti.nextplayer.core.database.mapper.toWebDavServer
import dev.anilbeesetti.nextplayer.core.database.mapper.toWebDavServerEntity
import dev.anilbeesetti.nextplayer.core.model.WebDavServer
import dev.anilbeesetti.nextplayer.core.model.WebDavFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDavRepositoryImpl @Inject constructor(
    private val webDavServerDao: WebDavServerDao,
    private val webDavClient: SardineWebDavClient
) : WebDavRepository {

    override fun getAllServers(): Flow<List<WebDavServer>> {
        return webDavServerDao.getAllServers().map { entities ->
            entities.map { it.toWebDavServer() }
        }
    }

    override suspend fun getServerById(id: String): WebDavServer? {
        return webDavServerDao.getServerById(id)?.toWebDavServer()
    }

    override suspend fun addServer(server: WebDavServer) {
        webDavServerDao.insertServer(server.toWebDavServerEntity())
    }

    override suspend fun updateServer(server: WebDavServer) {
        webDavServerDao.updateServer(server.toWebDavServerEntity())
    }

    override suspend fun deleteServer(id: String) {
        webDavServerDao.deleteServerById(id)
    }

    override suspend fun updateConnectionStatus(id: String, isConnected: Boolean, lastConnected: Long) {
        webDavServerDao.updateConnectionStatus(id, isConnected, lastConnected)
    }

    override suspend fun getServerFiles(serverId: String, path: String): Result<List<WebDavFile>> {
        return try {
            val server = getServerById(serverId)
                ?: return Result.failure(IllegalArgumentException("Server not found"))
            
            Timber.d("Listing WebDAV files at: ${server.url}$path")
            
            val result = webDavClient.listFiles(server, path)
            if (result.isSuccess) {
                Timber.d("Found ${result.getOrNull()?.size ?: 0} files/directories")
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to list WebDAV files")
            Result.failure(e)
        }
    }

    override suspend fun testConnection(server: WebDavServer): Result<Boolean> {
        return try {
            Timber.d("Testing WebDAV connection to: ${server.url}")
            
            val result = webDavClient.testConnection(server)
            if (result.isSuccess) {
                Timber.d("WebDAV connection test successful")
            }
            result
        } catch (e: Exception) {
            Timber.e(e, "WebDAV connection test failed")
            Result.failure(e)
        }
    }
    
}
