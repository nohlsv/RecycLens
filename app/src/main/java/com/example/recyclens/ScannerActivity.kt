package com.example.recyclens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var titleBar: TextView
    private lateinit var infoTitle: TextView
    private lateinit var infoText: TextView
    private lateinit var infoRightIcon: ImageView
    private lateinit var btnCamera: ImageButton
    private lateinit var btnGallery: ImageButton

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private lateinit var detector: ObjectDetector

    private val labelToCategory = mapOf(
        "banana_peel" to "Biodegradable",
        "fruit_slice" to "Biodegradable",
        "vegetable_leaf" to "Biodegradable",
        "tissue" to "Biodegradable",
        "paper" to "Biodegradable",
        "plastic_cup" to "Non-Biodegradable",
        "pet_bottle" to "Non-Biodegradable",
        "snack_wrapper" to "Non-Biodegradable",
        "styrofoam_cup" to "Non-Biodegradable"
    )
    private val categoryToBin = mapOf(
        "Biodegradable" to "Green Bin (Nabubulok)",
        "Non-Biodegradable" to "Blue Bin (Di-Nabubulok)"
    )
    private val binIconForCategory = mapOf(
        "Biodegradable" to R.drawable.ic_green_bin,
        "Non-Biodegradable" to R.drawable.ic_blue_bin
    )

    private val requestCamPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else titleBar.text = "Camera permission denied"
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scanner_page)

        previewView = findViewById(R.id.previewView)
        titleBar = findViewById(R.id.titleBar)
        infoTitle = findViewById(R.id.infoTitle)
        infoText = findViewById(R.id.infoText)
        infoRightIcon = findViewById(R.id.infoRightIcon)
        btnCamera = findViewById(R.id.btnCamera)
        btnGallery = findViewById(R.id.btnGallery)

        cameraExecutor = Executors.newSingleThreadExecutor()

        initDetector()
        checkOrRequestCamera()

        btnCamera.setOnClickListener { captureAndDetect() }
        // btnGallery.setOnClickListener { /* TODO */ }
    }

    private fun checkOrRequestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCamPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, selector, preview, imageCapture
                )
            } catch (e: Exception) {
                titleBar.text = "Camera error: ${e.localizedMessage}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun initDetector() {
        val base = BaseOptions.builder()
            .setNumThreads(4)
            // .useNnapi() // optional
            // .useGpu()   // optional if GPU delegate is available
            .build()

        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(base)
            .setMaxResults(3)
            .setScoreThreshold(0.45f)
            .build()

        detector = ObjectDetector.createFromFileAndOptions(
            this,
            "models/waste_yolov8n.tflite",
            options
        )
    }

    private fun captureAndDetect() {
        val ic = imageCapture ?: return
        titleBar.text = "Scanning…"

        ic.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bmp = imageProxyToBitmap(image)
                    ?.let { rotateBitmapIfNeeded(it, image.imageInfo.rotationDegrees) }
                image.close()

                if (bmp == null) {
                    postUI { titleBar.text = "Failed to read image" }
                    return
                }
                runDetection(bmp)
            }

            override fun onError(exc: ImageCaptureException) {
                postUI { titleBar.text = "Capture error: ${exc.message}" }
            }
        })
    }

    private fun runDetection(bitmap: Bitmap) {
        val tensorImage = TensorImage.fromBitmap(bitmap)
        val results: List<Detection> = try {
            detector.detect(tensorImage)
        } catch (e: Exception) {
            postUI { titleBar.text = "Detection error: ${e.localizedMessage}" }
            return
        }

        if (results.isEmpty()) {
            postUI {
                titleBar.text = "No object detected"
                infoTitle.text = "Try again"
                infoText.text = "Center the item and hold steady."
                infoRightIcon.setImageResource(R.drawable.ic_info)
            }
            return
        }

        val top = results.maxByOrNull { d -> d.categories.maxOf { it.score } }!!
        val best = top.categories.maxByOrNull { it.score }!!
        val label = best.label.lowercase(Locale.ROOT)
        val confPct = (best.score * 100).toInt()

        val materialCategory = labelToCategory[label] ?: "Unrecognized"
        val binText = categoryToBin[materialCategory] ?: "—"
        val prettyLabel = label.replace("_", " ").replaceFirstChar { it.titlecase() }

        postUI {
            titleBar.text = prettyLabel

            if (materialCategory == "Unrecognized") {
                infoTitle.text = "Unrecognized item"
                infoText.text = "Detected: $prettyLabel ($confPct%). Please try again."
                infoRightIcon.setImageResource(R.drawable.ic_info)
            } else {
                infoTitle.text = "$materialCategory → $binText"
                infoText.text = "Detected: $prettyLabel • Confidence $confPct%."
                val icon = binIconForCategory[materialCategory] ?: R.drawable.ic_info
                infoRightIcon.setImageResource(icon)
            }
        }
    }

    private fun postUI(block: () -> Unit) = runOnUiThread { block() }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

/** ImageProxy -> Bitmap via YUV -> JPEG (kept from your version), with null-safety */
private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
    if (image.format != ImageFormat.YUV_420_888) return null

    val yBuffer = image.planes[0].buffer
    val uBuffer = image.planes[1].buffer
    val vBuffer = image.planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
    val bytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

/** Rotate the bitmap to match the device orientation reported by CameraX */
private fun rotateBitmapIfNeeded(src: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return src
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}
