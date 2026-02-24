package com.murari.careerpolitics.feature.notifications.domain

sealed interface NotificationRegistrationError {
    data class Recoverable(
        val reason: String,
        val attempt: Int,
        val throwable: Throwable
    ) : NotificationRegistrationError

    data class Fatal(
        val reason: String,
        val throwable: Throwable
    ) : NotificationRegistrationError
}
