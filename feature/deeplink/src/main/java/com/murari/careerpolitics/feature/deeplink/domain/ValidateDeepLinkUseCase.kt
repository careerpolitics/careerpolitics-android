package com.murari.careerpolitics.feature.deeplink.domain

import javax.inject.Inject

class ValidateDeepLinkUseCase @Inject constructor(
    private val repository: DeepLinkRepository
) {
    operator fun invoke(url: String): Boolean = repository.isValidAppDeepLink(url)
}
