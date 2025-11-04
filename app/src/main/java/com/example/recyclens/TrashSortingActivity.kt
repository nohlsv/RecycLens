package com.example.recyclens.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.recyclens.R

class TrashSortingGameActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.trash_sorting)

        val level = intent.getStringExtra(ChooseLevelActivity.EXTRA_LEVEL) ?: "easy"
        findViewById<TextView>(R.id.tvLevel).text = level.replaceFirstChar { it.uppercase() }

        findViewById<ImageButton>(R.id.btnBackTrash).setOnClickListener { finish() }
        findViewById<ImageButton>(R.id.btnInfoTrash).setOnClickListener {
            // TODO: show game rules dialog
        }
        findViewById<TextView>(R.id.btnStartTrash).setOnClickListener {
            // TODO: start game logic
        }
    }
}
