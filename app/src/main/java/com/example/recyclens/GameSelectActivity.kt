package com.example.recyclens

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class GameSelectActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make sure this matches your XML filename exactly (game_page.xml or games_page.xml)
        setContentView(R.layout.game_page)

        // Go to ChooseLevel with the selected game
        findViewById<View>(R.id.cardTrashSorting).setOnClickListener {
            startActivity(
                Intent(this, ChooseLevelActivity::class.java)
                    .putExtra(ChooseLevelActivity.EXTRA_GAME, ChooseLevelActivity.GAME_TRASH)
            )
        }
        findViewById<View>(R.id.cardStreetCleanup).setOnClickListener {
            startActivity(
                Intent(this, ChooseLevelActivity::class.java)
                    .putExtra(ChooseLevelActivity.EXTRA_GAME, ChooseLevelActivity.GAME_STREET)
            )
        }
    }
}
