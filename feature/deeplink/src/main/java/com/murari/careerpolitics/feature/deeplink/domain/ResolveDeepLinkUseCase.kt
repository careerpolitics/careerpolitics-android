package com.murari.careerpolitics.feature.deeplink.domain

import android.content.Intent
import javax.inject.Inject

class ResolveDeepLinkUseCase @Inject constructor(
    private val parseDeepLink: ParseDeepLinkUseCase,
    private val validateDeepLink: ValidateDeepLinkUseCase
) {
    operator fun invoke(intent: Intent?): ResolvedDeepLink {
        val candidate = parseDeepLink(intent) ?: return ResolvedDeepLink.None
        return if (validateDeepLink(candidate)) {
            ResolvedDeepLink.Valid(candidate)
        } else {
            ResolvedDeepLink.Invalid(candidate)
        }
    }
}

sealed interface ResolvedDeepLink {
    data class Valid(val url: String) : ResolvedDeepLink
    data class Invalid(val candidate: String) : ResolvedDeepLink
    data object None : ResolvedDeepLink
}
