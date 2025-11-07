package com.example.recyclens

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.InputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : ComponentActivity() {

    private lateinit var topCard: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var titleBar: TextView
    private lateinit var infoTitle: TextView
    private lateinit var infoText: TextView
    private lateinit var infoRightIcon: ImageView
    private lateinit var btnCamera: ImageButton
    private lateinit var btnGallery: ImageButton

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private var capturedView: ImageView? = null
    private var showingCaptured = false

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val bmp = decodeBitmapFromUriSafely(contentResolver, uri, maxDim = 1600)
            if (bmp != null) {
                showCapturedPhoto(bmp)
                titleBar.text = "Photo Loaded"
                infoTitle.text = "Preview"
                infoText.text  = "Tap the orange button to return to camera."
                infoRightIcon.setImageResource(android.R.drawable.ic_menu_gallery)
            } else {
                Toast.makeText(this, "Could not open image", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scanner_page)

        topCard       = findViewById(R.id.topCard)
        previewView   = findViewById(R.id.previewView)
        titleBar      = findViewById(R.id.titleBar)
        infoTitle     = findViewById(R.id.infoTitle)
        infoText      = findViewById(R.id.infoText)
        infoRightIcon = findViewById(R.id.infoRightIcon)
        btnCamera     = findViewById(R.id.btnCamera)
        btnGallery    = findViewById(R.id.btnGallery)

        BottomBar.setup(this, selected = BottomBar.Tab.SCAN)

        cameraExecutor = Executors.newSingleThreadExecutor()
        ensureCameraReady()

        btnCamera.setOnClickListener {
            if (showingCaptured) showLivePreview() else captureFrameFromPreview()
        }
        btnGallery.setOnClickListener { pickImage.launch("image/*") }
    }

    private fun ensureCameraReady() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            provider.unbindAll()
            provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture
            )

            titleBar.text = "Camera Ready"
            infoTitle.text = getString(R.string.info_title)
            infoText.text  = getString(R.string.info_description)
            infoRightIcon.setImageResource(android.R.drawable.ic_dialog_info)

            showLivePreview()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureFrameFromPreview() {
        titleBar.text = "Capturing..."
        previewView.post {
            val bmp = previewView.bitmap
            if (bmp != null) {
                showCapturedPhoto(bmp)
                titleBar.text = "Photo Captured"
                infoTitle.text = "Preview"
                infoText.text  = "Tap the orange button to retake."
                infoRightIcon.setImageResource(android.R.drawable.ic_menu_camera)
            } else {
                titleBar.text = "Capture failed"
                Toast.makeText(this, "Unable to capture frame", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCapturedPhoto(bitmap: Bitmap) {
        if (capturedView == null) {
            capturedView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                scaleType = ImageView.ScaleType.CENTER_CROP
                if (Build.VERSION.SDK_INT >= 21) elevation = previewView.elevation + 1f
            }
            topCard.addView(capturedView)
        }
        capturedView!!.setImageBitmap(bitmap)
        capturedView!!.visibility = View.VISIBLE
        previewView.visibility = View.INVISIBLE
        showingCaptured = true
    }

    private fun showLivePreview() {
        capturedView?.visibility = View.GONE
        previewView.visibility = View.VISIBLE
        showingCaptured = false

        titleBar.text = "Camera Ready"
        infoTitle.text = getString(R.string.info_title)
        infoText.text  = getString(R.string.info_description)
        infoRightIcon.setImageResource(android.R.drawable.ic_dialog_info)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

/* ---------- Gallery decode helper ---------- */
private fun decodeBitmapFromUriSafely(
    resolver: ContentResolver,
    uri: Uri,
    maxDim: Int = 1600
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }

    val maxSrc = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (maxSrc / sample > maxDim) sample *= 2

    val opts = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    var bmp: Bitmap? = null
    var input: InputStream? = null
    try {
        input = resolver.openInputStream(uri)
        bmp = BitmapFactory.decodeStream(input, null, opts)
    } finally {
        input?.close()
    }
    return bmp
}
