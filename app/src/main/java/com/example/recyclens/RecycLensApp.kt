// RecycLensApp.kt
package com.example.recyclens

import android.app.Application
import com.example.recyclens.data.db.PreloadedDb

class RecycLensApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PreloadedDb.installIfNeeded(this) // copies from assets on first run
    }
}
