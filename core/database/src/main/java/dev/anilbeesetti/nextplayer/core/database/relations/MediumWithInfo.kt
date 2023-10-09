package dev.anilbeesetti.nextplayer.core.database.relations

import androidx.room.Embedded
import androidx.room.Relation
import dev.anilbeesetti.nextplayer.core.database.entities.AudioStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.MediumEntity
import dev.anilbeesetti.nextplayer.core.database.entities.SubtitleStreamInfoEntity
import dev.anilbeesetti.nextplayer.core.database.entities.VideoStreamInfoEntity

data class MediumWithInfo(
    @Embedded val mediumEntity: MediumEntity,
    @Relation(
        parentColumn = "path",
        entityColumn = "medium_path"
    )
    val videoStreamInfo: VideoStreamInfoEntity?,
    @Relation(
        parentColumn = "path",
        entityColumn = "medium_path"
    )
    val audioStreamsInfo: List<AudioStreamInfoEntity>,
    @Relation(
        parentColumn = "path",
        entityColumn = "medium_path"
    )
    val subtitleStreamsInfo: List<SubtitleStreamInfoEntity>
)
