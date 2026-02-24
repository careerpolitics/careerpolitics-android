package com.murari.careerpolitics.data.notifications.adapter

import android.content.Context
import com.pusher.pushnotifications.PushNotifications

class AndroidPusherPushAdapter(
    private val appContext: Context
) : PusherPushAdapter {
    override fun start(instanceId: String) {
        PushNotifications.start(appContext, instanceId)
    }

    override fun addInterest(interest: String) {
        PushNotifications.addDeviceInterest(interest)
    }
}
