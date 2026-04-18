package com.example.recyclens

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

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
        val languageTag = if (isEnglish(activity)) "en" else "tl"
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (current == languageTag) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }
}
