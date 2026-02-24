package com.murari.careerpolitics.feature.deeplink.domain

import android.content.Intent
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveDeepLinkUseCaseTest {

    @Test
    fun `returns valid deep link when repository validates`() {
        val repo = object : DeepLinkRepository {
            override fun extractCandidateUrl(intent: Intent?): String? = "https://careerpolitics.com/posts/1"
            override fun isValidAppDeepLink(url: String): Boolean = true
        }

        val useCase = ResolveDeepLinkUseCase(ParseDeepLinkUseCase(repo), ValidateDeepLinkUseCase(repo))
        val result = useCase(Intent())
        assertTrue(result is ResolvedDeepLink.Valid)
    }
}
