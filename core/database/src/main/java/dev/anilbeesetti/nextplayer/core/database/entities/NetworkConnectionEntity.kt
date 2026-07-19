package dev.anilbeesetti.nextplayer.core.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "network_connection")
data class NetworkConnectionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "protocol")
    val protocol: String,
    @ColumnInfo(name = "host")
    val host: String,
    @ColumnInfo(name = "port")
    val port: Int?,
    @ColumnInfo(name = "path")
    val path: String = "",
    @ColumnInfo(name = "username")
    val username: String = "",
    @ColumnInfo(name = "password")
    val password: String = "",
    @ColumnInfo(name = "use_https")
    val useHttps: Boolean = false,
    @ColumnInfo(name = "authentication", defaultValue = "'PASSWORD'")
    val authentication: String = "PASSWORD",
    @ColumnInfo(name = "private_key_file_name", defaultValue = "''")
    val privateKeyFileName: String = "",
    @ColumnInfo(name = "private_key_passphrase", defaultValue = "''")
    val privateKeyPassphrase: String = "",
    @ColumnInfo(name = "host_key_fingerprint", defaultValue = "''")
    val hostKeyFingerprint: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
)
