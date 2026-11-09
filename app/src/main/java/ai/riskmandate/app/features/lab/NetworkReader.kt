package ai.riskmandate.app.features.lab

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class Transport { WIFI, CELLULAR, ETHERNET, OTHER, NONE }

/** The device's real network state, shown beside scenario 5's answer. */
data class NetworkState(val connected: Boolean, val transport: Transport)

/** Reads the device's real network state for scenario 5 (ACCESS_NETWORK_STATE is already held). Read-only. */
object NetworkReader {
    fun read(context: Context): NetworkState = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        if (caps == null || !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            NetworkState(false, Transport.NONE)
        } else {
            NetworkState(true, when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
                else -> Transport.OTHER
            })
        }
    }.getOrDefault(NetworkState(false, Transport.NONE))
}
