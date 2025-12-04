package com.example.recyclens

import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class TrashSortingActivity : AppCompatActivity() {

    private lateinit var gameArea: FrameLayout
    private lateinit var btnStart: TextView
    private lateinit var tvLevel: TextView

    private lateinit var binGreen: View
    private lateinit var binBlue: View

    private lateinit var tvScore: TextView
    private lateinit var circleRow: LinearLayout
    private val circleViews = mutableListOf<View>()

    private data class WasteItem(
        val drawableRes: Int,
        val isBiodegradable: Boolean,  // true = BIO → green bin, false = NON-BIO → blue bin
        val label: String              // simple name for kids
    )

    private val allItems = listOf(
        WasteItem(R.drawable.ic_trash_banana, true,  "Banana peel"),
        WasteItem(R.drawable.ic_trash_fruit,  true,  "Fruit"),
        WasteItem(R.drawable.ic_trash_leaf,   true,  "Leaf"),
        WasteItem(R.drawable.ic_trash_paper,  true,  "Paper"),
        WasteItem(R.drawable.ic_trash_tissue, true,  "Tissue"),
        WasteItem(R.drawable.ic_trash_plastic_cup, false, "Plastic cup"),
        WasteItem(R.drawable.ic_trash_bottle,      false, "Plastic bottle"),
        WasteItem(R.drawable.ic_trash_wrapper,     false, "Candy wrapper"),
        WasteItem(R.drawable.ic_trash_styro,       false, "Styrofoam box")
    )

    private var currentQueue: MutableList<WasteItem> = mutableListOf()
    private var answeredCount = 0
    private var totalToSort = 0
    private var selectedLevel = 1
    private var score = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.trash_sorting)

        setupBottomBar(BottomBar.Tab.PLAY)

        gameArea = findViewById(R.id.gameAreaTrash)
        btnStart = findViewById(R.id.btnStartTrash)
        tvLevel = findViewById(R.id.tvLevel)

        binGreen = findViewById(R.id.binGreen)
        binBlue = findViewById(R.id.binBlue)

        tvScore = findViewById(R.id.tvScore)
        circleRow = findViewById(R.id.circleRow)

        findViewById<ImageButton>(R.id.btnBackTrash).setOnClickListener {
            finish()
        }

        findViewById<ImageButton>(R.id.btnInfoTrash).setOnClickListener {
            showInstructionDialog()
        }

        selectedLevel = intent.getIntExtra("extra_level", 1)
        tvLevel.text = when (selectedLevel) {
            1 -> "Easy"
            2 -> "Medium"
            3 -> "Hard"
            else -> "Easy"
        }

        resetScoreAndCircles(0)

        btnStart.setOnClickListener {
            startNewRound()
        }

        // Show instructions when screen opens
        showInstructionDialog()
    }

    private fun showInstructionDialog() {
        AlertDialog.Builder(this)
            .setTitle("How to play")
            .setMessage(
                "Hello, little recycler!\n\n" +
                        "1. Look at the trash picture and its name.\n" +
                        "2. If it is food, fruit, leaves, paper, or tissue,\n" +
                        "   drag it to the GREEN bin.\n" +
                        "3. If it is plastic, bottle, wrapper, or styro,\n" +
                        "   drag it to the BLUE bin.\n" +
                        "4. Drop the trash inside a bin and let go.\n" +
                        "5. Try to get many green circles!"
            )
            .setPositiveButton("Got it!") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun startNewRound() {
        gameArea.removeAllViews()
        answeredCount = 0
        score = 0

        totalToSort = when (selectedLevel) {
            1 -> 5   // Easy
            2 -> 7   // Medium
            3 -> 10  // Hard
            else -> 5
        }

        currentQueue = allItems.shuffled()
            .let { if (it.size >= totalToSort) it.take(totalToSort) else it }
            .toMutableList()

        resetScoreAndCircles(totalToSort)

        gameArea.post {
            showNextItem()
        }
    }

    private fun resetScoreAndCircles(count: Int) {
        tvScore.text = "Score: 0"
        circleRow.removeAllViews()
        circleViews.clear()

        repeat(count) {
            val circle = View(this).apply {
                val d = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#BBBBBB")) // grey = not answered yet
                }
                background = d
            }

            val lp = LinearLayout.LayoutParams(dp(16), dp(16))
            lp.marginStart = dp(4)
            lp.marginEnd = dp(4)
            circleRow.addView(circle, lp)
            circleViews.add(circle)
        }
    }

    private fun setCircleColor(index: Int, color: Int) {
        if (index < 0 || index >= circleViews.size) return
        val d = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
        circleViews[index].background = d
    }

    private fun showNextItem() {
        if (currentQueue.isEmpty() || answeredCount >= totalToSort) {
            showSuccessDialog()
            return
        }

        // ❌ old: val item = currentQueue.removeFirst()
        // ✅ new: use removeAt(0) for compatibility with Android 14/15
        val item = currentQueue[0]
        currentQueue.removeAt(0)

        // Container for image + label
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            tag = item  // attach WasteItem here
        }

        val iv = ImageView(this).apply {
            setImageResource(item.drawableRes)
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(dp(90), dp(90))
        }

        val labelView = TextView(this).apply {
            text = item.label
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
        }

        container.addView(iv)
        container.addView(labelView)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.topMargin = dp(40)

        gameArea.addView(container, lp)

        attachDragListener(container)
        updateProgress()
    }

    private fun updateProgress() {
        btnStart.text = "${answeredCount} / $totalToSort"
        tvScore.text = "Score: $score"
    }

    private fun attachDragListener(view: View) {
        var dX = 0f
        var dY = 0f

        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
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
                    handleDrop(v, event.rawX.toInt(), event.rawY.toInt())
                    true
                }
                else -> false
            }
        }
    }

    private fun handleDrop(view: View, dropRawX: Int, dropRawY: Int) {
        val item = view.tag as? WasteItem ?: return

        val greenRect = Rect()
        val blueRect = Rect()
        binGreen.getGlobalVisibleRect(greenRect)
        binBlue.getGlobalVisibleRect(blueRect)

        val droppedOnGreen = greenRect.contains(dropRawX, dropRawY)
        val droppedOnBlue = blueRect.contains(dropRawX, dropRawY)

        if (!droppedOnGreen && !droppedOnBlue) {
            Toast.makeText(this, "Drop the trash inside the green or blue bin.", Toast.LENGTH_SHORT).show()
            return
        }

        val currentIndex = answeredCount

        val correctIsGreen = item.isBiodegradable
        val isCorrect = (correctIsGreen && droppedOnGreen) || (!correctIsGreen && droppedOnBlue)

        gameArea.removeView(view)

        if (isCorrect) {
            score++
            setCircleColor(currentIndex, Color.parseColor("#4CAF50"))
            Toast.makeText(this, "Correct!", Toast.LENGTH_SHORT).show()
        } else {
            setCircleColor(currentIndex, Color.parseColor("#EF5350"))
            Toast.makeText(
                this,
                if (item.isBiodegradable)
                    "${item.label} is soft or from plants.\nIt goes in the GREEN bin."
                else
                    "${item.label} is plastic or hard.\nIt goes in the BLUE bin.",
                Toast.LENGTH_SHORT
            ).show()
        }

        answeredCount++
        updateProgress()
        showNextItem()
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Good work!")
            .setMessage("You finished the level!\nScore: $score / $totalToSort")
            .setCancelable(false)
            .setPositiveButton("Play again") { _, _ ->
                startNewRound()
            }
            .setNegativeButton("Back") { _, _ ->
                finish()
            }
            .show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
