package com.example.recyclens

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer // Import MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class StreetCleanupActivity : AppCompatActivity() {

    // Music player
    private var mediaPlayer: MediaPlayer? = null

    // For flood overlay
    private lateinit var rootLayout: ViewGroup
    private var floodView: View? = null

    private lateinit var gameArea: FrameLayout
    private lateinit var btnStart: TextView
    private lateinit var tvLevel: TextView

    // Bins from XML (green_bin.png and blue_bin.png)
    private lateinit var bioBin: View      // binGreenStreet
    private lateinit var nonBioBin: View   // binBlueStreet

    private lateinit var tvTimer: TextView
    private var feedbackCircle: View? = null   // red / green circle on top

    private var level = 1
    private var remainingItems = 0
    private var timer: CountDownTimer? = null

    // For score & time tracking
    private var totalItems: Int = 0
    private var totalSeconds: Int = 0
    private var secondsLeft: Int = 0

    private data class WasteItem(
        val drawableRes: Int,
        val isBiodegradable: Boolean
    )

    // All trash icons used in the game
    private val allItems = listOf(
        WasteItem(R.drawable.ic_trash_banana, true),
        WasteItem(R.drawable.ic_trash_fruit, true),
        WasteItem(R.drawable.ic_trash_leaf, true),
        WasteItem(R.drawable.ic_trash_paper, true),
        WasteItem(R.drawable.ic_trash_tissue, true),
        WasteItem(R.drawable.ic_trash_plastic_cup, false),
        WasteItem(R.drawable.ic_trash_bottle, false),
        WasteItem(R.drawable.ic_trash_wrapper, false),
        WasteItem(R.drawable.ic_trash_styro, false),
        // NEW: Grass icon (biodegradable)
        WasteItem(R.drawable.ic_trash_grass, true)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.street_cleanup)

        // Initialize and start the music
        // Make sure you have a music file named "street_cleanup_music.mp3" in res/raw/
        mediaPlayer = MediaPlayer.create(this, R.raw.street_cleanup_music)
        mediaPlayer?.isLooping = true // Loop the music

        // setupBottomBar(BottomBar.Tab.PLAY) // This line is causing an error, so I've commented it out.

        rootLayout = findViewById(android.R.id.content)

        gameArea = findViewById(R.id.gameAreaStreet)
        btnStart = findViewById(R.id.btnStartStreet)
        tvLevel = findViewById(R.id.tvLevel)

        bioBin = findViewById(R.id.binGreenStreet)
        nonBioBin = findViewById(R.id.binBlueStreet)

        findViewById<ImageButton>(R.id.btnBackStreet).setOnClickListener {
            timer?.cancel()
            timer = null
            finish()
        }

        findViewById<ImageButton>(R.id.btnInfoStreet).setOnClickListener {
            showInstructionDialog()
        }

        level = intent.getIntExtra("extra_level", 1)
        tvLevel.text = when (level) {
            1 -> "Easy"
            2 -> "Medium"
            3 -> "Hard"
            else -> "Easy"
        }

        // Start button with Yes/No validation (restart)
        btnStart.setOnClickListener {
            val gameRunning = (timer != null) || (remainingItems > 0)

            if (gameRunning) {
                AlertDialog.Builder(this)
                    .setTitle("Restart game?")
                    .setMessage("Do you want to reset the trash and the timer and start again?")
                    .setPositiveButton("Yes") { _, _ ->
                        startNewGame()
                    }
                    .setNegativeButton("No", null)
                    .show()
            } else {
                startNewGame()
            }
        }

        // Show tutorial first time
        showInstructionDialog()
    }

    override fun onResume() {
        super.onResume()
        // Start or resume playing music if it's not null and not already playing
        if (mediaPlayer != null && mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        // Pause music when the activity is not in the foreground
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop and release the MediaPlayer to free up resources
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        // Your existing timer cleanup
        timer?.cancel()
        timer = null
    }

    private fun showInstructionDialog() {
        AlertDialog.Builder(this)
            .setTitle("How to play")
            .setMessage(
                "Hello, street cleaner!\n\n" +
                        "1. Look for the trash on the street.\n" +
                        "2. Each trash icon is labeled so you can see what it is (e.g., Banana Peel, Plastic Cup, Wrapper, Grass).\n" +
                        "3. GREEN bin = Biodegradable (nabubulok): food, fruit, leaves, grass, paper, tissue.\n" +
                        "4. BLUE bin = Non-biodegradable (hindi nabubulok): plastic cups, bottles, wrappers, styro.\n" +
                        "5. Drag the trash and drop it on the correct bin.\n" +
                        "6. Green circle = correct, red circle = wrong.\n" +
                        "7. Clean all trash before the time is up!"
            )
            .setPositiveButton("Got it!") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /** Called every time the Start button is pressed (after confirmation if needed). */
    private fun startNewGame() {
        timer?.cancel()
        timer = null
        gameArea.removeAllViews()
        remainingItems = 0
        totalItems = 0
        totalSeconds = 0
        secondsLeft = 0

        // Remove flood overlay if it was shown before
        floodView?.let { rootLayout.removeView(it) }
        floodView = null

        gameArea.post {
            createTimer()
            createFeedbackCircle()
            populateTrash()
            startTimer()
        }
    }

    private fun createTimer() {
        if (!::tvTimer.isInitialized) {
            tvTimer = TextView(this).apply {
                textSize = 18f
                setTextColor(Color.WHITE)
                setShadowLayer(4f, 0f, 0f, Color.BLACK)
            }
        }

        // Make sure it’s attached to gameArea (important after removeAllViews)
        if (tvTimer.parent == null) {
            val lpTimer = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
            lpTimer.topMargin = dp(12)
            lpTimer.marginEnd = dp(12)
            gameArea.addView(tvTimer, lpTimer)
        }

        tvTimer.text = "Time: 00"
    }

    private fun createFeedbackCircle() {
        if (feedbackCircle == null) {
            feedbackCircle = View(this)
        }

        if (feedbackCircle?.parent == null) {
            val lp = FrameLayout.LayoutParams(
                dp(24),
                dp(24),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
            lp.topMargin = dp(8)
            gameArea.addView(feedbackCircle, lp)
        }

        feedbackCircle?.alpha = 0f
    }

    private fun showFeedbackCircle(isCorrect: Boolean) {
        val color = if (isCorrect) {
            Color.parseColor("#4CAF50") // green
        } else {
            Color.parseColor("#EF5350") // red
        }

        val d = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

        feedbackCircle?.background = d
        feedbackCircle?.animate()
            ?.alpha(1f)
            ?.setDuration(150)
            ?.withEndAction {
                feedbackCircle?.animate()
                    ?.alpha(0f)
                    ?.setStartDelay(400)
                    ?.setDuration(200)
                    ?.start()
            }
            ?.start()
    }

    // Populate trash icons for the level
    private fun populateTrash() {
        val count = when (level) {
            1 -> 5   // Easy
            2 -> 7   // Medium
            3 -> 9   // Hard
            else -> 5
        }

        val selected = allItems.shuffled().take(count)
        remainingItems = selected.size
        totalItems = remainingItems   // track score base

        val areaWidth = if (gameArea.width > 0) gameArea.width else gameArea.measuredWidth
        val areaHeight = if (gameArea.height > 0) gameArea.height else gameArea.measuredHeight

        if (areaWidth <= 0 || areaHeight <= 0) {
            remainingItems = 0
            totalItems = 0
            return
        }

        val size = dp(70)
        val sideMargin = dp(16)
        val topMarginLimit = dp(40)
        val bottomReserved = dp(150)

        val minX = sideMargin
        val rawMaxX = areaWidth - size - sideMargin
        val maxXExclusive = if (rawMaxX <= minX) minX + 1 else rawMaxX

        val minY = topMarginLimit
        val rawMaxY = areaHeight - size - bottomReserved
        val maxYExclusive = if (rawMaxY <= minY) minY + 1 else rawMaxY

        for (item in selected) {
            val iv = ImageView(this).apply {
                setImageResource(item.drawableRes)
                adjustViewBounds = true
                tag = item
            }

            val lp = FrameLayout.LayoutParams(size, size)

            val randomX = kotlin.random.Random.nextInt(minX, maxXExclusive)
            val randomY = kotlin.random.Random.nextInt(minY, maxYExclusive)

            lp.leftMargin = randomX
            lp.topMargin = randomY

            gameArea.addView(iv, lp)
            attachDragListener(iv)
        }

        btnStart.text = "Time running…"
    }

    private fun startTimer() {
        val seconds = when (level) {
            1 -> 90
            2 -> 60
            3 -> 45
            else -> 90
        }

        totalSeconds = seconds
        secondsLeft = seconds

        timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val s = (millisUntilFinished / 1000).toInt()
                secondsLeft = s
                tvTimer.text = String.format("Time: %02d", s)
            }

            override fun onFinish() {
                secondsLeft = 0
                // If already finished (all trash sorted), don’t show fail
                if (remainingItems <= 0) return

                tvTimer.text = "Time: 00"
                showFailDialog()
            }
        }.start()
    }

    private fun attachDragListener(view: ImageView) {
        var dX = 0f
        var dY = 0f
        var startX = 0f
        var startY = 0f

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = v.x
                    startY = v.y
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    v.x = event.rawX + dX
                    v.y = event.rawY + dY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    handleDrop(v, event.rawX.toInt(), event.rawY.toInt(), startX, startY)
                    true
                }
                else -> false
            }
        }
    }

    private fun handleDrop(
        view: View,
        dropRawX: Int,
        dropRawY: Int,
        startX: Float,
        startY: Float
    ) {
        val item = view.tag as? WasteItem ?: return

        val bioRect = Rect()
        val nonBioRect = Rect()
        bioBin.getGlobalVisibleRect(bioRect)
        nonBioBin.getGlobalVisibleRect(nonBioRect)

        val droppedOnBio = bioRect.contains(dropRawX, dropRawY)
        val droppedOnNonBio = nonBioRect.contains(dropRawX, dropRawY)

        if (!droppedOnBio && !droppedOnNonBio) {
            view.animate()
                .x(startX)
                .y(startY)
                .setDuration(200)
                .start()
            Toast.makeText(
                this,
                "Put the trash inside the GREEN or BLUE bin.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val correctIsBio = item.isBiodegradable
        val isCorrect = (correctIsBio && droppedOnBio) || (!correctIsBio && droppedOnNonBio)

        showFeedbackCircle(isCorrect)

        if (isCorrect) {
            gameArea.removeView(view)
            remainingItems--

            if (remainingItems <= 0) {
                timer?.cancel()
                timer = null
                showSuccessDialog()
            }
        } else {
            Toast.makeText(
                this,
                if (item.isBiodegradable)
                    "This trash can rot. It belongs in the GREEN bin."
                else
                    "This is plastic or hard. It belongs in the BLUE bin.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showFloodAnimation() {
        if (floodView == null) {
            floodView = View(this).apply {
                setBackgroundColor(Color.parseColor("#8800B0FF"))
            }
            rootLayout.addView(
                floodView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        floodView?.post {
            val h = rootLayout.height.toFloat()
            floodView?.translationY = h
            floodView?.alpha = 0f

            floodView?.animate()
                ?.translationY(0f)
                ?.alpha(1f)
                ?.setDuration(1200)
                ?.start()
        }
    }

    private fun showSuccessDialog() {
        val levelName = when (level) {
            1 -> "Easy"
            2 -> "Medium"
            3 -> "Hard"
            else -> "Easy"
        }

        // On Easy, totalItems will be 5 (5 items spawned)
        val scoreText = "Score: $totalItems / $totalItems trash sorted"
        val timeText = "Time left: $secondsLeft second(s)"

        AlertDialog.Builder(this)
            .setTitle("Level complete!")
            .setMessage(
                "You finished the $levelName level!\n\n" +
                        "$scoreText\n$timeText"
            )
            .setCancelable(false)
            .setPositiveButton("Play again") { _, _ ->
                startNewGame()
            }
            .setNegativeButton("Back") { _, _ ->
                finish()
            }
            .show()
    }

    private fun showFailDialog() {
        showFloodAnimation()

        val sorted = totalItems - remainingItems
        val timeUsed = totalSeconds - secondsLeft
        val scoreText = "Score: $sorted / $totalItems trash sorted"
        val timeText = "Time used: $timeUsed second(s)"

        AlertDialog.Builder(this)
            .setTitle("Oh no!")
            .setMessage(
                "The drains got blocked and the street flooded because some trash was left.\n\n" +
                        "$scoreText\n$timeText\n\n" +
                        "Try again and clean everything before the time runs out!"
            )
            .setCancelable(false)
            .setPositiveButton("Try again") { _, _ ->
                startNewGame()
            }
            .setNegativeButton("Back") { _, _ ->
                finish()
            }
            .show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    // This method is now handled by the new onResume, onPause, and onDestroy methods.
    // I have updated your original onDestroy to include the music player release.
}
