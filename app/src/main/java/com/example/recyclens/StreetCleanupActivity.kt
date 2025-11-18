package com.example.recyclens

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
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

    private data class WasteItem(
        val drawableRes: Int,
        val isBiodegradable: Boolean
    )

    private val allItems = listOf(
        WasteItem(R.drawable.ic_trash_banana, true),
        WasteItem(R.drawable.ic_trash_fruit, true),
        WasteItem(R.drawable.ic_trash_leaf, true),
        WasteItem(R.drawable.ic_trash_paper, true),
        WasteItem(R.drawable.ic_trash_tissue, true),
        WasteItem(R.drawable.ic_trash_plastic_cup, false),
        WasteItem(R.drawable.ic_trash_bottle, false),
        WasteItem(R.drawable.ic_trash_wrapper, false),
        WasteItem(R.drawable.ic_trash_styro, false)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.street_cleanup)

        setupBottomBar(BottomBar.Tab.PLAY)

        rootLayout = findViewById(android.R.id.content)

        gameArea = findViewById(R.id.gameAreaStreet)
        btnStart = findViewById(R.id.btnStartStreet)
        tvLevel = findViewById(R.id.tvLevel)

        // bins are the green_bin.png and blue_bin.png ImageViews in your XML
        bioBin = findViewById(R.id.binGreenStreet)
        nonBioBin = findViewById(R.id.binBlueStreet)

        findViewById<ImageButton>(R.id.btnBackStreet).setOnClickListener {
            timer?.cancel()
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

        // Start button always restarts the whole game
        btnStart.setOnClickListener {
            startNewGame()
        }

        // Show tutorial on first open
        showInstructionDialog()
    }

    private fun showInstructionDialog() {
        AlertDialog.Builder(this)
            .setTitle("How to play")
            .setMessage(
                "Hello, street cleaner!\n\n" +
                        "1. Look for the trash on the street.\n" +
                        "2. GREEN bin = Biodegradable (nabubulok): food, fruit, leaves, paper, tissue.\n" +
                        "3. BLUE bin = Non-biodegradable (hindi nabubulok): plastic cups, bottles, wrappers, styro.\n" +
                        "4. Drag the trash and drop it on the correct bin.\n" +
                        "5. Green circle = correct, red circle = wrong.\n" +
                        "6. Clean all trash before the time is up!"
            )
            .setPositiveButton("Got it!") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /** Called every time the Start button is pressed. */
    private fun startNewGame() {
        timer?.cancel()
        gameArea.removeAllViews()
        remainingItems = 0

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
                text = "Time: 00"
            }

            val lpTimer = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
            lpTimer.topMargin = dp(12)
            lpTimer.marginEnd = dp(12)
            gameArea.addView(tvTimer, lpTimer)
        } else {
            tvTimer.text = "Time: 00"
        }
    }

    private fun createFeedbackCircle() {
        if (feedbackCircle == null) {
            feedbackCircle = View(this).apply { alpha = 0f }
            val lp = FrameLayout.LayoutParams(
                dp(24),
                dp(24),
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            )
            lp.topMargin = dp(8)
            gameArea.addView(feedbackCircle, lp)
        } else {
            feedbackCircle?.alpha = 0f
        }
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

    private fun populateTrash() {
        val count = when (level) {
            1 -> 5   // Easy
            2 -> 7   // Medium
            3 -> 9   // Hard
            else -> 5
        }

        val selected = allItems.shuffled().take(count)
        remainingItems = selected.size

        val areaWidth = gameArea.width
        val areaHeight = gameArea.height

        val size = dp(70)
        val topMarginLimit = dp(40)
        val bottomReserved = dp(150) // keep space above bins

        for (item in selected) {
            val iv = ImageView(this).apply {
                setImageResource(item.drawableRes)
                adjustViewBounds = true
                tag = item
            }

            val lp = FrameLayout.LayoutParams(size, size)

            val maxX = areaWidth - size - dp(16)
            val maxY = areaHeight - size - bottomReserved

            val randomX = kotlin.random.Random.nextInt(dp(16), maxX.coerceAtLeast(dp(16)))
            val randomY = kotlin.random.Random.nextInt(topMarginLimit, maxY.coerceAtLeast(topMarginLimit))

            lp.leftMargin = randomX
            lp.topMargin = randomY

            gameArea.addView(iv, lp)
            attachDragListener(iv)
        }

        btnStart.text = "Time running…"
    }

    private fun startTimer() {
        val seconds = when (level) {
            1 -> 90   // Easy
            2 -> 60   // Medium
            3 -> 45   // Hard
            else -> 90
        }

        timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val s = (millisUntilFinished / 1000).toInt()
                tvTimer.text = String.format("Time: %02d", s)
            }

            override fun onFinish() {
                tvTimer.text = "Time: 00"
                if (remainingItems > 0) {
                    showFailDialog()
                } else {
                    showSuccessDialog()
                }
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
                    // remember original position
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

    /**
     * Uses the raw touch position vs. the GREEN and BLUE bin rects.
     * If dropped outside both bins → animate back to (startX, startY).
     */
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

        // ❌ Not on any bin → snap back to original spot
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
                showSuccessDialog()
            }
        } else {
            // Wrong bin: show hint but keep the trash where it is (they can drag again)
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
                setBackgroundColor(Color.parseColor("#8800B0FF")) // semi-transparent water
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
        AlertDialog.Builder(this)
            .setTitle("Great job!")
            .setMessage("You collected and sorted all the trash correctly!\nThe street stayed clean.")
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

        AlertDialog.Builder(this)
            .setTitle("Oh no!")
            .setMessage(
                "The drains got blocked and the street flooded because some trash was left.\n" +
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

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
