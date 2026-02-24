@file:Suppress("WildcardImport")

package com.murari.careerpolitics.util

import android.content.*
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.util.Logger
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.murari.careerpolitics.core.webview.bridge.BridgeCommandParser
import com.murari.careerpolitics.core.webview.bridge.PodcastBridgeCommand
import com.murari.careerpolitics.core.webview.bridge.VideoBridgeCommand
import com.murari.careerpolitics.feature.media.channel.MediaBridgeChannel
import com.murari.careerpolitics.feature.media.channel.MediaBridgeEvent
import com.murari.careerpolitics.feature.media.domain.AudioServiceController
import com.murari.careerpolitics.feature.media.domain.VideoPlaybackController
import com.murari.careerpolitics.media.controllers.AndroidAudioServiceController
import com.murari.careerpolitics.media.controllers.AndroidVideoPlaybackController
import com.murari.careerpolitics.webclients.CustomWebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class AndroidWebViewBridge(private val context: Context) {

    var webViewClient: CustomWebViewClient? = null

    private var timer: Timer? = null
    private val commandParser = BridgeCommandParser()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val audioController: AudioServiceController = AndroidAudioServiceController(context)
    private val videoController: VideoPlaybackController = AndroidVideoPlaybackController(context)

    companion object {
        private const val LOG_TAG = "AndroidWebViewBridge"
    }

    init {
        scope.launch {
            MediaBridgeChannel.events.collectLatest { event ->
                when (event) {
                    is MediaBridgeEvent.VideoTick -> {
                        webViewClient?.sendBridgeMessage(
                            "video",
                            mapOf("action" to "tick", "currentTime" to event.seconds)
                        )
                    }

                    MediaBridgeEvent.VideoPaused -> {
                        webViewClient?.sendBridgeMessage("video", mapOf("action" to "pause"))
                    }
                }
            }
        }
    }

    // Logging from JavaScript
    @JavascriptInterface
    fun logError(tag: String, message: String) {
        Logger.e("JS:$tag", message)
    }

    // Copy text to clipboard
    @JavascriptInterface
    fun copyToClipboard(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("CareerPolitics", text))
    }

    // Show toast from JS
    @JavascriptInterface
    fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    // Share plain text via Android share sheet
    @JavascriptInterface
    fun shareText(text: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(shareIntent, null))
    }

    // Podcast control from WebView
    @JavascriptInterface
    fun podcastMessage(message: String) {
        when (val command = commandParser.parsePodcast(message)) {
            is PodcastBridgeCommand.Load -> loadPodcast(command.url)
            is PodcastBridgeCommand.Play -> audioController.play(command.url, command.seconds)
            PodcastBridgeCommand.Pause -> audioController.pause()
            is PodcastBridgeCommand.Seek -> audioController.seekTo(command.seconds)
            is PodcastBridgeCommand.Rate -> audioController.rate(command.rate)
            is PodcastBridgeCommand.Muted -> audioController.mute(command.muted)
            is PodcastBridgeCommand.Volume -> audioController.volume(command.volume)
            is PodcastBridgeCommand.Metadata -> audioController.loadMetadata(
                command.episodeName,
                command.podcastName,
                command.imageUrl
            )
            PodcastBridgeCommand.Terminate -> terminatePodcast()
            is PodcastBridgeCommand.Unknown -> Logger.w(LOG_TAG, "Unknown podcast action: ${command.action}")
        }
    }

    // Video playback control from WebView
    @JavascriptInterface
    fun videoMessage(message: String) {
        when (val command = commandParser.parseVideo(message)) {
            is VideoBridgeCommand.Play -> playVideo(command.url, command.seconds)
            is VideoBridgeCommand.Unknown -> Logger.w(LOG_TAG, "Unknown video action: ${command.action}")
        }
    }

    private fun playVideo(url: String?, seconds: String?) {
        if (url.isNullOrEmpty()) return

        audioController.pause()
        timer?.cancel()
        videoController.play(url, seconds ?: "0")
    }

    private fun loadPodcast(url: String?) {
        if (url.isNullOrEmpty()) return

        audioController.load(url)

        // Start timer to send playback ticks to WebView
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                podcastTimeUpdate()
            }
        }, 0, AppConfig.PODCAST_TICK_INTERVAL_MS)
    }

    fun terminatePodcast() {
        timer?.cancel()
        timer = null

        audioController.terminate()
    }

    fun release() {
        terminatePodcast()
        scope.cancel()
    }

    private fun podcastTimeUpdate() {
        val time = audioController.currentTimeMs() / 1000
        val duration = audioController.durationMs() / 1000

        if (duration < 0) {
                webViewClient?.sendBridgeMessage("podcast", mapOf("action" to "init"))
        } else {
            webViewClient?.sendBridgeMessage("podcast", mapOf(
                "action" to "tick",
                "duration" to duration,
                "currentTime" to time
            ))
        }
    }
}
