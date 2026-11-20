package com.gabriel.ritmoplayer

import android.app.Activity
import android.app.Application
import android.os.Bundle
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

    var isAppInForeground: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        playbackManager = MusicPlaybackManager(this)
        audioScanner = AudioScanner(this)
        updateManager = GitHubUpdateManager(owner = "Gabriel48772", repo = "RitmoPlayer")

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivities = 0

            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                isAppInForeground = startedActivities > 0
                if (isAppInForeground) {
                    playbackManager.onAppForeground()
                }
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivities--
                isAppInForeground = startedActivities > 0
                if (!isAppInForeground) {
                    playbackManager.onAppBackground()
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    override fun onTerminate() {
        super.onTerminate()
        playbackManager.release()
    }
}
