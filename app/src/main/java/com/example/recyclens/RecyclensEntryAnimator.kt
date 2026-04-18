package com.example.recyclens

import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity

object RecyclensEntryAnimator {

    private const val MAX_ANIMATED_VIEWS = 28
    private const val DRIFT_TAG = "recyclens_bg_drift"

    fun play(activity: AppCompatActivity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content)
        val root = content.getChildAt(0) as? ViewGroup ?: return

        val targets = mutableListOf<View>()
        collectTargets(root, root, targets)
        val animatedTargets = targets.take(MAX_ANIMATED_VIEWS)

        // Hide animated views immediately to avoid one-frame flash in final position.
        animatedTargets.forEach { view ->
            view.animate().cancel()
            view.alpha = 0f
            view.visibility = View.INVISIBLE
        }

        root.post {
            val topStartBase = -root.height.coerceAtLeast(1) * 0.22f

            animatedTargets.forEachIndexed { index, view ->
                view.animate().cancel()
                view.alpha = 0f
                view.scaleX = 0.82f
                view.scaleY = 0.82f
                view.translationY = topStartBase - (index * 6f)

                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setStartDelay((index * 28L).coerceAtMost(420L))
                    .setDuration(430L)
                    .setInterpolator(OvershootInterpolator(1.18f))
                    .withStartAction {
                        view.visibility = View.VISIBLE
                    }
                    .withEndAction {
                        view.alpha = 1f
                        view.scaleX = 1f
                        view.scaleY = 1f
                        view.translationY = 0f
                        view.visibility = View.VISIBLE
                    }
                    .start()
            }
        }
    }

    private fun collectTargets(root: ViewGroup, node: View, out: MutableList<View>) {
        if (node === root) {
            if (node is ViewGroup) {
                for (i in 0 until node.childCount) {
                    collectTargets(root, node.getChildAt(i), out)
                }
            }
            return
        }

        if (node.tag == DRIFT_TAG) return
        if (node.visibility != View.VISIBLE) return

        out.add(node)

        if (node is ViewGroup) {
            for (i in 0 until node.childCount) {
                collectTargets(root, node.getChildAt(i), out)
            }
        }
    }
}
