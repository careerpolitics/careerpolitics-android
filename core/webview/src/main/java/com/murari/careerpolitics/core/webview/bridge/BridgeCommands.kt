package com.murari.careerpolitics.core.webview.bridge

sealed interface PodcastBridgeCommand {
    data class Load(val url: String) : PodcastBridgeCommand
    data class Play(val url: String?, val seconds: String?) : PodcastBridgeCommand
    data object Pause : PodcastBridgeCommand
    data class Seek(val seconds: String?) : PodcastBridgeCommand
    data class Rate(val rate: String?) : PodcastBridgeCommand
    data class Muted(val muted: String?) : PodcastBridgeCommand
    data class Volume(val volume: String?) : PodcastBridgeCommand
    data class Metadata(
        val episodeName: String?,
        val podcastName: String?,
        val imageUrl: String?
    ) : PodcastBridgeCommand
    data object Terminate : PodcastBridgeCommand
    data class Unknown(val action: String?) : PodcastBridgeCommand
}

sealed interface VideoBridgeCommand {
    data class Play(val url: String?, val seconds: String?) : VideoBridgeCommand
    data class Unknown(val action: String?) : VideoBridgeCommand
}
