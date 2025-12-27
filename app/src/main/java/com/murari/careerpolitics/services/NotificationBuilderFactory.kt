package com.murari.careerpolitics.services

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.graphics.toColorInt
import com.murari.careerpolitics.R
import com.murari.careerpolitics.activities.MainActivity

class NotificationBuilderFactory(
    private val context: Context,
    val channelManager: NotificationChannelManager
) {

    /**
     * Creates a NotificationCompat.Builder for a given category.
     * Channel must already be created.
     */
    fun createBuilder(data: NotificationData): NotificationCompat.Builder {

        val channelId = channelManager.getChannelIdForCategory(data.category)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(data.title)
            .setContentText(data.body)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        // ----------------------------------------------------
        // BigTextStyle + actor summary
        // ----------------------------------------------------
        if (data.body.isNotEmpty()) {
            val style = NotificationCompat.BigTextStyle()
                .bigText(data.body)

            data.actor?.username?.let { username ->
                style.setSummaryText("@$username")
            }

            builder.setStyle(style)
        }

        // ----------------------------------------------------
        // Priority
        // ----------------------------------------------------
        builder.priority = when (data.category) {
            NotificationCategory.COMMENT,
            NotificationCategory.MENTION ->
                NotificationCompat.PRIORITY_HIGH
            else ->
                NotificationCompat.PRIORITY_DEFAULT
        }

        // ----------------------------------------------------
        // Color
        // ----------------------------------------------------
        data.color?.let {
            builder.color = it.toColorInt()
        }

        // ----------------------------------------------------
        // Content intent (deep link)
        // ----------------------------------------------------
        data.url?.let { url ->
            builder.setContentIntent(createContentIntent(url, data.notificationType))
        }

        // ----------------------------------------------------
        // Grouping
        // ----------------------------------------------------
        data.groupKey?.let {
            builder.setGroup(it)
        }

        // ----------------------------------------------------
        // Actions
        // ----------------------------------------------------
        data.actions.forEach { action ->
            addAction(builder, action, data)
        }

        // ----------------------------------------------------
        // System category (Android optimization)
        // ----------------------------------------------------
        builder.setCategory(
            when (data.category) {
                NotificationCategory.COMMENT,
                NotificationCategory.MENTION ->
                    NotificationCompat.CATEGORY_MESSAGE

                NotificationCategory.REACTION,
                NotificationCategory.SOCIAL ->
                    NotificationCompat.CATEGORY_SOCIAL

                NotificationCategory.ACHIEVEMENT,
                NotificationCategory.MILESTONE ->
                    NotificationCompat.CATEGORY_STATUS

                else ->
                    NotificationCompat.CATEGORY_MESSAGE
            }
        )

        return builder
    }

    // ----------------------------------------------------
    // Helpers
    // ----------------------------------------------------

    private fun createContentIntent(
        url: String,
        notificationType: String
    ): PendingIntent {

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("url", url)
            putExtra("notification_type", notificationType)
        }

        return PendingIntent.getActivity(
            context,
            url.hashCode(), // stable & collision-resistant
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }


    private fun addAction(
        builder: NotificationCompat.Builder,
        action: NotificationAction,
        data: NotificationData
    ) {
        when (action.type.lowercase()) {

            // ------------------------------------
            // Reply action (RemoteInput)
            // ------------------------------------
            "reply" -> {
                val remoteInput = RemoteInput.Builder("reply_text")
                    .setLabel(action.label)
                    .build()

                val replyIntent = createActionIntent(action, data)

                val replyPendingIntent = PendingIntent.getBroadcast(
                    context,
                    action.id.hashCode(),
                    replyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                val actionBuilder = NotificationCompat.Action.Builder(
                     R.drawable.ic_notification,
                    action.label,
                    replyPendingIntent
                )
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(true)

                builder.addAction(actionBuilder.build())
            }

            // ------------------------------------
            // Like / Button action
            // ------------------------------------
            "like", "button" -> {
                val actionIntent = createActionIntent(action, data)

                val actionPendingIntent = PendingIntent.getBroadcast(
                    context,
                    action.id.hashCode(),
                    actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                builder.addAction(
                    R.drawable.ic_notification,
                    action.label,
                    actionPendingIntent
                )
            }

            // ------------------------------------
            // View / Navigate action
            // ------------------------------------
            "view", "navigate" -> {
                data.url?.let { url ->
                    val viewIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("url", url)
                        putExtra("action", action.id)
                    }

                    val viewPendingIntent = PendingIntent.getActivity(
                        context,
                        action.id.hashCode(),
                        viewIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    builder.addAction(
                        R.drawable.ic_notification,
                        action.label,
                        viewPendingIntent
                    )
                }
            }
        }
    }

    private fun createActionIntent(
        action: NotificationAction,
        data: NotificationData
    ): Intent {
        return Intent("com.murari.careerpolitics.NOTIFICATION_ACTION").apply {
            putExtra("action_id", action.id)
            putExtra("action_type", action.type)
            putExtra("notification_type", data.notificationType)
            putExtra("url", data.url)
        }
    }


}
