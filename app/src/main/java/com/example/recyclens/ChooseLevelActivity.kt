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

        setupBottomBar(BottomBar.Tab.PLAY)

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

        // All three buttons open the same game for now
        btnEasy.setOnClickListener   { openGame() }
        btnMedium.setOnClickListener { openGame() }
        btnHard.setOnClickListener   { openGame() }
    }

    private fun openGame() {
        val target = when (gameType) {
            GameSelectActivity.GAME_STREET_CLEANUP ->
                StreetCleanupActivity::class.java
            GameSelectActivity.GAME_TRASH_SORTING ->
                TrashSortingActivity::class.java
            else ->
                TrashSortingActivity::class.java
        }

        startActivity(Intent(this, target))
    }
}
