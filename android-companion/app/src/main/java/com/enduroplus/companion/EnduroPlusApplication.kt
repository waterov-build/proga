package com.enduroplus.companion

import android.app.Application

class EnduroPlusApplication : Application() {
    // Application-level singleton access point.
    // Extend here for dependency injection (e.g., Hilt/Koin) when the
    // project grows.
    companion object {
        lateinit var instance: EnduroPlusApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
