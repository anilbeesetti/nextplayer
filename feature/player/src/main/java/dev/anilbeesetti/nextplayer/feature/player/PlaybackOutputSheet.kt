package dev.anilbeesetti.nextplayer.feature.player

import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import dev.anilbeesetti.nextplayer.core.ui.R
import dev.anilbeesetti.nextplayer.feature.player.cast.CastDeviceRows
import dev.anilbeesetti.nextplayer.feature.player.service.CustomCommands
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackConnection
import dev.anilbeesetti.nextplayer.feature.player.service.PlaybackPresentation
import dev.anilbeesetti.nextplayer.feature.player.state.PlaybackSnapshot

/**
 * Where playback goes: the phone's audio outputs, nearby cast devices in the play build, and the
 * system picker for anything else. Choosing a phone output while casting brings playback back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackOutputSheet(
    player: Player,
    snapshot: PlaybackSnapshot,
    presentation: PlaybackPresentation,
    audioOutput: Int,
    onAudioOutput: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp).testTag("playback_outputs"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                stringResource(R.string.play_on),
                Modifier.padding(
                    start = 8.dp,
                    bottom = 12.dp,
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            val context = LocalContext.current
            val audio = remember(context) { context.getSystemService(AudioManager::class.java) }
            val outputs = rememberPhoneOutputs(audio)
            val onPhone = presentation.connection == PlaybackConnection.LOCAL ||
                presentation.connection == PlaybackConnection.STOPPED
            val routed = audio.routedOutput(outputs)
            val active = outputs.find { it.id == audioOutput }?.id ?: routed
            outputs.forEach { device ->
                PlaybackOutputRow(
                    icon = device.icon(),
                    title = device.label(),
                    summary = null,
                    selected = onPhone && device.id == active,
                    onClick = {
                        onAudioOutput(
                            if (device.id ==
                                routed
                            ) {
                                CustomCommands.SYSTEM_AUDIO_OUTPUT
                            } else {
                                device.id
                            },
                        )
                    },
                )
            }
            CastDeviceRows(
                presentation.connection,
                presentation.deviceName,
            )
            if (Build.VERSION.SDK_INT >= 34) {
                Spacer(Modifier.height(8.dp))
                PlaybackOutputRow(
                    icon = Icons.Rounded.Devices,
                    title = stringResource(R.string.more_devices),
                    summary = stringResource(R.string.more_devices_summary),
                    selected = false,
                    onClick = { MediaRouter2.getInstance(context).showSystemOutputSwitcher() },
                    trailing = Icons.AutoMirrored.Rounded.OpenInNew,
                )
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.padding(horizontal = 8.dp)) {
                PlaybackVolumeControl(
                    player,
                    snapshot,
                )
            }
        }
    }
}

/** One place playback can go; the chosen one is tinted and checked. */
@Composable
internal fun PlaybackOutputRow(
    icon: ImageVector,
    title: String,
    summary: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = if (selected) Icons.Rounded.Check else null,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "output_container",
    )
    val corner by animateDpAsState(
        if (selected) 28.dp else 16.dp,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "output_corner",
    )
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(corner),
        color = container,
    ) {
        Row(
            Modifier.padding(
                horizontal = 16.dp,
                vertical = 14.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (selected) colors.secondary else colors.surfaceContainerHighest,
                    contentColor = if (selected) colors.onSecondary else colors.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            icon,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 16.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (summary != null) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Icon(
                    trailing,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun rememberPhoneOutputs(audio: AudioManager): List<AudioDeviceInfo> {
    var outputs by remember(audio) { mutableStateOf(audio.phoneOutputs()) }
    DisposableEffect(audio) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                outputs = audio.phoneOutputs()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                outputs = audio.phoneOutputs()
            }
        }
        audio.registerAudioDeviceCallback(
            callback,
            Handler(Looper.getMainLooper()),
        )
        onDispose { audio.unregisterAudioDeviceCallback(callback) }
    }
    return outputs
}

/** Speaker, wired and wireless outputs; a headset listed once per kind even if it has two links. */
private fun AudioManager.phoneOutputs(): List<AudioDeviceInfo> =
    getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.kind() != OutputKind.OTHER }
        .distinctBy { it.kind() to it.productName.toString() }
        .sortedBy { it.kind().ordinal }

/** The output the system plays media on when the app has no preference. */
private fun AudioManager.routedOutput(outputs: List<AudioDeviceInfo>): Int? {
    if (Build.VERSION.SDK_INT >= 33) {
        val routed = getAudioDevicesForAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build(),
        ).firstOrNull()
        if (routed != null) {
            outputs.find {
                it.kind() == routed.kind() && it.productName == routed.productName
            }?.let { return it.id }
        }
    }
    return outputs.firstOrNull()?.id
}

private enum class OutputKind { WIRELESS, WIRED, SPEAKER, OTHER }

private fun AudioDeviceInfo.kind(): OutputKind = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_HEARING_AID,
    -> OutputKind.WIRELESS

    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    -> OutputKind.WIRED

    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> OutputKind.SPEAKER

    else -> OutputKind.OTHER
}

private fun AudioDeviceInfo.icon(): ImageVector = when (kind()) {
    OutputKind.WIRELESS -> Icons.Rounded.Bluetooth
    OutputKind.WIRED -> Icons.Rounded.Headphones
    else -> Icons.Rounded.PhoneAndroid
}

@Composable
private fun AudioDeviceInfo.label(): String = when (kind()) {
    OutputKind.SPEAKER -> stringResource(R.string.this_phone)

    else -> productName?.toString()?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.headphones)
}
