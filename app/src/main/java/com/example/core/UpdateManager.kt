package com.example.core

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.model.LogCategory
import com.example.data.model.LogLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class GitHubRelease(
    val tagName: String,
    val versionName: String,
    val releaseName: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val htmlUrl: String
)

sealed interface UpdateState {
    object Idle : UpdateState
    object Checking : UpdateState
    data class UpToDate(val currentVersion: String) : UpdateState
    data class UpdateAvailable(val release: GitHubRelease, val currentVersion: String) : UpdateState
    data class Downloading(
        val progress: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val cancelAction: () -> Unit
    ) : UpdateState
    data class ReadyToInstall(val apkFile: File, val release: GitHubRelease) : UpdateState
    data class Error(val message: String) : UpdateState
}

object UpdateManager {
    const val GITHUB_REPO_URL = "https://github.com/Sefeshki/VANTA"
    const val GITHUB_API_LATEST_RELEASE = "https://api.github.com/repos/Sefeshki/VANTA/releases/latest"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var downloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    fun resetState() {
        downloadJob?.cancel()
        _state.value = UpdateState.Idle
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        _state.value = UpdateState.Idle
    }

    /**
     * Checks the official Sefeshki/VANTA GitHub repository for the latest release.
     */
    fun checkForUpdates(
        currentVersion: String = BuildConfig.VERSION_NAME
    ) {
        if (_state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return

        _state.value = UpdateState.Checking
        scope.launch {
            try {
                val request = Request.Builder()
                    .url(GITHUB_API_LATEST_RELEASE)
                    .header("Accept", "application/vnd.github.v3+json")
                    .header("User-Agent", "VANTA-Android/$currentVersion")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val errMsg = when (response.code) {
                            404 -> "No public releases found yet on GitHub."
                            403 -> "GitHub API rate limit reached. Please try again later."
                            else -> "GitHub returned HTTP ${response.code}."
                        }
                        _state.value = UpdateState.Error(errMsg)
                        return@use
                    }

                    val releaseResult = parseReleaseJson(body)
                    releaseResult.onSuccess { release ->
                        val cmp = compareVersions(release.versionName, currentVersion)
                        if (cmp > 0) {
                            _state.value = UpdateState.UpdateAvailable(release, currentVersion)
                            EventLogger.log(
                                LogCategory.SYSTEM,
                                "Update available on GitHub: ${release.tagName} (Installed: $currentVersion)",
                                LogLevel.INFO
                            )
                        } else {
                            _state.value = UpdateState.UpToDate(currentVersion)
                            EventLogger.log(
                                LogCategory.SYSTEM,
                                "VANTA is up to date ($currentVersion)",
                                LogLevel.INFO
                            )
                        }
                    }.onFailure { ex ->
                        _state.value = UpdateState.Error(ex.message ?: "Failed to parse release information.")
                    }
                }
            } catch (e: Exception) {
                _state.value = UpdateState.Error("Network error: Could not reach GitHub servers.")
            }
        }
    }

    /**
     * Parses the GitHub release JSON object.
     */
    fun parseReleaseJson(jsonString: String): Result<GitHubRelease> {
        return try {
            val json = JSONObject(jsonString)
            val tagName = json.optString("tag_name", "").trim()
            if (tagName.isBlank()) {
                return Result.failure(IllegalStateException("GitHub release tag name is missing."))
            }

            val cleanVersion = tagName.removePrefix("v").removePrefix("V").trim()
            val releaseName = json.optString("name", tagName)
            val releaseNotes = json.optString("body", "Bug fixes and performance improvements.")
            val htmlUrl = json.optString("html_url", GITHUB_REPO_URL)

            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkName: String? = null
            var apkSize: Long = 0L

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url")
                        apkName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }
            }

            if (apkUrl.isNullOrBlank()) {
                return Result.failure(
                    IllegalStateException("Release $tagName is published, but no .apk asset was uploaded to GitHub yet.")
                )
            }

            Result.success(
                GitHubRelease(
                    tagName = tagName,
                    versionName = cleanVersion,
                    releaseName = releaseName,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkUrl,
                    apkFileName = apkName ?: "VANTA-$cleanVersion.apk",
                    apkSizeBytes = apkSize,
                    htmlUrl = htmlUrl
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compares two semantic version strings (e.g., "1.1.0" vs "1.0").
     * Returns > 0 if v1 is newer than v2, < 0 if older, 0 if identical.
     */
    fun compareVersions(v1: String, v2: String): Int {
        val s1 = v1.trim().removePrefix("v").removePrefix("V").substringBefore("-").substringBefore("+")
        val s2 = v2.trim().removePrefix("v").removePrefix("V").substringBefore("-").substringBefore("+")

        val parts1 = s1.split(".").mapNotNull { it.toIntOrNull() }
        val parts2 = s2.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val num1 = parts1.getOrElse(i) { 0 }
            val num2 = parts2.getOrElse(i) { 0 }
            if (num1 != num2) {
                return num1.compareTo(num2)
            }
        }
        return 0
    }

    /**
     * Downloads the APK file from GitHub release assets and updates download progress.
     */
    fun startDownload(context: Context, release: GitHubRelease) {
        downloadJob?.cancel()
        val appContext = context.applicationContext

        val cancelAction: () -> Unit = {
            downloadJob?.cancel()
            _state.value = UpdateState.UpdateAvailable(release, BuildConfig.VERSION_NAME)
        }

        _state.value = UpdateState.Downloading(
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = release.apkSizeBytes,
            cancelAction = cancelAction
        )

        downloadJob = scope.launch {
            try {
                val updatesDir = File(appContext.cacheDir, "updates")
                if (!updatesDir.exists()) updatesDir.mkdirs()

                val destinationFile = File(updatesDir, release.apkFileName)
                if (destinationFile.exists()) destinationFile.delete()

                val request = Request.Builder()
                    .url(release.apkDownloadUrl)
                    .header("User-Agent", "VANTA-Android/${BuildConfig.VERSION_NAME}")
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        _state.value = UpdateState.Error("Download failed: Server returned HTTP ${response.code}")
                        return@use
                    }

                    val body = response.body ?: throw IllegalStateException("Empty response body from GitHub")
                    val totalBytes = if (body.contentLength() > 0) body.contentLength() else release.apkSizeBytes

                    body.byteStream().use { input ->
                        FileOutputStream(destinationFile).use { output ->
                            val buffer = ByteArray(8 * 1024)
                            var bytesRead: Int
                            var downloaded: Long = 0L
                            var lastReportTime = 0L

                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!isActive) throw CancellationException("Download canceled by user")
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead

                                val now = System.currentTimeMillis()
                                if (now - lastReportTime > 100 || downloaded == totalBytes) {
                                    lastReportTime = now
                                    val progress = if (totalBytes > 0) {
                                        (downloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    _state.value = UpdateState.Downloading(
                                        progress = progress,
                                        downloadedBytes = downloaded,
                                        totalBytes = totalBytes,
                                        cancelAction = cancelAction
                                    )
                                }
                            }
                            output.flush()
                        }
                    }

                    _state.value = UpdateState.ReadyToInstall(destinationFile, release)
                    EventLogger.log(
                        LogCategory.SYSTEM,
                        "Update downloaded: ${destinationFile.name} (${destinationFile.length() / 1024} KB)",
                        LogLevel.SUCCESS
                    )
                }
            } catch (e: CancellationException) {
                _state.value = UpdateState.UpdateAvailable(release, BuildConfig.VERSION_NAME)
            } catch (e: Exception) {
                _state.value = UpdateState.Error("Failed to download update: ${e.message}")
            }
        }
    }

    /**
     * Prompts the Android system to install the downloaded APK file.
     * Never performs silent installation; native package installer requires user confirmation.
     */
    fun promptInstall(context: Context, apkFile: File) {
        try {
            if (!apkFile.exists()) {
                _state.value = UpdateState.Error("Downloaded package file not found. Please try again.")
                return
            }

            // Android 8.0+ Unknown App Sources Permission Check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val manageIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(manageIntent)
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
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            _state.value = UpdateState.Error("Could not launch package installer: ${e.message}")
        }
    }
}
