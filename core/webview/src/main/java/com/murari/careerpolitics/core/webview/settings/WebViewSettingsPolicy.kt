package com.murari.careerpolitics.core.webview.settings

import android.webkit.WebSettings
import android.webkit.WebView

class WebViewSettingsPolicy(
    private val config: WebViewSettingsConfig
) {
    fun apply(webView: WebView) {
        WebView.setWebContentsDebuggingEnabled(config.enableDebugging)
        with(webView.settings) {
            javaScriptEnabled = config.enableJavaScript
            domStorageEnabled = config.enableDomStorage
            userAgentString = config.userAgent
            allowFileAccess = false
            allowContentAccess = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }
    }
}

data class WebViewSettingsConfig(
    val enableDebugging: Boolean,
    val enableJavaScript: Boolean,
    val enableDomStorage: Boolean,
    val userAgent: String
)
