package com.murari.careerpolitics.data.auth.model

data class GoogleAuthPayload(
    val authCode: String?,
    val idToken: String?
)
