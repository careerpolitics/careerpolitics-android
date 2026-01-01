package com.murari.careerpolitics.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import androidx.core.app.NotificationManagerCompat

class NotificationChannelManager(private val context: Context) {

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }



    /**
     * Create all notification channels.
     * Safe to call multiple times.
     */
    fun createAllChannels() {

        val channels = listOf(
            createChannel(
                NotificationCategory.COMMENT,
                "Get notified when someone replies to your comments",
                importance = NotificationManager.IMPORTANCE_HIGH
            ),
            createChannel(
                NotificationCategory.MENTION,
                "Get notified when someone mentions you",
                importance = NotificationManager.IMPORTANCE_HIGH
            ),
            createChannel(
                NotificationCategory.REACTION,
                "Get notified about reactions to your content",
                importance = NotificationManager.IMPORTANCE_DEFAULT
            ),
            createChannel(
                NotificationCategory.FOLLOWER,
                "Get notified about new follower",
                importance = NotificationManager.IMPORTANCE_DEFAULT
            ),
            createChannel(
                NotificationCategory.ACHIEVEMENT,
                "Get notified when you earn badges",
                importance = NotificationManager.IMPORTANCE_DEFAULT
            ),
            createChannel(
                NotificationCategory.MILESTONE,
                "Get notified about milestones",
                importance = NotificationManager.IMPORTANCE_DEFAULT
            ),
            createChannel(
                NotificationCategory.DEFAULT,
                "Get notifications from CareerPolitics",
                importance = NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannels(channels)
    }


    /**
     * Check if notifications are enabled globally
     */
    fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // -------------------------
    // Helpers
    // -------------------------
    private fun createChannel(
        category: NotificationCategory,
        description: String,
        importance: Int
    ): NotificationChannel {
        return NotificationChannel(
            category.channelId,
            category.channelName,
            importance
        ).apply {
            this.description = description
            enableVibration(true)
            enableLights(true)

            //High importance channels get blue LED
            if(importance == NotificationManager.IMPORTANCE_HIGH){
                lightColor= Color.BLUE
            }
        }
    }

    fun getChannelIdForCategory(category: NotificationCategory): String {
        return category.channelId
    }

    fun deleteAllChannels(){
        NotificationCategory.entries.forEach { category ->
            notificationManager.deleteNotificationChannel(category.channelId)
        }
    }
}
