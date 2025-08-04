package com.murari.careerpolitics.events

/**
 * Event emitted every second while the video is playing,
 * carrying the current playback time in seconds.
 */
data class VideoPlayerTickEvent(
    val seconds: String,
    val action: String = "tick"
)
