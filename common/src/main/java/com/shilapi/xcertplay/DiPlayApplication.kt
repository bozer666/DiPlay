package com.shilapi.xcertplay

import android.app.Application

/** Process entry point: installs the crash reporter before anything else runs. */
class DiPlayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
