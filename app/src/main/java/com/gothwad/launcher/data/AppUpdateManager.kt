package com.gothwad.launcher.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class GitHubReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String?,
    val apkFileName: String?,
    val publishedAt: String
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: GitHubReleaseInfo, val currentVersion: String) : UpdateCheckResult()
    data class UpToDate(val currentVersion: String, val latestTag: String) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

sealed class InstallResult {
    data object SuccessAdb : InstallResult()
    data object PackageInstallerLaunched : InstallResult()
    data class Error(val message: String) : InstallResult()
}

object AppUpdateManager {

    private const val GITHUB_OWNER = "GothwadTech"
    private const val GITHUB_REPO = "launcher"
    private const val RELEASES_API_URL = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"

    /**
     * Checks if current app has root or local shell execution ability.
     * On TVs rooted or running local adb port / test-keys / su, commands succeed.
     */
    fun hasRootOrAdbShell(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val exit = p.waitFor()
            exit == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Checks if can install packages directly via normal package installer
     */
    fun canRequestPackageInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Fetches the latest release details from GitHub API for GothwadTech/launcher
     */
    suspend fun checkLatestRelease(currentVersionName: String): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val url = URL(RELEASES_API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "GothwadLauncher-AndroidTV")

            if (conn.responseCode != 200) {
                return@withContext UpdateCheckResult.Error("GitHub response error (${conn.responseCode}): ${conn.responseMessage}")
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val jsonStr = reader.readText()
            reader.close()

            val root = JSONObject(jsonStr)
            val tagName = root.optString("tag_name", "")
            val title = root.optString("name", tagName)
            val body = root.optString("body", "No changelog provided.")
            val publishedAt = root.optString("published_at", "")

            // Look for release APK asset
            var apkUrl: String? = null
            var apkName: String? = null
            val assets: JSONArray? = root.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    val downloadUrl = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        // Prefer Release APK over Debug APK if multiple
                        if (apkUrl == null || name.contains("Release", ignoreCase = true)) {
                            apkUrl = downloadUrl
                            apkName = name
                        }
                    }
                }
            }

            val cleanRemoteVersion = tagName.removePrefix("v").trim()
            val cleanCurrentVersion = currentVersionName.removePrefix("v").trim()

            val isNewer = isVersionNewer(cleanRemoteVersion, cleanCurrentVersion)

            val info = GitHubReleaseInfo(
                tagName = tagName,
                versionName = cleanRemoteVersion,
                releaseTitle = title,
                releaseNotes = body,
                apkDownloadUrl = apkUrl,
                apkFileName = apkName,
                publishedAt = publishedAt
            )

            if (isNewer && !apkUrl.isNullOrEmpty()) {
                UpdateCheckResult.UpdateAvailable(info, cleanCurrentVersion)
            } else {
                UpdateCheckResult.UpToDate(cleanCurrentVersion, tagName.ifEmpty { cleanRemoteVersion })
            }
        } catch (e: Exception) {
            UpdateCheckResult.Error("Failed to check update: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Download the APK to the application cache updates directory
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        targetFileName: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()

            val outFile = File(updateDir, targetFileName)
            if (outFile.exists()) outFile.delete()

            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "GothwadLauncher-AndroidTV")

            var responseCode = conn.responseCode
            var finalConn = conn
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == 307 || responseCode == 308) {
                val redirectUrl = conn.getHeaderField("Location")
                val redUrl = URL(redirectUrl)
                finalConn = redUrl.openConnection() as HttpURLConnection
                finalConn.connectTimeout = 15000
                finalConn.readTimeout = 30000
                finalConn.setRequestProperty("User-Agent", "GothwadLauncher-AndroidTV")
            }

            val fileLength = finalConn.contentLength
            val input = finalConn.inputStream
            val output = FileOutputStream(outFile)

            val buffer = ByteArray(8192)
            var total: Long = 0
            var count: Int

            while (input.read(buffer).also { count = it } != -1) {
                total += count
                if (fileLength > 0) {
                    val progress = ((total * 100) / fileLength).toInt()
                    withContext(Dispatchers.Main) {
                        onProgress(progress)
                    }
                }
                output.write(buffer, 0, count)
            }

            output.flush()
            output.close()
            input.close()

            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Tries silent self-installation via ADB shell / root command.
     * Commands: pm install -r <apkPath>
     */
    suspend fun installViaAdbShell(apkFile: File): InstallResult = withContext(Dispatchers.IO) {
        try {
            // Make file world readable so shell / pm process can read it
            apkFile.setReadable(true, false)

            val commands = listOf(
                arrayOf("su", "-c", "pm install -r \"${apkFile.absolutePath}\""),
                arrayOf("sh", "-c", "pm install -r \"${apkFile.absolutePath}\""),
                arrayOf("pm", "install", "-r", apkFile.absolutePath)
            )

            var lastError = "No command succeeded"
            for (cmd in commands) {
                try {
                    val proc = Runtime.getRuntime().exec(cmd)
                    val reader = BufferedReader(InputStreamReader(proc.inputStream))
                    val errReader = BufferedReader(InputStreamReader(proc.errorStream))
                    val out = reader.readText()
                    val err = errReader.readText()
                    val exit = proc.waitFor()

                    if (exit == 0 && (out.contains("Success", ignoreCase = true) || err.contains("Success", ignoreCase = true))) {
                        return@withContext InstallResult.SuccessAdb
                    } else {
                        lastError = (out + " " + err).trim()
                    }
                } catch (e: Exception) {
                    lastError = e.localizedMessage ?: "Command failed"
                }
            }

            InstallResult.Error("ADB/Shell install not permitted on this TV: $lastError")
        } catch (e: Exception) {
            InstallResult.Error("ADB install failed: ${e.localizedMessage}")
        }
    }

    /**
     * Standard Android PackageInstaller intent fallback via FileProvider
     */
    fun installViaPackageInstaller(context: Context, apkFile: File): InstallResult {
        return try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(intent)
            InstallResult.PackageInstallerLaunched
        } catch (e: Exception) {
            InstallResult.Error("Failed to launch Package Installer: ${e.localizedMessage}")
        }
    }

    private fun isVersionNewer(remoteVer: String, localVer: String): Boolean {
        if (remoteVer.isEmpty()) return false
        if (remoteVer.equals(localVer, ignoreCase = true)) return false

        val remoteParts = remoteVer.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = localVer.split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
