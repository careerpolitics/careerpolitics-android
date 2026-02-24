package com.murari.careerpolitics.integration

import android.content.Intent
import com.murari.careerpolitics.feature.deeplink.domain.DeepLinkRepository
import com.murari.careerpolitics.feature.deeplink.domain.ParseDeepLinkUseCase
import com.murari.careerpolitics.feature.deeplink.domain.ResolveDeepLinkUseCase
import com.murari.careerpolitics.feature.deeplink.domain.ResolvedDeepLink
import com.murari.careerpolitics.feature.deeplink.domain.ValidateDeepLinkUseCase
import com.murari.careerpolitics.feature.deeplink.presentation.DeepLinkViewModel
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkRoutingIntegrationTest {
    @Test
    fun `invalid host is rejected`() {
        val repo = object : DeepLinkRepository {
            override fun extractCandidateUrl(intent: Intent?): String? = "https://evil.com/phish"
            override fun isValidAppDeepLink(url: String): Boolean = false
        }
        val vm = DeepLinkViewModel(ResolveDeepLinkUseCase(ParseDeepLinkUseCase(repo), ValidateDeepLinkUseCase(repo)))
        assertTrue(vm.resolve(Intent()) is ResolvedDeepLink.Invalid)
    }
}
