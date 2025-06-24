package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "webdav_servers")
data class WebDavServerEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val username: String,
    val password: String,
    val isConnected: Boolean = false,
    val lastConnected: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
