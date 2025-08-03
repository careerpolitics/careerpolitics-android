package com.murari.careerpolitics.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.MainThread
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.SimpleExoPlayer
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import org.greenrobot.eventbus.EventBus
import com.murari.careerpolitics.R
import com.murari.careerpolitics.databinding.ActivityVideoPlayerBinding
import com.murari.careerpolitics.events.VideoPlayerPauseEvent
import com.murari.careerpolitics.events.VideoPlayerTickEvent
import java.util.*

class VideoPlayerActivity : BaseActivity<ActivityVideoPlayerBinding>() {

    private var player: SimpleExoPlayer? = null
    private val timer = Timer()

    override fun layout(): Int {
        return R.layout.activity_video_player
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val videoUrl = intent.getStringExtra(argVideoUrl)
        val videoTime = intent.getStringExtra(argVideoTime)

        val streamUri= Uri.parse(videoUrl)


        val dataSourceFactory: DataSource.Factory = DefaultHttpDataSource.Factory().setUserAgent("DEV-Native-android")
        val mediaSource = HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(streamUri))

        player = SimpleExoPlayer.Builder(this).build()
        binding.playerView.player = player
        player?.prepare(mediaSource)
        player?.seekTo(videoTime!!.toLong() * 1000)
        player?.playWhenReady = true

        val timeUpdateTask = object: TimerTask() {
            override fun run() {
                timeUpdate()
            }
        }
        timer.schedule(timeUpdateTask, 0, 1000)
    }

    override fun onDestroy() {
        player?.playWhenReady = false
        timer.cancel()
        EventBus.getDefault().post(VideoPlayerPauseEvent())
        super.onDestroy()
    }

    fun timeUpdate() {
        val milliseconds = (player?.currentPosition ?: 0)
        val currentTime = (milliseconds / 1000).toString()
        EventBus.getDefault().post(VideoPlayerTickEvent(currentTime))
    }

    companion object {
        @MainThread
        fun newIntent(
            context: Context,
            url: String,
            time: String
        ) = Intent(context, VideoPlayerActivity::class.java).apply {
            putExtra(argVideoUrl, url)
            putExtra(argVideoTime, time)
        }

        const val argVideoUrl = "ARG_VIDEO_URL"
        const val argVideoTime = "ARG_VIDEO_TIME"
    }
}
