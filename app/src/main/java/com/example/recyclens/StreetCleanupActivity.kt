package com.example.recyclens

import android.content.ContentValues
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.os.CountDownTimer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class StreetCleanupActivity : AppCompatActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var rootLayout: ViewGroup
    private var floodView: View? = null

    private lateinit var gameArea: FrameLayout
    private lateinit var btnStart: TextView
    private lateinit var tvLevel: TextView
    private lateinit var bioBin: View
    private lateinit var nonBioBin: View
    private lateinit var tvTimer: TextView
    private var feedbackCircle: View? = null

    private var level = 1
    private var remainingItems = 0
    private var timer: CountDownTimer? = null
    private var totalItems: Int = 0
    private var totalSeconds: Int = 0
    private var secondsLeft: Int = 0
    private var correctCount = 0
    private var wrongCount = 0

    private val dbName = "recyclensdb.db"

    private data class WasteItem(
        val resName: String,
        val drawableRes: Int,
        val isBiodegradable: Boolean
    )

    private data class LevelConfig(
        val trashCount: Int,
        val timerSeconds: Int,
        val streetIcon: String,
        val gameId: Int?
    )

    private var allItems: List<WasteItem> = emptyList()
    private var levelConfig: LevelConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.street_cleanup)

        mediaPlayer = MediaPlayer.create(this, R.raw.street_cleanup_music)
        mediaPlayer?.isLooping = true

        rootLayout = findViewById(android.R.id.content)
        gameArea = findViewById(R.id.gameAreaStreet)
        btnStart = findViewById(R.id.btnStartStreet)
        tvLevel = findViewById(R.id.tvLevel)
        bioBin = findViewById(R.id.binGreenStreet)
        nonBioBin = findViewById(R.id.binBlueStreet)

        findViewById<ImageView>(R.id.btnBackStreet).setOnClickListener {
            timer?.cancel()
            timer = null
            finish()
        }

        findViewById<ImageView>(R.id.btnInfoStreet).setOnClickListener {
            showInstructionDialog()
        }

        level = intent.getIntExtra("extra_level", 1)
        tvLevel.text = when (level) {
            1 -> "Easy"
            2 -> "Medium"
            3 -> "Hard"
            else -> "Easy"
        }

        levelConfig = loadLevelConfig(level)
        allItems = loadItemsFromDb()

        btnStart.setOnClickListener {
            val gameRunning = (timer != null) || (remainingItems > 0)
            if (gameRunning) {
                AlertDialog.Builder(this)
                    .setTitle("Restart game?")
                    .setMessage("Do you want to reset the trash and the timer and start again?")
                    .setPositiveButton("Yes") { _, _ -> startNewGame() }
                    .setNegativeButton("No", null)
                    .show()
            } else {
                startNewGame()
            }
        }

        showInstructionDialog()
        setupBottomBar(BottomBar.Tab.PLAY)
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
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        timer?.cancel()
        timer = null
    }

    private fun loadItemsFromDb(): List<WasteItem> {
        val map = LinkedHashMap<String, WasteItem>()
        try {
            val db = openOrCreateDatabase(dbName, MODE_PRIVATE, null)
            val sql = """
                SELECT wm.image_path, wc.name
                FROM waste_material wm
                JOIN waste_category wc ON wm.category_id = wc.category_id
                WHERE wm.image_path IS NOT NULL AND wm.image_path <> ''
            """.trimIndent()
            val c = db.rawQuery(sql, null)
            val pathIdx = c.getColumnIndex("image_path")
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                val path = c.getString(pathIdx) ?: continue
                if (map.containsKey(path)) continue
                val catName = c.getString(nameIdx) ?: ""
                val isBio = catName.equals("Biodegradable", ignoreCase = true)
                val resId = resources.getIdentifier(path, "drawable", packageName)
                if (resId != 0) {
                    map[path] = WasteItem(path, resId, isBio)
                }
            }
            c.close()
            db.close()
        } catch (_: Exception) {
        }

        if (map.isNotEmpty()) return map.values.toList()

        return listOf(
            WasteItem("ic_trash_banana", R.drawable.ic_trash_banana, true),
            WasteItem("ic_trash_fruit", R.drawable.ic_trash_fruit, true),
            WasteItem("ic_trash_leaf", R.drawable.ic_trash_leaf, true),
            WasteItem("ic_trash_grass", R.drawable.ic_trash_grass, true),
            WasteItem("ic_trash_paper", R.drawable.ic_trash_paper, true),
            WasteItem("ic_trash_tissue", R.drawable.ic_trash_tissue, true),
            WasteItem("ic_trash_plastic_cup", R.drawable.ic_trash_plastic_cup, false),
            WasteItem("ic_trash_bottle", R.drawable.ic_trash_bottle, false),
            WasteItem("ic_trash_wrapper", R.drawable.ic_trash_wrapper, false),
            WasteItem("ic_trash_styro", R.drawable.ic_trash_styro, false)
        )
    }

    private fun loadLevelConfig(level: Int): LevelConfig? {
        val levelName = when (level) {
            1 -> "Easy"
            2 -> "Medium"
            3 -> "Hard"
            else -> "Easy"
        }
        var streetIcon = "street_cleanup"
        var trashCount = 0
        var timerSeconds = 0
        var gameId: Int? = null
        try {
            val db = openOrCreateDatabase(dbName, MODE_PRIVATE, null)
            val offset = (level - 1).coerceAtLeast(0)
            val c = db.rawQuery(
                "SELECT street_icon, trash_count, timer FROM street_cleanup_game ORDER BY rowid LIMIT 1 OFFSET ?",
                arrayOf(offset.toString())
            )
            if (c.moveToFirst()) {
                val streetIdx = c.getColumnIndex("street_icon")
                val countIdx = c.getColumnIndex("trash_count")
                val timerIdx = c.getColumnIndex("timer")
                streetIcon = c.getString(streetIdx) ?: "street_cleanup"
                trashCount = c.getInt(countIdx)
                timerSeconds = c.getInt(timerIdx)
            }
            c.close()

            val g = db.rawQuery(
                "SELECT game_id FROM game WHERE game_title=? AND game_level=? LIMIT 1",
                arrayOf("Street Cleanup", levelName)
            )
            if (g.moveToFirst()) {
                gameId = g.getInt(0)
            }
            g.close()
            db.close()
        } catch (_: Exception) {
        }

        if (trashCount <= 0) {
            trashCount = when (level) {
                1 -> 5
                2 -> 7
                3 -> 10
                else -> 5
            }
        }
        if (timerSeconds <= 0) {
            timerSeconds = when (level) {
                1 -> 90
                2 -> 60
                3 -> 45
                else -> 90
            }
        }
        return LevelConfig(trashCount, timerSeconds, streetIcon, gameId)
    }

    private fun startNewGame() {
        timer?.cancel()
        timer = null
        gameArea.removeAllViews()
        remainingItems = 0
        totalItems = 0
        totalSeconds = 0
        secondsLeft = 0
        correctCount = 0
        wrongCount = 0
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
        if (feedbackCircle == null) feedbackCircle = View(this)
        if (feedbackCircle?.parent == null) {
            val lp = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            lp.topMargin = dp(8)
            gameArea.addView(feedbackCircle, lp)
        }
        feedbackCircle?.alpha = 0f
    }

    private fun showFeedbackCircle(isCorrect: Boolean) {
        val color = if (isCorrect) Color.parseColor("#4CAF50") else Color.parseColor("#EF5350")
        val d = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
        feedbackCircle?.background = d
        feedbackCircle?.animate()?.alpha(1f)?.setDuration(150)?.withEndAction {
            feedbackCircle?.animate()?.alpha(0f)?.setStartDelay(400)?.setDuration(200)?.start()
        }?.start()
    }

    private fun populateTrash() {
        val cfg = levelConfig
        val count = if (cfg != null && cfg.trashCount > 0) {
            cfg.trashCount
        } else {
            when (level) {
                1 -> 5
                2 -> 7
                3 -> 10
                else -> 5
            }
        }

        val source = if (allItems.size >= count) allItems else loadItemsFromDb()
        val selected = source.shuffled().take(count)

        remainingItems = selected.size
        totalItems = remainingItems

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
        val cfg = levelConfig
        val seconds = if (cfg != null && cfg.timerSeconds > 0) {
            cfg.timerSeconds
        } else {
            when (level) {
                1 -> 90
                2 -> 60
                3 -> 45
                else -> 90
            }
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

    private fun handleDrop(view: View, dropRawX: Int, dropRawY: Int, startX: Float, startY: Float) {
        val item = view.tag as? WasteItem ?: return
        val bioRect = Rect()
        val nonBioRect = Rect()
        bioBin.getGlobalVisibleRect(bioRect)
        nonBioBin.getGlobalVisibleRect(nonBioRect)
        val droppedOnBio = bioRect.contains(dropRawX, dropRawY)
        val droppedOnNonBio = nonBioRect.contains(dropRawX, dropRawY)

        if (!droppedOnBio && !droppedOnNonBio) {
            view.animate().x(startX).y(startY).setDuration(200).start()
            Toast.makeText(this, "Put the trash inside the GREEN or BLUE bin.", Toast.LENGTH_SHORT).show()
            return
        }

        val isCorrect = (item.isBiodegradable && droppedOnBio) || (!item.isBiodegradable && droppedOnNonBio)
        showFeedbackCircle(isCorrect)

        gameArea.removeView(view)
        remainingItems--

        if (isCorrect) {
            correctCount++
        } else {
            wrongCount++
            Toast.makeText(this, "Oops, wrong bin. Try again on the next trash.", Toast.LENGTH_SHORT).show()
        }

        if (remainingItems <= 0) {
            timer?.cancel()
            timer = null
            showSuccessDialog()
        }
    }

    private fun showFloodAnimation() {
        if (floodView == null) {
            floodView = View(this).apply { setBackgroundColor(Color.parseColor("#8800B0FF")) }
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
            floodView?.animate()?.translationY(0f)?.alpha(1f)?.setDuration(1200)?.start()
        }
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
            val streetIconVal = levelConfig?.streetIcon ?: "street_cleanup"
            val values = ContentValues().apply {
                put("total_score", totalScore)
                put("correct_count", correct)
                put("wrong_count", wrong)
                put("game", streetIconVal)
            }
            db.insert("game_result", null, values)

            val levelName = when (level) {
                1 -> "Easy"
                2 -> "Medium"
                3 -> "Hard"
                else -> "Easy"
            }
            val upd = ContentValues().apply {
                put("current_score", correct)
            }
            db.update(
                "game",
                upd,
                "game_title=? AND game_level=?",
                arrayOf("Street Cleanup", levelName)
            )

            db.close()
        } catch (_: Exception) {
        }
    }

    private fun showSuccessDialog() {
        val levelName = when (level) {
            1 -> "Easy"
            2 -> "Medium"
            3 -> "Hard"
            else -> "Easy"
        }
        val scoreText = "Correct: $correctCount  |  Wrong: $wrongCount"
        val timeText = "Time left: $secondsLeft second(s)"

        saveGameResultToDb(
            totalScore = correctCount,
            correct = correctCount,
            wrong = wrongCount
        )

        AlertDialog.Builder(this)
            .setTitle("Level complete!")
            .setMessage("You finished the $levelName level!\n\n$scoreText\n$timeText")
            .setCancelable(false)
            .setPositiveButton("Play again") { _, _ -> startNewGame() }
            .setNegativeButton("Back") { _, _ -> finish() }
            .show()
    }

    private fun showFailDialog() {
        showFloodAnimation()
        val scoreText = "Correct: $correctCount  |  Wrong: $wrongCount"
        val timeUsed = totalSeconds - secondsLeft
        val timeText = "Time used: $timeUsed second(s)"

        saveGameResultToDb(
            totalScore = correctCount,
            correct = correctCount,
            wrong = wrongCount + remainingItems
        )

        AlertDialog.Builder(this)
            .setTitle("Oh no!")
            .setMessage("The drains got blocked and the street flooded because some trash was left.\n\n$scoreText\n$timeText\n\nTry again and clean everything before the time runs out!")
            .setCancelable(false)
            .setPositiveButton("Try again") { _, _ -> startNewGame() }
            .setNegativeButton("Back") { _, _ -> finish() }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showInstructionDialog() {
        val fullMessage = "Hello, street cleaner!\n\n" +
                "1. Look for the trash on the street.\n" +
                "2. Drag the trash and drop it on the correct bin.\n" +
                "3. GREEN bin = Biodegradable (food, fruit, leaves, paper).\n" +
                "4. BLUE bin = Non-biodegradable (plastic, bottles, wrappers).\n" +
                "5. Clean all trash before the time is up!"

        AlertDialog.Builder(this)
            .setTitle("How to play")
            .setMessage(fullMessage)
            .setPositiveButton("Got it!") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
