package com.datawaves.animalmatch.game

import com.datawaves.animalmatch.data.models.ConnectPath
import com.datawaves.animalmatch.data.models.Dir
import com.datawaves.animalmatch.data.models.TilePos
import kotlin.math.max
import kotlin.math.min

/**
 * Pathfinding for the connect-tiles rule:
 *
 *   • Two tiles match if they share the same face index AND can be connected by an axis-aligned
 *     polyline that has at most 3 segments (= at most 2 turns).
 *   • The polyline travels through cells whose value is 0 (empty) — both empty playable cells
 *     and the 1-cell padded border ring around the board.
 *   • The two endpoint tiles are themselves non-empty, but they are still allowed as the start
 *     and end vertices of the path.
 *
 * Returns null if no such path exists. When multiple paths exist, returns one with the fewest
 * segments (1-segment > 2-segment > 3-segment).
 */
object PathFinder {

    fun find(board: Board, a: TilePos, b: TilePos): ConnectPath? {
        if (a == b) return null
        // Same row
        if (a.row == b.row && straightFree(board, a, b)) {
            return ConnectPath(listOf(a, b))
        }
        // Same col
        if (a.col == b.col && straightFree(board, a, b)) {
            return ConnectPath(listOf(a, b))
        }
        // 2 segments — one elbow at (a.col, b.row) or (b.col, a.row)
        val elbow1 = TilePos(a.col, b.row)
        if (board.isEmpty(elbow1.col, elbow1.row) &&
            straightFree(board, a, elbow1) && straightFree(board, elbow1, b)
        ) {
            return ConnectPath(listOf(a, elbow1, b))
        }
        val elbow2 = TilePos(b.col, a.row)
        if (board.isEmpty(elbow2.col, elbow2.row) &&
            straightFree(board, a, elbow2) && straightFree(board, elbow2, b)
        ) {
            return ConnectPath(listOf(a, elbow2, b))
        }
        // 3 segments — try every column between -1..cols as the vertical channel,
        // and every row between -1..rows as the horizontal channel.
        // Iterate proximity-first so we return a short bend when one exists.
        val cCandidates = orderedAround(a.col, b.col, -1, board.cols)
        for (c in cCandidates) {
            if (c == a.col || c == b.col) continue
            val p1 = TilePos(c, a.row)
            val p2 = TilePos(c, b.row)
            if (!board.isEmpty(p1.col, p1.row)) continue
            if (!board.isEmpty(p2.col, p2.row)) continue
            if (!straightFree(board, a, p1)) continue
            if (!straightFree(board, p1, p2)) continue
            if (!straightFree(board, p2, b)) continue
            return ConnectPath(listOf(a, p1, p2, b))
        }
        val rCandidates = orderedAround(a.row, b.row, -1, board.rows)
        for (r in rCandidates) {
            if (r == a.row || r == b.row) continue
            val p1 = TilePos(a.col, r)
            val p2 = TilePos(b.col, r)
            if (!board.isEmpty(p1.col, p1.row)) continue
            if (!board.isEmpty(p2.col, p2.row)) continue
            if (!straightFree(board, a, p1)) continue
            if (!straightFree(board, p1, p2)) continue
            if (!straightFree(board, p2, b)) continue
            return ConnectPath(listOf(a, p1, p2, b))
        }
        return null
    }

    /** True if every cell strictly between a and b on the same row/col is empty.
     *  Endpoints a and b are NOT required to be empty (they are the tile vertices). */
    private fun straightFree(board: Board, a: TilePos, b: TilePos): Boolean {
        require(a.col == b.col || a.row == b.row) { "not aligned: $a $b" }
        if (a.col == b.col) {
            val c = a.col
            val r0 = min(a.row, b.row) + 1
            val r1 = max(a.row, b.row) - 1
            for (r in r0..r1) if (!board.isEmpty(c, r)) return false
        } else {
            val r = a.row
            val c0 = min(a.col, b.col) + 1
            val c1 = max(a.col, b.col) - 1
            for (c in c0..c1) if (!board.isEmpty(c, r)) return false
        }
        return true
    }

    /** Returns indices in [lo..hi] ordered by closeness to the segment [min(x,y) .. max(x,y)]. */
    private fun orderedAround(x: Int, y: Int, lo: Int, hi: Int): IntArray {
        val all = IntArray(hi - lo + 1) { it + lo }
        val midLo = min(x, y)
        val midHi = max(x, y)
        // Sort by distance from the [midLo..midHi] interval (0 inside, positive outside).
        return all.toTypedArray().sortedBy { v ->
            when {
                v in midLo..midHi -> 0
                v < midLo -> midLo - v
                else -> v - midHi
            }
        }.toIntArray()
    }
}
