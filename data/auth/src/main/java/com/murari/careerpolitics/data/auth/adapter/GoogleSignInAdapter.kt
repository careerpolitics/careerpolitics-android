package com.murari.careerpolitics.data.auth.adapter

import android.content.Intent
import com.murari.careerpolitics.data.auth.model.GoogleAuthPayload

interface GoogleSignInAdapter {
    fun isConfigured(): Boolean
    fun launchIntent(): Intent?
    fun parseResult(data: Intent?): GoogleAuthPayload?
}
