package com.murari.careerpolitics.integration

import com.murari.careerpolitics.feature.notifications.domain.NotificationsRepository
import com.murari.careerpolitics.feature.notifications.domain.RegisterNotificationsUseCase
import com.murari.careerpolitics.feature.notifications.presentation.NotificationsEffect
import com.murari.careerpolitics.feature.notifications.presentation.NotificationsViewModel
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationRoutingIntegrationTest {
    @Test
    fun `permission flow requests permission effect`() {
        val vm = NotificationsViewModel(RegisterNotificationsUseCase(object : NotificationsRepository {
            override fun register(topic: String, instanceId: String, interest: String) = Unit
        }))
        vm.onNotificationPermissionRequired()
        assertTrue(vm.uiState.value.effect is NotificationsEffect.RequestPermission)
    }
}
