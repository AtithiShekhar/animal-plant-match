package com.datawaves.animalmatch.game

import com.datawaves.animalmatch.data.models.TilePos

/**
 * Padded grid. Internal storage is (cols+2) * (rows+2) so we always have a 1-cell empty ring
 * around the playable area — that ring is where the connect-line is allowed to route when it
 * leaves the board (see screenshots img_10/img_12/img_14).
 *
 * Coordinate convention (public API):
 *   playable cells: col in 0 until cols, row in 0 until rows
 *   border cells:   col in -1..cols,   row in -1..rows  (always 0 = empty)
 *
 * Cell values: 0 = empty, 1..N = face index into the level's theme tile list.
 */
class Board(val cols: Int, val rows: Int) {
    val pCols: Int = cols + 2
    val pRows: Int = rows + 2
    private val data = IntArray(pCols * pRows)

    fun inPlayable(c: Int, r: Int): Boolean = c in 0 until cols && r in 0 until rows
    fun inPadded(c: Int, r: Int): Boolean = c in -1..cols && r in -1..rows

    fun get(c: Int, r: Int): Int {
        if (!inPadded(c, r)) return -1
        return data[idx(c, r)]
    }

    fun set(c: Int, r: Int, v: Int) {
        require(inPlayable(c, r)) { "set must be within playable area: ($c,$r)" }
        data[idx(c, r)] = v
    }

    fun isEmpty(c: Int, r: Int): Boolean = get(c, r) == 0

    private fun idx(c: Int, r: Int) = (r + 1) * pCols + (c + 1)

    val remaining: Int get() = data.count { it > 0 }

    fun nonEmptyPositions(): List<TilePos> {
        val out = ArrayList<TilePos>()
        for (r in 0 until rows) for (c in 0 until cols) if (get(c, r) > 0) out.add(TilePos(c, r))
        return out
    }

    fun snapshot(): IntArray = data.copyOf()
    fun restore(snap: IntArray) { snap.copyInto(data) }
}
