package com.murari.careerpolitics.util

import android.view.MotionEvent
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.webclients.CustomWebChromeClient
import com.murari.careerpolitics.webclients.CustomWebViewClient
import com.murari.careerpolitics.util.network.OfflineWebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs

/**
 * Encapsulates all WebView initialisation, settings, and edge-swipe logic.
 *
 * Keeps [MainActivity] free of low-level WebView configuration details.
 */
class WebViewManager(
    private val webView: WebView,
    private val bridge: AndroidWebViewBridge,
    private val scope: CoroutineScope,
    private val chromeClientListener: CustomWebChromeClient.CustomListener,
    private val onPageFinish: () -> Unit,
    private val onGoogleNativeSignInRequested: (String) -> Boolean
) {

    lateinit var webViewClient: OfflineWebViewClient
        private set

    fun setup() {
        configureSettings()
        attachBridge()
        attachClients()
        setupLeftEdgeSwipeForSidebar()
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    private fun configureSettings() {
        WebView.setWebContentsDebuggingEnabled(AppConfig.enableWebViewDebugging)

        with(webView.settings) {
            javaScriptEnabled = AppConfig.enableJavaScript
            domStorageEnabled = AppConfig.enableDomStorage
            userAgentString = AppConfig.userAgent
            allowFileAccess = false
            allowContentAccess = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            @Suppress("DEPRECATION")
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }
    }

    // ------------------------------------------------------------------
    // Clients & bridge
    // ------------------------------------------------------------------

    private fun attachBridge() {
        webView.addJavascriptInterface(bridge, "AndroidBridge")
    }

    private fun attachClients() {
        val context = webView.context
        webViewClient = OfflineWebViewClient(
            context,
            webView,
            scope,
            onPageFinish = onPageFinish,
            onGoogleNativeSignInRequested = onGoogleNativeSignInRequested
        )
        webView.webViewClient = webViewClient as WebViewClient
        webView.webChromeClient = CustomWebChromeClient(AppConfig.baseUrl, chromeClientListener)
        bridge.webViewClient = webViewClient as CustomWebViewClient
    }

    // ------------------------------------------------------------------
    // Edge swipe → sidebar
    // ------------------------------------------------------------------

    private fun setupLeftEdgeSwipeForSidebar() {
        val density = webView.resources.displayMetrics.density
        val edgeWidthPx = (AppConfig.EDGE_SWIPE_WIDTH_DP * density).toInt()
        val triggerDxPx = (AppConfig.EDGE_SWIPE_TRIGGER_DP * density).toInt()
        var downX = 0f
        var downY = 0f
        var eligible = false
        var triggered = false

        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x; downY = event.y
                    eligible = downX <= edgeWidthPx; triggered = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (eligible && !triggered) {
                        val dx = event.x - downX; val dy = event.y - downY
                        if (dx > triggerDxPx && abs(dx) > 2 * abs(dy)) {
                            triggered = true; openWebSidebar(); false
                        } else false
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    eligible = false; triggered = false; false
                }
                else -> false
            }
        }
    }

    private fun openWebSidebar() {
        val js = """
            (function(){
                function q(){return document.querySelector('button.js-hamburger-trigger,[data-sidebar-toggle],button[aria-label*="menu" i],button[aria-label*="navigation" i],.hamburger,.menu,.drawer-toggle,.navbar-toggle,[data-testid="menu-button"],[data-action="open-sidebar"]');}
                function simulate(el){try{el.focus();}catch(e){} var o={bubbles:true,cancelable:true}; try{el.dispatchEvent(new PointerEvent('pointerdown',o));}catch(e){} try{el.dispatchEvent(new MouseEvent('mousedown',o));}catch(e){} try{el.dispatchEvent(new MouseEvent('click',o));}catch(e){} try{el.dispatchEvent(new MouseEvent('mouseup',o));}catch(e){} try{el.dispatchEvent(new PointerEvent('pointerup',o));}catch(e){} }
                var el=q(); if(el){simulate(el); return true;} return false;
            })()
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // ------------------------------------------------------------------
    // Lifecycle helpers
    // ------------------------------------------------------------------

    fun destroy() {
        webView.clearHistory()
        webView.clearCache(true)
        webView.loadUrl("about:blank")
        webView.onPause()
        webView.removeAllViews()
        webView.destroy()
    }
}