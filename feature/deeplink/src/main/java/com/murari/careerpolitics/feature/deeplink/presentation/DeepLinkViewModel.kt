package com.murari.careerpolitics.feature.deeplink.presentation

import android.content.Intent
import androidx.lifecycle.ViewModel
import com.murari.careerpolitics.feature.deeplink.domain.ResolveDeepLinkUseCase
import com.murari.careerpolitics.feature.deeplink.domain.ResolvedDeepLink
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DeepLinkViewModel @Inject constructor(
    private val resolveDeepLinkUseCase: ResolveDeepLinkUseCase
) : ViewModel() {

    fun resolve(intent: Intent?): ResolvedDeepLink = resolveDeepLinkUseCase(intent)
}
