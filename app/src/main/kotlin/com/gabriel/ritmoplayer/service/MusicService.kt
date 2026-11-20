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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.media.session.MediaButtonReceiver
import com.gabriel.ritmoplayer.MainActivity
import com.gabriel.ritmoplayer.R
import com.gabriel.ritmoplayer.RitmoApplication
import com.gabriel.ritmoplayer.model.AudioTrack
import com.gabriel.ritmoplayer.widget.RitmoWidgetManager

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

    private lateinit var mediaSession: MediaSessionCompat

    // Headphone multi-click detection (1 = Play/Pause, 2 = Next, 3 = Previous)
    private var headsetClickCount = 0
    private val headsetHandler = Handler(Looper.getMainLooper())
    private val headsetRunnable = Runnable {
        val app = application as? RitmoApplication
        val playback = app?.playbackManager
        when (headsetClickCount) {
            1 -> playback?.togglePlayPause()
            2 -> playback?.skipToNext()
            3 -> playback?.skipToPrevious()
            else -> playback?.togglePlayPause()
        }
        headsetClickCount = 0
    }

    private fun handleHeadsetHookClick() {
        headsetClickCount++
        headsetHandler.removeCallbacks(headsetRunnable)
        if (headsetClickCount >= 3) {
            headsetHandler.post(headsetRunnable)
        } else {
            headsetHandler.postDelayed(headsetRunnable, 350)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        initMediaSession()
    }

    private fun initMediaSession() {
        val app = application as? RitmoApplication

        mediaSession = MediaSessionCompat(this, "RitmoPlayerSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    app?.playbackManager?.togglePlayPause()
                }

                override fun onPause() {
                    app?.playbackManager?.togglePlayPause()
                }

                override fun onSkipToNext() {
                    app?.playbackManager?.skipToNext()
                }

                override fun onSkipToPrevious() {
                    app?.playbackManager?.skipToPrevious()
                }

                override fun onFastForward() {
                    app?.playbackManager?.fastForward()
                }

                override fun onRewind() {
                    app?.playbackManager?.rewind()
                }

                override fun onStop() {
                    app?.playbackManager?.exoPlayer?.pause()
                    stopForegroundService()
                }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    val keyEvent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        mediaButtonEvent?.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
                    }

                    if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_HEADSETHOOK,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                handleHeadsetHookClick()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                                if (app?.playbackManager?.isPlaying?.value == false) {
                                    app.playbackManager.togglePlayPause()
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                if (app?.playbackManager?.isPlaying?.value == true) {
                                    app.playbackManager.togglePlayPause()
                                }
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                app?.playbackManager?.skipToNext()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                app?.playbackManager?.skipToPrevious()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                app?.playbackManager?.fastForward()
                                return true
                            }
                            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                app?.playbackManager?.rewind()
                                return true
                            }
                        }
                    }
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })

            val mediaButtonReceiverIntent = Intent(Intent.ACTION_MEDIA_BUTTON, null, this@MusicService, MediaButtonReceiver::class.java)
            val mediaButtonPendingIntent = PendingIntent.getBroadcast(
                this@MusicService,
                0,
                mediaButtonReceiverIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            setMediaButtonReceiver(mediaButtonPendingIntent)
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? RitmoApplication
        val playback = app?.playbackManager

        MediaButtonReceiver.handleIntent(mediaSession, intent)

        when (intent?.action) {
            ACTION_PLAY_PAUSE -> {
                if (playback?.isPlaying?.value == false && playback.playlist.value.isEmpty()) {
                    playback.startPlaylistFromFavoritesOrStorage()
                } else {
                    playback?.togglePlayPause()
                }
            }
            ACTION_REWIND -> playback?.rewind()
            ACTION_FAST_FORWARD -> playback?.fastForward()
            ACTION_NEXT -> playback?.skipToNext()
            ACTION_PREV -> playback?.skipToPrevious()
            ACTION_STOP -> {
                playback?.exoPlayer?.pause()
                RitmoWidgetManager.updateAllWidgets(this, null, false, 0L, 0L)
                stopForegroundService()
                return START_NOT_STICKY
            }
        }

        val track = playback?.currentTrack?.value
        val isPlaying = playback?.isPlaying?.value ?: false
        val currentPosition = playback?.currentPosition?.value ?: 0L
        val duration = playback?.duration?.value ?: 0L

        // Update home screen widgets in real time
        RitmoWidgetManager.updateAllWidgets(this, track, isPlaying, currentPosition, duration)

        // Update MediaSession state for Bluetooth & Headphone controls
        updateMediaSessionState(track, isPlaying, currentPosition, duration)

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

    private fun updateMediaSessionState(
        track: AudioTrack?,
        isPlaying: Boolean,
        currentPos: Long,
        duration: Long
    ) {
        if (!::mediaSession.isInitialized) return

        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_FAST_FORWARD or
                PlaybackStateCompat.ACTION_REWIND or
                PlaybackStateCompat.ACTION_SEEK_TO

        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, currentPos, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)

        if (track != null) {
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationMs)
                .build()
            mediaSession.setMetadata(metadata)
        }
        mediaSession.isActive = true
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

        // MediaStyle shows actions in compact notification view and links to media session for Bluetooth/lockscreen
        builder.setStyle(
            androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.sessionToken)
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

    override fun onDestroy() {
        headsetHandler.removeCallbacksAndMessages(null)
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        super.onDestroy()
    }
}

