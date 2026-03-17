package com.enduroplus.companion

import android.app.Application

class EnduroPlusApplication : Application() {
    // Application-level singleton access point.
    // Extend here for dependency injection (e.g., Hilt/Koin) when the
    // project grows.
    companion object {
        var instance: EnduroPlusApplication? = null
            private set
    }

    /** Shared BleManager used across MainActivity and CourseEditorActivity. */
    lateinit var bleManager: BleManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        bleManager = BleManager(applicationContext)
    }
}
