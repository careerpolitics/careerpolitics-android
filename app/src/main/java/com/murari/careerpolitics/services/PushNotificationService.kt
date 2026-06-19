package com.murari.careerpolitics.services

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.google.firebase.messaging.FirebaseMessaging
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.util.Logger
import com.murari.careerpolitics.util.network.ApiClient
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.coroutines.resumeWithException

object PushNotificationService {

    private const val TAG = "PushNotificationService"
    private const val FCM_TOKEN_ENDPOINT = "/users/devices/fcm_token"
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 10_000
    private const val MAX_RETRIES = 3
    private const val INITIAL_BACKOFF_MS = 1000L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Entry point to register device FCM token with backend
     */
    fun registerFcmToken(context: Context) {
        serviceScope.launch {
            try {
                val cookies =
                    CookieManager.getInstance().getCookie(AppConfig.baseUrl)

                if (cookies.isNullOrBlank() ||
                    !cookies.contains(AppConfig.SESSION_COOKIE_NAME)
                ) {
                    Log.d(TAG, "User not authenticated, skipping FCM registration")
                    return@launch
                }

                val token = fetchFcmToken()
                Log.d(TAG, "FCM token obtained: ${token.take(20)}…")

                sendTokenWithRetry(token, cookies)
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
     * Send token with exponential backoff retry
     */
    private suspend fun sendTokenWithRetry(token: String, cookies: String) {
        var attempt = 0
        var backoff = INITIAL_BACKOFF_MS
        while (attempt < MAX_RETRIES) {
            try {
                sendTokenToServer(token, cookies)
                return
            } catch (e: Exception) {
                attempt++
                if (attempt >= MAX_RETRIES) {
                    Log.e(TAG, "FCM registration failed after $MAX_RETRIES attempts", e)
                    throw e
                }
                Log.w(TAG, "FCM registration attempt $attempt failed, retrying in ${backoff}ms")
                delay(backoff)
                backoff *= 2
            }
        }
    }

    /**
     * Send token to backend server. Throws on failure for retry logic.
     */
    private fun sendTokenToServer(token: String, cookies: String) {
        val payload = JSONObject().apply {
            put("fcm_token", token)
            put("app_bundle", AppConfig.applicationId)
            put("platform", "android")
        }

        val response = ApiClient.post(
            url = "${AppConfig.baseUrl}$FCM_TOKEN_ENDPOINT",
            body = payload,
            cookies = cookies,
            connectTimeout = CONNECT_TIMEOUT,
            readTimeout = READ_TIMEOUT
        )

        if (response.isSuccess) {
            Log.d(TAG, "FCM token registered successfully")
            try {
                val deviceId = JSONObject(response.body).optInt("device_id", -1)
                if (deviceId > 0) saveDeviceId(deviceId)
            } catch (_: Exception) { }
        } else {
            throw java.io.IOException("FCM registration failed: HTTP ${response.code}")
        }
    }

    /**
     * Persist a pending FCM token when it refreshes while user is logged out.
     * Will be picked up on next registerFcmToken call.
     */
    fun savePendingToken(context: Context, token: String) {
        context.getSharedPreferences("push_prefs", Context.MODE_PRIVATE)
            .edit().putString("pending_fcm_token", token).apply()
    }

    private fun saveDeviceId(deviceId: Int) {
        try {
            val context = com.murari.careerpolitics.activities.StarterApplication.instance
            context?.getSharedPreferences("push_prefs", Context.MODE_PRIVATE)
                ?.edit()?.putInt("device_id", deviceId)?.apply()
        } catch (_: Exception) { }
    }

    /**
     * Mark a notification as read on the backend (fire-and-forget)
     */
    fun markNotificationRead(context: Context, notificationId: String) {
        serviceScope.launch {
            try {
                val cookies = CookieManager.getInstance().getCookie(AppConfig.baseUrl).orEmpty()
                if (cookies.isBlank()) return@launch

                val payload = JSONObject().apply {
                    put("ids", org.json.JSONArray().apply { put(notificationId) })
                }

                val response = ApiClient.post(
                    url = "${AppConfig.baseUrl}/notifications/mark_read",
                    body = payload,
                    cookies = cookies,
                    connectTimeout = CONNECT_TIMEOUT,
                    readTimeout = READ_TIMEOUT
                )
                Log.d(TAG, "Mark read response: ${response.code}")
            } catch (e: Exception) {
                Log.e(TAG, "Error marking notification as read", e)
            }
        }
    }

    /**
     * Unregister the device token when user logs out
     */
    fun unregisterDevice(
        context: Context,
        userId: String?,
        token: String?
    ) {
        val prefs = context.getSharedPreferences("push_prefs", Context.MODE_PRIVATE)
        val deviceId = prefs.getInt("device_id", -1)

        if (deviceId < 0) {
            Logger.w(TAG, "Cannot unregister device: no stored device_id")
            return
        }

        serviceScope.launch {
            try {
                val cookies = CookieManager.getInstance().getCookie(AppConfig.baseUrl).orEmpty()
                val payload = JSONObject().apply {
                    put("token", token.orEmpty())
                    put("platform", "android")
                    put("app_bundle", context.packageName)
                }

                val response = ApiClient.delete(
                    url = "${AppConfig.baseUrl}/users/devices/$deviceId",
                    body = payload,
                    cookies = cookies
                )

                Logger.d(TAG, "Device unregistration response: ${response.code}")
                prefs.edit().remove("device_id").apply()
            } catch (e: Exception) {
                Logger.e(TAG, "Error unregistering device", e)
            }
        }
    }
}