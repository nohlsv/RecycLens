package com.example.recyclens

import android.content.Context

object SoundPrefs {
    private const val PREFS = "recyclens_sound_prefs"
    private const val KEY_MUTED = "muted"

    fun isMuted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MUTED, false)
    }

    fun setMuted(context: Context, muted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MUTED, muted)
            .apply()
    }

    fun toggle(context: Context): Boolean {
        val next = !isMuted(context)
        setMuted(context, next)
        return next
    }
}
