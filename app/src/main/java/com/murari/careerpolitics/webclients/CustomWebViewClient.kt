package com.murari.careerpolitics.webclients

import android.content.Context
import android.graphics.Color
import android.net.Uri
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.util.Logger
import android.view.View
import android.webkit.*
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import com.murari.careerpolitics.events.NetworkStatusEvent
import com.murari.careerpolitics.services.PushNotificationService
import com.murari.careerpolitics.util.network.NetworkStatus
import com.murari.careerpolitics.util.network.NetworkUtils
import com.murari.careerpolitics.util.network.NetworkWatcher
import com.pusher.pushnotifications.PushNotifications
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject

open class CustomWebViewClient(
    private val context: Context,
    private val view: WebView,
    private val coroutineScope: CoroutineScope,
    private val onPageFinish: () -> Unit,
    private val onGoogleNativeSignInRequested: (() -> Boolean)? = null
) : WebViewClient() {

    companion object {
        private const val LOG_TAG = "CustomWebViewClient"
    }

    // URLs that should NOT be opened in external browser (OAuth flows, internal pages)
    private val overrideUrlList = listOf(
        AppConfig.baseDomain, // Our base domain
        "api.twitter.com/oauth",
        "api.twitter.com/login/error",
        "api.twitter.com/account/login_verification",
        "github.com/login",
        "github.com/sessions/"
    )

    private val googleAuthHosts = setOf(
        "accounts.google.com",
        "oauth2.googleapis.com"
    )

    private var registeredUserNotifications = false
    private var networkWatcher: NetworkWatcher? = null
    private var registeredFcmToken = false

    override fun onPageFinished(view: WebView, url: String?) {
        view.visibility = View.VISIBLE
        onPageFinish()

        if (shouldFinishNativeGoogleCallback(url)) {
            Logger.d(LOG_TAG, "Native Google callback loaded, redirecting to app home")
            view.loadUrl(AppConfig.baseUrl)
            super.onPageFinished(view, url)
            return
        }

        checkAndRegisterFcmToken(view)
        super.onPageFinished(view, url)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        val script = "(function(){const body=document.body; if(!body){return null;} const raw=body.getAttribute(\"data-user\"); if(!raw){return null;} try{return JSON.parse(raw)?.id ?? null;}catch(e){return null;}})();"
        view?.evaluateJavascript(script) { result ->
            try {
                if (result.isNullOrBlank() || result == "null") {
                    return@evaluateJavascript
                }

                val userId = result.trim('"').toInt()
                if (!registeredUserNotifications) {
                    PushNotifications.addDeviceInterest("user-notifications-$userId")
                    registeredUserNotifications = true
                    Logger.d(LOG_TAG, "Registered device for user-$userId notifications")
                }

                if(!registeredFcmToken){
                    Logger.d(LOG_TAG, "User authenticated detected ( user_id: $userId), registering FCM token")
                    PushNotificationService.registerFcmToken(context)
                    registeredFcmToken = true
                }
            } catch (e: Exception) {
                Logger.d(LOG_TAG, "Could not parse user ID from body data-user attribute")
            }
        }
        super.doUpdateVisitedHistory(view, url, isReload)
    }

    private fun checkAndRegisterFcmToken(webView: WebView) {
        // 1. Don't proceed if the token has already been registered in this session.
        if (registeredFcmToken) {
            return
        }

        // 2. Check for the session cookie to quickly determine if the user might be logged in.
        val cookies = CookieManager.getInstance().getCookie(AppConfig.baseUrl).orEmpty()
        val hasSessionCookie = cookies.isNotBlank() && cookies.contains("_careerpolitics_session")

        if (!hasSessionCookie) {
            return
        }

        // 3. As a final confirmation, check the 'user-signed-in' meta tag within the web page.
        // This confirms the user's session is active on the frontend.
        val script = "(function(){const node=document.querySelector(\"meta[name=\\\"user-signed-in\\\"]\"); return node ? node.content : null;})();"

        webView.evaluateJavascript(script) { result ->
            val isSignedIn = result?.trim('"') == "true"

            if (isSignedIn) {
                Logger.d(LOG_TAG, "User authenticated detected. Registering FCM token.")

                // Trigger the service to get the FCM token and send it to the backend.
                PushNotificationService.registerFcmToken(context)

                // Mark as registered to prevent this check from running again.
                registeredFcmToken = true
            }
        }
    }


    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        return handleUrlOverride(view, url)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url?.toString().orEmpty()
        return handleUrlOverride(view, url)
    }

    private fun handleUrlOverride(view: WebView, url: String): Boolean {
        Logger.d(LOG_TAG, "Intercepting URL: $url")

        val parsedUri = url.toUri()
        val host = parsedUri.host.orEmpty().lowercase()

        // Clear cache/cookies on specific routes (e.g., login/logout)
        if (AppConfig.clearCacheRoutes.any { url.startsWith(it) }) {
            Logger.d(LOG_TAG, "Clearing cache for route: $url")
            view.clearCache(true)
            view.clearFormData()
            view.clearHistory()
            CookieManager.getInstance().apply {
            removeAllCookies(null)
            }
        }

        if (shouldStartNativeGoogleSignIn(host, url)) {
            val handledByNativeFlow = onGoogleNativeSignInRequested?.invoke() == true
            if (handledByNativeFlow) {
                Logger.d(LOG_TAG, "Starting native Google sign-in flow")
                return true
            }

            Logger.w(LOG_TAG, "Native Google sign-in unavailable, falling back to Custom Tab")
            launchCustomTab(url)
            return true
        }

        if (overrideUrlList.any { url.contains(it) }) return false

        launchCustomTab(url)

        return true
    }

    private fun shouldStartNativeGoogleSignIn(host: String, url: String): Boolean {
        if (host in googleAuthHosts) return true

        if (host.endsWith(AppConfig.baseDomain)) {
            val normalizedPath = url.toUri().path.orEmpty().lowercase()
            if (normalizedPath.contains("google") && normalizedPath.contains("auth")) {
                return true
            }
        }

        return false
    }

    private fun launchCustomTab(url: String) {
        CustomTabsIntent.Builder()
            .setToolbarColor(Color.TRANSPARENT)
            .build()
            .launchUrl(context, Uri.parse(url))
    }


    private fun shouldFinishNativeGoogleCallback(url: String?): Boolean {
        val currentUrl = url ?: return false
        val callbackPath = AppConfig.nativeGoogleLoginCallbackPath
            .trim()
            .let { if (it.startsWith("/")) it else "/$it" }

        val uri = currentUrl.toUri()
        return uri.host?.endsWith(AppConfig.baseDomain) == true && uri.path == callbackPath
    }

    fun sendBridgeMessage(type: String, message: Map<String, Any>) {
        val json = JSONObject(message).toString()
        val script = when (type) {
            "podcast" -> "document.getElementById('audiocontent')?.setAttribute('data-podcast', '$json')"
            "video" -> "document.getElementById('video-player-source')?.setAttribute('data-message', '$json')"
            else -> return
        }
        view.post { view.evaluateJavascript(script, null) }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)

        coroutineScope.launch {
            if (NetworkUtils.isOffline()) {
                EventBus.getDefault().post(NetworkStatusEvent(NetworkStatus.OFFLINE))
            }
        }
    }

    private fun registerNetworkWatcher() {
        if (networkWatcher == null) {
            networkWatcher = NetworkWatcher(coroutineScope).also {
                context.registerReceiver(it, NetworkWatcher.intentFilter)
            }
        }
    }

    private fun unregisterNetworkWatcher() {
        try {
            networkWatcher?.let {
                context.unregisterReceiver(it)
                networkWatcher = null
            }
        } catch (e: IllegalArgumentException) {
            Logger.d(LOG_TAG, "Network watcher receiver already unregistered")
        }
    }

    @Subscribe(threadMode = ThreadMode.BACKGROUND)
    fun onNetworkStatusEvent(event: NetworkStatusEvent) {
        when (event.networkStatus) {
            NetworkStatus.OFFLINE -> registerNetworkWatcher()
            NetworkStatus.BACK_ONLINE -> {
                unregisterNetworkWatcher()
                coroutineScope.launch {
                    withContext(Dispatchers.Main) {
                        val url = view.url
                        if (!url.isNullOrEmpty()) {
                            view.loadUrl(url)
                        }
                    }
                }
            }
        }
    }

    open fun observeNetwork() {
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    open fun unobserveNetwork() {
        coroutineScope.cancel()
        EventBus.getDefault().unregister(this)
        unregisterNetworkWatcher()
    }
}
