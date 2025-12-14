package com.murari.careerpolitics.util.network

import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

typealias NetworkStatusCallback = (isOnline: Boolean) -> Unit

object NetworkUtils {

    private const val LOG_TAG = "NetworkUtils"

    /**
     * Checks if the device is online by attempting to connect to a known domain.
     * Uses a timeout to avoid hanging indefinitely.
     *
     * @return true if online, false if offline.
     */
    suspend fun isOnline(): Boolean = withContext(Dispatchers.IO) {
        try {
            val address = InetSocketAddress(AppConfig.baseDomain, 80)

            Socket().use { socket ->
                socket.connect(address, AppConfig.networkCheckTimeout)
            }

            true
        } catch (e: IOException) {
            Logger.d(LOG_TAG, "Connectivity check failed: ${e.message}")
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
