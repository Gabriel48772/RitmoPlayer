package com.gabriel.ritmoplayer.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.gabriel.ritmoplayer.model.AudioTrack
import com.gabriel.ritmoplayer.model.RepeatMode
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

    val exoPlayer: ExoPlayer by lazy {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build().apply {
                addListener(playerListener)
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

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _rewindRestartNotice = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val rewindRestartNotice: SharedFlow<String> = _rewindRestartNotice.asSharedFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startProgressTracker()
            } else {
                stopProgressTracker()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                }
                Player.STATE_ENDED -> {
                    _isPlaying.value = false
                    stopProgressTracker()
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
        }
    }

    fun playTrack(track: AudioTrack) {
        val index = _playlist.value.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            exoPlayer.seekTo(index, 0L)
            _currentTrack.value = track
            exoPlayer.play()
        } else {
            setPlaylist(listOf(track), track)
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0L)
            }
            exoPlayer.play()
        }
    }

    /**
     * Regla explícita del usuario:
     * "Que cada vez que uno presione para retrasar y la música ya va por la mitad,
     * la misma se reinicie, la misma música, pero desde el inicio."
     */
    fun rewind() {
        val current = exoPlayer.currentPosition
        val total = exoPlayer.duration
        if (total > 0 && current >= (total / 2)) {
            // Ya superó la mitad: reiniciar la misma música desde el inicio
            exoPlayer.seekTo(0L)
            exoPlayer.play()
            _currentPosition.value = 0L
            _rewindRestartNotice.tryEmit("Reiniciando desde el inicio (va más del 50%)")
        } else {
            // Menos de la mitad: retrasar 10 segundos
            val target = (current - 10_000L).coerceAtLeast(0L)
            exoPlayer.seekTo(target)
            _currentPosition.value = target
        }
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
    }

    fun skipToPrevious() {
        val current = exoPlayer.currentPosition
        val total = exoPlayer.duration
        // Si va por la mitad o más, la regla también aplica: reiniciar desde el principio
        if (total > 0 && current >= (total / 2)) {
            exoPlayer.seekTo(0L)
            exoPlayer.play()
            _currentPosition.value = 0L
            _rewindRestartNotice.tryEmit("Reiniciando desde el inicio (va más del 50%)")
            return
        }

        if (exoPlayer.hasPreviousMediaItem()) {
            exoPlayer.seekToPreviousMediaItem()
        } else {
            exoPlayer.seekTo(0L)
        }
        exoPlayer.play()
    }

    fun skipToNext() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
            exoPlayer.play()
        } else if (_repeatMode.value == RepeatMode.ALL && _playlist.value.isNotEmpty()) {
            exoPlayer.seekTo(0, 0L)
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val total = exoPlayer.duration
        val validPos = if (total > 0) positionMs.coerceIn(0L, total) else positionMs.coerceAtLeast(0L)
        exoPlayer.seekTo(validPos)
        _currentPosition.value = validPos
    }

    fun toggleShuffle() {
        val newState = !_isShuffle.value
        _isShuffle.value = newState
        exoPlayer.shuffleModeEnabled = newState
    }

    fun toggleRepeat() {
        val nextMode = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
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
            while (isActive && exoPlayer.isPlaying) {
                _currentPosition.value = exoPlayer.currentPosition
                _duration.value = exoPlayer.duration.coerceAtLeast(0L)
                delay(400) // 400ms is battery-friendly while smooth enough for seekbar
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    fun release() {
        stopProgressTracker()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}
