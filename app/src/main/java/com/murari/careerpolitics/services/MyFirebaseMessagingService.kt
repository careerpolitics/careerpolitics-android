package com.murari.careerpolitics.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.murari.careerpolitics.R
import com.murari.careerpolitics.activities.MainActivity

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "careerpolitics_notifications"
        private const val CHANNEL_NAME = "CareerPolitics Notifications"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "Message received from: ${message.from}")

        // Case 1: Notification payload
        message.notification?.let { notification ->
            val title = notification.title ?: "CareerPolitics"
            val body = notification.body ?: ""
            showNotification(title, body, message.data)
            return
        }

        // Case 2: Data payload only
        if (message.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${message.data}")

            val title = message.data["notificationTitle"]
                ?: message.data["title"]
                ?: "CareerPolitics"

            val body = message.data["notificationBody"]
                ?: message.data["body"]
                ?: ""

            if (body.isNotEmpty()) {
                showNotification(title, body, message.data)
            }
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: ${token.take(20)}...")
        registerFcmToken(token)
    }

    private fun registerFcmToken(token: String) {
        // TODO: Send token to backend API
        // Example: PushNotificationService.registerFcmToken(applicationContext, token)
    }

    private fun showNotification(
        title: String,
        body: String,
        data: Map<String, String>
    ) {
        createNotificationChannel()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

            // Pass URL or other data if available
            data["url"]?.let { putExtra("url", it) }
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        notificationManager.notify(NOTIFICATION_ID, notificationBuilder.build())

        Log.d(TAG, "Notification displayed: $title")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications from CareerPolitics"
            enableLights(true)
            enableVibration(true)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}
