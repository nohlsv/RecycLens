package com.example.recyclens

import android.Manifest
import android.content.ContentResolver
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.vision.detector.Detection
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
    private var detector: ObjectDetector? = null

    // Capture-first
    private var imageCapture: ImageCapture? = null

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

    // ======= Permissions / Pickers =======
    private val requestCamPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startCamera() else showError("Camera permission denied", "Grant camera access in Settings.")
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) {
                showInfo("No image selected", "Please choose a photo from your gallery.")
                return@registerForActivityResult
            }
            handlePickedImage(uri)
        }

    // ======= Activity =======
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

        initDetector()               // robust loader w/ path checks + messages
        checkOrRequestCamera()       // ask permission / start camera

        btnCamera.setOnClickListener { onCameraCaptureClick() }
        btnGallery.setOnClickListener { onGalleryClick() }
    }

    // ======= Validation Entrypoints =======
    private fun onCameraCaptureClick() {
        val det = detector
        if (det == null) {
            showError(
                "Detector not ready",
                "Model missing or failed to load. Expected path(s): ml/waste_yolov8n.tflite (or assets fallback)."
            )
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCamPermission.launch(Manifest.permission.CAMERA)
            return
        }
        val ic = imageCapture
        if (ic == null) {
            showInfo("Camera not started", "Starting camera…")
            startCamera()
            return
        }

        titleBar.text = "Capturing…"
        ic.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bmp = imageProxyToBitmap(image)
                        ?.let { rotateBitmapIfNeeded(it, image.imageInfo.rotationDegrees) }
                    if (bmp == null) {
                        showError("Capture failed", "Could not decode the image frame.")
                        return
                    }
                    titleBar.text = "Analyzing…"
                    runDetection(bmp)
                } catch (e: Exception) {
                    showError("Capture error", e.localizedMessage ?: "Unknown error during capture.")
                } finally {
                    image.close()
                }
            }

            override fun onError(exc: ImageCaptureException) {
                showError("Capture error", exc.message ?: "Unknown capture error.")
            }
        })
    }

    private fun onGalleryClick() {
        if (detector == null) {
            showError(
                "Detector not ready",
                "Model missing or failed to load. Expected path(s): ml/waste_yolov8n.tflite (or assets fallback)."
            )
            return
        }
        pickImage.launch("image/*")
    }

    // ======= Setup / Init =======
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
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )

                titleBar.text = "Camera ready"
                infoTitle.text = "Capture-first"
                infoText.text = "Tap the camera button to scan."
                infoRightIcon.setImageResource(R.drawable.ic_info)
            } catch (e: Exception) {
                showError("Camera error", e.localizedMessage ?: "Failed to bind camera use cases.")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Robust loader that:
     * 1) Tries common packaged asset paths.
     * 2) Lists what's actually inside assets if not found.
     * 3) Surfaces real load errors (e.g., missing metadata).
     */
    private fun initDetector() {
        // Candidate paths to try (in order)
        val candidates = listOf(
            "ml/waste_yolov8n.tflite",      // src/main/ml/
            "models/waste_yolov8n.tflite",  // src/main/assets/models/
            "waste_yolov8n.tflite"          // src/main/assets/
        )

        fun assetExists(path: String): Boolean = try {
            assets.open(path).close(); true
        } catch (_: Exception) { false }

        val foundPath = candidates.firstOrNull { assetExists(it) }

        if (foundPath == null) {
            val assetRoot = assets.list("")?.joinToString() ?: "(empty)"
            val assetMl = assets.list("ml")?.joinToString() ?: "(none)"
            val assetModels = assets.list("models")?.joinToString() ?: "(none)"

            showError(
                "Model not found",
                """
                Searched for:
                - ml/waste_yolov8n.tflite
                - models/waste_yolov8n.tflite
                - waste_yolov8n.tflite

                Assets root: $assetRoot
                Assets/ml: $assetMl
                Assets/models: $assetModels

                Put your file at:
                • app/src/main/ml/waste_yolov8n.tflite (preferred, path "ml/waste_yolov8n.tflite"), or
                • app/src/main/assets/models/waste_yolov8n.tflite
                """.trimIndent()
            )
            detector = null
            return
        }

        try {
            val base = BaseOptions.builder().setNumThreads(4).build()
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setBaseOptions(base)
                .setMaxResults(3)
                .setScoreThreshold(0.45f)
                .build()

            detector = ObjectDetector.createFromFileAndOptions(this, foundPath, options)

            titleBar.text = "Scanner ready"
            infoTitle.text = "Model loaded"
            infoText.text = "Using: $foundPath"
            infoRightIcon.setImageResource(R.drawable.ic_info)
        } catch (e: Exception) {
            detector = null
            showError(
                "Model load error",
                "Path: $foundPath\n${e.localizedMessage ?: e.toString()}\n\n" +
                        "If this says the model lacks metadata, export a Task Library–ready TFLite or add metadata (labels, normalization)."
            )
        }
    }

    // ======= Gallery Handling =======
    private fun handlePickedImage(uri: Uri) {
        try {
            val bmp = decodeBitmapFromUriSafely(contentResolver, uri, maxDim = 1280)
            if (bmp == null) {
                showError("Open image failed", "Could not decode the selected image.")
                return
            }
            titleBar.text = "Analyzing photo…"
            runDetection(bmp)
        } catch (e: Exception) {
            showError("Gallery error", e.localizedMessage ?: "Unknown error reading image.")
        }
    }

    // ======= Detection =======
    private fun runDetection(bitmap: Bitmap) {
        val det = detector ?: run {
            showError("Detector not ready", "Model failed to load.")
            return
        }

        val results: List<Detection> = try {
            val tensor = TensorImage.fromBitmap(bitmap)
            det.detect(tensor)
        } catch (e: Exception) {
            showError("Detection error", e.localizedMessage ?: "Failed to run inference.")
            return
        }

        if (results.isEmpty()) {
            showInfo("No object detected", "Center the item and try again.")
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

    // ======= UI Helpers =======
    private fun showError(title: String, message: String) = postUI {
        titleBar.text = title
        infoTitle.text = "Error"
        infoText.text = message
        infoRightIcon.setImageResource(R.drawable.ic_info)
    }

    private fun showInfo(title: String, message: String) = postUI {
        titleBar.text = title
        infoTitle.text = "Info"
        infoText.text = message
        infoRightIcon.setImageResource(R.drawable.ic_info)
    }

    private fun postUI(block: () -> Unit) = runOnUiThread { block() }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

/* ===================== Utilities ===================== */

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

private fun rotateBitmapIfNeeded(src: Bitmap, degrees: Int): Bitmap {
    if (degrees == 0) return src
    val m = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
}

/** Decode a gallery Uri safely with sampling to avoid OOM. */
private fun decodeBitmapFromUriSafely(
    resolver: ContentResolver,
    uri: Uri,
    maxDim: Int = 1600
): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

    if (opts.outWidth <= 0 || opts.outHeight <= 0) {
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }

    val maxSrc = maxOf(opts.outWidth, opts.outHeight)
    var inSample = 1
    while (maxSrc / inSample > maxDim) inSample *= 2

    val opts2 = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inSampleSize = inSample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts2) }
}

private fun ContentResolver.getDisplayName(uri: Uri): String? {
    return try {
        query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) { null }
}
