package com.murari.careerpolitics.media.controllers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.murari.careerpolitics.media.AudioService
import com.murari.careerpolitics.feature.media.domain.AudioServiceController

class AndroidAudioServiceController(
    private val context: Context
) : AudioServiceController {

    private var audioService: AudioService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            audioService = (service as? AudioService.AudioServiceBinder)?.service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
        }
    }

    override fun load(url: String) {
        AudioService.newIntent(context, url).also { intent ->
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun play(url: String?, seconds: String?) {
        audioService?.play(url, seconds)
    }

    override fun pause() {
        audioService?.pause()
    }

    override fun seekTo(seconds: String?) {
        audioService?.seekTo(seconds)
    }

    override fun rate(rate: String?) {
        audioService?.rate(rate)
    }

    override fun mute(muted: String?) {
        audioService?.mute(muted)
    }

    override fun volume(volume: String?) {
        audioService?.volume(volume)
    }

    override fun loadMetadata(episodeName: String?, podcastName: String?, imageUrl: String?) {
        audioService?.loadMetadata(episodeName, podcastName, imageUrl)
    }

    override fun currentTimeMs(): Long = audioService?.currentTimeInSec() ?: 0L

    override fun durationMs(): Long = audioService?.durationInSec() ?: 0L

    override fun terminate() {
        audioService?.pause()
        try {
            context.unbindService(connection)
        } catch (_: IllegalArgumentException) {
        }
        context.stopService(Intent(context, AudioService::class.java))
        audioService = null
    }
}
