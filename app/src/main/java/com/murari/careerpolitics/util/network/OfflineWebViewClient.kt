package com.murari.careerpolitics.util.network

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.*
import androidx.browser.customtabs.CustomTabsIntent
import com.murari.careerpolitics.events.NetworkStatusEvent
import com.murari.careerpolitics.webclients.CustomWebViewClient
import com.pusher.pushnotifications.PushNotifications
import kotlinx.coroutines.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject

open class OfflineWebViewClient(
    private val context: Context,
    private val view: WebView,
    private val coroutineScope: CoroutineScope,
    private val onPageFinish: () -> Unit
) : CustomWebViewClient(context, view, coroutineScope, onPageFinish) {

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        // No caching logic needed
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        // Let all requests go through normally
        return super.shouldInterceptRequest(view, request)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        val url = request?.url?.toString()
        
        if (url != null) {
            // Check network status in background to avoid NetworkOnMainThreadException
            coroutineScope.launch {
                if (!NetworkUtils.isOnline()) {
                    Log.d("OfflineWebViewClient", "No internet connection, showing offline page")
                    // Load the offline page
                    view?.post {
                        view.loadUrl("file:///android_asset/offline.html")
                    }
                }
            }
        }
        
        super.onReceivedError(view, request, error)
    }

    /**
     * Observe network changes (inherited from CustomWebViewClient)
     */
    override fun observeNetwork() {
        super.observeNetwork()
    }

    /**
     * Unobserve network changes (inherited from CustomWebViewClient)
     */
    override fun unobserveNetwork() {
        super.unobserveNetwork()
    }
}
