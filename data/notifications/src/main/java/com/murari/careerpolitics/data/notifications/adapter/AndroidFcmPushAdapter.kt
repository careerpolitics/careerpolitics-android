package com.murari.careerpolitics.data.notifications.adapter

import com.google.firebase.messaging.FirebaseMessaging

class AndroidFcmPushAdapter : FcmPushAdapter {
    override fun subscribeToTopic(topic: String) {
        FirebaseMessaging.getInstance().subscribeToTopic(topic)
    }
}
