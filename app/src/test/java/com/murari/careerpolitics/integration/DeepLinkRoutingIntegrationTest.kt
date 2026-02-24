package com.murari.careerpolitics.integration

import android.content.Intent
import android.net.Uri
import com.murari.careerpolitics.feature.deeplink.data.AndroidDeepLinkRepository
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
        val repo = AndroidDeepLinkRepository(setOf("https"), setOf("careerpolitics.com"))
        val vm = DeepLinkViewModel(ResolveDeepLinkUseCase(ParseDeepLinkUseCase(repo), ValidateDeepLinkUseCase(repo)))
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://evil.com/phish"))
        assertTrue(vm.resolve(intent) is ResolvedDeepLink.Invalid)
    }
}
