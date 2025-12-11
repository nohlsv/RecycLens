package com.example.recyclens

import android.content.ContentValues
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
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
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

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

    private lateinit var langToggle: RelativeLayout
    private lateinit var langText: TextView
    private lateinit var labelScan: TextView
    private lateinit var labelPlay: TextView

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

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var wasMusicPlayingBeforeTts = false
    private var isEnglish = true

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

        langToggle = findViewById(R.id.langToggle)
        langText = findViewById(R.id.langText)
        labelScan = findViewById(R.id.labelScan)
        labelPlay = findViewById(R.id.labelPlay)

        findViewById<ImageButton>(R.id.btnBackTrash).setOnClickListener {
            stopSpeech()
            finish()
        }

        findViewById<ImageButton>(R.id.btnInfoTrash).setOnClickListener {
            stopSpeech()
            if (ttsReady) {
                showInstructionDialog(1)
            } else {
                Toast.makeText(this, if (isEnglish) "Voice guide is starting, please wait." else "Nagsisimula ang voice guide, pakihintay sandali.", Toast.LENGTH_SHORT).show()
            }
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
                stopSpeech()
                AlertDialog.Builder(this)
                    .setTitle(if (isEnglish) "Restart level?" else "Ulitin ang level?")
                    .setMessage(if (isEnglish) "Do you want to reset the trash and your score and start again?" else "Gusto mo bang i-reset ang basura at score at magsimula muli?")
                    .setPositiveButton(if (isEnglish) "Yes" else "Oo") { _, _ ->
                        startNewRound()
                    }
                    .setNegativeButton(if (isEnglish) "No" else "Hindi", null)
                    .show()
            } else {
                startNewRound()
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

    private fun speak(textEn: String, textTl: String, id: String = "TRASH_TTS") {
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
            WasteItem("ic_trash_banana", R.drawable.ic_trash_banana, true, if (isEnglish) "Banana Peel" else "Balat ng Saging"),
            WasteItem("ic_trash_fruit", R.drawable.ic_trash_fruit, true, if (isEnglish) "Fruit" else "Prutas"),
            WasteItem("ic_trash_leaf", R.drawable.ic_trash_leaf, true, if (isEnglish) "Leaf" else "Dahon"),
            WasteItem("ic_trash_paper", R.drawable.ic_trash_paper, true, if (isEnglish) "Paper" else "Papel"),
            WasteItem("ic_trash_tissue", R.drawable.ic_trash_tissue, true, if (isEnglish) "Tissue" else "Tisyu"),
            WasteItem("ic_trash_plastic_cup", R.drawable.ic_trash_plastic_cup, false, "Plastic Cup"),
            WasteItem("ic_trash_bottle", R.drawable.ic_trash_bottle, false, "Plastic Bottle"),
            WasteItem("ic_trash_wrapper", R.drawable.ic_trash_wrapper, false, "Candy Wrapper"),
            WasteItem("ic_trash_styro", R.drawable.ic_trash_styro, false, "Styrofoam Box"),
            WasteItem("ic_trash_grass", R.drawable.ic_trash_grass, true, if (isEnglish) "Grass" else "Damo")
        )
    }

    private fun showInstructionDialog(page: Int = 1) {
        val titleEn = if (page == 1) "Biodegradable – GREEN bin" else "Non-biodegradable – BLUE bin"
        val titleTl = if (page == 1) "Nabubulok – BERDENG basurahan" else "Di-nabubulok – ASUL na basurahan"
        val builder = AlertDialog.Builder(this)
            .setTitle(if (isEnglish) titleEn else titleTl)
            .setView(createInstructionContent(page))

        if (page == 1) {
            val en = "Biodegradable items like banana peel, fruit, leaf, grass, paper, and tissue should be dragged into the green bin."
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
            val en = "Non-biodegradable items like plastic cup, plastic bottle, candy wrapper, and styrofoam box should be dragged into the blue bin."
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
            val binView = if (page == 1) binGreen else binBlue
            if (binView is ImageView) {
                setImageDrawable(binView.drawable)
            } else {
                background = binView.background
            }
        }

        val binLabel = TextView(this).apply {
            text = if (page == 1) {
                if (isEnglish) "Drag these to this GREEN bin" else "I-drag ang mga ito sa BERDENG basurahan"
            } else {
                if (isEnglish) "Drag these to this BLUE bin" else "I-drag ang mga ito sa ASUL na basurahan"
            }
            textSize = 15f
            setPadding(dp(12), 0, 0, 0)
        }

        headerRow.addView(binIcon)
        headerRow.addView(binLabel)
        layout.addView(headerRow)

        val subtitle = TextView(this).apply {
            text = if (page == 1) {
                if (isEnglish) "These are biodegradable items:" else "Ito ang mga nabubulok na basura:"
            } else {
                if (isEnglish) "These are non-biodegradable items:" else "Ito ang mga di-nabubulok na basura:"
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
            text = if (isEnglish) "\nWatch the trash picture and its name, then drag it into the correct bin." else "\nTingnan ang larawan at pangalan ng basura, at i-drag ito sa tamang basurahan."
            textSize = 13f
        }
        layout.addView(hint)

        return layout
    }

    private fun startNewRound() {
        stopSpeech()
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
            val en = "Drag each trash into the correct bin. Biodegradable items go in the green bin. Non-biodegradable items go in the blue bin."
            val tl = "Hilahin ang bawat basura papunta sa tamang basurahan. Ang mga nabubulok ay sa berdeng basurahan. Ang mga di-nabubulok ay sa asul na basurahan."
            speak(en, tl)
            showNextItem()
        }
    }

    private fun resetScoreAndCircles(count: Int) {
        tvScore.text = "Score: 0 / $count"
        circleRow.removeAllViews()
        circleViews.clear()

        if (count <= 0) return

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
            btnStart.text = if (isEnglish) "Play Again" else "Maglaro Muli"
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
            text = if (isEnglish) item.label else translateItemLabel(item)
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

    private fun translateItemLabel(item: WasteItem): String {
        return when (item.resName) {
            "ic_trash_banana" -> "Balat ng Saging"
            "ic_trash_fruit" -> "Prutas"
            "ic_trash_leaf" -> "Dahon"
            "ic_trash_paper" -> "Papel"
            "ic_trash_tissue" -> "Tisyu"
            "ic_trash_grass" -> "Damo"
            else -> item.label
        }
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
            val en = "Drop the trash inside the green or blue bin."
            val tl = "Ihulog ang basura sa loob ng berdeng o asul na basurahan."
            Toast.makeText(this, if (isEnglish) en else tl, Toast.LENGTH_SHORT).show()
            speak(en, tl)
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
            val en = "Correct. ${item.label} is in the right bin."
            val tl = "Tama. Nasa tamang basurahan ang ${translateItemLabel(item)}."
            speak(en, tl)
        } else {
            wrongCount++
            setCircleColor(currentIndex, Color.parseColor("#EF5350"))
            val en = if (item.isBiodegradable) {
                "${item.label} should go in the green bin."
            } else {
                "${item.label} should go in the blue bin."
            }
            val tl = if (item.isBiodegradable) {
                "Dapat ilagay ang ${translateItemLabel(item)} sa berdeng basurahan."
            } else {
                "Dapat ilagay ang ${translateItemLabel(item)} sa asul na basurahan."
            }
            Toast.makeText(this, if (isEnglish) en else tl, Toast.LENGTH_SHORT).show()
            speak(en, tl)
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
        val en = "Good work. You finished the level. $scoreText."
        val tl = "Magaling. Natapos mo ang level na ito. $scoreText."
        speak(en, tl)

        AlertDialog.Builder(this)
            .setTitle(if (isEnglish) "Good work!" else "Magaling!")
            .setMessage(
                (if (isEnglish) "You finished the level!" else "Natapos mo ang level!") +
                        "\n$scoreText"
            )
            .setCancelable(false)
            .setPositiveButton(if (isEnglish) "Play again" else "Maglaro muli") { _, _ ->
                stopSpeech()
                startNewRound()
            }
            .setNegativeButton(if (isEnglish) "Back" else "Bumalik") { _, _ ->
                stopSpeech()
                finish()
            }
            .show()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}
