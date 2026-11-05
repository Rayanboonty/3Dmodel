// Blocky 3D lettering in the style of Minecraft title logos: chunky glyphs
// built from cells, a thick dark outline, depth going down and right, a
// bevel along every edge, a block texture on the face and a glossy shine.
// Used for the Vortex wordmark (ten styles), the boot screen and the
// generated covers of old Minecraft versions.

const GLYPHS = {
  V: ["XX...XX", "XX...XX", "XX...XX", "XXX.XXX", ".XX.XX.", ".XXXXX.", "..XXX.."],
  O: [".XXXX.", "XXXXXX", "XX..XX", "XX..XX", "XX..XX", "XXXXXX", ".XXXX."],
  R: ["XXXXX.", "XX..XX", "XX..XX", "XXXXX.", "XX.XX.", "XX..XX", "XX..XX"],
  T: ["XXXXXX", "XXXXXX", "..XX..", "..XX..", "..XX..", "..XX..", "..XX.."],
  E: ["XXXXX", "XX...", "XX...", "XXXX.", "XX...", "XX...", "XXXXX"],
  X: ["XX..XX", "XX..XX", ".XXXX.", "..XX..", ".XXXX.", "XX..XX", "XX..XX"],
  C: [".XXXX", "XX...", "XX...", "XX...", "XX...", "XX...", ".XXXX"],
  L: ["XX...", "XX...", "XX...", "XX...", "XX...", "XX...", "XXXXX"],
  I: ["XX", "XX", "XX", "XX", "XX", "XX", "XX"],
  N: ["XX..XX", "XXX.XX", "XXXXXX", "XX.XXX", "XX..XX", "XX..XX", "XX..XX"],
  A: [".XXX.", "XX.XX", "XX.XX", "XXXXX", "XX.XX", "XX.XX", "XX.XX"],
  B: ["XXXX.", "XX.XX", "XX.XX", "XXXX.", "XX.XX", "XX.XX", "XXXX."],
  D: ["XXXX.", "XX.XX", "XX.XX", "XX.XX", "XX.XX", "XX.XX", "XXXX."],
  0: [".XXX.", "XX.XX", "XX.XX", "XX.XX", "XX.XX", "XX.XX", ".XXX."],
  1: [".XX.", "XXX.", ".XX.", ".XX.", ".XX.", ".XX.", "XXXX"],
  2: ["XXXX.", "...XX", "...XX", ".XXX.", "XX...", "XX...", "XXXXX"],
  3: ["XXXX.", "...XX", "...XX", ".XXX.", "...XX", "...XX", "XXXX."],
  4: ["XX.XX", "XX.XX", "XX.XX", "XXXXX", "...XX", "...XX", "...XX"],
  5: ["XXXXX", "XX...", "XX...", "XXXX.", "...XX", "...XX", "XXXX."],
  6: [".XXX.", "XX...", "XX...", "XXXX.", "XX.XX", "XX.XX", ".XXX."],
  7: ["XXXXX", "...XX", "...XX", "..XX.", "..XX.", ".XX..", ".XX.."],
  8: [".XXX.", "XX.XX", "XX.XX", ".XXX.", "XX.XX", "XX.XX", ".XXX."],
  9: [".XXX.", "XX.XX", "XX.XX", ".XXXX", "...XX", "...XX", ".XXX."],
  ".": ["..", "..", "..", "..", "..", "XX", "XX"],
  "-": ["....", "....", "....", "XXXX", "....", "....", "...."],
  " ": ["..", "..", "..", "..", "..", "..", ".."],
};

// --------------------------------------------------------------- materials --

const hash = (x, y, s) => {
  let h = (x * 374761393 + y * 668265263 + s * 974711) ^ 0x5bd1e995;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
};
const hexrgb = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const css = (c, a = 1) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a})`;
const mixc = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];

/**
 * A material: face texture colours, the depth (side) colour, bevel light and
 * dark, and extras (glow, lava cracks, snow caps, grass tops).
 */
export const MATERIALS = {
  diamond: { face: ["#62dceb", "#4cc9dc", "#8beaf4", "#3bb3c8", "#d6fbff"], side: "#1d7e95", light: "#dcfdff", dark: "#1b8ba3", gloss: 0.55, spark: 0.08 },
  gold: { face: ["#fbcf3b", "#f3be2a", "#ffe370", "#e0a71c", "#fff3b0"], side: "#a6691a", light: "#fff4b8", dark: "#b07a18", gloss: 0.5, spark: 0.05 },
  amber: { face: ["#ffb547", "#f9a535", "#ffc966", "#ee8f24", "#ffe2a8"], side: "#9b4c0e", light: "#ffe7b5", dark: "#b8601a", gloss: 0.45, spark: 0.04 },
  netherite: { face: ["#655d68", "#5a525d", "#736a77", "#4d464f", "#8e8594"], side: "#241f24", light: "#b3a9b8", dark: "#332d34", gloss: 0.35, lava: true },
  emerald: { face: ["#41dc7c", "#2fc767", "#6cf09a", "#20a855", "#b9ffd2"], side: "#0f6a34", light: "#d2ffe1", dark: "#13803e", gloss: 0.5, spark: 0.06 },
  amethyst: { face: ["#b48bff", "#a175f5", "#c9a8ff", "#8a5ee0", "#eadcff"], side: "#4d2896", light: "#f0e4ff", dark: "#6a3fc4", gloss: 0.5, spark: 0.07 },
  obsidian: { face: ["#46356b", "#3c2d5e", "#56427f", "#30244d", "#9a74e6"], side: "#140d22", light: "#a888f0", dark: "#22183a", gloss: 0.45, glow: "#a35cff", spark: 0.08 },
  redstone: { face: ["#e63a2e", "#cf2c22", "#ff5a48", "#b1201a", "#ffb0a2"], side: "#5c0d0b", light: "#ffc2b8", dark: "#8a1712", gloss: 0.4, glow: "#ff3b2b", spark: 0.06 },
  copper: { face: ["#e58a5a", "#d77a4b", "#f3a173", "#c0663c", "#ffc9a6"], side: "#7d3d22", light: "#ffd7bf", dark: "#9c4a28", gloss: 0.45, spark: 0.04 },
  verdigris: { face: ["#5fbf9f", "#4fae8f", "#7cd3b5", "#3d977a", "#c4f3e1"], side: "#245e4c", light: "#d4fff0", dark: "#2f7b62", gloss: 0.4, spark: 0.04 },
  ice: { face: ["#c3e8ff", "#acdbfb", "#def3ff", "#93caf0", "#ffffff"], side: "#4f86b8", light: "#ffffff", dark: "#6d9fcc", gloss: 0.6, snow: true, spark: 0.1 },
  grass: { face: ["#8a5f3f", "#7a5236", "#9b6c49", "#6b472d", "#a87a55"], side: "#4a3020", light: "#c79a72", dark: "#5a3a26", gloss: 0.2, grass: true },
  stone: { face: ["#8c8c90", "#7d7d82", "#9a9a9e", "#6e6e74", "#b4b4b8"], side: "#3d3d44", light: "#d0d0d4", dark: "#55555c", gloss: 0.25 },
  quartz: { face: ["#f2ede6", "#e6e0d7", "#fbf8f3", "#d8d1c6", "#ffffff"], side: "#8f877c", light: "#ffffff", dark: "#b5ad9f", gloss: 0.5 },
};

function textureFor(mat, seed = 1) {
  const c = document.createElement("canvas");
  c.width = c.height = 16;
  const g = c.getContext("2d");
  const cols = mat.face.map(hexrgb);
  for (let y = 0; y < 16; y++) {
    for (let x = 0; x < 16; x++) {
      const h = hash(x, y, seed);
      let col = h < 0.48 ? cols[0] : h < 0.68 ? cols[1] : h < 0.85 ? cols[2] : cols[3];
      // Diagonal glints, like ore and gem blocks.
      if (mat.spark && (x + y) % 7 === 0 && hash(x, y, seed + 3) < mat.spark * 6) col = cols[4];
      if (mat.lava && hash(x >> 1, y >> 1, seed + 5) > 0.9) col = hash(x, y, seed + 6) > 0.5 ? [255, 120, 30] : [255, 180, 60];
      g.fillStyle = css(col);
      g.fillRect(x, y, 1, 1);
    }
  }
  return c;
}

// ------------------------------------------------------------------ layout --

function lineCells(text) {
  const cells = [];
  let x = 0;
  const letters = [];
  for (const ch of text.toUpperCase()) {
    const g = GLYPHS[ch] || GLYPHS[" "];
    const w = g[0].length;
    letters.push({ ch, x, w });
    g.forEach((row, y) => {
      for (let i = 0; i < w; i++) if (row[i] === "X") cells.push({ x: x + i, y, letter: letters.length - 1 });
    });
    x += w + 1;
  }
  return { cells, width: Math.max(0, x - 1), letters };
}

/**
 * A logo style: one or more lines of text, each with a cell size relative to
 * the first line, a material (or one per letter), and an optional arch.
 */
export const STYLES = [
  {
    id: "diamond-gold", name: "Diamond & Gold", note: "Your SMP's look: a diamond V and gold letters.",
    lines: [{ text: "VORTEX", mats: (i) => (i === 0 ? "diamond" : "gold") }, { text: "CLIENT", scale: 0.48, mats: () => "diamond" }],
  },
  {
    id: "dawn-amber", name: "Dawn Amber", note: "Warm amber, like the Vortex accent and Dawn's title.",
    lines: [{ text: "VORTEX", mats: () => "amber" }, { text: "CLIENT", scale: 0.48, mats: () => "amber", plate: "#2b1600" }],
  },
  {
    id: "netherite", name: "Netherite Ember", note: "Dark netherite with lava running through it.",
    lines: [{ text: "VORTEX", mats: () => "netherite" }, { text: "CLIENT", scale: 0.48, mats: () => "amber" }],
  },
  {
    id: "emerald", name: "Emerald Line", note: "One line, emerald all the way.",
    inline: true,
    lines: [{ text: "VORTEX", mats: () => "emerald" }, { text: "CLIENT", scale: 0.62, mats: () => "quartz" }],
  },
  {
    id: "amethyst", name: "Amethyst Arch", note: "Letters on an arch, like a title screen.",
    lines: [{ text: "VORTEX", mats: () => "amethyst", arch: 0.9 }, { text: "CLIENT", scale: 0.48, mats: () => "quartz" }],
  },
  {
    id: "grass", name: "Grass Block", note: "Grass on top, dirt below: the classic block.",
    lines: [{ text: "VORTEX", mats: () => "grass" }, { text: "CLIENT", scale: 0.48, mats: () => "stone" }],
  },
  {
    id: "obsidian", name: "Obsidian Portal", note: "Obsidian with a purple portal glow.",
    lines: [{ text: "VORTEX", mats: () => "obsidian" }, { text: "CLIENT", scale: 0.48, mats: () => "amethyst" }],
  },
  {
    id: "redstone", name: "Redstone", note: "Glowing redstone, built for PvP.",
    inline: true,
    lines: [{ text: "VORTEX", mats: () => "redstone" }, { text: "CLIENT", scale: 0.62, mats: () => "stone" }],
  },
  {
    id: "copper", name: "Oxidised Copper", note: "Copper ageing to verdigris from left to right.",
    lines: [{ text: "VORTEX", mats: (i) => (i < 2 ? "copper" : i < 4 ? "copperMid" : "verdigris") }, { text: "CLIENT", scale: 0.48, mats: () => "verdigris", plate: "#14322a" }],
  },
  {
    id: "frost", name: "Frost", note: "Packed ice with snow settled on top.",
    lines: [{ text: "VORTEX", mats: () => "ice", arch: 0.35 }, { text: "CLIENT", scale: 0.48, mats: () => "diamond" }],
  },
];

MATERIALS.copperMid = {
  face: ["#b9a06a", "#a88f5c", "#cbb57f", "#957e4d", "#ead9a8"], side: "#5a5232", light: "#f1e4bd", dark: "#776a40", gloss: 0.42, spark: 0.04,
};

// ------------------------------------------------------------------ render --

const texCache = new Map();
function texture(name) {
  if (!texCache.has(name)) texCache.set(name, textureFor(MATERIALS[name], name.length * 7 + 3));
  return texCache.get(name);
}

/**
 * Draw lines of blocky text. Returns a canvas sized to fit.
 * @param lines [{ text, scale=1, mats(i)→material, arch=0, plate }]
 * @param cell  size of one cell of the first line, in CSS px
 * @param opts  { inline, dpr }
 */
export function renderLines(lines, cell, opts = {}) {
  const dpr = opts.dpr || Math.min(2, window.devicePixelRatio || 1);
  const items = lines.map((l) => {
    const c = cell * (l.scale || 1);
    const lay = lineCells(l.text);
    return { ...l, c, lay, w: lay.width * c, h: 7 * c };
  });
  // Placement: stacked and centred, or inline on one baseline.
  const pad = cell * 2.6;
  let W, Hh;
  if (opts.inline) {
    const gap = cell * 2.2;
    W = items.reduce((a, it) => a + it.w, 0) + gap * (items.length - 1);
    Hh = Math.max(...items.map((it) => it.h));
    let x = 0;
    for (const it of items) {
      it.ox = x;
      it.oy = Hh - it.h;
      x += it.w + gap;
    }
  } else {
    const gap = cell * 1.6;
    W = Math.max(...items.map((it) => it.w));
    let y = 0;
    for (const it of items) {
      it.ox = (W - it.w) / 2;
      it.oy = y + (it.arch ? it.arch * it.c * 3 : 0);
      y = it.oy + it.h + gap + (it.plate ? it.c * 1.2 : 0);
    }
    Hh = y - gap;
  }
  const out = document.createElement("canvas");
  out.width = Math.ceil((W + pad * 2) * dpr);
  out.height = Math.ceil((Hh + pad * 2) * dpr);
  const g = out.getContext("2d");
  g.scale(dpr, dpr);
  g.translate(pad, pad);

  for (const it of items) drawLine(g, it, dpr);
  out.cssWidth = W + pad * 2;
  out.cssHeight = Hh + pad * 2;
  return out;
}

function drawLine(g, it, dpr) {
  const { c, lay } = it;
  const o = c * 0.62; // outline
  const depth = c * 1.05;
  const steps = Math.max(2, Math.round(depth / Math.max(0.5, c * 0.18)));
  const dirX = 0.32, dirY = 1;
  const archY = (letter) => {
    if (!it.arch) return 0;
    const n = lay.letters.length;
    const t = n > 1 ? letter / (n - 1) : 0.5;
    return -Math.sin(t * Math.PI) * it.arch * c * 3 + it.arch * c * 3 * 0;
  };
  const cellPos = (cl) => [it.ox + cl.x * c, it.oy + cl.y * c + archY(cl.letter)];
  const matOf = (cl) => MATERIALS[it.mats(cl.letter)] || MATERIALS.gold;

  // Optional dark plate behind the line (a ribbon).
  if (it.plate) {
    const px = it.ox - c * 2.2, py = it.oy - c * 1.4, pw = it.w + c * 4.4, ph = it.h + c * 2.8 + depth;
    g.fillStyle = "#0d0a08";
    g.fillRect(px - o * 0.6, py - o * 0.6, pw + o * 1.2, ph + o * 1.2);
    g.fillStyle = it.plate;
    g.fillRect(px, py, pw, ph);
    g.fillStyle = "rgba(255,255,255,0.08)";
    g.fillRect(px, py, pw, c * 0.5);
  }

  // Outline + depth silhouette, with a soft shadow (and glow if the material has one).
  const glow = it.mats(0) && MATERIALS[it.mats(0)]?.glow;
  g.save();
  g.shadowColor = glow ? glow : "rgba(0,0,0,0.45)";
  g.shadowBlur = glow ? c * 3 : c * 1.4;
  g.shadowOffsetY = glow ? 0 : c * 0.8;
  g.fillStyle = "#0b0811";
  g.beginPath();
  for (const cl of lay.cells) {
    const [x, y] = cellPos(cl);
    g.rect(x - o, y - o, c + o * 2 + depth * dirX, c + o * 2 + depth * dirY);
  }
  g.fill();
  g.restore();
  g.fillStyle = "#0b0811";
  g.beginPath();
  for (const cl of lay.cells) {
    const [x, y] = cellPos(cl);
    g.rect(x - o, y - o, c + o * 2 + depth * dirX, c + o * 2 + depth * dirY);
  }
  g.fill();

  // Depth: stacked copies, darker the further back.
  for (let k = steps; k >= 1; k--) {
    const f = k / steps;
    const dx = depth * dirX * f, dy = depth * dirY * f;
    for (const cl of lay.cells) {
      const m = matOf(cl);
      const side = hexrgb(m.side);
      g.fillStyle = css(mixc(side, [0, 0, 0], f * 0.35));
      const [x, y] = cellPos(cl);
      g.fillRect(x + dx, y + dy, c + 0.6, c + 0.6);
    }
  }

  // Face on its own layer so the shine can be clipped to it.
  const W = g.canvas.width, Hc = g.canvas.height;
  const face = document.createElement("canvas");
  face.width = W;
  face.height = Hc;
  const f = face.getContext("2d");
  f.setTransform(g.getTransform());
  f.imageSmoothingEnabled = false;
  const has = new Set(lay.cells.map((cl) => `${cl.x},${cl.y}`));
  const at = (x, y) => has.has(`${x},${y}`);
  for (const cl of lay.cells) {
    const m = matOf(cl);
    const [x, y] = cellPos(cl);
    const name = it.mats(cl.letter);
    // Each cell shows a quarter of the 16px texture, so letters read as blocks.
    const tx = (cl.x % 2) * 8, ty = (cl.y % 2) * 8;
    if (m.grass && (cl.y <= 1 || !at(cl.x, cl.y - 1))) {
      // Grass top on the first row of each stroke.
      const tint = cl.y <= 0 || !at(cl.x, cl.y - 1) ? ["#6fb43c", "#5ea232", "#8ccf52"] : null;
      if (tint) {
        for (let i = 0; i < 4; i++) for (let j = 0; j < 4; j++) {
          f.fillStyle = tint[Math.floor(hash(cl.x * 4 + i, cl.y * 4 + j, 9) * 3)];
          f.fillRect(x + (i * c) / 4, y + (j * c) / 4, c / 4 + 0.3, c / 4 + 0.3);
        }
        continue;
      }
    }
    f.drawImage(texture(name), tx, ty, 8, 8, x, y, c + 0.4, c + 0.4);
  }
  // Bevel: light along top and left edges, dark along bottom and right.
  const b = Math.max(1, c * 0.2);
  for (const cl of lay.cells) {
    const m = matOf(cl);
    const [x, y] = cellPos(cl);
    const light = hexrgb(m.light), dark = hexrgb(m.dark);
    if (!at(cl.x, cl.y - 1)) { f.fillStyle = css(light, 0.85); f.fillRect(x, y, c, b); }
    if (!at(cl.x - 1, cl.y)) { f.fillStyle = css(light, 0.55); f.fillRect(x, y, b, c); }
    if (!at(cl.x, cl.y + 1)) { f.fillStyle = css(dark, 0.9); f.fillRect(x, y + c - b, c, b); }
    if (!at(cl.x + 1, cl.y)) { f.fillStyle = css(dark, 0.7); f.fillRect(x + c - b, y, b, c); }
    if (m.snow && !at(cl.x, cl.y - 1)) {
      f.fillStyle = "#ffffff";
      f.fillRect(x - 0.5, y - 0.5, c + 1, c * 0.42);
      f.fillStyle = "rgba(200,225,255,0.9)";
      f.fillRect(x, y + c * 0.42 - 0.5, c, Math.max(1, c * 0.12));
    }
  }
  // Shine: a soft diagonal band.
  const gm = Math.max(...lay.cells.map((cl) => matOf(cl).gloss || 0.4));
  f.globalCompositeOperation = "source-atop";
  const gx = it.ox, gy = it.oy;
  const grd = f.createLinearGradient(gx, gy, gx + it.w * 0.5, gy + it.h);
  grd.addColorStop(0, `rgba(255,255,255,${0.28 * gm})`);
  grd.addColorStop(0.38, `rgba(255,255,255,${0.1 * gm})`);
  grd.addColorStop(0.42, "rgba(255,255,255,0)");
  grd.addColorStop(1, "rgba(0,0,0,0.12)");
  f.fillStyle = grd;
  f.fillRect(gx - c, gy - c * 4, it.w + c * 2, it.h + c * 8);
  f.globalCompositeOperation = "source-over";
  g.save();
  g.setTransform(1, 0, 0, 1, 0, 0);
  g.drawImage(face, 0, 0);
  g.restore();
}

/** Render one of the STYLES at a cell size. */
export function renderStyle(index, cell, opts = {}) {
  const s = STYLES[index] || STYLES[0];
  return renderLines(s.lines, cell, { inline: opts.inline ?? s.inline, dpr: opts.dpr });
}

/** The compact one-line wordmark for the top bar. */
export function renderBrand(index, cell = 3.2) {
  const s = STYLES[index] || STYLES[0];
  const [a, b] = s.lines;
  return renderLines([{ ...a, arch: 0 }, { ...b, scale: 0.5, plate: null }], cell, { inline: true });
}

/** Put a rendered canvas into an element, sized in CSS px. */
export function mount(el, canvas) {
  canvas.style.width = `${canvas.cssWidth}px`;
  canvas.style.height = `${canvas.cssHeight}px`;
  canvas.className = "logo-canvas";
  el.replaceChildren(canvas);
}
