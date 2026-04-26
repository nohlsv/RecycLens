package com.example.recyclens

import android.content.Context
import android.media.MediaPlayer
import android.util.Log

object MusicManager {
    private var player: MediaPlayer? = null
    private var currentResId: Int = 0
    private const val TAG = "MusicManager"

    @Synchronized
    fun start(context: Context, rawResName: String = "recyclens_browsing_music") {
        if (SoundPrefs.isMuted(context)) {
            pause()
            return
        }

        val appCtx = context.applicationContext
        val resId = appCtx.resources.getIdentifier(rawResName, "raw", appCtx.packageName)
        if (resId == 0) {
            Log.w(TAG, "start: raw resource not found: $rawResName")
            return
        }

        if (player == null || currentResId != resId) {
            try {
                player?.release()
            } catch (_: Exception) {
            }
            player = MediaPlayer.create(appCtx, resId)
            if (player == null) {
                Log.w(TAG, "start: MediaPlayer.create returned null for resId=$resId")
                currentResId = 0
                return
            }
            player?.isLooping = true
            player?.setOnErrorListener { mp, what, extra ->
                Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                try {
                    mp.stop()
                } catch (_: Exception) {
                }
                try {
                    mp.release()
                } catch (_: Exception) {
                }
                player = null
                currentResId = 0
                true
            }
            currentResId = resId
        }

        try {
            if (player?.isPlaying != true) {
                player?.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "start failed", e)
            try {
                player?.release()
            } catch (_: Exception) {
            }
            player = null
            currentResId = 0
        }
    }

    @Synchronized
    fun pause() {
        try {
            if (player?.isPlaying == true) {
                player?.pause()
            }
        } catch (e: Exception) {
            Log.w(TAG, "pause failed", e)
        }
    }

    @Synchronized
    fun resume() {
        try {
            if (player != null && player?.isPlaying == false) {
                player?.start()
            }
        } catch (e: Exception) {
            Log.w(TAG, "resume failed", e)
        }
    }

    @Synchronized
    fun stop() {
        try {
            if (player != null) {
                if (player?.isPlaying == true) {
                    player?.stop()
                }
                player?.release()
                player = null
                currentResId = 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "stop failed", e)
            try {
                player?.release()
            } catch (_: Exception) {
            }
            player = null
            currentResId = 0
        }
    }

    @Synchronized
    fun isPlaying(): Boolean {
        return try {
            player?.isPlaying == true
        } catch (e: Exception) {
            false
        }
    }
}
