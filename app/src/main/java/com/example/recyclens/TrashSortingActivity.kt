package com.example.recyclens

import android.content.ContentValues
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.recyclens.data.WasteCatalog
import com.example.recyclens.data.db.AppDatabase
import com.example.recyclens.data.db.GameDbContract
import com.example.recyclens.data.db.GameDatabase
import java.util.Locale

class TrashSortingActivity : AppCompatActivity(), BottomBar.LanguageAware {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    @Volatile
    private var isActivityInForeground = false

    private lateinit var gameArea: FrameLayout
    private lateinit var btnStart: TextView
    private lateinit var titleTrash: TextView
    private lateinit var tvLevel: TextView
    private lateinit var binGreen: View
    private lateinit var binBlue: View
    private lateinit var tvScore: TextView
    private lateinit var circleRow: LinearLayout
    private val circleViews = mutableListOf<View>()
    private lateinit var feedbackBanner: RecyclensFeedbackBanner
    private lateinit var styledDialog: RecyclensDialog

    private data class WasteItem(
        val resName: String,
        val drawableRes: Int,
        val isBiodegradable: Boolean,
        val labelEn: String? = null,
        val labelTl: String? = null
    )

    private var allItems: List<WasteItem> = emptyList()
    private var currentQueue: MutableList<WasteItem> = mutableListOf()
    private var answeredCount = 0
    private var totalToSort = 0
    private var selectedLevel = 1
    private var score = 0
    private var wrongCount = 0
    private var roundStartTriggered = false

    private val startRoundFallback = Runnable {
        beginRoundIfNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguagePrefs.applyLocale(this)
        setContentView(R.layout.trash_sorting)

        feedbackBanner = RecyclensFeedbackBanner(this)
        styledDialog = RecyclensDialog(this)

        gameArea = findViewById(R.id.gameAreaTrash)
        btnStart = findViewById(R.id.btnStartTrash)
        titleTrash = findViewById(R.id.titleTrash)
        tvLevel = findViewById(R.id.tvLevel)
        binGreen = findViewById(R.id.binGreen)
        binBlue = findViewById(R.id.binBlue)
        tvScore = findViewById(R.id.tvScore)
        circleRow = findViewById(R.id.circleRow)

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
                            MusicManager.start(this@TrashSortingActivity, "trash_sorting_music")
                            if (utteranceId == "TRASH_START_GAME") {
                                beginRoundIfNeeded()
                            }
                        }
                    }
                    override fun onError(utteranceId: String?) {
                        runOnUiThread {
                            if (!isActivityInForeground || isFinishing || isDestroyed) return@runOnUiThread
                            MusicManager.start(this@TrashSortingActivity, "trash_sorting_music")
                            if (utteranceId == "TRASH_START_GAME") {
                                beginRoundIfNeeded()
                            }
                        }
                    }
                })
                ttsReady = true
                showInstructionDialog(1)
            }
        }

        findViewById<ImageButton>(R.id.btnBackTrash).setOnClickListener {
            stopTts()
            finish()
        }

        findViewById<ImageButton>(R.id.btnInfoTrash).setOnClickListener {
            stopTts()
            showInstructionDialog(1)
        }

        selectedLevel = intent.getIntExtra("extra_level", 1)
        applyLevelBadgeText()
        refreshLocalizedTexts()

        AppDatabase.getInstance(applicationContext)
        allItems = loadItemsFromDb()
        resetScoreAndCircles(0)

        btnStart.text = getString(R.string.start_button)
        btnStart.setOnClickListener {
            val roundRunning =
                (answeredCount > 0 && answeredCount < totalToSort) || currentQueue.isNotEmpty()
            if (roundRunning) {
                styledDialog.show(
                    title = getString(R.string.dialog_restart_game_title),
                    message = getString(R.string.dialog_restart_game_message),
                    positiveText = getString(R.string.action_yes),
                    negativeText = getString(R.string.action_no),
                    tone = RecyclensDialog.Tone.INFO,
                    onPositive = {
                        stopTts()
                        startNewRound()
                    }
                )
            } else {
                stopTts()
                startNewRound()
            }
        }

        setupBottomBar(BottomBar.Tab.PLAY)
        RecyclensEntryAnimator.play(this)
    }

    override fun onLanguageChanged() {
        refreshLocalizedTexts()
    }

    private fun refreshLocalizedTexts() {
        titleTrash.text = getString(R.string.game_trash_sorting)
        applyLevelBadgeText()

        btnStart.text = when {
            currentQueue.isNotEmpty() || (answeredCount > 0 && answeredCount < totalToSort) -> getString(R.string.status_sorting)
            answeredCount >= totalToSort && totalToSort > 0 -> getString(R.string.status_play_again)
            else -> getString(R.string.start_button)
        }

        tvScore.text = getString(R.string.score_progress_format, score, totalToSort)

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
        when (selectedLevel) {
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
        MusicManager.start(this, "trash_sorting_music")
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
                SELECT wm.image_path, wm.name_en, wm.name_tl, wc.name_en AS category_name_en
                FROM waste_material wm
                JOIN waste_category wc ON wm.category_id = wc.category_id
                WHERE wm.image_path IS NOT NULL AND wm.image_path <> ''
            """.trimIndent()
            val c = db.rawQuery(sql, null)
            val imgIdx = c.getColumnIndexOrThrow("image_path")
            val nameEnIdx = c.getColumnIndexOrThrow("name_en")
            val nameTlIdx = c.getColumnIndexOrThrow("name_tl")
            val catIdx = c.getColumnIndexOrThrow("category_name_en")
            while (c.moveToNext()) {
                val img = c.getString(imgIdx) ?: continue
                if (map.containsKey(img)) continue
                val labelEn = c.getString(nameEnIdx) ?: img
                val labelTl = c.getString(nameTlIdx)
                val catName = c.getString(catIdx) ?: ""
                val isBio = catName.equals("Biodegradable", ignoreCase = true)
                val resId = resources.getIdentifier(img, "drawable", packageName)
                if (resId != 0) {
                    map[img] = WasteItem(
                        resName = img,
                        drawableRes = resId,
                        isBiodegradable = isBio,
                        labelEn = labelEn,
                        labelTl = labelTl
                    )
                }
            }
            c.close()
            db.close()
        } catch (e: Exception) {
            Log.e("TrashSortingActivity", "loadItemsFromDb failed", e)
        }

        if (map.isNotEmpty()) return map.values.toList()

        return listOf(
            WasteItem("ic_trash_banana", R.drawable.ic_trash_banana, true),
            WasteItem("ic_trash_fruit", R.drawable.ic_trash_fruit, true),
            WasteItem("ic_trash_leaf", R.drawable.ic_trash_leaf, true),
            WasteItem("ic_trash_paper", R.drawable.ic_trash_paper, true),
            WasteItem("ic_trash_tissue", R.drawable.ic_trash_tissue, true),
            WasteItem("ic_trash_plastic_cup", R.drawable.ic_trash_plastic_cup, false),
            WasteItem("ic_trash_bottle", R.drawable.ic_trash_bottle, false),
            WasteItem("ic_trash_wrapper", R.drawable.ic_trash_wrapper, false),
            WasteItem("ic_trash_styro", R.drawable.ic_trash_styro, false),
            WasteItem("ic_trash_grass", R.drawable.ic_trash_grass, true)
        )
    }

    private fun showInstructionDialog(page: Int = 1) {
        val title = if (page == 1) {
            getString(R.string.dialog_title_bio_green)
        } else {
            getString(R.string.dialog_title_nonbio_blue)
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
        speak(text, if (page == 1) "TRASH_INFO_1" else "TRASH_INFO_2")
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

    private fun startNewRound() {
        btnStart.text = getString(R.string.status_sorting)
        gameArea.removeCallbacks(startRoundFallback)
        roundStartTriggered = false
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

        val intro = getString(R.string.trash_intro)
        if (ttsReady) {
            speak(intro, "TRASH_START_GAME")
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
            showNextItem()
        }
    }

    private fun resetScoreAndCircles(count: Int) {
        tvScore.text = getString(R.string.score_progress_format, 0, count)
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
            return
        }

        val item = currentQueue.removeAt(0)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            tag = item
        }

        val iv = ImageView(this).apply {
            setImageResource(item.drawableRes)
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(dp(90), dp(90))
        }

        val labelView = TextView(this).apply {
            text = forceTwoLineLabel(localizedItemLabel(item))
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 0f, Color.BLACK)
            isSingleLine = false
            setHorizontallyScrolling(false)
            includeFontPadding = true
            maxLines = 2
            minLines = 2
            setLineSpacing(0f, 1.1f)
            layoutParams = LinearLayout.LayoutParams(dp(140), LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        container.addView(iv)
        container.addView(labelView)

        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(8)
        }

        gameArea.addView(container, lp)
        attachDragListener(container)
        updateProgress()
    }

    private fun updateProgress() {
        tvScore.text = getString(R.string.score_progress_format, score, totalToSort)
    }

    private fun attachDragListener(view: View) {
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

        val greenRect = Rect()
        val blueRect = Rect()
        binGreen.getGlobalVisibleRect(greenRect)
        binBlue.getGlobalVisibleRect(blueRect)

        val droppedOnGreen = greenRect.contains(dropRawX, dropRawY)
        val droppedOnBlue = blueRect.contains(dropRawX, dropRawY)

        if (!droppedOnGreen && !droppedOnBlue) {
            val msg = getString(R.string.feedback_drop_bin)
            feedbackBanner.show(msg, RecyclensFeedbackBanner.Style.INFO)
            speak(msg, "TRASH_HINT")
            view.animate().x(startX).y(startY).setDuration(200).start()
            return
        }

        val currentIndex = answeredCount
        val correctIsGreen = item.isBiodegradable
        val isCorrect = (correctIsGreen && droppedOnGreen) || (!correctIsGreen && droppedOnBlue)

        gameArea.removeView(view)

        if (isCorrect) {
            score++
            setCircleColor(currentIndex, Color.parseColor("#4CAF50"))
            feedbackBanner.show(getString(R.string.feedback_correct), RecyclensFeedbackBanner.Style.SUCCESS)
        } else {
            wrongCount++
            setCircleColor(currentIndex, Color.parseColor("#EF5350"))
            val msg = if (item.isBiodegradable) {
                getString(R.string.feedback_trash_correct_green, localizedItemLabel(item))
            } else {
                getString(R.string.feedback_trash_correct_blue, localizedItemLabel(item))
            }
            feedbackBanner.show(msg, RecyclensFeedbackBanner.Style.WARNING)
            speak(msg, "TRASH_FEEDBACK")
        }

        answeredCount++
        updateProgress()
        showNextItem()
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

            val values = ContentValues().apply {
                put("total_score", totalScore)
                put("correct_count", correct)
                put("wrong_count", wrong)
                put("game", "trash_sorting")
            }

            val rowId = db.insert("game_result", null, values)
            Log.d("TrashSortingActivity", "Inserted row into game_result, rowId=$rowId")

            val levelName = GameDbContract.levelNameForLevelNumber(selectedLevel)
            val upd = ContentValues().apply {
                put("current_score", correct)
            }
            val updatedRows = db.update(
                "game",
                upd,
                "game_title=? AND game_level=?",
                arrayOf(GameDbContract.GAME_TITLE_TRASH_SORTING, levelName)
            )
            Log.d("TrashSortingActivity", "Updated game table rows=$updatedRows")

            db.close()
        } catch (e: Exception) {
            Log.e("TrashSortingActivity", "saveGameResultToDb failed", e)
        }
    }

    private fun showSuccessDialog() {
        val scoreText = getString(R.string.score_result_format, score, wrongCount)
        val message = getString(R.string.trash_level_complete_message, scoreText)
        val speakText = getString(R.string.trash_result_success_speech, score, wrongCount)
        speak(speakText, "TRASH_RESULT_SUCCESS")
        btnStart.text = getString(R.string.status_play_again)

        styledDialog.show(
            title = getString(R.string.dialog_good_work),
            message = message,
            positiveText = getString(R.string.action_play_again),
            negativeText = getString(R.string.action_back),
            tone = RecyclensDialog.Tone.SUCCESS,
            onPositive = {
                stopTts()
                startNewRound()
            },
            onNegative = {
                stopTts()
                finish()
            }
        )
    }

    private fun localizedItemLabel(item: WasteItem): String {
        return WasteCatalog.localizedLabelForResName(this, item.resName)
            ?: translateArbitraryItemLabel(item.labelEn, item.labelTl)
    }

    private fun translateArbitraryItemLabel(rawEn: String?, rawTl: String?): String {
        val spec = rawEn?.let(WasteCatalog::findByDbName)
            ?: rawTl?.let(WasteCatalog::findByDbName)

        val localized = WasteCatalog.localizedLabel(this, spec)
        if (!localized.isNullOrBlank()) return localized

        if (LanguagePrefs.isEnglish(this)) {
            return rawEn ?: rawTl ?: getString(R.string.item_this_trash)
        }

        rawTl?.takeIf { it.isNotBlank() }?.let { return it }
        return rawEn ?: getString(R.string.item_this_trash)
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun forceTwoLineLabel(label: String): String {
        val clean = label.trim()
        val firstSpace = clean.indexOf(' ')
        if (firstSpace <= 0 || firstSpace >= clean.lastIndex) return clean
        return clean.substring(0, firstSpace) + "\n" + clean.substring(firstSpace + 1)
    }
}
