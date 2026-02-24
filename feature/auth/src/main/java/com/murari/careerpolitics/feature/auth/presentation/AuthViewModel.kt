package com.murari.careerpolitics.feature.auth.presentation

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.murari.careerpolitics.data.auth.adapter.GoogleSignInAdapter
import com.murari.careerpolitics.feature.auth.domain.BuildGoogleCallbackUrlUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val googleSignInAdapter: GoogleSignInAdapter,
    private val buildGoogleCallbackUrl: BuildGoogleCallbackUrlUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onGoogleOAuthRequested(oAuthUrl: String): Boolean {
        if (_uiState.value.isGoogleSignInInProgress) return true

        val state = Uri.parse(oAuthUrl).getQueryParameter("state") ?: "navbar_basic"

        if (!googleSignInAdapter.isConfigured()) {
            emitEffect(AuthEffect.Error("Google native sign-in is not configured"))
            return false
        }

        val signInIntent = googleSignInAdapter.launchIntent()
        if (signInIntent == null) {
            emitEffect(AuthEffect.Error("Failed to launch Google sign-in"))
            return false
        }

        _uiState.update {
            it.copy(
                pendingOAuthState = state,
                isGoogleSignInInProgress = true,
                pendingEffect = AuthEffect.LaunchGoogleSignIn(signInIntent)
            )
        }
        return true
    }

    fun onGoogleSignInResult(data: Intent?, baseUrl: String, callbackPath: String) {
        val payload = googleSignInAdapter.parseResult(data)
        _uiState.update { it.copy(isGoogleSignInInProgress = false) }

        if (payload == null || (payload.authCode.isNullOrBlank() && payload.idToken.isNullOrBlank())) {
            emitEffect(AuthEffect.Error("Native Google sign-in failed or returned empty tokens"))
            return
        }

        val callbackUrl = buildGoogleCallbackUrl(
            baseUrl = baseUrl,
            callbackPath = callbackPath,
            authCode = payload.authCode,
            idToken = payload.idToken,
            state = _uiState.value.pendingOAuthState
        )

        _uiState.update {
            it.copy(
                pendingOAuthState = null,
                pendingEffect = AuthEffect.CompleteGoogleLogin(callbackUrl)
            )
        }
    }

    fun consumeEffect() {
        _uiState.update { it.copy(pendingEffect = null) }
    }

    private fun emitEffect(effect: AuthEffect) {
        _uiState.update { it.copy(pendingEffect = effect) }
    }
}
