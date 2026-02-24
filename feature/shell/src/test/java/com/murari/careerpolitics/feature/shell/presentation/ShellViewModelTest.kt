package com.murari.careerpolitics.feature.shell.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellViewModelTest {

    @Test
    fun `startup falls back to home route`() {
        val vm = ShellViewModel()
        vm.resolveStartupRoute(false, null, null, "https://careerpolitics.com")
        val effect = vm.uiState.value.pendingNavigation as ShellNavigationCommand.LoadUrl
        assertEquals(ShellRouteSource.STARTUP_HOME, effect.source)
    }
}
