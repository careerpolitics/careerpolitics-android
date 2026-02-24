package com.murari.careerpolitics.core.webview.navigation

import androidx.core.net.toUri

data class WebNavigationConfig(
    val overrideUrlList: List<String>,
    val googleAuthHosts: Set<String>,
    val baseDomain: String,
    val clearCacheRoutes: List<String>
)

sealed interface WebNavigationDecision {
    data object AllowInWebView : WebNavigationDecision
    data class StartNativeGoogleSignIn(val url: String, val clearCache: Boolean) : WebNavigationDecision
    data class OpenExternal(val url: String, val clearCache: Boolean) : WebNavigationDecision
}

class WebNavigationDecisionEngine(
    private val config: WebNavigationConfig
) {
    fun evaluate(url: String): WebNavigationDecision {
        val host = url.toUri().host.orEmpty().lowercase()
        val clearCache = config.clearCacheRoutes.any { url.startsWith(it) }

        if (shouldStartNativeGoogleSignIn(host, url)) {
            return WebNavigationDecision.StartNativeGoogleSignIn(url, clearCache)
        }

        if (config.overrideUrlList.any { url.contains(it) }) {
            return WebNavigationDecision.AllowInWebView
        }

        return WebNavigationDecision.OpenExternal(url, clearCache)
    }

    private fun shouldStartNativeGoogleSignIn(host: String, url: String): Boolean {
        if (host in config.googleAuthHosts) return true

        if (host.endsWith(config.baseDomain)) {
            val normalizedPath = url.toUri().path.orEmpty().lowercase()
            if (normalizedPath.contains("google") && normalizedPath.contains("auth")) {
                return true
            }
        }

        return false
    }
}
