package com.murari.careerpolitics.util.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.murari.careerpolitics.events.NetworkStatusEvent
import org.greenrobot.eventbus.EventBus

/**
 * Modern network watcher using ConnectivityManager.NetworkCallback.
 * Replaces the deprecated CONNECTIVITY_CHANGE BroadcastReceiver.
 */
class NetworkWatcher(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            EventBus.getDefault().post(NetworkStatusEvent(NetworkStatus.BACK_ONLINE))
        }

        override fun onLost(network: Network) {
            EventBus.getDefault().post(NetworkStatusEvent(NetworkStatus.OFFLINE))
        }
    }

    fun register() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    fun unregister() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        }
    }
}