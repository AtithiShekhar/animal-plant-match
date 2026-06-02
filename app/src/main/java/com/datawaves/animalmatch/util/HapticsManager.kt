package com.datawaves.animalmatch.util

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View

object HapticsManager {
    private var enabled = true
    fun init(context: Context) { enabled = Prefs.get(context).hapticsEnabled }
    fun setEnabled(b: Boolean) { enabled = b }
    fun lightTick(v: View?) { if (enabled && v != null) v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    fun mediumImpact(v: View?) { if (enabled && v != null) v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }
    fun heavyImpact(v: View?) { if (enabled && v != null) v.performHapticFeedback(HapticFeedbackConstants.CONFIRM) }
    fun negative(v: View?) { if (enabled && v != null) v.performHapticFeedback(HapticFeedbackConstants.REJECT) }
}
