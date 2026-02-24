package com.murari.careerpolitics.data.notifications.adapter

interface PusherPushAdapter {
    fun start(instanceId: String)
    fun addInterest(interest: String)
}
