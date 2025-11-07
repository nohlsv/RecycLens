package com.example.recyclens

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class StreetCleanupActivity : AppCompatActivity() {

    private lateinit var tvLevel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.street_cleanup)

        setupBottomBar(BottomBar.Tab.PLAY)  // Highlight the Play tab

        val level = intent.getStringExtra(ChooseLevelActivity.EXTRA_LEVEL) ?: "Easy"

        tvLevel = findViewById(R.id.tvLevel)
        tvLevel.text = level

        findViewById<ImageButton>(R.id.btnBackStreet).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Automatically start the game when the activity opens
        startGame(level)

        // Start button (now triggers the game logic for manual restart)
        findViewById<TextView>(R.id.btnStartStreet).setOnClickListener {
            startGame(level)
        }
    }

    private fun startGame(level: String) {
        // Dummy game logic - replace with real game code (e.g., initialize game loop, timers, etc.)
        Toast.makeText(this, "Street Cleanup Game Started at $level level!", Toast.LENGTH_SHORT).show()
        // TODO: Implement actual game logic here (e.g., spawn items, handle user input, scoring)
    }
}