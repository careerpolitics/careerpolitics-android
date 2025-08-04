package com.murari.careerpolitics.media

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.MainThread
import androidx.annotation.Nullable
import androidx.lifecycle.LifecycleService
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.murari.careerpolitics.R

class AudioService : LifecycleService() {
    private val binder = AudioServiceBinder()

    private var currentPodcastUrl: String? = null
    private var episodeName: String? = null
    private var podcastName: String? = null
    private var imageUrl: String? = null

    private var player: ExoPlayer? = null
    private var playerNotificationManager: PlayerNotificationManager? = null
    private var mediaSession: MediaSessionCompat? = null

    inner class AudioServiceBinder : Binder() {
        val service: AudioService
            get() = this@AudioService
    }

    companion object {
        @MainThread
        fun newIntent(
            context: Context,
            episodeUrl: String
        ) = Intent(context, AudioService::class.java).apply {
            putExtra(argPodcastUrl, episodeUrl)
        }

        const val argPodcastUrl = "ARG_PODCAST_URL"
        const val playbackChannelId = "playback_channel"
        const val mediaSessionTag = "DEV Community Session"
        const val playbackNotificationId = 1
        const val incrementMs = 15000
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)

        val newPodcastUrl = intent.getStringExtra(argPodcastUrl)
        if (currentPodcastUrl != newPodcastUrl) {
            currentPodcastUrl = newPodcastUrl
            preparePlayer()
        }

        return binder
    }

    override fun onCreate() {
        super.onCreate()

        player = ExoPlayer.Builder(this).build()
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        player?.setAudioAttributes(audioAttributes, true)

        playerNotificationManager = PlayerNotificationManager.Builder(
            this,
            playbackNotificationId,
            playbackChannelId
        )
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): String {
                    return episodeName ?: getString(R.string.app_name)
                }

                @Nullable
                override fun createCurrentContentIntent(player: Player): PendingIntent? = null

                @Nullable
                override fun getCurrentContentText(player: Player): String? {
                    return podcastName ?: getString(R.string.playback_channel_description)
                }

                @Nullable
                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): Bitmap? {
                    return null
                }
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                @SuppressLint("ForegroundServiceType")
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean
                ) {
                    if (ongoing) {
                        startForeground(notificationId, notification)
                    } else {
                        stopForeground(false)
                    }
                }

                override fun onNotificationCancelled(
                    notificationId: Int,
                    dismissedByUser: Boolean
                ) {
                    stopSelf()
                }
            })
            .build()
            .apply {
                setUseStopAction(true)
                //setFastForwardIncrementMs(incrementMs.toLong())
                //setRewindIncrementMs(incrementMs.toLong())
                setPlayer(player)
            }

        // Show lock screen controls and let apps like Google assistant manage playback.
        mediaSession = MediaSessionCompat(this, mediaSessionTag)
        val builder = MediaMetadataCompat.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.putString(MediaMetadata.METADATA_KEY_TITLE, episodeName)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, podcastName)
        }
        mediaSession?.setMetadata(builder.build())
        playerNotificationManager?.setMediaSessionToken(mediaSession!!.sessionToken)
    }

    @MainThread
    fun play(audioUrl: String?, seconds: String?) {
        if (currentPodcastUrl != audioUrl) {
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
        muted?.toBoolean()?.let {
            player?.volume = if (it) 0F else 1F
        }
    }

    @MainThread
    fun volume(volume: String?) {
        volume?.toFloat()?.let {
            player?.volume = it
        }
    }

    @MainThread
    fun rate(rate: String?) {
        rate?.toFloat()?.let {
            player?.setPlaybackParameters(PlaybackParameters(it))
        }
    }

    @MainThread
    fun seekTo(seconds: String?) {
        seconds?.toFloat()?.let {
            player?.seekTo((it * 1000F).toLong())
        }
    }

    @MainThread
    fun loadMetadata(epName: String?, pdName: String?, url: String?) {
        episodeName = epName
        podcastName = pdName
        imageUrl = url
    }

    @MainThread
    fun currentTimeInSec(): Long {
        return player?.currentPosition ?: 0
    }

    @MainThread
    fun durationInSec(): Long {
        return player?.duration ?: 0L
    }

    @MainThread
    private fun preparePlayer() {
        player?.playWhenReady = false

        val extractorsFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)
        val dataSourceFactory = DefaultDataSource.Factory(this)
        val streamUri = currentPodcastUrl?.let { Uri.parse(it) }
        if (streamUri != null) {
            val mediaItem = MediaItem.fromUri(streamUri)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)
                .createMediaSource(mediaItem)
            player?.setMediaSource(mediaSource)
            player?.prepare()
        }
    }
}