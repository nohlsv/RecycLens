package com.example.recyclens

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

object LanguagePrefs {
    private const val PREFS_NAME = "recyclens_prefs"
    private const val KEY_IS_ENGLISH = "is_english"

    fun isEnglish(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_ENGLISH, true)
    }

    fun setEnglish(context: Context, isEnglish: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_ENGLISH, isEnglish)
            .apply()
    }

    fun toggle(context: Context): Boolean {
        val next = !isEnglish(context)
        setEnglish(context, next)
        return next
    }

    fun applyLocale(activity: AppCompatActivity) {
        val locale = if (isEnglish(activity)) {
            Locale.US
        } else {
            Locale.forLanguageTag("tl-PH")
        }

        Locale.setDefault(locale)
        val config = Configuration(activity.resources.configuration)
        config.setLocale(locale)
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)
    }
}
