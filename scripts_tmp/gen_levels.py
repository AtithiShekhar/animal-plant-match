#!/usr/bin/env python3
"""Generate levels.json for AnimalMatch. Deterministic, validated."""
import json
import os
import random

OUT = r"C:/Users/atithi shekhar/AndroidStudioProjects/animalmatch/app/src/main/assets/levels.json"

THEMES = {
    "fruit_a": {
        "label": "Orchard",
        "tiles": [f"fruit_a_{i:02d}" for i in range(1, 26)],
    },
    "fruit_b": {
        "label": "Harvest",
        "tiles": [f"fruit_b_{i:02d}" for i in range(1, 10)],
    },
    "birds": {
        "label": "Sky Song",
        "tiles": [
            "bird_owl", "bird_chick", "bird_chicken", "bird_dove", "bird_duck",
            "bird_eagle", "bird_flamingo", "bird_honeybee", "bird_hummingbird",
            "bird_parrot", "bird_peacock", "bird_pigeon", "bird_rooster",
            "bird_sparrow", "bird_swan", "bird_turkey", "bird_woodpecker",
            "bird_bat", "bird_butterfly", "bird_crow", "bird_dodo",
        ],
    },
    "veges": {
        "label": "Greens",
        "tiles": [
            "veg_arugula", "veg_beets", "veg_bittergourd", "veg_bokchoy",
            "veg_broccoli", "veg_cabbage", "veg_carrot", "veg_cauliflower",
            "veg_collardgreen", "veg_corn", "veg_cucumber", "veg_eggplant",
            "veg_elephantfoot", "veg_fenugreek", "veg_garlic", "veg_ginger",
            "veg_greenbean", "veg_kale", "veg_leeks", "veg_lettuce",
            "veg_mushroom", "veg_okra", "veg_onion", "veg_peas", "veg_pepper",
            "veg_potato", "veg_pumpkin", "veg_radicchio", "veg_radish",
            "veg_rutabaga", "veg_spinach", "veg_squash", "veg_sweetpotato",
            "veg_tomato", "veg_turnip", "veg_whitemushroom", "veg_zucchini",
        ],
    },
}

BADGES = [
    {"id": "wisdom",   "label": "Wisdom",   "drawable": "veg_broccoli"},
    {"id": "joy",      "label": "Joy",      "drawable": "fruit_a_10"},
    {"id": "harmony",  "label": "Harmony",  "drawable": "bird_dove"},
    {"id": "energy",   "label": "Energy",   "drawable": "bird_honeybee"},
    {"id": "hope",     "label": "Hope",     "drawable": "veg_tomato"},
    {"id": "love",     "label": "Love",     "drawable": "fruit_b_07"},
    {"id": "strength", "label": "Strength", "drawable": "bird_eagle"},
    {"id": "courage",  "label": "Courage",  "drawable": "bird_rooster"},
    {"id": "peace",    "label": "Peace",    "drawable": "bird_swan"},
    {"id": "light",    "label": "Light",    "drawable": "bird_butterfly"},
    {"id": "bloom",    "label": "Bloom",    "drawable": "veg_pumpkin"},
    {"id": "spark",    "label": "Spark",    "drawable": "bird_parrot"},
    {"id": "glow",     "label": "Glow",     "drawable": "bird_flamingo"},
    {"id": "dream",    "label": "Dream",    "drawable": "bird_owl"},
    {"id": "voyage",   "label": "Voyage",   "drawable": "bird_peacock"},
]

# (id, theme, cols, rows, uniqueFaces, pairs, timerSec, reward, badge, h, b, s, unlocks, badgeCount)
LEVELS_SPEC = [
    (1,  "fruit_a", 6, 5,  6, 12, 120, 100, "wisdom",   3, 1, 2, None,       1611),
    (2,  "fruit_a", 6, 6,  8, 15, 110, 110, "joy",      3, 1, 2, None,       1542),
    (3,  "birds",   6, 7, 10, 18, 100, 120, "harmony",  3, 1, 2, "bomb",     1438),
    (4,  "birds",   7, 7, 10, 20,  90, 130, "energy",   2, 2, 2, None,       1326),
    (5,  "veges",   7, 8, 12, 24,  90, 140, "hope",     2, 2, 2, "shuffle",  1207),
    (6,  "veges",   7, 8, 13, 26,  85, 150, "love",     2, 2, 3, None,       1188),
    (7,  "fruit_b", 7, 8,  9, 26,  80, 160, "strength", 2, 2, 3, None,       1102),
    (8,  "fruit_a", 7, 8, 14, 28,  75, 170, "courage",  2, 2, 3, None,       1057),
    (9,  "birds",   7, 9, 15, 30,  70, 180, "peace",    2, 2, 3, None,       1024),
    (10, "veges",   7, 9, 15, 30,  70, 190, "light",    2, 3, 3, "bigbomb",  1500),
    (11, "fruit_a", 7, 9, 16, 32,  65, 200, "bloom",    2, 3, 3, None,       1411),
    (12, "veges",   7,10, 17, 34,  65, 210, "spark",    2, 3, 3, None,       1380),
    (13, "birds",   7,10, 17, 35,  60, 220, "glow",     2, 3, 3, None,       1299),
    (14, "fruit_b", 7,10,  9, 36,  60, 230, "dream",    1, 3, 3, None,       1248),
    (15, "veges",   7,10, 18, 36,  50, 250, "voyage",   1, 3, 3, "finale",   1196),
]


def build_face_list(unique_faces, pairs):
    """Build list of face tokens (each face count even) totaling pairs*2."""
    # Distribute pairs across faces as evenly as possible.
    # Each face must get >=1 pair (so count >=2 and even).
    base = pairs // unique_faces
    rem = pairs - base * unique_faces
    pair_per_face = [base] * unique_faces
    for i in range(rem):
        pair_per_face[i] += 1
    # Ensure each face has at least one pair
    for i in range(unique_faces):
        if pair_per_face[i] == 0:
            # find one to donate from a face with >=2
            for j in range(unique_faces):
                if pair_per_face[j] >= 2:
                    pair_per_face[j] -= 1
                    pair_per_face[i] += 1
                    break
    tokens = []
    for face_idx, p in enumerate(pair_per_face, start=1):
        tokens.extend([str(face_idx)] * (p * 2))
    assert len(tokens) == pairs * 2
    # Sanity: each count even
    from collections import Counter
    c = Counter(tokens)
    for face, n in c.items():
        assert n % 2 == 0, f"face {face} count {n} odd"
    return tokens


def make_layout(cols, rows, unique_faces, pairs, seed):
    """Place tokens in grid with empty cells scattered. Returns list of row strings."""
    total = cols * rows
    non_empty = pairs * 2
    empties = total - non_empty
    assert empties >= 0, f"Too many pairs for grid {cols}x{rows}: need {non_empty} non-empty, have {total}"

    tokens = build_face_list(unique_faces, pairs)
    rng = random.Random(seed)
    # Shuffle tokens for variety but deterministic via seed.
    rng.shuffle(tokens)

    # Choose empty cell positions in a visually interesting way:
    # Strategy depends on seed mod a few patterns. We'll pick a pattern and add small jitter.
    cells = [(r, c) for r in range(rows) for c in range(cols)]

    # Build a "weight" for each cell - cells likelier to be empty have higher weight.
    # We use a couple of patterns to vary visuals across levels.
    pattern = seed % 4
    weights = []
    cr = (rows - 1) / 2.0
    cc = (cols - 1) / 2.0
    for (r, c) in cells:
        dr = r - cr
        dc = c - cc
        dist2 = dr * dr + dc * dc
        if pattern == 0:
            # holes in center
            w = 1.0 / (1.0 + dist2)
        elif pattern == 1:
            # holes on edges (frame is empty)
            edge_dist = min(r, rows - 1 - r, c, cols - 1 - c)
            w = 1.0 / (1.0 + edge_dist)
        elif pattern == 2:
            # diagonal stripes
            w = 1.0 if ((r + c) % 3 == 0) else 0.3
        else:
            # scattered (uniform-ish)
            w = 1.0
        # add jitter
        w *= 0.5 + rng.random()
        weights.append(w)

    # Pick `empties` cells with highest weights, but if pattern would over-cluster,
    # we still need exactly `empties` cells.
    indexed = list(range(len(cells)))
    indexed.sort(key=lambda i: -weights[i])
    empty_set = set(indexed[:empties])

    # Now fill grid: empty cells get ".", others get tokens.pop()
    grid = [["." for _ in range(cols)] for _ in range(rows)]
    ti = 0
    for idx, (r, c) in enumerate(cells):
        if idx in empty_set:
            grid[r][c] = "."
        else:
            grid[r][c] = tokens[ti]
            ti += 1
    assert ti == len(tokens), f"placed {ti} != {len(tokens)}"

    rows_out = [" ".join(grid[r]) for r in range(rows)]
    return rows_out


def validate_layout(rows_out, cols, rows, unique_faces, pairs, level_id):
    assert len(rows_out) == rows, f"L{level_id}: rows {len(rows_out)} != {rows}"
    from collections import Counter
    counts = Counter()
    for r, row in enumerate(rows_out):
        toks = row.split()
        assert len(toks) == cols, f"L{level_id} row {r}: {len(toks)} != {cols}"
        for t in toks:
            if t != ".":
                counts[t] += 1
    for face, n in counts.items():
        assert n % 2 == 0, f"L{level_id}: face {face} count {n} odd"
    total_non_empty = sum(counts.values())
    assert total_non_empty == pairs * 2, f"L{level_id}: non-empty {total_non_empty} != {pairs*2}"
    # all faces present 1..unique_faces
    for f in range(1, unique_faces + 1):
        assert str(f) in counts, f"L{level_id}: face {f} missing"


def build():
    levels = []
    for spec in LEVELS_SPEC:
        (lid, theme, cols, rows, uf, pairs, ts, rew, badge, h, b, s, unlocks, bc) = spec
        layout = make_layout(cols, rows, uf, pairs, seed=lid * 1009 + 17)
        validate_layout(layout, cols, rows, uf, pairs, lid)
        levels.append({
            "id": lid,
            "theme": theme,
            "grid": {"cols": cols, "rows": rows},
            "timerSec": ts,
            "targetSunReward": rew,
            "badge": badge,
            "badgeCount": bc,
            "powerups": {"hint": h, "bomb": b, "shuffle": s},
            "unlocksFeature": unlocks,
            "uniqueFaces": uf,
            "layout": layout,
        })

    catalog = {
        "version": 1,
        "themes": THEMES,
        "badges": BADGES,
        "levels": levels,
    }

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(catalog, f, ensure_ascii=False, indent=2)
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    build()
