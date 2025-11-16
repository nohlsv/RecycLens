package com.example.recyclens

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class TrashSortingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.trash_sorting)

        setupBottomBar(BottomBar.Tab.PLAY)

        val btnBack: View = findViewById(R.id.btnBackTrash)
        val tvLevel: TextView = findViewById(R.id.tvLevel)
        tvLevel.text = "Easy"

        btnBack.setOnClickListener { finish() }
    }
}
