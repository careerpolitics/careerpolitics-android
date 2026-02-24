package com.murari.careerpolitics.activities.main

import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.util.Logger
import com.pusher.pushnotifications.PushNotifications

class PushNotificationInitializer(
    private val context: Context
) {

    fun initialize() {
        try {
            FirebaseMessaging.getInstance().subscribeToTopic(AppConfig.pushConfig.firebaseBroadcastTopic)
            registerPusher()
            Logger.d(LOG_TAG, "Push notifications initialized (awaiting auth token registration)")
        } catch (exception: Exception) {
            Logger.e(LOG_TAG, "Error initializing Firebase push notifications", exception)

            try {
                registerPusher()
                Logger.d(LOG_TAG, "Push notifications initialized with fallback path")
            } catch (fallbackException: Exception) {
                Logger.e(LOG_TAG, "Fallback push notification initialization failed", fallbackException)
            }
        }
    }

    private fun registerPusher() {
        PushNotifications.start(context, AppConfig.pushConfig.pusherInstanceId)
        PushNotifications.addDeviceInterest(AppConfig.pushConfig.pusherDeviceInterest)
    }

    companion object {
        private const val LOG_TAG = "PushNotificationInitializer"
    }
}
