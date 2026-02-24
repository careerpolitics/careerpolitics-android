package com.murari.careerpolitics.feature.deeplink.domain

import android.content.Intent

interface DeepLinkRepository {
    fun extractCandidateUrl(intent: Intent?): String?
    fun isValidAppDeepLink(url: String): Boolean
}
