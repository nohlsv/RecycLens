package com.example.recyclens

import android.speech.tts.TextToSpeech
import java.util.Locale

object TtsLanguageHelper {

    fun apply(engine: TextToSpeech, isEnglish: Boolean) {
        val primary = if (isEnglish) Locale.US else Locale.forLanguageTag("fil-PH")
        val secondary = if (isEnglish) Locale.UK else Locale.forLanguageTag("tl-PH")

        var result = runCatching { engine.setLanguage(primary) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = runCatching { engine.setLanguage(secondary) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        }

        val voices = engine.voices.orEmpty()
        val preferredLanguageCodes = if (isEnglish) setOf("en") else setOf("fil", "tl")

        val bestVoice = voices
            .asSequence()
            .filter { it.locale.language.lowercase() in preferredLanguageCodes }
            .sortedByDescending { scoreVoice(it.locale, primary, secondary) }
            .firstOrNull()

        if (bestVoice != null) {
            runCatching { engine.voice = bestVoice }
        } else if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Last resort so speech still works even when Filipino voice packs are missing.
            runCatching { engine.setLanguage(Locale.US) }
        }

        runCatching { engine.setSpeechRate(1.0f) }
        runCatching { engine.setPitch(1.0f) }
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
