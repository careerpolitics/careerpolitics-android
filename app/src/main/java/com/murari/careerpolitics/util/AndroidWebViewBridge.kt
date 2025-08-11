package com.murari.careerpolitics.util

import android.content.*
import android.os.IBinder
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import com.google.gson.Gson
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

    private val gson = Gson()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            audioService = (service as? AudioService.AudioServiceBinder)?.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
        }
    }

    // Logging from JavaScript
    @JavascriptInterface
    fun logError(tag: String, message: String) {
        Log.e(tag, message)
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
        val map = try {
            gson.fromJson(message, Map::class.java) as? Map<String, String> ?: emptyMap()
        } catch (e: Exception) {
            logError("Podcast", "JSON parse error: ${e.localizedMessage}")
            return
        }

        when (map["action"]) {
            "load"      -> loadPodcast(map["url"])
            "play"      -> audioService?.play(map["url"], map["seconds"])
            "pause"     -> audioService?.pause()
            "seek"      -> audioService?.seekTo(map["seconds"])
            "rate"      -> audioService?.rate(map["rate"])
            "muted"     -> audioService?.mute(map["muted"])
            "volume"    -> audioService?.volume(map["volume"])
            "metadata"  -> audioService?.loadMetadata(
                map["episodeName"], map["podcastName"], map["imageUrl"]
            )
            "terminate" -> terminatePodcast()
            else        -> logError("Podcast", "Unknown action: ${map["action"]}")
        }
    }

    // Video playback control from WebView
    @JavascriptInterface
    fun videoMessage(message: String) {
        val map = try {
            gson.fromJson(message, Map::class.java) as? Map<String, String> ?: emptyMap()
        } catch (e: Exception) {
            logError("Video", "JSON parse error: ${e.localizedMessage}")
            return
        }

        when (map["action"]) {
            "play" -> playVideo(map["url"], map["seconds"])
            else   -> logError("Video", "Unknown action: ${map["action"]}")
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

        // Start audio service as foreground service, then bind
        AudioService.newIntent(context, url).also { intent ->
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) { /* best effort */ }
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }

        // Start timer to send playback ticks to WebView
        timer?.cancel()
        timer = Timer()
        timer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                podcastTimeUpdate()
            }
        }, 0, 1000)
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
