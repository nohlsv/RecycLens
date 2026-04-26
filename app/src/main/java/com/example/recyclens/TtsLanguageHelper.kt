package com.example.recyclens

import android.speech.tts.TextToSpeech
import java.util.Locale

object TtsLanguageHelper {

    fun apply(engine: TextToSpeech, isEnglish: Boolean) {
        // Clear any previously pinned voice so language changes apply cleanly.
        engine.voice = null

        val primary = if (isEnglish) Locale.US else Locale.forLanguageTag("fil-PH")
        val secondary = if (isEnglish) Locale.UK else Locale.forLanguageTag("tl-PH")

        var result = engine.setLanguage(primary)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = engine.setLanguage(secondary)
        }

        val voices = engine.voices.orEmpty()
        val preferredLanguageCodes = if (isEnglish) setOf("en") else setOf("fil", "tl")

        val bestVoice = voices
            .asSequence()
            .filter { it.locale.language.lowercase() in preferredLanguageCodes }
            .sortedByDescending { scoreVoice(it.locale, primary, secondary) }
            .firstOrNull()

        if (bestVoice != null) {
            engine.voice = bestVoice
        } else if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Last resort so speech still works even when Filipino voice packs are missing.
            engine.setLanguage(Locale.US)
        }

        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)
    }

    private fun scoreVoice(locale: Locale, primary: Locale, secondary: Locale): Int {
        var score = 0
        if (locale.language.equals(primary.language, ignoreCase = true)) score += 4
        if (locale.country.equals(primary.country, ignoreCase = true)) score += 2
        if (locale.language.equals(secondary.language, ignoreCase = true)) score += 2
        if (locale.country.equals(secondary.country, ignoreCase = true)) score += 1
        return score
    }
}
