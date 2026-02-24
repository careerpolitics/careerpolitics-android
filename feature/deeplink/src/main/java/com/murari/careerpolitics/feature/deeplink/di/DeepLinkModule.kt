package com.murari.careerpolitics.feature.deeplink.di

import com.murari.careerpolitics.feature.deeplink.data.AndroidDeepLinkRepository
import com.murari.careerpolitics.feature.deeplink.domain.DeepLinkRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DeepLinkModule {

    @Provides
    @Singleton
    fun provideDeepLinkRepository(): DeepLinkRepository = AndroidDeepLinkRepository(
        allowedSchemes = setOf("https"),
        allowedHosts = setOf("careerpolitics.com", "www.careerpolitics.com")
    )
}
