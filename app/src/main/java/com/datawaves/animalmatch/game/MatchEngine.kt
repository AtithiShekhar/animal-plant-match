package com.datawaves.animalmatch.game

import com.datawaves.animalmatch.data.models.ConnectPath
import com.datawaves.animalmatch.data.models.Level
import com.datawaves.animalmatch.data.models.MatchResult
import com.datawaves.animalmatch.data.models.TilePos
import kotlin.random.Random

/**
 * Stateful in-memory game engine for a single level.
 *
 *   • Holds the [Board] and the currently selected tile.
 *   • [tap] feeds the player's tile tap and returns a [MatchResult].
 *   • [findHint] returns any solvable pair (used by the hint power-up).
 *   • [popRandomPair] removes a random solvable pair (bomb power-up).
 *   • [shuffle] reshuffles remaining tiles in place, guaranteeing at least one solvable pair.
 */
class MatchEngine(val level: Level, private val rng: Random = Random.Default) {
    val board: Board = Board(level.grid.cols, level.grid.rows).also { b ->
        for (r in 0 until level.grid.rows) {
            val row = level.layout[r]
            for (c in 0 until level.grid.cols) b.set(c, r, row[c])
        }
    }

    var selected: TilePos? = null
        private set

    val remaining: Int get() = board.remaining
    val isCleared: Boolean get() = board.remaining == 0

    fun tap(pos: TilePos): MatchResult {
        if (board.get(pos.col, pos.row) <= 0) return MatchResult.Deselected
        val cur = selected
        if (cur == null) {
            selected = pos
            return MatchResult.Selected
        }
        if (cur == pos) {
            selected = null
            return MatchResult.Deselected
        }
        if (board.get(cur.col, cur.row) != board.get(pos.col, pos.row)) {
            // Different face — switch selection
            selected = pos
            return MatchResult.Selected
        }
        val path = PathFinder.find(board, cur, pos)
        return if (path != null) {
            board.set(cur.col, cur.row, 0)
            board.set(pos.col, pos.row, 0)
            selected = null
            MatchResult.Matched(cur, pos, path)
        } else {
            // Invalid — keep first selection? Match-3 games usually clear. We clear.
            selected = null
            MatchResult.InvalidPair(cur, pos)
        }
    }

    fun clearSelection() { selected = null }

    fun findHint(): Triple<TilePos, TilePos, ConnectPath>? {
        val positions = board.nonEmptyPositions()
        // Group by face for O(F) instead of O(N^2) where possible.
        val byFace = positions.groupBy { board.get(it.col, it.row) }
        for ((_, list) in byFace) {
            for (i in list.indices) for (j in i + 1 until list.size) {
                val p = PathFinder.find(board, list[i], list[j]) ?: continue
                return Triple(list[i], list[j], p)
            }
        }
        return null
    }

    fun popRandomPair(): Triple<TilePos, TilePos, ConnectPath>? {
        val hint = findHint() ?: return null
        board.set(hint.first.col, hint.first.row, 0)
        board.set(hint.second.col, hint.second.row, 0)
        selected = null
        return hint
    }

    /**
     * Shuffle remaining tiles to a new arrangement that has at least one solvable pair.
     * Up to N retries; if all fail, returns false.
     */
    fun shuffle(maxAttempts: Int = 32): Boolean {
        val positions = board.nonEmptyPositions()
        if (positions.size < 2) return false
        val faces = positions.map { board.get(it.col, it.row) }
        repeat(maxAttempts) {
            val shuffled = faces.shuffled(rng)
            for (i in positions.indices) {
                val p = positions[i]
                board.set(p.col, p.row, shuffled[i])
            }
            if (findHint() != null) {
                selected = null
                return true
            }
        }
        // give up — restore was never broken because we kept assigning faces to the same positions
        return false
    }
}
