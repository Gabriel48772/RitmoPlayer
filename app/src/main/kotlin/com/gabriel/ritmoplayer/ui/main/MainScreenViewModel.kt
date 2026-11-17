package com.gabriel.ritmoplayer.ui.main

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gabriel.ritmoplayer.RitmoApplication
import com.gabriel.ritmoplayer.model.AudioTrack
import com.gabriel.ritmoplayer.model.RepeatMode
import com.gabriel.ritmoplayer.model.SortOrder
import com.gabriel.ritmoplayer.updater.UpdateInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainScreenViewModel(application: Application) : AndroidViewModel(application) {

    private val ritmoApp = application as RitmoApplication
    val playbackManager = ritmoApp.playbackManager
    private val audioScanner = ritmoApp.audioScanner
    val updateManager = ritmoApp.updateManager

    private val _allTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_MODIFIED_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    private val _rewindNotice = MutableStateFlow<String?>(null)
    val rewindNotice: StateFlow<String?> = _rewindNotice.asStateFlow()

    // GitHub Updates
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _updateMessage = MutableStateFlow<String?>(null)
    val updateMessage: StateFlow<String?> = _updateMessage.asStateFlow()

    // Filtered and sorted track list
    val displayedTracks: StateFlow<List<AudioTrack>> = combine(
        _allTracks,
        _searchQuery,
        _sortOrder,
        _selectedFolder
    ) { tracks, query, sort, folder ->
        var result = tracks

        // Folder filter
        if (folder != null) {
            result = result.filter { it.folderName.equals(folder, ignoreCase = true) }
        }

        // Search filter
        if (query.isNotBlank()) {
            val q = query.trim().lowercase()
            result = result.filter {
                it.title.lowercase().contains(q) ||
                it.artist.lowercase().contains(q) ||
                it.album.lowercase().contains(q)
            }
        }

        // Sort order (default is DATE_MODIFIED_DESC)
        when (sort) {
            SortOrder.DATE_MODIFIED_DESC -> result.sortedByDescending { it.dateModified }
            SortOrder.DATE_MODIFIED_ASC -> result.sortedBy { it.dateModified }
            SortOrder.TITLE_ASC -> result.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST_ASC -> result.sortedBy { it.artist.lowercase() }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Collect rewind notification events
        viewModelScope.launch {
            playbackManager.rewindRestartNotice.collect { notice ->
                _rewindNotice.value = notice
                kotlinx.coroutines.delay(4000)
                if (_rewindNotice.value == notice) {
                    _rewindNotice.value = null
                }
            }
        }

        // Auto-check for updates silently on launch
        checkForUpdates(manual = false)
    }

    fun loadMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val tracks = audioScanner.scanMediaStore(_sortOrder.value)
                _allTracks.value = tracks
                _folders.value = tracks.map { it.folderName }.distinct().sorted()

                if (playbackManager.playlist.value.isEmpty() && tracks.isNotEmpty()) {
                    playbackManager.setPlaylist(tracks)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadMusicFromCustomFolder(treeUri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val tracks = audioScanner.scanDocumentTree(treeUri)
                if (tracks.isNotEmpty()) {
                    _allTracks.value = tracks
                    _folders.value = tracks.map { it.folderName }.distinct().sorted()
                    _selectedFolder.value = tracks.firstOrNull()?.folderName
                    playbackManager.setPlaylist(tracks, tracks.firstOrNull())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun playTrack(track: AudioTrack) {
        val currentList = displayedTracks.value
        playbackManager.setPlaylist(currentList, track)
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun selectFolder(folder: String?) {
        _selectedFolder.value = folder
    }

    fun dismissRewindNotice() {
        _rewindNotice.value = null
    }

    fun checkForUpdates(manual: Boolean) {
        viewModelScope.launch {
            val info = updateManager.checkLatestRelease()
            _updateInfo.value = info
            if (info.isAvailable) {
                _showUpdateDialog.value = true
            } else if (manual) {
                _updateMessage.value = "Tu app ya está en la versión más reciente (v${info.currentVersion})"
            }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun clearUpdateMessage() {
        _updateMessage.value = null
    }

    fun downloadAndInstallUpdate(context: Context) {
        val info = _updateInfo.value ?: return
        val apkUrl = info.apkUrl ?: return
        val fileName = info.apkFileName ?: "RitmoPlayer.apk"

        viewModelScope.launch {
            _isDownloading.value = true
            _downloadProgress.value = 0

            val result = updateManager.downloadAndInstallApk(
                context = context,
                apkUrl = apkUrl,
                fileName = fileName,
                onProgress = { progress ->
                    _downloadProgress.value = progress
                }
            )

            _isDownloading.value = false
            if (result.isSuccess) {
                _showUpdateDialog.value = false
            } else {
                _updateMessage.value = "Error al descargar: ${result.exceptionOrNull()?.message}"
            }
        }
    }
}
