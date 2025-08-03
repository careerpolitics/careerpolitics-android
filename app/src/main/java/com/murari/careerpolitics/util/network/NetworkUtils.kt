package com.murari.careerpolitics.util.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

typealias NetworkStatusCallback = (isOnline: Boolean) -> Unit

object NetworkUtils {
    suspend fun isOnline(): Boolean = withContext(Dispatchers.IO) {
        try {
            val timeout = 1500
            val address = InetSocketAddress("careerpolitics.com", 80)

            Socket().apply {
                connect(address, timeout)
                close()
            }

            true
        } catch (e: IOException) {
            false
        }
    }

    suspend fun isOffline(): Boolean = !isOnline()
}