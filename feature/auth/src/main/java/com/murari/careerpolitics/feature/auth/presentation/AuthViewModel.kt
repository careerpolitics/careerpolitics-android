package com.murari.careerpolitics.feature.auth.presentation

import android.content.Intent
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
        val state = extractStateFromOAuthUrl(oAuthUrl)
        val signInIntent = googleSignInAdapter.launchIntent()
        val isConfigured = googleSignInAdapter.isConfigured()
        val hasIntent = signInIntent != null
        val canProceed = !_uiState.value.isGoogleSignInInProgress && isConfigured && hasIntent

        if (!canProceed) {
            when {
                _uiState.value.isGoogleSignInInProgress -> Unit
                !isConfigured -> emitEffect(AuthEffect.Error("Google native sign-in is not configured"))
                else -> emitEffect(AuthEffect.Error("Failed to launch Google sign-in"))
            }
            return _uiState.value.isGoogleSignInInProgress
        }

        _uiState.update {
            it.copy(
                pendingOAuthState = state,
                isGoogleSignInInProgress = true,
                pendingEffect = AuthEffect.LaunchGoogleSignIn(signInIntent!!)
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

    private fun extractStateFromOAuthUrl(oAuthUrl: String): String {
        val stateValue = oAuthUrl
            .substringAfter("?", missingDelimiterValue = "")
            .split("&")
            .mapNotNull { param ->
                val key = param.substringBefore("=", missingDelimiterValue = "")
                val value = param.substringAfter("=", missingDelimiterValue = "")
                if (key == "state") value else null
            }
            .firstOrNull()

        return stateValue.orEmpty().ifBlank { "navbar_basic" }
    }
}
