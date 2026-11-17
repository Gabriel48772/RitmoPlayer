package com.gabriel.ritmoplayer.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.gabriel.ritmoplayer.model.AudioTrack
import com.gabriel.ritmoplayer.model.SortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioScanner(private val context: Context) {

    private val supportedExtensions = setOf(
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "wma", "mka"
    )

    suspend fun scanMediaStore(sortOrder: SortOrder = SortOrder.DATE_MODIFIED_DESC): List<AudioTrack> =
        withContext(Dispatchers.IO) {
            val trackList = mutableListOf<AudioTrack>()
            val collectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.SIZE
            )

            // Ignore ringtones/notifications by filtering duration > 3 seconds
            val selection = "${MediaStore.Audio.Media.DURATION} >= 3000"
            val orderClause = when (sortOrder) {
                SortOrder.DATE_MODIFIED_DESC -> "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
                SortOrder.DATE_MODIFIED_ASC -> "${MediaStore.Audio.Media.DATE_MODIFIED} ASC"
                SortOrder.TITLE_ASC -> "${MediaStore.Audio.Media.TITLE} ASC"
                SortOrder.ARTIST_ASC -> "${MediaStore.Audio.Media.ARTIST} ASC"
            }

            try {
                context.contentResolver.query(
                    collectionUri,
                    projection,
                    selection,
                    null,
                    orderClause
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol) ?: "Pista desconocida"
                        val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Artista desconocido"
                        val album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" } ?: "Álbum desconocido"
                        val duration = cursor.getLong(durationCol)
                        val path = cursor.getString(dataCol) ?: ""
                        val dateModified = cursor.getLong(dateModifiedCol)
                        val size = cursor.getLong(sizeCol)

                        val folderName = if (path.isNotEmpty()) {
                            try {
                                File(path).parentFile?.name ?: "Música"
                            } catch (_: Exception) {
                                "Música"
                            }
                        } else {
                            "Música"
                        }

                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                        trackList.add(
                            AudioTrack(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                durationMs = duration,
                                uri = contentUri,
                                path = path,
                                folderName = folderName,
                                dateModified = dateModified,
                                size = size
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return@withContext trackList
        }

    suspend fun scanDocumentTree(treeUri: Uri): List<AudioTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<AudioTrack>()
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext emptyList()
            scanDocumentDirectory(rootDoc, tracks, rootDoc.name ?: "Carpeta seleccionada")
            // Default sort by last modified descending
            tracks.sortByDescending { it.dateModified }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext tracks
    }

    private fun scanDocumentDirectory(doc: DocumentFile, tracks: MutableList<AudioTrack>, folderName: String) {
        val files = doc.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                scanDocumentDirectory(file, tracks, file.name ?: folderName)
            } else if (file.isFile) {
                val name = file.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in supportedExtensions) {
                    val title = name.substringBeforeLast('.')
                    tracks.add(
                        AudioTrack(
                            id = file.uri.hashCode().toLong(),
                            title = title,
                            artist = "Desconocido",
                            album = folderName,
                            durationMs = 0L, // Will be resolved dynamically by ExoPlayer
                            uri = file.uri,
                            path = file.uri.toString(),
                            folderName = folderName,
                            dateModified = file.lastModified() / 1000L,
                            size = file.length()
                        )
                    )
                }
            }
        }
    }
}
