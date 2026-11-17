package com.gabriel.ritmoplayer

import android.app.Application
import com.gabriel.ritmoplayer.player.MusicPlaybackManager
import com.gabriel.ritmoplayer.scanner.AudioScanner
import com.gabriel.ritmoplayer.updater.GitHubUpdateManager

class RitmoApplication : Application() {

    lateinit var playbackManager: MusicPlaybackManager
        private set

    lateinit var audioScanner: AudioScanner
        private set

    lateinit var updateManager: GitHubUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        playbackManager = MusicPlaybackManager(this)
        audioScanner = AudioScanner(this)
        updateManager = GitHubUpdateManager(owner = "Gabriel48772", repo = "RitmoPlayer")
    }

    override fun onTerminate() {
        super.onTerminate()
        playbackManager.release()
    }
}
