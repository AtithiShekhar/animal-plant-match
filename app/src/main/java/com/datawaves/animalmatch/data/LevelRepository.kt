package com.datawaves.animalmatch.data

import android.content.Context
import com.datawaves.animalmatch.data.models.Badge
import com.datawaves.animalmatch.data.models.GridSpec
import com.datawaves.animalmatch.data.models.Level
import com.datawaves.animalmatch.data.models.LevelCatalog
import com.datawaves.animalmatch.data.models.Powerups
import com.datawaves.animalmatch.data.models.ThemeDef
import org.json.JSONObject

/**
 * Loads `assets/levels.json` once and caches the parsed catalog.
 *
 * JSON layout convention: each level's `layout` is an array of row strings, where each row is
 * a space-separated set of tokens. "." = empty. "1".."N" = 1-based index into the level's
 * theme.tiles list. The parser validates that every face count is even and that grid dims match.
 */
object LevelRepository {
    @Volatile private var cached: LevelCatalog? = null

    fun get(context: Context): LevelCatalog = cached ?: synchronized(this) {
        cached ?: parse(context.assets.open("levels.json").bufferedReader().use { it.readText() })
            .also { cached = it }
    }

    fun parse(json: String): LevelCatalog {
        val root = JSONObject(json)
        val themes = HashMap<String, ThemeDef>()
        val themesJson = root.getJSONObject("themes")
        val themeIter = themesJson.keys()
        while (themeIter.hasNext()) {
            val id = themeIter.next()
            val obj = themesJson.getJSONObject(id)
            val arr = obj.getJSONArray("tiles")
            val tiles = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) tiles.add(arr.getString(i))
            themes[id] = ThemeDef(id, obj.getString("label"), tiles)
        }
        val badges = HashMap<String, Badge>()
        val badgesJson = root.getJSONArray("badges")
        for (i in 0 until badgesJson.length()) {
            val obj = badgesJson.getJSONObject(i)
            val id = obj.getString("id")
            badges[id] = Badge(id, obj.getString("label"), obj.getString("drawable"))
        }
        val levels = ArrayList<Level>()
        val lvls = root.getJSONArray("levels")
        for (i in 0 until lvls.length()) {
            val obj = lvls.getJSONObject(i)
            val gridObj = obj.getJSONObject("grid")
            val grid = GridSpec(gridObj.getInt("cols"), gridObj.getInt("rows"))
            val pup = obj.getJSONObject("powerups")
            val powerups = Powerups(pup.optInt("hint", 0), pup.optInt("bomb", 0), pup.optInt("shuffle", 0))
            val layoutArr = obj.getJSONArray("layout")
            require(layoutArr.length() == grid.rows) {
                "level ${obj.getInt("id")}: layout has ${layoutArr.length()} rows, grid says ${grid.rows}"
            }
            val layout = ArrayList<List<Int>>(grid.rows)
            for (r in 0 until grid.rows) {
                val raw = layoutArr.getString(r).trim().split(Regex("\\s+"))
                require(raw.size == grid.cols) {
                    "level ${obj.getInt("id")} row $r: ${raw.size} tokens, grid says ${grid.cols} cols"
                }
                layout.add(raw.map { tok -> if (tok == ".") 0 else tok.toInt() })
            }
            // even-count validation
            val counts = HashMap<Int, Int>()
            for (row in layout) for (v in row) if (v > 0) counts.merge(v, 1) { a, b -> a + b }
            for ((face, n) in counts) require(n % 2 == 0) {
                "level ${obj.getInt("id")}: face $face has odd count $n"
            }
            levels.add(
                Level(
                    id = obj.getInt("id"),
                    themeId = obj.getString("theme"),
                    grid = grid,
                    timerSec = obj.getInt("timerSec"),
                    targetSunReward = obj.getInt("targetSunReward"),
                    badgeId = obj.getString("badge"),
                    badgeCount = obj.getInt("badgeCount"),
                    powerups = powerups,
                    unlocksFeature = if (obj.isNull("unlocksFeature")) null else obj.getString("unlocksFeature"),
                    uniqueFaces = obj.getInt("uniqueFaces"),
                    layout = layout
                )
            )
        }
        return LevelCatalog(themes, badges, levels)
    }
}
