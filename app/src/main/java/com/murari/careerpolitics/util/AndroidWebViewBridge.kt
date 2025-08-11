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
// import removed: native audio service no longer used
import com.murari.careerpolitics.webclients.CustomWebViewClient
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.util.*

class AndroidWebViewBridge(private val context: Context) {

    var webViewClient: CustomWebViewClient? = null

    private var timer: Timer? = null
    // private var audioService: AudioService? = null

    private val gson = Gson()

    // Native audio service connection is disabled; WebView manages audio itself
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
        override fun onServiceDisconnected(name: ComponentName?) {}
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

        // Let the web app handle podcast playback natively; we only relay time ticks for UI if needed
        when (map["action"]) {
            "load"      -> loadPodcast(map["url"]) // start tick timer only
            "terminate" -> terminatePodcast()
            else        -> Unit
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

        // no-op on native audio; ensure our tick timer is reset
        timer?.cancel()

        // Launch video player activity
        context.startActivity(VideoPlayerActivity.newIntent(context, url, seconds ?: "0"))

        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this)
        }
    }

    private fun loadPodcast(url: String?) {
        if (url.isNullOrEmpty()) return

        // We are not starting any native audio; WebView manages playback itself
        // We only start a timer to forward ticks back to the Web UI, if needed

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

        // No native audio; just stop our timer
    }

    private fun podcastTimeUpdate() {
        // Without native player, we cannot compute real time; ask the WebView to initialize itself
        webViewClient?.sendBridgeMessage("podcast", mapOf("action" to "init"))
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
