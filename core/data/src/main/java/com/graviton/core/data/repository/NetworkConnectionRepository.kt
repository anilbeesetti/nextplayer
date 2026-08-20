package com.graviton.core.data.repository

import com.graviton.core.model.NetworkConnection
import kotlinx.coroutines.flow.Flow

interface NetworkConnectionRepository {

    fun getConnections(): Flow<List<NetworkConnection>>

    suspend fun getConnection(id: Long): NetworkConnection?

    /** Inserts or updates [connection] and returns its row id. */
    suspend fun upsert(connection: NetworkConnection): Long

    suspend fun delete(id: Long)
}
