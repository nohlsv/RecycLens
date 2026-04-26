// app/src/main/java/com/example/recyclens/BottomBar.kt
package com.example.recyclens

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat

object BottomBar {
    enum class Tab { SCAN, PLAY }
    interface LanguageAware {
        fun onLanguageChanged()
    }
    interface SoundAware {
        fun onSoundChanged(isMuted: Boolean)
    }

    fun setup(activity: Activity, selected: Tab? = null) {
        val navScan: View? = activity.findViewById(R.id.navScan)
        val navPlay: View? = activity.findViewById(R.id.navPlay)
        val langToggle: View? = activity.findViewById(R.id.langToggle)
        val langText: TextView? = activity.findViewById(R.id.langText)
        val labelScan: TextView? = activity.findViewById(R.id.labelScan)
        val labelPlay: TextView? = activity.findViewById(R.id.labelPlay)
        val btnSpeaker: ImageButton? = activity.findViewById(R.id.btnSpeaker)

        navScan?.isSelected = (selected == Tab.SCAN)
        navPlay?.isSelected = (selected == Tab.PLAY)

        fun applyLanguageUi() {
            val isEnglish = LanguagePrefs.isEnglish(activity)
            langText?.text = activity.getString(if (isEnglish) R.string.label_en else R.string.label_tl)
            labelScan?.text = activity.getString(R.string.label_scan_trash)
            labelPlay?.text = activity.getString(R.string.label_play_games)
        }

        applyLanguageUi()

        fun applySoundUi() {
            val muted = SoundPrefs.isMuted(activity)
            btnSpeaker?.setImageResource(
                if (muted) android.R.drawable.ic_lock_silent_mode else android.R.drawable.ic_lock_silent_mode_off
            )
            btnSpeaker?.contentDescription = activity.getString(
                if (muted) R.string.cd_sound_muted else R.string.cd_sound_unmuted
            )
        }

        applySoundUi()

        langToggle?.setOnClickListener {
            LanguagePrefs.toggle(activity)
            (activity as? AppCompatActivity)?.let { LanguagePrefs.applyLocale(it) }

            // Defer refresh one frame so updated locales are visible without forcing recreate.
            langToggle.post {
                applyLanguageUi()
                applySoundUi()
                (activity as? LanguageAware)?.onLanguageChanged()
            }
        }

        btnSpeaker?.setOnClickListener {
            val muted = SoundPrefs.toggle(activity)
            applySoundUi()
            (activity as? SoundAware)?.onSoundChanged(muted)
        }

        navScan?.setOnClickListener {
            if (activity is ScannerActivity) return@setOnClickListener
            val i = Intent(activity, ScannerActivity::class.java)
            activity.startActivity(
                i,
                ActivityOptionsCompat.makeCustomAnimation(
                    activity, android.R.anim.fade_in, android.R.anim.fade_out
                ).toBundle()
            )
        }

        navPlay?.setOnClickListener {
            if (activity is GameSelectActivity) return@setOnClickListener
            val i = Intent(activity, GameSelectActivity::class.java)
            activity.startActivity(
                i,
                ActivityOptionsCompat.makeCustomAnimation(
                    activity, android.R.anim.fade_in, android.R.anim.fade_out
                ).toBundle()
            )
        }
    }
}

fun AppCompatActivity.setupBottomBar(selected: BottomBar.Tab? = null) {
    BottomBar.setup(this, selected)
}
