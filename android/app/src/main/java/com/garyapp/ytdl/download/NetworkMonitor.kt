package com.garyapp.ytdl.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.SystemClock

internal interface DownloadNetwork : AutoCloseable {
    fun isAvailable(): Boolean
    fun isStable(): Boolean
}

internal class NetworkMonitor(context: Context, private val onLost: () -> Unit = {}) : DownloadNetwork {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    @Volatile private var availableSince = 0L
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { update() }
        override fun onLost(network: Network) { update() }
        override fun onAvailable(network: Network) { update() }
    }
    init {
        update()
        manager.registerDefaultNetworkCallback(callback)
    }
    override fun isAvailable(): Boolean {
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    @Synchronized private fun update() {
        if (isAvailable()) {
            if (availableSince == 0L) availableSince = SystemClock.elapsedRealtime().coerceAtLeast(1L)
        } else {
            availableSince = 0L
            onLost()
        }
    }
    override fun isStable(): Boolean {
        update()
        return isAvailable() && availableSince > 0 && SystemClock.elapsedRealtime() - availableSince >= 1_000
    }
    override fun close() { manager.unregisterNetworkCallback(callback) }
}
