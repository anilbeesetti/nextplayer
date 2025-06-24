package dev.anilbeesetti.nextplayer.core.model

import kotlinx.serialization.Serializable

@Serializable
data class WebDavServer(
    val id: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String = "",
    val isConnected: Boolean = false,
    val lastConnected: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
data class WebDavFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val mimeType: String? = null,
)

@Serializable
data class WebDavConnectionState(
    val serverId: String,
    val isConnecting: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null,
)
