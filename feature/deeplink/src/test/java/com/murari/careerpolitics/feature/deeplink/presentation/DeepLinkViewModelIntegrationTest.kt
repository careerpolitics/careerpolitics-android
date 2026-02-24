package com.murari.careerpolitics.feature.deeplink.presentation

import android.content.Intent
import android.net.Uri
import com.murari.careerpolitics.feature.deeplink.data.AndroidDeepLinkRepository
import com.murari.careerpolitics.feature.deeplink.domain.ParseDeepLinkUseCase
import com.murari.careerpolitics.feature.deeplink.domain.ResolveDeepLinkUseCase
import com.murari.careerpolitics.feature.deeplink.domain.ResolvedDeepLink
import com.murari.careerpolitics.feature.deeplink.domain.ValidateDeepLinkUseCase
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkViewModelIntegrationTest {

    @Test
    fun `resolves allowed host as valid`() {
        val repo = AndroidDeepLinkRepository(setOf("https"), setOf("careerpolitics.com"))
        val vm = DeepLinkViewModel(
            ResolveDeepLinkUseCase(ParseDeepLinkUseCase(repo), ValidateDeepLinkUseCase(repo))
        )
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://careerpolitics.com/welcome"))

        val result = vm.resolve(intent)
        assertTrue(result is ResolvedDeepLink.Valid)
    }
}
