package com.murari.careerpolitics.media

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleService
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.murari.careerpolitics.R

class AudioService : LifecycleService() {

    inner class AudioServiceBinder : Binder() {
        val service: AudioService
            get() = this@AudioService
    }

    companion object {
        @MainThread
        fun newIntent(context: Context, episodeUrl: String) =
            Intent(context, AudioService::class.java).apply {
                putExtra(ARG_PODCAST_URL, episodeUrl)
            }

        const val ARG_PODCAST_URL = "ARG_PODCAST_URL"
        const val PLAYBACK_CHANNEL_ID = "playback_channel"
        const val MEDIA_SESSION_TAG = "CareerPolitics Session"
        const val NOTIFICATION_ID = 1
        const val SKIP_MS = 15000
    }

    private var currentPodcastUrl: String? = null
    private var episodeName: String? = null
    private var podcastName: String? = null
    private var imageUrl: String? = null

    private var player: ExoPlayer? = null
    private var playerNotificationManager: PlayerNotificationManager? = null
    private var mediaSession: MediaSessionCompat? = null

    private val binder = AudioServiceBinder()

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        val newPodcastUrl = intent.getStringExtra(ARG_PODCAST_URL)
        if (currentPodcastUrl != newPodcastUrl) {
            currentPodcastUrl = newPodcastUrl
            preparePlayer()
        }
        return binder
    }

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(), true
            )
        }

        setupNotificationManager()
        setupMediaSession()
    }

    private fun setupNotificationManager() {
        playerNotificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            PLAYBACK_CHANNEL_ID
        )
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): String {
                    return episodeName ?: getString(R.string.app_name)
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent? = null

                override fun getCurrentContentText(player: Player): String? {
                    return podcastName ?: getString(R.string.playback_channel_description)
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): Bitmap? = null
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                @SuppressLint("ForegroundServiceType")
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    if (ongoing) startForeground(notificationId, notification)
                    else stopForeground(false)
                }

                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopSelf()
                }
            })
            .build().apply {
                setUseStopAction(true)
                setPlayer(player)
            }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, MEDIA_SESSION_TAG).apply {
            val metadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, episodeName)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, podcastName)
                .build()
            setMetadata(metadata)
        }
        playerNotificationManager?.setMediaSessionToken(mediaSession!!.sessionToken)
    }

    @MainThread
    fun play(audioUrl: String?, seconds: String?) {
        if (audioUrl != null && currentPodcastUrl != audioUrl) {
            currentPodcastUrl = audioUrl
            preparePlayer()
            seekTo("0")
        } else {
            seekTo(seconds)
        }
        player?.playWhenReady = true
    }

    @MainThread
    fun pause() {
        player?.playWhenReady = false
    }

    @MainThread
    fun mute(muted: String?) {
        player?.volume = if (muted?.toBoolean() == true) 0f else 1f
    }

    @MainThread
    fun volume(volume: String?) {
        volume?.toFloatOrNull()?.let { player?.volume = it }
    }

    @MainThread
    fun rate(rate: String?) {
        rate?.toFloatOrNull()?.let { player?.setPlaybackParameters(PlaybackParameters(it)) }
    }

    @MainThread
    fun seekTo(seconds: String?) {
        seconds?.toFloatOrNull()?.let {
            player?.seekTo((it * 1000).toLong())
        }
    }

    @MainThread
    fun loadMetadata(epName: String?, pdName: String?, url: String?) {
        episodeName = epName
        podcastName = pdName
        imageUrl = url
    }

    @MainThread
    fun currentTimeInSec(): Long = player?.currentPosition ?: 0L

    @MainThread
    fun durationInSec(): Long = player?.duration ?: 0L

    @MainThread
    private fun preparePlayer() {
        player?.playWhenReady = false
        currentPodcastUrl?.let { url ->
            val streamUri = Uri.parse(url)
            val dataSourceFactory = DefaultDataSource.Factory(this)
            val extractorsFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
            val mediaItem = MediaItem.fromUri(streamUri)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                .createMediaSource(mediaItem)
            player?.setMediaSource(mediaSource)
            player?.prepare()
        }
    }
}
