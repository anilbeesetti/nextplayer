package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.database.dao.NetworkConnectionDao
import dev.anilbeesetti.nextplayer.core.database.entities.NetworkConnectionEntity
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class LocalNetworkConnectionRepository @Inject constructor(
    private val networkConnectionDao: NetworkConnectionDao,
) : NetworkConnectionRepository {

    override fun getConnections(): Flow<List<NetworkConnection>> =
        networkConnectionDao.getAll().map { entities -> entities.map { it.toModel() } }

    override suspend fun getConnection(id: Long): NetworkConnection? =
        networkConnectionDao.getById(id)?.toModel()

    override suspend fun upsert(connection: NetworkConnection): Long =
        networkConnectionDao.upsert(connection.toEntity())

    override suspend fun delete(id: Long) = networkConnectionDao.deleteById(id)

    private fun NetworkConnectionEntity.toModel() = NetworkConnection(
        id = id,
        name = name,
        protocol = runCatching { NetworkProtocol.valueOf(protocol) }.getOrDefault(NetworkProtocol.SMB),
        host = host,
        port = port,
        path = path,
        username = username,
        password = password,
        useHttps = useHttps,
    )

    private fun NetworkConnection.toEntity() = NetworkConnectionEntity(
        id = id,
        name = name,
        protocol = protocol.name,
        host = host,
        port = port,
        path = path,
        username = username,
        password = password,
        useHttps = useHttps,
    )
}
