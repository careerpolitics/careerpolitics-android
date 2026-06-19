package com.murari.careerpolitics.util

import android.content.*
import android.os.IBinder
import com.murari.careerpolitics.config.AppConfig
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import com.google.firebase.messaging.FirebaseMessaging
import com.google.gson.Gson
import com.murari.careerpolitics.activities.VideoPlayerActivity
import com.murari.careerpolitics.events.VideoPlayerPauseEvent
import com.murari.careerpolitics.events.VideoPlayerTickEvent
import com.murari.careerpolitics.media.AudioService
import com.murari.careerpolitics.services.PushNotificationService
import com.murari.careerpolitics.webclients.CustomWebViewClient
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.json.JSONObject
import java.util.*

@UnstableApi
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
        val map = try {
            gson.fromJson(message, Map::class.java) as? Map<String, String> ?: emptyMap()
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Podcast JSON parse error", e)
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
            else        -> Logger.w(LOG_TAG, "Unknown podcast action: ${map["action"]}")
        }
    }

    // User login bridge — called by platform JS when user signs in
    @JavascriptInterface
    fun userLoginMessage(message: String) {
        Logger.d(LOG_TAG, "User login received: ${message.take(50)}")
        try {
            val userData = JSONObject(message)
            val userId = userData.optString("id", "")

            context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
                .edit()
                .putString("user_id", userId)
                .putString("username", userData.optString("username", ""))
                .apply()

            PushNotificationService.registerFcmToken(context)
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Error handling userLoginMessage", e)
        }
    }

    // User logout bridge — called by platform JS when user signs out
    @JavascriptInterface
    fun userLogoutMessage(message: String) {
        Logger.d(LOG_TAG, "User logout received")
        try {
            val prefs = context.getSharedPreferences("user_session", Context.MODE_PRIVATE)
            val userId = prefs.getString("user_id", null)

            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                PushNotificationService.unregisterDevice(context, userId, token)
            }

            prefs.edit().clear().apply()

            context.getSharedPreferences("push_prefs", Context.MODE_PRIVATE)
                .edit().clear().apply()

            webViewClient?.resetRegistrationFlags()

            terminatePodcast()
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Error handling userLogoutMessage", e)
        }
    }

    // Video playback control from WebView
    @JavascriptInterface
    fun videoMessage(message: String) {
        val map = try {
            gson.fromJson(message, Map::class.java) as? Map<String, String> ?: emptyMap()
        } catch (e: Exception) {
            Logger.e(LOG_TAG, "Video JSON parse error", e)
            return
        }

        when (map["action"]) {
            "play" -> playVideo(map["url"], map["seconds"])
            else   -> Logger.w(LOG_TAG, "Unknown video action: ${map["action"]}")
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
