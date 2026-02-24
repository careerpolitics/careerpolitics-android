package com.murari.careerpolitics.core.webview.bridge

import com.google.gson.Gson

class BridgeCommandParser(
    private val gson: Gson = Gson()
) {
    fun parsePodcast(message: String): PodcastBridgeCommand {
        val map = parse(message)
        return when (map["action"]) {
            "load" -> PodcastBridgeCommand.Load(map["url"].orEmpty())
            "play" -> PodcastBridgeCommand.Play(map["url"], map["seconds"])
            "pause" -> PodcastBridgeCommand.Pause
            "seek" -> PodcastBridgeCommand.Seek(map["seconds"])
            "rate" -> PodcastBridgeCommand.Rate(map["rate"])
            "muted" -> PodcastBridgeCommand.Muted(map["muted"])
            "volume" -> PodcastBridgeCommand.Volume(map["volume"])
            "metadata" -> PodcastBridgeCommand.Metadata(map["episodeName"], map["podcastName"], map["imageUrl"])
            "terminate" -> PodcastBridgeCommand.Terminate
            else -> PodcastBridgeCommand.Unknown(map["action"])
        }
    }

    fun parseVideo(message: String): VideoBridgeCommand {
        val map = parse(message)
        return when (map["action"]) {
            "play" -> VideoBridgeCommand.Play(map["url"], map["seconds"])
            else -> VideoBridgeCommand.Unknown(map["action"])
        }
    }

    private fun parse(message: String): Map<String, String> =
        try {
            gson.fromJson(message, Map::class.java) as? Map<String, String> ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
}
