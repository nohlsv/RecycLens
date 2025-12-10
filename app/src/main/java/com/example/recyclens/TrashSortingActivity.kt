package com.example.recyclens

import android.content.ContentValues
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
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

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var gameArea: FrameLayout
    private lateinit var btnStart: TextView
    private lateinit var tvLevel: TextView
    private lateinit var binGreen: View
    private lateinit var binBlue: View
    private lateinit var tvScore: TextView
    private lateinit var circleRow: LinearLayout
    private val circleViews = mutableListOf<View>()

    private val dbName = "recyclensdb.db"

    private data class WasteItem(
        val resName: String,
        val drawableRes: Int,
        val isBiodegradable: Boolean,
        val label: String
    )

    private var allItems: List<WasteItem> = emptyList()
    private var currentQueue: MutableList<WasteItem> = mutableListOf()
    private var answeredCount = 0
    private var totalToSort = 0
    private var selectedLevel = 1
    private var score = 0
    private var wrongCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.trash_sorting)

        mediaPlayer = MediaPlayer.create(this, R.raw.trash_sorting_music)
        mediaPlayer?.isLooping = true

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
            showInstructionDialog(1)
        }

        selectedLevel = intent.getIntExtra("extra_level", 1)
        tvLevel.text = when (selectedLevel) {
            1 -> "Easy"
            2 -> "Medium"
            3 -> "Hard"
            else -> "Easy"
        }

        allItems = loadItemsFromDb()
        resetScoreAndCircles(0)

        btnStart.setOnClickListener {
            val roundRunning =
                (answeredCount > 0 && answeredCount < totalToSort) || currentQueue.isNotEmpty()
            if (roundRunning) {
                AlertDialog.Builder(this)
                    .setTitle("Restart level?")
                    .setMessage("Do you want to reset the trash and your score and start again?")
                    .setPositiveButton("Yes") { _, _ ->
                        startNewRound()
                    }
                    .setNegativeButton("No", null)
                    .show()
            } else {
                startNewRound()
            }
        }

        showInstructionDialog(1)
        setupBottomBar(BottomBar.Tab.PLAY)
    }

    override fun onResume() {
        super.onResume()
        if (mediaPlayer != null && mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
    }

    override fun onPause() {
        super.onPause()
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun loadItemsFromDb(): List<WasteItem> {
        val map = LinkedHashMap<String, WasteItem>()
        try {
            val db = openOrCreateDatabase(dbName, MODE_PRIVATE, null)
            val sql = """
                SELECT wm.image_path, wm.name_en, wc.name
                FROM waste_material wm
                JOIN waste_category wc ON wm.category_id = wc.category_id
                WHERE wm.image_path IS NOT NULL AND wm.image_path <> ''
            """.trimIndent()
            val c = db.rawQuery(sql, null)
            val imgIdx = c.getColumnIndex("image_path")
            val nameIdx = c.getColumnIndex("name_en")
            val catIdx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                val img = c.getString(imgIdx) ?: continue
                if (map.containsKey(img)) continue
                val label = c.getString(nameIdx) ?: img
                val catName = c.getString(catIdx) ?: ""
                val isBio = catName.equals("Biodegradable", ignoreCase = true)
                val resId = resources.getIdentifier(img, "drawable", packageName)
                if (resId != 0) {
                    map[img] = WasteItem(img, resId, isBio, label)
                }
            }
            c.close()
            db.close()
        } catch (e: Exception) {
            Log.e("TrashSortingActivity", "loadItemsFromDb failed", e)
        }

        if (map.isNotEmpty()) return map.values.toList()

        return listOf(
            WasteItem("ic_trash_banana", R.drawable.ic_trash_banana, true, "Banana Peel"),
            WasteItem("ic_trash_fruit", R.drawable.ic_trash_fruit, true, "Fruit"),
            WasteItem("ic_trash_leaf", R.drawable.ic_trash_leaf, true, "Leaf"),
            WasteItem("ic_trash_paper", R.drawable.ic_trash_paper, true, "Paper"),
            WasteItem("ic_trash_tissue", R.drawable.ic_trash_tissue, true, "Tissue"),
            WasteItem("ic_trash_plastic_cup", R.drawable.ic_trash_plastic_cup, false, "Plastic Cup"),
            WasteItem("ic_trash_bottle", R.drawable.ic_trash_bottle, false, "Plastic Bottle"),
            WasteItem("ic_trash_wrapper", R.drawable.ic_trash_wrapper, false, "Candy Wrapper"),
            WasteItem("ic_trash_styro", R.drawable.ic_trash_styro, false, "Styrofoam Box"),
            WasteItem("ic_trash_grass", R.drawable.ic_trash_grass, true, "Grass")
        )
    }

    private fun showInstructionDialog(page: Int = 1) {
        val title = if (page == 1) {
            "Biodegradable – GREEN bin"
        } else {
            "Non-biodegradable – BLUE bin"
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(createInstructionContent(page))

        if (page == 1) {
            builder.setPositiveButton("Next (Non-bio)") { dialog, _ ->
                dialog.dismiss()
                showInstructionDialog(2)
            }
            builder.setNegativeButton("Close") { dialog, _ -> dialog.dismiss() }
        } else {
            builder.setPositiveButton("Back (Bio)") { dialog, _ ->
                dialog.dismiss()
                showInstructionDialog(1)
            }
            builder.setNegativeButton("Done") { dialog, _ -> dialog.dismiss() }
        }

        builder.show()
    }

    private fun createInstructionContent(page: Int): View {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }

        val binIcon = ImageView(this).apply {
            val size = dp(40)
            layoutParams = LinearLayout.LayoutParams(size, size)
            val binView = if (page == 1) binGreen else binBlue
            if (binView is ImageView) {
                setImageDrawable(binView.drawable)
            } else {
                background = binView.background
            }
        }

        val binLabel = TextView(this).apply {
            text = if (page == 1) {
                "Drag these to this GREEN bin"
            } else {
                "Drag these to this BLUE bin"
            }
            textSize = 15f
            setPadding(dp(12), 0, 0, 0)
        }

        headerRow.addView(binIcon)
        headerRow.addView(binLabel)
        layout.addView(headerRow)

        val subtitle = TextView(this).apply {
            text = if (page == 1) {
                "These are biodegradable items:"
            } else {
                "These are non-biodegradable items:"
            }
            textSize = 14f
        }
        layout.addView(subtitle)

        val items = if (page == 1) {
            listOf(
                R.drawable.ic_trash_banana to "Banana Peel",
                R.drawable.ic_trash_fruit to "Fruit",
                R.drawable.ic_trash_leaf to "Leaf",
                R.drawable.ic_trash_grass to "Grass",
                R.drawable.ic_trash_paper to "Paper",
                R.drawable.ic_trash_tissue to "Tissue"
            )
        } else {
            listOf(
                R.drawable.ic_trash_plastic_cup to "Plastic Cup",
                R.drawable.ic_trash_bottle to "Plastic Bottle",
                R.drawable.ic_trash_wrapper to "Candy Wrapper",
                R.drawable.ic_trash_styro to "Styrofoam Box"
            )
        }

        for ((resId, label) in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
                gravity = Gravity.CENTER_VERTICAL
            }

            val icon = ImageView(this).apply {
                setImageResource(resId)
                val size = dp(32)
                layoutParams = LinearLayout.LayoutParams(size, size)
            }

            val text = TextView(this).apply {
                text = label
                textSize = 14f
                setPadding(dp(12), 0, 0, 0)
            }

            row.addView(icon)
            row.addView(text)
            layout.addView(row)
        }

        val hint = TextView(this).apply {
            text = "\nWatch the trash picture and its name, then drag it into the correct bin."
            textSize = 13f
        }
        layout.addView(hint)

        return layout
    }

    private fun startNewRound() {
        btnStart.visibility = View.GONE
        gameArea.removeAllViews()
        answeredCount = 0
        score = 0
        wrongCount = 0

        totalToSort = when (selectedLevel) {
            1 -> 5
            2 -> 7
            3 -> 10
            else -> 5
        }

        val source = if (allItems.size >= totalToSort) allItems else loadItemsFromDb()
        currentQueue = source.shuffled()
            .let { if (it.size >= totalToSort) it.take(totalToSort) else it }
            .toMutableList()

        resetScoreAndCircles(totalToSort)

        gameArea.post {
            showNextItem()
        }
    }

    private fun resetScoreAndCircles(count: Int) {
        tvScore.text = "Score: 0 / $count"
        circleRow.removeAllViews()
        circleViews.clear()

        repeat(count) {
            val circle = View(this).apply {
                val d = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#BBBBBB"))
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
        if (currentQueue.isEmpty()) {
            saveGameResultToDb(totalToSort, score, wrongCount)
            showSuccessDialog()
            btnStart.visibility = View.VISIBLE
            btnStart.text = "Play Again"
            return
        }

        val item = currentQueue.removeAt(0)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            tag = item
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
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }

        container.addView(iv)
        container.addView(labelView)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        }

        gameArea.addView(container, lp)
        attachDragListener(container)
        updateProgress()
    }

    private fun updateProgress() {
        tvScore.text = "Score: $score / $totalToSort"
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
            Toast.makeText(this, "Drop the trash inside the GREEN or BLUE bin.", Toast.LENGTH_SHORT).show()
            view.animate().translationX(0f).translationY(0f).setDuration(200).start()
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
            wrongCount++
            setCircleColor(currentIndex, Color.parseColor("#EF5350"))
            Toast.makeText(
                this,
                if (item.isBiodegradable)
                    "${item.label} should go in the GREEN bin."
                else
                    "${item.label} should go in the BLUE bin.",
                Toast.LENGTH_SHORT
            ).show()
        }

        answeredCount++
        updateProgress()
        showNextItem()
    }

    private fun saveGameResultToDb(totalScore: Int, correct: Int, wrong: Int) {
        try {
            val db = openOrCreateDatabase(dbName, MODE_PRIVATE, null)

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS game_result(
                    total_score INTEGER NOT NULL,
                    correct_count INTEGER NOT NULL,
                    wrong_count INTEGER NOT NULL,
                    game TEXT NOT NULL
                )
                """.trimIndent()
            )

            val values = ContentValues().apply {
                put("total_score", totalScore)
                put("correct_count", correct)
                put("wrong_count", wrong)
                put("game", "trash_sorting")
            }

            val rowId = db.insert("game_result", null, values)
            Log.d("TrashSortingActivity", "Inserted row into game_result, rowId=$rowId")

            val levelName = when (selectedLevel) {
                1 -> "Easy"
                2 -> "Medium"
                3 -> "Hard"
                else -> "Easy"
            }
            val upd = ContentValues().apply {
                put("current_score", correct)
            }
            val updatedRows = db.update(
                "game",
                upd,
                "game_title=? AND game_level=?",
                arrayOf("Trash Sorting", levelName)
            )
            Log.d("TrashSortingActivity", "Updated game table rows=$updatedRows")

            db.close()
        } catch (e: Exception) {
            Log.e("TrashSortingActivity", "saveGameResultToDb failed", e)
        }
    }

    private fun showSuccessDialog() {
        val scoreText = "Correct: $score  |  Wrong: $wrongCount"
        AlertDialog.Builder(this)
            .setTitle("Good work!")
            .setMessage("You finished the level!\n$scoreText")
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
