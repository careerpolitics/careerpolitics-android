package com.murari.careerpolitics.data.notifications.di

import android.content.Context
import com.murari.careerpolitics.data.notifications.adapter.AndroidFcmPushAdapter
import com.murari.careerpolitics.data.notifications.adapter.AndroidPusherPushAdapter
import com.murari.careerpolitics.data.notifications.adapter.FcmPushAdapter
import com.murari.careerpolitics.data.notifications.adapter.PusherPushAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PushAdaptersModule {

    @Provides
    @Singleton
    fun provideFcmPushAdapter(): FcmPushAdapter = AndroidFcmPushAdapter()

    @Provides
    @Singleton
    fun providePusherPushAdapter(@ApplicationContext context: Context): PusherPushAdapter =
        AndroidPusherPushAdapter(context)
}
