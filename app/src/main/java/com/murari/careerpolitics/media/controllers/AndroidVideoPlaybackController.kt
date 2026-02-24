package com.murari.careerpolitics.media.controllers

import android.content.Context
import com.murari.careerpolitics.activities.VideoPlayerActivity
import com.murari.careerpolitics.feature.media.domain.VideoPlaybackController

class AndroidVideoPlaybackController(
    private val context: Context
) : VideoPlaybackController {
    override fun play(url: String, seconds: String) {
        context.startActivity(VideoPlayerActivity.newIntent(context, url, seconds))
    }
}
