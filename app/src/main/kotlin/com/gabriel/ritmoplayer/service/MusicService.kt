package com.gabriel.ritmoplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.gabriel.ritmoplayer.MainActivity
import com.gabriel.ritmoplayer.R
import com.gabriel.ritmoplayer.RitmoApplication
import com.gabriel.ritmoplayer.model.AudioTrack

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "ritmo_playback_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_PLAY_PAUSE = "com.gabriel.ritmoplayer.ACTION_PLAY_PAUSE"
        const val ACTION_REWIND = "com.gabriel.ritmoplayer.ACTION_REWIND"
        const val ACTION_FAST_FORWARD = "com.gabriel.ritmoplayer.ACTION_FAST_FORWARD"
        const val ACTION_NEXT = "com.gabriel.ritmoplayer.ACTION_NEXT"
        const val ACTION_PREV = "com.gabriel.ritmoplayer.ACTION_PREV"
        const val ACTION_STOP = "com.gabriel.ritmoplayer.ACTION_STOP"
        const val ACTION_UPDATE = "com.gabriel.ritmoplayer.ACTION_UPDATE"

        fun startOrUpdate(context: Context) {
            try {
                val intent = Intent(context, MusicService::class.java).apply {
                    action = ACTION_UPDATE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? RitmoApplication
        val playback = app?.playbackManager

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> playback?.togglePlayPause()
            ACTION_REWIND -> playback?.rewind()
            ACTION_FAST_FORWARD -> playback?.fastForward()
            ACTION_NEXT -> playback?.skipToNext()
            ACTION_PREV -> playback?.skipToPrevious()
            ACTION_STOP -> {
                playback?.exoPlayer?.pause()
                stopForegroundService()
                return START_NOT_STICKY
            }
        }

        val track = playback?.currentTrack?.value
        val isPlaying = playback?.isPlaying?.value ?: false
        val currentPosition = playback?.currentPosition?.value ?: 0L
        val duration = playback?.duration?.value ?: 0L

        val notification = buildNotification(track, isPlaying, currentPosition, duration)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun buildNotification(
        track: AudioTrack?,
        isPlaying: Boolean,
        currentPos: Long,
        duration: Long
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rewindIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MusicService::class.java).apply { action = ACTION_REWIND },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val playPauseIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, MusicService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val fastForwardIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, MusicService::class.java).apply { action = ACTION_FAST_FORWARD },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val nextIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, MusicService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = track?.title ?: "RitmoPlayer"
        val artist = track?.artist ?: "Reproductor de Música"
        val album = track?.album ?: ""

        val totalSecs = (duration / 1000).toInt().coerceAtLeast(0)
        val currentSecs = (currentPos / 1000).toInt().coerceIn(0, if (totalSecs > 0) totalSecs else Int.MAX_VALUE)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_music)
            .setContentTitle(title)
            .setContentText(artist)
            .setSubText(album)
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Progress bar on notification (how much music has elapsed and how much is left)
        if (totalSecs > 0) {
            builder.setProgress(totalSecs, currentSecs, false)
        }

        // Action 0: Retrasar (si va > 50% reinicia la canción)
        builder.addAction(
            R.drawable.ic_notif_rewind,
            "Retrasar",
            rewindIntent
        )

        // Action 1: Pausar / Reproducir
        builder.addAction(
            if (isPlaying) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
            if (isPlaying) "Pausar" else "Reproducir",
            playPauseIntent
        )

        // Action 2: Adelantar (+10s)
        builder.addAction(
            R.drawable.ic_notif_fast_forward,
            "Adelantar",
            fastForwardIntent
        )

        // Action 3: Siguiente
        builder.addAction(
            R.drawable.ic_notif_next,
            "Siguiente",
            nextIntent
        )

        // MediaStyle shows actions in compact notification view
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
        )

        return builder.build()
    }

    private fun stopForegroundService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Reproductor de Música"
            val descriptionText = "Controles multimedia de audio y barra de progreso"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
