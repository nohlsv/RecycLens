package com.example.recyclens

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import ph.recyclens.app.R

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.landing_page) // res/layout/landing_page.xml

        findViewById<MaterialButton>(R.id.btn_start)?.setOnClickListener {
            // Go straight to Scanner. Change to GameSelectActivity.kt if you prefer.
            startActivity(Intent(this, ScannerActivity::class.java))
        }
    }
}
