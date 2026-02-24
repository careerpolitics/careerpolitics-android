package com.murari.careerpolitics.core.webview.lifecycle

import android.webkit.WebView

interface WebViewAttachable {
    fun attach(webView: WebView)
    fun detach(webView: WebView)
}
