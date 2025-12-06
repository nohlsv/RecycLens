package com.example.recyclens

import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder

class MusicService : Service() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? {
        // We don't need binding for this simple case, so return null.
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // This is called when the service is started.
        if (mediaPlayer == null) {
            // Initialize and prepare the media player if it doesn't exist
            mediaPlayer = MediaPlayer.create(this, R.raw.recyclens_browsing_music)
            mediaPlayer?.isLooping = true // Loop the music
        }

        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start() // Start playing
        }

        // START_STICKY tells the system to recreate the service if it gets killed.
        return START_STICKY
    }

    override fun onDestroy() {
        // This is called when the service is stopped.
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
