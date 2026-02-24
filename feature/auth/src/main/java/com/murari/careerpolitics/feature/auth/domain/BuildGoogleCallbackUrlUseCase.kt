package com.murari.careerpolitics.feature.auth.domain

import android.net.Uri
import javax.inject.Inject

class BuildGoogleCallbackUrlUseCase @Inject constructor() {

    operator fun invoke(
        baseUrl: String,
        callbackPath: String,
        authCode: String?,
        idToken: String?,
        state: String?
    ): String {
        val normalizedPath = callbackPath.trim().let {
            if (it.startsWith("/")) it else "/$it"
        }

        return Uri.parse(baseUrl).buildUpon()
            .encodedPath(normalizedPath)
            .apply {
                if (!authCode.isNullOrBlank()) appendQueryParameter("code", authCode)
                if (!idToken.isNullOrBlank()) appendQueryParameter("id_token", idToken)
                if (!state.isNullOrBlank()) appendQueryParameter("state", state)
                appendQueryParameter("platform", "android")
            }
            .build()
            .toString()
    }
}
