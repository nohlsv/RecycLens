package com.example.recyclens

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.recyclens.data.db.AppDatabase
import com.example.recyclens.data.model.WasteCategory
import com.example.recyclens.data.model.WasteMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScannerActivity : AppCompatActivity() {

    private lateinit var topCard: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var titleBar: TextView
    private lateinit var infoTitle: TextView
    private lateinit var infoText: TextView
    private lateinit var infoRightIcon: ImageView
    private lateinit var btnCamera: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var navScan: ImageButton
    private lateinit var navPlay: ImageView
    private lateinit var langToggle: RelativeLayout
    private lateinit var langText: TextView
    private lateinit var btnSpeaker: ImageButton
    private lateinit var labelScan: TextView
    private lateinit var labelPlay: TextView

    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private var capturedView: ImageView? = null
    private var showingCaptured = false

    private lateinit var model2Interpreter: Interpreter
    private lateinit var extraInterpreter: Interpreter
    private lateinit var model2Labels: List<String>
    private lateinit var extraLabels: List<String>

    private var mediaPlayer: MediaPlayer? = null

    private var isEnglish: Boolean = true

    private var lastPrediction: PredictedItem? = null
    private var lastMaterial: WasteMaterial? = null
    private var lastCategory: WasteCategory? = null

    companion object {
        private const val MODEL2 = "model2.tflite"
        private const val MODEL2_LABELS_FILE = "model2_labels.txt"
        private const val EXTRA_MODEL = "model.tflite"
        private const val EXTRA_LABELS_FILE = "model_labels.txt"
        private const val INPUT_SIZE = 224
        private const val THRESHOLD = 0.60f
        private const val CUP_BOTTLE_MARGIN = 0.10f
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else Toast.makeText(
                this,
                "Camera permission required",
                Toast.LENGTH_SHORT
            ).show()
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            val bmp = decodeBitmapFromUriSafely(contentResolver, uri, maxDim = 1600)
            if (bmp != null) {
                showCapturedPhoto(bmp)
                classifyAndFetch(bmp)
            } else {
                Toast.makeText(this, "Could not open image", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scanner_page)

        mediaPlayer = MediaPlayer.create(this, R.raw.recyclens_browsing_music)
        mediaPlayer?.isLooping = true

        topCard = findViewById(R.id.topCard)
        previewView = findViewById(R.id.previewView)
        titleBar = findViewById(R.id.titleBar)
        infoTitle = findViewById(R.id.infoTitle)
        infoText = findViewById(R.id.infoText)
        infoRightIcon = findViewById(R.id.infoRightIcon)
        btnCamera = findViewById(R.id.btnCamera)
        btnGallery = findViewById(R.id.btnGallery)
        navScan = findViewById(R.id.navScan)
        navPlay = findViewById(R.id.navPlay)
        langToggle = findViewById(R.id.langToggle)
        langText = findViewById(R.id.langText)
        btnSpeaker = findViewById(R.id.btnSpeaker)
        labelScan = findViewById(R.id.labelScan)
        labelPlay = findViewById(R.id.labelPlay)

        cameraExecutor = Executors.newSingleThreadExecutor()

        initModels()
        ensureCameraReady()
        setupBottomBar()
        setupLanguageToggle()
        setupButtons()

        showLivePreview()
        renderPredictionOrIdle()
    }

    override fun onResume() {
        super.onResume()
        if (mediaPlayer != null && mediaPlayer?.isPlaying == false) mediaPlayer?.start()
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer?.isPlaying == true) mediaPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        model2Interpreter.close()
        extraInterpreter.close()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun setupButtons() {
        btnCamera.setOnClickListener {
            if (showingCaptured) {
                showLivePreview()
            } else {
                captureFrameFromPreview()
            }
        }

        btnGallery.setOnClickListener { pickImage.launch("image/*") }

        btnSpeaker.setOnClickListener {
            Toast.makeText(
                this,
                if (isEnglish) "Voice guide not implemented yet."
                else "Hindi pa available ang voice guide.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun setupLanguageToggle() {
        updateLanguageTexts()
        langToggle.setOnClickListener {
            isEnglish = !isEnglish
            updateLanguageTexts()
            renderPredictionOrIdle()
        }
    }

    private fun updateLanguageTexts() {
        if (isEnglish) {
            langText.text = "EN"
            labelScan.text = "Scan Trash"
            labelPlay.text = "Play Games"
        } else {
            langText.text = "TL"
            labelScan.text = "I-scan ang Basura"
            labelPlay.text = "Maglaro"
        }
    }

    private fun setupBottomBar() {
        navScan.isSelected = true
    }

    private fun ensureCameraReady() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
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
        titleBar.text = if (isEnglish) "Taking a picture..." else "Kumukuha ng larawan..."
        previewView.post {
            val bmp = previewView.bitmap
            if (bmp != null) {
                showCapturedPhoto(bmp)
                classifyAndFetch(bmp)
            } else {
                titleBar.text = if (isEnglish) "Oops, try again!" else "Ay, ulitin natin!"
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
        lastPrediction = null
        lastMaterial = null
        lastCategory = null
        renderPredictionOrIdle()
    }

    private fun initModels() {
        val mapped2 = loadModelMapped(MODEL2)
        model2Interpreter = Interpreter(mapped2, Interpreter.Options())
        model2Labels = assets.open(MODEL2_LABELS_FILE)
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }

        val mappedExtra = loadModelMapped(EXTRA_MODEL)
        extraInterpreter = Interpreter(mappedExtra, Interpreter.Options())
        extraLabels = assets.open(EXTRA_LABELS_FILE)
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
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

    private fun classifyAndFetch(bitmap: Bitmap) {
        titleBar.text = if (isEnglish) "Looking closely..." else "Tinitingnan ko mabuti..."
        infoTitle.text = if (isEnglish) "Analyzing" else "Sinusuri"
        infoText.text =
            if (isEnglish) "I’m checking what kind of waste this is."
            else "Tinitingnan ko kung anong uri ng basura ito."
        infoRightIcon.setImageResource(R.drawable.ic_green_bin)

        cameraExecutor.execute {
            val prediction = classifyBitmap(bitmap)
            runOnUiThread {
                if (prediction == null) {
                    showUnknown()
                } else {
                    fetchMaterialAndCategory(prediction)
                }
            }
        }
    }

    private fun classifyBitmap(bitmap: Bitmap): PredictedItem? {
        val pred2 = classifyWithModel(bitmap, model2Interpreter, model2Labels)
        val res2 = pred2?.let { chooseFinalLabel(it) }

        val predExtra = classifyWithModel(bitmap, extraInterpreter, extraLabels)
        val resExtra = predExtra?.let { chooseFinalLabelSimple(it) }

        var bestPair: Pair<String, Float>? = null
        if (res2 != null) bestPair = res2
        if (resExtra != null && (bestPair == null || resExtra.second > bestPair!!.second)) {
            bestPair = resExtra
        }

        val chosen = bestPair ?: return null
        return PredictedItem(chosen.first, chosen.second)
    }

    private fun showUnknown() {
        lastPrediction = null
        lastMaterial = null
        lastCategory = null
        if (isEnglish) {
            titleBar.text = "I'm not sure"
            infoTitle.text = "Unknown item"
            infoText.text = "Try another photo or choose from gallery."
        } else {
            titleBar.text = "Hindi ako sigurado"
            infoTitle.text = "Hindi kilala"
            infoText.text = "Subukan ang ibang larawan o pumili sa gallery."
        }
        infoRightIcon.setImageResource(R.drawable.ic_green_bin)
    }

    private fun renderPredictionOrIdle() {
        val pred = lastPrediction
        if (pred == null) {
            if (isEnglish) {
                titleBar.text = "Camera Ready"
                infoTitle.text = "Scan a trash item"
                infoText.text = "Point the camera at a waste item or pick a photo from gallery."
            } else {
                titleBar.text = "Handa ang Camera"
                infoTitle.text = "I-scan ang basura"
                infoText.text = "Itutok ang camera sa basura o pumili ng larawan mula sa gallery."
            }
            infoRightIcon.setImageResource(R.drawable.ic_green_bin)
            return
        }

        val material = lastMaterial
        val category = lastCategory

        val materialNameEn: String
        val materialNameTl: String
        val categoryId: Int
        val isNonBio: Boolean
        val categoryEn: String
        val categoryTl: String

        if (material != null) {
            materialNameEn = material.nameEn?.takeIf { it.isNotBlank() } ?: pred.label
            materialNameTl = material.nameTl?.takeIf { it.isNotBlank() } ?: materialNameEn

            categoryId = material.categoryId
            isNonBio = (categoryId == 2)

            categoryEn = category?.name ?: if (isNonBio) "Non-biodegradable" else "Biodegradable"
            categoryTl = when (categoryId) {
                1 -> "Nabubulok"
                2 -> "Di-nabubulok"
                else -> if (isNonBio) "Di-nabubulok" else "Nabubulok"
            }
        } else {
            materialNameEn = pred.label
            materialNameTl = pred.label

            val guessedCategoryId = mapToCategoryId(pred.label) ?: 0
            categoryId = guessedCategoryId
            isNonBio = (categoryId == 2)

            categoryEn = if (isNonBio) "Non-biodegradable" else "Biodegradable"
            categoryTl = if (isNonBio) "Di-nabubulok" else "Nabubulok"
        }

        val binRes = if (isNonBio) R.drawable.ic_blue_bin else R.drawable.ic_green_bin

        if (isEnglish) {
            titleBar.text = materialNameEn
            infoTitle.text = categoryEn
            val binSentence = if (isNonBio) {
                "Throw it in the BLUE bin."
            } else {
                "Throw it in the GREEN bin."
            }
            infoText.text = "This looks like: $materialNameEn\n$binSentence"
        } else {
            titleBar.text = materialNameTl
            infoTitle.text = categoryTl
            val binSentence = if (isNonBio) {
                "Itapon ito sa ASUL na basurahan."
            } else {
                "Itapon ito sa BERDENG basurahan."
            }
            infoText.text = "Ito ay: $materialNameTl\n$binSentence"
        }

        val imageName = material?.image ?: ""
        val resId =
            if (imageName.isNotEmpty()) resources.getIdentifier(
                imageName,
                "drawable",
                packageName
            ) else 0

        if (resId != 0) {
            infoRightIcon.setImageResource(resId)
        } else {
            infoRightIcon.setImageResource(binRes)
        }
    }

    private fun fetchMaterialAndCategory(prediction: PredictedItem) {
        lastPrediction = prediction
        lastMaterial = null
        lastCategory = null

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(applicationContext)

            val candidates = mapLabelToMaterialCandidates(prediction.label)
            var material: WasteMaterial? = null

            for (c in candidates) {
                material = db.recycLensDao().getMaterialByName(c)
                if (material != null) break
            }

            val category = material?.let {
                db.recycLensDao().getCategoryById(it.categoryId)
            }

            lastMaterial = material
            lastCategory = category

            withContext(Dispatchers.Main) {
                renderPredictionOrIdle()
            }
        }
    }

    private fun mapLabelToMaterialCandidates(rawLabel: String): List<String> {
        val label = rawLabel.trim()
        val lower = label.lowercase()
        val list = mutableListOf<String>()

        if (label.isNotEmpty()) {
            list.add(label)
            list.add(lower)
            list.add(label.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() })
        }

        when {
            lower.contains("banana") -> {
                list.add("Banana Peel")
                list.add("Banana peels")
            }
            lower.contains("apple") -> {
                list.add("Apple Core")
                list.add("Apple")
            }
            lower.contains("mango") -> {
                list.add("Mango Peel")
                list.add("Mango")
            }
            lower.contains("plastic cup") -> list.add("Plastic Cup")
            lower.contains("bottle") -> list.add("Plastic Bottle")
            lower.contains("tissue") -> {
                list.add("Tissue")
                list.add("Tissue Roll")
            }
            lower.contains("grass") -> {
                list.add("Grass")
                list.add("Bermuda Grass")
            }
            lower.contains("leaf") || lower.contains("leaves") -> list.add("Leaves")
            lower.contains("styro") || lower.contains("tray") -> {
                list.add("Styrofoam Tray")
                list.add("Styrofoam Box")
            }
            lower.contains("ampalaya") -> list.add("Ampalaya")
            lower.contains("kangkong") -> list.add("Kangkong")
            lower.contains("okra") -> list.add("Okra")
            lower.contains("eggplant") -> {
                list.add("Eggplant")
                list.add("Talong")
            }
        }

        return list.distinct()
    }

    private data class PredictedItem(
        val label: String,
        val confidence: Float
    )

    private data class Top2Prediction(
        val bestLabel: String,
        val bestScore: Float,
        val secondLabel: String?,
        val secondScore: Float?
    )

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
                secondIdx = bestIdx
                secondScore = bestScore
                bestScore = s
                bestIdx = i
            } else if (s > secondScore) {
                secondIdx = i
                secondScore = s
            }
        }

        val bestLabel = labels.getOrNull(bestIdx) ?: return null
        val secondLabel = if (secondIdx >= 0) labels.getOrNull(secondIdx) else null
        val secondScoreFinal = if (secondIdx >= 0) secondScore else null

        return Top2Prediction(bestLabel, bestScore, secondLabel, secondScoreFinal)
    }

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

    private fun chooseFinalLabelSimple(pred: Top2Prediction): Pair<String, Float>? {
        if (pred.bestScore < THRESHOLD) return null
        return pred.bestLabel to pred.bestScore
    }

    private fun mapToCategoryId(rawLabel: String): Int? {
        val label = rawLabel.trim().lowercase()
        return when {
            label.contains("fruit") || label.contains("prutas") -> 1
            label.contains("vegetable") || label.contains("gulay") -> 1
            label.contains("paper") || label.contains("papel") -> 1
            label.contains("leaf") || label.contains("leaves") || label.contains("dahon") -> 1
            label.contains("grass") -> 1
            label.contains("tissue") || label.contains("tisyu") -> 1
            label.contains("ampalaya") || label.contains("kangkong") || label.contains("okra") || label.contains("eggplant") -> 1
            label.contains("apple") || label.contains("banana") || label.contains("mango") -> 1
            label.contains("bottle") -> 2
            label.contains("plastic") || label.contains("plastik") -> 2
            label.contains("styro") || label.contains("styrofoam") || label.contains("tray") -> 2
            label.contains("wrapper") -> 2
            label.contains("can") || label.contains("lata") -> 2
            else -> null
        }
    }

    private fun decodeBitmapFromUriSafely(
        resolver: ContentResolver,
        uri: Uri,
        maxDim: Int = 1600
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val first = resolver.openInputStream(uri) ?: return null
        first.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            val raw = resolver.openInputStream(uri) ?: return null
            raw.use {
                return BitmapFactory.decodeStream(it)
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

        val input2 = resolver.openInputStream(uri) ?: return null
        input2.use {
            return BitmapFactory.decodeStream(it, null, opts)
        }
    }
}
