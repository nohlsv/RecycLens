package com.example.recyclens

import android.content.ContentValues
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.random.Random

class StreetCleanupActivity : AppCompatActivity() {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile
    private var isActivityInForeground = false

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
    private var totalItems = 0
    private var totalSeconds = 0
    private var secondsLeft = 0
    private var correctCount = 0
    private var wrongCount = 0
    private var roundStartTriggered = false

    private val startRoundFallback = Runnable {
        beginRoundIfNeeded()
    }

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

        rootLayout = findViewById(android.R.id.content)
        gameArea = findViewById(R.id.gameAreaStreet)
        btnStart = findViewById(R.id.btnStartStreet)
        tvLevel = findViewById(R.id.tvLevel)
        bioBin = findViewById(R.id.binGreenStreet)
        nonBioBin = findViewById(R.id.binBlueStreet)

        tvTimer = TextView(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
        }
        val lpTimer = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
        lpTimer.topMargin = dp(12)
        lpTimer.marginEnd = dp(12)
        gameArea.addView(tvTimer, lpTimer)
        tvTimer.text = "Time: 00"

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        MusicManager.pause()
                    }
                    override fun onDone(utteranceId: String?) {
                        runOnUiThread {
                            if (!isActivityInForeground || isFinishing || isDestroyed) return@runOnUiThread
                            MusicManager.start(this@StreetCleanupActivity, "street_cleanup_music")
                            if (utteranceId == "STREET_START_GAME") {
                                beginRoundIfNeeded()
                            }
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            if (!isActivityInForeground || isFinishing || isDestroyed) return@runOnUiThread
                            MusicManager.start(this@StreetCleanupActivity, "street_cleanup_music")
                            if (utteranceId == "STREET_START_GAME") {
                                beginRoundIfNeeded()
                            }
                        }
                    }
                })
                ttsReady = true
                showInstructionDialog(1)
            }
        }

        findViewById<ImageView>(R.id.btnBackStreet).setOnClickListener {
            stopTts()
            finish()
        }

        findViewById<ImageView>(R.id.btnInfoStreet).setOnClickListener {
            stopTts()
            showInstructionDialog(1)
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

        btnStart.text = "Start"
        btnStart.setOnClickListener {
            val gameRunning = (timer != null) || (remainingItems > 0)
            if (gameRunning) {
                AlertDialog.Builder(this)
                    .setTitle("Restart game?")
                    .setMessage("Do you want to reset the trash and the timer and start again?")
                    .setPositiveButton("Yes") { _, _ ->
                        stopTts()
                        startNewGame()
                    }
                    .setNegativeButton("No", null)
                    .show()
            } else {
                stopTts()
                startNewGame()
            }
        }

        setupBottomBar(BottomBar.Tab.PLAY)
    }

    override fun onResume() {
        super.onResume()
        isActivityInForeground = true
        MusicManager.start(this, "street_cleanup_music")
    }

    override fun onPause() {
        isActivityInForeground = false
        super.onPause()
        gameArea.removeCallbacks(startRoundFallback)
        MusicManager.pause()
        stopTts()
    }

    override fun onDestroy() {
        super.onDestroy()
        gameArea.removeCallbacks(startRoundFallback)
        stopTts()
        tts?.shutdown()
        tts = null
        timer?.cancel()
        timer = null
    }

    private fun speak(text: String, utteranceId: String) {
        if (!ttsReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun stopTts() {
        if (ttsReady) {
            tts?.stop()
        }
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
        gameArea.removeCallbacks(startRoundFallback)
        roundStartTriggered = false
        gameArea.removeAllViews()
        gameArea.addView(tvTimer)
        tvTimer.text = "Time: 00"
        remainingItems = 0
        totalItems = 0
        totalSeconds = 0
        secondsLeft = 0
        correctCount = 0
        wrongCount = 0
        floodView?.let { rootLayout.removeView(it) }
        floodView = null
        btnStart.text = "Time running..."

        val intro = "Street cleanup game started. Drag each trash item into the correct bin. Biodegradable trash goes into the green bin, and non biodegradable trash goes into the blue bin. Clean the street before the time runs out."
        if (ttsReady) {
            speak(intro, "STREET_START_GAME")
            gameArea.postDelayed(startRoundFallback, 7000L)
        } else {
            beginRoundIfNeeded()
        }
    }

    private fun beginRoundIfNeeded() {
        if (roundStartTriggered) return
        if (!isActivityInForeground || isFinishing || isDestroyed) return
        roundStartTriggered = true
        gameArea.removeCallbacks(startRoundFallback)
        gameArea.post {
            createFeedbackCircle()
            populateTrash()
            startTimer()
        }
    }

    private fun createFeedbackCircle() {
        if (feedbackCircle == null) {
            feedbackCircle = View(this)
            val lp = FrameLayout.LayoutParams(dp(24), dp(24), Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            lp.topMargin = dp(8)
            gameArea.addView(feedbackCircle, lp)
        }
        feedbackCircle?.alpha = 0f
    }

    private fun showFeedbackCircle(isCorrect: Boolean) {
        val color = if (isCorrect) Color.parseColor("#4CAF50") else Color.parseColor("#EF5350")
        val d = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
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
            val randomX = Random.nextInt(minX, maxXExclusive)
            val randomY = Random.nextInt(minY, maxYExclusive)
            lp.leftMargin = randomX
            lp.topMargin = randomY
            gameArea.addView(iv, lp)
            attachDragListener(iv)
        }
        btnStart.text = "Time running..."
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
        tvTimer.text = String.format("Time: %02d", seconds)
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
                btnStart.text = "Start"
                showFloodAnimation {
                    showFailDialog()
                }
            }
        }.start()
    }

    private fun attachDragListener(view: ImageView) {
        var dX = 0f
        var dY = 0f
        var startX = 0f
        var startY = 0f
        val gameAreaLocation = IntArray(2)
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    gameArea.getLocationOnScreen(gameAreaLocation)
                    val touchX = event.rawX - gameAreaLocation[0]
                    val touchY = event.rawY - gameAreaLocation[1]
                    startX = v.x
                    startY = v.y
                    dX = v.x - touchX
                    dY = v.y - touchY
                    v.bringToFront()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val touchX = event.rawX - gameAreaLocation[0]
                    val touchY = event.rawY - gameAreaLocation[1]
                    val maxX = (gameArea.width - v.width).toFloat().coerceAtLeast(0f)
                    val bottomOverflow = dp(180).toFloat()
                    val maxY = (gameArea.height + bottomOverflow - v.height).coerceAtLeast(0f)
                    v.x = (touchX + dX).coerceIn(0f, maxX)
                    v.y = (touchY + dY).coerceIn(0f, maxY)
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
            val name = getTrashDisplayName(item)
            view.animate().x(startX).y(startY).setDuration(200).start()
            Toast.makeText(
                this,
                "Drag $name into the correct bin.",
                Toast.LENGTH_SHORT
            ).show()
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
            val name = getTrashDisplayName(item)
            val correctBin = getCorrectBinText(item)
            Toast.makeText(
                this,
                "$name should go to the $correctBin.",
                Toast.LENGTH_SHORT
            ).show()
        }

        if (remainingItems <= 0) {
            timer?.cancel()
            timer = null
            evaluateFinalScore()
        }
    }

    private fun evaluateFinalScore() {
        if (totalItems <= 0) {
            btnStart.text = "Start"
            showSuccessDialog()
            return
        }

        val accuracy = correctCount.toFloat() / totalItems.toFloat()
        btnStart.text = "Start"

        if (accuracy < 0.5f) {
            showFloodAnimation {
                showLowScoreDialog()
            }
        } else {
            showSuccessDialog()
        }
    }

    private fun showFloodAnimation(onEnd: (() -> Unit)? = null) {
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
                ?.withEndAction {
                    onEnd?.invoke()
                }
                ?.start()
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

        val speakText = "Good work! You finished the $levelName level with $correctCount correct and $wrongCount wrong. You had $secondsLeft seconds left."
        speak(speakText, "STREET_RESULT_SUCCESS")
        btnStart.text = "Start"

        AlertDialog.Builder(this)
            .setTitle("Level complete!")
            .setMessage("You finished the $levelName level!\n\n$scoreText\n$timeText")
            .setCancelable(false)
            .setPositiveButton("Play again") { _, _ ->
                stopTts()
                startNewGame()
            }
            .setNegativeButton("Back") { _, _ ->
                stopTts()
                finish()
            }
            .show()
    }

    private fun showFailDialog() {
        val scoreText = "Correct: $correctCount  |  Wrong: $wrongCount"
        val timeUsed = totalSeconds - secondsLeft
        val timeText = "Time used: $timeUsed second(s)"

        saveGameResultToDb(
            totalScore = correctCount,
            correct = correctCount,
            wrong = wrongCount + remainingItems
        )

        val speakText = "Oh no. Time is up. You got $correctCount correct and $wrongCount wrong. Try again and clean everything before the time runs out."
        speak(speakText, "STREET_RESULT_FAIL_TIME")
        btnStart.text = "Start"

        AlertDialog.Builder(this)
            .setTitle("Oh no!")
            .setMessage(
                "The drains got blocked and the street flooded because some trash was left.\n\n" +
                        "$scoreText\n$timeText\n\nTry again and clean everything before the time runs out!"
            )
            .setCancelable(false)
            .setPositiveButton("Try again") { _, _ ->
                stopTts()
                startNewGame()
            }
            .setNegativeButton("Back") { _, _ ->
                stopTts()
                finish()
            }
            .show()
    }

    private fun showLowScoreDialog() {
        val scoreText = "Correct: $correctCount  |  Wrong: $wrongCount"
        val itemsText = "You sorted all $totalItems items."

        saveGameResultToDb(
            totalScore = correctCount,
            correct = correctCount,
            wrong = wrongCount
        )

        val speakText = "You sorted all $totalItems items, but some went into the wrong bin. You got $correctCount correct and $wrongCount wrong. Try again to improve your score."
        speak(speakText, "STREET_RESULT_FAIL_LOW")
        btnStart.text = "Start"

        AlertDialog.Builder(this)
            .setTitle("Oh no!")
            .setMessage(
                "Some trash went into the wrong bin, so the drains still got blocked and the street flooded.\n\n" +
                        "$scoreText\n$itemsText"
            )
            .setCancelable(false)
            .setPositiveButton("Try again") { _, _ ->
                stopTts()
                startNewGame()
            }
            .setNegativeButton("Back") { _, _ ->
                stopTts()
                finish()
            }
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun getTrashDisplayName(item: WasteItem): String {
        return when (item.resName) {
            "ic_trash_banana" -> "Banana Peel"
            "ic_trash_fruit" -> "Fruit"
            "ic_trash_leaf" -> "Leaf"
            "ic_trash_grass" -> "Grass"
            "ic_trash_paper" -> "Paper"
            "ic_trash_tissue" -> "Tissue"
            "ic_trash_plastic_cup" -> "Plastic Cup"
            "ic_trash_bottle" -> "Plastic Bottle"
            "ic_trash_wrapper" -> "Candy Wrapper"
            "ic_trash_styro" -> "Styrofoam Box"
            else -> "This trash"
        }
    }

    private fun getCorrectBinText(item: WasteItem): String {
        return if (item.isBiodegradable) {
            "green biodegradable bin"
        } else {
            "blue non biodegradable bin"
        }
    }

    private fun showInstructionDialog(page: Int = 1) {
        val title = if (page == 1) {
            "Biodegradable"
        } else {
            "Non-biodegradable"
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setView(createInstructionContent(page))

        if (page == 1) {
            builder.setPositiveButton("Next (Non-bio)") { dialog, _ ->
                stopTts()
                dialog.dismiss()
                showInstructionDialog(2)
            }
            builder.setNegativeButton("Close") { dialog, _ ->
                stopTts()
                dialog.dismiss()
            }
        } else {
            builder.setPositiveButton("Back (Bio)") { dialog, _ ->
                stopTts()
                dialog.dismiss()
                showInstructionDialog(1)
            }
            builder.setNegativeButton("Done") { dialog, _ ->
                stopTts()
                dialog.dismiss()
            }
        }

        builder.show()

        val text = if (page == 1) {
            "Biodegradable items, like food waste, paper, and leaves, should be placed in the green bin so they can decompose properly."
        } else {
            "Non biodegradable items, like plastic, bottles, and styrofoam, should be placed in the blue bin so they do not pollute and block the drains."
        }
        speak(text, if (page == 1) "STREET_INFO_1" else "STREET_INFO_2")
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
            val binView = if (page == 1) bioBin else nonBioBin
            if (binView is ImageView) {
                setImageDrawable(binView.drawable)
            } else {
                background = binView.background
            }
        }

        val binLabel = TextView(this).apply {
            text = if (page == 1) {
                "Drop these into this green bin"
            } else {
                "Drop these into this blue bin"
            }
            textSize = 15f
            setPadding(dp(12), 0, 0, 0)
        }

        headerRow.addView(binIcon)
        headerRow.addView(binLabel)
        layout.addView(headerRow)

        val subtitle = TextView(this).apply {
            text = if (page == 1) {
                "These items belong to the biodegradable bin:"
            } else {
                "These items belong to the non-biodegradable bin:"
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
                this.text = label
                textSize = 14f
                setPadding(dp(12), 0, 0, 0)
            }

            row.addView(icon)
            row.addView(text)
            layout.addView(row)
        }

        val hint = TextView(this).apply {
            text = "\nDuring the game, drag each piece of trash into the correct bin before the time runs out."
            textSize = 13f
        }
        layout.addView(hint)

        return layout
    }
}
