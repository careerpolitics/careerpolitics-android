package com.murari.careerpolitics.feature.deeplink.presentation

import android.content.Intent
import com.murari.careerpolitics.feature.deeplink.domain.DeepLinkRepository
import com.murari.careerpolitics.feature.deeplink.domain.ParseDeepLinkUseCase
import com.murari.careerpolitics.feature.deeplink.domain.ResolveDeepLinkUseCase
import com.murari.careerpolitics.feature.deeplink.domain.ResolvedDeepLink
import com.murari.careerpolitics.feature.deeplink.domain.ValidateDeepLinkUseCase
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkViewModelIntegrationTest {

    @Test
    fun `resolves allowed host as valid`() {
        val repo = object : DeepLinkRepository {
            override fun extractCandidateUrl(intent: Intent?): String? = "https://careerpolitics.com/welcome"
            override fun isValidAppDeepLink(url: String): Boolean = true
        }
        val vm = DeepLinkViewModel(
            ResolveDeepLinkUseCase(ParseDeepLinkUseCase(repo), ValidateDeepLinkUseCase(repo))
        )

        val result = vm.resolve(Intent())
        assertTrue(result is ResolvedDeepLink.Valid)
    }
}
