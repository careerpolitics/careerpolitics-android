package com.murari.careerpolitics.services

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.google.firebase.messaging.FirebaseMessaging
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.util.Logger
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resumeWithException

object PushNotificationService {

    private const val TAG = "PushNotificationService"
    private const val FCM_TOKEN_ENDPOINT = "/users/devices/fcm_token"
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 10_000

    /**
     * Entry point to register device FCM token with backend
     */
    fun registerFcmToken(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val cookies = CookieManager.getInstance().getCookie(AppConfig.baseUrl)
                if(cookies.isNullOrBlank() || !cookies.contains("_Career_Politics_Session")){
                    Log.d(TAG,"User not authenticated, skipping FCM token registration")
                    return@launch
                }
                val token = fetchFcmToken()
                Log.d(TAG, "FCM token obtained: ${token.take(20)}...")
                sendTokenToServer(token, cookies)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register FCM token", e)
            }
        }
    }

    /**
     * Fetch FCM token from Firebase
     */
    private suspend fun fetchFcmToken(): String =
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    continuation.resume(token) { cause, _, _ -> }
                }
                .addOnFailureListener { exception ->
                    continuation.resumeWithException(exception)
                }
        }

    /**
     * Send token to backend server
     */
    private fun sendTokenToServer(token: String, cookies: String) {
        val url = URL("${AppConfig.baseUrl}$FCM_TOKEN_ENDPOINT")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cookie",cookies)
        }

        try {
            val payload = JSONObject().apply {
                put("fcm_token", token)
                put("app_bundle", "com.murari.careerpolitics")
                put("platform", "android")
            }

            connection.outputStream.use { os ->
                os.write(payload.toString().toByteArray())
                os.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = readResponse(connection)

            if (responseCode == HttpURLConnection.HTTP_OK ||
                responseCode == HttpURLConnection.HTTP_CREATED
            ) {
                Log.d(TAG, "FCM token registered successfully: $responseBody")
            } else {
                Log.e(
                    TAG,
                    "Failed to register FCM token. Code=$responseCode, Response=$responseBody"
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sending FCM token to server", e)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Read HTTP response safely
     */
    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        return stream?.let {
            BufferedReader(InputStreamReader(it)).use { reader ->
                reader.readText()
            }
        } ?: ""
    }

    /**
     * Unregister the device token when user logs out
     */
    fun unregisterDevice(
        context: Context,
        userId: String?,
        token: String?
    ) {
        if (userId.isNullOrBlank() || token.isNullOrBlank()) {
            Logger.w(TAG, "Cannot unregister device: missing userId or token")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            var connection: HttpURLConnection? = null

            try {
                val url = URL("${AppConfig.baseUrl}/users/devices/$userId")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }

                val jsonPayload = JSONObject().apply {
                    put("token", token)
                    put("platform", "android")
                    put("app_bundle", context.packageName)
                }

                connection.outputStream.use { os ->
                    os.write(jsonPayload.toString().toByteArray())
                    os.flush()
                }

                val responseCode = connection.responseCode
                Logger.d(TAG, "Device unregistration response: $responseCode")

            } catch (e: Exception) {
                Logger.e(TAG, "Error unregistering device", e)
            } finally {
                connection?.disconnect()
            }
        }
    }
}
