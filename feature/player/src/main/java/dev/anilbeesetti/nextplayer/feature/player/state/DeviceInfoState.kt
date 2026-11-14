package dev.anilbeesetti.nextplayer.feature.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player

@Composable
internal fun rememberDeviceInfo(player: Player): DeviceInfo {
    var info by remember(player) { mutableStateOf(player.deviceInfo) }
    DisposableEffect(player) {
        info = player.deviceInfo
        val listener = object : Player.Listener {
            override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                info = deviceInfo
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return info
}
