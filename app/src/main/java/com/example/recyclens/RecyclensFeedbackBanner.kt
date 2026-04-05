package com.example.recyclens

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class RecyclensFeedbackBanner(private val activity: Activity) {

    enum class Style(
        val backgroundColor: Int,
        val borderColor: Int,
        val icon: String
    ) {
        INFO(Color.parseColor("#FFF8D9"), Color.parseColor("#E7C96A"), "🌿"),
        SUCCESS(Color.parseColor("#E9F8E0"), Color.parseColor("#79B86B"), "✨"),
        WARNING(Color.parseColor("#FFF1D6"), Color.parseColor("#E3A85D"), "🍂"),
        ERROR(Color.parseColor("#FFE1E1"), Color.parseColor("#D97A7A"), "💭")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val rootView by lazy { activity.findViewById<ViewGroup>(android.R.id.content) }

    private var hostView: FrameLayout? = null
    private var bannerCard: LinearLayout? = null
    private var iconView: TextView? = null
    private var messageView: TextView? = null
    private var hideRunnable: Runnable? = null
    private var currentVisible = false

    fun show(message: CharSequence, style: Style = Style.INFO, durationMs: Long = 2200L) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread { show(message, style, durationMs) }
            return
        }

        ensureAttached()
        bannerCard?.animate()?.cancel()
        updateContent(message, style)
        scheduleHide(durationMs)
        animateIn()
    }

    fun release() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            activity.runOnUiThread { release() }
            return
        }

        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        hideRunnable = null
        hostView?.let { host ->
            (host.parent as? ViewGroup)?.removeView(host)
        }
        hostView = null
        bannerCard = null
        iconView = null
        messageView = null
        currentVisible = false
    }

    private fun ensureAttached() {
        if (hostView != null) return

        val host = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = false
            isFocusable = false
            clipChildren = false
            clipToPadding = false
        }

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            alpha = 0f
            translationY = -dp(24).toFloat()
            scaleX = 0.96f
            scaleY = 0.96f
            elevation = dp(10).toFloat()
            background = makeBackground(Style.INFO)
        }

        val icon = TextView(activity).apply {
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#5B4636"))
            text = Style.INFO.icon
        }

        val message = TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#4A3B2A"))
            maxLines = 3
        }

        card.addView(icon)
        card.addView(message)

        val hostParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ).apply {
            leftMargin = dp(14)
            rightMargin = dp(14)
            topMargin = dp(14)
        }

        host.addView(card, hostParams)
        rootView.addView(host)

        hostView = host
        bannerCard = card
        iconView = icon
        messageView = message
    }

    private fun updateContent(message: CharSequence, style: Style) {
        bannerCard?.background = makeBackground(style)
        iconView?.text = style.icon
        messageView?.text = message
    }

    private fun scheduleHide(durationMs: Long) {
        hideRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable {
            animateOut()
        }
        hideRunnable = runnable
        mainHandler.postDelayed(runnable, durationMs)
    }

    private fun animateIn() {
        val card = bannerCard ?: return
        if (!currentVisible) {
            card.animate().cancel()
            card.alpha = 0f
            card.translationY = -dp(24).toFloat()
            card.scaleX = 0.96f
            card.scaleY = 0.96f
        }
        currentVisible = true
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(240L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun animateOut() {
        val card = bannerCard ?: return
        card.animate()
            .alpha(0f)
            .translationY(-dp(16).toFloat())
            .setDuration(180L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                if (currentVisible) {
                    currentVisible = false
                }
            }
            .start()
    }

    private fun makeBackground(style: Style): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(style.backgroundColor)
            setStroke(dp(2), style.borderColor)
        }
    }

    private fun dp(value: Int): Int {
        return (value * activity.resources.displayMetrics.density).toInt()
    }
}