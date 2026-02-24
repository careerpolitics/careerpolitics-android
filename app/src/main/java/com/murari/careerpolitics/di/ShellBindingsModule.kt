package com.murari.careerpolitics.di

import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.feature.shell.domain.AppUrlValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ShellBindingsModule {

    @Provides
    @Singleton
    fun provideAppUrlValidator(): AppUrlValidator =
        object : AppUrlValidator {
            override fun isValid(url: String): Boolean = AppConfig.isValidAppUrl(url)
        }
}
