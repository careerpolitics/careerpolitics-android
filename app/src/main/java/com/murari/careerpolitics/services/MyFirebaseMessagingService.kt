package com.murari.careerpolitics.services

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "MessagingService"
    }

    private lateinit var channelManager: NotificationChannelManager
    private lateinit var builderFactory: NotificationBuilderFactory
    private lateinit var groupManager: NotificationGroupManager

    override fun onCreate() {
        super.onCreate()

        // Initialize channel manager and create channels
        channelManager = NotificationChannelManager(applicationContext)
        builderFactory= NotificationBuilderFactory(applicationContext, channelManager)
        groupManager= NotificationGroupManager(applicationContext, builderFactory)
        channelManager.createAllChannels()
        Log.d(TAG, "Notification system initialized")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Received from FCM: $message")
        Log.d(TAG, "Message data payload: ${message.data}")
        try {
            val data = parseNotificationData(message)
            showRichNotification(data)
        } catch (ex: IllegalArgumentException) {
            Log.e(TAG, "Failed to process notification", ex)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "FCM token refreshed: $token")

        PushNotificationService.registerFcmToken(applicationContext)
    }

    /**
     * Parse RemoteMessage into NotificationData
     */
    private fun parseNotificationData(message: RemoteMessage): NotificationData {
        require(message.data.isNotEmpty()) { "FCM message data payload is empty" }

        val data = NotificationData.fromDataPayload(message.data)
            ?: throw IllegalArgumentException("Failed to parse NotificationData")

        require(data.body.isNotBlank()) { "Notification body is empty" }

        return data
    }


    /**
     * Display notification using grouping and rich styles
     */
    private fun showRichNotification(data: NotificationData) {
        val notificationId = System.currentTimeMillis().toInt()

        groupManager.showNotification(
            data = data,
            notificationId = notificationId
        )
    }
}
