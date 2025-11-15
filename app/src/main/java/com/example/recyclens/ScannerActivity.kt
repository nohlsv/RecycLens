package com.example.recyclens

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.InputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
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

    // --- TFLite: two models ---
    private var fruitInterpreter: Interpreter? = null
    private var fruitLabels: List<String> = emptyList()

    private var plasticInterpreter: Interpreter? = null
    private var plasticLabels: List<String> = emptyList()

    // Place these files in: app/src/main/assets/
    private val FRUIT_MODEL = "fruit_model1.tflite"
    private val PLASTIC_MODEL = "plastic_cup_eggplant_okra.tflite"
    private val FRUIT_LABELS_FILE = "fruit_labels.txt"
    private val PLASTIC_LABELS_FILE = "plastic_labels.txt"

    private val INPUT_SIZE = 224

    // Strict thresholds so it only answers when confident
    private val THRESHOLD = 0.90f
    private val MARGIN = 0.15f

    // ---------- Permission & Gallery ----------

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera()
            else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val bmp = decodeBitmapFromUriSafely(contentResolver, uri, maxDim = 1600)
            if (bmp != null) {
                showCapturedPhoto(bmp)
                runRecognitionIfAvailable(bmp)
            } else {
                Toast.makeText(this, "Could not open image", Toast.LENGTH_SHORT).show()
            }
        }

    // ---------- Lifecycle ----------

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

        initModels()
        ensureCameraReady()

        btnCamera.setOnClickListener {
            if (showingCaptured) showLivePreview()
            else captureFrameFromPreview()
        }

        btnGallery.setOnClickListener {
            pickImage.launch("image/*")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        fruitInterpreter?.close()
        plasticInterpreter?.close()
    }

    // ---------- Camera ----------

    private fun ensureCameraReady() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
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

            showLivePreview()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureFrameFromPreview() {
        titleBar.text = "Taking a picture..."
        previewView.post {
            val bmp = previewView.bitmap
            if (bmp != null) {
                showCapturedPhoto(bmp)
                runRecognitionIfAvailable(bmp)
            } else {
                titleBar.text = "Oops, try again!"
                Toast.makeText(this, "Unable to capture picture", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- UI: live vs captured ----------

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
        infoRightIcon.setImageResource(R.drawable.ic_green_bin)
    }

    // ---------- Model init (independent, robust) ----------

    private fun initModels() {
        // Try fruit model
        try {
            val mapped = loadModelMapped(FRUIT_MODEL)
            fruitInterpreter = Interpreter(mapped, Interpreter.Options())
            fruitLabels = assets.open(FRUIT_LABELS_FILE)
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            fruitInterpreter = null
            fruitLabels = emptyList()
        }

        // Try plastic/veggies model
        try {
            val mapped = loadModelMapped(PLASTIC_MODEL)
            plasticInterpreter = Interpreter(mapped, Interpreter.Options())
            plasticLabels = assets.open(PLASTIC_LABELS_FILE)
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            plasticInterpreter = null
            plasticLabels = emptyList()
        }

        if (fruitInterpreter == null && plasticInterpreter == null) {
            Toast.makeText(
                this,
                "I can't load my models. Please check the .tflite and .txt files in assets.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun loadModelMapped(name: String): MappedByteBuffer {
        val fd = assets.openFd(name)
        FileInputStream(fd.fileDescriptor).use { fis ->
            val channel: FileChannel = fis.channel
            return channel.map(
                FileChannel.MapMode.READ_ONLY,
                fd.startOffset,
                fd.declaredLength
            )
        }
    }

    // ---------- Inference & routing ----------

    private fun runRecognitionIfAvailable(bitmap: Bitmap) {
        val fruitInt = fruitInterpreter
        val plasticInt = plasticInterpreter

        if (fruitInt == null && plasticInt == null) {
            titleBar.text = "Photo Loaded"
            infoTitle.text = "Helper is sleeping"
            infoText.text  = "Ask your teacher to check the app files."
            infoRightIcon.setImageResource(R.drawable.ic_green_bin)
            return
        }

        titleBar.text = "Looking closely..."
        infoTitle.text = "Let me check"
        infoText.text  = "I’m checking if this is fruit, vegetable, or plastic."
        infoRightIcon.setImageResource(R.drawable.ic_green_bin)

        cameraExecutor.execute {
            val candidates = mutableListOf<PredictedItem>()

            // Fruit model → banana / mango
            if (fruitInt != null && fruitLabels.isNotEmpty()) {
                classifyWithModel(bitmap, fruitInt, fruitLabels)?.let { (label, score) ->
                    mapToCategory(label)?.let { cat ->
                        candidates.add(PredictedItem(label, score, cat))
                    }
                }
            }

            // Plastic/veg model → plastic cup / okra / eggplant
            if (plasticInt != null && plasticLabels.isNotEmpty()) {
                classifyWithModel(bitmap, plasticInt, plasticLabels)?.let { (label, score) ->
                    mapToCategory(label)?.let { cat ->
                        candidates.add(PredictedItem(label, score, cat))
                    }
                }
            }

            val best = candidates.maxByOrNull { it.confidence }

            runOnUiThread {
                if (best == null) {
                    titleBar.text = "I’m not sure"
                    infoTitle.text = "I don’t know this item."
                    infoText.text =
                        "Ask your teacher or a grown-up which bin to use."
                    infoRightIcon.setImageResource(R.drawable.ic_green_bin)
                } else {
                    showCategoryResult(best)
                }
            }
        }
    }

    private data class PredictedItem(
        val label: String,
        val confidence: Float,
        val category: Category
    )

    private enum class Category {
        PLASTIC, FRUIT, VEGETABLE
    }

    private fun classifyWithModel(
        bitmap: Bitmap,
        interpreter: Interpreter,
        labels: List<String>
    ): Pair<String, Float>? {
        if (labels.isEmpty()) return null

        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        val input = Array(1) { Array(INPUT_SIZE) { Array(INPUT_SIZE) { FloatArray(3) } } }
        for (y in 0 until INPUT_SIZE) {
            for (x in 0 until INPUT_SIZE) {
                val px = resized.getPixel(x, y)
                input[0][y][x][0] = Color.red(px) / 255f
                input[0][y][x][1] = Color.green(px) / 255f
                input[0][y][x][2] = Color.blue(px) / 255f
            }
        }

        val output = Array(1) { FloatArray(labels.size) }
        interpreter.run(input, output)

        val scores = output[0]
        if (scores.isEmpty()) return null

        var maxIdx = 0
        var maxScore = scores[0]
        var secondScore = Float.NEGATIVE_INFINITY

        for (i in 1 until scores.size) {
            val s = scores[i]
            if (s > maxScore) {
                secondScore = maxScore
                maxScore = s
                maxIdx = i
            } else if (s > secondScore) {
                secondScore = s
            }
        }

        val gap = maxScore - (if (secondScore.isFinite()) secondScore else 0f)

        return if (maxScore >= THRESHOLD && gap >= MARGIN && maxIdx in labels.indices) {
            labels[maxIdx] to maxScore
        } else {
            null
        }
    }

    /**
     * Map raw labels to our 3 item categories.
     * - PLASTIC: plastic cup
     * - FRUIT: banana, mango
     * - VEGETABLE: okra, eggplant
     * Anything else (like orange) is ignored (null).
     */
    private fun mapToCategory(rawLabel: String): Category? {
        val label = rawLabel.trim().lowercase()

        return when {
            // Plastic cup
            label.contains("plastic") && label.contains("cup") ->
                Category.PLASTIC

            // Fruits
            label == "banana" || label.contains("banana") ||
                    label == "mango"  || label.contains("mango") ->
                Category.FRUIT

            // Vegetables
            label == "okra" || label.contains("okra") ||
                    label == "eggplant" || label.contains("eggplant") ||
                    label == "aubergine" ->
                Category.VEGETABLE

            else -> null
        }
    }

    /**
     * Show waste category + bin color in kid-friendly form.
     *
     * Biodegradable (GREEN bin):
     *  - Fruit (banana, mango)
     *  - Vegetable (okra, eggplant)
     *
     * Non-biodegradable (BLUE bin):
     *  - Plastic cups
     */
    private fun showCategoryResult(item: PredictedItem) {
        val label = item.label.trim().lowercase()

        when (item.category) {
            Category.PLASTIC -> {
                // Non-biodegradable → BLUE bin
                titleBar.text = "Plastic Cup"
                infoTitle.text = "Non-biodegradable waste"
                infoText.text =
                    "This is a PLASTIC CUP.\nPut it in the BLUE BIN."
                infoRightIcon.setImageResource(R.drawable.ic_blue_bin)
            }

            Category.FRUIT -> {
                // Biodegradable → GREEN bin
                titleBar.text = "Fruit"
                val name = when {
                    label.contains("banana") -> "banana"
                    label.contains("mango")  -> "mango"
                    else -> "fruit"
                }
                infoTitle.text = "Biodegradable waste"
                infoText.text =
                    "This is a $name.\nPut peels and leftovers in the GREEN BIN."
                infoRightIcon.setImageResource(R.drawable.ic_green_bin)
            }

            Category.VEGETABLE -> {
                // Biodegradable → GREEN bin
                titleBar.text = "Vegetable"
                val name = when {
                    label.contains("okra") -> "okra"
                    label.contains("eggplant") || label.contains("aubergine") -> "eggplant"
                    else -> "vegetable"
                }
                infoTitle.text = "Biodegradable waste"
                infoText.text =
                    "This is $name.\nPut scraps in the GREEN BIN."
                infoRightIcon.setImageResource(R.drawable.ic_green_bin)
            }
        }
    }
}

/* ---------- Gallery decode helper ---------- */

private fun decodeBitmapFromUriSafely(
    resolver: ContentResolver,
    uri: Uri,
    maxDim: Int = 1600
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, bounds)
    }

    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
    }

    val maxSrc = maxOf(bounds.outWidth, bounds.outHeight)
    var sample = 1
    while (maxSrc / sample > maxDim) sample *= 2

    val opts = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    var input: InputStream? = null
    return try {
        input = resolver.openInputStream(uri)
        BitmapFactory.decodeStream(input, null, opts)
    } finally {
        input?.close()
    }
}
