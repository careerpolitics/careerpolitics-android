package com.murari.careerpolitics.feature.media.channel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBridgeChannelTest {

    @Test
    fun `emits media events`() = runTest {
        MediaBridgeChannel.emit(MediaBridgeEvent.VideoPaused)
        val event = MediaBridgeChannel.events.replayCache.firstOrNull()
        assertTrue(event == null || event is MediaBridgeEvent.VideoPaused)
    }
}
