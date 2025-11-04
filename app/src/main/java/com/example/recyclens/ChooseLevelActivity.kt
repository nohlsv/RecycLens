package com.example.recyclens.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.recyclens.R

class ChooseLevelActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_GAME = "game"     // "trash" or "street"
        const val EXTRA_LEVEL = "level"   // "easy" | "medium" | "hard"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.screen_choose_level)

        val game = intent.getStringExtra(EXTRA_GAME) ?: "trash"
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        fun go(level: String) {
            val target = if (game == "street")
                Intent(this, StreetCleanupGameActivity::class.java)
            else
                Intent(this, TrashSortingGameActivity::class.java)
            target.putExtra(EXTRA_LEVEL, level)
            startActivity(target)
        }

        findViewById<View>(R.id.btnEasy).setOnClickListener { go("easy") }
        findViewById<View>(R.id.btnMedium).setOnClickListener { go("medium") }
        findViewById<View>(R.id.btnHard).setOnClickListener { go("hard") }
    }
}
