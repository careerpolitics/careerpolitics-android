package com.murari.careerpolitics.feature.auth.presentation

import android.content.Intent
import com.murari.careerpolitics.data.auth.adapter.GoogleSignInAdapter
import com.murari.careerpolitics.data.auth.model.GoogleAuthPayload
import com.murari.careerpolitics.feature.auth.domain.BuildGoogleCallbackUrlUseCase
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthViewModelTest {

    @Test
    fun `emits complete effect on successful sign in`() {
        val vm = AuthViewModel(FakeAdapter(), BuildGoogleCallbackUrlUseCase())

        vm.onGoogleOAuthRequested("https://careerpolitics.com/auth/google?state=abc")
        vm.onGoogleSignInResult(Intent(), "https://careerpolitics.com", "/users/auth/google_oauth2/callback")

        val effect = vm.uiState.value.pendingEffect
        assertTrue(effect is AuthEffect.CompleteGoogleLogin)
    }

    private class FakeAdapter : GoogleSignInAdapter {
        override fun isConfigured(): Boolean = true
        override fun launchIntent(): Intent = Intent("fake")
        override fun parseResult(data: Intent?): GoogleAuthPayload = GoogleAuthPayload("code", "token")
    }
}
