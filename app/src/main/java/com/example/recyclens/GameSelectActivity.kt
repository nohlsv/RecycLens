package com.example.recyclens.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.recyclens.R

class GamesSelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.games_page)

        // Tap: Trash Sorting -> Choose Level
        findViewById<View>(R.id.cardTrashSorting).setOnClickListener {
            startActivity(
                Intent(this, ChooseLevelActivity::class.java)
                    .putExtra(ChooseLevelActivity.EXTRA_GAME, "trash")
            )
        }

        // Tap: Street Cleanup -> Choose Level
        findViewById<View>(R.id.cardStreetCleanup).setOnClickListener {
            startActivity(
                Intent(this, ChooseLevelActivity::class.java)
                    .putExtra(ChooseLevelActivity.EXTRA_GAME, "street")
            )
        }
    }
}
