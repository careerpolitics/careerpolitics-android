package com.murari.careerpolitics.feature.notifications.domain

import javax.inject.Inject

class RegisterNotificationsUseCase @Inject constructor(
    private val repository: NotificationsRepository
) {

    operator fun invoke(
        topic: String,
        instanceId: String,
        interest: String,
        maxAttempts: Int = 2
    ): Result<Unit> {
        var lastError: Throwable? = null
        repeat(maxAttempts) { index ->
            try {
                repository.register(topic, instanceId, interest)
                return Result.success(Unit)
            } catch (exception: RuntimeException) {
                lastError = exception
            }
        }

        return Result.failure(
            IllegalStateException(
                "Notification registration failed after $maxAttempts attempts",
                lastError
            )
        )
    }
}
