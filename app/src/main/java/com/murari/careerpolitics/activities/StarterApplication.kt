package com.murari.careerpolitics.activities

import android.app.Application
import androidx.multidex.MultiDexApplication
import com.murari.careerpolitics.MyEventBusIndex
import org.greenrobot.eventbus.EventBus

class StarterApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()

        // Properly configure the EventBus with index only once
        EventBus.builder()
            .addIndex(MyEventBusIndex())
            .installDefaultEventBus()
    }
}
