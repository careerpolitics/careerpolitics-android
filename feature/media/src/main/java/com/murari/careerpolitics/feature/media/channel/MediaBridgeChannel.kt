package com.murari.careerpolitics.feature.media.channel

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object MediaBridgeChannel {
    private val _events = MutableSharedFlow<MediaBridgeEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<MediaBridgeEvent> = _events

    fun emit(event: MediaBridgeEvent) {
        _events.tryEmit(event)
    }
}
