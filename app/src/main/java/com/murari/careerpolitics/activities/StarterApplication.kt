package com.murari.careerpolitics.activities

import androidx.multidex.MultiDexApplication
import org.greenrobot.eventbus.EventBus
import com.murari.careerpolitics.MyEventBusIndex

//import to.dev.dev_android.webclients.EventBusClientIndex


class StarterApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        EventBus.builder().addIndex(MyEventBusIndex()).build()
        EventBus.builder().addIndex(MyEventBusIndex()).installDefaultEventBus()
    }

}