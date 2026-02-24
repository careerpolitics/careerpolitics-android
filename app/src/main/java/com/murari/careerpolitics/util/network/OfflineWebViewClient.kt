package com.murari.careerpolitics.util.network

import android.content.Context
import android.util.Log
import android.webkit.*
import com.murari.careerpolitics.webclients.CustomWebViewClient
import kotlinx.coroutines.*

class OfflineWebViewClient(
    private val context: Context,
    private val view: WebView,
    private val coroutineScope: CoroutineScope,
    private val onPageFinish: () -> Unit,
    private val onGoogleNativeSignInRequested: ((String) -> Boolean)? = null
) : CustomWebViewClient(context, view, coroutineScope, onPageFinish, onGoogleNativeSignInRequested) {

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
