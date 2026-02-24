package com.murari.careerpolitics.integration

import android.content.Intent
import com.murari.careerpolitics.data.auth.adapter.GoogleSignInAdapter
import com.murari.careerpolitics.data.auth.model.GoogleAuthPayload
import com.murari.careerpolitics.feature.auth.domain.BuildGoogleCallbackUrlUseCase
import com.murari.careerpolitics.feature.auth.presentation.AuthEffect
import com.murari.careerpolitics.feature.auth.presentation.AuthViewModel
import org.junit.Assert.assertTrue
import org.junit.Test

class OAuthCallbackIntegrationTest {
    @Test
    fun `callback completion effect contains callback path`() {
        val vm = AuthViewModel(object : GoogleSignInAdapter {
            override fun isConfigured(): Boolean = true
            override fun launchIntent(): Intent = Intent()
            override fun parseResult(data: Intent?): GoogleAuthPayload = GoogleAuthPayload("c", "t")
        }, BuildGoogleCallbackUrlUseCase())

        vm.onGoogleOAuthRequested("https://careerpolitics.com/users/auth/google?state=s")
        vm.onGoogleSignInResult(Intent(), "https://careerpolitics.com", "/users/auth/google_oauth2/callback")

        val effect = vm.uiState.value.pendingEffect as AuthEffect.CompleteGoogleLogin
        assertTrue(effect.callbackUrl.contains("google_oauth2/callback"))
    }
}
