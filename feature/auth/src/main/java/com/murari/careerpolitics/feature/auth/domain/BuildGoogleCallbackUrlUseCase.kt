package com.murari.careerpolitics.feature.auth.domain

import javax.inject.Inject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        val params = linkedMapOf<String, String>()
        if (!authCode.isNullOrBlank()) params["code"] = authCode
        if (!idToken.isNullOrBlank()) params["id_token"] = idToken
        if (!state.isNullOrBlank()) params["state"] = state
        params["platform"] = "android"

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }

        return "$normalizedBaseUrl$normalizedPath?$queryString"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
}
