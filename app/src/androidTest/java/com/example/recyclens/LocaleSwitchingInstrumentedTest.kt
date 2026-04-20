package com.example.recyclens

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocaleSwitchingInstrumentedTest {

    @Test
    fun coreGameStringsDifferBetweenEnglishAndFilipino() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        val keysToCheck = listOf(
            R.string.label_easy,
            R.string.game_trash_sorting,
            R.string.game_street_cleanup,
            R.string.dialog_level_complete,
            R.string.dialog_time_up,
            R.string.trash_intro,
            R.string.street_intro
        )

        keysToCheck.forEach { key ->
            val en = localizedString(appContext, "en", key)
            val tl = localizedString(appContext, "tl", key)

            assertTrue("English string is blank for resId=$key", en.isNotBlank())
            assertTrue("Filipino string is blank for resId=$key", tl.isNotBlank())
            assertNotEquals("Expected localized values to differ for resId=$key", en, tl)
        }
    }

    private fun localizedString(context: Context, languageTag: String, stringResId: Int): String {
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(Locale.forLanguageTag(languageTag)))
        return context.createConfigurationContext(config).resources.getString(stringResId)
    }
}
