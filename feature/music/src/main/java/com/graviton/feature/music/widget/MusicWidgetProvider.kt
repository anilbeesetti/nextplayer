package com.graviton.feature.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.graviton.feature.music.MusicSessionConnection
import com.graviton.feature.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Process-safe compact widget backed by the same MediaSession as the app and notification. */
class MusicWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        refresh(context, manager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in PLAYER_ACTIONS) return
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            val connection = MusicSessionConnection(context.applicationContext)
            try {
                connection.connect()
                val controller = connection.controller ?: return@launch
                when (action) {
                    ACTION_PREVIOUS -> controller.seekToPreviousMediaItem()
                    ACTION_PLAY_PAUSE -> if (controller.isPlaying) controller.pause() else controller.play()
                    ACTION_NEXT -> controller.seekToNextMediaItem()
                }
                updateViews(context, AppWidgetManager.getInstance(context), null, controller)
            } finally {
                connection.release()
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private fun refresh(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            val connection = MusicSessionConnection(context.applicationContext)
            try {
                connection.connect()
                updateViews(context, manager, ids, connection.controller)
            } finally {
                connection.release()
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private fun updateViews(
        context: Context,
        manager: AppWidgetManager,
        requestedIds: IntArray?,
        player: androidx.media3.common.Player?,
    ) {
        val component = android.content.ComponentName(context, MusicWidgetProvider::class.java)
        val ids = requestedIds ?: manager.getAppWidgetIds(component)
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.music_widget).apply {
                setTextViewText(R.id.widget_title, player?.mediaMetadata?.title ?: "Graviton Music")
                setTextViewText(R.id.widget_artist, player?.mediaMetadata?.artist ?: "Nothing playing")
                setTextViewText(R.id.widget_play_pause, if (player?.isPlaying == true) "Pause" else "Play")
                setOnClickPendingIntent(R.id.widget_previous, actionIntent(context, ACTION_PREVIOUS, id))
                setOnClickPendingIntent(R.id.widget_play_pause, actionIntent(context, ACTION_PLAY_PAUSE, id))
                setOnClickPendingIntent(R.id.widget_next, actionIntent(context, ACTION_NEXT, id))
            }
            manager.updateAppWidget(id, views)
        }
    }

    private fun actionIntent(context: Context, action: String, widgetId: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        widgetId * 10 + action.hashCode(),
        Intent(context, MusicWidgetProvider::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val ACTION_PREVIOUS = "com.graviton.music.widget.PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.graviton.music.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.graviton.music.widget.NEXT"
        val PLAYER_ACTIONS = setOf(ACTION_PREVIOUS, ACTION_PLAY_PAUSE, ACTION_NEXT)
    }
}
