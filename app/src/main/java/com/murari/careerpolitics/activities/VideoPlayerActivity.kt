package com.murari.careerpolitics.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.MainThread
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.murari.careerpolitics.R
import com.murari.careerpolitics.databinding.ActivityVideoPlayerBinding
import com.murari.careerpolitics.events.VideoPlayerPauseEvent
import com.murari.careerpolitics.events.VideoPlayerTickEvent
import org.greenrobot.eventbus.EventBus
import java.util.*
import androidx.core.net.toUri

class VideoPlayerActivity : BaseActivity<ActivityVideoPlayerBinding>() {

    private var player: ExoPlayer? = null
    private var timer: Timer? = null

    override fun layout(): Int = R.layout.activity_video_player

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUrl = intent.getStringExtra(ARG_VIDEO_URL)
        val videoTime = intent.getStringExtra(ARG_VIDEO_TIME)?.toLongOrNull() ?: 0L

        if (videoUrl.isNullOrEmpty()) {
            finish() // Avoid crashing if URL is invalid
            return
        }

        initializePlayer(videoUrl, videoTime)
        startTimer()
    }

    private fun initializePlayer(url: String, seekToSeconds: Long) {
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("CareerPolitics-Android")

        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url.toUri()))

        player = ExoPlayer.Builder(this).build().also { exoPlayer ->
            binding?.playerView?.player = exoPlayer
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.seekTo(seekToSeconds * 1000)
            exoPlayer.playWhenReady = true
        }
    }

    private fun startTimer() {
        timer = Timer().apply {
            schedule(object : TimerTask() {
                override fun run() {
                    player?.currentPosition?.let {
                        val currentSeconds = (it / 1000).toString()
                        EventBus.getDefault().post(VideoPlayerTickEvent(currentSeconds))
                    }
                }
            }, 0, 1000)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        timer = null
        player?.release()
        player = null
        EventBus.getDefault().post(VideoPlayerPauseEvent())
    }

    companion object {
        const val ARG_VIDEO_URL = "ARG_VIDEO_URL"
        const val ARG_VIDEO_TIME = "ARG_VIDEO_TIME"

        @MainThread
        fun newIntent(context: Context, url: String, time: String) =
            Intent(context, VideoPlayerActivity::class.java).apply {
                putExtra(ARG_VIDEO_URL, url)
                putExtra(ARG_VIDEO_TIME, time)
            }
    }
}
