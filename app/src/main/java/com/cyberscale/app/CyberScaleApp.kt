package com.cyberscale.app

import android.app.Application
import com.cyberscale.app.data.AppContainer

class CyberScaleApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
