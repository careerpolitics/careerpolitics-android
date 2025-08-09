package com.murari.careerpolitics.webclients

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.browser.customtabs.CustomTabsIntent
import com.murari.careerpolitics.events.NetworkStatusEvent
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
    private val onPageFinish: () -> Unit
) : WebViewClient() {

    private val overrideUrlList = listOf(
        "careerpolitics",
        "api.twitter.com/oauth",
        "api.twitter.com/login/error",
        "api.twitter.com/account/login_verification",
        "accounts.google.com",
        "github.com/login",
        "github.com/sessions/"
    )

    private var registeredUserNotifications = false
    private var networkWatcher: NetworkWatcher? = null

    override fun onPageFinished(view: WebView, url: String?) {
        view.visibility = View.VISIBLE
        onPageFinish()
        // Signal page-loaded to activity via a JS bridge callback
        // Soft signal of page load; Activity can also manage readiness
        // Inject helper to open sidebar from native reliably
        val injectSidebarHelper = (
            "(function(){" +
                "if(window.__nativeSidebar&&window.__nativeSidebar.__v==1)return true;" +
                "window.__nativeSidebar=(function(){" +
                    "function q(){return document.querySelector('button.js-hamburger-trigger,[data-sidebar-toggle],button[aria-label*\\u003d\"menu\" i],button[aria-label*\\u003d\"navigation\" i],.hamburger,.menu,.drawer-toggle,.navbar-toggle,[data-testid\\u003d\"menu-button\"],[data-action\\u003d\"open-sidebar\"]');}" +
                    "function isOpen(){return !!document.querySelector('.crayons-drawer--left.open,.crayons-drawer.open,.nav--open,.is-open,.menu-open,body.sidebar-open');}" +
                    "function simulate(el){try{el.focus();}catch(e){} var opts={bubbles:true,cancelable:true}; try{el.dispatchEvent(new PointerEvent('pointerdown',opts));}catch(e){} try{el.dispatchEvent(new MouseEvent('mousedown',opts));}catch(e){} try{el.dispatchEvent(new TouchEvent('touchstart',opts));}catch(e){} try{el.dispatchEvent(new MouseEvent('click',opts));}catch(e){} try{el.dispatchEvent(new MouseEvent('mouseup',opts));}catch(e){} try{el.dispatchEvent(new PointerEvent('pointerup',opts));}catch(e){} }" +
                    "function open(){if(isOpen())return true;var el=q();if(el){simulate(el);return true;}return false;}" +
                    "return {open:open,isOpen:isOpen,__v:1};" +
                "})();" +
                // Listen for native event to trigger open
                "window.addEventListener('native-open-sidebar', function(){ try{window.__nativeSidebar&&__nativeSidebar.open();}catch(e){} }, {passive:true});" +
                "true;" +
            "})();"
        )
        view.evaluateJavascript(injectSidebarHelper, null)
        super.onPageFinished(view, url)
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        // Early inject to help SPAs
        val injectSidebarHelperEarly = (
            "(function(){" +
                "if(window.__nativeSidebar&&window.__nativeSidebar.__v==1)return true;" +
                "window.__nativeSidebar=(function(){" +
                    "function q(){return document.querySelector('button.js-hamburger-trigger,[data-sidebar-toggle],button[aria-label*\\u003d\"menu\" i],button[aria-label*\\u003d\"navigation\" i],.hamburger,.menu,.drawer-toggle,.navbar-toggle,[data-testid\\u003d\"menu-button\"],[data-action\\u003d\"open-sidebar\"]');}" +
                    "function isOpen(){return !!document.querySelector('.crayons-drawer--left.open,.crayons-drawer.open,.nav--open,.is-open,.menu-open,body.sidebar-open');}" +
                    "function simulate(el){try{el.focus();}catch(e){} var opts={bubbles:true,cancelable:true}; try{el.dispatchEvent(new PointerEvent('pointerdown',opts));}catch(e){} try{el.dispatchEvent(new MouseEvent('mousedown',opts));}catch(e){} try{el.dispatchEvent(new TouchEvent('touchstart',opts));}catch(e){} try{el.dispatchEvent(new MouseEvent('click',opts));}catch(e){} try{el.dispatchEvent(new MouseEvent('mouseup',opts));}catch(e){} try{el.dispatchEvent(new PointerEvent('pointerup',opts));}catch(e){} }" +
                    "function open(){if(isOpen())return true;var el=q();if(el){simulate(el);return true;}return false;}" +
                    "return {open:open,isOpen:isOpen,__v:1};" +
                "})();" +
                "true;" +
            "})();"
        )
        view?.evaluateJavascript(injectSidebarHelperEarly, null)
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        val script = "JSON.parse(document.getElementsByTagName('body')[0].getAttribute('data-user')).id"
        view?.evaluateJavascript(script) { result ->
            try {
                val userId = result.trim('"').toInt()
                if (!registeredUserNotifications) {
                    PushNotifications.addDeviceInterest("user-notifications-$userId")
                    registeredUserNotifications = true
                }
            } catch (e: Exception) {
                Log.e("WebViewClient", "Error parsing user ID: $result", e)
            }
        }
        super.doUpdateVisitedHistory(view, url, isReload)
    }

    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        Log.i("WebViewClient", "Intercepting URL: $url")

        if (url == "https://careerpolitics.com/enter") {
            view.clearCache(true)
            view.clearFormData()
            view.clearHistory()
            CookieManager.getInstance().apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                    removeAllCookie()
                } else {
                    removeAllCookies(null)
                }
            }
        }

        if (overrideUrlList.any { url.contains(it) }) return false

        CustomTabsIntent.Builder()
            .setToolbarColor(Color.TRANSPARENT)
            .build()
            .launchUrl(context, Uri.parse(url))

        return true
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
            Log.w("WebViewClient", "Receiver not registered or already unregistered", e)
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
