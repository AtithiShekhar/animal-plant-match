package com.datawaves.animalmatch.data.models

data class GridSpec(val cols: Int, val rows: Int)

data class Powerups(val hint: Int, val bomb: Int, val shuffle: Int) {
    companion object {
        val ZERO = Powerups(0, 0, 0)
    }
    operator fun plus(o: Powerups) = Powerups(hint + o.hint, bomb + o.bomb, shuffle + o.shuffle)
}

data class ThemeDef(
    val id: String,
    val label: String,
    val tiles: List<String>
)

data class Badge(
    val id: String,
    val label: String,
    val drawableName: String
)

data class Level(
    val id: Int,
    val themeId: String,
    val grid: GridSpec,
    val timerSec: Int,
    val targetSunReward: Int,
    val badgeId: String,
    val badgeCount: Int,
    val powerups: Powerups,
    val unlocksFeature: String?,
    val uniqueFaces: Int,
    val layout: List<List<Int>>
)

data class LevelCatalog(
    val themes: Map<String, ThemeDef>,
    val badges: Map<String, Badge>,
    val levels: List<Level>
) {
    fun level(id: Int): Level = levels.first { it.id == id }
    fun theme(id: String): ThemeDef = themes.getValue(id)
    fun badge(id: String): Badge = badges.getValue(id)
}

data class TilePos(val col: Int, val row: Int) {
    override fun toString() = "($col,$row)"
}

enum class Dir(val dc: Int, val dr: Int) {
    UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);
    fun opposite(): Dir = when (this) { UP -> DOWN; DOWN -> UP; LEFT -> RIGHT; RIGHT -> LEFT }
}

data class ConnectPath(val points: List<TilePos>) {
    val segments: Int get() = points.size - 1
}

sealed class MatchResult {
    object Selected : MatchResult()
    object Deselected : MatchResult()
    data class Matched(val a: TilePos, val b: TilePos, val path: ConnectPath) : MatchResult()
    data class InvalidPair(val a: TilePos, val b: TilePos) : MatchResult()
}
