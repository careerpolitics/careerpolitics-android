package com.murari.careerpolitics.util.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

typealias NetworkStatusCallback = (isOnline: Boolean) -> Unit

object NetworkUtils {

    /**
     * Checks if the device is online by attempting to connect to a known domain.
     * Uses a timeout to avoid hanging indefinitely.
     *
     * @return true if online, false if offline.
     */
    suspend fun isOnline(): Boolean = withContext(Dispatchers.IO) {
        try {
            val timeout = 1500
            val address = InetSocketAddress("careerpolitics.com", 80)

            Socket().use { socket ->
                socket.connect(address, timeout)
            }

            true
        } catch (e: IOException) {
            // Log.e("NetworkUtils", "Connectivity check failed", e) // Optional logging
            false
        }
    }

    /**
     * Checks if the device is offline.
     *
     * @return true if offline, false otherwise.
     */
    suspend fun isOffline(): Boolean = !isOnline()
}
