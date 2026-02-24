package com.murari.careerpolitics.data.auth.adapter

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.murari.careerpolitics.data.auth.model.GoogleAuthPayload

class AndroidGoogleSignInAdapter(
    private val appContext: Context,
    private val webClientId: String
) : GoogleSignInAdapter {

    override fun isConfigured(): Boolean = webClientId.isNotBlank()

    override fun launchIntent(): Intent? {
        if (!isConfigured()) return null
        googleSignInClient().signOut()
        return googleSignInClient().signInIntent
    }

    override fun parseResult(data: Intent?): GoogleAuthPayload? {
        if (!isConfigured()) return null
        return try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
            GoogleAuthPayload(account.serverAuthCode, account.idToken)
        } catch (_: ApiException) {
            null
        }
    }

    private fun googleSignInClient(): GoogleSignInClient {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(webClientId)
            .requestServerAuthCode(webClientId, true)
            .build()
        return GoogleSignIn.getClient(appContext, options)
    }
}
