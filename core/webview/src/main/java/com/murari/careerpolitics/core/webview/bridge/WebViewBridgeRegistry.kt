package com.murari.careerpolitics.core.webview.bridge

import android.annotation.SuppressLint
import android.os.Build
import android.webkit.WebView
import com.murari.careerpolitics.core.webview.lifecycle.WebViewAttachable

class WebViewBridgeRegistry : WebViewAttachable {
    private val bridges = mutableMapOf<String, Any>()

    fun register(name: String, bridge: Any) {
        bridges[name] = bridge
    }

    @SuppressLint("JavascriptInterface")
    override fun attach(webView: WebView) {
        bridges.forEach { (name, bridge) -> webView.addJavascriptInterface(bridge, name) }
    }

    override fun detach(webView: WebView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            bridges.keys.forEach(webView::removeJavascriptInterface)
        }
    }
}
