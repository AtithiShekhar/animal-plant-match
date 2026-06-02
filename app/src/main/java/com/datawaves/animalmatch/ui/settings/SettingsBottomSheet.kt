package com.datawaves.animalmatch.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import com.datawaves.animalmatch.R
import com.datawaves.animalmatch.data.Progress
import com.datawaves.animalmatch.util.HapticsManager
import com.datawaves.animalmatch.util.Prefs
import com.datawaves.animalmatch.util.SoundManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet with sound/music/haptics toggles, progress stat, reset, and how-to-play link.
 */
class SettingsBottomSheet : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val prefs = Prefs.get(ctx)

        val toggleSound = view.findViewById<SwitchCompat>(R.id.toggleSound)
        val toggleMusic = view.findViewById<SwitchCompat>(R.id.toggleMusic)
        val toggleHaptics = view.findViewById<SwitchCompat>(R.id.toggleHaptics)
        val statText = view.findViewById<TextView>(R.id.statText)
        val howToPlayBtn = view.findViewById<Button>(R.id.howToPlayBtn)
        val resetBtn = view.findViewById<Button>(R.id.resetBtn)
        val closeBtn = view.findViewById<ImageButton>(R.id.closeBtn)

        toggleSound.isChecked = prefs.soundEnabled
        toggleMusic.isChecked = prefs.musicEnabled
        toggleHaptics.isChecked = prefs.hapticsEnabled

        toggleSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.soundEnabled = isChecked
            SoundManager.setEnabled(isChecked)
        }
        toggleMusic.setOnCheckedChangeListener { _, isChecked ->
            prefs.musicEnabled = isChecked
        }
        toggleHaptics.setOnCheckedChangeListener { _, isChecked ->
            prefs.hapticsEnabled = isChecked
            HapticsManager.setEnabled(isChecked)
        }

        val cleared = Progress.get(ctx).highestCompleted.coerceAtMost(30)
        statText.text = "Levels cleared: $cleared / 30"

        howToPlayBtn.setOnClickListener {
            dismiss()
            HowToPlayBottomSheet().show(parentFragmentManager, "howToPlay")
        }

        resetBtn.setOnClickListener {
            AlertDialog.Builder(ctx)
                .setTitle("Reset progress?")
                .setMessage("This will erase all level progress. Continue?")
                .setPositiveButton("Reset") { _, _ ->
                    ctx.getSharedPreferences("progress", Context.MODE_PRIVATE)
                        .edit().clear().apply()
                    dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        closeBtn.setOnClickListener { dismiss() }
    }
}
