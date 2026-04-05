package com.example.recyclens

import android.app.Activity
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class RecyclensDialog(private val activity: Activity) {

    enum class Tone(
        val cardColor: Int,
        val borderColor: Int,
        val accentColor: Int,
        val icon: String
    ) {
        INFO(Color.parseColor("#FFF9E6"), Color.parseColor("#E4C773"), Color.parseColor("#7C5A1E"), "🌿"),
        SUCCESS(Color.parseColor("#EAF7E1"), Color.parseColor("#7FB36A"), Color.parseColor("#2F6B2D"), "✨"),
        WARNING(Color.parseColor("#FFF1D9"), Color.parseColor("#E2A85C"), Color.parseColor("#8C5A1E"), "🍂"),
        ERROR(Color.parseColor("#FFE4E4"), Color.parseColor("#D97A7A"), Color.parseColor("#8B2E2E"), "💭")
    }

    fun show(
        title: String,
        message: String,
        positiveText: String,
        negativeText: String? = null,
        tone: Tone = Tone.INFO,
        cancelable: Boolean = false,
        contentView: View? = null,
        onPositive: () -> Unit = {},
        onNegative: () -> Unit = {}
    ): AlertDialog {
        val content = buildContent(title, message, tone, contentView)

        val builder = AlertDialog.Builder(activity)
            .setView(content)
            .setCancelable(cancelable)
            .setPositiveButton(positiveText) { dialog, _ ->
                dialog.dismiss()
                onPositive()
            }

        if (negativeText != null) {
            builder.setNegativeButton(negativeText) { dialog, _ ->
                dialog.dismiss()
                onNegative()
            }
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            styleButton(dialog.getButton(DialogInterface.BUTTON_POSITIVE), tone.accentColor)
            styleButton(dialog.getButton(DialogInterface.BUTTON_NEGATIVE), Color.parseColor("#6C4E3A"))
        }
        dialog.show()
        return dialog
    }

    private fun buildContent(title: String, message: String, tone: Tone, contentView: View?): LinearLayout {
        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(24).toFloat()
                setColor(tone.cardColor)
                setStroke(dp(2), tone.borderColor)
            }
            elevation = dp(10).toFloat()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconView = TextView(activity).apply {
            text = tone.icon
            textSize = 22f
            setTextColor(tone.accentColor)
        }

        val titleView = TextView(activity).apply {
            text = title
            textSize = 20f
            setTextColor(Color.parseColor("#4A3B2A"))
            setPadding(dp(10), 0, 0, 0)
        }

        header.addView(iconView)
        header.addView(titleView)
        container.addView(header)
        if (contentView != null) {
            container.addView(contentView)
        } else {
            val bodyView = TextView(activity).apply {
                text = message
                textSize = 15f
                setTextColor(Color.parseColor("#4A3B2A"))
                setPadding(0, dp(14), 0, 0)
                setLineSpacing(0f, 1.15f)
            }
            container.addView(bodyView)
        }
        return container
    }

    private fun styleButton(button: Button?, color: Int) {
        button ?: return
        button.setTextColor(color)
        button.isAllCaps = false
    }

    private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
}
