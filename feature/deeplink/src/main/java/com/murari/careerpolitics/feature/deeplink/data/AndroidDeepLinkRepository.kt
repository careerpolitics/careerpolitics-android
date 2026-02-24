package com.murari.careerpolitics.feature.deeplink.data

import android.content.Intent
import com.murari.careerpolitics.feature.deeplink.domain.DeepLinkRepository
import java.net.URI

class AndroidDeepLinkRepository(
    private val allowedSchemes: Set<String>,
    private val allowedHosts: Set<String>
) : DeepLinkRepository {

    override fun extractCandidateUrl(intent: Intent?): String? = intent?.dataString

    override fun isValidAppDeepLink(url: String): Boolean {
        val parsed = runCatching { URI(url) }.getOrNull()
        val scheme = parsed?.scheme.orEmpty().lowercase()
        val host = parsed?.host.orEmpty().lowercase()
        val hasValidParts = scheme.isNotBlank() && host.isNotBlank()
        return hasValidParts && allowedSchemes.contains(scheme) && allowedHosts.contains(host)
    }
}
