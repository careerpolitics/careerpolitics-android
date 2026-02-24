package com.murari.careerpolitics.feature.notifications.domain

import com.murari.careerpolitics.data.notifications.adapter.FcmPushAdapter
import com.murari.careerpolitics.data.notifications.adapter.PusherPushAdapter

class DefaultNotificationsRepository(
    private val fcmPushAdapter: FcmPushAdapter,
    private val pusherPushAdapter: PusherPushAdapter
) : NotificationsRepository {

    override fun register(topic: String, instanceId: String, interest: String) {
        fcmPushAdapter.subscribeToTopic(topic)
        pusherPushAdapter.start(instanceId)
        pusherPushAdapter.addInterest(interest)
    }
}
