package com.murari.careerpolitics.data.notifications.adapter

interface FcmPushAdapter {
    fun subscribeToTopic(topic: String)
}
