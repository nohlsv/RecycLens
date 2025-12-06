package com.example.recyclens

import android.content.Context
import android.content.Intent

object MusicManager {

    fun start(context: Context) {
        val serviceIntent = Intent(context, MusicService::class.java)
        context.startService(serviceIntent)
    }

    fun stop(context: Context) {
        val serviceIntent = Intent(context, MusicService::class.java)
        context.stopService(serviceIntent)
    }
}
