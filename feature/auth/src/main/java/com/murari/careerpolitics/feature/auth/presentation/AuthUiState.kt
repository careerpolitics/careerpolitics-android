package com.murari.careerpolitics.feature.auth.presentation

data class AuthUiState(
    val pendingEffect: AuthEffect? = null,
    val pendingOAuthState: String? = null,
    val isGoogleSignInInProgress: Boolean = false
)
