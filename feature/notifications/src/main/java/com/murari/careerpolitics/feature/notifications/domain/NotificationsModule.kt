package com.murari.careerpolitics.feature.notifications.domain

import com.murari.careerpolitics.data.notifications.adapter.FcmPushAdapter
import com.murari.careerpolitics.data.notifications.adapter.PusherPushAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationsModule {

    @Provides
    @Singleton
    fun provideNotificationsRepository(
        fcmPushAdapter: FcmPushAdapter,
        pusherPushAdapter: PusherPushAdapter
    ): NotificationsRepository {
        return DefaultNotificationsRepository(fcmPushAdapter, pusherPushAdapter)
    }
}
