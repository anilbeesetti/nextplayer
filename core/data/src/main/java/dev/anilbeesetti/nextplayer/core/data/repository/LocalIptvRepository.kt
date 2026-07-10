package dev.anilbeesetti.nextplayer.core.data.repository

import android.content.Context
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.data.iptv.M3uParser
import dev.anilbeesetti.nextplayer.core.data.mappers.toEntity
import dev.anilbeesetti.nextplayer.core.data.mappers.toIptvChannel
import dev.anilbeesetti.nextplayer.core.data.mappers.toIptvPlaylist
import dev.anilbeesetti.nextplayer.core.database.dao.IptvDao
import dev.anilbeesetti.nextplayer.core.database.entities.IptvPlaylistEntity
import dev.anilbeesetti.nextplayer.core.model.IptvChannel
import dev.anilbeesetti.nextplayer.core.model.IptvPlaylist
import dev.anilbeesetti.nextplayer.core.model.IptvSourceType
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class LocalIptvRepository @Inject constructor(
    private val iptvDao: IptvDao,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.IO) private val ioDispatcher: CoroutineDispatcher,
) : IptvRepository {

    override fun observePlaylists(): Flow<List<IptvPlaylist>> =
        iptvDao.getPlaylistsWithCount().map { list -> list.map { it.toIptvPlaylist() } }

    override fun observeAllChannels(): Flow<List<IptvChannel>> =
        iptvDao.getAllChannels().map { list -> list.map { it.toIptvChannel() } }

    override fun observeChannels(playlistId: Long): Flow<List<IptvChannel>> =
        iptvDao.getChannelsForPlaylist(playlistId).map { list -> list.map { it.toIptvChannel() } }

    override suspend fun importFromUrl(url: String, name: String?): ImportResult =
        withContext(ioDispatcher) {
            val normalized = url.trim()
            if (normalized.isEmpty()) return@withContext ImportResult.Error("Empty URL")
            val content = try {
                download(normalized)
            } catch (e: Exception) {
                return@withContext ImportResult.Error(e.message ?: "Download failed")
            }
            store(
                content = content,
                name = name?.takeIf { it.isNotBlank() } ?: normalized.substringAfterLast('/').ifBlank { normalized },
                source = normalized,
                sourceType = IptvSourceType.URL,
            )
        }

    override suspend fun importFromContent(content: String, name: String, source: String): ImportResult =
        withContext(ioDispatcher) {
            store(content = content, name = name, source = source, sourceType = IptvSourceType.FILE)
        }

    override suspend fun refreshPlaylist(playlist: IptvPlaylist): ImportResult {
        if (playlist.sourceType != IptvSourceType.URL) {
            return ImportResult.Error("Only URL playlists can be refreshed")
        }
        return importFromUrl(playlist.source, playlist.name)
    }

    override suspend fun deletePlaylist(playlistId: Long) = withContext(ioDispatcher) {
        // Channels are removed via the ON DELETE CASCADE foreign key.
        iptvDao.deletePlaylist(playlistId)
    }

    private suspend fun store(
        content: String,
        name: String,
        source: String,
        sourceType: IptvSourceType,
    ): ImportResult {
        val channels = M3uParser.parse(content)
        if (channels.isEmpty()) {
            return ImportResult.Error("No channels found in playlist")
        }

        val existing = iptvDao.getPlaylistBySource(source)
        val playlistId = existing?.id ?: iptvDao.insertPlaylist(
            IptvPlaylistEntity(
                name = name,
                source = source,
                sourceType = sourceType.name,
            ),
        )

        iptvDao.replaceChannels(
            playlistId = playlistId,
            channels = channels.map { it.toEntity(playlistId) },
        )
        return ImportResult.Success(playlistId = playlistId, channelCount = channels.size)
    }

    private fun download(url: String): String {
        val uri = url.toUri()
        val scheme = uri.scheme?.lowercase()
        // Local content:// or file:// pointers can be opened directly through the resolver.
        if (scheme == "content" || scheme == "file") {
            return context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: throw IOException("Cannot open $url")
        }

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }
            return connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val USER_AGENT = "NextPlayer/IPTV"
    }
}
