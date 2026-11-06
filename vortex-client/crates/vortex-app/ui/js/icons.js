// Pixel-art icons drawn from tiny bitmaps ("#" = filled pixel), rendered as
// crisp SVG paths so they scale without blurring.

const BITMAPS = {
  logo: [
    "..#....#..",
    ".###..###.",
    ".###..###.",
    "..##..##..",
    "..###.##..",
    "...####...",
    "...####...",
    "....##....",
  ],
  home: [
    "....##....",
    "...####...",
    "..######..",
    ".########.",
    "##########",
    ".##....##.",
    ".##.##.##.",
    ".##.##.##.",
    ".########.",
  ],
  grid: [
    "####.####",
    "####.####",
    "####.####",
    "####.####",
    ".........",
    "####.####",
    "####.####",
    "####.####",
    "####.####",
  ],
  plus: [
    "...###...",
    "...###...",
    "...###...",
    "#########",
    "#########",
    "#########",
    "...###...",
    "...###...",
    "...###...",
  ],
  puzzle: [
    "...##.....",
    "..####....",
    "######....",
    "########..",
    "#######.##",
    "#######.##",
    "########..",
    "########..",
    "...##.....",
  ],
  user: [
    "..#####..",
    "..#####..",
    "..#####..",
    "..#####..",
    ".........",
    ".#######.",
    "#########",
    "#########",
    "#########",
  ],
  gear: [
    "...##...",
    ".######.",
    ".##..##.",
    "###..###",
    "###..###",
    ".##..##.",
    ".######.",
    "...##...",
  ],
  play: [
    "##......",
    "####....",
    "######..",
    "########",
    "########",
    "######..",
    "####....",
    "##......",
  ],
  stop: [
    "########",
    "########",
    "########",
    "########",
    "########",
    "########",
    "########",
    "########",
  ],
  folder: [
    "####......",
    "#..#####..",
    "##########",
    "#........#",
    "#........#",
    "#........#",
    "##########",
  ],
  trash: [
    "...####...",
    "##########",
    ".#......#.",
    ".#.#..#.#.",
    ".#.#..#.#.",
    ".#.#..#.#.",
    ".#......#.",
    ".########.",
  ],
  sun: [
    "....#....",
    ".#..#..#.",
    "...###...",
    "..#####..",
    "#.#####.#",
    "..#####..",
    "...###...",
    ".#..#..#.",
    "....#....",
  ],
  moon: [
    "...####.",
    "..##....",
    ".##.....",
    ".##.....",
    ".##.....",
    ".###...#",
    "..######",
    "...####.",
  ],
  sunset: [
    "...###...",
    "..#####..",
    ".#######.",
    "#########",
    ".........",
    "#########",
    ".........",
    "..#####..",
  ],
  log: [
    "########",
    "#......#",
    "#.##...#",
    "#......#",
    "#.####.#",
    "#......#",
    "#.###..#",
    "########",
  ],
  x: [
    "##....##",
    "###..###",
    ".######.",
    "..####..",
    "..####..",
    ".######.",
    "###..###",
    "##....##",
  ],
  check: [
    ".......##",
    "......###",
    ".....###.",
    "##..###..",
    "######...",
    ".####....",
    "..##.....",
  ],
  chev: [
    "#.....#",
    "##...##",
    ".##.##.",
    "..###..",
    "...#...",
  ],
  edit: [
    "......##",
    ".....###",
    "....###.",
    "...###..",
    "..###...",
    ".###....",
    "##......",
    "###.....",
  ],
  search: [
    ".####....",
    "##..##...",
    "#....#...",
    "#....#...",
    "##..##...",
    ".#####...",
    ".....##..",
    "......##.",
    ".......##",
  ],
  heart: [
    ".##..##.",
    "########",
    "########",
    "########",
    ".######.",
    "..####..",
    "...##...",
  ],
  shirt: [
    "..##..##..",
    ".###..###.",
    "####..####",
    "##.####.##",
    "...####...",
    "...####...",
    "...####...",
    "...####...",
  ],
  sparkle: [
    "....#....",
    "....#....",
    "...###...",
    "#########",
    "...###...",
    "....#....",
    "....#....",
  ],
  bolt: [
    "....###",
    "...###.",
    "..###..",
    ".######",
    "#####..",
    "..###..",
    ".###...",
    ".##....",
    "##.....",
  ],
  image: [
    "##########",
    "#........#",
    "#.##.....#",
    "#.##..#..#",
    "#....###.#",
    "#.#.######",
    "##########",
  ],
  download: [
    "...##...",
    "...##...",
    "...##...",
    "########",
    ".######.",
    "..####..",
    "...##...",
    "########",
  ],
  copy: [
    "#####...",
    "#...#...",
    "#..#####",
    "#..#...#",
    "####...#",
    "...#...#",
    "...#####",
  ],
  link: [
    "....####",
    ".....###",
    "....####",
    "#..###.#",
    "#.###...",
    "#.......",
    "#.......",
    "#######.",
  ],
  // Loader emblems.
  grass: [
    "########",
    "##.###.#",
    "########",
    "#.#.#.##",
    "########",
    "##.###.#",
    "########",
    "#.###.##",
  ],
  fabric: [
    "########",
    ".#....#.",
    ".######.",
    ".#.##.#.",
    ".######.",
    ".#.##.#.",
    ".######.",
    "########",
  ],
  forge: [
    "##########",
    "##########",
    ".#######..",
    "...####...",
    "...####...",
    "..######..",
    ".########.",
  ],
};

const cache = new Map();

// Some icons are drawn in separate parts so each part can move on its own
// (the grid's tiles, the player's head, the puzzle piece's nubs). Each
// function maps a filled pixel to its part number.
const PARTS = {
  grid: (x, y) => (x > 4 ? 1 : 0) + (y > 4 ? 2 : 0),
  user: (x, y) => (y < 4 ? 1 : 0),
  puzzle: (x, y) => (x >= 8 ? 1 : y <= 1 ? 2 : 0),
  folder: (x, y) => (y < 2 ? 1 : 0),
  trash: (x, y) => (y < 2 ? 1 : 0),
  download: (x, y) => (y < 7 ? 1 : 0),
};
// Extra pixels drawn outside the icon that only show while it animates.
const TRAILS = {
  play: "M-3 2h2v1h-2zM-4 3h3v2h-3zM-3 5h2v1h-2z",
};

/** SVG markup for an icon. */
export function icon(name, size = 18) {
  const key = `${name}:${size}`;
  if (cache.has(key)) return cache.get(key);
  const rows = BITMAPS[name] || BITMAPS.sparkle;
  const w = Math.max(...rows.map((r) => r.length));
  const h = rows.length;
  const part = PARTS[name] || (() => 0);
  const d = [];
  rows.forEach((row, y) => {
    let x = 0;
    while (x < row.length) {
      if (row[x] === "#") {
        const p = part(x, y);
        let run = 1;
        while (row[x + run] === "#" && part(x + run, y) === p) run++;
        d[p] = (d[p] || "") + `M${x} ${y}h${run}v1h-${run}z`;
        x += run;
      } else x++;
    }
  });
  const svgW = (size * w) / Math.max(w, h);
  const svgH = (size * h) / Math.max(w, h);
  const paths = d.map((pd, i) => (pd ? `<path class="ip ip${i}" fill="currentColor" d="${pd}"/>` : "")).join("");
  const trail = TRAILS[name] ? `<path class="ip-trail" fill="currentColor" d="${TRAILS[name]}"/>` : "";
  const svg = `<svg class="px-icon ic-${name in BITMAPS ? name : "sparkle"}" viewBox="0 0 ${w} ${h}" width="${svgW.toFixed(1)}" height="${svgH.toFixed(1)}" aria-hidden="true" shape-rendering="crispEdges">${trail}${paths}</svg>`;
  cache.set(key, svg);
  return svg;
}

/** Fill every `[data-icon]` placeholder under `root`. */
export function hydrateIcons(root = document) {
  root.querySelectorAll("[data-icon]").forEach((el) => {
    const size = Number(el.dataset.size || 18);
    el.innerHTML = icon(el.dataset.icon, size);
    el.classList.add("icon-slot");
  });
}

/** A deterministic 8×8 pixel face for an account, drawn on a canvas. */
export function drawAvatar(canvas, seed) {
  const ctx = canvas.getContext("2d");
  let h = 2166136261;
  for (const c of seed || "steve") h = Math.imul(h ^ c.charCodeAt(0), 16777619);
  const rnd = () => ((h = Math.imul(h ^ (h >>> 15), 2246822507) ^ Math.imul(h ^ (h >>> 13), 3266489909)) >>> 0) / 4294967296;
  const skins = ["#c69c6d", "#a8784d", "#e0b48a", "#8d5a3b", "#f1c9a0"];
  const hairs = ["#4a2f1b", "#2b2b2b", "#d9a33c", "#8a3b1f", "#6a4bc4", "#e8e8e8"];
  const eyes = ["#3c5bd9", "#2f8f4a", "#5a3a22", "#7a2fd9"];
  const skin = skins[Math.floor(rnd() * skins.length)];
  const hair = hairs[Math.floor(rnd() * hairs.length)];
  const eye = eyes[Math.floor(rnd() * eyes.length)];
  ctx.fillStyle = skin;
  ctx.fillRect(0, 0, 8, 8);
  ctx.fillStyle = hair;
  ctx.fillRect(0, 0, 8, 2);
  if (rnd() > 0.4) { ctx.fillRect(0, 2, 1, 2); ctx.fillRect(7, 2, 1, 2); }
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(1, 4, 2, 1); ctx.fillRect(5, 4, 2, 1);
  ctx.fillStyle = eye;
  ctx.fillRect(2, 4, 1, 1); ctx.fillRect(5, 4, 1, 1);
  ctx.fillStyle = "rgba(0,0,0,.35)";
  ctx.fillRect(3, 6, 2, 1);
}
