package com.example.recyclens

import android.content.ContentValues
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
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
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

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

    private lateinit var langToggle: RelativeLayout
    private lateinit var langText: TextView
    private lateinit var labelScan: TextView
    private lateinit var labelPlay: TextView

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

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wasMusicPlayingBeforeTts = false
    private var isEnglish = true

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

        langToggle = findViewById(R.id.langToggle)
        langText = findViewById(R.id.langText)
        labelScan = findViewById(R.id.labelScan)
        labelPlay = findViewById(R.id.labelPlay)

        findViewById<ImageView>(R.id.btnBackStreet).setOnClickListener {
            stopSpeech()
            timer?.cancel()
            timer = null
            finish()
        }

        findViewById<ImageView>(R.id.btnInfoStreet).setOnClickListener {
            stopSpeech()
            if (ttsReady) {
                showInstructionDialog(1)
            } else {
                Toast.makeText(this, if (isEnglish) "Voice guide is starting, please wait." else "Nagsisimula ang voice guide, pakihintay sandali.", Toast.LENGTH_SHORT).show()
            }
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
                stopSpeech()
                AlertDialog.Builder(this)
                    .setTitle(if (isEnglish) "Restart game?" else "Ulitin ang laro?")
                    .setMessage(if (isEnglish) "Do you want to reset the trash and the timer and start again?" else "Gusto mo bang i-reset ang basura at oras at magsimula muli?")
                    .setPositiveButton(if (isEnglish) "Yes" else "Oo") { _, _ -> startNewGame() }
                    .setNegativeButton(if (isEnglish) "No" else "Hindi", null)
                    .show()
            } else {
                startNewGame()
            }
        }

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
                showInstructionDialog(1)
            } else {
                ttsReady = false
            }
        }

        setupBottomBar(BottomBar.Tab.PLAY)
        setupLanguageToggle()
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
        stopSpeech()
        tts?.shutdown()
        tts = null
    }

    private fun setupLanguageToggle() {
        updateLanguageTexts()
        langToggle.setOnClickListener {
            isEnglish = !isEnglish
            updateLanguageTexts()
            if (ttsReady) {
                updateTtsLanguage()
            }
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
        val locale = if (isEnglish) Locale.US else Locale.forLanguageTag("fil-PH")
        var result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = engine.setLanguage(Locale.US)
        }
    }

    private fun speak(textEn: String, textTl: String, id: String = "STREET_TTS") {
        val engine = tts ?: return
        if (!ttsReady) return
        val text = if (isEnglish) textEn else textTl
        if (text.isBlank()) return
        wasMusicPlayingBeforeTts = mediaPlayer?.isPlaying == true
        if (wasMusicPlayingBeforeTts) {
            mediaPlayer?.pause()
        }
        engine.stop()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    private fun stopSpeech() {
        val engine = tts ?: return
        engine.stop()
        if (wasMusicPlayingBeforeTts && mediaPlayer != null) {
            if (mediaPlayer?.isPlaying == false) {
                mediaPlayer?.start()
            }
        }
        wasMusicPlayingBeforeTts = false
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
        stopSpeech()
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
            val en = "Drag each trash into the correct bin before time runs out. " +
                    "Biodegradable items like banana peel, fruit, leaf, grass, paper, and tissue go in the green bin. " +
                    "Non-biodegradable items like plastic cup, plastic bottle, candy wrapper, and styrofoam box go in the blue bin."
            val tl = "Hilahin ang bawat basura papunta sa tamang basurahan bago maubos ang oras. " +
                    "Ang mga nabubulok na basura tulad ng balat ng saging, prutas, dahon, damo, papel, at tisyu ay ilagay sa berdeng basurahan. " +
                    "Ang mga di-nabubulok tulad ng plastic cup, plastic bottle, candy wrapper, at styrofoam box ay ilagay sa asul na basurahan."
            speak(en, tl)
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
            val randomX = kotlin.random.Random.nextInt(minX, maxXExclusive)
            val randomY = kotlin.random.Random.nextInt(minY, maxYExclusive)
            lp.leftMargin = randomX
            lp.topMargin = randomY
            gameArea.addView(iv, lp)
            attachDragListener(iv)
        }
        btnStart.text = if (isEnglish) "Time running…" else "Umiikot ang oras…"
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
            val en = "Drag the trash into the green or blue bin."
            val tl = "Hilahin ang basura papunta sa berdeng o asul na basurahan."
            view.animate().x(startX).y(startY).setDuration(200).start()
            Toast.makeText(this, if (isEnglish) en else tl, Toast.LENGTH_SHORT).show()
            speak(en, tl)
            return
        }

        val isCorrect = (item.isBiodegradable && droppedOnBio) || (!item.isBiodegradable && droppedOnNonBio)
        showFeedbackCircle(isCorrect)

        gameArea.removeView(view)
        remainingItems--

        if (isCorrect) {
            correctCount++
            val en = if (item.isBiodegradable) "Correct. This should go in the green bin." else "Correct. This should go in the blue bin."
            val tl = if (item.isBiodegradable) "Tama. Dapat ito ay nasa berdeng basurahan." else "Tama. Dapat ito ay nasa asul na basurahan."
            speak(en, tl)
        } else {
            wrongCount++
            val name = getTrashDisplayName(item)
            val en = if (item.isBiodegradable) "$name should go in the green bin." else "$name should go in the blue bin."
            val tl = if (item.isBiodegradable) "Dapat ilagay ang $name sa berdeng basurahan." else "Dapat ilagay ang $name sa asul na basurahan."
            Toast.makeText(this, if (isEnglish) en else tl, Toast.LENGTH_SHORT).show()
            speak(en, tl)
        }

        if (remainingItems <= 0) {
            timer?.cancel()
            timer = null
            evaluateFinalScore()
        }
    }

    private fun evaluateFinalScore() {
        if (totalItems <= 0) {
            showSuccessDialog()
            return
        }

        val accuracy = if (totalItems > 0) correctCount.toFloat() / totalItems.toFloat() else 0f

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

        val en = "Good job. You finished the $levelName level. $scoreText. $timeText."
        val tl = "Magaling. Natapos mo ang $levelName na antas. $scoreText. $timeText."
        speak(en, tl)

        saveGameResultToDb(
            totalScore = correctCount,
            correct = correctCount,
            wrong = wrongCount
        )

        AlertDialog.Builder(this)
            .setTitle(if (isEnglish) "Level complete!" else "Tapos na ang level!")
            .setMessage(
                (if (isEnglish) "You finished the $levelName level!" else "Natapos mo ang $levelName na antas!") +
                        "\n\n$scoreText\n$timeText"
            )
            .setCancelable(false)
            .setPositiveButton(if (isEnglish) "Play again" else "Maglaro muli") { _, _ ->
                stopSpeech()
                startNewGame()
            }
            .setNegativeButton(if (isEnglish) "Back" else "Bumalik") { _, _ ->
                stopSpeech()
                finish()
            }
            .show()
    }

    private fun showFailDialog() {
        val scoreText = "Correct: $correctCount  |  Wrong: $wrongCount"
        val timeUsed = totalSeconds - secondsLeft
        val timeText = "Time used: $timeUsed second(s)"

        val en = "Oh no. Time is up and some trash was left on the street. The drains got blocked and the street flooded. $scoreText. $timeText. Try again and clean everything before the time runs out."
        val tl = "Naku. Naubos na ang oras at may naiwan pang basura sa kalsada. Nabara ang kanal at bumaha. $scoreText. $timeText. Subukan muli at linisin lahat bago maubos ang oras."
        speak(en, tl)

        saveGameResultToDb(
            totalScore = correctCount,
            correct = correctCount,
            wrong = wrongCount + remainingItems
        )

        AlertDialog.Builder(this)
            .setTitle(if (isEnglish) "Oh no!" else "Naku!")
            .setMessage(
                (if (isEnglish)
                    "The drains got blocked and the street flooded because some trash was left."
                else
                    "Nabara ang kanal at bumaha dahil may naiwan pang basura.") +
                        "\n\n$scoreText\n$timeText\n\n" +
                        if (isEnglish)
                            "Try again and clean everything before the time runs out!"
                        else
                            "Subukan muli at linisin lahat bago maubos ang oras!"
            )
            .setCancelable(false)
            .setPositiveButton(if (isEnglish) "Try again" else "Subukan muli") { _, _ ->
                stopSpeech()
                startNewGame()
            }
            .setNegativeButton(if (isEnglish) "Back" else "Bumalik") { _, _ ->
                stopSpeech()
                finish()
            }
            .show()
    }

    private fun showLowScoreDialog() {
        val scoreText = "Correct: $correctCount  |  Wrong: $wrongCount"
        val itemsText = "You sorted all $totalItems items."

        val en = "Oh no. Some trash went into the wrong bin so the street still flooded. $scoreText. $itemsText."
        val tl = "Naku. May mga basura na napunta sa maling basurahan kaya bumaha pa rin sa kalsada. $scoreText. $itemsText."
        speak(en, tl)

        saveGameResultToDb(
            totalScore = correctCount,
            correct = correctCount,
            wrong = wrongCount
        )

        AlertDialog.Builder(this)
            .setTitle(if (isEnglish) "Oh no!" else "Naku!")
            .setMessage(
                (if (isEnglish)
                    "Some trash went into the wrong bin, so the drains still got blocked and the street flooded."
                else
                    "May mga basura na napunta sa maling basurahan kaya nabara pa rin ang kanal at bumaha.") +
                        "\n\n$scoreText\n$itemsText"
            )
            .setCancelable(false)
            .setPositiveButton(if (isEnglish) "Try again" else "Subukan muli") { _, _ ->
                stopSpeech()
                startNewGame()
            }
            .setNegativeButton(if (isEnglish) "Back" else "Bumalik") { _, _ ->
                stopSpeech()
                finish()
            }
            .show()
    }

    private fun showInstructionDialog(page: Int = 1) {
        val titleEn = if (page == 1) "Biodegradable – GREEN bin" else "Non-biodegradable – BLUE bin"
        val titleTl = if (page == 1) "Nabubulok – BERDENG basurahan" else "Di-nabubulok – ASUL na basurahan"
        val builder = AlertDialog.Builder(this)
            .setTitle(if (isEnglish) titleEn else titleTl)
            .setView(createInstructionContent(page))

        if (page == 1) {
            val en = "Biodegradable items like banana peel, fruit, leaf, grass, paper, and tissue should be dropped into the green bin."
            val tl = "Ang mga nabubulok na basura tulad ng balat ng saging, prutas, dahon, damo, papel, at tisyu ay dapat ilagay sa berdeng basurahan."
            speak(en, tl)
            builder.setPositiveButton(if (isEnglish) "Next (Non-bio)" else "Susunod (Di-nabubulok)") { dialog, _ ->
                stopSpeech()
                dialog.dismiss()
                showInstructionDialog(2)
            }
            builder.setNegativeButton(if (isEnglish) "Close" else "Isara") { dialog, _ ->
                stopSpeech()
                dialog.dismiss()
            }
        } else {
            val en = "Non-biodegradable items like plastic cup, plastic bottle, candy wrapper, and styrofoam box should be dropped into the blue bin."
            val tl = "Ang mga di-nabubulok na basura tulad ng plastic cup, plastic bottle, candy wrapper, at styrofoam box ay dapat ilagay sa asul na basurahan."
            speak(en, tl)
            builder.setPositiveButton(if (isEnglish) "Back (Bio)" else "Bumalik (Nabubulok)") { dialog, _ ->
                stopSpeech()
                dialog.dismiss()
                showInstructionDialog(1)
            }
            builder.setNegativeButton(if (isEnglish) "Done" else "Tapos") { dialog, _ ->
                stopSpeech()
                dialog.dismiss()
            }
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
            val binView = if (page == 1) bioBin else nonBioBin
            if (binView is ImageView) {
                setImageDrawable(binView.drawable)
            } else {
                background = binView.background
            }
        }

        val binLabel = TextView(this).apply {
            text = if (page == 1) {
                if (isEnglish) "Drop these into this GREEN bin" else "Ilagay ang mga ito sa BERDENG basurahan"
            } else {
                if (isEnglish) "Drop these into this BLUE bin" else "Ilagay ang mga ito sa ASUL na basurahan"
            }
            textSize = 15f
            setPadding(dp(12), 0, 0, 0)
        }

        headerRow.addView(binIcon)
        headerRow.addView(binLabel)
        layout.addView(headerRow)

        val subtitle = TextView(this).apply {
            text = if (page == 1) {
                if (isEnglish) "These belong to the biodegradable bin:" else "Ang mga ito ay nabubulok na basura:"
            } else {
                if (isEnglish) "These belong to the non-biodegradable bin:" else "Ang mga ito ay di-nabubulok na basura:"
            }
            textSize = 14f
        }
        layout.addView(subtitle)

        val items = if (page == 1) {
            listOf(
                R.drawable.ic_trash_banana to if (isEnglish) "Banana Peel" else "Balat ng Saging",
                R.drawable.ic_trash_fruit to if (isEnglish) "Fruit" else "Prutas",
                R.drawable.ic_trash_leaf to if (isEnglish) "Leaf" else "Dahon",
                R.drawable.ic_trash_grass to if (isEnglish) "Grass" else "Damo",
                R.drawable.ic_trash_paper to if (isEnglish) "Paper" else "Papel",
                R.drawable.ic_trash_tissue to if (isEnglish) "Tissue" else "Tisyu"
            )
        } else {
            listOf(
                R.drawable.ic_trash_plastic_cup to if (isEnglish) "Plastic Cup" else "Plastic Cup",
                R.drawable.ic_trash_bottle to if (isEnglish) "Plastic Bottle" else "Plastic Bottle",
                R.drawable.ic_trash_wrapper to if (isEnglish) "Candy Wrapper" else "Candy Wrapper",
                R.drawable.ic_trash_styro to if (isEnglish) "Styrofoam Box" else "Styrofoam Box"
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
            text = if (isEnglish) "\nDrag each trash into this bin during the game." else "\nHilahin ang bawat basura papunta sa basurahang ito habang naglalaro."
            textSize = 13f
        }
        layout.addView(hint)

        return layout
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun getTrashDisplayName(item: WasteItem): String {
        return when (item.resName) {
            "ic_trash_banana" -> if (isEnglish) "Banana Peel" else "Balat ng Saging"
            "ic_trash_fruit" -> if (isEnglish) "Fruit" else "Prutas"
            "ic_trash_leaf" -> if (isEnglish) "Leaf" else "Dahon"
            "ic_trash_grass" -> if (isEnglish) "Grass" else "Damo"
            "ic_trash_paper" -> if (isEnglish) "Paper" else "Papel"
            "ic_trash_tissue" -> if (isEnglish) "Tissue" else "Tisyu"
            "ic_trash_plastic_cup" -> "Plastic Cup"
            "ic_trash_bottle" -> "Plastic Bottle"
            "ic_trash_wrapper" -> "Candy Wrapper"
            "ic_trash_styro" -> "Styrofoam Box"
            else -> if (isEnglish) "This trash" else "Basurang ito"
        }
    }
}
