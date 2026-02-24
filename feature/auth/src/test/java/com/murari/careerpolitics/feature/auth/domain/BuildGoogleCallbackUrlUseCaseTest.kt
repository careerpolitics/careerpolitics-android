package com.murari.careerpolitics.feature.auth.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class BuildGoogleCallbackUrlUseCaseTest {

    @Test
    fun `builds callback url with all oauth params`() {
        val useCase = BuildGoogleCallbackUrlUseCase()

        val url = useCase(
            baseUrl = "https://careerpolitics.com/",
            callbackPath = "users/auth/google_oauth2/callback",
            authCode = "code123",
            idToken = "id456",
            state = "state789"
        )

        assertTrue(url.contains("/users/auth/google_oauth2/callback"))
        assertTrue(url.contains("code=code123"))
        assertTrue(url.contains("id_token=id456"))
        assertTrue(url.contains("state=state789"))
        assertTrue(url.contains("platform=android"))
    }
}
