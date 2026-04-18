package com.example.recyclens

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.drawable.BitmapDrawable
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
import android.widget.ArrayAdapter
import android.view.LayoutInflater
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
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
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class ScannerActivity : AppCompatActivity() {

    private lateinit var topCard: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var titleBar: TextView
    private lateinit var infoTitle: TextView
    private lateinit var infoText: TextView
    private lateinit var infoRightIcon: ImageView
    private lateinit var btnCamera: ImageButton
    private lateinit var btnGallery: ImageButton
    private lateinit var btnPreset: ImageButton
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
    private lateinit var feedbackBanner: RecyclensFeedbackBanner

    private data class PresetSample(
        val label: String,
        val drawableRes: Int,
        val nameEn: String,
        val nameTl: String
    )

    private val presetSamples = listOf(
        PresetSample("banana", R.drawable.ic_trash_banana, "Banana Peel", "Balat ng saging"),
        PresetSample("pet-bottle", R.drawable.ic_trash_bottle, "Plastic Bottle", "Plastic na bote"),
        PresetSample("apple", R.drawable.ic_trash_fruit, "Apple Core", "Ubod ng mansanas"),
        PresetSample("grass", R.drawable.ic_trash_grass, "Grass", "Damo"),
        PresetSample("leaves", R.drawable.ic_trash_leaf, "Leaves", "Dahon"),
        PresetSample("paper", R.drawable.ic_trash_paper, "Stationery Paper", "Papel pang-sulat"),
        PresetSample("plastic-cup", R.drawable.ic_trash_plastic_cup, "Plastic Cup", "Plastic na baso"),
        PresetSample("styrofoam", R.drawable.ic_trash_styro, "Styrofoam Tray", "Styrofoam na lalagyan"),
        PresetSample("tissue", R.drawable.ic_trash_tissue, "Tissue", "Tisyu"),
        PresetSample("plastic-wrapper", R.drawable.ic_trash_wrapper, "Snack Wrapper", "Balot ng meryenda")
    )

    companion object {
        private const val MODEL2 = "best_float32.tflite"
        private const val MODEL2_LABELS_FILE = "best_int8_labels.txt"
        // private const val MODEL2 = "model2.tflite"
        // private const val MODEL2_LABELS_FILE = "model2_labels.txt"
        private const val EXTRA_MODEL = "model.tflite"
        private const val EXTRA_LABELS_FILE = "model_labels.txt"
        private const val INPUT_SIZE = 224
        private const val THRESHOLD = 0.40f  // Lowered from 0.50f for better detection
        private const val CUP_BOTTLE_MARGIN = 0.15f  // Increased margin for better cup/bottle differentiation
    }

    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                val msg = if (isEnglish)
                    getString(R.string.scanner_camera_permission_required)
                else
                    getString(R.string.scanner_camera_permission_required_tl)
                feedbackBanner.show(msg, RecyclensFeedbackBanner.Style.ERROR)
            }
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) {
                Log.w("RECYC_LENS_ML", "Gallery picker returned null URI")
                return@registerForActivityResult
            }
            val bmp = decodeBitmapFromUriSafely(contentResolver, uri, maxDim = 1600)
            if (bmp != null) {
                showCapturedPhoto(bmp)
                classifyAndFetch(bmp)
            } else {
                val msg = if (isEnglish) "Could not open image" else "Hindi mabuksan ang larawan"
                feedbackBanner.show(msg, RecyclensFeedbackBanner.Style.ERROR)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scanner_page)
        BackgroundDriftHelper.attach(this)
        feedbackBanner = RecyclensFeedbackBanner(this)
        isEnglish = LanguagePrefs.isEnglish(this)

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
        btnPreset = findViewById(R.id.btnPreset)
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
        BackgroundDriftHelper.detach(this)
        cameraExecutor.shutdown()
        model2Interpreter.close()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        feedbackBanner.release()
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

        btnPreset.setOnClickListener { showPresetPicker() }

        btnSpeaker.setOnClickListener {
            val text = if (isEnglish) speakTextEn else speakTextTl
            if (!ttsReady || tts == null) {
                feedbackBanner.show(
                    if (isEnglish) "Voice guide is not ready yet." else "Hindi pa handa ang voice guide.",
                    RecyclensFeedbackBanner.Style.INFO
                )
                return@setOnClickListener
            }
            if (text.isNullOrBlank()) {
                feedbackBanner.show(
                    if (isEnglish) "Scan a trash item first." else "Mag-scan muna ng basura.",
                    RecyclensFeedbackBanner.Style.WARNING
                )
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
            LanguagePrefs.setEnglish(this, isEnglish)
            updateLanguageTexts()
            if (ttsReady) {
                updateTtsLanguage()
            }
            renderPredictionOrIdle()
        }
    }

    private fun updateLanguageTexts() {
        if (isEnglish) {
            langText.text = getString(R.string.label_en)
            labelScan.text = getString(R.string.label_scan_trash)
            labelPlay.text = getString(R.string.label_play_games)
        } else {
            langText.text = getString(R.string.label_tl)
            labelScan.text = getString(R.string.scanner_i_scan_ang_basura)
            labelPlay.text = getString(R.string.label_play_games)
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
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)  // Changed for better image quality
                .setTargetRotation(windowManager.defaultDisplay.rotation)
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
        titleBar.text = if (isEnglish) getString(R.string.scanner_taking_picture) else getString(R.string.scanner_kumukuha_ng_larawan)
        val capture = imageCapture
        if (capture == null) {
            titleBar.text = if (isEnglish) getString(R.string.scanner_opps_try_again) else getString(R.string.scanner_ay_ulitin)
            feedbackBanner.show(getString(R.string.scanner_camera_not_ready), RecyclensFeedbackBanner.Style.ERROR)
            return
        }
        val photoFile = try {
            File.createTempFile("recyclens_capture_", ".jpg", cacheDir)
        } catch (e: Exception) {
            Log.e("RECYC_LENS_ML", "Failed to create temp file for capture", e)
            feedbackBanner.show(getString(R.string.scanner_unable_capture), RecyclensFeedbackBanner.Style.ERROR)
            return
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    try {
                        val bitmap = decodeBitmapFromFileSafely(photoFile, maxDim = 1600)
                        if (bitmap == null) {
                            runOnUiThread {
                                titleBar.text = if (isEnglish) "Oops, try again!" else "Ay, ulitin natin!"
                                val msg = if (isEnglish) getString(R.string.scanner_unable_capture) else getString(R.string.scanner_hindi_makuha)
                                feedbackBanner.show(msg, RecyclensFeedbackBanner.Style.ERROR)
                            }
                            return
                        }
                        Log.d("RECYC_LENS_ML", "Captured bitmap ${bitmap.width}x${bitmap.height} from file")
                        runOnUiThread {
                            showCapturedPhoto(bitmap)
                            classifyAndFetch(bitmap)
                        }
                    } catch (e: Exception) {
                        Log.e("RECYC_LENS_ML", "Capture decode failed", e)
                        runOnUiThread {
                            titleBar.text = if (isEnglish) "Oops, try again!" else "Ay, ulitin natin!"
                            val msg = if (isEnglish) getString(R.string.scanner_unable_capture) else getString(R.string.scanner_hindi_makuha)
                            feedbackBanner.show(msg, RecyclensFeedbackBanner.Style.ERROR)
                        }
                    } finally {
                        try {
                            if (photoFile.exists()) photoFile.delete()
                        } catch (_: Exception) {
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("RECYC_LENS_ML", "Capture error", exception)
                    runOnUiThread {
                        titleBar.text = if (isEnglish) "Oops, try again!" else "Ay, ulitin natin!"
                        val msg = if (isEnglish) getString(R.string.scanner_unable_capture) else getString(R.string.scanner_hindi_makuha)
                        feedbackBanner.show(msg, RecyclensFeedbackBanner.Style.ERROR)
                    }
                    try {
                        if (photoFile.exists()) photoFile.delete()
                    } catch (_: Exception) {
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

    private fun showPresetPicker() {
        val labels = presetSamples.map { sample ->
            if (isEnglish) sample.nameEn else sample.nameTl
        }

        val adapter = object : ArrayAdapter<String>(
            this,
            R.layout.dialog_preset_sample_item,
            labels
        ) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = convertView ?: LayoutInflater.from(context)
                    .inflate(R.layout.dialog_preset_sample_item, parent, false)
                val icon = view.findViewById<ImageView>(R.id.presetItemIcon)
                val label = view.findViewById<TextView>(R.id.presetItemLabel)
                icon.setImageResource(presetSamples[position].drawableRes)
                label.text = labels[position]
                return view
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (isEnglish) getString(R.string.dialog_choose_sample_title) else getString(R.string.dialog_choose_sample_title_tl))
            .setAdapter(adapter) { _, which ->
                applyPresetSample(presetSamples[which])
            }
            .setNegativeButton(if (isEnglish) getString(R.string.action_cancel) else getString(R.string.action_cancel), null)
            .show()
    }

    private fun applyPresetSample(sample: PresetSample) {
        val bitmap = drawableToBitmap(sample.drawableRes)
        if (bitmap == null) {
            val msg = if (isEnglish) getString(R.string.scanner_load_sample_fail) else getString(R.string.scanner_load_sample_fail_tl)
            feedbackBanner.show(msg, RecyclensFeedbackBanner.Style.ERROR)
            return
        }

        showCapturedPhoto(bitmap)
        titleBar.text = if (isEnglish) getString(R.string.scanner_using_sample) else getString(R.string.scanner_gumagamit_sample)
        lastPrediction = PredictedItem(sample.label, 1f)
        fetchMaterialAndCategory(PredictedItem(sample.label, 1f))
    }

    private fun drawableToBitmap(drawableRes: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(this, drawableRes) ?: return null
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }

        val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else INPUT_SIZE
        val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else INPUT_SIZE
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun initModels() {
        val mapped2 = loadModelMapped(MODEL2)
        model2Interpreter = Interpreter(mapped2, Interpreter.Options())
        model2Labels = assets.open(MODEL2_LABELS_FILE)
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }
        logInterpreterInfo("MODEL2", model2Interpreter, model2Labels)
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
        titleBar.text = if (isEnglish) getString(R.string.scanner_looking_closely) else getString(R.string.scanner_tinitingnan_mabuti)
        infoTitle.text = if (isEnglish) getString(R.string.scanner_analyzing) else getString(R.string.scanner_sinusuri)
        infoText.text =
            if (isEnglish) getString(R.string.scanner_checking_waste)
            else getString(R.string.scanner_checking_waste_tl)
        infoRightIcon.setImageResource(R.drawable.ic_green_bin)
        speakTextEn = null
        speakTextTl = null

        cameraExecutor.execute {
            val prediction = try {
                classifyBitmap(bitmap)
            } catch (e: Exception) {
                Log.e("RECYC_LENS_ML", "Classification crashed", e)
                null
            }
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

        if (pred2 != null) {
            Log.d(
                "RECYC_LENS_ML",
                "model2 top1=${pred2.bestLabel} ${pred2.bestScore}, top2=${pred2.secondLabel ?: "-"} ${pred2.secondScore ?: 0f}"
            )
        }

        val chosen = when {
            res2 != null -> res2
            else -> {
                Log.d("RECYC_LENS_ML", "No prediction returned by MODEL2")
                return null
            }
        }
        if (chosen.second < THRESHOLD) {
            Log.d("RECYC_LENS_ML", "Prediction below threshold: ${chosen.second} < $THRESHOLD")
            return null
        }
        Log.d("RECYC_LENS_ML", "Chosen label=${chosen.first} score=${chosen.second}")
        return PredictedItem(chosen.first, chosen.second)
    }

    private fun logInterpreterInfo(tagName: String, interpreter: Interpreter, labels: List<String>) {
        try {
            val inTensor = interpreter.getInputTensor(0)
            Log.d(
                "RECYC_LENS_ML",
                "$tagName input: shape=${inTensor.shape().joinToString(prefix = "[", postfix = "]")}, type=${inTensor.dataType()}, qScale=${inTensor.quantizationParams().scale}, qZero=${inTensor.quantizationParams().zeroPoint}"
            )
            for (i in 0 until interpreter.outputTensorCount) {
                val out = interpreter.getOutputTensor(i)
                Log.d(
                    "RECYC_LENS_ML",
                    "$tagName output[$i]: shape=${out.shape().joinToString(prefix = "[", postfix = "]")}, type=${out.dataType()}, qScale=${out.quantizationParams().scale}, qZero=${out.quantizationParams().zeroPoint}"
                )
            }
            Log.d("RECYC_LENS_ML", "$tagName labels count=${labels.size}, labels=${labels.joinToString()}")
        } catch (e: Exception) {
            Log.e("RECYC_LENS_ML", "Failed to log interpreter info for $tagName", e)
        }
    }

    private fun detectWithModel(
        bitmap: Bitmap,
        interpreter: Interpreter,
        labels: List<String>
    ): Top2Prediction? {
        if (labels.isEmpty()) return null

        val square = cropCenterSquare(bitmap)
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        val inH = inputShape.getOrNull(1) ?: INPUT_SIZE
        val inW = inputShape.getOrNull(2) ?: INPUT_SIZE
        val channels = inputShape.getOrNull(3) ?: 3
        if (channels != 3) return null

        val modelInput = Bitmap.createScaledBitmap(square, inW, inH, true)
        val inputType = inputTensor.dataType().toString()
        val bytesPerChannel = if (inputType == "FLOAT32") 4 else 1
        val inputBuffer = ByteBuffer.allocateDirect(inH * inW * 3 * bytesPerChannel)
            .order(ByteOrder.nativeOrder())
        val inputQ = inputTensor.quantizationParams()
        val inScale = if (inputQ.scale == 0f) 1f else inputQ.scale
        val inZero = inputQ.zeroPoint

        for (y in 0 until inH) {
            for (x in 0 until inW) {
                val px = modelInput.getPixel(x, y)
                val r = Color.red(px) / 255f
                val g = Color.green(px) / 255f
                val b = Color.blue(px) / 255f
                if (inputType == "FLOAT32") {
                    inputBuffer.putFloat(r)
                    inputBuffer.putFloat(g)
                    inputBuffer.putFloat(b)
                } else {
                    val qr = (r / inScale + inZero).roundToInt()
                    val qg = (g / inScale + inZero).roundToInt()
                    val qb = (b / inScale + inZero).roundToInt()
                    if (inputType == "INT8") {
                        inputBuffer.put(qr.coerceIn(-128, 127).toByte())
                        inputBuffer.put(qg.coerceIn(-128, 127).toByte())
                        inputBuffer.put(qb.coerceIn(-128, 127).toByte())
                    } else {
                        inputBuffer.put(qr.coerceIn(0, 255).toByte())
                        inputBuffer.put(qg.coerceIn(0, 255).toByte())
                        inputBuffer.put(qb.coerceIn(0, 255).toByte())
                    }
                }
            }
        }
        inputBuffer.rewind()

        val outputs = HashMap<Int, Any>()
        val outputBuffers = HashMap<Int, ByteBuffer>()
        for (i in 0 until interpreter.outputTensorCount) {
            val tensor = interpreter.getOutputTensor(i)
            val count = numElements(tensor.shape())
            val bytesPerElement = when (tensor.dataType().toString()) {
                "FLOAT32", "INT32" -> 4
                "INT64" -> 8
                else -> 1
            }
            val buffer = ByteBuffer.allocateDirect(count * bytesPerElement)
                .order(ByteOrder.nativeOrder())
            outputs[i] = buffer
            outputBuffers[i] = buffer
        }

        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)

        val outValues = HashMap<Int, FloatArray>()
        for (i in 0 until interpreter.outputTensorCount) {
            val tensor = interpreter.getOutputTensor(i)
            val buffer = outputBuffers[i] ?: continue
            outValues[i] = dequantizeTensorToFloatArray(buffer, tensor)
            Log.d(
                "RECYC_LENS_ML",
                "detect output[$i] shape=${tensor.shape().joinToString(prefix = "[", postfix = "]")} type=${tensor.dataType()}"
            )
            val arr = outValues[i] ?: FloatArray(0)
            val sample = arr.take(12).joinToString { String.format("%.4f", it) }
            Log.d("RECYC_LENS_ML", "detect output[$i] sample(first ${minOf(12, arr.size)}): $sample")
        }

        parseBoxesClassesScores(interpreter, outValues, labels)?.let { return it }
        parseYoloLikeSingleOutput(interpreter, outValues, labels)?.let { return it }

        Log.w("RECYC_LENS_ML", "Detector output format not recognized")
        return null
    }

    private fun parseBoxesClassesScores(
        interpreter: Interpreter,
        outValues: Map<Int, FloatArray>,
        labels: List<String>
    ): Top2Prediction? {
        var boxesIdx: Int? = null
        var classesIdx: Int? = null
        var scoresIdx: Int? = null

        for (i in 0 until interpreter.outputTensorCount) {
            val shape = interpreter.getOutputTensor(i).shape()
            if (shape.size == 3 && shape.getOrNull(2) == 4) boxesIdx = i
            if (shape.size >= 2 && (shape.lastOrNull() == 1 || shape.size == 2)) {
                if (classesIdx == null) classesIdx = i else if (scoresIdx == null) scoresIdx = i
            }
        }

        if (boxesIdx == null || classesIdx == null || scoresIdx == null) return null

        val boxesShape = interpreter.getOutputTensor(boxesIdx).shape()
        val n = boxesShape.getOrNull(1) ?: return null
        val classArr = outValues[classesIdx] ?: return null
        val scoreArr = outValues[scoresIdx] ?: return null

        var bestLabel = ""
        var bestScore = Float.NEGATIVE_INFINITY
        var secondLabel: String? = null
        var secondScore = Float.NEGATIVE_INFINITY

        for (i in 0 until n) {
            val score = scoreArr.getOrElse(i) { Float.NEGATIVE_INFINITY }
            val cls = classArr.getOrElse(i) { -1f }.roundToInt()
            if (cls !in labels.indices) continue
            val label = labels[cls]

            if (score > bestScore) {
                secondScore = bestScore
                secondLabel = bestLabel.takeIf { it.isNotBlank() }
                bestScore = score
                bestLabel = label
            } else if (score > secondScore) {
                secondScore = score
                secondLabel = label
            }
        }

        if (bestLabel.isBlank()) return null
        return Top2Prediction(
            bestLabel = bestLabel,
            bestScore = bestScore,
            secondLabel = secondLabel,
            secondScore = secondScore.takeIf { it != Float.NEGATIVE_INFINITY }
        )
    }

    private fun parseYoloLikeSingleOutput(
        interpreter: Interpreter,
        outValues: Map<Int, FloatArray>,
        labels: List<String>
    ): Top2Prediction? {
        var chosenIdx = -1
        var attrs = -1
        var candidates = -1
        var channelFirst = false

        for (i in 0 until interpreter.outputTensorCount) {
            val shape = interpreter.getOutputTensor(i).shape()
            if (shape.size != 3) continue
            val a = shape[1]
            val b = shape[2]

            if (a >= labels.size + 4 && b > 10) {
                chosenIdx = i
                attrs = a
                candidates = b
                channelFirst = true
                break
            }
            if (b >= labels.size + 4 && a > 10) {
                chosenIdx = i
                attrs = b
                candidates = a
                channelFirst = false
                break
            }
        }

        if (chosenIdx < 0 || attrs <= 0 || candidates <= 0) return null
        val data = outValues[chosenIdx] ?: return null

        val classStart = if (attrs >= labels.size + 5) 5 else 4
        if (attrs <= classStart) return null
        val classCount = minOf(labels.size, attrs - classStart)
        if (classCount <= 0) return null

        fun valueAt(candidate: Int, attr: Int): Float {
            return if (channelFirst) {
                data[attr * candidates + candidate]
            } else {
                data[candidate * attrs + attr]
            }
        }

        var bestLabel = ""
        var bestScore = Float.NEGATIVE_INFINITY
        var secondLabel: String? = null
        var secondScore = Float.NEGATIVE_INFINITY

        for (c in 0 until candidates) {
            val obj = if (classStart == 5) valueAt(c, 4).coerceAtLeast(0f) else 1f
            var localBestCls = 0
            var localBestScore = Float.NEGATIVE_INFINITY
            for (k in 0 until classCount) {
                val s = valueAt(c, classStart + k)
                if (s > localBestScore) {
                    localBestScore = s
                    localBestCls = k
                }
            }

            val finalScore = if (classStart == 5) obj * localBestScore else localBestScore
            val label = labels[localBestCls]

            if (finalScore > bestScore) {
                secondScore = bestScore
                secondLabel = bestLabel.takeIf { it.isNotBlank() }
                bestScore = finalScore
                bestLabel = label
            } else if (finalScore > secondScore) {
                secondScore = finalScore
                secondLabel = label
            }
        }

        if (bestLabel.isBlank()) return null
        return Top2Prediction(
            bestLabel = bestLabel,
            bestScore = bestScore,
            secondLabel = secondLabel,
            secondScore = secondScore.takeIf { it != Float.NEGATIVE_INFINITY }
        )
    }

    private fun numElements(shape: IntArray): Int {
        if (shape.isEmpty()) return 0
        var n = 1
        for (d in shape) n *= d.coerceAtLeast(1)
        return n
    }

    private fun dequantizeTensorToFloatArray(buffer: ByteBuffer, tensor: org.tensorflow.lite.Tensor): FloatArray {
        val count = numElements(tensor.shape())
        val result = FloatArray(count)
        val dtype = tensor.dataType().toString()
        val q = tensor.quantizationParams()
        val scale = if (q.scale == 0f) 1f else q.scale
        val zero = q.zeroPoint

        buffer.rewind()
        when (dtype) {
            "FLOAT32" -> {
                for (i in 0 until count) result[i] = buffer.float
            }
            "INT8" -> {
                for (i in 0 until count) {
                    val v = buffer.get().toInt()
                    result[i] = (v - zero) * scale
                }
            }
            "UINT8" -> {
                for (i in 0 until count) {
                    val v = buffer.get().toInt() and 0xFF
                    result[i] = (v - zero) * scale
                }
            }
            "INT32" -> {
                for (i in 0 until count) {
                    val v = buffer.int
                    result[i] = (v - zero) * scale
                }
            }
            "INT64" -> {
                for (i in 0 until count) {
                    result[i] = buffer.long.toFloat()
                }
            }
            else -> {
                for (i in 0 until count) {
                    val v = buffer.get().toInt() and 0xFF
                    result[i] = (v - zero) * scale
                }
            }
        }
        return result
    }

    private fun showUnknown() {
        lastPrediction = null
        lastMaterial = null
        lastCategory = null
        speakTextEn = null
        speakTextTl = null
        if (isEnglish) {
            titleBar.text = getString(R.string.scanner_not_sure)
            infoTitle.text = getString(R.string.category_unknown)
            infoText.text = getString(R.string.scanner_try_another_desc)
        } else {
            titleBar.text = getString(R.string.scanner_hindi_sigurado)
            infoTitle.text = getString(R.string.category_unknown_tl)
            infoText.text = getString(R.string.scanner_try_another_desc_tl)
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
            lower.contains("pet bottle") -> "PET na bote"
            lower.contains("snack wrapper") -> "Balot ng meryenda"
            lower.contains("stationery paper") -> "Papel pang-sulat"
            lower.contains("tissue core") -> "Gitna ng tisyu"
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
                titleBar.text = getString(R.string.scanner_camera_ready)
                infoTitle.text = getString(R.string.scanner_scan_a_trash_item)
                infoText.text = getString(R.string.scanner_camera_ready_desc)
            } else {
                titleBar.text = getString(R.string.scanner_handa_ang_camera)
                infoTitle.text = getString(R.string.scanner_i_scan_ang_basura)
                infoText.text = getString(R.string.scanner_camera_ready_desc_tl)
            }
            infoRightIcon.setImageResource(R.drawable.ic_green_bin)
            return
        }

        val material = lastMaterial
        val category = lastCategory
        val displayLabel = humanReadableLabel(pred.label)

        val materialNameEn: String
        val materialNameTl: String
        val categoryId: Int
        val isNonBio: Boolean
        val categoryNameEn: String
        val categoryNameTl: String
        val categoryDescEn: String
        val categoryDescTl: String

        if (material != null) {
            materialNameEn = displayLabel
            materialNameTl =
                material.nameTl?.takeIf { it.isNotBlank() }
                    ?: getTagalogMaterialName(materialNameEn)
                            ?: materialNameEn

            categoryId = material.categoryId
            isNonBio = (categoryId == 2)

            categoryNameEn =
                category?.nameEn ?: if (isNonBio) getString(R.string.category_non_biodegradable) else getString(R.string.category_biodegradable)
            categoryNameTl =
                category?.nameTl ?: if (isNonBio) getString(R.string.category_di_nabubulok) else getString(R.string.category_nabubulok)

            categoryDescEn =
                category?.descriptionEn ?: if (isNonBio) getString(R.string.scanner_throw_blue) else getString(R.string.scanner_throw_green)
            categoryDescTl =
                category?.descriptionTl ?: if (isNonBio) getString(R.string.scanner_itapon_blue) else getString(R.string.scanner_itapon_green)
        } else {
            materialNameEn = displayLabel
            materialNameTl = getTagalogMaterialName(materialNameEn) ?: materialNameEn

            val guessedCategoryId = mapToCategoryId(pred.label) ?: 0
            categoryId = guessedCategoryId
            isNonBio = (categoryId == 2)

            categoryNameEn = if (isNonBio) getString(R.string.category_non_biodegradable) else getString(R.string.category_biodegradable)
            categoryNameTl = if (isNonBio) getString(R.string.category_di_nabubulok) else getString(R.string.category_nabubulok)

            categoryDescEn =
                if (isNonBio) getString(R.string.scanner_throw_blue) else getString(R.string.scanner_throw_green)
            categoryDescTl =
                if (isNonBio) getString(R.string.scanner_itapon_blue) else getString(R.string.scanner_itapon_green)
        }

        val binRes = if (isNonBio) R.drawable.ic_blue_bin else R.drawable.ic_green_bin

        speakTextEn = "$materialNameEn. $categoryNameEn. $categoryDescEn"
        speakTextTl = "$materialNameTl. $categoryNameTl. $categoryDescTl"

        if (isEnglish) {
            titleBar.text = materialNameEn
            infoTitle.text = categoryNameEn
            val confidence = (pred.confidence.coerceIn(0f, 1f) * 100f).toInt()
            val confidenceText = getString(R.string.scanner_confidence, confidence)
            infoText.text = "$materialNameEn\n$categoryDescEn\n$confidenceText"
        } else {
            titleBar.text = materialNameTl
            infoTitle.text = categoryNameTl
            val confidence = (pred.confidence.coerceIn(0f, 1f) * 100f).toInt()
            val confidenceText = getString(R.string.scanner_confidence_tl, confidence)
            infoText.text = "$materialNameTl\n$categoryDescTl\n$confidenceText"
        }

        val imageName = material?.image ?: ""
        val resId = resolveScannedObjectImageRes(
            imageName = imageName,
            predictedLabel = pred.label,
            displayMaterialName = materialNameEn
        )

        if (resId != 0) {
            infoRightIcon.setImageResource(resId)
        } else {
            infoRightIcon.setImageResource(binRes)
        }
    }

    private fun resolveScannedObjectImageRes(
        imageName: String,
        predictedLabel: String,
        displayMaterialName: String
    ): Int {
        // 1) Try DB image value directly
        if (imageName.isNotBlank()) {
            val direct = resources.getIdentifier(imageName, "drawable", packageName)
            if (direct != 0) return direct

            // 2) Normalize values like "ic_trash_banana.png" or "drawable/ic_trash_banana"
            val normalized = imageName
                .substringAfterLast('/')
                .substringBeforeLast('.')
                .trim()
            if (normalized.isNotEmpty()) {
                val normalizedRes = resources.getIdentifier(normalized, "drawable", packageName)
                if (normalizedRes != 0) return normalizedRes
            }
        }

        // 3) Fallback by predicted label/material name
        val key = ("$predictedLabel $displayMaterialName")
            .lowercase()
            .replace("_", " ")
            .replace("-", " ")

        return when {
            key.contains("banana") -> R.drawable.ic_trash_banana
            key.contains("apple") || key.contains("fruit") || key.contains("orange") || key.contains("mango") -> R.drawable.ic_trash_fruit
            key.contains("leaf") || key.contains("leaves") -> R.drawable.ic_trash_leaf
            key.contains("grass") -> R.drawable.ic_trash_grass
            key.contains("paper") -> R.drawable.ic_trash_paper
            key.contains("tissue") -> R.drawable.ic_trash_tissue
            key.contains("cup") -> R.drawable.ic_trash_plastic_cup
            key.contains("bottle") || key.contains("pet") -> R.drawable.ic_trash_bottle
            key.contains("wrapper") -> R.drawable.ic_trash_wrapper
            key.contains("styro") || key.contains("foam") || key.contains("tray") -> R.drawable.ic_trash_styro
            else -> 0
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
        val lower = label.lowercase().replace("_", " ").replace("-", " ")
        val list = mutableListOf<String>()

        if (label.isNotEmpty()) {
            list.add(label)
            list.add(lower)
            list.add(humanReadableLabel(label))
        }

        when (lower) {
            "snack wrapper" -> list.add("Candy Wrapper")
            "plastic wrapper" -> list.add("Candy Wrapper")
            "leaves" -> list.add("Leaf")
            "leaf" -> list.add("Leaf")
            "bottle" -> {
                list.add("Bottle")
                list.add("Plastic Bottle")
            }
            "pet bottle" -> {
                list.add("PET Bottle")
                list.add("Plastic Bottle")
                list.add("Bottle")
            }
            "tissue core" -> {
                list.add("Tissue Core")
                list.add("Tissue")
            }
            "tissue" -> list.add("Tissue")
            "styrofoam" -> {
                list.add("Styrofoam Tray")
                list.add("Styrofoam Box")
                list.add("Styrofoam Cup")
            }
            "apple", "mango", "banana", "orange" -> {
                list.add("Fruit")
                if (lower == "banana") list.add("Banana Peel")
                if (lower == "mango") list.add("Mango Peel")
                if (lower == "apple") list.add("Apple Core")
            }
            "styrofoam cup" -> {
                list.add("Styrofoam Cup")
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
                list.add("Tissue Core")
                list.add("Tissue")
                list.add("Tissue Roll")
            }

            lower.contains("grass") -> {
                list.add("Grass")
                list.add("Bermuda Grass")
            }

            lower.contains("leaf") || lower.contains("leaves") -> list.add("Leaves")
            lower.contains("styro") || lower.contains("tray") -> {
                list.add("Styrofoam Cup")
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
                list.add("PET Bottle")
                list.add("Bottle")
                list.add("Plastic Bottle")
            }
            lower.contains("styrofoam cup") -> {
                list.add("Styrofoam Cup")
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

    private fun humanReadableLabel(rawLabel: String): String {
        val normalized = rawLabel.trim().lowercase().replace("_", " ").replace("-", " ")

        return when (normalized) {
            "plastic wrapper", "wrapper" -> "Snack Wrapper"
            "pet bottle" -> "PET Bottle"
            "plastic cup" -> "Plastic Cup"
            "styrofoam cup", "styrofoam tray" -> "Styrofoam Cup/Tray"
            "tissue core" -> "Tissue Core"
            "stationery paper", "bond paper", "intermediate pad", "construction paper", "paper" -> "Stationery Paper"
            "leaf", "leaves" -> "Leaves"
            "grass" -> "Grass"
            "kangkong" -> "Kangkong Stem"
            "eggplant" -> "Eggplant"
            "okra" -> "Okra"
            "ampalaya" -> "Ampalaya"
            "apple" -> "Apple"
            "banana" -> "Banana"
            "orange" -> "Orange"
            "mango" -> "Mango"
            else -> normalized.split(Regex("\\s+")).joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
    }

    private fun humanReadableEnglishText(rawText: String): String {
        return rawText.trim().replace("-", " ").replace(Regex("\\s+"), " ")
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
        Log.d("RECYC_LENS_ML", "Cropping ${src.width}x${src.height} to ${size}x${size} at ($x, $y)")
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
        Log.d("RECYC_LENS_ML", "Input to model: ${resized.width}x${resized.height}, original: ${bitmap.width}x${bitmap.height}")

        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)
        val inputShape = inputTensor.shape()
        val inH = inputShape.getOrNull(1) ?: INPUT_SIZE
        val inW = inputShape.getOrNull(2) ?: INPUT_SIZE
        val channels = inputShape.getOrNull(3) ?: 3
        if (channels != 3) {
            Log.e("RECYC_LENS_ML", "Unsupported channel count: $channels")
            return null
        }

        val modelInput = if (inH == INPUT_SIZE && inW == INPUT_SIZE) {
            resized
        } else {
            Bitmap.createScaledBitmap(square, inW, inH, true)
        }

        val outputSize = outputTensor.shape().lastOrNull() ?: labels.size
        if (outputSize <= 0) return null

        val scores: FloatArray

        if (inputTensor.dataType().toString() == "FLOAT32") {
            val input = Array(1) { Array(inH) { Array(inW) { FloatArray(3) } } }
            for (y in 0 until inH) {
                for (x in 0 until inW) {
                    val px = modelInput.getPixel(x, y)
                    input[0][y][x][0] = Color.red(px) / 255f
                    input[0][y][x][1] = Color.green(px) / 255f
                    input[0][y][x][2] = Color.blue(px) / 255f
                }
            }

            val output = Array(1) { FloatArray(outputSize) }
            interpreter.run(input, output)
            scores = output[0]
            Log.d(
                "RECYC_LENS_ML",
                "classify output raw sample(first ${minOf(12, scores.size)}): ${scores.take(12).joinToString { String.format("%.4f", it) }}"
            )
        } else {
            val inputQ = inputTensor.quantizationParams()
            val inputScale = if (inputQ.scale == 0f) 1f else inputQ.scale
            val inputZeroPoint = inputQ.zeroPoint
            val isInputInt8 = inputTensor.dataType().toString() == "INT8"

            val inputBuffer = ByteBuffer.allocateDirect(inH * inW * 3).order(ByteOrder.nativeOrder())
            for (y in 0 until inH) {
                for (x in 0 until inW) {
                    val px = modelInput.getPixel(x, y)
                    val r = Color.red(px) / 255f
                    val g = Color.green(px) / 255f
                    val b = Color.blue(px) / 255f

                    val qr = (r / inputScale + inputZeroPoint).roundToInt()
                    val qg = (g / inputScale + inputZeroPoint).roundToInt()
                    val qb = (b / inputScale + inputZeroPoint).roundToInt()

                    if (isInputInt8) {
                        inputBuffer.put(qr.coerceIn(-128, 127).toByte())
                        inputBuffer.put(qg.coerceIn(-128, 127).toByte())
                        inputBuffer.put(qb.coerceIn(-128, 127).toByte())
                    } else {
                        inputBuffer.put(qr.coerceIn(0, 255).toByte())
                        inputBuffer.put(qg.coerceIn(0, 255).toByte())
                        inputBuffer.put(qb.coerceIn(0, 255).toByte())
                    }
                }
            }
            inputBuffer.rewind()

            val isOutputInt8 = outputTensor.dataType().toString() == "INT8"
            val outputQ = outputTensor.quantizationParams()
            val outputScale = if (outputQ.scale == 0f) 1f else outputQ.scale
            val outputZeroPoint = outputQ.zeroPoint

            val rawOutput = ByteArray(outputSize)
            interpreter.run(inputBuffer, rawOutput)
            scores = FloatArray(outputSize)
            for (i in 0 until outputSize) {
                val qVal = if (isOutputInt8) rawOutput[i].toInt() else rawOutput[i].toInt() and 0xFF
                scores[i] = (qVal - outputZeroPoint) * outputScale
            }
            Log.d(
                "RECYC_LENS_ML",
                "classify output dequant sample(first ${minOf(12, scores.size)}): ${scores.take(12).joinToString { String.format("%.4f", it) }}"
            )
        }

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

        // Log top 3 predictions for debugging
        val sortedPredictions = scores.mapIndexed { i, score -> labels.getOrNull(i) to score }
            .sortedByDescending { it.second }
            .take(3)
        Log.d("RECYC_LENS_ML", "Top 3: ${sortedPredictions.joinToString { "${it.first}=${String.format("%.3f", it.second)}" }}")

        val bestLabel = labels.getOrNull(bestIdx) ?: return null
        val secondLabel = if (secondIdx >= 0) labels.getOrNull(secondIdx) else null
        val secondScoreFinal = if (secondIdx >= 0) secondScore else null

        return Top2Prediction(bestLabel, bestScore, secondLabel, secondScoreFinal)
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        return try {
            val planes = image.planes
            if (planes.size < 3) {
                Log.e("RECYC_LENS_ML", "ImageProxy has insufficient planes: ${planes.size}")
                return null
            }
            
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer

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
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e("RECYC_LENS_ML", "Failed to convert ImageProxy to Bitmap", e)
            null
        }
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

        // Check for valid dimensions (minimum 50x50)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || bounds.outWidth < 50 || bounds.outHeight < 50) {
            Log.w("RECYC_LENS_ML", "Image too small or invalid: ${bounds.outWidth}x${bounds.outHeight}")
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

    private fun decodeBitmapFromFileSafely(file: File, maxDim: Int = 1600): Bitmap? {
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            val raw = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            return applyExifOrientation(raw, orientation)
        }

        val maxSrc = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxSrc / sample > maxDim) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        return applyExifOrientation(bmp, orientation)
    }
}
