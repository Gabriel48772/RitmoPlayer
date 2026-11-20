package com.gabriel.ritmoplayer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gabriel.ritmoplayer.ui.theme.RitmoPlayerTheme

class MainActivity : ComponentActivity() {

  companion object {
    const val ACTION_PLAY_FROM_WIDGET = "com.gabriel.ritmoplayer.ACTION_PLAY_FROM_WIDGET"
    const val ACTION_EXIT_APP = "com.gabriel.ritmoplayer.ACTION_EXIT_APP"
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    handleIntent(intent)

    enableEdgeToEdge()
    setContent {
      RitmoPlayerTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { MainNavigation() } }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  private fun handleIntent(intent: Intent?) {
    if (intent == null) return
    when (intent.action) {
      ACTION_EXIT_APP -> {
        finishAffinity()
      }
      ACTION_PLAY_FROM_WIDGET -> {
        val app = application as? RitmoApplication
        app?.playbackManager?.startPlaylistFromFavoritesOrStorage()
      }
    }
  }
}

