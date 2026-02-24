package com.murari.careerpolitics.feature.auth.presentation

import android.content.Intent

sealed interface AuthEffect {
    data class LaunchGoogleSignIn(val intent: Intent) : AuthEffect
    data class CompleteGoogleLogin(val callbackUrl: String) : AuthEffect
    data class Error(val message: String) : AuthEffect
}
