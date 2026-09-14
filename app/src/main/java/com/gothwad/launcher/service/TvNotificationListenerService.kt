package com.gothwad.launcher.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TvNotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val appIcon: ImageBitmap? = null,
    val nativeBitmap: Bitmap? = null,
    val title: String,
    val text: String,
    val subText: String? = null,
    val postTime: Long,
    val contentIntent: PendingIntent? = null,
    val isClearable: Boolean = true,
)

object NotificationManagerBridge {
    private val _notifications = MutableStateFlow<List<TvNotificationItem>>(emptyList())
    val notifications: StateFlow<List<TvNotificationItem>> = _notifications.asStateFlow()

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    var activeService: TvNotificationListenerService? = null
        internal set

    fun updateNotifications(items: List<TvNotificationItem>) {
        _notifications.value = items
    }

    fun setConnected(connected: Boolean) {
        _isServiceConnected.value = connected
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        return flat.contains(context.packageName)
    }

    fun dismissNotification(key: String) {
        runCatching {
            activeService?.cancelNotification(key)
        }
    }

    fun clearAll() {
        runCatching {
            activeService?.cancelAllNotifications()
        }
    }

    fun launchNotification(context: Context, item: TvNotificationItem) {
        val pi = item.contentIntent
        if (pi != null) {
            runCatching {
                pi.send()
            }.onFailure {
                // Fallback: try launching the app directly
                val pm = context.packageManager
                val intent = pm.getLeanbackLaunchIntentForPackage(item.packageName)
                    ?: pm.getLaunchIntentForPackage(item.packageName)
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        } else {
            val pm = context.packageManager
            val intent = pm.getLeanbackLaunchIntentForPackage(item.packageName)
                ?: pm.getLaunchIntentForPackage(item.packageName)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
}

class TvNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationManagerBridge.activeService = this
        NotificationManagerBridge.setConnected(true)
        refreshNotifications()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (NotificationManagerBridge.activeService == this) {
            NotificationManagerBridge.activeService = null
            NotificationManagerBridge.setConnected(false)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshNotifications()
    }

    private fun refreshNotifications() {
        runCatching {
            val sbns = activeNotifications ?: return
            val pm = packageManager
            val items = mutableListOf<TvNotificationItem>()

            for (sbn in sbns) {
                val n = sbn.notification ?: continue
                // Don't show notifications from launcher itself
                if (sbn.packageName == packageName) continue

                val extras = n.extras ?: continue
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
                    ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                    ?: ""

                if (title.isBlank() && text.isBlank()) continue

                val appInfo = runCatching { pm.getApplicationInfo(sbn.packageName, 0) }.getOrNull()
                val appName = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: sbn.packageName
                val nativeBmp: Bitmap? = runCatching {
                    val drawable = appInfo?.loadIcon(pm) ?: if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        n.smallIcon?.loadDrawable(this)
                    } else {
                        null
                    }
                    drawable?.let { toNativeBitmap(it) }
                }.getOrNull()
                val iconBitmap: ImageBitmap? = nativeBmp?.asImageBitmap()

                val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                val isClearable = sbn.isClearable

                items.add(
                    TvNotificationItem(
                        key = sbn.key,
                        packageName = sbn.packageName,
                        appName = appName,
                        appIcon = iconBitmap,
                        nativeBitmap = nativeBmp,
                        title = title.ifBlank { appName },
                        text = text,
                        subText = subText,
                        postTime = sbn.postTime,
                        contentIntent = n.contentIntent,
                        isClearable = isClearable,
                    )
                )
            }
            items.sortByDescending { it.postTime }
            NotificationManagerBridge.updateNotifications(items)
        }
    }

    private fun toNativeBitmap(drawable: Drawable): Bitmap {
        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 64
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 64
        val targetW = width.coerceIn(32, 96)
        val targetH = height.coerceIn(32, 96)

        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val src = drawable.bitmap
            if (src.width <= 96 && src.height <= 96) {
                return src
            }
            return Bitmap.createScaledBitmap(src, targetW, targetH, true)
        }

        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    private fun toImageBitmap(drawable: Drawable): ImageBitmap? {
        return toNativeBitmap(drawable).asImageBitmap()
    }
}
