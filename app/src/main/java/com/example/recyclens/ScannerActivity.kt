package com.example.recyclens

import android.os.Bundle
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ScannerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scanner)

        findViewById<ImageButton>(R.id.btnCamera).setOnClickListener {
            Toast.makeText(this, "Camera feature coming soon.", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.btnGallery).setOnClickListener {
            Toast.makeText(this, "Gallery feature coming soon.", Toast.LENGTH_SHORT).show()
        }
    }
}
