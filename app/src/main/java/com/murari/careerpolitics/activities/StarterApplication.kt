package com.murari.careerpolitics.activities

import androidx.multidex.MultiDexApplication
import com.murari.careerpolitics.MyEventBusIndex
import dagger.hilt.android.HiltAndroidApp
import org.greenrobot.eventbus.EventBus

@HiltAndroidApp
class StarterApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()

        // Properly configure the EventBus with index only once
        EventBus.builder()
            .addIndex(MyEventBusIndex())
            .installDefaultEventBus()
    }
}
