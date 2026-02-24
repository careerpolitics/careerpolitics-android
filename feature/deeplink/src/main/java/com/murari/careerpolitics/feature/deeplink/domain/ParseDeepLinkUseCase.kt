package com.murari.careerpolitics.feature.deeplink.domain

import android.content.Intent
import javax.inject.Inject

class ParseDeepLinkUseCase @Inject constructor(
    private val repository: DeepLinkRepository
) {
    operator fun invoke(intent: Intent?): String? = repository.extractCandidateUrl(intent)
}
