package com.gabriel.ritmoplayer.player

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.gabriel.ritmoplayer.MainActivity
import com.gabriel.ritmoplayer.model.AudioTrack
import com.gabriel.ritmoplayer.model.RepeatMode
import com.gabriel.ritmoplayer.scanner.AudioScanner
import com.gabriel.ritmoplayer.service.MusicService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MusicPlaybackManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var progressJob: Job? = null
    private var inactivityJob: Job? = null

    // 2 horas de inactividad automática (7,200,000 milisegundos)
    private val INACTIVITY_TIMEOUT_MS = 2 * 60 * 60 * 1000L

    init {
        startInactivityTimer()
    }

    val exoPlayer: ExoPlayer by lazy {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build().apply {
                addListener(playerListener)
                repeatMode = Player.REPEAT_MODE_ALL
            }
    }

    private val _playlist = MutableStateFlow<List<AudioTrack>>(emptyList())
    val playlist: StateFlow<List<AudioTrack>> = _playlist.asStateFlow()

    private val _currentTrack = MutableStateFlow<AudioTrack?>(null)
    val currentTrack: StateFlow<AudioTrack?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle: StateFlow<Boolean> = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.ALL)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _rewindRestartNotice = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val rewindRestartNotice: SharedFlow<String> = _rewindRestartNotice.asSharedFlow()

    private var lastRewindPressTime: Long = 0L
    private val DOUBLE_PRESS_INTERVAL_MS = 1500L

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            MusicService.startOrUpdate(context)
            if (isPlaying) {
                cancelInactivityTimer()
                startProgressTracker()
            } else {
                stopProgressTracker()
                startInactivityTimer()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                    MusicService.startOrUpdate(context)
                }
                Player.STATE_ENDED -> {
                    if (_playlist.value.isNotEmpty()) {
                        exoPlayer.seekTo(0, 0L)
                        exoPlayer.play()
                    } else {
                        _isPlaying.value = false
                        stopProgressTracker()
                        MusicService.startOrUpdate(context)
                    }
                }
                else -> Unit
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val mediaId = mediaItem?.mediaId
            val track = _playlist.value.find { it.id.toString() == mediaId }
            _currentTrack.value = track
            _currentPosition.value = 0L
            _duration.value = exoPlayer.duration.coerceAtLeast(0L)
            MusicService.startOrUpdate(context)
        }
    }

    fun setPlaylist(tracks: List<AudioTrack>, startTrack: AudioTrack? = null) {
        _playlist.value = tracks
        val mediaItems = tracks.map { track ->
            MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri(track.uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setAlbumTitle(track.album)
                        .setDisplayTitle(track.title)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        }

        exoPlayer.setMediaItems(mediaItems)
        val startIndex = if (startTrack != null) {
            tracks.indexOfFirst { it.id == startTrack.id }.coerceAtLeast(0)
        } else 0

        exoPlayer.seekTo(startIndex, 0L)
        exoPlayer.prepare()
        if (startTrack != null) {
            _currentTrack.value = startTrack
            exoPlayer.play()
            MusicService.startOrUpdate(context)
        }
    }

    fun playTrack(track: AudioTrack) {
        val index = _playlist.value.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            exoPlayer.seekTo(index, 0L)
            _currentTrack.value = track
            exoPlayer.play()
            MusicService.startOrUpdate(context)
        } else {
            setPlaylist(listOf(track), track)
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (_playlist.value.isEmpty()) {
                startPlaylistFromFavoritesOrStorage()
                return
            }
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0L)
            }
            exoPlayer.play()
        }
        MusicService.startOrUpdate(context)
    }

    /**
     * Inicia el playlist de canciones automáticamente.
     * Si el usuario tiene una carpeta favorita configurada, carga la música de esa carpeta.
     * De lo contrario, carga todas las canciones disponibles y reproduce de inmediato.
     */
    fun startPlaylistFromFavoritesOrStorage() {
        if (_isPlaying.value) return

        if (_playlist.value.isNotEmpty()) {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0L)
            }
            exoPlayer.play()
            MusicService.startOrUpdate(context)
            return
        }

        scope.launch {
            try {
                val scanner = AudioScanner(context)
                val allTracks = scanner.scanMediaStore()
                val prefs = context.getSharedPreferences("ritmo_prefs", Context.MODE_PRIVATE)
                val favoriteFolder = prefs.getString("favorite_folder", null)

                val targetTracks = if (!favoriteFolder.isNullOrBlank()) {
                    val filtered = allTracks.filter { it.folderName.equals(favoriteFolder, ignoreCase = true) }
                    if (filtered.isNotEmpty()) filtered else allTracks
                } else {
                    allTracks
                }

                if (targetTracks.isNotEmpty()) {
                    setPlaylist(targetTracks, targetTracks.first())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Regla del usuario para Retrasar / Retroceder:
     * 1. Presión doble (dos veces en menos de 1.5s):
     *    - Regresa a la canción anterior de la lista.
     *    - Si no posee ninguna canción anterior (ej. primera canción de la lista),
     *      reproduce una canción aleatoria.
     * 2. Presión simple:
     *    - Si la música ya va por la mitad o más (>= 50%), se reinicia la misma música desde el inicio (0:00).
     *    - Si va por menos de la mitad, se retrasan 10 segundos.
     */
    fun rewind() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRewindPressTime <= DOUBLE_PRESS_INTERVAL_MS) {
            // Detección de doble presión: regresar a la canción anterior o aleatoria
            lastRewindPressTime = 0L
            playPreviousOrRandom()
            return
        }
        lastRewindPressTime = now

        val current = exoPlayer.currentPosition
        val total = exoPlayer.duration
        if (total > 0 && current >= (total / 2)) {
            exoPlayer.seekTo(0L)
            exoPlayer.play()
            _currentPosition.value = 0L
            _rewindRestartNotice.tryEmit("Reiniciando desde el inicio (va más del 50%)")
        } else {
            val target = (current - 10_000L).coerceAtLeast(0L)
            exoPlayer.seekTo(target)
            _currentPosition.value = target
        }
        MusicService.startOrUpdate(context)
    }

    /**
     * Regresa a la canción anterior de la lista.
     * Si no posee ninguna canción previa (ej. estamos en la primera de la lista),
     * reproduce una canción aleatoria.
     */
    fun playPreviousOrRandom() {
        val playlist = _playlist.value
        val currentIndex = exoPlayer.currentMediaItemIndex

        if (playlist.isEmpty()) return

        if (currentIndex > 0 && currentIndex < playlist.size) {
            val prevIndex = currentIndex - 1
            val prevTrack = playlist[prevIndex]
            _currentTrack.value = prevTrack
            _currentPosition.value = 0L
            exoPlayer.seekTo(prevIndex, 0L)
            exoPlayer.play()
            _rewindRestartNotice.tryEmit("Canción anterior: ${prevTrack.title}")
        } else {
            // Al no poseer ninguna canción previa, reproducir una aleatoria
            if (playlist.size > 1) {
                val available = playlist.indices.filter { it != currentIndex }
                val targetIndex = if (available.isNotEmpty()) available.random() else playlist.indices.random()
                val randomTrack = playlist[targetIndex]
                _currentTrack.value = randomTrack
                _currentPosition.value = 0L
                exoPlayer.seekTo(targetIndex, 0L)
                exoPlayer.play()
                _rewindRestartNotice.tryEmit("Aleatoria: ${randomTrack.title}")
            } else {
                exoPlayer.seekTo(0, 0L)
                exoPlayer.play()
                _currentPosition.value = 0L
                _rewindRestartNotice.tryEmit("Reiniciando canción actual")
            }
        }
        MusicService.startOrUpdate(context)
    }

    fun fastForward() {
        val current = exoPlayer.currentPosition
        val total = exoPlayer.duration
        val target = if (total > 0) {
            (current + 10_000L).coerceAtMost(total)
        } else {
            current + 10_000L
        }
        exoPlayer.seekTo(target)
        _currentPosition.value = target
        MusicService.startOrUpdate(context)
    }

    fun skipToPrevious() {
        val current = exoPlayer.currentPosition
        val total = exoPlayer.duration
        if (total > 0 && current >= (total / 2)) {
            exoPlayer.seekTo(0L)
            exoPlayer.play()
            _currentPosition.value = 0L
            _rewindRestartNotice.tryEmit("Reiniciando desde el inicio (va más del 50%)")
            MusicService.startOrUpdate(context)
            return
        }

        playPreviousOrRandom()
    }

    fun skipToNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.play()
        } else if (_playlist.value.isNotEmpty()) {
            exoPlayer.seekTo(0, 0L)
            exoPlayer.play()
        }
        MusicService.startOrUpdate(context)
    }

    fun seekTo(positionMs: Long) {
        val total = exoPlayer.duration
        val validPos = if (total > 0) positionMs.coerceIn(0L, total) else positionMs.coerceAtLeast(0L)
        exoPlayer.seekTo(validPos)
        _currentPosition.value = validPos
        MusicService.startOrUpdate(context)
    }

    fun toggleShuffle() {
        val newState = !_isShuffle.value
        _isShuffle.value = newState
        exoPlayer.shuffleModeEnabled = newState
    }

    fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
            RepeatMode.OFF -> RepeatMode.ALL
        }
        _repeatMode.value = nextMode
        exoPlayer.repeatMode = when (nextMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        progressJob = scope.launch {
            var tick = 0
            while (isActive && exoPlayer.isPlaying) {
                _currentPosition.value = exoPlayer.currentPosition
                _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                tick++
                // Update notification progress every 2 seconds
                if (tick % 5 == 0) {
                    MusicService.startOrUpdate(context)
                }
                delay(400)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun startInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = scope.launch {
            delay(INACTIVITY_TIMEOUT_MS)
            closeAppOnInactivity()
        }
    }

    fun cancelInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = null
    }

    /**
     * Cierra la aplicación automáticamente tras 2 horas sin reproducir música.
     * Detiene el servicio en segundo plano, cancela notificaciones y cierra la interfaz.
     */
    private fun closeAppOnInactivity() {
        try {
            exoPlayer.pause()
            _isPlaying.value = false
            stopProgressTracker()

            val stopIntent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_STOP
            }
            context.startService(stopIntent)

            val exitIntent = Intent(context, MainActivity::class.java).apply {
                action = MainActivity.ACTION_EXIT_APP
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(exitIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        cancelInactivityTimer()
        stopProgressTracker()
        try {
            val intent = Intent(context, MusicService::class.java).apply {
                action = MusicService.ACTION_STOP
            }
            context.startService(intent)
        } catch (_: Exception) {}
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}
