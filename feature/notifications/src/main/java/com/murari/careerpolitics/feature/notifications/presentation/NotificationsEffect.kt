package com.murari.careerpolitics.feature.notifications.presentation

sealed interface NotificationsEffect {
    data object RequestPermission : NotificationsEffect
    data object InitializePush : NotificationsEffect
}
