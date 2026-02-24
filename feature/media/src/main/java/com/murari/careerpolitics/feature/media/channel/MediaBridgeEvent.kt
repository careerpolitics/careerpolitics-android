package com.murari.careerpolitics.feature.media.channel

sealed interface MediaBridgeEvent {
    data class VideoTick(val seconds: String) : MediaBridgeEvent
    data object VideoPaused : MediaBridgeEvent
}
