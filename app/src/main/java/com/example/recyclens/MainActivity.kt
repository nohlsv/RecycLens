package com.example.recyclens

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.activity.ComponentActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.landing_page)
        
        // Set up the START button click listener
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            // TODO: Navigate to next screen
        }
    }
}