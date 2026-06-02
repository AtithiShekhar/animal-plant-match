package com.datawaves.animalmatch.ui.start

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.datawaves.animalmatch.R
import com.datawaves.animalmatch.data.LevelRepository
import com.datawaves.animalmatch.databinding.DialogLevelStartBinding
import com.datawaves.animalmatch.ui.play.GameActivity

class LevelStartDialog : DialogFragment() {

    companion object {
        const val ARG_LEVEL_ID = "level_id"
        fun newInstance(levelId: Int): LevelStartDialog = LevelStartDialog().apply {
            arguments = Bundle().apply { putInt(ARG_LEVEL_ID, levelId) }
        }
    }

    private var _binding: DialogLevelStartBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Try a project-specific style, else fall back to NO_TITLE / NO_FRAME.
        val style = resolveStyle("Style_LevelStartDialog")
            ?: resolveStyle("Theme_AnimalMatch_NoBg")
        if (style != null) setStyle(STYLE_NO_TITLE, style) else setStyle(STYLE_NO_FRAME, 0)
        isCancelable = true
    }

    private fun resolveStyle(name: String): Int? {
        val ctx = context ?: return null
        return try {
            val id = ctx.resources.getIdentifier(name, "style", ctx.packageName)
            if (id != 0) id else null
        } catch (_: Throwable) { null }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogLevelStartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val levelId = arguments?.getInt(ARG_LEVEL_ID, 1) ?: 1
        val ctx = requireContext()

        val level = try {
            LevelRepository.get(ctx).level(levelId)
        } catch (_: Throwable) { null }

        // Header text — try generated binding first, then the existing XML id "headerLevelText",
        // then a legacy "levelHeader" id (back-compat).
        val headerText = ctx.getString(R.string.level_label_fmt, levelId)
        safe { binding.headerLevelText.text = headerText }
        setHeaderText(ctx, view, headerText)

        val secs = level?.timerSec ?: 60
        val mm = secs / 60
        val ss = secs % 60
        safe { binding.timerText.text = String.format("%02d:%02d", mm, ss) }
        safe { binding.objectiveText.text = ctx.getString(R.string.clear_all_tiles) }

        safe {
            binding.continueBtn.setOnClickListener {
                val intent = GameActivity.newIntent(ctx, levelId)
                startActivity(intent)
                // Apply a slide-in for the GameActivity entrance.
                requireActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
                dismissAllowingStateLoss()
            }
        }
        safe {
            binding.closeBtn.setOnClickListener { dismissWithAnim() }
        }

        // Tap-outside dismiss is already handled by setCanceledOnTouchOutside via window dim.
        // Entrance animation on the card root.
        playEntranceAnim(view)
    }

    private fun setHeaderText(ctx: Context, root: View, text: String) {
        // Preferred ID per the current layout.
        try {
            val id = ctx.resources.getIdentifier("headerLevelText", "id", ctx.packageName)
            if (id != 0) {
                val tv = root.findViewById<TextView?>(id)
                if (tv != null) { tv.text = text; return }
            }
        } catch (_: Throwable) { /* ignore */ }
        // Back-compat fallback.
        try {
            val id = ctx.resources.getIdentifier("levelHeader", "id", ctx.packageName)
            if (id != 0) {
                val tv = root.findViewById<TextView?>(id)
                tv?.text = text
            }
        } catch (_: Throwable) { /* ignore */ }
    }

    private fun playEntranceAnim(root: View) {
        // The card itself is the first child of the FrameLayout root; animate it directly
        // so the dim background fades in via the dialog window rather than our content.
        val card: View = (root as? ViewGroup)?.getChildAt(0) ?: root
        card.alpha = 0f
        card.translationY = 80f * resources.displayMetrics.density
        val fade = ObjectAnimator.ofFloat(card, "alpha", 0f, 1f)
        val slide = ObjectAnimator.ofFloat(card, "translationY", card.translationY, 0f)
        AnimatorSet().apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            playTogether(fade, slide)
            start()
        }
    }

    private fun dismissWithAnim() {
        val root = view ?: run { dismissAllowingStateLoss(); return }
        val card: View = (root as? ViewGroup)?.getChildAt(0) ?: root
        val fade = ObjectAnimator.ofFloat(card, "alpha", card.alpha, 0f)
        val slide = ObjectAnimator.ofFloat(card, "translationY", card.translationY, 80f * resources.displayMetrics.density)
        AnimatorSet().apply {
            duration = 200L
            playTogether(fade, slide)
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (isAdded) dismissAllowingStateLoss()
                }
            })
            start()
        }
    }

    override fun onStart() {
        super.onStart()
        val d: Dialog? = dialog
        d?.window?.setBackgroundDrawable(ColorDrawable(0x99000000.toInt()))
        d?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // Tap-outside cancels.
        d?.setCanceledOnTouchOutside(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (_: Throwable) { /* swallow missing views */ }
    }
}
