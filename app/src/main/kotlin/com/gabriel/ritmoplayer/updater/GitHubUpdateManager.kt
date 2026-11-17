package com.gabriel.ritmoplayer.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.gabriel.ritmoplayer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val isAvailable: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val title: String,
    val changelog: String,
    val apkUrl: String?,
    val apkFileName: String?
)

class GitHubUpdateManager(
    private val owner: String = "Gabriel48772",
    private val repo: String = "RitmoPlayer"
) {

    suspend fun checkLatestRelease(): UpdateInfo = withContext(Dispatchers.IO) {
        val currentVer = BuildConfig.VERSION_NAME
        val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"

        try {
            val url = URL(apiUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "RitmoPlayer-App")
            }

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)

                val tagName = json.optString("tag_name", "").removePrefix("v").removePrefix("V")
                val releaseName = json.optString("name", "Versión $tagName")
                val body = json.optString("body", "Sin notas de versión disponibles.")

                var apkDownloadUrl: String? = null
                var apkName: String? = null

                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url")
                            apkName = name
                            break
                        }
                    }
                }

                val hasUpdate = isNewerVersion(tagName, currentVer)

                return@withContext UpdateInfo(
                    isAvailable = hasUpdate,
                    currentVersion = currentVer,
                    latestVersion = tagName,
                    title = releaseName,
                    changelog = body,
                    apkUrl = apkDownloadUrl,
                    apkFileName = apkName ?: "RitmoPlayer-v$tagName.apk"
                )
            } else {
                return@withContext UpdateInfo(
                    isAvailable = false,
                    currentVersion = currentVer,
                    latestVersion = currentVer,
                    title = "Sin actualizaciones",
                    changelog = "Código de respuesta: $responseCode",
                    apkUrl = null,
                    apkFileName = null
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext UpdateInfo(
                isAvailable = false,
                currentVersion = currentVer,
                latestVersion = currentVer,
                title = "Error al verificar",
                changelog = e.localizedMessage ?: "No se pudo conectar a GitHub",
                apkUrl = null,
                apkFileName = null
            )
        }
    }

    private fun isNewerVersion(remote: String, current: String): Boolean {
        if (remote.isBlank() || current.isBlank()) return false
        val remoteParts = remote.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val currentParts = current.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

        val maxLength = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLength) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        apkUrl: String,
        fileName: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = File(context.cacheDir, "updates")
            if (!downloadDir.exists()) downloadDir.mkdirs()

            val targetFile = File(downloadDir, fileName)
            if (targetFile.exists()) targetFile.delete()

            val url = URL(apkUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "RitmoPlayer-App")
            }

            val fileLength = connection.contentLength
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(8192)
            var totalBytesRead = 0L
            var bytesRead: Int

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (fileLength > 0) {
                    val progress = ((totalBytesRead * 100) / fileLength).toInt()
                    withContext(Dispatchers.Main) {
                        onProgress(progress)
                    }
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()

            withContext(Dispatchers.Main) {
                promptInstall(context, targetFile)
            }

            return@withContext Result.success(targetFile)
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.failure(e)
        }
    }

    fun promptInstall(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                return
            }
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }

        context.startActivity(installIntent)
    }
}
