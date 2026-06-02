"""
Generates levels 16-30 (15 new tricky levels) and appends them to assets/levels.json.

Design principles:
  * Each level has a NAMED design concept (e.g. "Tower of Two", "Diamond Heart").
  * Each level has a hand-designed MASK (which cells are tokens vs empty) and a placement
    STRATEGY (how the tokens are distributed across the mask).
  * Strategies: 'random', 'mirror' (symmetric across vertical axis), 'spread' (same-face
    pairs placed far apart, forcing 3-segment routing), 'clustered' (same-face tokens
    placed adjacent, forcing order-of-operations puzzles).
  * After placement we validate two invariants:
       (a) every face count is even
       (b) at least one solvable pair exists on the initial board
    If (b) fails we re-shuffle with a new seed until it passes (up to 200 attempts).
"""

import json
import os
import random
from itertools import combinations

# ============================================================
# Mask helpers
# ============================================================


def m_rect(cols, rows):
    """Fully filled rectangle."""
    return ["X" * cols for _ in range(rows)]


def m_corners_empty(cols, rows, n_corners):
    """Rectangle with N empty corner positions (1-4)."""
    g = [list("X" * cols) for _ in range(rows)]
    coords = [(0, 0), (0, cols - 1), (rows - 1, 0), (rows - 1, cols - 1)]
    for r, c in coords[:n_corners]:
        g[r][c] = "."
    return ["".join(row) for row in g]


def m_apply_empties(mask, empties):
    """Take a mask and additionally mark these (r, c) cells empty."""
    g = [list(row) for row in mask]
    for r, c in empties:
        g[r][c] = "."
    return ["".join(row) for row in g]


def m_print(mask):
    return "\n".join(mask)


# ============================================================
# Level designs
# ============================================================
# Each entry: id, name, theme, cols, rows, timer_sec, sun_reward, badge,
# badge_count, hint/bomb/shuffle, unlocks_feature, strategy, mask, face_counts.
# face_counts: list of integers (one per unique face), MUST sum to # of X's and
# each entry MUST be even.

LEVELS = []


def L(id, name, theme, cols, rows, mask, face_counts, timer_sec, sun_reward,
      badge, badge_count, h, b, s, unlocks, strategy):
    # sanity
    assert len(mask) == rows, f"L{id} mask rows {len(mask)} != {rows}"
    for r, row in enumerate(mask):
        assert len(row) == cols, f"L{id} mask row {r} len {len(row)} != {cols}"
    fill = sum(row.count("X") for row in mask)
    assert sum(face_counts) == fill, f"L{id} sum face_counts {sum(face_counts)} != fill {fill}"
    for i, c in enumerate(face_counts):
        assert c % 2 == 0, f"L{id} face {i+1} count {c} is odd"
    LEVELS.append({
        "id": id, "name": name, "theme": theme, "cols": cols, "rows": rows,
        "uniqueFaces": len(face_counts), "pairs": fill // 2,
        "timerSec": timer_sec, "targetSunReward": sun_reward,
        "badge": badge, "badgeCount": badge_count,
        "hint": h, "bomb": b, "shuffle": s,
        "unlocksFeature": unlocks,
        "strategy": strategy,
        "mask": mask, "face_counts": face_counts,
    })


# ---------- L16  "Tower of Two" -----------------------------------
# Narrow 5-wide pillar, 3 cells wide of tokens flanked by empty columns.
# Mirror strategy: each face appears symmetrically left/right inside the pillar.
L(16, "Tower of Two", "fruit_b", 5, 10,
  mask=[".XXX.", ".XXX.", ".XXX.", ".XXX.", ".XXX.",
        ".XXX.", ".XXX.", ".XXX.", ".XXX.", ".XXX."],
  face_counts=[6, 6, 6, 6, 6],  # 5 faces × 6 each = 30
  timer_sec=60, sun_reward=260, badge="ember", badge_count=512,
  h=1, b=2, s=2, unlocks=None, strategy="mirror")


# ---------- L17  "Echo Chamber" -----------------------------------
# Almost-full 7x9 with 3 holes on a diagonal. 30 pairs, heavy repetition.
L(17, "Echo Chamber", "birds", 7, 9,
  mask=m_apply_empties(m_rect(7, 9), [(2, 2), (4, 3), (6, 4)]),
  face_counts=[4] * 15,  # 15 faces × 4 = 60
  timer_sec=55, sun_reward=270, badge="mist", badge_count=478,
  h=1, b=2, s=2, unlocks=None, strategy="random")


# ---------- L18  "Diamond Heart" ----------------------------------
# Diamond void in center + 2 corner empties for parity. 14 faces × 4 = 56.
def _mask_diamond_heart():
    g = m_rect(7, 9)
    g = m_apply_empties(g, [
        (3, 3),                  # top of diamond
        (4, 2), (4, 3), (4, 4),  # middle row
        (5, 3),                  # bottom
        (0, 0), (8, 6),          # parity corners
    ])
    return g
L(18, "Diamond Heart", "veges", 7, 9,
  mask=_mask_diamond_heart(),
  face_counts=[4] * 14,  # 14 × 4 = 56
  timer_sec=60, sun_reward=280, badge="dawn", badge_count=444,
  h=2, b=2, s=2, unlocks="freeze", strategy="spread")


# ---------- L19  "Locked Box" -------------------------------------
# Six "lock" empties spaced around the board to force long routing.
def _mask_locked_box():
    g = m_rect(7, 10)
    g = m_apply_empties(g, [
        (0, 0), (0, 6),
        (3, 0), (3, 6),
        (9, 0), (9, 6),
    ])
    return g
L(19, "Locked Box", "fruit_a", 7, 10,
  mask=_mask_locked_box(),
  face_counts=[4] * 16,  # 16 × 4 = 64
  timer_sec=60, sun_reward=290, badge="dusk", badge_count=412,
  h=2, b=2, s=2, unlocks=None, strategy="spread")


# ---------- L20  "Mirror" -----------------------------------------
# Pure mirror-symmetric layout, 7x8, 4 empties on the axis of symmetry.
def _mask_mirror():
    g = m_rect(7, 8)
    return m_apply_empties(g, [(0, 3), (3, 0), (3, 6), (7, 3)])
L(20, "Mirror", "birds", 7, 8,
  mask=_mask_mirror(),
  face_counts=[4] * 13,  # 13 × 4 = 52
  timer_sec=55, sun_reward=300, badge="river", badge_count=383,
  h=2, b=2, s=2, unlocks=None, strategy="mirror")


# ---------- L21  "Zigzag" -----------------------------------------
# Snake-like pattern of empty cells weaving down the board.
def _mask_zigzag():
    g = m_rect(7, 10)
    empties = [(0, 2), (2, 2), (2, 4), (4, 2), (4, 4),
               (6, 2), (6, 4), (8, 2), (8, 4), (9, 4)]
    return m_apply_empties(g, empties)
L(21, "Zigzag", "veges", 7, 10,
  mask=_mask_zigzag(),
  face_counts=[4] * 15,  # 15 × 4 = 60
  timer_sec=55, sun_reward=310, badge="ocean", badge_count=359,
  h=2, b=2, s=2, unlocks=None, strategy="random")


# ---------- L22  "Singleton" --------------------------------------
# 7x9 with exactly ONE empty cell. Almost everything must route through
# the outer ring. 16 faces: 15x4 + 1x2 = 62 tokens.
def _mask_singleton():
    g = m_rect(7, 9)
    return m_apply_empties(g, [(4, 3)])
L(22, "Singleton", "fruit_a", 7, 9,
  mask=_mask_singleton(),
  face_counts=[4] * 15 + [2],  # 60 + 2 = 62
  timer_sec=55, sun_reward=320, badge="summit", badge_count=331,
  h=1, b=3, s=3, unlocks=None, strategy="spread")


# ---------- L23  "Solid" ------------------------------------------
# FULLY packed 7x8. Zero empties. Outer ring is the only route.
# Clustered strategy: same-face tokens placed close, forcing the player to
# choose between many adjacent pairs.
L(23, "Solid", "birds", 7, 8,
  mask=m_rect(7, 8),
  face_counts=[4] * 14,  # 14 × 4 = 56
  timer_sec=55, sun_reward=330, badge="canyon", badge_count=308,
  h=1, b=3, s=3, unlocks="doublebomb", strategy="clustered")


# ---------- L24  "Twin Pillars" -----------------------------------
# Two 3-wide vertical pillars separated by an empty column.
def _mask_twin_pillars():
    cols, rows = 6, 10
    g = []
    for r in range(rows):
        # Empty column at index 3 (0-indexed: cols 0..2 tokens, col 3 empty, cols 4..5 tokens)
        g.append("XXX." + "XX")
    return g
L(24, "Twin Pillars", "fruit_b", 6, 10,
  mask=_mask_twin_pillars(),
  face_counts=[6, 6, 6, 6, 6, 4, 4, 4, 4, 4],  # 5×6 + 5×4 = 30+20 = 50
  timer_sec=55, sun_reward=340, badge="forest", badge_count=286,
  h=1, b=3, s=3, unlocks=None, strategy="random")


# ---------- L25  "Pyramid" ----------------------------------------
# Stacked triangular shape — wider at the top of the visible block.
def _mask_pyramid():
    rows, cols = 10, 7
    base = []
    # Tapered top: row 0 = 1 token center; row 1 = 3 center; row 2 = 5 center.
    base.append("...X...")
    base.append("..XXX..")
    base.append(".XXXXX.")
    # Middle solid (rows 3-7)
    for _ in range(5):
        base.append("XXXXXXX")
    # Tapered bottom
    base.append(".XXXXX.")
    base.append("..XXX..")
    return base
L(25, "Pyramid", "veges", 7, 10,
  mask=_mask_pyramid(),
  face_counts=[4] * 13,  # 1+3+5+7*5+5+3 = 52; 13 × 4 = 52
  timer_sec=55, sun_reward=350, badge="desert", badge_count=265,
  h=1, b=3, s=3, unlocks=None, strategy="spread")


# ---------- L26  "Frame & Core" -----------------------------------
# Solid outer frame + inner 5x6 packed core; two thin gaps between frame and core.
def _mask_frame_core():
    rows, cols = 10, 7
    # Full board
    g = [list("X" * cols) for _ in range(rows)]
    # Punch a moat between rows 1..8, cols 1 and 5
    for r in range(1, 9):
        g[r][1] = "."
        g[r][5] = "."
    return ["".join(row) for row in g]
L(26, "Frame & Core", "fruit_a", 7, 10,
  mask=_mask_frame_core(),
  face_counts=[6, 6, 6, 6, 6, 6, 6, 6, 6],  # 9 × 6 = 54; mask fill = ?
  timer_sec=55, sun_reward=360, badge="tundra", badge_count=247,
  h=1, b=3, s=3, unlocks=None, strategy="clustered")


# ---------- L27  "Loose Checker" ----------------------------------
# Loose checkerboard: every other row is fully filled, alternating rows have gaps.
def _mask_loose_checker():
    rows, cols = 9, 7
    g = []
    for r in range(rows):
        if r % 2 == 0:
            # gapped row: 4 tokens
            g.append("X.X.X.X")
        else:
            g.append("XXXXXXX")
    return g
L(27, "Loose Checker", "birds", 7, 9,
  mask=_mask_loose_checker(),
  face_counts=[4] * 12,  # check fill
  timer_sec=50, sun_reward=370, badge="aurora", badge_count=224,
  h=1, b=3, s=3, unlocks="magnet", strategy="random")


# ---------- L28  "Few Faces" --------------------------------------
# Only 5 unique faces, 10 of each — massive repetition. 6×10 with 10 empties
# in evenly-spaced pairs.
def _mask_few_faces():
    rows, cols = 10, 6
    g = []
    for r in range(rows):
        if r % 2 == 1:
            g.append("X.XX.X")
        else:
            g.append("XXXXXX")
    return g
L(28, "Few Faces", "fruit_b", 6, 10,
  mask=_mask_few_faces(),
  face_counts=[10, 10, 10, 10, 10],  # 5 × 10 = 50
  timer_sec=50, sun_reward=380, badge="eclipse", badge_count=203,
  h=1, b=3, s=3, unlocks=None, strategy="clustered")


# ---------- L29  "Crown" ------------------------------------------
# Crown silhouette: 3 spikes on top, full body below.
def _mask_crown():
    rows, cols = 10, 7
    g = [
        "X.X.X.X",
        "X.XXX.X",
        "XXXXXXX",
        "XXXXXXX",
        "XXXXXXX",
        "XXXXXXX",
        "XXXXXXX",
        "XXXXXXX",
        "XXXXXXX",
        "XXX.XXX",
    ]
    return g
L(29, "Crown", "veges", 7, 10,
  mask=_mask_crown(),
  face_counts=[4] * 16,  # check fill
  timer_sec=45, sun_reward=400, badge="nebula", badge_count=187,
  h=1, b=3, s=3, unlocks=None, strategy="mirror")


# ---------- L30  "Endless" ----------------------------------------
# Final boss — fully packed 7x10 board, 35 pairs. 17 unique faces with one
# tripled. Clustered placement makes it hard to find the right pair order.
L(30, "Endless", "birds", 7, 10,
  mask=m_rect(7, 10),
  face_counts=[6] + [4] * 16,  # 6 + 64 = 70; 35 pairs
  timer_sec=40, sun_reward=500, badge="zenith", badge_count=100,
  h=1, b=3, s=3, unlocks="infinite", strategy="clustered")


# ============================================================
# Placement strategies
# ============================================================


def positions_of_mask(mask):
    rows = len(mask)
    cols = len(mask[0])
    out = []
    for r in range(rows):
        for c in range(cols):
            if mask[r][c] == "X":
                out.append((r, c))
    return out


def place_random(mask, face_counts, rng):
    positions = positions_of_mask(mask)
    tokens = []
    for i, n in enumerate(face_counts, start=1):
        tokens.extend([i] * n)
    rng.shuffle(tokens)
    return dict(zip(positions, tokens))


def place_mirror(mask, face_counts, rng):
    """For each row, pair (c, cols-1-c) get the SAME face. Unpaired positions
    on the axis of symmetry get paired with each other."""
    rows = len(mask)
    cols = len(mask[0])
    pair_slots = []      # list of [(r,c),(r,c)]
    leftovers = []
    seen = set()
    for r in range(rows):
        for c in range(cols // 2):
            mc = cols - 1 - c
            if mask[r][c] == "X" and mask[r][mc] == "X":
                pair_slots.append([(r, c), (r, mc)])
                seen.add((r, c)); seen.add((r, mc))
        if cols % 2 == 1:
            mid = cols // 2
            if mask[r][mid] == "X":
                leftovers.append((r, mid))
                seen.add((r, mid))
    # Any X positions not on a mirror pair — shouldn't happen if mask is symmetric
    for p in positions_of_mask(mask):
        if p not in seen:
            leftovers.append(p)
    # Pair leftovers with each other randomly
    rng.shuffle(leftovers)
    for i in range(0, len(leftovers) - 1, 2):
        pair_slots.append([leftovers[i], leftovers[i + 1]])
    if len(leftovers) % 2 == 1:
        raise ValueError("mirror strategy: odd leftover position; mask is asymmetric")
    # Flatten face_counts → list of "pair faces"
    face_pairs = []
    for i, n in enumerate(face_counts, start=1):
        face_pairs.extend([i] * (n // 2))
    if len(face_pairs) != len(pair_slots):
        raise ValueError(f"face_pairs {len(face_pairs)} != pair_slots {len(pair_slots)}")
    rng.shuffle(face_pairs)
    placement = {}
    for (p1, p2), face in zip(pair_slots, face_pairs):
        placement[p1] = face
        placement[p2] = face
    return placement


def place_spread(mask, face_counts, rng):
    """Place same-face tokens as far apart as possible.
    Heuristic: pick a random ordering of pairs; for each pair, choose the two
    positions whose pairwise distance is in the top quartile of remaining
    candidates. Greedy but effective."""
    positions = positions_of_mask(mask)
    rng.shuffle(positions)
    # Pre-compute distances on demand
    def dist(a, b):
        return abs(a[0] - b[0]) + abs(a[1] - b[1])  # Manhattan
    placement = {}
    # Build pair list as multiset: each face appears (count/2) times
    pair_faces = []
    for i, n in enumerate(face_counts, start=1):
        pair_faces.extend([i] * (n // 2))
    rng.shuffle(pair_faces)
    remaining = list(positions)
    for face in pair_faces:
        # Pick a position randomly, then partner with the FARTHEST remaining
        if not remaining:
            break
        a = remaining.pop(rng.randrange(len(remaining)))
        # Choose partner from top 30% by distance for some variety
        sorted_by_dist = sorted(remaining, key=lambda p: -dist(a, p))
        topN = max(1, len(sorted_by_dist) // 3)
        b = sorted_by_dist[rng.randrange(topN)]
        remaining.remove(b)
        placement[a] = face
        placement[b] = face
    return placement


def place_clustered(mask, face_counts, rng):
    """Place each face's tokens in nearby positions. Approach: BFS-grow a
    contiguous blob of size `count` for each face, starting from a random
    seed cell among the unassigned positions."""
    rows = len(mask)
    cols = len(mask[0])
    free = set(positions_of_mask(mask))
    placement = {}
    # Sort faces by count desc so big blobs get placed first
    indexed = list(enumerate(face_counts, start=1))
    indexed.sort(key=lambda x: -x[1])
    for face, count in indexed:
        if count == 0:
            continue
        # Pick a random seed in free
        if not free:
            raise ValueError("clustered: ran out of free cells")
        seed = rng.choice(list(free))
        blob = {seed}
        frontier = [seed]
        # Grow until we have `count` cells
        while len(blob) < count and frontier:
            new_frontier = []
            for r, c in frontier:
                for dr, dc in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                    nr, nc = r + dr, c + dc
                    np = (nr, nc)
                    if np in free and np not in blob:
                        blob.add(np)
                        new_frontier.append(np)
                        if len(blob) >= count:
                            break
                if len(blob) >= count:
                    break
            frontier = new_frontier
            rng.shuffle(frontier)
        if len(blob) < count:
            # Fallback: pull from any free cells (no longer contiguous)
            for p in list(free - blob):
                if len(blob) >= count:
                    break
                blob.add(p)
        for p in blob:
            placement[p] = face
            free.discard(p)
    return placement


# ============================================================
# Lightweight pathfinder (mirror of the Kotlin engine) for solvability check
# ============================================================


def _line_clear(grid, a, b, rows, cols):
    """True if every cell strictly between a and b on the same row/col is 0.
    a/b need not be 0 — they're endpoints. Uses padded grid where (-1) and
    (cols)/(rows) are always 0."""
    if a[0] != b[0] and a[1] != b[1]:
        return False
    def get(r, c):
        if r < -1 or r > rows or c < -1 or c > cols:
            return -1
        if r == -1 or r == rows or c == -1 or c == cols:
            return 0
        return grid[r][c]
    if a[0] == b[0]:
        r = a[0]
        lo = min(a[1], b[1]) + 1
        hi = max(a[1], b[1]) - 1
        for c in range(lo, hi + 1):
            if get(r, c) != 0:
                return False
    else:
        c = a[1]
        lo = min(a[0], b[0]) + 1
        hi = max(a[0], b[0]) - 1
        for r in range(lo, hi + 1):
            if get(r, c) != 0:
                return False
    return True


def _path_exists(grid, a, b, rows, cols):
    if a == b:
        return False
    # 1 segment
    if (a[0] == b[0] or a[1] == b[1]) and _line_clear(grid, a, b, rows, cols):
        return True
    def is_empty(r, c):
        if r < 0 or r >= rows or c < 0 or c >= cols:
            return True  # outer ring
        return grid[r][c] == 0
    # 2 segments — elbow at (a.row, b.col) or (b.row, a.col)
    for elbow in [(a[0], b[1]), (b[0], a[1])]:
        er, ec = elbow
        if is_empty(er, ec) and _line_clear(grid, a, elbow, rows, cols) and _line_clear(grid, elbow, b, rows, cols):
            return True
    # 3 segments — vertical channel at column c
    for c in range(-1, cols + 1):
        if c == a[1] or c == b[1]:
            continue
        p1 = (a[0], c); p2 = (b[0], c)
        if not is_empty(p1[0], p1[1]) or not is_empty(p2[0], p2[1]):
            continue
        if _line_clear(grid, a, p1, rows, cols) and _line_clear(grid, p1, p2, rows, cols) and _line_clear(grid, p2, b, rows, cols):
            return True
    # 3 segments — horizontal channel at row r
    for r in range(-1, rows + 1):
        if r == a[0] or r == b[0]:
            continue
        p1 = (r, a[1]); p2 = (r, b[1])
        if not is_empty(p1[0], p1[1]) or not is_empty(p2[0], p2[1]):
            continue
        if _line_clear(grid, a, p1, rows, cols) and _line_clear(grid, p1, p2, rows, cols) and _line_clear(grid, p2, b, rows, cols):
            return True
    return False


def has_any_solvable_pair(layout_rows, cols, rows):
    """Parse layout strings, return True if at least one connectable pair exists."""
    grid = [[0] * cols for _ in range(rows)]
    by_face = {}
    for r, row in enumerate(layout_rows):
        toks = row.split()
        for c, tok in enumerate(toks):
            v = 0 if tok == "." else int(tok)
            grid[r][c] = v
            if v > 0:
                by_face.setdefault(v, []).append((r, c))
    for face, positions in by_face.items():
        for a, b in combinations(positions, 2):
            if _path_exists(grid, a, b, rows, cols):
                return True
    return False


# ============================================================
# Build all 15 layouts
# ============================================================


STRATEGIES = {
    "random": place_random,
    "mirror": place_mirror,
    "spread": place_spread,
    "clustered": place_clustered,
}


def build_layout(L_def):
    mask = L_def["mask"]
    cols = L_def["cols"]
    rows = L_def["rows"]
    fn = STRATEGIES[L_def["strategy"]]
    # Re-seed until a solvable initial board is found
    for attempt in range(200):
        rng = random.Random(1000 + L_def["id"] * 17 + attempt)
        placement = fn(mask, L_def["face_counts"], rng)
        layout = []
        for r in range(rows):
            row_tokens = []
            for c in range(cols):
                if (r, c) in placement:
                    row_tokens.append(str(placement[(r, c)]))
                else:
                    row_tokens.append(".")
            layout.append(" ".join(row_tokens))
        if has_any_solvable_pair(layout, cols, rows):
            return layout, attempt
    raise RuntimeError(f"L{L_def['id']}: no solvable arrangement found in 200 attempts")


# ============================================================
# Badge defs for the new levels
# ============================================================
NEW_BADGES = [
    {"id": "ember",   "label": "Ember",   "drawable": "veg_pepper"},
    {"id": "mist",    "label": "Mist",    "drawable": "bird_pigeon"},
    {"id": "dawn",    "label": "Dawn",    "drawable": "fruit_a_05"},
    {"id": "dusk",    "label": "Dusk",    "drawable": "bird_owl"},
    {"id": "river",   "label": "River",   "drawable": "veg_fenugreek"},
    {"id": "ocean",   "label": "Ocean",   "drawable": "fruit_a_01"},
    {"id": "summit",  "label": "Summit",  "drawable": "bird_eagle"},
    {"id": "canyon",  "label": "Canyon",  "drawable": "veg_pumpkin"},
    {"id": "forest",  "label": "Forest",  "drawable": "veg_broccoli"},
    {"id": "desert",  "label": "Desert",  "drawable": "veg_corn"},
    {"id": "tundra",  "label": "Tundra",  "drawable": "bird_swan"},
    {"id": "aurora",  "label": "Aurora",  "drawable": "bird_peacock"},
    {"id": "eclipse", "label": "Eclipse", "drawable": "bird_crow"},
    {"id": "nebula",  "label": "Nebula",  "drawable": "fruit_b_03"},
    {"id": "zenith",  "label": "Zenith",  "drawable": "bird_butterfly"},
]


# ============================================================
# Main
# ============================================================
def main():
    path = os.path.join(os.path.dirname(__file__), "app", "src", "main", "assets", "levels.json")
    with open(path, "r", encoding="utf-8") as f:
        existing = json.load(f)
    assert len(existing["levels"]) == 15, "expected existing 15 levels"
    existing_badge_ids = {b["id"] for b in existing["badges"]}
    for nb in NEW_BADGES:
        if nb["id"] not in existing_badge_ids:
            existing["badges"].append(nb)
    # Build & append the 15 new levels
    for L_def in LEVELS:
        layout, attempts = build_layout(L_def)
        print(f"L{L_def['id']:>2} '{L_def['name']}' "
              f"({L_def['cols']}x{L_def['rows']}, {L_def['pairs']} pairs, "
              f"{L_def['strategy']}) solvable after {attempts} attempt(s)")
        existing["levels"].append({
            "id": L_def["id"],
            "theme": L_def["theme"],
            "grid": {"cols": L_def["cols"], "rows": L_def["rows"]},
            "timerSec": L_def["timerSec"],
            "targetSunReward": L_def["targetSunReward"],
            "badge": L_def["badge"],
            "badgeCount": L_def["badgeCount"],
            "powerups": {"hint": L_def["hint"], "bomb": L_def["bomb"], "shuffle": L_def["shuffle"]},
            "unlocksFeature": L_def["unlocksFeature"],
            "uniqueFaces": L_def["uniqueFaces"],
            "layout": layout,
        })
    # Validate everything (parser-style)
    for lvl in existing["levels"]:
        cols = lvl["grid"]["cols"]
        rows = lvl["grid"]["rows"]
        assert len(lvl["layout"]) == rows, f"L{lvl['id']}: rows mismatch"
        counts = {}
        for r, row in enumerate(lvl["layout"]):
            toks = row.split()
            assert len(toks) == cols, f"L{lvl['id']} r{r}: token count mismatch"
            for t in toks:
                if t != ".":
                    counts[t] = counts.get(t, 0) + 1
        for face, n in counts.items():
            assert n % 2 == 0, f"L{lvl['id']}: face {face} odd count {n}"
    with open(path, "w", encoding="utf-8") as f:
        json.dump(existing, f, indent=2)
    print(f"OK {len(existing['levels'])} levels written to {path}")


if __name__ == "__main__":
    main()
