package com.murari.careerpolitics.activities.main

import android.view.MotionEvent
import android.webkit.WebSettings
import android.webkit.WebView
import com.murari.careerpolitics.activities.MainActivity
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.util.AndroidWebViewBridge
import com.murari.careerpolitics.util.network.OfflineWebViewClient
import com.murari.careerpolitics.webclients.CustomWebChromeClient
import com.murari.careerpolitics.webclients.CustomWebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs

class MainWebViewConfigurator(
    private val activity: MainActivity,
    private val scope: CoroutineScope,
    private val bridge: AndroidWebViewBridge,
    private val onNativeGoogleSignInRequested: (String) -> Boolean,
    private val onPageFinished: () -> Unit
) {

    fun configure(webView: WebView): OfflineWebViewClient {
        WebView.setWebContentsDebuggingEnabled(AppConfig.webViewConfig.enableDebugging)

        with(webView.settings) {
            javaScriptEnabled = AppConfig.webViewConfig.enableJavaScript
            domStorageEnabled = AppConfig.webViewConfig.enableDomStorage
            userAgentString = AppConfig.webViewConfig.userAgent
            allowFileAccess = false
            allowContentAccess = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }

        webView.addJavascriptInterface(bridge, JS_BRIDGE_NAME)

        val webViewClient = OfflineWebViewClient(
            activity,
            webView,
            scope,
            onPageFinish = onPageFinished,
            onGoogleNativeSignInRequested = onNativeGoogleSignInRequested
        )

        webView.webViewClient = webViewClient
        webView.webChromeClient = CustomWebChromeClient(AppConfig.baseUrl, activity)
        bridge.webViewClient = webViewClient as CustomWebViewClient

        setupLeftEdgeSwipeForSidebar(webView)
        return webViewClient
    }

    private fun setupLeftEdgeSwipeForSidebar(webView: WebView) {
        val edgeWidthPx = (AppConfig.webViewConfig.edgeSwipeWidthDp * webView.resources.displayMetrics.density).toInt()
        val triggerDxPx = (AppConfig.webViewConfig.edgeSwipeTriggerDp * webView.resources.displayMetrics.density).toInt()
        var downX = 0f
        var downY = 0f
        var eligible = false
        var triggered = false

        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    eligible = downX <= edgeWidthPx
                    triggered = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (eligible && !triggered) {
                        val dx = event.x - downX
                        val dy = event.y - downY
                        if (dx > triggerDxPx && abs(dx) > 2 * abs(dy)) {
                            triggered = true
                            openWebSidebar(webView)
                        }
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    eligible = false
                    triggered = false
                    false
                }
                else -> false
            }
        }
    }

    private fun openWebSidebar(webView: WebView) {
        val js = """
            (function(){
                function q(){return document.querySelector('button.js-hamburger-trigger,[data-sidebar-toggle],button[aria-label*="menu" i],button[aria-label*="navigation" i],.hamburger,.menu,.drawer-toggle,.navbar-toggle,[data-testid="menu-button"],[data-action="open-sidebar"]');}
                function simulate(el){try{el.focus();}catch(e){} var o={bubbles:true,cancelable:true}; try{el.dispatchEvent(new PointerEvent('pointerdown',o));}catch(e){} try{el.dispatchEvent(new MouseEvent('mousedown',o));}catch(e){} try{el.dispatchEvent(new TouchEvent('touchstart',o));}catch(e){} try{el.dispatchEvent(new MouseEvent('click',o));}catch(e){} try{el.dispatchEvent(new MouseEvent('mouseup',o));}catch(e){} try{el.dispatchEvent(new PointerEvent('pointerup',o));}catch(e){} }
                var el=q(); if(el){simulate(el); return true;} return false;
            })()
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    companion object {
        private const val JS_BRIDGE_NAME = "Android"
    }
}
