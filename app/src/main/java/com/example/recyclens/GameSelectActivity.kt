package com.example.recyclens

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class GameSelectActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME_TYPE = "extra_game_type"
        const val GAME_TRASH_SORTING = "TRASH"
        const val GAME_STREET_CLEANUP = "STREET"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.game_page)

        setupBottomBar(BottomBar.Tab.PLAY)

        val cardTrash: View = findViewById(R.id.cardTrashSorting)
        val cardStreet: View = findViewById(R.id.cardStreetCleanup)

        cardTrash.setOnClickListener {
            openChooseLevel(GAME_TRASH_SORTING)
        }

        cardStreet.setOnClickListener {
            openChooseLevel(GAME_STREET_CLEANUP)
        }
    }

    private fun openChooseLevel(gameType: String) {
        val intent = Intent(this, ChooseLevelActivity::class.java)
            .putExtra(EXTRA_GAME_TYPE, gameType)
        startActivity(intent)
    }
}
