package com.murari.careerpolitics.media

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.MainThread
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.media.session.MediaButtonReceiver
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
        const val PLAYBACK_CHANNEL_NAME = "Podcast Playback"
        const val MEDIA_SESSION_TAG = "CareerPoliticsPodcast"
        const val NOTIFICATION_ID = 1
    }

    private var currentPodcastUrl: String? = null
    private var episodeName: String? = null
    private var podcastName: String? = null
    private var imageUrl: String? = null

    private var player: ExoPlayer? = null
    private var playerNotificationManager: PlayerNotificationManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

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

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        createNotificationChannel()

        player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(), true
            )
            setHandleAudioBecomingNoisy(true)
            addListener(playerListener)
        }

        setupMediaSession()
        setupNotificationManager()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            PLAYBACK_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Media playback controls"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateMediaSessionState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                requestAudioFocus()
            }
            updateMediaSessionState()
        }
    }

    private fun requestAudioFocus() {
        val audioAttributes = android.media.AudioAttributes.Builder()
            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS -> pause()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.volume = 0.3f
                    AudioManager.AUDIOFOCUS_GAIN -> player?.volume = 1f
                }
            }
            .build()

        audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
    }

    private fun setupNotificationManager() {
        playerNotificationManager = PlayerNotificationManager.Builder(
            this,
            NOTIFICATION_ID,
            PLAYBACK_CHANNEL_ID
        )
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): String {
                    return episodeName ?: "Unknown Episode"
                }

                override fun createCurrentContentIntent(player: Player): PendingIntent? {
                    return packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
                        PendingIntent.getActivity(
                            this@AudioService,
                            0,
                            intent,
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        )
                    }
                }

                override fun getCurrentContentText(player: Player): String? {
                    return podcastName ?: "Podcast"
                }

                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): Bitmap? = null
            })
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
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

                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    stopSelf()
                }
            })
            .build().apply {
                setMediaSessionToken(mediaSession!!.sessionToken)
                setUseRewindAction(false)
                setUseFastForwardAction(false)
                setUseNextAction(true)
                setUsePreviousAction(true)
                setUsePlayPauseActions(true)
                setUseStopAction(true)
                setPlayer(player)
            }
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, MEDIA_SESSION_TAG).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    player?.play()
                }

                override fun onPause() {
                    player?.pause()
                }

                override fun onSeekTo(pos: Long) {
                    player?.seekTo(pos)
                }

                override fun onSkipToNext() {
                    // Implement skip to next episode if needed
                }

                override fun onSkipToPrevious() {
                    // Implement skip to previous episode if needed
                }

                override fun onStop() {
                    pause()
                    stopSelf()
                }
            })

            isActive = true
        }

        updateMediaSessionMetadata()
        updateMediaSessionState()
    }

    private fun updateMediaSessionMetadata() {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, episodeName ?: "Unknown Episode")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, podcastName ?: "Unknown Podcast")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player?.duration ?: 0)
            .build()
        mediaSession?.setMetadata(metadata)
    }

    private fun updateMediaSessionState() {
        val state = when (player?.playbackState) {
            Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            Player.STATE_READY -> if (player?.playWhenReady == true) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            Player.STATE_ENDED -> PlaybackStateCompat.STATE_STOPPED
            else -> PlaybackStateCompat.STATE_NONE
        }

        val playbackState = PlaybackStateCompat.Builder()
            .setState(state, player?.currentPosition ?: 0, 1f)
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or
                        PlaybackStateCompat.ACTION_SEEK_TO or
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
            .build()

        mediaSession?.setPlaybackState(playbackState)
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
        updateMediaSessionMetadata()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        playerNotificationManager?.setPlayer(null)
        mediaSession?.isActive = false
        mediaSession?.release()
        player?.release()
        player = null
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (player?.isPlaying == false) {
            stopSelf()
        }
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
