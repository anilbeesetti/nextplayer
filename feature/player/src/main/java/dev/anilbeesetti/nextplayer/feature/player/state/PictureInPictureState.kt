package dev.anilbeesetti.nextplayer.feature.player.state

import android.app.AppOpsManager
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
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
import androidx.core.app.PictureInPictureParamsCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.net.toUri
import androidx.core.pip.PictureInPictureDelegate
import androidx.core.pip.VideoPlaybackPictureInPicture
import androidx.media3.common.Player
import androidx.media3.common.listen
import dev.anilbeesetti.nextplayer.core.common.extensions.isPipFeatureSupported
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

/**
 * Wraps the AndroidX Picture-in-Picture Jetpack library ([androidx.core.pip]) so the rest of the
 * player can drive PiP through a single state object. The library handles the OS-version
 * differences: [PictureInPictureParamsCompat.Builder.setEnabled] arms auto-enter via
 * `setAutoEnterEnabled` on Android 12+ and `onUserLeaveHint` on older versions, and
 * [PictureInPictureDelegate] delivers unified enter/exit callbacks.
 */
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

    val isPipSupported: Boolean = activity.isPipFeatureSupported

    val hasPipPermission: Boolean
        get() = if (isPipSupported) {
            val appOps = getSystemService(activity, AppOpsManager::class.java) ?: return true
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_PICTURE_IN_PICTURE, Process.myUid(), activity.packageName) == AppOpsManager.MODE_ALLOWED
        } else {
            true
        }

    var isInPictureInPictureMode: Boolean by mutableStateOf(false)
        private set

    private val pictureInPicture: VideoPlaybackPictureInPicture? = if (isPipSupported) {
        VideoPlaybackPictureInPicture(activity, ContextCompat.getMainExecutor(activity))
    } else {
        null
    }

    private var aspectRatio: Rational? = null
    private var sourceRectHint: Rect? = null
    private var actions: List<RemoteAction> = emptyList()

    fun setVideoViewRect(rect: Rect) {
        if (pictureInPicture == null) return
        if (rect.width() <= 0 || rect.height() <= 0) return

        val newSourceRectHint = Rect(rect)
        val newAspectRatio = Rational(newSourceRectHint.width(), newSourceRectHint.height())
            .takeIf { it.toFloat() in 0.5f..2.39f }

        var changed = false
        if (newAspectRatio != null && newAspectRatio != aspectRatio) {
            aspectRatio = newAspectRatio
            changed = true
        }
        if (newSourceRectHint != sourceRectHint) {
            sourceRectHint = newSourceRectHint
            changed = true
        }

        if (changed) applyPictureInPictureParams()
    }

    fun enterPictureInPictureMode(): Boolean {
        if (pictureInPicture == null || isInPictureInPictureMode) return false
        return runCatching {
            activity.enterPictureInPictureMode(buildPictureInPictureParams())
        }.isSuccess
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

        val eventListener = object : PictureInPictureDelegate.OnPictureInPictureEventListener {
            override fun onPictureInPictureEvent(event: PictureInPictureDelegate.Event, config: Configuration?) {
                when (event) {
                    PictureInPictureDelegate.Event.ENTER_ANIMATION_START -> onEnterPictureInPictureMode(pipBroadcastReceiver)
                    PictureInPictureDelegate.Event.EXITED -> onExitPictureInPictureMode(pipBroadcastReceiver)
                    else -> Unit
                }
            }
        }

        pictureInPicture?.addOnPictureInPictureEventListener(ContextCompat.getMainExecutor(activity), eventListener)

        return onDispose {
            pictureInPicture?.removeOnPictureInPictureEventListener(eventListener)
            pictureInPicture?.close()
            runCatching { activity.unregisterReceiver(pipBroadcastReceiver) }
        }
    }

    suspend fun observe() {
        updatePictureInPictureActions()
        applyPictureInPictureParams()

        player.listen { events ->
            if (events.contains(Player.EVENT_IS_PLAYING_CHANGED)) {
                updatePictureInPictureActions()
                applyPictureInPictureParams()
            }
        }
    }

    private fun onEnterPictureInPictureMode(receiver: BroadcastReceiver) {
        isInPictureInPictureMode = true
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(PIP_INTENT_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun onExitPictureInPictureMode(receiver: BroadcastReceiver) {
        isInPictureInPictureMode = false
        runCatching { activity.unregisterReceiver(receiver) }
    }

    private fun updatePictureInPictureActions() {
        if (pictureInPicture == null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        actions = listOf(
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
    }

    private fun buildPictureInPictureParams(): PictureInPictureParamsCompat =
        PictureInPictureParamsCompat.Builder().apply {
            setActions(actions)
            setSeamlessResizeEnabled(true)
            setEnabled(autoEnter && player.isPlaying)
            aspectRatio?.let { setAspectRatio(it) }
            sourceRectHint?.let { setSourceRectHint(it) }
        }.build()

    private fun applyPictureInPictureParams() {
        val pictureInPicture = pictureInPicture ?: return
        try {
            pictureInPicture.setPictureInPictureParams(buildPictureInPictureParams())
        } catch (e: IllegalStateException) {
            e.printStackTrace()
        }
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
