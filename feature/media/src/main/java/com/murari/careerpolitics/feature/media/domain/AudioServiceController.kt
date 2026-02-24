package com.murari.careerpolitics.feature.media.domain

interface AudioServiceController {
    fun load(url: String)
    fun play(url: String?, seconds: String?)
    fun pause()
    fun seekTo(seconds: String?)
    fun rate(rate: String?)
    fun mute(muted: String?)
    fun volume(volume: String?)
    fun loadMetadata(episodeName: String?, podcastName: String?, imageUrl: String?)
    fun currentTimeMs(): Long
    fun durationMs(): Long
    fun terminate()
}
