package com.datawaves.animalmatch.ui.play

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Stub container for the HUD. The active layout (`activity_game.xml`) manages HUD widgets
 * inline via their own IDs; this class exists as a future hook for encapsulating HUD styling
 * (timer tint thresholds, badge layout) without disturbing the activity.
 */
class HudView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle)
