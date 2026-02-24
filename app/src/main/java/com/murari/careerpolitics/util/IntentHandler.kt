package com.murari.careerpolitics.util

import android.content.Intent
import android.webkit.WebView
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.services.PushNotificationService

/**
 * Handles incoming intents for the main activity:
 *  - Android App Links (deep links)
 *  - Push-notification click intents
 *
 * Keeps intent-parsing logic out of the Activity.
 */
object IntentHandler {

    private const val LOG_TAG = "IntentHandler"

    /**
     * Attempt to handle an App-Link deep-link intent.
     * @return `true` if the URL was loaded in the WebView.
     */
    fun handleAppLink(intent: Intent?, webView: WebView): Boolean {
        val deepLinkUrl = intent?.dataString ?: return false

        if (!AppConfig.isValidAppUrl(deepLinkUrl)) {
            Logger.d(LOG_TAG, "Ignoring non-app deep link: $deepLinkUrl")
            return false
        }

        Logger.d(LOG_TAG, "Opening app link in WebView: $deepLinkUrl")
        webView.loadUrl(deepLinkUrl)
        return true
    }

    /**
     * Handle a push-notification click intent.
     * Loads the notification URL, marks the notification as read, and cleans up extras.
     */
    fun handleNotification(intent: Intent?, webView: WebView) {
        if (intent == null) return

        try {
            val url = intent.getStringExtra("url")
            val notificationType = intent.getStringExtra("notification_type")
            val actionType = intent.getStringExtra("action")

            if (url.isNullOrBlank()) {
                Logger.d(LOG_TAG, "Notification intent without URL, ignoring")
                return
            }

            webView.loadUrl(url)

            // Fire-and-forget mark notification as read on the backend
            intent.getStringExtra("notification_id")?.let { id ->
                PushNotificationService.markNotificationRead(webView.context, id)
            }

            Logger.d(
                LOG_TAG,
                "Notification clicked: type=$notificationType, action=$actionType, url=$url"
            )

            intent.removeExtra("url")
            intent.removeExtra("notification_type")
            intent.removeExtra("notification_id")
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Failed to handle notification intent", e)
        }
    }
}