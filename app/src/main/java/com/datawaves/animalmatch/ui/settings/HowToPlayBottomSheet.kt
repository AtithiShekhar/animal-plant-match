package com.datawaves.animalmatch.ui.settings

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import com.datawaves.animalmatch.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet that explains the game rules.
 */
class HowToPlayBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_how_to_play, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<ImageButton>(R.id.closeBtn)?.setOnClickListener { dismiss() }

        val palette = intArrayOf(
            Color.parseColor("#22D3EE"),
            Color.parseColor("#A78BFA"),
            Color.parseColor("#F472B6"),
            Color.parseColor("#FB923C"),
            Color.parseColor("#34D399"),
            Color.parseColor("#FACC15")
        )
        val ids = intArrayOf(
            R.id.num1, R.id.num2, R.id.num3,
            R.id.num4, R.id.num5, R.id.num6
        )
        for (i in ids.indices) {
            val tv = view.findViewById<TextView>(ids[i]) ?: continue
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette[i])
            }
            tv.background = bg
        }
    }
}
