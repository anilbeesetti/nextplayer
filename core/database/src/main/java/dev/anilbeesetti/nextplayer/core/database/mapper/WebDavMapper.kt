package dev.anilbeesetti.nextplayer.core.database.mapper

import dev.anilbeesetti.nextplayer.core.database.entities.WebDavServerEntity
import dev.anilbeesetti.nextplayer.core.model.WebDavServer

fun WebDavServerEntity.toWebDavServer(): WebDavServer {
    return WebDavServer(
        id = id,
        name = name,
        url = url,
        username = username,
        password = password,
        isConnected = isConnected,
        lastConnected = lastConnected,
        createdAt = createdAt,
    )
}

fun WebDavServer.toWebDavServerEntity(): WebDavServerEntity {
    return WebDavServerEntity(
        id = id,
        name = name,
        url = url,
        username = username,
        password = password,
        isConnected = isConnected,
        lastConnected = lastConnected,
        createdAt = createdAt,
    )
}
