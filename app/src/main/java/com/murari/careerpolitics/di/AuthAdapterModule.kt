package com.murari.careerpolitics.di

import android.content.Context
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.data.auth.adapter.AndroidGoogleSignInAdapter
import com.murari.careerpolitics.data.auth.adapter.GoogleSignInAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthAdapterModule {

    @Provides
    @Singleton
    fun provideGoogleSignInAdapter(@ApplicationContext context: Context): GoogleSignInAdapter {
        return AndroidGoogleSignInAdapter(
            appContext = context,
            webClientId = AppConfig.googleWebClientId
        )
    }
}
