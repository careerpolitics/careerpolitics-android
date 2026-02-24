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
        val shouldStartNativeGoogle = shouldStartNativeGoogleSignIn(host, url)
        val shouldAllowInWebView = config.overrideUrlList.any { url.contains(it) }

        return when {
            shouldStartNativeGoogle -> WebNavigationDecision.StartNativeGoogleSignIn(url, clearCache)
            shouldAllowInWebView -> WebNavigationDecision.AllowInWebView
            else -> WebNavigationDecision.OpenExternal(url, clearCache)
        }
    }

    private fun shouldStartNativeGoogleSignIn(host: String, url: String): Boolean {
        val isKnownGoogleAuthHost = host in config.googleAuthHosts
        val isProjectHost = host.endsWith(config.baseDomain)
        val normalizedPath = url.toUri().path.orEmpty().lowercase()
        val isGoogleAuthPath = normalizedPath.contains("google") && normalizedPath.contains("auth")
        return isKnownGoogleAuthHost || (isProjectHost && isGoogleAuthPath)
    }
}
