package com.murari.careerpolitics.activities.main

import android.content.Intent
import android.webkit.WebView
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.util.Logger

class MainIntentRouter(
    private val webViewProvider: () -> WebView?
) {

    fun handleAppLinkIntent(intent: Intent?): Boolean {
        val deepLinkUrl = intent?.dataString ?: return false

        if (!AppConfig.isValidAppUrl(deepLinkUrl)) {
            Logger.d(LOG_TAG, "Ignoring non-app deep link: $deepLinkUrl")
            return false
        }

        Logger.d(LOG_TAG, "Opening app link in WebView: $deepLinkUrl")
        webViewProvider()?.loadUrl(deepLinkUrl)
        return true
    }

    fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return

        try {
            val url = intent.getStringExtra(KEY_URL)
            val notificationType = intent.getStringExtra(KEY_NOTIFICATION_TYPE)
            val actionType = intent.getStringExtra(KEY_ACTION)

            if (url.isNullOrBlank()) {
                Logger.d(LOG_TAG, "Notification intent received without URL, ignoring")
                return
            }

            webViewProvider()?.loadUrl(url)

            Logger.d(
                LOG_TAG,
                "Notification clicked: type=$notificationType, action=$actionType, url=$url"
            )

            intent.removeExtra(KEY_URL)
            intent.removeExtra(KEY_NOTIFICATION_TYPE)
        } catch (exception: Exception) {
            Logger.e(LOG_TAG, "Failed to handle notification intent", exception)
        }
    }

    companion object {
        private const val LOG_TAG = "MainIntentRouter"
        private const val KEY_URL = "url"
        private const val KEY_NOTIFICATION_TYPE = "notification_type"
        private const val KEY_ACTION = "action"
    }
}
