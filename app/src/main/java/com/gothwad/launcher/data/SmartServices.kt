package com.gothwad.launcher.data

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==========================================
// 1. USAGE-ACCESS PERMISSION CHECK
// ==========================================
/**
 * `PACKAGE_USAGE_STATS` is a *special* access, not a runtime permission: it can only be
 * granted by the user in Settings > Apps > Special access. It is used for foreground-app
 * detection (boot-ad shield) and nothing else - the "most used apps" list this object
 * used to build was dead code and has been removed along with the unused strings.
 */
object UsageTracker {
    fun hasPermission(context: Context): Boolean {
        return runCatching {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }

}

// ==========================================
// 2. BLUETOOTH & REMOTE BATTERY STATUS
// ==========================================
data class BluetoothDeviceStatus(
    val connected: Boolean = false,
    val name: String = "Remote",
    val batteryLevel: Int = -1, // 0..100 or -1 if unknown
    val isRemote: Boolean = true,
)

fun bluetoothStatusFlow(context: Context): Flow<BluetoothDeviceStatus> = callbackFlow {
    // Addresses we have seen an ACL_CONNECTED broadcast for, while this flow is alive.
    // Bluetooth has no public "is this device connected?" query, so the connection state
    // is derived from those broadcasts instead of being assumed.
    val connectedAddresses = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun queryStatus(): BluetoothDeviceStatus {
        return runCatching {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = bm?.adapter ?: BluetoothAdapter.getDefaultAdapter()

            if (adapter == null || !adapter.isEnabled) {
                // Bluetooth is off - there is no remote to report. (This used to claim a
                // connected "TV Remote" with a made-up 85% battery level.)
                return BluetoothDeviceStatus(connected = false, name = "", batteryLevel = -1, isRemote = true)
            }

            val canReadDevices = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

            if (!canReadDevices) {
                // Android 12+ without BLUETOOTH_CONNECT: we genuinely do not know, so
                // report "unknown" instead of inventing a connected remote.
                return BluetoothDeviceStatus(connected = false, name = "", batteryLevel = -1, isRemote = false)
            }

            val bonded = runCatching { adapter.bondedDevices }.getOrNull().orEmpty()
            val connected = bonded.filter { connectedAddresses.contains(it.address) }

            if (connected.isEmpty()) {
                // Nothing has connected since we started watching: unknown, not fake.
                return BluetoothDeviceStatus(connected = false, name = "", batteryLevel = -1, isRemote = false)
            }

            val device = connected.first()
            val name = runCatching { device.name }.getOrNull().orEmpty().ifBlank { "Bluetooth device" }

            // Battery level is only available on some devices/remotes (hidden API).
            val battery = runCatching {
                device.javaClass.getMethod("getBatteryLevel").invoke(device) as? Int ?: -1
            }.getOrDefault(-1)

            BluetoothDeviceStatus(
                connected = true,
                name = name,
                batteryLevel = if (battery in 0..100) battery else -1,
                isRemote = true,
            )
        }.getOrDefault(BluetoothDeviceStatus(connected = false, name = "", batteryLevel = -1))
    }

    trySend(queryStatus())

    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            val address = runCatching {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)?.address
            }.getOrNull()

            when (intent?.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> if (!address.isNullOrEmpty()) {
                    connectedAddresses.add(address)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (!address.isNullOrEmpty()) {
                    connectedAddresses.remove(address)
                }
            }
            trySend(queryStatus())
        }
    }

    val filter = IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        @Suppress("DEPRECATION")
        addAction("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED")
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
    }

    runCatching { context.registerReceiver(receiver, filter) }

    // Slow poll so a freshly granted BLUETOOTH_CONNECT permission (or a battery level
    // that is only reported on demand) shows up without waiting for a broadcast.
    val ticker = launch {
        while (isActive) {
            delay(20_000L)
            trySend(queryStatus())
        }
    }

    awaitClose {
        ticker.cancel()
        runCatching { context.unregisterReceiver(receiver) }
    }
}

// ==========================================
// 3. AUDIO & TV CONTROL HELPER
// ==========================================
object TvControlHelper {
    fun getVolume(context: Context): Pair<Int, Int> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return Pair(5, 15)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return Pair(current, max)
    }

    fun setVolume(context: Context, volume: Int) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        runCatching {
            am.setStreamVolume(AudioManager.STREAM_MUSIC, volume, AudioManager.FLAG_SHOW_UI)
        }
    }

    fun isMuted(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.isStreamMute(AudioManager.STREAM_MUSIC)
        } else {
            am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }
    }

    fun toggleMute(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val mute = !am.isStreamMute(AudioManager.STREAM_MUSIC)
                am.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                    AudioManager.FLAG_SHOW_UI
                )
                mute
            } else {
                false
            }
        }.getOrDefault(false)
    }
}
