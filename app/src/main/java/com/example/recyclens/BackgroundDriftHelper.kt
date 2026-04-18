package com.example.recyclens

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.WeakHashMap

object BackgroundDriftHelper {

    private const val DRIFT_TAG = "recyclens_bg_drift"
    private val running = WeakHashMap<AppCompatActivity, AnimatorSet>()

    fun attach(activity: AppCompatActivity, backgroundRes: Int = R.drawable.background) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = content.getChildAt(0) as? ViewGroup ?: return

        for (i in 0 until root.childCount) {
            if (root.getChildAt(i).tag == DRIFT_TAG) return
        }

        val probeBackground = createRepeatingBackground(activity, backgroundRes)
        val travelX = resolveTravelDistance(probeBackground?.intrinsicWidth, 260f)
        val travelY = resolveTravelDistance(probeBackground?.intrinsicHeight, 260f)

        val host = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            tag = DRIFT_TAG
            isClickable = false
            isFocusable = false
            clipChildren = false
            clipToPadding = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val tilePositions = listOf(
            0f to 0f,
            travelX to 0f,
            0f to travelY,
            travelX to travelY
        )

        val animators = mutableListOf<android.animation.Animator>()

        for ((startX, startY) in tilePositions) {
            val tile = View(activity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                background = createRepeatingBackground(activity, backgroundRes)
                translationX = startX
                translationY = startY
                // Slight overscale helps hide precision edges on some devices.
                scaleX = 1.04f
                scaleY = 1.04f
            }
            host.addView(tile)

            animators += ObjectAnimator.ofFloat(tile, View.TRANSLATION_X, startX, startX - travelX).apply {
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                duration = 9000L
            }
            animators += ObjectAnimator.ofFloat(tile, View.TRANSLATION_Y, startY, startY - travelY).apply {
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                duration = 9000L
            }
        }

        root.addView(host, 0)

        val set = AnimatorSet().apply {
            playTogether(animators)
            start()
        }
        running[activity] = set
    }

    fun detach(activity: AppCompatActivity) {
        running.remove(activity)?.cancel()

        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = content.getChildAt(0) as? ViewGroup ?: return
        for (i in root.childCount - 1 downTo 0) {
            if (root.getChildAt(i).tag == DRIFT_TAG) {
                root.removeViewAt(i)
            }
        }
    }

    private fun createRepeatingBackground(activity: AppCompatActivity, backgroundRes: Int): Drawable? {
        val drawable = ContextCompat.getDrawable(activity, backgroundRes)?.mutate() ?: return null
        if (drawable is BitmapDrawable) {
            drawable.tileModeX = Shader.TileMode.REPEAT
            drawable.tileModeY = Shader.TileMode.REPEAT
            drawable.isFilterBitmap = true
        }
        return drawable
    }

    private fun resolveTravelDistance(sizePx: Int?, fallback: Float): Float {
        val px = sizePx ?: return fallback
        return if (px > 1) px.toFloat() else fallback
    }
}
