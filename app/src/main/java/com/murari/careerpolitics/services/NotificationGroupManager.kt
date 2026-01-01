package com.murari.careerpolitics.services

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.murari.careerpolitics.R

class NotificationGroupManager(
    private val context: Context,
    private val builderFactory: NotificationBuilderFactory
) {

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    private val groupCounts = mutableMapOf<String, Int>()

    /**
     * Show notification with automatic grouping
     */
    fun showNotification(data: NotificationData, notificationId: Int) {
        val builder = builderFactory.createBuilder(data)

        val groupKey = data.groupKey
        if (groupKey.isNullOrBlank()) {
            notificationManager.notify(notificationId, builder.build())
            return
        }

        builder.setGroup(groupKey)
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)

        val count = groupCounts.getOrDefault(groupKey, 0) + 1
        groupCounts[groupKey] = count

        notificationManager.notify(notificationId, builder.build())

        if (count >= 2) {
            showGroupSummary(groupKey, count, data)
        }
    }

    /**
     * Create and show group summary notification
     */
    private fun showGroupSummary(
        groupKey: String,
        count: Int,
        data: NotificationData
    ) {
        val channelId = builderFactory.channelManager.getChannelIdForCategory(data.category)

        val summaryTitle = data.target?.title
            ?: when {
                groupKey.startsWith("article_") -> "Article Discussion"
                groupKey.startsWith("thread_") -> "Thread Discussion"
                else -> "CareerPolitics"
            }

        val summaryBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(summaryTitle)
            .setContentText("$count new notifications")
            .setStyle(
                NotificationCompat.InboxStyle()
                    .setBigContentTitle(summaryTitle)
                    .setSummaryText("$count new notifications")
            )
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setAutoCancel(true)

        val summaryId = groupKey.hashCode()
        notificationManager.notify(summaryId, summaryBuilder.build())
    }

    /**
     * Called when a notification is dismissed
     */
    fun onNotificationDismissed(groupKey: String) {
        val currentCount = groupCounts[groupKey] ?: return
        val newCount = currentCount - 1

        if (newCount <= 0) {
            groupCounts.remove(groupKey)
            notificationManager.cancel(groupKey.hashCode()) // remove summary
        } else {
            groupCounts[groupKey] = newCount
        }
    }

    /**
     * Clear all group tracking (useful for logout or testing)
     */
    fun clearAllGroups() {
        groupCounts.clear()
    }

    /**
     * Get count for a specific group
     */
    fun getGroupCount(groupKey: String): Int {
        return groupCounts[groupKey] ?: 0
    }
}