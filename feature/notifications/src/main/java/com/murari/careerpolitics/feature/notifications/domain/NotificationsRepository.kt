package com.murari.careerpolitics.feature.notifications.domain

interface NotificationsRepository {
    fun register(topic: String, instanceId: String, interest: String)
}
