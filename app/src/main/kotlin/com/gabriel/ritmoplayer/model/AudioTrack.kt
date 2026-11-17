package com.gabriel.ritmoplayer.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: Uri,
    val path: String,
    val folderName: String,
    val dateModified: Long, // seconds or ms timestamp
    val size: Long
) {
    fun getFormattedDuration(): String {
        if (durationMs <= 0) return "0:00"
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val remMinutes = minutes % 60
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, remMinutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
        }
    }

    fun getFormattedDate(): String {
        return try {
            // MediaStore DATE_MODIFIED is in seconds, SAF might be ms
            val timeMs = if (dateModified < 100_000_000_000L) dateModified * 1000L else dateModified
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
            sdf.format(Date(timeMs))
        } catch (_: Exception) {
            ""
        }
    }
}

enum class SortOrder(val label: String) {
    DATE_MODIFIED_DESC("Fecha de modificación (reciente)"),
    DATE_MODIFIED_ASC("Fecha de modificación (antiguo)"),
    TITLE_ASC("Título (A-Z)"),
    ARTIST_ASC("Artista (A-Z)")
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}
