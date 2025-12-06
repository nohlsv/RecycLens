package com.example.recyclens

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ChooseLevelActivity : AppCompatActivity() {

    private var gameType: String = GameSelectActivity.GAME_TRASH_SORTING

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.choose_level_page)

        // setupBottomBar(BottomBar.Tab.PLAY) // Commented out for safety

        gameType = intent.getStringExtra(GameSelectActivity.EXTRA_GAME_TYPE)
            ?: GameSelectActivity.GAME_TRASH_SORTING

        val btnBack: View = findViewById(R.id.btnBack)
        val tvTitle: TextView = findViewById(R.id.tvTitle)

        val btnEasy: View = findViewById(R.id.btnEasy)
        val btnMedium: View = findViewById(R.id.btnMedium)
        val btnHard: View = findViewById(R.id.btnHard)

        tvTitle.text = when (gameType) {
            GameSelectActivity.GAME_STREET_CLEANUP ->
                "Choose Your\nLevel!\n(Street Cleanup)"
            GameSelectActivity.GAME_TRASH_SORTING ->
                "Choose Your\nLevel!\n(Trash Sorting)"
            else ->
                "Choose Your\nLevel!"
        }

        btnBack.setOnClickListener { finish() }

        btnEasy.setOnClickListener   { openGame(1) }
        btnMedium.setOnClickListener { openGame(2) }
        btnHard.setOnClickListener   { openGame(3) }
    }

    private fun openGame(level: Int) {
        val target = when (gameType) {
            GameSelectActivity.GAME_STREET_CLEANUP ->
                StreetCleanupActivity::class.java
            GameSelectActivity.GAME_TRASH_SORTING ->
                TrashSortingActivity::class.java
            else ->
                TrashSortingActivity::class.java
        }

        // --- CHANGE IS HERE ---
        // Stop the browsing music before starting a game
        MusicManager.stop(this)
        // --- END OF CHANGE ---

        val intent = Intent(this, target)
            .putExtra("extra_level", level)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // When returning from a game, restart the browsing music.
        MusicManager.start(this)
    }
}
