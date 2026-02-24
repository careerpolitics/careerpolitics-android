package com.murari.careerpolitics.util

import android.content.*
import android.os.IBinder
import com.murari.careerpolitics.config.AppConfig
import com.murari.careerpolitics.util.Logger
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.murari.careerpolitics.core.webview.bridge.BridgeCommandParser
import com.murari.careerpolitics.core.webview.bridge.PodcastBridgeCommand
import com.murari.careerpolitics.core.webview.bridge.VideoBridgeCommand
import com.murari.careerpolitics.activities.VideoPlayerActivity
import com.murari.careerpolitics.events.VideoPlayerPauseEvent
import com.murari.careerpolitics.events.VideoPlayerTickEvent
import com.murari.careerpolitics.media.AudioService
import com.murari.careerpolitics.webclients.CustomWebViewClient
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class AndroidWebViewBridge(private val context: Context) {

    var webViewClient: CustomWebViewClient? = null

    private var timer: Timer? = null
    private var audioService: AudioService? = null

    private val commandParser = BridgeCommandParser()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            audioService = (service as? AudioService.AudioServiceBinder)?.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
        }
    }

    companion object {
        private const val LOG_TAG = "AndroidWebViewBridge"
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
            is PodcastBridgeCommand.Play -> audioService?.play(command.url, command.seconds)
            PodcastBridgeCommand.Pause -> audioService?.pause()
            is PodcastBridgeCommand.Seek -> audioService?.seekTo(command.seconds)
            is PodcastBridgeCommand.Rate -> audioService?.rate(command.rate)
            is PodcastBridgeCommand.Muted -> audioService?.mute(command.muted)
            is PodcastBridgeCommand.Volume -> audioService?.volume(command.volume)
            is PodcastBridgeCommand.Metadata -> audioService?.loadMetadata(
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

        audioService?.pause()
        timer?.cancel()

        // Launch video player activity
        context.startActivity(VideoPlayerActivity.newIntent(context, url, seconds ?: "0"))

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    private fun loadPodcast(url: String?) {
        if (url.isNullOrEmpty()) return

        // Start audio service
        AudioService.newIntent(context, url).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

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

        audioService?.pause()
        try {
            context.unbindService(connection)
        } catch (_: IllegalArgumentException) { /* already unbound */ }

        context.stopService(Intent(context, AudioService::class.java))
        audioService = null
    }

    private fun podcastTimeUpdate() {
        audioService?.let { service ->
            val time = service.currentTimeInSec() / 1000
            val duration = service.durationInSec() / 1000

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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onVideoPauseEvent(event: VideoPlayerPauseEvent) {
        webViewClient?.sendBridgeMessage("video", mapOf("action" to event.action))
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onVideoTickEvent(event: VideoPlayerTickEvent) {
        webViewClient?.sendBridgeMessage("video", mapOf(
            "action" to event.action,
            "currentTime" to event.seconds
        ))
    }
}
