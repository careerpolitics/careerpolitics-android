package com.murari.careerpolitics.feature.shell.presentation

data class ShellUiState(
    val pendingNavigation: ShellNavigationCommand? = null
)

sealed interface ShellNavigationCommand {
    data class LoadUrl(val url: String, val source: ShellRouteSource) : ShellNavigationCommand
}

enum class ShellRouteSource {
    STARTUP_DEEP_LINK,
    STARTUP_NOTIFICATION,
    STARTUP_HOME,
    INCOMING_DEEP_LINK,
    INCOMING_NOTIFICATION,
    RESUME_NOTIFICATION
}
