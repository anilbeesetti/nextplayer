package dev.anilbeesetti.nextplayer.core.data.mappers

import dev.anilbeesetti.nextplayer.core.database.dao.IptvPlaylistWithCount
import dev.anilbeesetti.nextplayer.core.database.entities.IptvChannelEntity
import dev.anilbeesetti.nextplayer.core.database.entities.IptvPlaylistEntity
import dev.anilbeesetti.nextplayer.core.model.IptvChannel
import dev.anilbeesetti.nextplayer.core.model.IptvPlaylist
import dev.anilbeesetti.nextplayer.core.model.IptvSourceType

fun IptvPlaylistWithCount.toIptvPlaylist(): IptvPlaylist = IptvPlaylist(
    id = playlist.id,
    name = playlist.name,
    source = playlist.source,
    sourceType = runCatching { IptvSourceType.valueOf(playlist.sourceType) }
        .getOrDefault(IptvSourceType.URL),
    channelCount = channelCount,
    addedAt = playlist.addedAt,
)

fun IptvChannelEntity.toIptvChannel(): IptvChannel = IptvChannel(
    id = id,
    playlistId = playlistId,
    name = name,
    url = url,
    logoUrl = logoUrl,
    groupTitle = groupTitle,
    tvgId = tvgId,
    isLive = isLive,
)

fun IptvChannel.toEntity(playlistId: Long): IptvChannelEntity = IptvChannelEntity(
    playlistId = playlistId,
    name = name,
    url = url,
    logoUrl = logoUrl,
    groupTitle = groupTitle,
    tvgId = tvgId,
    isLive = isLive,
)

fun IptvPlaylist.toEntity(): IptvPlaylistEntity = IptvPlaylistEntity(
    id = id,
    name = name,
    source = source,
    sourceType = sourceType.name,
    addedAt = addedAt,
)
