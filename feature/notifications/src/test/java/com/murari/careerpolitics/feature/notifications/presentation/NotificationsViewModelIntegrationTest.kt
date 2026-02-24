package com.murari.careerpolitics.feature.notifications.presentation

import com.murari.careerpolitics.feature.notifications.domain.NotificationsRepository
import com.murari.careerpolitics.feature.notifications.domain.RegisterNotificationsUseCase
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationsViewModelIntegrationTest {

    @Test
    fun `permission grant emits initialize effect`() {
        val vm = NotificationsViewModel(RegisterNotificationsUseCase(object : NotificationsRepository {
            override fun register(topic: String, instanceId: String, interest: String) = Unit
        }))

        vm.onPermissionGranted()
        assertTrue(vm.uiState.value.effect is NotificationsEffect.InitializePush)
    }
}
