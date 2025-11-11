package com.example.recyclens

import android.app.Application

class RecycLensApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // SQLite temporarily disabled.
        // No database initialization here for now.
    }
}
