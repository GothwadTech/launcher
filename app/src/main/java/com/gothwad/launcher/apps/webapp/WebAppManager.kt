package com.gothwad.launcher.apps.webapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.gothwad.launcher.data.ConfigStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

object WebAppManager {

    fun formatUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        return if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            "https://$trimmed"
        } else {
            trimmed
        }
    }

    fun getSafeId(url: String): String {
        return runCatching {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(url.toByteArray())
            bytes.joinToString("") { "%02x".format(it) }
        }.getOrDefault("pwa_${System.currentTimeMillis()}")
    }

    suspend fun fetchFaviconAndTitle(rawUrl: String): Pair<String?, Bitmap?> = withContext(Dispatchers.IO) {
        val formatted = formatUrl(rawUrl)
        var fetchedTitle: String? = null
        var fetchedIcon: Bitmap? = null

        // 1. Guess a clean title from host
        runCatching {
            val host = URL(formatted).host.removePrefix("www.")
            val parts = host.split(".")
            if (parts.isNotEmpty()) {
                fetchedTitle = parts[0].replaceFirstChar { it.uppercase() }
            }
        }

        // 2. Fetch high-res favicon via Google's favicon service
        runCatching {
            val encoded = URLEncoder.encode(formatted, "UTF-8")
            val faviconUrl = "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=$encoded&size=128"
            val conn = (URL(faviconUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                instanceFollowRedirects = true
            }
            if (conn.responseCode in 200..299) {
                conn.inputStream.use { input ->
                    fetchedIcon = BitmapFactory.decodeStream(input)
                }
            }
            conn.disconnect()
        }

        // 3. If still null, try domain /favicon.ico
        if (fetchedIcon == null) {
            runCatching {
                val hostUrl = URL(formatted)
                val directIco = "${hostUrl.protocol}://${hostUrl.host}/favicon.ico"
                val conn = (URL(directIco).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                if (conn.responseCode in 200..299) {
                    conn.inputStream.use { input ->
                        fetchedIcon = BitmapFactory.decodeStream(input)
                    }
                }
                conn.disconnect()
            }
        }

        Pair(fetchedTitle, fetchedIcon)
    }

    suspend fun installWebApp(
        context: Context,
        url: String,
        title: String,
        icon: Bitmap?
    ): String = withContext(Dispatchers.IO) {
        val formattedUrl = formatUrl(url)
        val finalTitle = title.trim().ifEmpty {
            URL(formattedUrl).host.removePrefix("www.").replaceFirstChar { it.uppercase() }
        }
        val safeId = getSafeId(formattedUrl)
        val pwaPkg = "pwa://$formattedUrl"

        // Save icon to app internal storage
        var iconPath: String? = null
        val iconBitmap = icon ?: generateFallbackSquircleIcon(finalTitle)
        runCatching {
            val iconsDir = File(context.filesDir, "webapp_icons")
            if (!iconsDir.exists()) iconsDir.mkdirs()
            val iconFile = File(iconsDir, "$safeId.png")
            FileOutputStream(iconFile).use { out ->
                iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            iconPath = iconFile.absolutePath
        }

        // Save into LauncherConfig
        ConfigStore(context).update { cfg ->
            val updatedOrder = cfg.pcDesktopOrder.toMutableList().apply {
                if (!contains(pwaPkg)) add(pwaPkg)
            }
            val updatedLabels = cfg.pcCustomLabels.toMutableMap().apply {
                put(pwaPkg, finalTitle)
            }
            val updatedIcons = cfg.pcCustomIcons.toMutableMap().apply {
                if (iconPath != null) put(pwaPkg, iconPath!!)
            }
            cfg.copy(
                pcDesktopOrder = updatedOrder,
                pcCustomLabels = updatedLabels,
                pcCustomIcons = updatedIcons
            )
        }

        pwaPkg
    }

    fun loadSavedIcon(context: Context, pwaPkg: String): Bitmap? {
        val url = if (pwaPkg.startsWith("pwa://")) pwaPkg.removePrefix("pwa://") else pwaPkg
        val safeId = getSafeId(url)
        val iconFile = File(File(context.filesDir, "webapp_icons"), "$safeId.png")
        if (iconFile.exists()) {
            return runCatching { BitmapFactory.decodeFile(iconFile.absolutePath) }.getOrNull()
        }
        return null
    }

    fun generateFallbackSquircleIcon(title: String): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Generate consistent background color based on title hash
        val colors = intArrayOf(
            0xFF2563EB.toInt(),
            0xFF059669.toInt(),
            0xFFD97706.toInt(),
            0xFF7C3AED.toInt(),
            0xFFDC2626.toInt(),
            0xFF0284C7.toInt()
        )
        val color = colors[Math.abs(title.hashCode()) % colors.size]

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
        }
        val radius = size * 0.24f
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)

        // Draw initial letter
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = size * 0.5f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val letter = title.trim().take(1).uppercase()
        val textY = (size / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(letter, size / 2f, textY, textPaint)

        return bitmap
    }
}
