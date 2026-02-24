package com.murari.careerpolitics.feature.shell.presentation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class ShellViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    fun resolveStartupRoute(
        savedInstanceStateExists: Boolean,
        deepLinkUrl: String?,
        notificationUrl: String?,
        homeUrl: String
    ) {
        val startupCommand = when {
            savedInstanceStateExists -> null
            !deepLinkUrl.isNullOrBlank() -> {
                ShellNavigationCommand.LoadUrl(deepLinkUrl, ShellRouteSource.STARTUP_DEEP_LINK)
            }

            !notificationUrl.isNullOrBlank() -> {
                ShellNavigationCommand.LoadUrl(notificationUrl, ShellRouteSource.STARTUP_NOTIFICATION)
            }

            else -> ShellNavigationCommand.LoadUrl(homeUrl, ShellRouteSource.STARTUP_HOME)
        }

        startupCommand?.let(::publishCommand)
    }

    fun resolveIncomingIntent(deepLinkUrl: String?, notificationUrl: String?) {
        if (!deepLinkUrl.isNullOrBlank()) {
            publishCommand(ShellNavigationCommand.LoadUrl(deepLinkUrl, ShellRouteSource.INCOMING_DEEP_LINK))
            return
        }

        if (!notificationUrl.isNullOrBlank()) {
            publishCommand(
                ShellNavigationCommand.LoadUrl(
                    notificationUrl,
                    ShellRouteSource.INCOMING_NOTIFICATION
                )
            )
        }
    }

    fun resolveResumeIntent(notificationUrl: String?) {
        if (!notificationUrl.isNullOrBlank()) {
            publishCommand(
                ShellNavigationCommand.LoadUrl(
                    notificationUrl,
                    ShellRouteSource.RESUME_NOTIFICATION
                )
            )
        }
    }

    fun consumeNavigation() {
        _uiState.update { it.copy(pendingNavigation = null) }
    }

    private fun publishCommand(command: ShellNavigationCommand) {
        _uiState.update { it.copy(pendingNavigation = command) }
    }
}
