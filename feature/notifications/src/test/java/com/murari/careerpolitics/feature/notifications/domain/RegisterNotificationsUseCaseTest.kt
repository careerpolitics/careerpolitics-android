package com.murari.careerpolitics.feature.notifications.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class RegisterNotificationsUseCaseTest {

    @Test
    fun `retries and succeeds on second attempt`() {
        var calls = 0
        val repo = object : NotificationsRepository {
            override fun register(topic: String, instanceId: String, interest: String) {
                calls++
                if (calls == 1) error("first failure")
            }
        }

        val result = RegisterNotificationsUseCase(repo)("all", "instance", "broadcast", maxAttempts = 2)
        assertTrue(result.isSuccess)
    }
}
