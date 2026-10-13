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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.TimeUnit

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

    private const val USER_AGENT = "GothwadLauncher-AndroidTV"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_REDIRECTS = 5

    /**
     * A shelled-out `su` can block for as long as a root prompt waits for the user, so every
     * subprocess gets a hard deadline instead of an unbounded `waitFor()`.
     */
    private const val SHELL_TIMEOUT_MS = 8_000L

    /** How this device is able to install a downloaded update. */
    enum class InstallCapability { ROOT_SHELL, PACKAGE_INSTALLER, NEEDS_PERMISSION }

    /**
     * Checks if current app has root or local shell execution ability.
     * On TVs rooted or running local adb port / test-keys / su, commands succeed.
     */
    fun hasRootOrAdbShell(): Boolean =
        runShell(arrayOf("su", "-c", "id"))?.first == 0

    /**
     * Runs [command] with merged stdout/stderr and a hard timeout.
     *
     * @return exit code to output, or null when the command could not run or timed out.
     */
    private fun runShell(command: Array<String>): Pair<Int, String>? {
        var process: Process? = null
        return try {
            process = ProcessBuilder(*command).redirectErrorStream(true).start()
            // Drain the single merged stream first: reading stdout to EOF and *then* waiting is
            // what avoids the classic full-pipe deadlock.
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val finished = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(SHELL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            } else {
                process.waitFor()
                true
            }
            if (!finished) {
                process.destroy()
                null
            } else {
                process.exitValue() to output
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { process?.destroy() }
        }
    }

    /**
     * Resolves how this device can install an update, off the main thread.
     *
     * `hasRootOrAdbShell()` shells out to `su`; the Settings sheet used to call it directly on
     * the UI thread, which hangs (ANR) on any box where `su` exists and shows a prompt.
     */
    suspend fun detectInstallCapability(context: Context): InstallCapability =
        withContext(Dispatchers.IO) {
            when {
                hasRootOrAdbShell() -> InstallCapability.ROOT_SHELL
                canRequestPackageInstalls(context) -> InstallCapability.PACKAGE_INSTALLER
                else -> InstallCapability.NEEDS_PERMISSION
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
        var conn: HttpURLConnection? = null
        try {
            conn = URL(RELEASES_API_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", USER_AGENT)

            if (conn.responseCode != 200) {
                return@withContext UpdateCheckResult.Error("GitHub response error (${conn.responseCode}): ${conn.responseMessage}")
            }

            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }

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
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /**
     * Downloads the APK into the application cache `updates/` directory (the path exposed by
     * `res/xml/file_paths.xml` for the FileProvider install intent).
     */
    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        targetFileName: String,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val updateDir = File(context.cacheDir, "updates")
            if (!updateDir.exists()) updateDir.mkdirs()

            val outFile = File(updateDir, targetFileName)
            if (outFile.exists()) outFile.delete()

            conn = openFollowingRedirects(downloadUrl)
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Refusing to download from: $downloadUrl")
                )

            val code = conn.responseCode
            if (code !in 200..299) {
                return@withContext Result.failure(
                    IllegalStateException("Download failed with HTTP $code")
                )
            }

            val fileLength = conn.contentLength
            var lastReported = -1

            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count == -1) break
                        total += count
                        output.write(buffer, 0, count)

                        if (fileLength > 0) {
                            val progress = ((total * 100) / fileLength).toInt().coerceIn(0, 100)
                            // One main-thread hop per percent instead of one per 8 KB chunk.
                            if (progress != lastReported) {
                                lastReported = progress
                                withContext(Dispatchers.Main) { onProgress(progress) }
                            }
                        }
                    }
                    output.flush()
                }
            }

            Result.success(outFile)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /**
     * Opens [url], following up to [MAX_REDIRECTS] hops manually.
     *
     * `HttpURLConnection.instanceFollowRedirects` does not cross hosts, and GitHub release
     * assets redirect from github.com to a signed objects.githubusercontent.com URL, so the hop
     * has to be handled here. Only http(s) targets are followed: anything else (`file:`,
     * `content:`, `jar:`) is rejected instead of opened, because what comes back is handed to a
     * package installer.
     */
    private fun openFollowingRedirects(url: String): HttpURLConnection? {
        var current = url
        repeat(MAX_REDIRECTS) {
            val scheme = runCatching { URI(current).scheme?.lowercase() }.getOrNull()
            if (scheme != "https" && scheme != "http") return null

            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", USER_AGENT)

            val code = runCatching { conn.responseCode }.getOrDefault(-1)
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrEmpty()) return null
                current = URL(URL(current), location).toString()
                return@repeat
            }
            return conn
        }
        return null
    }

    /**
     * Tries a silent self-install through root / a local shell (`pm install -r <apk>`).
     *
     * Every command is bounded by [SHELL_TIMEOUT_MS]; an unrooted box where `su` exists but
     * waits for a user prompt used to block here forever.
     */
    suspend fun installViaAdbShell(apkFile: File): InstallResult = withContext(Dispatchers.IO) {
        try {
            // Make the file readable by the shell / pm process performing the install.
            apkFile.setReadable(true, false)

            val path = apkFile.absolutePath
            val commands = listOf(
                arrayOf("su", "-c", "pm install -r \"$path\""),
                arrayOf("sh", "-c", "pm install -r \"$path\""),
                arrayOf("pm", "install", "-r", path),
            )

            var lastError = "No command succeeded"
            for (cmd in commands) {
                val result = runShell(cmd)
                if (result == null) {
                    lastError = "${cmd.first()} timed out or could not be executed"
                    continue
                }
                val (exit, output) = result
                if (exit == 0 && output.contains("Success", ignoreCase = true)) {
                    return@withContext InstallResult.SuccessAdb
                }
                lastError = output.trim().ifEmpty { "exit code $exit" }
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
