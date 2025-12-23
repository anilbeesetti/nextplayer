package dev.anilbeesetti.nextplayer.feature.player.state

import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Process
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.DisposableEffectResult
import androidx.compose.runtime.DisposableEffectScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.net.toUri
import androidx.core.util.Consumer
import androidx.media3.common.Player
import androidx.media3.common.listen
import dev.anilbeesetti.nextplayer.core.ui.R as coreUiR

@Composable
fun rememberPictureInPictureState(
    player: Player,
    autoEnter: Boolean = true,
): PictureInPictureState {
    val activity = LocalActivity.current
    val pictureInPictureState = remember {
        PictureInPictureState(
            player = player,
            activity = activity as ComponentActivity,
            autoEnter = autoEnter,
        )
    }
    DisposableEffect(activity) { pictureInPictureState.handleListeners(this) }
    LaunchedEffect(player) { pictureInPictureState.observe() }
    return pictureInPictureState
}

@Stable
class PictureInPictureState(
    private val player: Player,
    private val activity: ComponentActivity,
    private val autoEnter: Boolean = true,
) {
    companion object {
        private const val PIP_INTENT_ACTION = "pip_action"
        private const val PIP_INTENT_ACTION_CODE = "pip_action_code"
        private const val PIP_ACTION_PLAY = 1
        private const val PIP_ACTION_PAUSE = 2
        private const val PIP_ACTION_NEXT = 3
        private const val PIP_ACTION_PREVIOUS = 4
    }

    val isPipSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    val hasPipPermission: Boolean
        get() = if (isPipSupported) {
            val appOps = getSystemService(activity, AppOpsManager::class.java) ?: return true
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), activity.packageName) == AppOpsManager.MODE_ALLOWED
        } else {
            true
        }

    var isInPictureInPictureMode: Boolean by mutableStateOf(false)
        private set

    @RequiresApi(Build.VERSION_CODES.O)
    private val pictureInPictureParamsBuilder = PictureInPictureParams.Builder().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            setSeamlessResizeEnabled(true)
        }
    }

    fun setVideoViewRect(rect: Rect) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (rect.width() <= 0 || rect.height() <= 0) return

        Rational(rect.width(), rect.height()).takeIf { it.toFloat() in 0.5f..2.39f }?.let {
            pictureInPictureParamsBuilder.setAspectRatio(it)
        }
        pictureInPictureParamsBuilder.setSourceRectHint(rect)
        activity.setPictureInPictureParams(pictureInPictureParamsBuilder.build())
    }

    fun enterPictureInPictureMode(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (isInPictureInPictureMode) return false

        return activity.enterPictureInPictureMode(pictureInPictureParamsBuilder.build())
    }

    fun openPictureInPictureSettings() {
        val intent = Intent("android.settings.PICTURE_IN_PICTURE_SETTINGS").apply {
            data = "package:${activity.packageName}".toUri()
        }
        activity.startActivity(intent)
    }

    fun handleListeners(disposableEffectScope: DisposableEffectScope): DisposableEffectResult = with(disposableEffectScope) {
        val pipBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent == null || intent.action != PIP_INTENT_ACTION) return
                when (intent.getIntExtra(PIP_INTENT_ACTION_CODE, 0)) {
                    PIP_ACTION_PLAY -> player.play()
                    PIP_ACTION_PAUSE -> player.pause()
                    PIP_ACTION_NEXT -> player.seekToNext()
                    PIP_ACTION_PREVIOUS -> player.seekToPrevious()
                }
            }
        }

        val pictureInPictureModeChangedListener: Consumer<PictureInPictureModeChangedInfo> = Consumer {
            updateIsInPictureInPictureMode(pipBroadcastReceiver)
        }

        updateIsInPictureInPictureMode(pipBroadcastReceiver)
        activity.addOnPictureInPictureModeChangedListener(pictureInPictureModeChangedListener)

        return onDispose {
            runCatching { activity.unregisterReceiver(pipBroadcastReceiver) }
            activity.removeOnPictureInPictureModeChangedListener(pictureInPictureModeChangedListener)
        }
    }

    suspend fun observe() {
        updateAutoEnterEnabled()
        updatePictureInPictureActions()

        player.listen { events ->
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                updateAutoEnterEnabled()
                updatePictureInPictureActions()
            }
        }
    }

    private fun updateIsInPictureInPictureMode(pipBroadcastReceiver: BroadcastReceiver) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        isInPictureInPictureMode = activity.isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            ContextCompat.registerReceiver(
                activity,
                pipBroadcastReceiver,
                IntentFilter(PIP_INTENT_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } else {
            runCatching { activity.unregisterReceiver(pipBroadcastReceiver) }
        }
    }

    private fun updateAutoEnterEnabled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        pictureInPictureParamsBuilder.setAutoEnterEnabled(autoEnter && player.isPlaying)
        activity.setPictureInPictureParams(pictureInPictureParamsBuilder.build())
    }

    private fun updatePictureInPictureActions() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val actions = listOf(
            createPipAction(
                context = activity,
                title = "skip to previous",
                icon = coreUiR.drawable.ic_skip_prev,
                actionCode = PIP_ACTION_PREVIOUS,
            ),
            if (player.isPlaying) {
                createPipAction(
                    context = activity,
                    title = "pause",
                    icon = coreUiR.drawable.ic_pause,
                    actionCode = PIP_ACTION_PAUSE,
                )
            } else {
                createPipAction(
                    context = activity,
                    title = "play",
                    icon = coreUiR.drawable.ic_play,
                    actionCode = PIP_ACTION_PLAY,
                )
            },
            createPipAction(
                context = activity,
                title = "skip to next",
                icon = coreUiR.drawable.ic_skip_next,
                actionCode = PIP_ACTION_NEXT,
            ),
        )

        pictureInPictureParamsBuilder.setActions(actions)
        activity.setPictureInPictureParams(pictureInPictureParamsBuilder.build())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createPipAction(
        context: Context,
        title: String,
        @DrawableRes icon: Int,
        actionCode: Int,
    ): RemoteAction {
        return RemoteAction(
            Icon.createWithResource(context, icon),
            title,
            title,
            PendingIntent.getBroadcast(
                context,
                actionCode,
                Intent(PIP_INTENT_ACTION).apply {
                    putExtra(PIP_INTENT_ACTION_CODE, actionCode)
                    setPackage(context.packageName)
                },
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
    }
}
