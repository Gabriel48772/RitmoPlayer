package com.gabriel.ritmoplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.gabriel.ritmoplayer.MainActivity
import com.gabriel.ritmoplayer.R
import com.gabriel.ritmoplayer.RitmoApplication
import com.gabriel.ritmoplayer.model.AudioTrack
import com.gabriel.ritmoplayer.service.MusicService

object RitmoWidgetManager {

    private const val PREFS_NAME = "ritmo_widget_prefs"
    private const val KEY_LAST_TITLE = "last_title"
    private const val KEY_LAST_ARTIST = "last_artist"
    private const val KEY_LAST_FOLDER = "last_folder"
    private const val KEY_LAST_DURATION = "last_duration"
    private const val KEY_LAST_IS_PLAYING = "last_is_playing"

    fun saveLastPlaybackState(context: Context, track: AudioTrack?, isPlaying: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (track != null) {
                putString(KEY_LAST_TITLE, track.title)
                putString(KEY_LAST_ARTIST, track.artist)
                putString(KEY_LAST_FOLDER, track.folderName)
                putLong(KEY_LAST_DURATION, track.durationMs)
            }
            putBoolean(KEY_LAST_IS_PLAYING, isPlaying)
            apply()
        }
    }

    private fun getEffectiveTrackInfo(context: Context, track: AudioTrack?): Triple<String, String, String> {
        if (track != null) {
            return Triple(track.title, track.artist, track.folderName)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_LAST_TITLE, "RitmoPlayer") ?: "RitmoPlayer"
        val artist = prefs.getString(KEY_LAST_ARTIST, "Toca para reproducir") ?: "Toca para reproducir"
        val folder = prefs.getString(KEY_LAST_FOLDER, "Tu Música") ?: "Tu Música"
        return Triple(title, artist, folder)
    }

    private fun getServicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MusicService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getActivityPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // -------------------------------------------------------------
    // Compact Widget (4x1)
    // -------------------------------------------------------------
    fun buildCompactViews(context: Context, track: AudioTrack?, isPlaying: Boolean): RemoteViews {
        val (title, artist, _) = getEffectiveTrackInfo(context, track)
        val views = RemoteViews(context.packageName, R.layout.widget_compact)

        views.setTextViewText(R.id.widget_compact_title, title)
        views.setTextViewText(R.id.widget_compact_artist, artist)

        // Play/Pause icon
        views.setImageViewResource(
            R.id.btn_compact_play_pause,
            if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        )

        // Pending Intents
        val openApp = getActivityPendingIntent(context)
        views.setOnClickPendingIntent(R.id.widget_compact_container, openApp)
        views.setOnClickPendingIntent(R.id.widget_compact_icon, openApp)
        views.setOnClickPendingIntent(R.id.widget_compact_text_container, openApp)

        views.setOnClickPendingIntent(
            R.id.btn_compact_rewind,
            getServicePendingIntent(context, MusicService.ACTION_REWIND, 101)
        )
        views.setOnClickPendingIntent(
            R.id.btn_compact_play_pause,
            getServicePendingIntent(context, MusicService.ACTION_PLAY_PAUSE, 102)
        )
        views.setOnClickPendingIntent(
            R.id.btn_compact_next,
            getServicePendingIntent(context, MusicService.ACTION_NEXT, 103)
        )

        return views
    }

    // -------------------------------------------------------------
    // Card Widget (4x2)
    // -------------------------------------------------------------
    fun buildCardViews(
        context: Context,
        track: AudioTrack?,
        isPlaying: Boolean,
        currentPos: Long,
        duration: Long
    ): RemoteViews {
        val (title, artist, folder) = getEffectiveTrackInfo(context, track)
        val views = RemoteViews(context.packageName, R.layout.widget_card)

        views.setTextViewText(R.id.widget_card_folder, "📁 $folder")
        views.setTextViewText(R.id.widget_card_title, title)
        views.setTextViewText(R.id.widget_card_artist, artist)

        // Status badge
        views.setTextViewText(R.id.widget_card_status, if (isPlaying) "REPRODUCIENDO" else "PAUSA")

        // Progress bar
        val progressPercent = if (duration > 0) {
            ((currentPos.toFloat() / duration.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } else 0
        views.setProgressBar(R.id.widget_card_progress, 100, progressPercent, false)

        // Play/Pause icon
        views.setImageViewResource(
            R.id.btn_card_play_pause,
            if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        )

        // Pending Intents
        val openApp = getActivityPendingIntent(context)
        views.setOnClickPendingIntent(R.id.widget_card_container, openApp)
        views.setOnClickPendingIntent(R.id.widget_card_icon, openApp)
        views.setOnClickPendingIntent(R.id.widget_card_text_container, openApp)

        views.setOnClickPendingIntent(
            R.id.btn_card_rewind,
            getServicePendingIntent(context, MusicService.ACTION_REWIND, 201)
        )
        views.setOnClickPendingIntent(
            R.id.btn_card_play_pause,
            getServicePendingIntent(context, MusicService.ACTION_PLAY_PAUSE, 202)
        )
        views.setOnClickPendingIntent(
            R.id.btn_card_fast_forward,
            getServicePendingIntent(context, MusicService.ACTION_FAST_FORWARD, 203)
        )
        views.setOnClickPendingIntent(
            R.id.btn_card_next,
            getServicePendingIntent(context, MusicService.ACTION_NEXT, 204)
        )

        return views
    }

    // -------------------------------------------------------------
    // Circle / Vinyl Widget (2x2)
    // -------------------------------------------------------------
    fun buildCircleViews(context: Context, track: AudioTrack?, isPlaying: Boolean): RemoteViews {
        val (title, artist, _) = getEffectiveTrackInfo(context, track)
        val views = RemoteViews(context.packageName, R.layout.widget_circle)

        views.setTextViewText(R.id.widget_circle_title, title)
        views.setTextViewText(R.id.widget_circle_artist, artist)

        // Play/Pause icon
        views.setImageViewResource(
            R.id.btn_circle_center_play,
            if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play
        )

        // Pending Intents
        val openApp = getActivityPendingIntent(context)
        views.setOnClickPendingIntent(R.id.widget_circle_container, openApp)
        views.setOnClickPendingIntent(R.id.widget_circle_icon, openApp)

        views.setOnClickPendingIntent(
            R.id.btn_circle_center_play,
            getServicePendingIntent(context, MusicService.ACTION_PLAY_PAUSE, 301)
        )
        views.setOnClickPendingIntent(
            R.id.btn_circle_rewind,
            getServicePendingIntent(context, MusicService.ACTION_REWIND, 302)
        )
        views.setOnClickPendingIntent(
            R.id.btn_circle_next,
            getServicePendingIntent(context, MusicService.ACTION_NEXT, 303)
        )

        return views
    }

    // -------------------------------------------------------------
    // Update All Widgets
    // -------------------------------------------------------------
    fun updateAllWidgets(
        context: Context,
        track: AudioTrack?,
        isPlaying: Boolean,
        currentPos: Long = 0L,
        duration: Long = 0L
    ) {
        saveLastPlaybackState(context, track, isPlaying)

        try {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return

            // 1. Compact
            val compactComponent = ComponentName(context, RitmoCompactWidgetProvider::class.java)
            val compactIds = appWidgetManager.getAppWidgetIds(compactComponent)
            if (compactIds != null && compactIds.isNotEmpty()) {
                val views = buildCompactViews(context, track, isPlaying)
                appWidgetManager.updateAppWidget(compactIds, views)
            }

            // 2. Card
            val cardComponent = ComponentName(context, RitmoCardWidgetProvider::class.java)
            val cardIds = appWidgetManager.getAppWidgetIds(cardComponent)
            if (cardIds != null && cardIds.isNotEmpty()) {
                val views = buildCardViews(context, track, isPlaying, currentPos, duration)
                appWidgetManager.updateAppWidget(cardIds, views)
            }

            // 3. Circle
            val circleComponent = ComponentName(context, RitmoCircleWidgetProvider::class.java)
            val circleIds = appWidgetManager.getAppWidgetIds(circleComponent)
            if (circleIds != null && circleIds.isNotEmpty()) {
                val views = buildCircleViews(context, track, isPlaying)
                appWidgetManager.updateAppWidget(circleIds, views)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
