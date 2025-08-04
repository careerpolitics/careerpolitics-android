package com.murari.careerpolitics.util.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.murari.careerpolitics.events.NetworkStatusEvent
import org.greenrobot.eventbus.EventBus

class NetworkWatcher(private val coroutineScope: CoroutineScope) : BroadcastReceiver() {

    companion object {
        val intentFilter = IntentFilter("android.net.conn.CONNECTIVITY_CHANGE")
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        coroutineScope.launch {
            try {
                if (NetworkUtils.isOnline()) {
                    EventBus.getDefault().post(NetworkStatusEvent(NetworkStatus.BACK_ONLINE))
                }
            } catch (e: Exception) {
                // Optional: Log error or report to crash monitoring
            }
        }
    }
}
