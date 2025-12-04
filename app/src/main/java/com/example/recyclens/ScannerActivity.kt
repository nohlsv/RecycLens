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

    // ------- MODEL 2: old fruits / veggies / plastic -------
    private var model2Interpreter: Interpreter? = null
    private var model2Labels: List<String> = emptyList()
    private val MODEL2 = "model2.tflite"
    private val MODEL2_LABELS_FILE = "model2_labels.txt"

    // ------- MODEL 3: new leaves / grass / styro -------
    private var extraInterpreter: Interpreter? = null
    private var extraLabels: List<String> = emptyList()
    private val EXTRA_MODEL = "model.tflite"
    private val EXTRA_LABELS_FILE = "model_labels.txt"

    private val INPUT_SIZE = 224
    private val THRESHOLD = 0.60f
    private val CUP_BOTTLE_MARGIN = 0.10f

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
        model2Interpreter?.close()
        extraInterpreter?.close()
    }

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

    // ----------------- LOAD BOTH MODELS -----------------

    private fun initModels() {
        try {
            // main model2
            val mapped2 = loadModelMapped(MODEL2)
            model2Interpreter = Interpreter(mapped2, Interpreter.Options())
            model2Labels = assets.open(MODEL2_LABELS_FILE)
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            model2Interpreter = null
            model2Labels = emptyList()
        }

        try {
            // extra model for leaves / grass / styrofoam
            val mappedExtra = loadModelMapped(EXTRA_MODEL)
            extraInterpreter = Interpreter(mappedExtra, Interpreter.Options())
            extraLabels = assets.open(EXTRA_LABELS_FILE)
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() }
        } catch (_: Exception) {
            extraInterpreter = null
            extraLabels = emptyList()
        }

        if (model2Interpreter == null && extraInterpreter == null) {
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

    // ----------------- RUN BOTH MODELS -----------------

    private fun runRecognitionIfAvailable(bitmap: Bitmap) {
        val mainInt = model2Interpreter
        val extraInt = extraInterpreter

        if ((mainInt == null || model2Labels.isEmpty()) &&
            (extraInt == null || extraLabels.isEmpty())
        ) {
            titleBar.text = "Photo Loaded"
            infoTitle.text = "Helper is sleeping"
            infoText.text  = "Ask your teacher to check the app files."
            infoRightIcon.setImageResource(R.drawable.ic_green_bin)
            return
        }

        titleBar.text = "Looking closely..."
        infoTitle.text = "Let me check"
        infoText.text  = "I’m checking what kind of waste this is."
        infoRightIcon.setImageResource(R.drawable.ic_green_bin)

        cameraExecutor.execute {
            var bestPair: Pair<String, Float>? = null

            // run model2 if available
            if (mainInt != null && model2Labels.isNotEmpty()) {
                val pred2 = classifyWithModel(bitmap, mainInt, model2Labels)
                val res2 = pred2?.let { chooseFinalLabel(it) }
                if (res2 != null && (bestPair == null || res2.second > bestPair!!.second)) {
                    bestPair = res2
                }
            }

            // run extra model if available
            if (extraInt != null && extraLabels.isNotEmpty()) {
                val predExtra = classifyWithModel(bitmap, extraInt, extraLabels)
                val resExtra = predExtra?.let { chooseFinalLabelSimple(it) }
                if (resExtra != null && (bestPair == null || resExtra.second > bestPair!!.second)) {
                    bestPair = resExtra
                }
            }

            val best = bestPair?.let { (label, score) ->
                mapToCategory(label)?.let { cat ->
                    PredictedItem(label, score, cat)
                }
            }

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

    // ----------------- DATA CLASSES -----------------

    private data class PredictedItem(
        val label: String,
        val confidence: Float,
        val category: Category
    )

    private enum class Category {
        PLASTIC, FRUIT, VEGETABLE
    }

    private data class Top2Prediction(
        val bestLabel: String,
        val bestScore: Float,
        val secondLabel: String?,
        val secondScore: Float?
    )

    // ----------------- CLASSIFICATION HELPERS -----------------

    private fun cropCenterSquare(src: Bitmap): Bitmap {
        val size = minOf(src.width, src.height)
        val x = (src.width - size) / 2
        val y = (src.height - size) / 2
        return Bitmap.createBitmap(src, x, y, size, size)
    }

    private fun classifyWithModel(
        bitmap: Bitmap,
        interpreter: Interpreter,
        labels: List<String>
    ): Top2Prediction? {
        if (labels.isEmpty()) return null

        val square = cropCenterSquare(bitmap)
        val resized = Bitmap.createScaledBitmap(square, INPUT_SIZE, INPUT_SIZE, true)

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

        var bestIdx = 0
        var bestScore = scores[0]
        var secondIdx = -1
        var secondScore = Float.NEGATIVE_INFINITY

        for (i in 1 until scores.size) {
            val s = scores[i]
            if (s > bestScore) {
                secondScore = bestScore
                secondIdx = bestIdx
                bestScore = s
                bestIdx = i
            } else if (s > secondScore) {
                secondScore = s
                secondIdx = i
            }
        }

        val bestLabel = labels[bestIdx]
        val secondLabel = if (secondIdx in labels.indices) labels[secondIdx] else null
        val secondScoreFinal = if (secondIdx >= 0) secondScore else null

        return Top2Prediction(bestLabel, bestScore, secondLabel, secondScoreFinal)
    }

    // original chooseFinalLabel (handles bottle vs cup margin)
    private fun chooseFinalLabel(pred: Top2Prediction): Pair<String, Float>? {
        if (pred.bestScore < THRESHOLD) return null

        var finalLabel = pred.bestLabel
        val bestLower = pred.bestLabel.lowercase()
        val secondLabel = pred.secondLabel
        val secondScore = pred.secondScore ?: Float.NEGATIVE_INFINITY
        val secondLower = secondLabel?.lowercase() ?: ""

        if (
            bestLower.contains("bottle") &&
            secondLabel != null &&
            secondLower.contains("cup") &&
            pred.bestScore - secondScore < CUP_BOTTLE_MARGIN
        ) {
            finalLabel = secondLabel
        }

        return finalLabel to pred.bestScore
    }

    // simpler version: no special bottle/cup logic
    private fun chooseFinalLabelSimple(pred: Top2Prediction): Pair<String, Float>? {
        if (pred.bestScore < THRESHOLD) return null
        return pred.bestLabel to pred.bestScore
    }

    // ----------------- LABEL → CATEGORY (includes new labels) -----------------

    private fun mapToCategory(rawLabel: String): Category? {
        val label = rawLabel.trim().lowercase()

        return when {
            // NEW: leaves & grass are biodegradable (treat as VEGETABLE)
            label.contains("leave") || label.contains("leaves") -> Category.VEGETABLE
            label.contains("grass") -> Category.VEGETABLE

            // NEW: styrofoam tray = non-biodegradable -> PLASTIC / BLUE BIN
            label.contains("styro") || label.contains("styrofoam") || label.contains("tray") ->
                Category.PLASTIC

            // old mappings
            label.contains("ampalaya") -> Category.VEGETABLE
            label.contains("kangkong") -> Category.VEGETABLE
            label.contains("apple") -> Category.FRUIT
            label.contains("bermuda") && label.contains("grass") -> Category.VEGETABLE
            label.contains("tissue") -> Category.VEGETABLE
            label.contains("bottle") -> Category.PLASTIC
            label.contains("banana") -> Category.FRUIT
            label.contains("plastic") && label.contains("cup") -> Category.PLASTIC
            label.contains("mango") -> Category.FRUIT
            label.contains("okra") -> Category.VEGETABLE
            label.contains("eggplant") -> Category.VEGETABLE
            else -> null
        }
    }

    // ----------------- SHOW RESULT (BINS) -----------------

    private fun showCategoryResult(item: PredictedItem) {
        val label = item.label.trim().lowercase()

        when (item.category) {
            Category.PLASTIC -> {
                val niceName = when {
                    label.contains("styro") || label.contains("styrofoam") || label.contains("tray") ->
                        "Styrofoam Tray"
                    label.contains("bottle") -> "Plastic Bottle"
                    label.contains("cup")    -> "Plastic Cup"
                    else                     -> "Plastic Item"
                }
                titleBar.text = niceName
                infoTitle.text = "Non-biodegradable waste"
                infoText.text =
                    "This is a $niceName.\nPut it in the BLUE BIN."
                infoRightIcon.setImageResource(R.drawable.ic_blue_bin)
            }

            Category.FRUIT -> {
                val name = when {
                    label.contains("apple")  -> "Apple"
                    label.contains("banana") -> "Banana"
                    label.contains("mango")  -> "Mango"
                    else -> "Fruit"
                }
                titleBar.text = name
                infoTitle.text = "Biodegradable waste"
                infoText.text =
                    "This is a $name.\nPut peels and leftovers in the GREEN BIN."
                infoRightIcon.setImageResource(R.drawable.ic_green_bin)
            }

            Category.VEGETABLE -> {
                val name = when {
                    // NEW: leaves / grass
                    label.contains("leave") || label.contains("leaves") -> "Leaves"
                    label.contains("grass") && !label.contains("bermuda") -> "Grass"

                    label.contains("okra") -> "Okra"
                    label.contains("eggplant") -> "Eggplant"
                    label.contains("ampalaya") -> "Ampalaya"
                    label.contains("kangkong") -> "Kangkong"
                    label.contains("bermuda") && label.contains("grass") -> "Bermuda Grass"
                    label.contains("tissue") -> "Tissue Roll"
                    else -> "Vegetable"
                }
                titleBar.text = name
                infoTitle.text = "Biodegradable waste"
                infoText.text =
                    "This is $name.\nPut scraps in the GREEN BIN."
                infoRightIcon.setImageResource(R.drawable.ic_green_bin)
            }
        }
    }
}

// -------- helper remains the same --------

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
