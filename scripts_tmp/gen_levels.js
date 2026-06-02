// Generate levels.json for AnimalMatch (no deps).
const fs = require('fs');
const path = require('path');

const OUT = 'C:/Users/atithi shekhar/AndroidStudioProjects/animalmatch/app/src/main/assets/levels.json';

const THEMES = {
  fruit_a: {
    label: 'Orchard',
    tiles: Array.from({length: 25}, (_, i) => `fruit_a_${String(i+1).padStart(2,'0')}`),
  },
  fruit_b: {
    label: 'Harvest',
    tiles: Array.from({length: 9}, (_, i) => `fruit_b_${String(i+1).padStart(2,'0')}`),
  },
  birds: {
    label: 'Sky Song',
    tiles: [
      'bird_owl','bird_chick','bird_chicken','bird_dove','bird_duck',
      'bird_eagle','bird_flamingo','bird_honeybee','bird_hummingbird',
      'bird_parrot','bird_peacock','bird_pigeon','bird_rooster',
      'bird_sparrow','bird_swan','bird_turkey','bird_woodpecker',
      'bird_bat','bird_butterfly','bird_crow','bird_dodo',
    ],
  },
  veges: {
    label: 'Greens',
    tiles: [
      'veg_arugula','veg_beets','veg_bittergourd','veg_bokchoy',
      'veg_broccoli','veg_cabbage','veg_carrot','veg_cauliflower',
      'veg_collardgreen','veg_corn','veg_cucumber','veg_eggplant',
      'veg_elephantfoot','veg_fenugreek','veg_garlic','veg_ginger',
      'veg_greenbean','veg_kale','veg_leeks','veg_lettuce',
      'veg_mushroom','veg_okra','veg_onion','veg_peas','veg_pepper',
      'veg_potato','veg_pumpkin','veg_radicchio','veg_radish',
      'veg_rutabaga','veg_spinach','veg_squash','veg_sweetpotato',
      'veg_tomato','veg_turnip','veg_whitemushroom','veg_zucchini',
    ],
  },
};

const BADGES = [
  {id:'wisdom',  label:'Wisdom',  drawable:'veg_broccoli'},
  {id:'joy',     label:'Joy',     drawable:'fruit_a_10'},
  {id:'harmony', label:'Harmony', drawable:'bird_dove'},
  {id:'energy',  label:'Energy',  drawable:'bird_honeybee'},
  {id:'hope',    label:'Hope',    drawable:'veg_tomato'},
  {id:'love',    label:'Love',    drawable:'fruit_b_07'},
  {id:'strength',label:'Strength',drawable:'bird_eagle'},
  {id:'courage', label:'Courage', drawable:'bird_rooster'},
  {id:'peace',   label:'Peace',   drawable:'bird_swan'},
  {id:'light',   label:'Light',   drawable:'bird_butterfly'},
  {id:'bloom',   label:'Bloom',   drawable:'veg_pumpkin'},
  {id:'spark',   label:'Spark',   drawable:'bird_parrot'},
  {id:'glow',    label:'Glow',    drawable:'bird_flamingo'},
  {id:'dream',   label:'Dream',   drawable:'bird_owl'},
  {id:'voyage',  label:'Voyage',  drawable:'bird_peacock'},
];

// Some specced pair totals exceed grid capacity; we cap at floor(cols*rows/2).
// (id, theme, cols, rows, uniqueFaces, pairs, timerSec, reward, badge, h, b, s, unlocks, badgeCount)
const SPEC = [
  [1,  'fruit_a', 6, 5,  6, 12, 120, 100, 'wisdom',   3, 1, 2, null,       1611],
  [2,  'fruit_a', 6, 6,  8, 15, 110, 110, 'joy',      3, 1, 2, null,       1542],
  [3,  'birds',   6, 7, 10, 18, 100, 120, 'harmony',  3, 1, 2, 'bomb',     1438],
  [4,  'birds',   7, 7, 10, 20,  90, 130, 'energy',   2, 2, 2, null,       1326],
  [5,  'veges',   7, 8, 12, 24,  90, 140, 'hope',     2, 2, 2, 'shuffle',  1207],
  [6,  'veges',   7, 8, 13, 26,  85, 150, 'love',     2, 2, 3, null,       1188],
  [7,  'fruit_b', 7, 8,  9, 26,  80, 160, 'strength', 2, 2, 3, null,       1102],
  [8,  'fruit_a', 7, 8, 14, 28,  75, 170, 'courage',  2, 2, 3, null,       1057],
  [9,  'birds',   7, 9, 15, 30,  70, 180, 'peace',    2, 2, 3, null,       1024],
  [10, 'veges',   7, 9, 15, 30,  70, 190, 'light',    2, 3, 3, 'bigbomb',  1500],
  [11, 'fruit_a', 7, 9, 16, 32,  65, 200, 'bloom',    2, 3, 3, null,       1411],
  [12, 'veges',   7,10, 17, 34,  65, 210, 'spark',    2, 3, 3, null,       1380],
  [13, 'birds',   7,10, 17, 35,  60, 220, 'glow',     2, 3, 3, null,       1299],
  [14, 'fruit_b', 7,10,  9, 36,  60, 230, 'dream',    1, 3, 3, null,       1248],
  [15, 'veges',   7,10, 18, 36,  50, 250, 'voyage',   1, 3, 3, 'finale',   1196],
];

function buildPairsPerFace(uniqueFaces, pairs) {
  const base = Math.floor(pairs / uniqueFaces);
  const rem = pairs - base * uniqueFaces;
  const arr = Array(uniqueFaces).fill(base);
  for (let i = 0; i < rem; i++) arr[i] += 1;
  // ensure each face has >= 1 pair (so >=2 tokens, even)
  for (let i = 0; i < uniqueFaces; i++) {
    if (arr[i] === 0) {
      for (let j = 0; j < uniqueFaces; j++) {
        if (arr[j] >= 2) { arr[j] -= 1; arr[i] += 1; break; }
      }
    }
  }
  return arr;
}

function makeLayout(cols, rows, uniqueFaces, pairs) {
  const total = cols * rows;
  const maxPairs = Math.floor(total / 2);
  const usedPairs = Math.min(pairs, maxPairs);
  const nonEmpty = usedPairs * 2;
  const emptyCount = total - nonEmpty;

  const perFace = buildPairsPerFace(uniqueFaces, usedPairs);
  const tokens = [];
  for (let f = 1; f <= uniqueFaces; f++) {
    for (let k = 0; k < perFace[f-1] * 2; k++) tokens.push(String(f));
  }
  if (tokens.length !== nonEmpty) {
    throw new Error(`tokens ${tokens.length} != nonEmpty ${nonEmpty}`);
  }

  // Spread empties evenly across the grid by index.
  const emptySet = new Set();
  if (emptyCount > 0) {
    const step = total / emptyCount;
    for (let i = 0; i < emptyCount; i++) {
      const pos = Math.floor(i * step + step / 2);
      // Ensure uniqueness; if collision, walk forward.
      let p = Math.min(pos, total - 1);
      while (emptySet.has(p)) p = (p + 1) % total;
      emptySet.add(p);
    }
  }

  // Build grid row-major.
  const grid = new Array(total).fill('.');
  let ti = 0;
  for (let i = 0; i < total; i++) {
    if (emptySet.has(i)) continue;
    grid[i] = tokens[ti++];
  }
  if (ti !== tokens.length) throw new Error(`placed ${ti} of ${tokens.length}`);

  const out = [];
  for (let r = 0; r < rows; r++) {
    const row = [];
    for (let c = 0; c < cols; c++) row.push(grid[r * cols + c]);
    out.push(row.join(' '));
  }
  return out;
}

function validate(layout, cols, rows, uniqueFaces, levelId) {
  if (layout.length !== rows) throw new Error(`L${levelId}: rows ${layout.length} != ${rows}`);
  const counts = {};
  for (let r = 0; r < rows; r++) {
    const toks = layout[r].split(/\s+/);
    if (toks.length !== cols) throw new Error(`L${levelId} row ${r}: ${toks.length} != ${cols}`);
    for (const t of toks) {
      if (t !== '.') counts[t] = (counts[t]||0) + 1;
    }
  }
  for (const [face, n] of Object.entries(counts)) {
    if (n % 2 !== 0) throw new Error(`L${levelId}: face ${face} count ${n} odd`);
  }
  for (let f = 1; f <= uniqueFaces; f++) {
    if (!counts[String(f)]) throw new Error(`L${levelId}: face ${f} missing`);
  }
}

function build() {
  const levels = [];
  for (const s of SPEC) {
    const [lid, theme, cols, rows, uf, pairs, ts, rew, badge, h, b, sp, unlocks, bc] = s;
    const layout = makeLayout(cols, rows, uf, pairs);
    validate(layout, cols, rows, uf, lid);
    levels.push({
      id: lid,
      theme,
      grid: { cols, rows },
      timerSec: ts,
      targetSunReward: rew,
      badge,
      badgeCount: bc,
      powerups: { hint: h, bomb: b, shuffle: sp },
      unlocksFeature: unlocks,
      uniqueFaces: uf,
      layout,
    });
  }
  const catalog = { version: 1, themes: THEMES, badges: BADGES, levels };

  fs.mkdirSync(path.dirname(OUT), { recursive: true });
  fs.writeFileSync(OUT, JSON.stringify(catalog, null, 2), 'utf8');
  console.log('Wrote', OUT);
}

build();
