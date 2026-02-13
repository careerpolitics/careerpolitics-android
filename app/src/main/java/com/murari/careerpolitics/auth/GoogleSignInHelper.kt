package com.murari.careerpolitics.auth

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.util.Logger
import androidx.core.net.toUri

object GoogleSignInHelper {

    private const val LOG_TAG = "GoogleSignInHelper"

    const val OAUTH_CALLBACK_SCHEME = "careerpolitics"
    const val OAUTH_CALLBACK_HOST = "oauth"

    private val googleOAuthPatterns = listOf(
        "accounts.google.com/o/oauth2",
        "accounts.google.com/signin",
        "accounts.google.com/v3/signin",
        "accounts.google.com/AccountChooser"
    )

    private val oauthCallbackPatterns = listOf(
        "${AppConfig.baseUrl}/users/auth/google_oauth2/callback",
        "${AppConfig.baseDomain}/users/auth/google_oauth2/callback",
        "$OAUTH_CALLBACK_SCHEME://$OAUTH_CALLBACK_HOST"
    )

    /**
     * Check whether the URL is a Google OAuth authorization URL.
     * This should be opened via Chrome Custom Tabs instead of WebView.
     */
    fun isGoogleOAuthUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        return googleOAuthPatterns.any { pattern ->
            url.contains(pattern, ignoreCase = true)
        }
    }

    /**
     * Check whether the URL is the OAuth callback deep link
     * that should be intercepted by the app.
     */
    fun isOAuthCallbackUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false

        return oauthCallbackPatterns.any { pattern ->
            url.contains(pattern, ignoreCase = true)
        } || url.startsWith("$OAUTH_CALLBACK_SCHEME://", ignoreCase = true)
    }

    /**
     * Convert the intercepted deep link back to a proper web URL
     * so that it can be loaded inside WebView.
     */
    fun getWebCallbackUrl(deeplinkUri: Uri?): String? {
        if (deeplinkUri == null) return null

        return if (deeplinkUri.scheme.equals(OAUTH_CALLBACK_SCHEME, ignoreCase = true)) {

            val path = deeplinkUri.path ?: ""
            val query = deeplinkUri.query?.let { "?$it" } ?: ""

            "${AppConfig.baseUrl}/users/auth/google_oauth2/callback$path$query"

        } else {
            deeplinkUri.toString()
        }
    }

    /**
     * Launch Google OAuth URL in Chrome Custom Tabs.
     *
     * @param context Activity context
     * @param url OAuth authorization URL
     * @return true if launched successfully, false otherwise
     */
    fun launchGoogleOAuth(context: Context, url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!isGoogleOAuthUrl(url)) return false

        return try {
            Logger.d(LOG_TAG, "Launching Google OAuth in Chrome Custom Tabs: $url")

            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .setUrlBarHidingEnabled(false)
                .setInstantAppsEnabled(false)
                .build()

            customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

            customTabsIntent.launchUrl(context, url.toUri())

            true
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Failed to launch Chrome Custom Tabs for Google OAuth", e)

            try {
                val intent= Intent(Intent.ACTION_VIEW, (url.toUri()))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }
            catch (e2: Exception){
                Logger.e(LOG_TAG,"Failed to open Google OAuth URL in any browser",e2)
                false
            }
        }
    }
}
