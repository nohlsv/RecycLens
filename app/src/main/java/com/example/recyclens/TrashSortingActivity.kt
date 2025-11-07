package com.example.recyclens

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// TrashSortingActivity.kt (same pattern for StreetCleanupActivity)
class TrashSortingActivity : AppCompatActivity() {
    private lateinit var tvLevel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.trash_sorting)

        setupBottomBar(BottomBar.Tab.PLAY)

        val level = intent.getStringExtra(ChooseLevelActivity.EXTRA_LEVEL) ?: "Easy"
        tvLevel = findViewById(R.id.tvLevel)
        tvLevel.text = level

        findViewById<ImageButton>(R.id.btnBackTrash).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        startGame(level)
        findViewById<TextView>(R.id.btnStartTrash).setOnClickListener { startGame(level) }
    }

    private fun startGame(level: String) {
        Toast.makeText(this, "Trash Sorting Game Started at $level level!", Toast.LENGTH_SHORT).show()
    }
}
