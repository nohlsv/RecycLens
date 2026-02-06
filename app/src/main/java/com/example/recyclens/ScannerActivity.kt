package com.example.recyclens

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
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
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.example.recyclens.data.db.AppDatabase
import com.example.recyclens.data.model.WasteCategory
import com.example.recyclens.data.model.WasteMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.ByteArrayOutputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
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

    private var tts: TextToSpeech? = null
    private var ttsReady: Boolean = false
    private var speakTextEn: String? = null
    private var speakTextTl: String? = null
    private var wasMusicPlayingBeforeTts: Boolean = false

    companion object {
        private const val MODEL2 = "model2.tflite"
        private const val MODEL2_LABELS_FILE = "model2_labels.txt"
        private const val EXTRA_MODEL = "model.tflite"
        private const val EXTRA_LABELS_FILE = "model_labels.txt"
        private const val INPUT_SIZE = 224
        private const val THRESHOLD = 0.50f
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

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                updateTtsLanguage()
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (wasMusicPlayingBeforeTts && mediaPlayer != null) {
                            runOnUiThread {
                                if (mediaPlayer?.isPlaying == false) {
                                    mediaPlayer?.start()
                                }
                            }
                        }
                    }

                    override fun onError(utteranceId: String?) {
                        if (wasMusicPlayingBeforeTts && mediaPlayer != null) {
                            runOnUiThread {
                                if (mediaPlayer?.isPlaying == false) {
                                    mediaPlayer?.start()
                                }
                            }
                        }
                    }
                })
            }
        }

        initModels()
        ensureCameraReady()
        setupBottomBar(BottomBar.Tab.PLAY)
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
        tts?.stop()
        tts?.shutdown()
        tts = null
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
            val text = if (isEnglish) speakTextEn else speakTextTl
            if (!ttsReady || tts == null) {
                Toast.makeText(
                    this,
                    if (isEnglish) "Voice guide is not ready yet." else "Hindi pa handa ang voice guide.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            if (text.isNullOrBlank()) {
                Toast.makeText(
                    this,
                    if (isEnglish) "Scan a trash item first." else "Mag-scan muna ng basura.",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            wasMusicPlayingBeforeTts = mediaPlayer?.isPlaying == true
            if (wasMusicPlayingBeforeTts) {
                mediaPlayer?.pause()
            }

            updateTtsLanguage()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "SCAN_TTS")
        }
    }

    private fun setupLanguageToggle() {
        updateLanguageTexts()
        langToggle.setOnClickListener {
            isEnglish = !isEnglish
            updateLanguageTexts()
            if (ttsReady) {
                updateTtsLanguage()
            }
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

    private fun updateTtsLanguage() {
        val engine = tts ?: return

        val locale = if (isEnglish) {
            Locale.US
        } else {
            Locale.forLanguageTag("fil-PH")
        }

        var result = engine.setLanguage(locale)

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            if (!isEnglish) {
                val tlLocale = Locale.forLanguageTag("tl-PH")
                result = engine.setLanguage(tlLocale)
            } else {
                result = engine.setLanguage(Locale.US)
            }
        }

        if (!isEnglish) {
            val voices = engine.voices
            val tagalogVoice = voices?.firstOrNull { v ->
                val lang = v.locale.language.lowercase()
                lang == "fil" || lang == "tl"
            }
            if (tagalogVoice != null) {
                engine.voice = tagalogVoice
            }
        }

        engine.setSpeechRate(1.0f)
        engine.setPitch(1.0f)
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
        val capture = imageCapture
        if (capture == null) {
            titleBar.text = if (isEnglish) "Oops, try again!" else "Ay, ulitin natin!"
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        val rotation = image.imageInfo.rotationDegrees
                        val bitmap = imageProxyToBitmap(image)
                        image.close()
                        if (bitmap == null) {
                            runOnUiThread {
                                titleBar.text = if (isEnglish) "Oops, try again!" else "Ay, ulitin natin!"
                                Toast.makeText(this@ScannerActivity, "Unable to capture picture", Toast.LENGTH_SHORT).show()
                            }
                            return
                        }
                        val rotated = rotateBitmapIfNeeded(bitmap, rotation)
                        Log.d("RECYC_LENS_ML", "Captured bitmap ${rotated.width}x${rotated.height}, rotation=$rotation")
                        runOnUiThread {
                            showCapturedPhoto(rotated)
                            classifyAndFetch(rotated)
                        }
                    } catch (e: Exception) {
                        image.close()
                        runOnUiThread {
                            titleBar.text = if (isEnglish) "Oops, try again!" else "Ay, ulitin natin!"
                            Toast.makeText(this@ScannerActivity, "Unable to capture picture", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        titleBar.text = if (isEnglish) "Oops, try again!" else "Ay, ulitin natin!"
                        Toast.makeText(this@ScannerActivity, "Unable to capture picture", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
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
        speakTextEn = null
        speakTextTl = null
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
        speakTextEn = null
        speakTextTl = null

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
        val validPred2 = pred2?.takeIf { it.bestScore >= THRESHOLD }
        val res2 = validPred2?.let { chooseFinalLabel(it) }

        val predExtra = classifyWithModel(bitmap, extraInterpreter, extraLabels)
        val validExtra = predExtra?.takeIf { it.bestScore >= THRESHOLD }
        val resExtra = validExtra?.let { chooseFinalLabelSimple(it) }

        if (pred2 != null) {
            Log.d(
                "RECYC_LENS_ML",
                "model2 top1=${pred2.bestLabel} ${pred2.bestScore}, top2=${pred2.secondLabel ?: "-"} ${pred2.secondScore ?: 0f}, pass=${validPred2 != null}"
            )
        }
        if (predExtra != null) {
            Log.d(
                "RECYC_LENS_ML",
                "model top1=${predExtra.bestLabel} ${predExtra.bestScore}, top2=${predExtra.secondLabel ?: "-"} ${predExtra.secondScore ?: 0f}, pass=${validExtra != null}"
            )
        }

        var bestPair: Pair<String, Float>? = null
        if (res2 != null) bestPair = res2
        if (resExtra != null && (bestPair == null || resExtra.second > bestPair!!.second)) {
            bestPair = resExtra
        }

        val chosen = bestPair ?: run {
            Log.d("RECYC_LENS_ML", "Rejected both models below threshold")
            return null
        }
        Log.d("RECYC_LENS_ML", "Chosen label=${chosen.first} score=${chosen.second}")
        return PredictedItem(chosen.first, chosen.second)
    }

    private fun showUnknown() {
        lastPrediction = null
        lastMaterial = null
        lastCategory = null
        speakTextEn = null
        speakTextTl = null
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

    private fun getTagalogMaterialName(englishName: String): String? {
        val lower = englishName.lowercase()
        return when {
            lower.contains("banana") -> "Balat ng saging"
            lower.contains("apple core") || lower.contains("apple") -> "Ubod ng mansanas"
            lower.contains("mango peel") || lower.contains("mango") -> "Balat ng mangga"
            lower.contains("plastic bottle") || (lower.contains("bottle") && lower.contains("plastic")) -> "Plastic na bote"
            lower.contains("plastic cup") -> "Plastic na baso"
            lower.contains("tissue") -> "Tisyu"
            lower.contains("grass") -> "Damo"
            lower.contains("leaf") || lower.contains("leaves") -> "Dahon"
            lower.contains("styrofoam") || lower.contains("styro") || lower.contains("tray") -> "Styrofoam na lalagyan"
            lower.contains("wrapper") -> "Balot ng kendi"
            lower.contains("can") || lower.contains("lata") -> "Lata"
            else -> null
        }
    }

    private fun renderPredictionOrIdle() {
        val pred = lastPrediction
        if (pred == null) {
            speakTextEn = null
            speakTextTl = null
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
        val categoryNameEn: String
        val categoryNameTl: String
        val categoryDescEn: String
        val categoryDescTl: String

        if (material != null) {
            materialNameEn = material.nameEn?.takeIf { it.isNotBlank() } ?: pred.label
            materialNameTl =
                material.nameTl?.takeIf { it.isNotBlank() }
                    ?: getTagalogMaterialName(materialNameEn)
                            ?: materialNameEn

            categoryId = material.categoryId
            isNonBio = (categoryId == 2)

            categoryNameEn =
                category?.nameEn ?: if (isNonBio) "Non-biodegradable" else "Biodegradable"
            categoryNameTl =
                category?.nameTl ?: if (isNonBio) "Di-Nabubulok" else "Nabubulok"

            categoryDescEn =
                category?.descriptionEn ?: if (isNonBio) "Throw in the Blue Bin." else "Throw in the Green Bin."
            categoryDescTl =
                category?.descriptionTl ?: if (isNonBio) "Itapon sa Blue Bin." else "Itapon sa Green Bin."
        } else {
            materialNameEn = pred.label
            materialNameTl = getTagalogMaterialName(pred.label) ?: pred.label

            val guessedCategoryId = mapToCategoryId(pred.label) ?: 0
            categoryId = guessedCategoryId
            isNonBio = (categoryId == 2)

            categoryNameEn = if (isNonBio) "Non-biodegradable" else "Biodegradable"
            categoryNameTl = if (isNonBio) "Di-Nabubulok" else "Nabubulok"

            categoryDescEn =
                if (isNonBio) "Throw in the Blue Bin." else "Throw in the Green Bin."
            categoryDescTl =
                if (isNonBio) "Itapon sa Blue Bin." else "Itapon sa Green Bin."
        }

        val binRes = if (isNonBio) R.drawable.ic_blue_bin else R.drawable.ic_green_bin

        speakTextEn = "$materialNameEn. $categoryNameEn. $categoryDescEn"
        speakTextTl = "$materialNameTl. $categoryNameTl. $categoryDescTl"

        if (isEnglish) {
            titleBar.text = materialNameEn
            infoTitle.text = categoryNameEn
            infoText.text = "$materialNameEn\n$categoryDescEn"
        } else {
            titleBar.text = materialNameTl
            infoTitle.text = categoryNameTl
            infoText.text = "$materialNameTl\n$categoryDescTl"
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
            try {
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
            } catch (e: Exception) {
                lastMaterial = null
                lastCategory = null
            }

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

        when (lower) {
            "snack wrapper" -> list.add("Candy Wrapper")
            "leaves" -> list.add("Leaf")
            "leaf" -> list.add("Leaf")
            "bottle" -> {
                list.add("Bottle")
                list.add("Plastic Bottle")
            }
            "tissue roll" -> list.add("Tissue")
            "styrofoam tray" -> list.add("Styrofoam Box")
            "apple", "mango", "banana", "orange" -> {
                list.add("Fruit")
                if (lower == "banana") list.add("Banana Peel")
                if (lower == "mango") list.add("Mango Peel")
                if (lower == "apple") list.add("Apple Core")
            }
            "pet bottle" -> {
                list.add("Bottle")
                list.add("Plastic Bottle")
            }
            "styrofoam cup" -> {
                list.add("Styrofoam Box")
                list.add("Styrofoam Tray")
            }
            "tissue core" -> list.add("Tissue")
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
            lower.contains("orange") -> list.add("Fruit")
            lower.contains("pet bottle") -> {
                list.add("Bottle")
                list.add("Plastic Bottle")
            }
            lower.contains("styrofoam cup") -> {
                list.add("Styrofoam Box")
                list.add("Styrofoam Tray")
            }
            lower.contains("tissue core") -> list.add("Tissue")
        }

        // Generic vegetable fallback to existing DB entry
        if (lower in listOf("ampalaya", "okra", "eggplant", "kangkong")) {
            list.add("Fruit")
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
        Log.d("RECYC_LENS_ML", "Input to model: ${resized.width}x${resized.height}")

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

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
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
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    private fun rotateBitmapIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return bitmap
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun chooseFinalLabel(pred: Top2Prediction): Pair<String, Float>? {
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
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

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
            val bmp = BitmapFactory.decodeStream(it, null, opts) ?: return null
            return applyExifOrientation(bmp, orientation)
        }
    }
}
