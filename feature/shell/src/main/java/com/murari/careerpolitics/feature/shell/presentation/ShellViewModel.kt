package com.murari.careerpolitics.feature.shell.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.murari.careerpolitics.feature.shell.domain.AppUrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class ShellViewModel @Inject constructor(
    private val appUrlValidator: AppUrlValidator
) : ViewModel() {

    private val _uiState = MutableStateFlow(ShellUiState())
    val uiState: StateFlow<ShellUiState> = _uiState.asStateFlow()

    fun resolveStartupRoute(savedInstanceStateExists: Boolean, intent: Intent?, homeUrl: String) {
        if (savedInstanceStateExists) return

        val appLinkUrl = intent?.dataString
        if (!appLinkUrl.isNullOrBlank() && appUrlValidator.isValid(appLinkUrl)) {
            publishCommand(ShellNavigationCommand.LoadUrl(appLinkUrl, ShellRouteSource.STARTUP_DEEP_LINK))
            return
        }

        val notificationUrl = intent?.getStringExtra(NOTIFICATION_URL_EXTRA)
        if (!notificationUrl.isNullOrBlank()) {
            publishCommand(
                ShellNavigationCommand.LoadUrl(
                    notificationUrl,
                    ShellRouteSource.STARTUP_NOTIFICATION
                )
            )
            return
        }

        publishCommand(ShellNavigationCommand.LoadUrl(homeUrl, ShellRouteSource.STARTUP_HOME))
    }

    fun resolveIncomingIntent(intent: Intent?) {
        val appLinkUrl = intent?.dataString
        if (!appLinkUrl.isNullOrBlank() && appUrlValidator.isValid(appLinkUrl)) {
            publishCommand(ShellNavigationCommand.LoadUrl(appLinkUrl, ShellRouteSource.INCOMING_DEEP_LINK))
            return
        }

        val notificationUrl = intent?.getStringExtra(NOTIFICATION_URL_EXTRA)
        if (!notificationUrl.isNullOrBlank()) {
            publishCommand(
                ShellNavigationCommand.LoadUrl(
                    notificationUrl,
                    ShellRouteSource.INCOMING_NOTIFICATION
                )
            )
        }
    }

    fun resolveResumeIntent(intent: Intent?) {
        val notificationUrl = intent?.getStringExtra(NOTIFICATION_URL_EXTRA)
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

    companion object {
        private const val NOTIFICATION_URL_EXTRA = "url"
    }
}
