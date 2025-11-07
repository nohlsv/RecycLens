package com.example.recyclens

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ChooseLevelActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME = "extra_game"
        const val EXTRA_LEVEL = "extra_level"

        const val GAME_TRASH = "trash"
        const val GAME_STREET = "street"

        const val LEVEL_EASY = "Easy"
        const val LEVEL_MEDIUM = "Medium"
        const val LEVEL_HARD = "Hard"
    }

    private lateinit var tvTitle: TextView
    private var game: String = GAME_TRASH

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.choose_level_page)

        tvTitle = findViewById(R.id.tvTitle)
        game = intent.getStringExtra(EXTRA_GAME) ?: GAME_TRASH
        tvTitle.text = "Choose Your\nLevel!"

        // Back (just finish)
        findViewById<ImageView>(R.id.btnBack)?.setOnClickListener { finish() }

        // Level buttons -> open the selected game Activity
        findViewById<LinearLayout>(R.id.btnEasy)?.setOnClickListener { launchGame(LEVEL_EASY) }
        findViewById<LinearLayout>(R.id.btnMedium)?.setOnClickListener { launchGame(LEVEL_MEDIUM) }
        findViewById<LinearLayout>(R.id.btnHard)?.setOnClickListener { launchGame(LEVEL_HARD) }
    }

    private fun launchGame(level: String) {
        val dest = if (game == GAME_STREET) StreetCleanupActivity::class.java
        else TrashSortingActivity::class.java

        startActivity(
            Intent(this, dest)
                .putExtra(EXTRA_GAME, game)
                .putExtra(EXTRA_LEVEL, level)
        )
    }
}
