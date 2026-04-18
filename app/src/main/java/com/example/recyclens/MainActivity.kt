package com.example.recyclens

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguagePrefs.applyLocale(this)
        setContentView(R.layout.landing_page)
        BackgroundDriftHelper.attach(this, R.drawable.plastic_background)

        findViewById<MaterialButton>(R.id.btn_start)?.setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
        }

        RecyclensEntryAnimator.play(this)
    }

    override fun onResume() {
        super.onResume()
        MusicManager.start(this, "recyclens_browsing_music")
    }

    override fun onPause() {
        super.onPause()
        MusicManager.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        BackgroundDriftHelper.detach(this)
    }
}
