package com.example.recyclens.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.recyclens.R

class StreetCleanupGameActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.street_cleanup)

        val level = intent.getStringExtra(ChooseLevelActivity.EXTRA_LEVEL) ?: "easy"
        findViewById<TextView>(R.id.tvLevel).text = level.replaceFirstChar { it.uppercase() }

        findViewById<ImageButton>(R.id.btnBackStreet).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnInfoStreet).setOnClickListener {
            // TODO: show game rules dialog
        }
        findViewById<TextView>(R.id.btnStartStreet).setOnClickListener {
            // TODO: start game logic
        }
    }
}
