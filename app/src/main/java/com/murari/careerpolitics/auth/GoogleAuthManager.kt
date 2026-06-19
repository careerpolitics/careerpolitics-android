package com.murari.careerpolitics.auth

import android.app.Activity
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.util.Logger
import com.murari.careerpolitics.util.network.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Manages Google sign-in via the Credential Manager API.
 *
 * Responsibilities:
 *  - Initialise CredentialManager
 *  - Launch the native Google sign-in bottom sheet
 *  - Exchange the resulting ID-token with the platform's MobileAuthController
 *  - Sync the returned JWT cookie into the WebView
 */
class GoogleAuthManager(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val onAuthComplete: () -> Unit,
    private val onAuthFailed: ((String) -> Unit)? = null
) {

    companion object {
        private const val LOG_TAG = "GoogleAuthManager"
        private const val MOBILE_AUTH_PATH = "api/auth/mobile_exchange"
    }

    private var credentialManager: CredentialManager? = null
    private var signInInProgress = false

    // ------------------------------------------------------------------
    // Initialisation
    // ------------------------------------------------------------------

    fun init() {
        if (AppConfig.googleWebClientId.isBlank()) {
            Logger.w(LOG_TAG, "Google web client ID not configured — native sign-in disabled")
            return
        }
        credentialManager = CredentialManager.create(activity)
    }

    val isAvailable: Boolean get() = credentialManager != null

    // ------------------------------------------------------------------
    // Sign-in flow
    // ------------------------------------------------------------------

    /**
     * Launches the native Google sign-in flow.
     * @return `true` if the flow was started (or already in progress), `false` if unavailable.
     */
    fun launchSignIn(): Boolean {
        val cm = credentialManager ?: run {
            Logger.w(LOG_TAG, "CredentialManager not initialised")
            return false
        }

        if (signInInProgress) {
            Logger.d(LOG_TAG, "Sign-in already in progress")
            return true
        }
        signInInProgress = true

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(AppConfig.googleWebClientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch {
            try {
                val result = cm.getCredential(activity, request)
                handleResult(result)
            } catch (e: GetCredentialException) {
                Logger.e(LOG_TAG, "Credential Manager sign-in failed: ${e.type}", e)
                onAuthFailed?.invoke(e.type ?: "unknown_error")
            } finally {
                signInInProgress = false
            }
        }
        return true
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private fun handleResult(result: GetCredentialResponse) {
        try {
            val idToken = GoogleIdTokenCredential
                .createFrom(result.credential.data)
                .idToken

            if (idToken.isBlank()) {
                Logger.w(LOG_TAG, "ID token is empty after sign-in")
                onAuthFailed?.invoke("empty_token")
                return
            }

            Logger.d(LOG_TAG, "ID token obtained — exchanging with backend")
            exchangeToken(idToken)
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Failed to parse Google credential", e)
            onAuthFailed?.invoke("credential_parse_error")
        }
    }

    private fun exchangeToken(idToken: String) {
        scope.launch {
            try {
                val payload = JSONObject().apply {
                    put("provider", "google_oauth2")
                    put("id_token", idToken)
                }

                val response = withContext(Dispatchers.IO) {
                    ApiClient.post(
                        url = "${AppConfig.baseUrl}$MOBILE_AUTH_PATH",
                        body = payload,
                        headers = mapOf("User-Agent" to AppConfig.userAgent)
                    )
                }

                if (response.isSuccess) {
                    val jwt = JSONObject(response.body).optString("jwt", "")
                    if (jwt.isNotBlank()) {
                        withContext(Dispatchers.Main) { syncJwtCookie(jwt) }
                    }
                    Logger.d(LOG_TAG, "Auth exchange succeeded")
                } else {
                    Logger.e(LOG_TAG, "Auth exchange failed: HTTP ${response.code} — ${response.body}")
                    onAuthFailed?.invoke("http_${response.code}")
                }

                withContext(Dispatchers.Main) { onAuthComplete() }
            } catch (e: Exception) {
                Logger.e(LOG_TAG, "Error during token exchange", e)
                onAuthFailed?.invoke("exchange_error")
                withContext(Dispatchers.Main) { onAuthComplete() }
            }
        }
    }

    private fun syncJwtCookie(jwt: String) {
        CookieManager.getInstance().apply {
            setCookie(AppConfig.baseUrl, "jwt=$jwt; Path=/; Secure; HttpOnly")
            flush()
        }
    }
}
