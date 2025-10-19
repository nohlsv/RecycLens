package com.example.recyclens

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.landing_page)

        findViewById<MaterialButton>(R.id.btn_start).setOnClickListener {
            startActivity(Intent(this, ScannerActivity::class.java))
            finish() // closes landing page
        }
    }
}
