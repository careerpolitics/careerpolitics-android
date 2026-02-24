package com.murari.careerpolitics.feature.notifications.presentation

import com.murari.careerpolitics.feature.notifications.domain.NotificationRegistrationError

data class NotificationsUiState(
    val effect: NotificationsEffect? = null,
    val error: NotificationRegistrationError? = null
)
