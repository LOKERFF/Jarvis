package com.jarvis

import android.app.Application
import com.jarvis.core.ServiceLocator

class JarvisApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)

        // Touch the audit logger once so the database is created early and the
        // very first AI call is never lost while Room warms up.
        ServiceLocator.audit.info("APP", "Jarvis ${BuildConfig.VERSION_NAME} started")
    }
}
