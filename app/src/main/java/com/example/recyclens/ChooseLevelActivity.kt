package com.example.recyclens

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ChooseLevelActivity : AppCompatActivity(), BottomBar.LanguageAware {

    private var gameType: String = GameSelectActivity.GAME_TRASH_SORTING
    private lateinit var tvTitle: TextView
    private lateinit var tvEasyLabel: TextView
    private lateinit var tvMediumLabel: TextView
    private lateinit var tvHardLabel: TextView
    private lateinit var tvEasyDesc: TextView
    private lateinit var tvMediumDesc: TextView
    private lateinit var tvHardDesc: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguagePrefs.applyLocale(this)
        setContentView(R.layout.choose_level_page)
        BackgroundDriftHelper.attach(this)

        gameType = intent.getStringExtra(GameSelectActivity.EXTRA_GAME_TYPE)
            ?: GameSelectActivity.GAME_TRASH_SORTING

        val btnBack: View = findViewById(R.id.btnBack)
        tvTitle = findViewById(R.id.tvTitle)
        tvEasyLabel = findViewById(R.id.tvEasyLabel)
        tvMediumLabel = findViewById(R.id.tvMediumLabel)
        tvHardLabel = findViewById(R.id.tvHardLabel)
        tvEasyDesc = findViewById(R.id.tvEasyDesc)
        tvMediumDesc = findViewById(R.id.tvMediumDesc)
        tvHardDesc = findViewById(R.id.tvHardDesc)

        val btnEasy: View = findViewById(R.id.btnEasy)
        val btnMedium: View = findViewById(R.id.btnMedium)
        val btnHard: View = findViewById(R.id.btnHard)

        refreshLocalizedTexts()

        btnBack.setOnClickListener { finish() }

        btnEasy.setOnClickListener   { openGame(1) }
        btnMedium.setOnClickListener { openGame(2) }
        btnHard.setOnClickListener   { openGame(3) }
        setupBottomBar(BottomBar.Tab.PLAY)

        RecyclensEntryAnimator.play(this)
    }

    override fun onLanguageChanged() {
        refreshLocalizedTexts()
    }

    private fun refreshLocalizedTexts() {
        val isEnglish = LanguagePrefs.isEnglish(this)

        tvTitle.text = when (gameType) {
            GameSelectActivity.GAME_STREET_CLEANUP ->
                getString(if (isEnglish) R.string.label_choose_level_street_en else R.string.label_choose_level_street_tl)
            GameSelectActivity.GAME_TRASH_SORTING ->
                getString(if (isEnglish) R.string.label_choose_level_trash_en else R.string.label_choose_level_trash_tl)
            else ->
                getString(if (isEnglish) R.string.label_choose_level_en else R.string.label_choose_level_tl)
        }

        tvEasyLabel.text = getString(if (isEnglish) R.string.label_easy_en else R.string.label_easy_tl)
        tvMediumLabel.text = getString(if (isEnglish) R.string.label_medium_en else R.string.label_medium_tl)
        tvHardLabel.text = getString(if (isEnglish) R.string.label_hard_en else R.string.label_hard_tl)
        tvEasyDesc.text = getString(if (isEnglish) R.string.desc_easy_en else R.string.desc_easy_tl)
        tvMediumDesc.text = getString(if (isEnglish) R.string.desc_medium_en else R.string.desc_medium_tl)
        tvHardDesc.text = getString(if (isEnglish) R.string.desc_hard_en else R.string.desc_hard_tl)
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

        // Stop the browsing music before starting a game
        MusicManager.stop() // fixed: stop() takes no arguments

        val intent = Intent(this, target)
            .putExtra("extra_level", level)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        // When returning from a game, restart the browsing music.
        MusicManager.start(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        BackgroundDriftHelper.detach(this)
    }
}
