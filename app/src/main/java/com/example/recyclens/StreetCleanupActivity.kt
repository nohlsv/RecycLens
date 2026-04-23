package com.example.recyclens

import android.content.ContentValues
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.recyclens.data.WasteCatalog
import com.example.recyclens.data.db.AppDatabase
import com.example.recyclens.data.db.GameDbContract
import com.example.recyclens.data.db.GameDatabase
import java.util.Locale
import kotlin.random.Random

class StreetCleanupActivity : AppCompatActivity(), BottomBar.LanguageAware {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile
    private var isActivityInForeground = false

    private lateinit var rootLayout: ViewGroup
    private var floodView: View? = null
    private lateinit var feedbackBanner: RecyclensFeedbackBanner
    private lateinit var styledDialog: RecyclensDialog

    private lateinit var gameArea: FrameLayout
    private lateinit var btnStart: TextView
    private lateinit var titleStreet: TextView
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
        LanguagePrefs.applyLocale(this)
        setContentView(R.layout.street_cleanup)

        feedbackBanner = RecyclensFeedbackBanner(this)
        styledDialog = RecyclensDialog(this)

        rootLayout = findViewById(android.R.id.content)
        rootLayout.setBackgroundResource(R.drawable.trash_sorting_game_background)
        gameArea = findViewById(R.id.gameAreaStreet)
        btnStart = findViewById(R.id.btnStartStreet)
        titleStreet = findViewById(R.id.titleStreet)
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
        tvTimer.text = getString(R.string.label_time_zero)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                updateTtsLanguage()
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
        applyLevelBadgeText()
        refreshLocalizedTexts()

        AppDatabase.getInstance(applicationContext)
        levelConfig = loadLevelConfig(level)
        allItems = loadItemsFromDb()

        btnStart.text = getString(R.string.start_button)
        btnStart.setOnClickListener {
            val gameRunning = (timer != null) || (remainingItems > 0)
            if (gameRunning) {
                styledDialog.show(
                    title = getString(R.string.dialog_restart_game_title),
                    message = getString(R.string.dialog_restart_game_message),
                    positiveText = getString(R.string.action_yes),
                    negativeText = getString(R.string.action_no),
                    tone = RecyclensDialog.Tone.INFO,
                    onPositive = {
                        stopTts()
                        startNewGame()
                    }
                )
            } else {
                stopTts()
                startNewGame()
            }
        }

        setupBottomBar(BottomBar.Tab.PLAY)
        RecyclensEntryAnimator.play(this)
    }

    override fun onLanguageChanged() {
        refreshLocalizedTexts()
    }

    private fun refreshLocalizedTexts() {
        titleStreet.text = getString(R.string.game_street_cleanup)
        applyLevelBadgeText()

        btnStart.text = if ((timer != null) || (remainingItems > 0)) {
            getString(R.string.status_time_running)
        } else {
            getString(R.string.start_button)
        }

        tvTimer.text = if (timer != null) {
            getString(R.string.time_format, secondsLeft)
        } else {
            getString(R.string.label_time_zero)
        }

        if (ttsReady) {
            updateTtsLanguage()
        }
    }

    private fun updateTtsLanguage() {
        val engine = tts ?: return
        val isEnglish = LanguagePrefs.isEnglish(this)

        var result = if (isEnglish) {
            engine.setLanguage(Locale.US)
        } else {
            engine.setLanguage(Locale.forLanguageTag("fil-PH"))
        }

        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            result = if (isEnglish) {
                engine.setLanguage(Locale.US)
            } else {
                engine.setLanguage(Locale.forLanguageTag("tl-PH"))
            }
        }

        if (!isEnglish) {
            val tagalogVoice = engine.voices?.firstOrNull { v ->
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

    private fun applyLevelBadgeText() {
        when (level) {
            1 -> {
                tvLevel.text = getString(R.string.label_easy)
                tvLevel.setBackgroundResource(R.drawable.level_bg_easy)
            }
            2 -> {
                tvLevel.text = getString(R.string.label_medium)
                tvLevel.setBackgroundResource(R.drawable.level_bg_medium)
            }
            3 -> {
                tvLevel.text = getString(R.string.label_hard)
                tvLevel.setBackgroundResource(R.drawable.level_bg_hard)
            }
            else -> {
                tvLevel.text = getString(R.string.label_easy)
                tvLevel.setBackgroundResource(R.drawable.level_bg_easy)
            }
        }
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
        BackgroundDriftHelper.detach(this)
        gameArea.removeCallbacks(startRoundFallback)
        stopTts()
        tts?.shutdown()
        tts = null
        timer?.cancel()
        timer = null
        feedbackBanner.release()
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
            val db = GameDatabase.open(this)
            val sql = """
                SELECT wm.image_path, wc.name_en AS category_name_en
                FROM waste_material wm
                JOIN waste_category wc ON wm.category_id = wc.category_id
                WHERE wm.image_path IS NOT NULL AND wm.image_path <> ''
            """.trimIndent()
            val c = db.rawQuery(sql, null)
            val pathIdx = c.getColumnIndexOrThrow("image_path")
            val nameIdx = c.getColumnIndexOrThrow("category_name_en")
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
        } catch (e: Exception) {
            Log.e("StreetCleanupActivity", "loadItemsFromDb failed", e)
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
        val levelName = levelNameForDb(level)
        var streetIcon = "street_cleanup"
        var trashCount = 0
        var timerSeconds = 0
        var gameId: Int? = null
        try {
            val db = GameDatabase.open(this)
            val offset = (level - 1).coerceAtLeast(0)
            val c = db.rawQuery(
                "SELECT street_icon, trash_count, timer FROM street_cleanup_game ORDER BY rowid LIMIT 1 OFFSET ?",
                arrayOf(offset.toString())
            )
            if (c.moveToFirst()) {
                val streetIdx = c.getColumnIndexOrThrow("street_icon")
                val countIdx = c.getColumnIndexOrThrow("trash_count")
                val timerIdx = c.getColumnIndexOrThrow("timer")
                streetIcon = c.getString(streetIdx) ?: "street_cleanup"
                trashCount = c.getInt(countIdx)
                timerSeconds = c.getInt(timerIdx)
            }
            c.close()

            val g = db.rawQuery(
                "SELECT game_id FROM game WHERE game_title=? AND game_level=? LIMIT 1",
                arrayOf(GameDbContract.GAME_TITLE_STREET_CLEANUP, levelName)
            )
            if (g.moveToFirst()) {
                gameId = g.getInt(0)
            }
            g.close()
            db.close()
        } catch (e: Exception) {
            Log.e("StreetCleanupActivity", "loadLevelConfig failed", e)
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
        tvTimer.text = getString(R.string.label_time_zero)
        remainingItems = 0
        totalItems = 0
        totalSeconds = 0
        secondsLeft = 0
        correctCount = 0
        wrongCount = 0
        floodView?.let { rootLayout.removeView(it) }
        floodView = null
        rootLayout.setBackgroundResource(R.drawable.trash_sorting_game_background)
        btnStart.text = getString(R.string.status_time_running)

        val intro = getString(R.string.street_intro)
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
        btnStart.text = getString(R.string.status_time_running)
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
        tvTimer.text = getString(R.string.time_format, seconds)
        timer = object : CountDownTimer(seconds * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val s = (millisUntilFinished / 1000).toInt()
                secondsLeft = s
                tvTimer.text = getString(R.string.time_format, s)
            }

            override fun onFinish() {
                secondsLeft = 0
                if (remainingItems <= 0) return
                tvTimer.text = getString(R.string.label_time_zero)
                btnStart.text = getString(R.string.start_button)
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
            feedbackBanner.show(getString(R.string.street_correct_bin, name), RecyclensFeedbackBanner.Style.INFO)
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
            feedbackBanner.show(getString(R.string.street_should_go_bin, name, correctBin), RecyclensFeedbackBanner.Style.WARNING)
        }

        if (remainingItems <= 0) {
            timer?.cancel()
            timer = null
            evaluateFinalScore()
        }
    }

    private fun evaluateFinalScore() {
        if (totalItems <= 0) {
            btnStart.text = getString(R.string.start_button)
            showSuccessDialog()
            return
        }

        val accuracy = correctCount.toFloat() / totalItems.toFloat()
        btnStart.text = getString(R.string.start_button)

        if (accuracy < 0.5f) {
            showFloodAnimation {
                showLowScoreDialog()
            }
        } else {
            showSuccessDialog()
        }
    }

    private fun showFloodAnimation(onEnd: (() -> Unit)? = null) {
        rootLayout.setBackgroundResource(R.drawable.street_cleanup_game_flood)
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
            val db = GameDatabase.open(this)
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

            val upd = ContentValues().apply {
                put("current_score", correct)
            }
            db.update(
                "game",
                upd,
                "game_title=? AND game_level=?",
                arrayOf(GameDbContract.GAME_TITLE_STREET_CLEANUP, levelNameForDb(level))
            )

            db.close()
        } catch (e: Exception) {
            Log.e("StreetCleanupActivity", "saveGameResultToDb failed", e)
        }
    }

    private fun showSuccessDialog() {
        val levelName = when (level) {
            1 -> getString(R.string.label_easy)
            2 -> getString(R.string.label_medium)
            3 -> getString(R.string.label_hard)
            else -> getString(R.string.label_easy)
        }
        val scoreText = getString(R.string.score_result_format, correctCount, wrongCount)
        val timeText = getString(R.string.time_left_format, secondsLeft)

        saveGameResultToDb(
            totalScore = correctCount,
            correct = correctCount,
            wrong = wrongCount
        )

        val speakText = trf(
            R.string.street_result_success_speech,
            levelName,
            correctCount,
            wrongCount,
            secondsLeft
        )
        speak(speakText, "STREET_RESULT_SUCCESS")
        btnStart.text = getString(R.string.start_button)

        styledDialog.show(
            title = getString(R.string.dialog_level_complete),
            message = getString(R.string.dialog_level_complete_message_with_level, levelName, scoreText, timeText),
            positiveText = getString(R.string.action_play_again),
            negativeText = getString(R.string.action_back),
            tone = RecyclensDialog.Tone.SUCCESS,
            onPositive = {
                stopTts()
                startNewGame()
            },
            onNegative = {
                stopTts()
                finish()
            }
        )
    }

    private fun showFailDialog() {
        val scoreText = getString(R.string.score_result_format, correctCount, wrongCount)
        val timeUsed = totalSeconds - secondsLeft
        val timeText = getString(R.string.time_used_format, timeUsed)

        saveGameResultToDb(
            totalScore = correctCount,
            correct = correctCount,
            wrong = wrongCount + remainingItems
        )

        val speakText = trf(
            R.string.street_result_fail_time_speech,
            correctCount,
            wrongCount
        )
        speak(speakText, "STREET_RESULT_FAIL_TIME")
        btnStart.text = getString(R.string.start_button)

        styledDialog.show(
            title = getString(R.string.dialog_time_up),
            message = getString(R.string.dialog_time_up_message, scoreText, timeText),
            positiveText = getString(R.string.action_try_again),
            negativeText = getString(R.string.action_back),
            tone = RecyclensDialog.Tone.ERROR,
            onPositive = {
                stopTts()
                startNewGame()
            },
            onNegative = {
                stopTts()
                finish()
            }
        )
    }

    private fun showLowScoreDialog() {
        val scoreText = getString(R.string.score_result_format, correctCount, wrongCount)
        val itemsText = getString(R.string.street_sorted_all_items, totalItems)

        saveGameResultToDb(
            totalScore = correctCount,
            correct = correctCount,
            wrong = wrongCount
        )

        val speakText = trf(
            R.string.street_result_fail_low_speech,
            totalItems,
            correctCount,
            wrongCount
        )
        speak(speakText, "STREET_RESULT_FAIL_LOW")
        btnStart.text = getString(R.string.start_button)

        styledDialog.show(
            title = getString(R.string.dialog_level_fail_title),
            message = getString(R.string.dialog_low_score_message, scoreText, itemsText),
            positiveText = getString(R.string.action_try_again),
            negativeText = getString(R.string.action_back),
            tone = RecyclensDialog.Tone.WARNING,
            onPositive = {
                stopTts()
                startNewGame()
            },
            onNegative = {
                stopTts()
                finish()
            }
        )
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun getTrashDisplayName(item: WasteItem): String {
        return WasteCatalog.localizedLabelForResName(this, item.resName)
            ?: getString(R.string.item_this_trash)
    }

    private fun getCorrectBinText(item: WasteItem): String {
        return if (item.isBiodegradable) {
            getString(R.string.bin_green_bio)
        } else {
            getString(R.string.bin_blue_non_bio)
        }
    }

    private fun showInstructionDialog(page: Int = 1) {
        val title = if (page == 1) {
            getString(R.string.category_biodegradable)
        } else {
            getString(R.string.category_non_biodegradable)
        }
        val text = if (page == 1) {
            getString(R.string.street_game_info_1)
        } else {
            getString(R.string.street_game_info_2)
        }

        val content = createInstructionContent(page)

        if (page == 1) {
            styledDialog.show(
                title = title,
                message = text,
                positiveText = getString(R.string.action_next_non_bio),
                negativeText = getString(R.string.action_close),
                tone = RecyclensDialog.Tone.INFO,
                contentView = content,
                onPositive = {
                    stopTts()
                    showInstructionDialog(2)
                },
                onNegative = { stopTts() }
            )
        } else {
            styledDialog.show(
                title = title,
                message = text,
                positiveText = getString(R.string.action_back_bio),
                negativeText = getString(R.string.action_done),
                tone = RecyclensDialog.Tone.INFO,
                contentView = content,
                onPositive = {
                    stopTts()
                    showInstructionDialog(1)
                },
                onNegative = { stopTts() }
            )
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
            text = if (page == 1) getString(R.string.street_green_bin_text) else getString(R.string.street_blue_bin_text)
            textSize = 15f
            setTextColor(Color.parseColor("#4A3B2A"))
            setPadding(dp(12), 0, 0, 0)
        }

        headerRow.addView(binIcon)
        headerRow.addView(binLabel)
        layout.addView(headerRow)

        val subtitle = TextView(this).apply {
            text = if (page == 1) getString(R.string.street_bio_items) else getString(R.string.street_nonbio_items)
            textSize = 14f
            setTextColor(Color.parseColor("#4A3B2A"))
        }
        layout.addView(subtitle)

        val items = if (page == 1) {
            listOf(
                R.drawable.ic_trash_banana to getString(R.string.item_banana_peel),
                R.drawable.ic_trash_fruit to getString(R.string.item_fruit),
                R.drawable.ic_trash_leaf to getString(R.string.item_leaf),
                R.drawable.ic_trash_grass to getString(R.string.item_grass),
                R.drawable.ic_trash_paper to getString(R.string.item_paper),
                R.drawable.ic_trash_tissue to getString(R.string.item_tissue)
            )
        } else {
            listOf(
                R.drawable.ic_trash_plastic_cup to getString(R.string.item_plastic_cup),
                R.drawable.ic_trash_bottle to getString(R.string.item_plastic_bottle),
                R.drawable.ic_trash_wrapper to getString(R.string.item_candy_wrapper),
                R.drawable.ic_trash_styro to getString(R.string.item_styrofoam_box)
            )
        }

        for ((resId, label) in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            val icon = ImageView(this).apply {
                setImageResource(resId)
                val size = dp(32)
                layoutParams = LinearLayout.LayoutParams(size, size)
            }

            val text = TextView(this).apply {
                this.text = label
                textSize = 14f
                setTextColor(Color.parseColor("#4A3B2A"))
                setPadding(dp(12), 0, 0, 0)
                isSingleLine = false
                setHorizontallyScrolling(false)
                maxLines = 4
                setLineSpacing(0f, 1.1f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            row.addView(icon)
            row.addView(text)
            layout.addView(row)
        }

        val hint = TextView(this).apply {
            text = "\n${getString(R.string.street_game_intro_footer)}"
            textSize = 13f
            setTextColor(Color.parseColor("#4A3B2A"))
        }
        layout.addView(hint)

        return layout
    }

    private fun trf(resId: Int, vararg args: Any): String = getString(resId, *args)

    private fun levelNameForDb(level: Int): String {
        return GameDbContract.levelNameForLevelNumber(level)
    }
}
