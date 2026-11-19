package com.clippost.app

import android.app.Application

class ClipPostApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
