package com.example.recyclens

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

data class StreetTrashItem(
    val name: String,
    val isBiodegradable: Boolean,
    val drawableRes: Int
)

class StreetCleanupActivity : AppCompatActivity() {

    private lateinit var gameArea: FrameLayout
    private lateinit var btnStart: TextView
    private lateinit var tvLevel: TextView
    private lateinit var btnBack: View

    private var binGreen: ImageView? = null
    private var binBlue: ImageView? = null

    private var currentTrashView: ImageView? = null
    private var currentIndex = 0

    private val trashItems = listOf(
        StreetTrashItem("Banana Peel", true, R.drawable.ic_trash_banana),
        StreetTrashItem("Fruit", true, R.drawable.ic_trash_fruit),
        StreetTrashItem("Leaf", true, R.drawable.ic_trash_leaf),
        StreetTrashItem("Paper", true, R.drawable.ic_trash_paper),
        StreetTrashItem("Tissue", true, R.drawable.ic_trash_tissue),
        StreetTrashItem("Plastic Bottle", false, R.drawable.ic_trash_bottle),
        StreetTrashItem("Plastic Cup", false, R.drawable.ic_trash_plastic_cup),
        StreetTrashItem("Styro", false, R.drawable.ic_trash_styro),
        StreetTrashItem("Candy Wrapper", false, R.drawable.ic_trash_wrapper)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.street_cleanup)

        setupBottomBar(BottomBar.Tab.PLAY)

        gameArea = findViewById(R.id.gameAreaStreet)   // now really a FrameLayout
        btnStart = findViewById(R.id.btnStartStreet)
        tvLevel = findViewById(R.id.tvLevel)
        btnBack = findViewById(R.id.btnBackStreet)

        tvLevel.text = "Easy"

        btnBack.setOnClickListener { finish() }

        addBinsIfNeeded()

        btnStart.setOnClickListener {
            startGame()
        }
    }

    private fun startGame() {
        currentIndex = 0
        spawnNextTrash()
        Toast.makeText(
            this,
            "Drag the trash to the right bin!",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun addBinsIfNeeded() {
        if (binGreen != null && binBlue != null) return

        val size = dpToPx(80)

        // Green bin - biodegradable (left)
        binGreen = ImageView(this).apply {
            setBackgroundColor(Color.parseColor("#2E7D32"))
            contentDescription = "Green Bin - Biodegradable"
            id = View.generateViewId()
        }
        val lpGreen = FrameLayout.LayoutParams(size, size)
        lpGreen.leftMargin = dpToPx(16)
        lpGreen.bottomMargin = dpToPx(16)
        lpGreen.gravity = android.view.Gravity.START or android.view.Gravity.BOTTOM
        gameArea.addView(binGreen, lpGreen)

        // Blue bin - non-biodegradable (right)
        binBlue = ImageView(this).apply {
            setBackgroundColor(Color.parseColor("#1565C0"))
            contentDescription = "Blue Bin - Non-biodegradable"
            id = View.generateViewId()
        }
        val lpBlue = FrameLayout.LayoutParams(size, size)
        lpBlue.rightMargin = dpToPx(16)
        lpBlue.bottomMargin = dpToPx(16)
        lpBlue.gravity = android.view.Gravity.END or android.view.Gravity.BOTTOM
        gameArea.addView(binBlue, lpBlue)

        binGreen?.setOnDragListener(binDragListener)
        binBlue?.setOnDragListener(binDragListener)
    }

    private fun spawnNextTrash() {
        currentTrashView?.let { v ->
            (v.parent as? ViewGroup)?.removeView(v)
        }
        currentTrashView = null

        if (currentIndex >= trashItems.size) {
            Toast.makeText(this, "Yay! The street is clean!", Toast.LENGTH_SHORT).show()
            return
        }

        val item = trashItems[currentIndex]

        val trashView = ImageView(this).apply {
            id = View.generateViewId()
            setImageResource(item.drawableRes)
            tag = item
            setOnTouchListener(trashTouchListener)
        }

        val size = dpToPx(80)
        val lp = FrameLayout.LayoutParams(size, size)
        lp.topMargin = dpToPx(24)
        lp.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL

        gameArea.addView(trashView, lp)
        currentTrashView = trashView
    }

    private val trashTouchListener = View.OnTouchListener { v, event ->
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val shadow = View.DragShadowBuilder(v)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    v.startDragAndDrop(null, shadow, v, 0)
                } else {
                    @Suppress("DEPRECATION")
                    v.startDrag(null, shadow, v, 0)
                }
                v.visibility = View.INVISIBLE
                true
            }
            else -> false
        }
    }

    private val binDragListener = View.OnDragListener { target, event ->
        when (event.action) {
            DragEvent.ACTION_DROP -> {
                val draggedView = event.localState as? View ?: return@OnDragListener true
                val item = draggedView.tag as? StreetTrashItem ?: return@OnDragListener true

                val droppedOnGreen = target == binGreen
                val droppedOnBlue = target == binBlue

                (draggedView.parent as? ViewGroup)?.removeView(draggedView)
                currentTrashView = null

                val correct = if (item.isBiodegradable) droppedOnGreen else droppedOnBlue

                if (correct) {
                    Toast.makeText(
                        this,
                        "Good job! ${item.name} is in the right bin.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    val correctBin =
                        if (item.isBiodegradable) "green (biodegradable)" else "blue (non-biodegradable)"
                    Toast.makeText(
                        this,
                        "Oops! ${item.name} should go in the $correctBin bin.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                currentIndex++
                spawnNextTrash()
                true
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                val draggedView = event.localState as? View
                if (draggedView != null && draggedView.visibility == View.INVISIBLE) {
                    draggedView.visibility = View.VISIBLE
                }
                true
            }

            else -> true
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }
}
