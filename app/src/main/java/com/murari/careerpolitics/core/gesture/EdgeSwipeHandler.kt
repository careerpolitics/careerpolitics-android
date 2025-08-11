package com.murari.careerpolitics.core.gesture

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

class EdgeSwipeHandler(
    private val view: View,
    private val onTrigger: () -> Unit
) : View.OnTouchListener {

    private var startX = 0f
    private var startY = 0f
    private var triggered = false
    private val touchSlop: Float = 16f * view.resources.displayMetrics.density

    init {
        view.setOnTouchListener(this)
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        event ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                triggered = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startX
                val dy = kotlin.math.abs(event.rawY - startY)
                if (!triggered && dx > touchSlop && dy < 3 * touchSlop) {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    triggered = true
                }
                return triggered
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (triggered) {
                    onTrigger()
                    triggered = false
                    return true
                }
            }
        }
        return false
    }
}