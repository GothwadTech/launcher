package com.gothwad.launcher.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class NetStatus(
    val wifi: Boolean = false,
    val ethernet: Boolean = false,
    val vpn: Boolean = false,
    val wifiSignalLevel: Int = 4, // 0..4 bars
    val linkSpeedMbps: Int = 0,
    val ssid: String = "",
) {
    val connected get() = wifi || ethernet
}

/** Emits the current network status and updates on every connectivity change. */
fun networkStatusFlow(context: Context): Flow<NetStatus> = callbackFlow {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    fun compute(): NetStatus {
        var wifi = false
        var eth = false
        var vpn = false
        var level = 4
        var speed = 0
        var ssidName = ""

        @Suppress("DEPRECATION")
        val networks = runCatching { cm.allNetworks }.getOrNull().orEmpty()
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifi = true
                speed = caps.linkDownstreamBandwidthKbps / 1000
                if (speed <= 0) speed = runCatching { wm?.connectionInfo?.linkSpeed ?: 0 }.getOrDefault(0)
                level = runCatching {
                    val rssi = wm?.connectionInfo?.rssi ?: -60
                    WifiManager.calculateSignalLevel(rssi, 5)
                }.getOrDefault(4)
                ssidName = runCatching {
                    wm?.connectionInfo?.ssid?.replace("\"", "") ?: ""
                }.getOrDefault("")
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) eth = true
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) vpn = true
        }
        return NetStatus(
            wifi = wifi,
            ethernet = eth,
            vpn = vpn,
            wifiSignalLevel = level,
            linkSpeedMbps = speed,
            ssid = ssidName,
        )
    }

    trySend(compute())

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(compute()) }
        override fun onLost(network: Network) { trySend(compute()) }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            trySend(compute())
        }
    }
    val request = NetworkRequest.Builder().build()
    cm.registerNetworkCallback(request, callback)
    awaitClose { cm.unregisterNetworkCallback(callback) }
}
