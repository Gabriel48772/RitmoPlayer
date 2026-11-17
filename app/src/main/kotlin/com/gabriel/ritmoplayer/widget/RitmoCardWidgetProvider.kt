package com.gabriel.ritmoplayer.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.gabriel.ritmoplayer.RitmoApplication

class RitmoCardWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as? RitmoApplication
        val playback = app?.playbackManager
        val track = playback?.currentTrack?.value
        val isPlaying = playback?.isPlaying?.value ?: false
        val currentPos = playback?.currentPosition?.value ?: 0L
        val duration = playback?.duration?.value ?: 0L

        for (appWidgetId in appWidgetIds) {
            val views = RitmoWidgetManager.buildCardViews(context, track, isPlaying, currentPos, duration)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
