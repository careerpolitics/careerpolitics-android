package com.murari.careerpolitics.feature.deeplink.data

import android.content.Intent
import android.net.Uri
import com.murari.careerpolitics.feature.deeplink.domain.DeepLinkRepository

class AndroidDeepLinkRepository(
    private val allowedSchemes: Set<String>,
    private val allowedHosts: Set<String>
) : DeepLinkRepository {

    override fun extractCandidateUrl(intent: Intent?): String? = intent?.dataString

    override fun isValidAppDeepLink(url: String): Boolean {
        val uri = Uri.parse(url)
        val scheme = uri.scheme ?: return false
        val host = uri.host ?: return false
        return allowedSchemes.contains(scheme.lowercase()) &&
            allowedHosts.contains(host.lowercase())
    }
}
