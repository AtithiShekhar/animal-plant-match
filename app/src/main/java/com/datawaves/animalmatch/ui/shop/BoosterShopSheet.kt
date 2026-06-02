package com.datawaves.animalmatch.ui.shop

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.datawaves.animalmatch.R
import com.datawaves.animalmatch.data.Wallet
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet that lets the player spend coins to refill hints / bombs / shuffles.
 *
 * Usage: BoosterShopSheet.newInstance(focus).show(supportFragmentManager, "shop") where
 * [focus] is one of "hint" / "bomb" / "shuffle" / null. When focused, the corresponding
 * row briefly pulses to draw the eye.
 */
class BoosterShopSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_FOCUS = "focus"
        const val FOCUS_HINT = "hint"
        const val FOCUS_BOMB = "bomb"
        const val FOCUS_SHUFFLE = "shuffle"

        /** Coin prices. Tune in one place. */
        const val PRICE_HINT = 30
        const val PRICE_BOMB = 80
        const val PRICE_SHUFFLE = 60

        fun newInstance(focus: String? = null): BoosterShopSheet = BoosterShopSheet().apply {
            arguments = Bundle().apply { focus?.let { putString(ARG_FOCUS, it) } }
        }
    }

    /** Optional listener so the host activity can refresh its HUD counts after a buy. */
    var onWalletChanged: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_booster_shop, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val wallet = Wallet.get(ctx)

        val coinChip = view.findViewById<TextView>(R.id.shopCoinChip)
        val hintOwned = view.findViewById<TextView>(R.id.shopHintOwned)
        val bombOwned = view.findViewById<TextView>(R.id.shopBombOwned)
        val shuffleOwned = view.findViewById<TextView>(R.id.shopShuffleOwned)
        val hintBuy = view.findViewById<TextView>(R.id.shopHintBuy)
        val bombBuy = view.findViewById<TextView>(R.id.shopBombBuy)
        val shuffleBuy = view.findViewById<TextView>(R.id.shopShuffleBuy)
        val closeBtn = view.findViewById<Button>(R.id.shopCloseBtn)

        fun refresh() {
            coinChip.text = wallet.coins.toString()
            hintOwned.text = getString(R.string.shop_owned_fmt, wallet.hints)
            bombOwned.text = getString(R.string.shop_owned_fmt, wallet.bombs)
            shuffleOwned.text = getString(R.string.shop_owned_fmt, wallet.shuffles)
            hintBuy.text = getString(R.string.shop_buy_fmt, 1, PRICE_HINT)
            bombBuy.text = getString(R.string.shop_buy_fmt, 1, PRICE_BOMB)
            shuffleBuy.text = getString(R.string.shop_buy_fmt, 1, PRICE_SHUFFLE)
        }
        refresh()

        hintBuy.setOnClickListener {
            if (tryBuy(wallet, PRICE_HINT) { wallet.addHints(1) }) {
                refresh(); pulse(hintBuy)
                Toast.makeText(ctx, getString(R.string.shop_purchased_fmt, 1, getString(R.string.shop_hints)), Toast.LENGTH_SHORT).show()
                onWalletChanged?.invoke()
            }
        }
        bombBuy.setOnClickListener {
            if (tryBuy(wallet, PRICE_BOMB) { wallet.addBombs(1) }) {
                refresh(); pulse(bombBuy)
                Toast.makeText(ctx, getString(R.string.shop_purchased_fmt, 1, getString(R.string.shop_bombs)), Toast.LENGTH_SHORT).show()
                onWalletChanged?.invoke()
            }
        }
        shuffleBuy.setOnClickListener {
            if (tryBuy(wallet, PRICE_SHUFFLE) { wallet.addShuffles(1) }) {
                refresh(); pulse(shuffleBuy)
                Toast.makeText(ctx, getString(R.string.shop_purchased_fmt, 1, getString(R.string.shop_shuffles)), Toast.LENGTH_SHORT).show()
                onWalletChanged?.invoke()
            }
        }

        closeBtn.setOnClickListener { dismiss() }

        // Focus pulse on the row matching the powerup the player tried to use.
        val focus = arguments?.getString(ARG_FOCUS)
        val rowId = when (focus) {
            FOCUS_HINT -> R.id.shopRowHint
            FOCUS_BOMB -> R.id.shopRowBomb
            FOCUS_SHUFFLE -> R.id.shopRowShuffle
            else -> 0
        }
        if (rowId != 0) {
            val row = view.findViewById<LinearLayout>(rowId)
            row?.post { pulse(row) }
        }
    }

    private fun tryBuy(wallet: Wallet, price: Int, grant: () -> Unit): Boolean {
        if (!wallet.spendCoins(price)) {
            Toast.makeText(requireContext(), getString(R.string.shop_not_enough_coins), Toast.LENGTH_SHORT).show()
            return false
        }
        grant()
        return true
    }

    private fun pulse(v: View) {
        ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.08f, 1f).apply {
            duration = 260L
            start()
        }
        ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.08f, 1f).apply {
            duration = 260L
            start()
        }
    }
}
