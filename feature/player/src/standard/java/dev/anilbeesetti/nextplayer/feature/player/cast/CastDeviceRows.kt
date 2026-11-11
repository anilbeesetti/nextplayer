package dev.anilbeesetti.nextplayer.feature.player.cast

import androidx.compose.runtime.Composable
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection

/** The standard build has no cast; its output sheet lists the phone's outputs only. */
@Composable
internal fun CastDeviceRows(connection: PlaybackConnection, deviceName: String?) = Unit
