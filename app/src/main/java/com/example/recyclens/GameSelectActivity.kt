package com.example.recyclens

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class GameSelectActivity : AppCompatActivity(), BottomBar.LanguageAware {

    companion object {
        const val EXTRA_GAME_TYPE = "extra_game_type"
        const val GAME_TRASH_SORTING = "TRASH"
        const val GAME_STREET_CLEANUP = "STREET"
    }

    private lateinit var tvTrashTitle: TextView
    private lateinit var tvStreetTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguagePrefs.applyLocale(this)
        setContentView(R.layout.game_page)
        BackgroundDriftHelper.attach(this)

        setupBottomBar(BottomBar.Tab.PLAY)

        val cardTrash: View = findViewById(R.id.cardTrashSorting)
        val cardStreet: View = findViewById(R.id.cardStreetCleanup)
        tvTrashTitle = findViewById(R.id.tvTrashSortingTitle)
        tvStreetTitle = findViewById(R.id.tvStreetCleanupTitle)

        refreshLocalizedTexts()

        cardTrash.setOnClickListener {
            openChooseLevel(GAME_TRASH_SORTING)
        }

        cardStreet.setOnClickListener {
            openChooseLevel(GAME_STREET_CLEANUP)
        }

        RecyclensEntryAnimator.play(this)
    }

    override fun onLanguageChanged() {
        refreshLocalizedTexts()
    }

    private fun refreshLocalizedTexts() {
        tvTrashTitle.text = getString(R.string.game_trash_sorting)
        tvStreetTitle.text = getString(R.string.game_street_cleanup)
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

    private fun openChooseLevel(gameType: String) {
        val intent = Intent(this, ChooseLevelActivity::class.java)
            .putExtra(EXTRA_GAME_TYPE, gameType)
        startActivity(intent)
    }
}
