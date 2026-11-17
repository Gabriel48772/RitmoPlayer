package com.gabriel.ritmoplayer.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.gabriel.ritmoplayer.RitmoApplication

class RitmoCircleWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as? RitmoApplication
        val playback = app?.playbackManager
        val track = playback?.currentTrack?.value
        val isPlaying = playback?.isPlaying?.value ?: false

        for (appWidgetId in appWidgetIds) {
            val views = RitmoWidgetManager.buildCircleViews(context, track, isPlaying)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
