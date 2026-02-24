package com.murari.careerpolitics.feature.notifications.presentation

import androidx.lifecycle.ViewModel
import com.murari.careerpolitics.feature.notifications.domain.NotificationRegistrationError
import com.murari.careerpolitics.feature.notifications.domain.RegisterNotificationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val registerNotifications: RegisterNotificationsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    fun onNotificationPermissionRequired() {
        _uiState.update { it.copy(effect = NotificationsEffect.RequestPermission) }
    }

    fun onPermissionGranted() {
        _uiState.update { it.copy(effect = NotificationsEffect.InitializePush) }
    }

    fun initializePushRegistration(topic: String, instanceId: String, interest: String) {
        val result = registerNotifications(
            topic = topic,
            instanceId = instanceId,
            interest = interest
        )

        result.exceptionOrNull()?.let { throwable ->
            _uiState.update {
                it.copy(
                    error = NotificationRegistrationError.Recoverable(
                        reason = "Push registration failure",
                        attempt = 2,
                        throwable = throwable
                    )
                )
            }
        }
    }

    fun consumeEffect() {
        _uiState.update { it.copy(effect = null) }
    }

    fun consumeError() {
        _uiState.update { it.copy(error = null) }
    }
}
