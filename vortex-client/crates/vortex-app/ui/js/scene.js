// The Vortex world: detailed Minecraft-style landscapes drawn at full
// resolution, in ten scenes. Overworld scenes follow the day cycle; the Lush
// Cave, the Nether and the End have their own fixed light.
//
// Blocks use 16×16 textures drawn crisp; sky, light and haze are smooth.
// Depth comes from layers: far land and hills are drawn at low resolution
// and scaled up (a soft depth-of-field blur), then hazed toward the horizon.
// The foreground is crisp, with rim light along the tops of blocks and
// leaves. Static layers are cached and re-tinted a few times a second;
// water, lava, fire, plants, critters and particles animate every frame.

import { makeTextures, hash, rng, mix } from "./textures.js";

export const H = 270; // logical rows; `scale` = screen px per logical row

// ---------------------------------------------------------------- palette --

const NIGHT = { top: "#060920", mid: "#101849", hor: "#26306a", haze: "#18204e", cloud: "#262c5e", cloudShade: "#141838", tint: [8, 12, 44], tintA: 0.62, rim: [130, 150, 255], rimA: 0.12, glow: [150, 170, 255], glowA: 0.22, stars: 1 };
const DAWN1 = { top: "#2b2f7a", mid: "#c3749b", hor: "#ffc28c", haze: "#e6a2ac", cloud: "#ffd8d0", cloudShade: "#c88aa6", tint: [255, 128, 108], tintA: 0.16, rim: [255, 196, 120], rimA: 0.7, glow: [255, 186, 132], glowA: 0.85, stars: 0.25 };
const DAWN2 = { top: "#5b87d8", mid: "#f6b9a0", hor: "#fff1c9", haze: "#f2d9c6", cloud: "#fff7ef", cloudShade: "#e5c3c8", tint: [255, 190, 140], tintA: 0.07, rim: [255, 228, 140], rimA: 0.6, glow: [255, 236, 186], glowA: 0.8, stars: 0 };
const DAY = { top: "#3d8de4", mid: "#89c7f6", hor: "#e1f4ff", haze: "#bcd9ef", cloud: "#ffffff", cloudShade: "#cbdcf0", tint: [255, 255, 255], tintA: 0, rim: [255, 252, 196], rimA: 0.3, glow: [255, 250, 228], glowA: 0.45, stars: 0 };
const DUSK1 = { top: "#3a2f82", mid: "#e0688b", hor: "#ffad66", haze: "#e08a8c", cloud: "#ffc7ae", cloudShade: "#b86c88", tint: [255, 118, 88], tintA: 0.2, rim: [255, 168, 88], rimA: 0.75, glow: [255, 156, 98], glowA: 0.9, stars: 0.1 };
const DUSK2 = { top: "#141340", mid: "#3d2c70", hor: "#86486f", haze: "#4e376a", cloud: "#4b3c74", cloudShade: "#291f48", tint: [38, 24, 70], tintA: 0.48, rim: [210, 120, 170], rimA: 0.2, glow: [255, 136, 140], glowA: 0.28, stars: 0.8 };
const KEYS = [
  [0, NIGHT], [4.8, NIGHT], [6.0, DAWN1], [7.4, DAWN2], [9.4, DAY], [16.4, DAY],
  [18.1, DUSK1], [19.6, DUSK2], [21.0, NIGHT], [24, NIGHT],
];
const FIXED = { dawn: 6.6, day: 12.5, dusk: 18.35, night: 23.0 };

export function phaseOf(hour) {
  if (hour >= 5.2 && hour < 8.6) return "dawn";
  if (hour >= 8.6 && hour < 17.4) return "day";
  if (hour >= 17.4 && hour < 20.4) return "dusk";
  return "night";
}

const hex = (h) => [parseInt(h.slice(1, 3), 16), parseInt(h.slice(3, 5), 16), parseInt(h.slice(5, 7), 16)];
const lerp = (a, b, t) => a + (b - a) * t;
const rgb = (c, a = 1) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a})`;
const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const smooth = (t) => t * t * (3 - 2 * t);

function resolve(p) {
  const out = {};
  for (const [k, v] of Object.entries(p)) out[k] = typeof v === "string" ? hex(v) : v;
  return out;
}

function cyclePalette(hour) {
  for (let i = 0; i < KEYS.length - 1; i++) {
    const [h0, p0] = KEYS[i];
    const [h1, p1] = KEYS[i + 1];
    if (hour >= h0 && hour <= h1) {
      const s = smooth(h1 === h0 ? 0 : (hour - h0) / (h1 - h0));
      const out = {};
      for (const k of Object.keys(p0)) {
        const a = p0[k], b = p1[k];
        if (typeof a === "number") out[k] = lerp(a, b, s);
        else if (Array.isArray(a)) out[k] = mix(a, b, s);
        else out[k] = mix(hex(a), hex(b), s);
      }
      return out;
    }
  }
  return cyclePalette(0);
}

// ----------------------------------------------------------------- scenes --

/** The scenes you can pick, in order. */
export const SCENES = [
  { id: "golden", name: "Golden Meadow" },
];
export const ALL_SCENES = [
  { id: "golden", name: "Golden Meadow" },
  { id: "sunset", name: "Sunset Lake" },
  { id: "valley", name: "Valley" },
  { id: "vista", name: "Vortex Valley" },
  { id: "meadow", name: "Sunrise Meadow" },
  { id: "cherry", name: "Cherry Grove" },
  { id: "peaks", name: "Snowy Peaks" },
  { id: "desert", name: "Desert Oasis" },
  { id: "ocean", name: "Ocean Shore" },
  { id: "jungle", name: "Jungle" },
  { id: "village", name: "Village Hills" },
  { id: "lush", name: "Lush Cave" },
  { id: "nether", name: "Nether" },
  { id: "end", name: "The End" },
];

const DEFS = {
  custom: {
    image: "custom", sunAt: null, glowCol: [255, 230, 200], dust: [255, 240, 220], phase: "day",
  },
  golden: {
    image: "golden", sunAt: [0.445, 0.33], glowCol: [255, 196, 120], dust: [255, 226, 150], phase: "dusk",
    ground: { top: "cleanGrass", sub: "cleanDirt", deep: "cleanStone" },
    far: "none", hills: null, mid: { ridge: null, trees: [], density: 0 },
    fore: { tree: null }, water: "lake", fire: "campfire", flora: [],
    particles: "petals", leaf: [250, 190, 214], critters: {}, clouds: 0, sun: false,
  },
  sunset: {
    image: "sunset", sunAt: [0.49, 0.45], glowCol: [255, 170, 80], dust: [255, 200, 120], phase: "dusk",
    ground: { top: "cleanGrass", sub: "cleanDirt", deep: "cleanStone" },
    far: "none", hills: null, mid: { ridge: null, trees: [], density: 0 },
    fore: { tree: null }, water: "lake", fire: "campfire", flora: [],
    particles: "petals", leaf: [250, 190, 214], critters: {}, clouds: 0, sun: false,
  },
  valley: {
    image: "valley", sunAt: [0.3, 0.12], glowCol: [255, 245, 220], dust: [255, 255, 235], phase: "day",
    ground: { top: "cleanGrass", sub: "cleanDirt", deep: "cleanStone" },
    far: "none", hills: null, mid: { ridge: null, trees: [], density: 0 },
    fore: { tree: null }, water: "lake", fire: "campfire", flora: [],
    particles: "petals", leaf: [250, 190, 214], critters: {}, clouds: 0, sun: false,
  },
  vista: {
    tweak: { color: [150, 205, 255], amount: 0.12 },
    ground: { top: "cleanGrass", sub: "cleanDirt", deep: "cleanStone" },
    far: "vista", hills: { kind: "vista" },
    mid: { kind: "vista", ridge: "vista", trees: [], density: 0 },
    fore: { tree: "oak", nest: true, frame: "birch" },
    water: "lake", fire: "campfire",
    flora: ["poppy", "dandelion", "cornflower", "tulip", "tufts", "tufts"],
    particles: "petals", leaf: [250, 190, 214], critters: { bees: 3, butterflies: 3, birds: true, fireflies: true, bats: 1, creeper: true, enderman: true },
    clouds: 4, softClouds: 6, sun: true, farHaze: 0.42,
  },
  meadow: {
    ground: { top: "grass", sub: "dirt", deep: "stone" },
    far: "mountains", hills: { top: "#5f9a46", body: "#6f7f52", tree: "oak" },
    mid: { ridge: "grass", trees: ["oak", "oak", "birch"], density: 0.34 },
    fore: { tree: "oak", nest: true, frame: "oak" },
    water: "pond", fire: "campfire",
    flora: ["poppy", "dandelion", "cornflower", "daisy", "allium", "tufts"],
    particles: "leaves", leaf: [96, 160, 56], critters: { bees: 4, butterflies: 3, birds: true, fireflies: true },
    clouds: 9, sun: true,
  },
  cherry: {
    tweak: { color: [255, 170, 205], amount: 0.28 },
    ground: { top: "grass", sub: "dirt", deep: "stone" },
    far: "mountains", hills: { top: "#e6a0c0", body: "#8a7f88", tree: "cherry" },
    mid: { ridge: "grass", trees: ["cherry", "cherry", "cherry"], density: 0.4 },
    fore: { tree: "cherry", nest: true, frame: "cherry" },
    water: "pond", fire: "campfire",
    flora: ["petals", "petals", "tulip", "allium", "tufts"],
    particles: "petals", leaf: [250, 190, 214], critters: { bees: 5, butterflies: 4, birds: true, fireflies: true },
    clouds: 8, sun: true,
  },
  peaks: {
    tweak: { color: [180, 210, 255], amount: 0.2 },
    ground: { top: "snowGrass", sub: "dirt", deep: "stone" },
    far: "snowpeaks", hills: { top: "#f4f8ff", body: "#7f8fa6", tree: "spruce" },
    mid: { ridge: "snowGrass", trees: ["snowSpruce", "snowSpruce", "spruce"], density: 0.42 },
    fore: { tree: "snowSpruce", frame: null },
    water: "ice", fire: "campfire",
    flora: ["tufts"], snowCover: true,
    particles: "snow", leaf: [255, 255, 255], critters: { birds: true },
    clouds: 7, sun: true,
  },
  desert: {
    tweak: { color: [255, 190, 120], amount: 0.12 },
    ground: { top: "sand", sub: "sandstone", deep: "sandstone" },
    far: "dunes", hills: { top: "#e8d29a", body: "#d2b47a", tree: "cactus" },
    mid: { ridge: "sand", trees: ["cactus", "cactus", "deadbush"], density: 0.3 },
    fore: { tree: "cactus", frame: null },
    water: "pool", fire: "campfire",
    flora: ["deadBush", "deadBush"],
    particles: "dust", leaf: [230, 210, 160], critters: { birds: true },
    clouds: 3, sun: true,
  },
  ocean: {
    tweak: { color: [140, 200, 255], amount: 0.15 },
    ground: { top: "sand", sub: "sand", deep: "sandstone" },
    far: "sea", hills: null,
    mid: { ridge: null, trees: [], density: 0 },
    fore: { tree: "oak", frame: null },
    water: "sea", fire: "campfire",
    flora: ["tufts", "deadBush"],
    particles: "leaves", leaf: [96, 160, 56], critters: { birds: true, fish: true, fireflies: false },
    clouds: 10, sun: true,
  },
  jungle: {
    tweak: { color: [150, 230, 160], amount: 0.16 },
    ground: { top: "jungleGrass", sub: "dirt", deep: "stone" },
    far: "jungle", hills: { top: "#3f8f34", body: "#4b6a3a", tree: "jungle" },
    mid: { ridge: "jungleGrass", trees: ["jungle", "jungle", "bush"], density: 0.6 },
    fore: { tree: "jungle", frame: "jungle" },
    water: "pond", fire: "campfire",
    flora: ["ferns", "ferns", "tufts", "poppy"],
    particles: "leaves", leaf: [60, 150, 40], critters: { butterflies: 4, birds: true, fireflies: true },
    clouds: 7, sun: true,
  },
  village: {
    ground: { top: "grass", sub: "dirt", deep: "stone" },
    far: "mountains", hills: { top: "#6aa84f", body: "#6f7f52", tree: "oak" },
    mid: { ridge: "grass", trees: ["house", "house", "oak", "house"], density: 0.7 },
    fore: { tree: "oak", frame: null, path: true },
    water: "none", fire: "campfire",
    flora: ["poppy", "dandelion", "tufts", "tufts"],
    particles: "leaves", leaf: [96, 160, 56], critters: { butterflies: 2, birds: true, fireflies: true },
    clouds: 9, sun: true,
  },
  lush: {
    time: { hour: 22, phase: "night", palette: { top: "#05090a", mid: "#0b1612", hor: "#1b2c22", haze: "#15241c", cloud: "#000000", cloudShade: "#000000", tint: [10, 28, 20], tintA: 0.38, rim: [190, 255, 150], rimA: 0.22, glow: [200, 255, 170], glowA: 0, stars: 0 } },
    ground: { top: "mossCap", sub: "clay", deep: "deep" },
    far: "cave", hills: null,
    mid: { ridge: "moss", trees: ["azalea", "azalea", "bush"], density: 0.4 },
    fore: { tree: "azalea", frame: null },
    water: "pool", fire: "campfire", ceiling: "cave",
    flora: ["dripleaf", "tufts", "ferns", "dripleaf"],
    particles: "spores", leaf: [140, 200, 90], critters: { bats: true, fireflies: true, fish: true },
    clouds: 0, sun: false,
  },
  nether: {
    time: { hour: 19, phase: "dusk", palette: { top: "#1c0404", mid: "#4a0f0a", hor: "#9a3216", haze: "#6e200e", cloud: "#000000", cloudShade: "#000000", tint: [70, 14, 4], tintA: 0.18, rim: [255, 130, 60], rimA: 0.55, glow: [255, 110, 40], glowA: 0.5, stars: 0 } },
    ground: { top: "nylium", sub: "netherrack", deep: "netherrack" },
    far: "nether", hills: { top: "#7a1e1e", body: "#4e1616", tree: "fungus" },
    mid: { ridge: "nylium", trees: ["crimson", "warped", "crimson"], density: 0.38 },
    fore: { tree: "crimson", frame: null },
    water: "lava", fire: "soul", ceiling: "nether",
    flora: ["roots", "roots", "sprouts", "mushroom"],
    particles: "ash", leaf: [255, 140, 60], critters: {},
    clouds: 0, sun: false,
  },
  end: {
    time: { hour: 23, phase: "night", palette: { top: "#040209", mid: "#120922", hor: "#2a1844", haze: "#24163a", cloud: "#000000", cloudShade: "#000000", tint: [26, 12, 46], tintA: 0.46, rim: [210, 170, 255], rimA: 0.14, glow: [200, 150, 255], glowA: 0, stars: 1 } },
    ground: { top: "endstone", sub: "endstone", deep: "endstone" },
    far: "end", hills: null,
    mid: { ridge: "endstone", trees: ["chorus", "chorus", "chorus"], density: 0.36 },
    fore: { tree: "chorus", frame: null },
    water: "none", fire: "endrod",
    flora: [],
    particles: "portal", leaf: [200, 140, 255], critters: { dragon: true },
    clouds: 0, sun: false,
  },
};

// ------------------------------------------------------------------- trees --

/** Draw a tree (or other tall feature) onto a layer. Returns glow points. */
function drawTree(g, T, kind, x, base, bs, rims, r) {
  const put = (tex, cx, cy, rim) => {
    g.drawImage(tex, cx, cy, bs + 0.4, bs + 0.4);
    if (rim) rims.push([cx, cy, bs]);
  };
  const lights = [];
  const canopy = (leaf, ct, rows) => {
    for (const [a, b, dy] of rows) {
      for (let k = a; k <= b; k++) {
        if ((k === a || k === b) && dy !== 0 && r() > 0.6) continue;
        const top = dy === rows[rows.length - 1][2] || ((k === a || k === b) && dy !== rows[0][2]);
        put(leaf, x + k * bs, ct + dy * bs, top || r() > 0.85);
      }
    }
  };
  switch (kind) {
    case "oak":
    case "birch":
    case "azalea": {
      const logT = kind === "birch" ? T.birch : T.oakLog;
      const leaf = kind === "birch" ? T.birchLeaf : kind === "azalea" ? T.azaleaLeaf : T.oakLeaf;
      const th = kind === "azalea" ? 2 : 3 + Math.floor(r() * 2);
      for (let k = 1; k <= th; k++) put(logT, x, base - k * bs);
      const ct = base - th * bs - bs;
      canopy(leaf, ct, kind === "azalea" ? [[-1, 1, 0], [-1, 1, -1], [0, 0, -2]] : [[-2, 2, 0], [-2, 2, -1], [-1, 1, -2]]);
      g.fillStyle = "rgba(10,20,10,0.35)";
      g.fillRect(x, base - th * bs, bs, bs * 0.6);
      break;
    }
    case "spruce":
    case "snowSpruce": {
      const leaf = kind === "snowSpruce" ? T.snowLeaf : T.spruceLeaf;
      const th = 6 + Math.floor(r() * 2);
      for (let k = 1; k <= th; k++) put(T.spruceLog, x, base - k * bs);
      const layers = [2, 1, 2, 1, 1, 0, 0];
      for (let i = 0; i < layers.length; i++) {
        const y = base - (2 + i) * bs;
        const wv = layers[i];
        for (let k = -wv; k <= wv; k++) put(leaf, x + k * bs, y, k === -wv || k === wv || i === layers.length - 1);
      }
      put(leaf, x, base - (2 + layers.length) * bs, true);
      break;
    }
    case "cherry": {
      const th = 4;
      for (let k = 1; k <= th; k++) put(T.cherryLog, x, base - k * bs);
      put(T.cherryLog, x + bs, base - (th - 1) * bs);
      const ct = base - th * bs - bs;
      canopy(T.cherryLeaf, ct, [[-3, 3, 0], [-3, 3, -1], [-2, 2, -2]]);
      // Hanging blossoms.
      for (let k = -3; k <= 3; k++) if (r() > 0.6) put(T.cherryLeaf, x + k * bs, ct + bs, false);
      break;
    }
    case "jungle": {
      const th = 8 + Math.floor(r() * 3);
      for (let k = 1; k <= th; k++) put(T.jungleLog, x, base - k * bs);
      const ct = base - th * bs - bs;
      canopy(T.jungleLeaf, ct, [[-3, 3, 0], [-3, 3, -1], [-2, 2, -2]]);
      // Vines.
      g.fillStyle = "#3d7a25";
      for (let k = -3; k <= 3; k++) {
        if (r() > 0.55) continue;
        const len = bs * (1 + r() * 3);
        g.fillRect(x + k * bs + bs * (0.2 + r() * 0.5), ct + bs, Math.max(2, bs * 0.12), len);
      }
      break;
    }
    case "bush":
      put(T.oakLeaf, x, base - bs, true);
      if (r() > 0.4) put(T.oakLeaf, x + bs, base - bs, true);
      break;
    case "cactus": {
      const hgt = 2 + Math.floor(r() * 2);
      for (let k = 1; k <= hgt; k++) put(T.cactus, x, base - k * bs, k === hgt);
      if (r() > 0.5) { put(T.cactus, x + bs, base - 2 * bs, true); }
      break;
    }
    case "deadbush":
      g.drawImage(T.deadBush, x, base - bs, bs, bs);
      break;
    case "crimson":
    case "warped": {
      const stem = kind === "crimson" ? T.stemC : T.stemW;
      const wart = kind === "crimson" ? T.wart : T.warpedWart;
      const th = 4 + Math.floor(r() * 3);
      for (let k = 1; k <= th; k++) put(stem, x, base - k * bs);
      const ct = base - th * bs - bs;
      const rows = [[-2, 2, 0], [-2, 2, -1], [-1, 1, -2]];
      for (const [a, b, dy] of rows) {
        for (let k = a; k <= b; k++) {
          const shroom = r() > 0.86;
          put(shroom ? T.shroom : wart, x + k * bs, ct + dy * bs, dy === -2 || k === a || k === b);
          if (shroom) lights.push([x + k * bs + bs / 2, ct + dy * bs + bs / 2]);
        }
      }
      // Weeping vines under crimson caps.
      if (kind === "crimson") {
        g.fillStyle = "#a01c1c";
        for (let k = -2; k <= 2; k++) if (r() > 0.5) g.fillRect(x + k * bs + bs * 0.4, ct + bs, Math.max(2, bs * 0.14), bs * (0.8 + r() * 2));
      }
      break;
    }
    case "chorus": {
      let cx = x, cy = base;
      const hgt = 3 + Math.floor(r() * 3);
      for (let k = 1; k <= hgt; k++) {
        cy = base - k * bs;
        put(T.chorus, cx, cy, false);
        if (k > 1 && r() > 0.55) {
          const dir = r() > 0.5 ? 1 : -1;
          put(T.chorus, cx + dir * bs, cy, false);
          put(T.chorus, cx + dir * bs, cy - bs, false);
          put(T.chorusFlower, cx + dir * bs, cy - 2 * bs, true);
        }
      }
      put(T.chorusFlower, cx, cy - bs, true);
      break;
    }
    case "house": {
      const w = 4, hh = 3;
      const x0 = x - bs;
      for (let i = 0; i < w; i++) {
        for (let j = 1; j <= hh; j++) {
          const corner = i === 0 || i === w - 1;
          put(corner ? T.oakLog : T.planks, x0 + i * bs, base - j * bs, false);
        }
      }
      put(T.cobble[0], x0, base - bs, false);
      put(T.cobble[1], x0 + (w - 1) * bs, base - bs, false);
      put(T.door, x0 + bs, base - bs, false);
      put(T.door, x0 + bs, base - 2 * bs, false);
      put(T.glass, x0 + 2 * bs, base - 2 * bs, false);
      lights.push([x0 + 2.5 * bs, base - 1.5 * bs, "window"]);
      // Roof: steps of dark planks.
      for (let k = 0; k < 3; k++) {
        const y = base - (hh + 1 + k) * bs;
        for (let i = -1 + k; i <= w - k; i++) put(T.darkPlanks, x0 + i * bs, y, true);
      }
      break;
    }
    default:
      break;
  }
  return lights;
}

// ------------------------------------------------------------------ world --

export class World {
  /**
   * @param canvas the canvas to draw into
   * @param opts { offscreen, width, height, scene, hour } — offscreen worlds
   *   don't follow the window or animate; call drawStill() for a picture.
   */
  constructor(canvas, opts = {}) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.offscreen = !!opts.offscreen;
    this.T = TEXTURES();
    this.sceneId = DEFS[opts.scene] ? opts.scene : "meadow";
    this.mode = "cycle";
    this.dayLength = 10 * 60;
    this.cycleHour = opts.hour ?? 6.2;
    this.cycleRef = performance.now();
    this.days = 0;
    this.animate = true;
    this.life = true;
    this.parallax = 0;
    this.parallaxTarget = 0;
    this.rise = this.offscreen ? 1 : 0;
    this.last = performance.now();
    this.listeners = new Set();
    this.lastPhase = null;
    this.particles = [];
    this.broken = null;
    this.torch = null;
    this.fish = null;
    this.dragon = null;
    this.tintKey = "";
    this.tintAt = 0;
    if (this.offscreen) {
      this.fixedHour = opts.hour;
      this.resize(opts.width, opts.height, opts.dpr || 1);
      return;
    }
    this.resize();
    window.addEventListener("resize", () => { if (!this.viewport) this.resize(); });
    window.addEventListener("mousemove", (e) => {
      this.mouseY = e.clientY / window.innerHeight;
      this.parallaxTarget = (e.clientX / window.innerWidth - 0.5) * 2;
    });
    requestAnimationFrame((t) => this.loop(t));
  }

  get def() { return DEFS[this.sceneId] || DEFS.meadow; }

  // ------------------------------------------------------------- public --

  onPhase(fn) { this.listeners.add(fn); }

  setScene(id) {
    if (!DEFS[id] || id === this.sceneId) return;
    this.sceneId = id;
    this.particles = [];
    this.broken = null;
    this.build();
    this.tintKey = "";
    this.emitPhase(true);
  }

  setMode(mode) {
    const h = this.hour();
    this.mode = mode || "cycle";
    if (this.mode === "cycle") { this.cycleHour = h; this.cycleRef = performance.now(); }
    this.tintKey = "";
    this.emitPhase(true);
  }

  setDayLength(minutes) {
    const h = this.hour();
    this.dayLength = Math.max(1, Number(minutes) || 10) * 60;
    this.cycleHour = h;
    this.cycleRef = performance.now();
  }

  setAnimate(on) { this.animate = on; }

  /** Critters and particles on or off (the "World life" setting). */
  setLife(on) { this.life = on !== false; }
  /** Extra darkening at the wallpaper edges, 0 to 0.6. */
  setDim(v) { this.dim = v; }

  /** True when the scene has its own fixed light (cave, Nether, End). */
  get fixedTime() { return !!this.def.time; }

  hour() {
    if (this.def.time) return this.def.time.hour;
    if (this.offscreen && this.fixedHour != null) return this.fixedHour;
    if (this.mode in FIXED) return FIXED[this.mode];
    if (this.mode === "clock") {
      const d = new Date();
      return d.getHours() + d.getMinutes() / 60 + d.getSeconds() / 3600;
    }
    const elapsed = (performance.now() - this.cycleRef) / 1000;
    const total = this.cycleHour + (elapsed / this.dayLength) * 24;
    this.days = Math.floor(total / 24);
    return ((total % 24) + 24) % 24;
  }

  phase() {
    if (this.def.image) return this.def.phase;
    return this.def.time ? this.def.time.phase : phaseOf(this.hour());
  }

  palette() {
    const d = this.def;
    if (d.time) return resolve(d.time.palette);
    const p = cyclePalette(this.hour());
    if (d.tweak) {
      const { color, amount } = d.tweak;
      for (const k of ["haze", "hor", "mid", "cloudShade"]) p[k] = mix(p[k], color, amount * (k === "haze" ? 1 : 0.6));
    }
    return p;
  }

  timeLabel(h12 = false) {
    if (this.def.time) return SCENES.find((s) => s.id === this.sceneId)?.name || "";
    const h = this.hour();
    let hh = Math.floor(h);
    const mm = String(Math.floor((h - hh) * 60)).padStart(2, "0");
    if (h12) {
      const ap = hh >= 12 ? "PM" : "AM";
      hh = hh % 12 || 12;
      return `${hh}:${mm} ${ap}`;
    }
    return `${String(hh).padStart(2, "0")}:${mm}`;
  }

  /** Darkness 0 (noon) … 1 (midnight). */
  darkness() {
    return Math.min(1, this.palette().tintA / 0.62);
  }

  get scale() { return this.h / H; }

  /** Where the player's feet go, in screen pixels. */
  /** Put the world in a box on screen (the Home wallpaper card). */
  setViewport(x, y, w, h) {
    const v = this.viewport;
    if (v && v.x === x && v.y === y && v.w === w && v.h === h) return;
    this.viewport = { x, y, w, h };
    this.ox = x;
    this.oy = y;
    Object.assign(this.canvas.style, { left: `${x}px`, top: `${y}px`, right: "auto", bottom: "auto" });
    this.resize(w, h);
  }

  /** Screen x of the middle of the world. */
  centerX() { return (this.ox || 0) + this.w / 2; }

  anchor() {
    if (this.def.image) {
      if (this.feet) return { x: this.feet.x, y: this.feet.y, scale: this.scale };
      return { x: (this.ox || 0) + this.w / 2, y: (this.oy || 0) + this.h - 140, scale: this.scale };
    }
    return { x: this.playerX, y: this.groundY, scale: this.scale };
  }

  /** True for picture backgrounds (no block world to interact with). */
  get picture() { return !!this.def.image; }

  /** Screen-x of named spots, relative to the player's home. */
  spot(name) {
    const col = { punch: this.punchCol, fire: this.fireCol, pond: this.pondEnd }[name];
    return this.colX(col) + this.BS / 2 - this.playerX;
  }

  /** Visible critters near the player, in screen pixels. */
  critters() {
    return [...(this.bees || []), ...(this.butterflies || [])].filter((c) => c.visible).map((c) => ({ x: c.x, y: c.y }));
  }

  setTorch(screenX, screenY, on) {
    this.torch = on ? { x: screenX, y: screenY } : null;
  }

  /** One hit on the block in front of the player; it breaks after a few. */
  hitBlock() {
    const now = performance.now();
    if (this.broken && this.broken.until > now) return false;
    if (!this.broken || this.broken.until) this.broken = { col: this.punchCol, crack: 0, until: 0, grown: 0 };
    this.broken.crack += 1;
    const x = this.colX(this.punchCol) + this.BS / 2;
    this.spawnChunks(x, this.groundY + 4, 3);
    if (this.broken.crack >= 4) {
      this.broken.until = now + 7000;
      this.spawnChunks(x, this.groundY + this.BS / 2, 14);
      return true;
    }
    return false;
  }

  // --------------------------------------------------------- generation --

  colX(c) { return this.x0 + c * this.BS; }

  resize(w = window.innerWidth, h = window.innerHeight, dpr = Math.min(2, window.devicePixelRatio || 1)) {
    this.dpr = dpr;
    this.w = w;
    this.h = h;
    this.canvas.width = Math.round(w * dpr);
    this.canvas.height = Math.round(h * dpr);
    if (!this.offscreen) {
      this.canvas.style.width = `${w}px`;
      this.canvas.style.height = `${h}px`;
    }
    this.build();
    this.tintKey = "";
  }

  layerCanvas(res, margin) {
    const c = document.createElement("canvas");
    c.width = Math.max(1, Math.round((this.w + margin * 2) * this.dpr * res));
    c.height = Math.max(1, Math.round(this.h * this.dpr * res));
    const g = c.getContext("2d");
    g.scale(this.dpr * res, this.dpr * res);
    g.translate(margin, 0);
    g.imageSmoothingEnabled = false;
    return { c, g, res, margin };
  }

  build() {
    const { w, h } = this;
    const s = h / H;
    const BS = Math.max(22, Math.round(s * 16));
    this.BS = BS;
    this.texel = BS / 16;
    this.groundY = Math.round(198 * s);
    const margin = 48;
    this.x0 = -margin;
    const cols = Math.ceil((w + margin * 2) / BS) + 1;
    this.cols = cols;
    const pc = Math.round((w * 0.4 - this.x0) / BS - 0.5);
    this.playerCol = pc;
    this.playerX = this.colX(pc) + BS / 2;
    this.punchCol = pc - 2;
    this.fireCol = pc + 3;
    const water = this.def.water;
    this.pondStart = pc - (water === "pool" ? 9 : 7);
    this.pondEnd = pc - 5;
    if (water === "none" || water === "sea" || water === "lake") { this.pondStart = -99; this.pondEnd = -99; }
    this.treeCol = pc + 7;
    this.lights = [];
    this.glows = [];

    const off = new Array(cols).fill(0);
    for (let c = 0; c < cols; c++) {
      const d = c - pc;
      const n = Math.sin(c * 0.5) * 0.8 + Math.sin(c * 0.23 + 1.3) * 1.1;
      if (d >= -4 && d <= 3) off[c] = 0;
      else if (c >= this.pondStart && c <= this.pondEnd) off[c] = -2;
      else if (water === "sea" && d < -4) off[c] = d < -9 ? 0 : 0;
      else if (d < -7) off[c] = clamp(Math.round(1 + n * 0.6 + (-d - 8) * 0.25), 1, 4);
      else if (d <= 5) off[c] = 1;
      else off[c] = clamp(Math.round(2 + n * 0.7 + (d - 6) * 0.12), 2, 5);
      if (water === "sea" && d > 5) off[c] = clamp(off[c], 1, 3);
    }
    this.off = off;
    this.topOf = (c) => this.groundY - (off[clamp(c, 0, cols - 1)] || 0) * BS;

    this.buildFar();
    this.buildHills();
    this.buildMid();
    this.buildFore();
    this.buildFlora();
    this.buildClouds();
    this.buildStars();
    this.buildCritters();
    this.soft = null;
    this.motes = null;
    this.tinted = null;
  }

  /** Far land: mountains, dunes, sea islands, jungle canopy, Nether cliffs, End islands, cave walls. */
  buildFar() {
    const kind = this.def.far;
    const L = this.layerCanvas(0.22, 60);
    const { g } = L;
    const { w, h } = this;
    const r = rng(11 + kind.length);
    const bs = this.BS * 0.62;
    const base = this.groundY - this.BS * 2.2;
    this.farLights = [];
    this.lavaFalls = [];
    this.crystals = [];
    const ridge = (peaksN, hMin, hMax, fill, side, snowLine, snow = "#f4f8ff") => {
      const peaks = [];
      for (let x = -60; x < w + 60; x += w / peaksN) peaks.push({ x: x + r() * w * 0.12, hgt: h * (hMin + r() * (hMax - hMin)), wid: w * (0.16 + r() * 0.14) });
      for (let x = -60; x < w + 60; x += bs) {
        let top = base;
        for (const p of peaks) {
          const d = Math.abs(x - p.x) / p.wid;
          if (d < 1) top = Math.min(top, base - p.hgt * (1 - d) * (1 - d * 0.35));
        }
        top = Math.round(top / bs) * bs;
        g.fillStyle = fill;
        g.fillRect(x, top, bs + 0.5, h - top);
        g.fillStyle = side;
        g.fillRect(x + bs * 0.6, top, bs * 0.4 + 0.5, h - top);
        if (snowLine != null && base - top > h * snowLine) {
          g.fillStyle = snow;
          g.fillRect(x, top, bs + 0.5, bs * (1 + r() * (kind === "snowpeaks" ? 3 : 1)));
        }
      }
    };
    if (kind === "mountains") ridge(4.2, 0.16, 0.3, "#7d8fa8", "#6a7c96", 0.12);
    else if (kind === "snowpeaks") ridge(3.4, 0.24, 0.42, "#8a9ab4", "#71829f", 0.08);
    else if (kind === "dunes") {
      for (let layer = 0; layer < 2; layer++) {
        let lvl = 2;
        for (let x = -60; x < w + 60; x += bs) {
          lvl = clamp(lvl + (r() > 0.7 ? (r() > 0.5 ? 1 : -1) : 0), 0, 5);
          const top = base - lvl * bs * 0.8 - layer * bs * 2 + bs * 2;
          g.fillStyle = layer ? "#e2b46a" : "#cf9a52";
          g.fillRect(x, top, bs + 0.5, h - top);
        }
      }
      // A desert temple on the horizon.
      const tx = w * 0.72;
      for (let k = 0; k < 6; k++) {
        g.fillStyle = k % 2 ? "#c9a46a" : "#d6b47a";
        g.fillRect(tx - (6 - k) * bs, base - k * bs, (6 - k) * 2 * bs, bs + 0.5);
      }
      g.fillStyle = "#b8925a";
      g.fillRect(tx - 6.5 * bs, base - 7 * bs, bs * 1.2, bs * 7);
      g.fillRect(tx + 5.3 * bs, base - 7 * bs, bs * 1.2, bs * 7);
    } else if (kind === "sea") {
      const sea = this.groundY - this.BS * 3.6;
      for (let i = 0; i < 4; i++) {
        const ix = w * (0.1 + r() * 0.8), iw = bs * (3 + r() * 6);
        const top = sea - bs * (1 + r() * 2);
        g.fillStyle = "#5f7f6a";
        g.fillRect(ix, top, iw, sea - top + 2);
        g.fillStyle = "#3f6a44";
        g.fillRect(ix + bs, top - bs * 1.2, iw - bs * 2, bs * 1.4);
      }
      g.fillStyle = "#8aa4b8";
      for (let x = -60; x < w + 60; x += bs) g.fillRect(x, sea - bs * (2 + Math.abs(Math.sin(x * 0.004)) * 4), bs + 0.5, h);
    } else if (kind === "jungle") {
      for (let x = -60; x < w + 60; x += bs * 2) {
        const hgt = h * (0.08 + r() * 0.12);
        const top = base - hgt;
        g.fillStyle = r() > 0.5 ? "#2f6a2c" : "#3a7a32";
        g.fillRect(x, top, bs * 2.6, h - top);
        g.fillStyle = "#4f3a22";
        if (r() > 0.6) g.fillRect(x + bs, top - h * 0.08, bs * 0.5, h * 0.08);
        g.fillStyle = "#2a5f26";
        if (r() > 0.6) g.fillRect(x - bs * 0.5, top - h * 0.1, bs * 3.5, h * 0.03);
      }
    } else if (kind === "nether") {
      ridge(3, 0.2, 0.46, "#5a1414", "#481010", null);
      for (let i = 0; i < 4; i++) {
        const fx = w * (0.12 + i * 0.24 + r() * 0.08);
        const top = h * (0.1 + r() * 0.12);
        this.lavaFalls.push({ x: fx, top, bottom: base + this.BS, w: bs * (0.8 + r() * 0.8) });
      }
      for (let i = 0; i < 6; i++) {
        const px = r() * w, ph = h * (0.1 + r() * 0.2);
        g.fillStyle = "#3a3438";
        g.fillRect(px, base - ph, bs * (1 + r()), ph + h);
      }
    } else if (kind === "end") {
      for (let i = 0; i < 7; i++) {
        const ix = r() * w, iy = h * (0.2 + r() * 0.35), iw = bs * (4 + r() * 10);
        for (let k = 0; k < 4; k++) {
          g.fillStyle = k === 0 ? "#d8dc9c" : "#b7bb80";
          g.fillRect(ix + k * bs * 0.8, iy + k * bs * 0.7, iw - k * bs * 1.6, bs * 0.8);
        }
      }
      for (let i = 0; i < 6; i++) {
        const px = w * (0.06 + i * 0.17 + r() * 0.05), ph = h * (0.22 + r() * 0.3);
        g.fillStyle = "#140d22";
        g.fillRect(px, base - ph, bs * 1.6, ph + h);
        g.fillStyle = "#24183a";
        g.fillRect(px + bs, base - ph, bs * 0.6, ph + h);
        this.crystals.push({ x: px + bs * 0.8, y: base - ph - bs * 1.2, ph: r() * 6 });
      }
      g.fillStyle = "#c9cd8e";
      g.fillRect(-60, base, w + 120, h);
    } else if (kind === "vista") {
      // Big jagged mountains with snow, lit from the left.
      const mountains = [
        { x: 0.1, top: 0.12, w: 0.2 }, { x: 0.3, top: 0.24, w: 0.16 }, { x: 0.46, top: 0.3, w: 0.14 },
        { x: 0.6, top: 0.27, w: 0.15 }, { x: 0.8, top: 0.04, w: 0.26 }, { x: 0.98, top: 0.18, w: 0.18 },
      ];
      const foot = this.groundY - this.BS * 2.6;
      for (const m of mountains) {
        const cx = m.x * w, peak = h * m.top, half = m.w * w;
        const pts = [[cx - half, foot]];
        const steps = 9;
        for (let i = 1; i < steps; i++) {
          const k = i / steps;
          const side = k < 0.5 ? k * 2 : (1 - k) * 2;
          pts.push([cx - half + k * half * 2 + (r() - 0.5) * half * 0.12, foot - (foot - peak) * Math.pow(side, 0.85) + (r() - 0.3) * h * 0.03]);
        }
        pts.push([cx, peak]);
        pts.sort((a, b) => a[0] - b[0]);
        pts.push([cx + half, foot]);
        g.save();
        g.beginPath();
        g.moveTo(cx - half, foot);
        for (const [px, py] of pts) g.lineTo(px, py);
        g.closePath();
        g.clip();
        const rock = g.createLinearGradient(0, peak, 0, foot);
        rock.addColorStop(0, "#8d96a8");
        rock.addColorStop(1, "#5d6b72");
        g.fillStyle = rock;
        g.fillRect(cx - half, peak - 10, half * 2, foot - peak + 20);
        // Snow above a ragged line.
        const snowLine = peak + (foot - peak) * (0.45 + r() * 0.15);
        g.fillStyle = "#f6f9ff";
        g.beginPath();
        g.moveTo(cx - half, peak - 10);
        for (let x = cx - half; x <= cx + half; x += half * 0.08) g.lineTo(x, snowLine + Math.sin(x * 0.13) * h * 0.02 + (r() - 0.5) * h * 0.03);
        g.lineTo(cx + half, peak - 10);
        g.fill();
        // Shadowed right faces and a few rock streaks.
        g.fillStyle = "rgba(40,50,80,0.32)";
        g.beginPath();
        g.moveTo(cx, peak);
        g.lineTo(cx + half, foot);
        g.lineTo(cx + half * 0.15, foot);
        g.fill();
        g.strokeStyle = "rgba(60,64,80,0.35)";
        g.lineWidth = 2;
        for (let i = 0; i < 6; i++) {
          const sx = cx - half * 0.6 + r() * half * 1.2;
          g.beginPath();
          g.moveTo(sx, snowLine - h * 0.04);
          g.lineTo(sx + (r() - 0.5) * 20, snowLine + h * 0.08);
          g.stroke();
        }
        g.restore();
      }
    } else if (kind === "cave") {
      for (let x = -60; x < w + 60; x += bs) {
        const top = base - h * (0.1 + Math.abs(Math.sin(x * 0.01)) * 0.14);
        g.fillStyle = "#26302c";
        g.fillRect(x, top, bs + 0.5, h - top);
        if (r() > 0.8) this.farLights.push([x, top + bs * r() * 4]);
      }
    }
    this.far = L;
  }

  /** Where the lake meets the far shore (Vortex Valley). */
  lakeTop() {
    return this.groundY - this.BS * 2.6;
  }

  /** Rolling hills with tree lines, blurred. */
  buildHills() {
    const L = this.layerCanvas(0.34, 60);
    this.hills = L;
    const H0 = this.def.hills;
    if (!H0) return;
    const { g } = L;
    const { w, h } = this;
    const bs = this.BS * 0.7;
    const base = this.groundY - this.BS * 1.1;
    const r = rng(23);
    if (H0.kind === "vista") {
      // Far shore: green hills with cherry groves on the left, terracotta
      // mesas on the right, all standing on the lake line.
      const lake = this.lakeTop();
      for (let x = -60; x < w * 0.55; x += bs) {
        const k = (x + 60) / (w * 0.6);
        const top = Math.round((lake - bs * (1 + Math.sin(x * 0.012) * 0.8 + (1 - k) * 3)) / bs) * bs;
        g.fillStyle = "#5fa846";
        g.fillRect(x, top, bs + 0.5, bs * 0.4);
        g.fillStyle = "#6b7f4c";
        g.fillRect(x, top + bs * 0.4, bs + 0.5, lake - top + bs);
        if (r() > 0.4) {
          g.fillStyle = r() > 0.55 ? "#f2a7c8" : r() > 0.5 ? "#2f6f2c" : "#3f8a34";
          const cw = bs * (1.4 + r() * 1.4);
          g.fillRect(x - cw / 4, top - bs * (0.9 + r() * 0.6), cw, bs * 1.1);
        }
      }
      const bands = ["#c46a3c", "#d99a62", "#b0533a", "#e3b98a", "#a85a3e", "#cf8450"];
      for (let x = w * 0.58; x < w + 60; x += bs) {
        const k = (x - w * 0.58) / (w * 0.42);
        const top = Math.round((lake - bs * (2 + k * 5 + Math.abs(Math.sin(x * 0.02)) * 3)) / bs) * bs;
        for (let y = top, i = 0; y < lake + bs; y += bs * 0.5, i++) {
          g.fillStyle = bands[(i + Math.floor(x / 40)) % bands.length];
          g.fillRect(x, y, bs + 0.5, bs * 0.5 + 0.5);
        }
        g.fillStyle = "#6aa84f";
        if (r() > 0.6) g.fillRect(x, top - bs * 0.25, bs + 0.5, bs * 0.3);
      }
      return;
    }
    for (let x = -60; x < w + 60; x += bs) {
      const n = Math.sin(x * 0.006) * 0.9 + Math.sin(x * 0.017 + 2) * 0.6;
      const top = Math.round((base - bs * (2 + n * 1.6)) / bs) * bs;
      g.fillStyle = H0.top;
      g.fillRect(x, top, bs + 0.5, bs * 0.4);
      g.fillStyle = H0.body;
      g.fillRect(x, top + bs * 0.4, bs + 0.5, h - top);
      if (r() > 0.55) {
        const t = H0.tree;
        if (t === "cactus") {
          g.fillStyle = "#5b8a34";
          g.fillRect(x + bs * 0.3, top - bs * 1.6, bs * 0.4, bs * 1.6);
          continue;
        }
        const th = bs * (1.5 + r() * 1.5) * (t === "jungle" ? 1.8 : 1);
        g.fillStyle = t === "fungus" ? "#6a2a40" : "#4b3a26";
        g.fillRect(x + bs * 0.35, top - th, bs * 0.3, th);
        const leaf = { oak: ["#3f7d34", "#4f8f3a"], cherry: ["#f0a8c8", "#e690b8"], spruce: ["#2f5a3c", "#36654a"], jungle: ["#2f7a2a", "#3a8a30"], fungus: ["#9a1e26", "#1f8a80"] }[t] || ["#3f7d34", "#4f8f3a"];
        g.fillStyle = leaf[r() > 0.4 ? 0 : 1];
        if (t === "spruce") {
          for (let k = 0; k < 4; k++) g.fillRect(x + bs / 2 - bs * (1.4 - k * 0.3), top - th - bs * (0.5 + k * 0.7), bs * (2.8 - k * 0.6), bs * 0.8);
        } else {
          const cw = bs * (2 + Math.floor(r() * 2));
          g.fillRect(x + bs / 2 - cw / 2, top - th - bs * 1.4, cw, bs * 1.6);
          g.fillRect(x + bs / 2 - cw / 4, top - th - bs * 2, cw / 2, bs * 0.7);
        }
      }
    }
  }

  /** Mid-ground: textured trees (or houses) on a low ridge, slightly soft. */
  buildMid() {
    const L = this.layerCanvas(0.6, 60);
    this.mid = L;
    this.midRims = [];
    this.midLights = [];
    const M = this.def.mid;
    this.waterfall = null;
    this.mobsMid = [];
    if (!M.ridge) return;
    const { g } = L;
    const { w, h, T } = this;
    const bs = this.BS * 0.78;
    const base = this.groundY - this.BS * 0.25;
    if (M.kind === "vista") {
      const r = rng(61);
      const lake = this.lakeTop() + bs * 0.6;
      // Left bank sloping down into the lake, with cherry and oak trees.
      for (let x = -60, i = 0; x < w * 0.27; x += bs, i++) {
        const k = (x + 60) / (w * 0.27 + 60);
        const top = Math.round((lake - bs * (2.6 - k * 2.6 + Math.sin(i * 0.7) * 0.4)) / bs) * bs;
        g.drawImage(T.cleanGrass[i % 3], x, top, bs + 0.4, bs + 0.4);
        for (let y = top + bs; y < h; y += bs) g.drawImage(y > top + bs * 3 ? T.cleanStone[i % 2] : T.cleanDirt[i % 3], x, y, bs + 0.4, bs + 0.4);
        this.midRims.push([x, top, bs]);
        if (i % 4 === 1 && x > 0) drawTree(g, T, r() > 0.45 ? "cherry" : "oak", x, top, bs * 0.62, this.midRims, r);
        if (i === 3) this.mobsMid.push({ kind: "enderman", x: x + bs * 2, y: top });
      }
      // A small island with one tree.
      const ix = w * 0.47;
      for (let k = 0; k < 4; k++) {
        g.drawImage(T.cleanGrass[k % 3], ix + k * bs, lake - bs * 0.6, bs + 0.4, bs + 0.4);
        this.midRims.push([ix + k * bs, lake - bs * 0.6, bs]);
      }
      drawTree(g, T, "azalea", ix + bs * 1.5, lake - bs * 0.6, bs * 0.8, this.midRims, r);
      // Right: a terracotta cliff with a waterfall pouring into the lake.
      const cx0 = w * 0.76;
      for (let x = cx0, i = 0; x < w + 60; x += bs, i++) {
        const top = Math.round((lake - bs * (3 + Math.min(3, i * 0.45) + Math.abs(Math.sin(i * 0.9)) * 1.2)) / bs) * bs;
        g.drawImage(T.cleanGrass[i % 3], x, top - bs, bs + 0.4, bs + 0.4);
        this.midRims.push([x, top - bs, bs]);
        for (let y = top, j = 0; y < h; y += bs, j++) g.drawImage(T.terracotta[(j + (i % 2)) % T.terracotta.length], x, y, bs + 0.4, bs + 0.4);
        if (i === 0) this.waterfall = { x: x + bs * 0.15, top: top + bs * 0.4, bottom: lake + bs * 0.6, w: bs * 0.9 };
        if (i % 3 === 2 && r() > 0.3) drawTree(g, T, "oak", x, top - bs, bs * 0.62, this.midRims, r);
      }
      g.fillStyle = "rgba(0,0,0,0.18)";
      g.fillRect(cx0, lake - bs * 6, bs * 0.4, bs * 7);
      return;
    }
    const r = rng(47 + this.sceneId.length * 13);
    const ridgeTop = { grass: T.grass[0], snowGrass: T.snowGrass[0], sand: T.sand[0], jungleGrass: T.jungleGrass[0], moss: T.mossCap[0], nylium: T.nylium[0], endstone: T.endstone[0] }[M.ridge] || T.grass[0];
    const fill = { sand: T.sandstone[0], nylium: T.netherrack[0], endstone: T.endstone[1], moss: T.stone[0] }[M.ridge] || T.dirt[0];
    const tops = [];
    for (let x = -60, i = 0; x < w + 60; x += bs, i++) {
      const n = Math.sin(i * 0.4) * 0.7 + Math.sin(i * 0.13 + 1) * 0.9;
      const top = base - Math.round(1 + n) * bs;
      tops.push([x, top]);
      g.drawImage(ridgeTop, x, top, bs + 0.4, bs + 0.4);
      for (let y = top + bs; y < h; y += bs) g.drawImage(fill, x, y, bs + 0.4, bs + 0.4);
      this.midRims.push([x, top, bs]);
    }
    let last = -999;
    for (const [x, top] of tops) {
      if (r() > M.density) continue;
      if (Math.abs(x - this.playerX) < bs * 3) continue;
      if (x - last < bs * 4) continue;
      last = x;
      const kind = M.trees[Math.floor(r() * M.trees.length)];
      const lights = drawTree(g, T, kind, x, top, bs, this.midRims, r);
      for (const [lx, ly, type] of lights) this.midLights.push([lx, ly, type]);
    }
  }

  /** Foreground terrain: crisp textured blocks. */
  buildFore() {
    const L = this.layerCanvas(1, 0);
    const { g } = L;
    const { BS, T, h, cols } = this;
    const D = this.def;
    this.foreRims = [];
    this.foreLights = [];
    const top = { grass: T.grass, snowGrass: T.snowGrass, sand: T.sand, jungleGrass: T.jungleGrass, mossCap: T.mossCap, nylium: T.nylium, endstone: T.endstone, cleanGrass: T.cleanGrass }[D.ground.top];
    const sub = { dirt: T.dirt, sandstone: T.sandstone, sand: T.sand, netherrack: T.netherrack, endstone: T.endstone, clay: T.clay, cleanDirt: T.cleanDirt }[D.ground.sub];
    const deep = { stone: T.stone, sandstone: T.sandstone, netherrack: T.netherrack, endstone: T.endstone, deep: T.deep, basalt: T.basalt, cleanStone: T.cleanStone }[D.ground.deep];
    for (let c = 0; c < cols; c++) {
      const x = this.colX(c);
      const t0 = this.topOf(c);
      const pond = c >= this.pondStart && c <= this.pondEnd;
      for (let y = t0, row = 0; y < h + BS; y += BS, row++) {
        const hsh = hash(c, row, 3);
        let t;
        if (pond) t = row < 2 ? (D.water === "lava" ? T.netherrack[0] : D.water === "ice" ? T.stone[0] : T.sand[0]) : deep[0];
        else if (row === 0) {
          t = (D.water === "sea" && this.off[c] >= 1 ? T.grass : top)[Math.floor(hsh * top.length) % 3];
          if (D.ground.top === "nylium" && hsh > 0.6) t = T.warpedNylium[0];
          if (D.fore.path && Math.abs(c - this.playerCol) <= 1) t = T.path[0];
        } else if (row < 5) t = sub[Math.floor(hsh * sub.length)];
        else t = deep[Math.floor(hsh * deep.length)];
        g.drawImage(t, x, y, BS + 0.4, BS + 0.4);
        const dd = clamp((y - this.groundY - BS * 0.5) / (h - this.groundY), 0, 1);
        if (dd > 0) {
          g.fillStyle = `rgba(14,8,20,${Math.min(0.72, dd * 0.8)})`;
          g.fillRect(x, y, BS + 0.4, BS + 0.4);
        }
      }
      for (const [nc, side] of [[c - 1, 0], [c + 1, 1]]) {
        const ntop = this.topOf(nc);
        if (ntop > t0) {
          const grd = g.createLinearGradient(side ? x + BS : x, 0, side ? x + BS - BS * 0.35 : x + BS * 0.35, 0);
          grd.addColorStop(0, "rgba(0,0,0,0.32)");
          grd.addColorStop(1, "rgba(0,0,0,0)");
          g.fillStyle = grd;
          g.fillRect(x, t0, BS, ntop - t0);
        }
      }
      if (D.snowCover && !pond) {
        g.fillStyle = "#ffffff";
        g.fillRect(x, t0 - BS * 0.12, BS + 0.4, BS * 0.14);
      }
      if (!pond) this.foreRims.push([x, t0, BS]);
    }
    const r = rng(91 + this.sceneId.length);
    // The main tree, with a bee nest in leafy scenes.
    const tc = this.treeCol;
    if (D.fore.tree && tc < cols - 1) {
      const x = this.colX(tc), base = this.topOf(tc);
      const lights = drawTree(g, T, D.fore.tree, x, base, BS, this.foreRims, r);
      for (const l of lights) this.foreLights.push(l);
      if (D.fore.nest) {
        const th = 4;
        const ny = base - th * BS;
        g.drawImage(T.nest, x - BS, ny, BS, BS);
        this.hive = { x: x - BS / 2, y: ny + BS / 2 };
      } else this.hive = null;
      if (D.fore.tree === "cactus") drawTree(g, T, "cactus", this.colX(tc + 3), this.topOf(tc + 3), BS, this.foreRims, r);
    } else this.hive = null;
    // A big framing tree at the left edge, standing on the ground.
    if (D.fore.frame) {
      const leaf = { oak: T.oakLeaf, cherry: T.cherryLeaf, jungle: T.jungleLeaf, birch: T.oakLeaf }[D.fore.frame];
      const logT = { oak: T.oakLog, cherry: T.cherryLog, jungle: T.jungleLog, birch: T.birch }[D.fore.frame];
      const col = 1;
      const lx = this.colX(col);
      const base = this.topOf(col);
      const th = D.fore.frame === "jungle" ? 7 : 5;
      for (let k = 1; k <= th; k++) g.drawImage(logT, lx, base - k * BS, BS + 0.4, BS + 0.4);
      const ct = base - th * BS - BS;
      const rows = [[-3, 2, 1], [-3, 3, 0], [-3, 3, -1], [-2, 2, -2]];
      for (const [a, b, dy] of rows) {
        for (let k = a; k <= b; k++) {
          if (dy === 1 && (k === a || k === b || r() > 0.6)) continue;
          if ((k === a || k === b) && dy === -2 && r() > 0.5) continue;
          const x = lx + k * BS, y = ct + dy * BS;
          g.drawImage(leaf, x, y, BS + 0.4, BS + 0.4);
          if (dy === -2 || k === b || (dy === -1 && k === a)) this.foreRims.push([x, y, BS]);
        }
      }
      g.fillStyle = "rgba(10,20,10,0.35)";
      g.fillRect(lx, base - th * BS, BS, BS * 0.6);
      this.frameX = lx;
    }
    this.fore = L;
  }

  /** Plants on top of the foreground (drawn every frame so they sway). */
  buildFlora() {
    const { T, cols, BS } = this;
    const kinds = this.def.flora.map((k) => (Array.isArray(T[k]) ? T[k] : [T[k]])).filter((k) => k[0]);
    this.flora = [];
    if (!kinds.length) return;
    for (let c = 0; c < cols; c++) {
      if (c >= this.pondStart - 1 && c <= this.pondEnd + 1) continue;
      if (c === this.punchCol || c === this.fireCol || c === this.treeCol || (this.def.fore.frame && c === 1)) continue;
      const near = Math.abs(c - this.playerCol) <= 1;
      const h = hash(c, 1, 41);
      if (near && h > 0.3) continue;
      if (h > 0.82) continue;
      const list = kinds[Math.floor(hash(c, 2, 41) * kinds.length)];
      const img = list[Math.floor(hash(c, 5, 41) * list.length)];
      const x = this.colX(c), top = this.topOf(c);
      this.flora.push({ img, x: x + BS * (0.05 + hash(c, 3, 41) * 0.4), y: top, size: BS * 0.8, phase: h * 6 });
      if (h < 0.35 && T.tufts) this.flora.push({ img: (this.def.flora.includes("roots") ? T.roots : this.def.flora.includes("ferns") ? T.ferns : T.tufts)[c % 2], x: x + BS * 0.45, y: top, size: BS * 0.7, phase: h * 9 });
    }
  }

  buildClouds() {
    const r = rng(5);
    const { w, h, BS } = this;
    this.clouds = [];
    const n = this.def.clouds || 0;
    for (let i = 0; i < n; i++) {
      const far = i < Math.ceil(n * 0.45);
      const bs = BS * (far ? 0.55 : 0.95);
      const blocks = [];
      const len = 3 + Math.floor(r() * 6);
      for (let k = 0; k < len; k++) {
        blocks.push([k, 0]);
        if (r() > 0.4 && k > 0 && k < len - 1) blocks.push([k, -1]);
      }
      if (len > 4) blocks.push([Math.floor(len / 2), -2]);
      this.clouds.push({ x: r() * (w + 400) - 200, y: h * (far ? 0.14 + r() * 0.1 : 0.06 + r() * 0.16), bs, blocks, speed: (far ? 3 : 7) * (0.6 + r() * 0.8), far });
    }
  }

  buildStars() {
    const r = rng(9);
    this.stars = [];
    for (let i = 0; i < 170; i++) this.stars.push({ x: r(), y: Math.pow(r(), 1.5) * 0.64, s: r() > 0.9 ? 2 : 1, tw: r() * 6.28 });
  }

  buildCritters() {
    const r = rng(17);
    const C = this.def.critters || {};
    this.bees = [];
    for (let i = 0; i < (C.bees || 0); i++) this.bees.push({ a: r() * 6.28, r: this.BS * (0.8 + r() * 1.4), sp: 0.7 + r() * 0.8, x: 0, y: 0, visible: false });
    this.butterflies = [];
    const colors = [[255, 180, 60], [140, 200, 255], [255, 255, 255], [240, 120, 200]];
    for (let i = 0; i < (C.butterflies || 0); i++) this.butterflies.push({ t: r() * 100, cx: this.playerX + (r() - 0.5) * this.BS * 8, cy: this.groundY - this.BS * (1 + r() * 1.5), col: colors[i % colors.length], x: 0, y: 0, visible: false });
    this.fireflies = [];
    if (C.fireflies) for (let i = 0; i < 18; i++) this.fireflies.push({ x: r() * this.w, y: this.groundY - r() * this.BS * 4, t: r() * 100 });
    this.bats = [];
    if (C.bats) for (let i = 0; i < 3; i++) this.bats.push({ t: r() * 100, cx: r() * this.w, cy: this.h * (0.2 + r() * 0.2) });
    this.birds = [];
    this.dragon = null;
  }

  // ------------------------------------------------------------- tinting --

  /** Re-tint the cached layers for the current light (a few times a second). */
  retint(p) {
    const key = `${this.sceneId}|` + [p.haze, p.tint, p.rim].map((c) => c.map((v) => v | 0).join(",")).join("|") + `|${p.tintA.toFixed(2)}|${p.rimA.toFixed(2)}`;
    if (key === this.tintKey && this.tinted) return;
    this.tintKey = key;
    const layers = [
      ["far", this.def.farHaze ?? 0.72, null],
      ["hills", 0.5, null],
      ["mid", 0.24, "midRims"],
      ["fore", 0, "foreRims"],
    ];
    this.tinted = this.tinted || {};
    for (const [name, haze, rims] of layers) {
      const L = this[name];
      let out = this.tinted[name];
      if (!out || out.width !== L.c.width || out.height !== L.c.height) {
        out = document.createElement("canvas");
        out.width = L.c.width;
        out.height = L.c.height;
        this.tinted[name] = out;
      }
      const g = out.getContext("2d");
      g.setTransform(1, 0, 0, 1, 0, 0);
      g.globalCompositeOperation = "source-over";
      g.clearRect(0, 0, out.width, out.height);
      g.drawImage(L.c, 0, 0);
      g.globalCompositeOperation = "source-atop";
      if (haze > 0) {
        const grd = g.createLinearGradient(0, 0, 0, out.height);
        grd.addColorStop(0, rgb(p.haze, haze * 0.85));
        grd.addColorStop(1, rgb(p.haze, Math.min(1, haze * 1.15)));
        g.fillStyle = grd;
        g.fillRect(0, 0, out.width, out.height);
      }
      const tintA = p.tintA * (1 - haze * 0.4);
      if (tintA > 0.005) {
        g.fillStyle = rgb(p.tint, tintA);
        g.fillRect(0, 0, out.width, out.height);
      }
      if (rims && p.rimA > 0.02) {
        const sc = this.dpr * L.res;
        for (const [x, y, bs] of this[rims]) {
          const X = (x + L.margin) * sc, Y = y * sc, W = bs * sc;
          const grd = g.createLinearGradient(0, Y, 0, Y + W * 0.3);
          grd.addColorStop(0, rgb(p.rim, p.rimA * (1 - haze)));
          grd.addColorStop(1, rgb(p.rim, 0));
          g.fillStyle = grd;
          g.fillRect(X, Y, W, W * 0.3);
        }
      }
      g.globalCompositeOperation = "source-over";
    }
    this.floraLit = new Map();
    for (const f of this.flora) {
      if (this.floraLit.has(f.img)) continue;
      const c = document.createElement("canvas");
      c.width = c.height = 16;
      const g = c.getContext("2d");
      g.drawImage(f.img, 0, 0);
      g.globalCompositeOperation = "source-atop";
      if (p.tintA > 0.005) { g.fillStyle = rgb(p.tint, p.tintA * 0.9); g.fillRect(0, 0, 16, 16); }
      if (p.rimA > 0.05) { g.fillStyle = rgb(p.rim, p.rimA * 0.25); g.fillRect(0, 0, 16, 4); }
      this.floraLit.set(f.img, c);
    }
  }

  // -------------------------------------------------------------- frames --

  emitPhase(force = false) {
    const p = this.phase();
    if (force || p !== this.lastPhase) {
      this.lastPhase = p;
      this.listeners.forEach((fn) => fn(p, this.hour()));
    }
  }

  loop(now) {
    const dt = Math.min(0.05, (now - this.last) / 1000);
    this.last = now;
    if (!document.hidden) {
      this.emitPhase();
      this.draw(now, this.animate ? dt : 0);
    }
    requestAnimationFrame((t) => this.loop(t));
  }

  /** A single finished frame (for thumbnails and the in-game menu). */
  drawStill() {
    this.life = true;
    const now = performance.now();
    // Let particles and critters settle into place first.
    for (let i = 0; i < 40; i++) this.draw(now + i * 50, 0.05);
    return this.canvas;
  }

  spawnChunks(x, y, n) {
    const D = this.def.ground.top;
    const top = { grass: [106, 170, 48], snowGrass: [240, 246, 255], sand: [220, 206, 160], jungleGrass: [72, 160, 44], mossCap: [90, 116, 46], nylium: [156, 32, 36], endstone: [222, 226, 168] }[D] || [106, 170, 48];
    const cols = [top, [134, 96, 67], [118, 84, 56], top];
    for (let i = 0; i < n; i++) {
      this.particles.push({ kind: "chunk", x, y, vx: (Math.random() - 0.5) * 160, vy: -80 - Math.random() * 160, life: 1.2, age: 0, size: this.texel * (2 + Math.random() * 2), col: cols[i % cols.length] });
    }
  }

  draw(now, dt) {
    const ctx = this.ctx;
    if (this.def.image) {
      this.drawPicture(now, dt);
      return;
    }
    const { w, h, BS } = this;
    const D = this.def;
    const t = now / 1000;
    const hour = this.hour();
    const p = this.palette();
    const life = this.life !== false;
    if (this.rise < 1) this.rise = Math.min(1, this.rise + dt / 2.8);
    const rise = 1 - Math.pow(1 - this.rise, 3);
    this.parallax += (this.parallaxTarget - this.parallax) * Math.min(1, dt * 3);
    if (now - this.tintAt > 350 || !this.tinted) {
      this.retint(p);
      this.tintAt = now;
    }

    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = "high";
    ctx.globalCompositeOperation = "source-over";
    ctx.globalAlpha = 1;

    // Sky.
    const sky = ctx.createLinearGradient(0, 0, 0, this.groundY);
    sky.addColorStop(0, rgb(p.top));
    sky.addColorStop(0.55, rgb(p.mid));
    sky.addColorStop(1, rgb(p.hor));
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, w, h);

    if (p.stars > 0.02) this.drawStars(t, p);
    const horizon = this.groundY - BS * 1.5;
    let light = null;
    if (D.sun) light = this.drawSunMoon(t, hour, p, horizon, rise);
    if (D.time && p.glowA > 0.05) light = { x: w * 0.5, y: h * 1.05, col: p.glow, a: p.glowA * 0.7 };

    if (life && D.critters?.birds && p.stars < 0.3 && dt > 0) this.moveBirds(dt);
    if (life) for (const b of this.birds) this.drawBird(b, t, p);
    this.drawClouds(dt, p);
    if (D.softClouds) this.drawSoftClouds(dt, p);
    if (D.critters?.dragon && life) this.drawDragon(t, dt);

    const lay = (name, depth) => {
      const L = this[name];
      const img = this.tinted[name];
      ctx.drawImage(img, -L.margin - this.parallax * depth, (1 - rise) * 30 * (1 - depth / 30), img.width / (this.dpr * L.res), img.height / (this.dpr * L.res));
    };
    lay("far", 4);
    this.drawFarExtras(t, p);
    const hz = ctx.createLinearGradient(0, horizon - BS * 3, 0, this.groundY);
    hz.addColorStop(0, rgb(p.haze, 0));
    hz.addColorStop(1, rgb(p.haze, 0.45));
    ctx.fillStyle = hz;
    ctx.fillRect(0, horizon - BS * 3, w, this.groundY - horizon + BS * 3);
    if (D.water === "sea") this.drawSea(t, p, horizon, light);
    lay("hills", 8);
    if (D.water === "lake") this.drawLake(t, p, light);
    lay("mid", 14);
    if (this.waterfall) this.drawWaterfall(t, dt, p);
    if (life) for (const m of this.mobsMid) this.drawEnderman(m, t, dt, p);
    ctx.imageSmoothingEnabled = false;
    lay("fore", 0);
    ctx.imageSmoothingEnabled = true;
    if (D.ceiling) this.drawCeiling(t, p);

    if (D.water === "pond" || D.water === "pool" || D.water === "ice") this.drawPond(t, dt, p);
    if (D.water === "lava") this.drawLava(t, dt, p);
    this.drawBroken(now, p);
    this.drawFire(t, dt, p);
    this.drawFlora(t);

    // Lights.
    const dark = this.darkness();
    ctx.globalCompositeOperation = "lighter";
    const fx = this.colX(this.fireCol) + BS / 2, fy = this.groundY - BS * 0.4;
    const flick = 0.9 + Math.sin(t * 13) * 0.05 + Math.sin(t * 7.3) * 0.05;
    const fireCol = D.fire === "soul" ? [80, 200, 255] : D.fire === "endrod" ? [240, 220, 255] : [255, 150, 60];
    this.glow(fx, fy, BS * (2.5 + dark * 3.5) * flick, fireCol, 0.18 + dark * 0.4);
    if (this.torch) this.glow(this.torch.x, this.torch.y, BS * 4.5 * flick, [255, 170, 80], 0.15 + dark * 0.45);
    for (const [lx, ly, type] of this.midLights) {
      const sx = lx - this.parallax * 14;
      this.glow(sx, ly, BS * (type === "window" ? 1.4 : 1.8), type === "window" ? [255, 200, 110] : [255, 170, 70], (type === "window" ? dark : 0.5) * 0.5);
    }
    for (const [lx, ly] of this.foreLights) this.glow(lx, ly, BS * 2.4, [255, 170, 70], 0.4);
    if (life && dark > 0.3) this.drawFireflies(dt, dark);
    ctx.globalCompositeOperation = "source-over";

    if (life) {
      this.drawBees(t, dt, p);
      this.drawButterflies(t, dt, p);
      this.drawBats(t, dt);
      if (D.critters?.creeper) this.drawCreeper(t, p);
    }
    this.drawParticles(dt, p, life);

    if (light && light.a > 0.05) {
      ctx.globalCompositeOperation = "screen";
      const rr = Math.max(w, h) * 0.9;
      const grd = ctx.createRadialGradient(light.x, light.y, 0, light.x, light.y, rr);
      grd.addColorStop(0, rgb(light.col, 0.32 * light.a));
      grd.addColorStop(0.4, rgb(light.col, 0.1 * light.a));
      grd.addColorStop(1, rgb(light.col, 0));
      ctx.fillStyle = grd;
      ctx.fillRect(0, 0, w, h);
      ctx.globalCompositeOperation = "source-over";
    }
    const vg = ctx.createRadialGradient(w / 2, h * 0.45, h * 0.35, w / 2, h * 0.5, Math.max(w, h) * 0.8);
    vg.addColorStop(0, "rgba(0,0,0,0)");
    vg.addColorStop(1, `rgba(6,4,16,${0.28 + dark * 0.25})`);
    ctx.fillStyle = vg;
    ctx.fillRect(0, 0, w, h);
  }

  drawStars(t, p) {
    const ctx = this.ctx;
    const { w, h } = this;
    for (const s of this.stars) {
      const a = p.stars * (0.55 + 0.45 * Math.sin(t * 1.7 + s.tw));
      ctx.fillStyle = `rgba(255,255,255,${a})`;
      ctx.fillRect(s.x * w, s.y * h, s.s, s.s);
    }
    if (p.stars > 0.5 && this.def.sun) {
      ctx.globalCompositeOperation = "lighter";
      for (let i = 0; i < 3; i++) {
        const y = h * (0.14 + i * 0.05) + Math.sin(t * 0.2 + i) * 10;
        const grd = ctx.createLinearGradient(0, y - 40, 0, y + 40);
        const col = i === 1 ? [140, 90, 220] : [60, 220, 160];
        grd.addColorStop(0, rgb(col, 0));
        grd.addColorStop(0.5, rgb(col, 0.06 * (p.stars - 0.5) * 2));
        grd.addColorStop(1, rgb(col, 0));
        ctx.fillStyle = grd;
        ctx.beginPath();
        ctx.moveTo(0, y);
        for (let x = 0; x <= w; x += 40) ctx.lineTo(x, y + Math.sin(x * 0.006 + t * 0.3 + i) * 26);
        ctx.lineTo(w, y + 60);
        ctx.lineTo(0, y + 60);
        ctx.fill();
      }
      ctx.globalCompositeOperation = "source-over";
    }
  }

  drawSunMoon(t, hour, p, horizon, rise) {
    const ctx = this.ctx;
    const { w, h, BS } = this;
    let light = null;
    const sunP = (hour - 5.6) / 13.2;
    if (sunP > -0.05 && sunP < 1.05) {
      const sx = lerp(w * 0.08, w * 0.92, sunP);
      const sy = horizon - Math.sin(clamp(sunP, 0, 1) * Math.PI) * (horizon - h * 0.12) + (1 - rise) * 90;
      light = { x: sx, y: sy, col: p.glow, a: p.glowA };
      ctx.globalCompositeOperation = "lighter";
      const r1 = h * 0.55;
      const g1 = ctx.createRadialGradient(sx, sy, 0, sx, sy, r1);
      g1.addColorStop(0, rgb(p.glow, 0.55 * p.glowA));
      g1.addColorStop(0.25, rgb(p.glow, 0.18 * p.glowA));
      g1.addColorStop(1, rgb(p.glow, 0));
      ctx.fillStyle = g1;
      ctx.fillRect(sx - r1, sy - r1, r1 * 2, r1 * 2);
      ctx.save();
      ctx.translate(sx, sy);
      ctx.rotate(t * 0.01);
      for (let i = 0; i < 12; i++) {
        ctx.rotate((Math.PI * 2) / 12);
        const len = h * (0.75 + 0.25 * Math.sin(t * 0.3 + i));
        const grd = ctx.createLinearGradient(0, 0, len, 0);
        grd.addColorStop(0, rgb(p.glow, 0.08 * p.glowA));
        grd.addColorStop(1, rgb(p.glow, 0));
        ctx.fillStyle = grd;
        ctx.beginPath();
        ctx.moveTo(0, 0);
        ctx.lineTo(len, -len * 0.05);
        ctx.lineTo(len, len * 0.05);
        ctx.fill();
      }
      ctx.restore();
      ctx.globalCompositeOperation = "source-over";
      const sr = BS * 0.8;
      const disc = ctx.createRadialGradient(sx, sy, 0, sx, sy, sr);
      disc.addColorStop(0, "rgba(255,255,250,1)");
      disc.addColorStop(0.55, rgb(mix([255, 252, 236], p.glow, 0.3), 1));
      disc.addColorStop(1, rgb(p.glow, 0));
      ctx.fillStyle = disc;
      ctx.beginPath();
      ctx.arc(sx, sy, sr, 0, Math.PI * 2);
      ctx.fill();
    }
    const moonP = ((hour + 24 - 18.6) % 24) / 11.6;
    if (moonP >= 0 && moonP <= 1) {
      const mx = lerp(w * 0.1, w * 0.9, moonP);
      const my = horizon - Math.sin(moonP * Math.PI) * (horizon - h * 0.14) + (1 - rise) * 90;
      if (!light) light = { x: mx, y: my, col: [170, 190, 255], a: 0.25 };
      const mr = BS * 0.55;
      ctx.globalCompositeOperation = "lighter";
      const g2 = ctx.createRadialGradient(mx, my, 0, mx, my, mr * 6);
      g2.addColorStop(0, "rgba(180,200,255,0.22)");
      g2.addColorStop(1, "rgba(180,200,255,0)");
      ctx.fillStyle = g2;
      ctx.fillRect(mx - mr * 6, my - mr * 6, mr * 12, mr * 12);
      ctx.globalCompositeOperation = "source-over";
      ctx.fillStyle = "#eef2ff";
      ctx.beginPath();
      ctx.arc(mx, my, mr, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = "rgba(160,170,200,0.55)";
      for (const [dx, dy, rr] of [[-0.3, -0.2, 0.18], [0.25, 0.15, 0.13], [-0.05, 0.38, 0.1]]) {
        ctx.beginPath();
        ctx.arc(mx + dx * mr, my + dy * mr, rr * mr, 0, Math.PI * 2);
        ctx.fill();
      }
      const ph = ((this.days % 8) + 8) % 8;
      if (ph !== 0) {
        ctx.fillStyle = rgb(p.top, 0.85);
        ctx.beginPath();
        ctx.arc(mx + (ph - 4) * mr * 0.5, my, mr * 1.02, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    return light;
  }

  /**
   * A picture background with depth: it drifts against the mouse, the sun
   * breathes with a soft bloom, light motes float past and birds cross.
   */
  drawPicture(now, dt) {
    const ctx = this.ctx;
    const { w, h } = this;
    const D = this.def;
    const t = now / 1000;
    const img = PICTURES[D.image];
    ctx.setTransform(this.dpr, 0, 0, this.dpr, 0, 0);
    ctx.globalCompositeOperation = "source-over";
    ctx.fillStyle = "#3a2050";
    ctx.fillRect(0, 0, w, h);
    if (!img || !img.complete || !img.naturalWidth) return;
    if (this.rise < 1) this.rise = Math.min(1, this.rise + dt / 2.2);
    const rise = 1 - Math.pow(1 - this.rise, 3);
    // The wallpaper stays still: it doesn't follow the mouse.
    this.parallax = 0;
    this.tilt = 0;
    const zoom = 1.08 + (1 - rise) * 0.06;
    const sc = Math.max(w / img.naturalWidth, h / img.naturalHeight) * zoom;
    const dw = img.naturalWidth * sc, dh = img.naturalHeight * sc;
    const ox = (w - dw) / 2 - this.parallax * w * 0.018, oy = (h - dh) / 2 - this.tilt * h * 0.02;
    ctx.imageSmoothingEnabled = true;
    ctx.imageSmoothingQuality = "high";
    ctx.drawImage(img, ox, oy, dw, dh);
    if (D.sunAt) this.drawSun(ctx, ox + dw * D.sunAt[0], oy + dh * D.sunAt[1], w, h, t, D);
    this.drawPictureLife(ctx, w, h, t, dt, D);
  }

  drawSun(ctx, sx, sy, w, h, t, D) {
    // Breathing sun bloom.
    ctx.globalCompositeOperation = "screen";
    const pulse = 0.85 + Math.sin(t * 0.8) * 0.08;
    const r1 = Math.max(w, h) * 0.55;
    const g1 = ctx.createRadialGradient(sx, sy, 0, sx, sy, r1);
    g1.addColorStop(0, rgb(D.glowCol, 0.32 * pulse));
    g1.addColorStop(0.25, rgb(D.glowCol, 0.1 * pulse));
    g1.addColorStop(1, rgb(D.glowCol, 0));
    ctx.fillStyle = g1;
    ctx.fillRect(0, 0, w, h);
    // Slow god rays.
    ctx.save();
    ctx.translate(sx, sy);
    ctx.rotate(t * 0.012);
    for (let i = 0; i < 14; i++) {
      ctx.rotate((Math.PI * 2) / 14);
      const len = h * (0.9 + 0.2 * Math.sin(t * 0.4 + i * 1.3));
      const grd = ctx.createLinearGradient(0, 0, len, 0);
      grd.addColorStop(0, rgb(D.glowCol, 0.06));
      grd.addColorStop(1, rgb(D.glowCol, 0));
      ctx.fillStyle = grd;
      ctx.beginPath();
      ctx.moveTo(0, 0);
      ctx.lineTo(len, -len * 0.04);
      ctx.lineTo(len, len * 0.04);
      ctx.fill();
    }
    ctx.restore();
    ctx.globalCompositeOperation = "source-over";
  }

  drawPictureLife(ctx, w, h, t, dt, D) {
    // Floating light motes at three depths.
    if (!this.motes) {
      const r = rng(77);
      this.motes = Array.from({ length: 46 }, () => ({ x: r() * w, y: r() * h, z: 0.4 + r() * 1.4, ph: r() * 6.28, sp: 6 + r() * 14 }));
    }
    ctx.globalCompositeOperation = "lighter";
    for (const m of this.motes) {
      if (dt > 0 && this.life !== false) {
        m.x += (m.sp * 0.6 * m.z) * dt;
        m.y -= m.sp * 0.25 * dt;
        if (m.x > w + 20) m.x = -20;
        if (m.y < -20) m.y = h + 20;
      }
      const a = (0.35 + 0.35 * Math.sin(t * 1.5 + m.ph)) * Math.min(1, m.z);
      const px = m.x - this.parallax * 14 * m.z, py = m.y + Math.sin(t + m.ph) * 6;
      this.glow(px, py, 3 + m.z * 5, D.dust, a * 0.5);
      ctx.fillStyle = rgb(D.dust, a);
      ctx.fillRect(px - m.z, py - m.z, m.z * 2, m.z * 2);
    }
    ctx.globalCompositeOperation = "source-over";
    if (this.life !== false && dt > 0) this.moveBirds(dt);
    const pal = { top: [60, 30, 70] };
    for (const b of this.birds) this.drawBird(b, t, pal);
    // Depth: vignette, darker at the bottom so the interface sits on it.
    const vg = ctx.createRadialGradient(w / 2, h * 0.42, h * 0.3, w / 2, h * 0.5, Math.max(w, h) * 0.78);
    vg.addColorStop(0, "rgba(0,0,0,0)");
    vg.addColorStop(1, `rgba(10,4,20,${(0.3 + (this.dim ?? 0.12)).toFixed(2)})`);
    ctx.fillStyle = vg;
    ctx.fillRect(0, 0, w, h);
    const bottom = ctx.createLinearGradient(0, h * 0.62, 0, h);
    bottom.addColorStop(0, "rgba(8,4,18,0)");
    bottom.addColorStop(1, "rgba(8,4,18,0.38)");
    ctx.fillStyle = bottom;
    ctx.fillRect(0, h * 0.62, w, h * 0.38);
  }

  /** Puffy, soft-shaded clouds (Vortex Valley). */
  drawSoftClouds(dt, p) {
    const ctx = this.ctx;
    const { w, h } = this;
    if (!this.soft) {
      const r = rng(303);
      this.soft = [];
      for (let i = 0; i < (this.def.softClouds || 0); i++) {
        const puffs = [];
        const n = 5 + Math.floor(r() * 5);
        for (let k = 0; k < n; k++) puffs.push([k * 0.22 + (r() - 0.5) * 0.1, -Math.sin((k / (n - 1)) * Math.PI) * (0.25 + r() * 0.2), 0.22 + r() * 0.2]);
        this.soft.push({ x: r() * w * 1.2 - w * 0.1, y: h * (0.06 + r() * 0.2), s: h * (0.12 + r() * 0.1), puffs, speed: 4 + r() * 6 });
      }
    }
    for (const c of this.soft) {
      if (dt > 0) c.x += c.speed * dt;
      if (c.x > w + c.s * 2) c.x = -c.s * 2.5;
      const px = c.x - this.parallax * 6;
      for (const [dx, dy, rr] of c.puffs) {
        const x = px + dx * c.s * 2, y = c.y + dy * c.s, rad = rr * c.s;
        const grd = ctx.createRadialGradient(x - rad * 0.3, y - rad * 0.4, rad * 0.1, x, y, rad);
        grd.addColorStop(0, rgb(mix(p.cloud, [255, 255, 255], 0.35), 0.95));
        grd.addColorStop(0.7, rgb(mix(p.cloud, p.cloudShade, 0.35), 0.85));
        grd.addColorStop(1, rgb(p.cloudShade, 0));
        ctx.fillStyle = grd;
        ctx.beginPath();
        ctx.arc(x, y, rad, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  }

  drawLake(t, p, light) {
    const ctx = this.ctx;
    const { w, BS } = this;
    const top = this.lakeTop(), bottom = this.groundY + BS * 0.5;
    const grd = ctx.createLinearGradient(0, top, 0, bottom);
    grd.addColorStop(0, rgb(mix(p.hor, [120, 180, 230], 0.55)));
    grd.addColorStop(0.5, rgb(mix(mix(p.mid, [50, 120, 190], 0.65), p.tint, p.tintA * 0.8)));
    grd.addColorStop(1, rgb(mix(mix(p.top, [26, 70, 130], 0.6), p.tint, p.tintA)));
    ctx.fillStyle = grd;
    ctx.fillRect(0, top, w, bottom - top);
    // Mountain reflections: a soft mirrored band.
    ctx.fillStyle = rgb(mix(p.haze, [255, 255, 255], 0.2), 0.18);
    ctx.fillRect(0, top + 2, w, BS * 0.5);
    if (light) {
      ctx.globalCompositeOperation = "lighter";
      for (let i = 0; i < 30; i++) {
        const k = i / 30;
        const y = top + (bottom - top) * k;
        const spread = 8 + k * BS * 2.5;
        ctx.fillStyle = rgb(light.col, (0.2 + 0.15 * Math.sin(t * 4 + i)) * light.a * (1 - k * 0.6));
        ctx.fillRect(light.x + Math.sin(t * 1.7 + i * 1.3) * spread - spread * 0.3, y, spread * 0.6, 2);
      }
      ctx.globalCompositeOperation = "source-over";
    }
    ctx.fillStyle = rgb(mix(p.hor, [255, 255, 255], 0.5), 0.3);
    for (let i = 0; i < 16; i++) {
      const y = top + 6 + ((i * 29) % (bottom - top - 8));
      const x = ((t * (8 + (i % 3) * 4) + i * 151) % (w + 200)) - 100;
      ctx.fillRect(x, y, BS * (0.6 + (i % 3) * 0.3), 2);
    }
  }

  drawWaterfall(t, dt, p) {
    const ctx = this.ctx;
    const f = this.waterfall;
    const x = f.x - this.parallax * 14;
    const grd = ctx.createLinearGradient(x, 0, x + f.w, 0);
    grd.addColorStop(0, rgb(this.lit([150, 200, 240], p), 0.85));
    grd.addColorStop(0.5, rgb(this.lit([225, 245, 255], p), 0.95));
    grd.addColorStop(1, rgb(this.lit([150, 200, 240], p), 0.85));
    ctx.fillStyle = grd;
    ctx.fillRect(x, f.top, f.w, f.bottom - f.top);
    ctx.fillStyle = "rgba(255,255,255,0.7)";
    for (let k = 0; k < 10; k++) {
      const y = f.top + ((t * 90 + k * 37) % (f.bottom - f.top));
      ctx.fillRect(x + f.w * (0.1 + (k % 3) * 0.3), y, f.w * 0.18, 8);
    }
    // Mist where it lands.
    for (let k = 0; k < 5; k++) {
      const mx = x + f.w / 2 + Math.sin(t * 1.3 + k) * f.w;
      const g2 = ctx.createRadialGradient(mx, f.bottom, 0, mx, f.bottom, f.w * 1.6);
      g2.addColorStop(0, "rgba(255,255,255,0.35)");
      g2.addColorStop(1, "rgba(255,255,255,0)");
      ctx.fillStyle = g2;
      ctx.fillRect(mx - f.w * 1.6, f.bottom - f.w * 1.6, f.w * 3.2, f.w * 3.2);
    }
  }

  /** A pixel sprite from rows of palette keys, drawn at `px` per pixel. */
  sprite(rows, pal, x, y, px) {
    const ctx = this.ctx;
    for (let j = 0; j < rows.length; j++) {
      for (let i = 0; i < rows[j].length; i++) {
        const c = pal[rows[j][i]];
        if (!c) continue;
        ctx.fillStyle = c;
        ctx.fillRect(x + i * px, y + j * px, px + 0.5, px + 0.5);
      }
    }
  }

  drawCreeper(t, p) {
    const c = this.treeCol + 3;
    if (c >= this.cols) return;
    const px = Math.max(2, Math.round(this.scale * 1.8));
    const base = this.topOf(c);
    const x = this.colX(c) + this.BS * 0.2;
    // Every few seconds it swells and flashes, then calms down.
    const cyc = t % 9;
    const flash = cyc > 7.6 && Math.floor(cyc * 8) % 2 === 0;
    const swell = cyc > 7.6 ? 1 + (cyc - 7.6) * 0.05 : 1;
    const g = (a) => rgb(this.lit(flash ? [245, 255, 245] : a, p));
    const pal = { a: g([88, 176, 72]), b: g([60, 140, 52]), c: g([128, 206, 104]), d: g([36, 96, 34]), k: flash ? "#d8d8d8" : "#141414" };
    const face = [
      "abcabaca", "bacbacab", "akkbckka", "akkcbkka", "cbakkacb", "abkkkkba", "bckkkkcb", "abkbckba",
    ];
    const body = ["abcaba", "bacabc", "cabacb", "abcbab", "bcabca", "acbabc", "babcab", "abcabc", "cbacba", "abcabc", "bacbab", "cabcab"];
    const legs = ["ab..ab", "ba..cb", "dd..dd", "dd..dd"];
    const ctx = this.ctx;
    ctx.save();
    ctx.translate(x + px * 4, base);
    ctx.scale(swell, swell);
    ctx.translate(-px * 4, 0);
    const bob = Math.sin(t * 2) * px * 0.15;
    this.sprite(legs, pal, px, -px * 4, px);
    this.sprite(body, pal, px, -px * 16 + bob, px);
    this.sprite(face, pal, 0, -px * 24 + bob, px);
    ctx.restore();
    ctx.fillStyle = "rgba(0,0,0,0.25)";
    ctx.fillRect(x - px, base - 2, px * 10, 3);
  }

  drawEnderman(m, t, dt, p) {
    const px = Math.max(1.5, this.scale * 0.9);
    // Now and then it teleports a little along the bank, in a puff of purple.
    m.tp = (m.tp ?? 6) - dt;
    if (m.tp <= 0) {
      m.tp = 7 + Math.random() * 6;
      for (let i = 0; i < 16; i++) this.particles.push({ kind: "glowdot", x: m.x - this.parallax * 14, y: m.y - px * 30, vx: (Math.random() - 0.5) * 60, vy: (Math.random() - 0.5) * 60, life: 1, age: 0, size: 2, col: [200, 90, 255] });
      m.offset = ((m.offset || 0) + (Math.random() > 0.5 ? 1 : -1) * px * 20 + px * 60) % (px * 120);
    }
    const x = m.x + (m.offset || 0) - this.parallax * 14, y = m.y;
    const ctx = this.ctx;
    const dark = rgb(this.lit([22, 16, 30], p)), dark2 = rgb(this.lit([34, 26, 44], p));
    ctx.fillStyle = dark;
    ctx.fillRect(x + px * 2, y - px * 30, px * 2, px * 30);
    ctx.fillRect(x + px * 5, y - px * 30, px * 2, px * 30);
    ctx.fillStyle = dark2;
    ctx.fillRect(x, y - px * 42, px * 9, px * 12);
    ctx.fillStyle = dark;
    ctx.fillRect(x - px * 2, y - px * 41, px * 2, px * 26);
    ctx.fillRect(x + px * 9, y - px * 41, px * 2, px * 26);
    ctx.fillRect(x + px * 0.5, y - px * 50, px * 8, px * 8);
    ctx.fillStyle = "#e07cff";
    ctx.fillRect(x + px * 1.5, y - px * 46, px * 2.5, px);
    ctx.fillRect(x + px * 5, y - px * 46, px * 2.5, px);
    ctx.globalCompositeOperation = "lighter";
    this.glow(x + px * 4.5, y - px * 46, px * 8, [200, 100, 255], 0.25);
    ctx.globalCompositeOperation = "source-over";
    // A grass block held in front.
    ctx.drawImage(this.T.cleanGrass[0], x - px * 3, y - px * 26, px * 7, px * 7);
    if (Math.random() < dt * 4) this.particles.push({ kind: "glowdot", x: x + Math.random() * px * 9, y: y - Math.random() * px * 50, vx: 0, vy: -10, life: 1.5, age: 0, size: 1.5, col: [190, 90, 255] });
  }

  drawClouds(dt, p) {
    const ctx = this.ctx;
    const { w } = this;
    for (const c of this.clouds) {
      if (dt > 0) c.x += c.speed * dt;
      const width = (Math.max(...c.blocks.map((b) => b[0])) + 1) * c.bs;
      if (c.x > w + 60) c.x = -width - 60;
      const px = c.x - this.parallax * (c.far ? 4 : 10);
      const alpha = c.far ? 0.55 : 0.88;
      for (const [bx, by] of c.blocks) {
        const x = px + bx * c.bs, y = c.y + by * c.bs * 0.6;
        const grd = ctx.createLinearGradient(0, y, 0, y + c.bs * 0.6);
        grd.addColorStop(0, rgb(mix(p.cloud, [255, 255, 255], 0.25), alpha));
        grd.addColorStop(1, rgb(p.cloudShade, alpha));
        ctx.fillStyle = grd;
        ctx.fillRect(x, y, c.bs + 0.5, c.bs * 0.6);
      }
    }
  }

  drawFarExtras(t, p) {
    const ctx = this.ctx;
    const px = -this.parallax * 4;
    // Nether lava falls.
    for (const f of this.lavaFalls) {
      const x = f.x + px;
      const grd = ctx.createLinearGradient(x, 0, x + f.w, 0);
      grd.addColorStop(0, "rgba(255,120,30,0.75)");
      grd.addColorStop(0.5, "rgba(255,200,80,0.95)");
      grd.addColorStop(1, "rgba(255,120,30,0.75)");
      ctx.fillStyle = grd;
      ctx.fillRect(x, f.top, f.w, f.bottom - f.top);
      ctx.fillStyle = "rgba(255,240,170,0.5)";
      for (let k = 0; k < 6; k++) {
        const y = f.top + (((t * 60 + k * 47) % (f.bottom - f.top)));
        ctx.fillRect(x + f.w * 0.2, y, f.w * 0.6, 4);
      }
      ctx.globalCompositeOperation = "lighter";
      this.glow(x + f.w / 2, f.bottom, this.BS * 3, [255, 120, 40], 0.35);
      ctx.globalCompositeOperation = "source-over";
    }
    // End crystals on the pillars.
    for (const c of this.crystals) {
      const x = c.x + px, y = c.y + Math.sin(t * 1.5 + c.ph) * 4;
      ctx.globalCompositeOperation = "lighter";
      this.glow(x, y, this.BS * 1.6, [230, 140, 255], 0.5);
      ctx.globalCompositeOperation = "source-over";
      ctx.save();
      ctx.translate(x, y);
      ctx.rotate(t * 1.2 + c.ph);
      ctx.fillStyle = "rgba(255,200,255,0.9)";
      ctx.fillRect(-4, -4, 8, 8);
      ctx.strokeStyle = "rgba(255,255,255,0.8)";
      ctx.strokeRect(-6, -6, 12, 12);
      ctx.restore();
    }
    // Cave glow lichen.
    for (const [lx, ly] of this.farLights) {
      ctx.globalCompositeOperation = "lighter";
      this.glow(lx + px, ly, this.BS * 1.2, [200, 255, 170], 0.18 + Math.sin(t + lx) * 0.05);
      ctx.globalCompositeOperation = "source-over";
    }
  }

  drawSea(t, p, horizon, light) {
    const ctx = this.ctx;
    const { w, BS } = this;
    const top = this.groundY - BS * 3.6;
    const bottom = this.groundY - BS * 0.2;
    const grd = ctx.createLinearGradient(0, top, 0, bottom);
    grd.addColorStop(0, rgb(mix(p.hor, [70, 140, 200], 0.5)));
    grd.addColorStop(1, rgb(mix(mix(p.mid, [20, 80, 150], 0.7), p.tint, p.tintA)));
    ctx.fillStyle = grd;
    ctx.fillRect(0, top, w, bottom - top);
    // Glitter under the sun or moon.
    if (light) {
      ctx.globalCompositeOperation = "lighter";
      for (let i = 0; i < 40; i++) {
        const k = i / 40;
        const y = top + (bottom - top) * k;
        const spread = 10 + k * BS * 3;
        const x = light.x + Math.sin(t * 2 + i * 1.7) * spread;
        ctx.fillStyle = rgb(light.col, (0.25 + 0.2 * Math.sin(t * 5 + i)) * light.a * (1 - k * 0.5));
        ctx.fillRect(x - spread * 0.3, y, spread * 0.6, 2);
      }
      ctx.globalCompositeOperation = "source-over";
    }
    // Waves.
    ctx.fillStyle = rgb(mix(p.hor, [255, 255, 255], 0.4), 0.35);
    for (let i = 0; i < 18; i++) {
      const y = top + ((i * 37) % (bottom - top));
      const x = ((t * (10 + (i % 4) * 5) + i * 131) % (w + 200)) - 100;
      ctx.fillRect(x, y, BS * (0.8 + (i % 3) * 0.4), 2);
    }
    // Foam at the shore.
    ctx.fillStyle = "rgba(255,255,255,0.7)";
    for (let x = 0; x < w; x += BS * 0.5) {
      const yy = bottom - 3 + Math.sin(t * 2 + x * 0.05) * 2;
      ctx.fillRect(x, yy, BS * 0.45, 3);
    }
    // A boat far out.
    const bx = ((t * 6) % (w + 200)) - 100;
    ctx.fillStyle = rgb(mix([90, 60, 30], p.haze, 0.5));
    ctx.fillRect(bx, top + BS * 0.6, BS * 1.2, BS * 0.25);
  }

  drawCeiling(t, p) {
    const ctx = this.ctx;
    const { w, h, BS } = this;
    const nether = this.def.ceiling === "nether";
    const r = rng(77);
    const base = h * 0.08;
    ctx.fillStyle = nether ? "#2a0a0a" : "#121a17";
    ctx.fillRect(0, 0, w, base);
    for (let x = 0; x < w; x += BS * 0.8) {
      const len = BS * (0.5 + r() * (nether ? 3 : 2.4));
      ctx.fillStyle = nether ? (r() > 0.5 ? "#3a0f0f" : "#4a1414") : r() > 0.5 ? "#1a2420" : "#222e28";
      ctx.fillRect(x, base - 2, BS * 0.8 + 0.5, len * (r() > 0.6 ? 1 : 0.4));
      if (nether && r() > 0.82) {
        const gx = x + BS * 0.4, gy = base + len * 0.4;
        ctx.fillStyle = "#ffd36b";
        ctx.fillRect(gx - BS * 0.3, gy - BS * 0.3, BS * 0.6, BS * 0.6);
        ctx.globalCompositeOperation = "lighter";
        this.glow(gx, gy, BS * 2, [255, 210, 120], 0.45);
        ctx.globalCompositeOperation = "source-over";
      }
      if (!nether && r() > 0.7) {
        // Glow berry vines.
        const vx = x + BS * 0.3, vlen = BS * (1 + r() * 3.5);
        ctx.fillStyle = "#3e6a2a";
        ctx.fillRect(vx, base, 3, vlen);
        for (let k = BS * 0.6; k < vlen; k += BS * 0.9) {
          const gy = base + k;
          ctx.fillStyle = "#ffb347";
          ctx.fillRect(vx - 3, gy, 8, 8);
          ctx.globalCompositeOperation = "lighter";
          this.glow(vx + 1, gy + 4, BS * 1.6, [255, 190, 90], 0.25 + Math.sin(t * 2 + k) * 0.05);
          ctx.globalCompositeOperation = "source-over";
        }
      }
    }
    if (!nether) {
      // Daylight through a crack in the cave roof.
      ctx.globalCompositeOperation = "lighter";
      const sx = w * 0.62;
      const grd = ctx.createLinearGradient(sx, 0, sx - w * 0.2, h);
      grd.addColorStop(0, "rgba(220,255,200,0.22)");
      grd.addColorStop(1, "rgba(220,255,200,0)");
      ctx.fillStyle = grd;
      ctx.beginPath();
      ctx.moveTo(sx - BS, 0);
      ctx.lineTo(sx + BS, 0);
      ctx.lineTo(sx - w * 0.08, h);
      ctx.lineTo(sx - w * 0.3, h);
      ctx.fill();
      ctx.globalCompositeOperation = "source-over";
    }
  }

  glow(x, y, r, col, a) {
    if (a <= 0.005) return;
    const ctx = this.ctx;
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, rgb(col, a));
    g.addColorStop(0.35, rgb(col, a * 0.4));
    g.addColorStop(1, rgb(col, 0));
    ctx.fillStyle = g;
    ctx.fillRect(x - r, y - r, r * 2, r * 2);
  }

  lit(col, p) { return mix(col, p.tint, p.tintA); }

  drawPond(t, dt, p) {
    const ctx = this.ctx;
    const { BS } = this;
    const x0 = this.colX(this.pondStart), x1 = this.colX(this.pondEnd + 1);
    const surface = this.groundY + BS * 0.18;
    const bottom = this.groundY + BS * 2;
    if (this.def.water === "ice") {
      ctx.drawImage(this.T.ice[0], x0, surface - BS * 0.1, x1 - x0, BS * 0.5);
      ctx.fillStyle = rgb(this.lit([90, 130, 190], p), 0.85);
      ctx.fillRect(x0, surface + BS * 0.4, x1 - x0, bottom - surface - BS * 0.4);
      ctx.fillStyle = "rgba(255,255,255,0.6)";
      ctx.fillRect(x0, surface - BS * 0.1, x1 - x0, 2);
      return;
    }
    const grd = ctx.createLinearGradient(0, surface, 0, bottom);
    const sky = mix(p.mid, [48, 128, 214], 0.8);
    grd.addColorStop(0, rgb(this.lit(mix(sky, [255, 255, 255], 0.12), p), 0.86));
    grd.addColorStop(1, rgb(this.lit([18, 52, 120], p), 0.94));
    ctx.fillStyle = grd;
    ctx.fillRect(x0, surface, x1 - x0, bottom - surface);
    ctx.fillStyle = rgb(mix(p.hor, [255, 255, 255], 0.5), 0.45);
    for (let i = 0; i < 6; i++) {
      const lx = x0 + (((t * 14 + i * 37) % (x1 - x0 + 40)) - 20);
      const ly = surface + 4 + (i % 3) * BS * 0.35;
      ctx.fillRect(clamp(lx, x0, x1 - 2), ly, Math.min(BS * 0.5, x1 - lx), 2);
    }
    ctx.fillStyle = rgb(mix(p.hor, [255, 255, 255], 0.6), 0.7);
    ctx.fillRect(x0, surface, x1 - x0, 2);
    ctx.fillStyle = rgb(this.lit([60, 140, 50], p));
    ctx.fillRect(x0 + (x1 - x0) * 0.7 + Math.sin(t * 0.5) * 3, surface - 1, BS * 0.45, 3);
    if (dt > 0 && this.life !== false) {
      if (!this.fish && Math.random() < dt * 0.12) this.fish = { x: x0 + (x1 - x0) * (0.2 + Math.random() * 0.5), t: 0, dir: Math.random() > 0.5 ? 1 : -1 };
      if (this.fish) {
        const f = this.fish;
        f.t += dt;
        const k = f.t / 0.9;
        if (k >= 1) { this.splash(f.x + f.dir * BS * 0.8, surface); this.fish = null; }
        else {
          const fx = f.x + f.dir * BS * 0.8 * k, fy = surface - Math.sin(k * Math.PI) * BS * 0.9;
          ctx.save();
          ctx.translate(fx, fy);
          ctx.rotate(f.dir * (k - 0.5) * 1.6);
          const tx = this.texel;
          ctx.fillStyle = rgb(this.lit([230, 140, 60], p));
          ctx.fillRect(-tx * 3, -tx, tx * 6, tx * 2);
          ctx.restore();
          if (f.t - dt <= 0.02) this.splash(f.x, surface);
        }
      }
    }
  }

  drawLava(t, dt, p) {
    const ctx = this.ctx;
    const { BS } = this;
    const x0 = this.colX(this.pondStart), x1 = this.colX(this.pondEnd + 1);
    const surface = this.groundY + BS * 0.18, bottom = this.groundY + BS * 2;
    const grd = ctx.createLinearGradient(0, surface, 0, bottom);
    grd.addColorStop(0, "#ffb02e");
    grd.addColorStop(1, "#d8461a");
    ctx.fillStyle = grd;
    ctx.fillRect(x0, surface, x1 - x0, bottom - surface);
    ctx.fillStyle = "rgba(255,240,150,0.7)";
    for (let i = 0; i < 8; i++) {
      const lx = x0 + (((t * 8 + i * 29) % (x1 - x0)));
      ctx.fillRect(lx, surface + (i % 4) * BS * 0.4 + 3, BS * 0.4, BS * 0.15);
    }
    ctx.globalCompositeOperation = "lighter";
    this.glow((x0 + x1) / 2, surface, (x1 - x0) * 0.9, [255, 120, 40], 0.4);
    ctx.globalCompositeOperation = "source-over";
    if (dt > 0 && Math.random() < dt * 4) this.particles.push({ kind: "ember", x: x0 + Math.random() * (x1 - x0), y: surface, vx: (Math.random() - 0.5) * 20, vy: -40 - Math.random() * 50, life: 1.5, age: 0, size: this.texel, col: [255, 190, 70] });
  }

  splash(x, y) {
    for (let i = 0; i < 8; i++) this.particles.push({ kind: "drop", x, y, vx: (Math.random() - 0.5) * 80, vy: -60 - Math.random() * 90, life: 0.7, age: 0, size: 2 + Math.random() * 2, col: [200, 230, 255] });
  }

  drawBroken(now, p) {
    const b = this.broken;
    if (!b) return;
    const ctx = this.ctx;
    const { BS } = this;
    const x = this.colX(b.col), y = this.topOf(b.col);
    if (b.until && now < b.until) {
      ctx.fillStyle = rgb(this.lit([62, 42, 28], p));
      ctx.fillRect(x, y, BS, BS);
      ctx.fillStyle = "rgba(0,0,0,0.35)";
      ctx.fillRect(x, y, BS, BS * 0.25);
      return;
    }
    if (b.until && now >= b.until) {
      b.grown = b.grown || now;
      const k = Math.min(1, (now - b.grown) / 260);
      if (k < 1) { ctx.fillStyle = rgb([255, 255, 255], (1 - k) * 0.35); ctx.fillRect(x, y, BS, BS); return; }
      this.broken = null;
      return;
    }
    ctx.strokeStyle = "rgba(20,12,6,0.75)";
    ctx.lineWidth = Math.max(1, this.texel);
    ctx.beginPath();
    const r = rng(b.col * 7);
    for (let i = 0; i < b.crack * 2; i++) {
      let cx = x + BS / 2, cy = y + BS / 2;
      ctx.moveTo(cx, cy);
      for (let k = 0; k < 3; k++) {
        cx += (r() - 0.5) * BS * 0.5;
        cy += (r() - 0.5) * BS * 0.5;
        ctx.lineTo(clamp(cx, x, x + BS), clamp(cy, y, y + BS));
      }
    }
    ctx.stroke();
  }

  drawFire(t, dt, p) {
    const ctx = this.ctx;
    const { BS } = this;
    const tx = this.texel;
    const x = this.colX(this.fireCol), base = this.topOf(this.fireCol);
    const kind = this.def.fire;
    if (kind === "endrod") {
      ctx.fillStyle = "#f4ecff";
      ctx.fillRect(x + BS * 0.44, base - BS * 1.1, BS * 0.12, BS * 1.1);
      ctx.fillStyle = "#c8b8e0";
      ctx.fillRect(x + BS * 0.35, base - BS * 0.12, BS * 0.3, BS * 0.12);
      ctx.globalCompositeOperation = "lighter";
      this.glow(x + BS / 2, base - BS * 0.6, BS * 2, [240, 220, 255], 0.35);
      ctx.globalCompositeOperation = "source-over";
      if (dt > 0 && Math.random() < dt * 2) this.particles.push({ kind: "ember", x: x + BS / 2, y: base - BS, vx: (Math.random() - 0.5) * 10, vy: -12, life: 2, age: 0, size: tx, col: [255, 255, 255] });
      return;
    }
    ctx.fillStyle = rgb(this.lit([92, 66, 38], p));
    ctx.fillRect(x + tx, base - tx * 4, BS - tx * 2, tx * 3);
    ctx.fillStyle = rgb(this.lit([70, 50, 28], p));
    ctx.fillRect(x + tx * 3, base - tx * 6, BS - tx * 6, tx * 2);
    const soul = kind === "soul";
    ctx.fillStyle = soul ? "#7fe6ff" : "#ffb347";
    ctx.fillRect(x + tx * 5, base - tx * 4, tx * 6, tx);
    ctx.globalCompositeOperation = "lighter";
    for (let i = 0; i < 4; i++) {
      const fx = x + BS * (0.3 + i * 0.13);
      const hgt = BS * (0.7 + 0.3 * Math.sin(t * 9 + i * 1.7) + 0.15 * Math.sin(t * 17 + i));
      const grd = ctx.createLinearGradient(0, base - tx * 5 - hgt, 0, base - tx * 4);
      if (soul) {
        grd.addColorStop(0, "rgba(40,160,255,0)");
        grd.addColorStop(0.4, "rgba(60,200,255,0.75)");
        grd.addColorStop(1, "rgba(200,250,255,0.95)");
      } else {
        grd.addColorStop(0, "rgba(255,90,20,0)");
        grd.addColorStop(0.4, "rgba(255,140,40,0.75)");
        grd.addColorStop(1, "rgba(255,230,120,0.95)");
      }
      ctx.fillStyle = grd;
      ctx.beginPath();
      const wv = Math.sin(t * 6 + i) * tx * 1.5;
      ctx.moveTo(fx - tx * 2.2, base - tx * 4);
      ctx.quadraticCurveTo(fx - tx * 2 + wv, base - tx * 4 - hgt * 0.6, fx + wv, base - tx * 4 - hgt);
      ctx.quadraticCurveTo(fx + tx * 2 + wv, base - tx * 4 - hgt * 0.6, fx + tx * 2.2, base - tx * 4);
      ctx.fill();
    }
    ctx.globalCompositeOperation = "source-over";
    if (dt > 0 && this.life !== false) {
      if (Math.random() < dt * 5) this.particles.push({ kind: "smoke", x: x + BS / 2 + (Math.random() - 0.5) * tx * 4, y: base - BS * 0.9, vx: 6 + Math.random() * 8, vy: -24 - Math.random() * 12, life: 3.5, age: 0, size: tx * 2.5, col: [190, 190, 200] });
      if (Math.random() < dt * 3) this.particles.push({ kind: "ember", x: x + BS / 2, y: base - BS * 0.6, vx: (Math.random() - 0.5) * 30, vy: -50 - Math.random() * 40, life: 1.4, age: 0, size: tx * 0.9, col: soul ? [120, 220, 255] : [255, 180, 60] });
    }
  }

  drawFlora(t) {
    const ctx = this.ctx;
    ctx.imageSmoothingEnabled = false;
    for (const f of this.flora) {
      const sway = this.animate ? Math.sin(t * 1.6 + f.phase) * 0.12 : 0;
      ctx.save();
      ctx.translate(f.x + f.size / 2, f.y);
      ctx.transform(1, 0, sway, 1, 0, 0);
      ctx.drawImage(this.floraLit?.get(f.img) || f.img, -f.size / 2, -f.size, f.size, f.size);
      ctx.restore();
    }
    ctx.imageSmoothingEnabled = true;
  }

  drawFireflies(dt, dark) {
    const ctx = this.ctx;
    for (const f of this.fireflies) {
      f.t += dt;
      const fxp = f.x + Math.sin(f.t * 0.7) * 30, fyp = f.y + Math.sin(f.t * 1.1) * 14;
      const a = (0.5 + 0.5 * Math.sin(f.t * 3)) * (dark - 0.3) * 1.4;
      this.glow(fxp, fyp, this.BS * 0.5, [200, 255, 120], a * 0.5);
      ctx.fillStyle = rgb([230, 255, 160], a);
      ctx.fillRect(fxp - 1.5, fyp - 1.5, 3, 3);
    }
  }

  moveBirds(dt) {
    if (this.birds.length < 3 && Math.random() < dt * 0.05) {
      const dir = Math.random() > 0.5 ? 1 : -1;
      const y = this.h * (0.12 + Math.random() * 0.2);
      for (let i = 0; i < 3; i++) this.birds.push({ x: dir > 0 ? -40 - i * 26 : this.w + 40 + i * 26, y: y + i * 9, dir, ph: Math.random() * 6 });
    }
    for (const b of this.birds) b.x += b.dir * 46 * dt;
    this.birds = this.birds.filter((b) => b.x > -120 && b.x < this.w + 120);
  }

  drawBird(b, t, p) {
    const ctx = this.ctx;
    const flap = Math.sin(t * 9 + b.ph) * 4;
    ctx.strokeStyle = rgb(mix([40, 40, 60], p.top, 0.3), 0.75);
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(b.x - 7, b.y - flap);
    ctx.lineTo(b.x, b.y);
    ctx.lineTo(b.x + 7, b.y - flap);
    ctx.stroke();
  }

  drawDragon(t, dt) {
    const ctx = this.ctx;
    const { w, h } = this;
    if (!this.dragon && Math.random() < dt * 0.05) this.dragon = { x: -200, y: h * (0.18 + Math.random() * 0.15), dir: 1 };
    const d = this.dragon;
    if (!d) return;
    d.x += 70 * dt;
    if (d.x > w + 200) { this.dragon = null; return; }
    const flap = Math.sin(t * 3) * 18;
    ctx.fillStyle = "rgba(10,6,18,0.85)";
    ctx.beginPath();
    ctx.moveTo(d.x - 60, d.y);
    ctx.lineTo(d.x + 50, d.y - 4);
    ctx.lineTo(d.x + 66, d.y + 2);
    ctx.lineTo(d.x + 50, d.y + 6);
    ctx.lineTo(d.x - 60, d.y + 6);
    ctx.fill();
    ctx.beginPath();
    ctx.moveTo(d.x - 10, d.y);
    ctx.lineTo(d.x - 40, d.y - 40 - flap);
    ctx.lineTo(d.x + 20, d.y);
    ctx.fill();
    ctx.fillStyle = "rgba(220,120,255,0.9)";
    ctx.fillRect(d.x + 58, d.y - 1, 4, 3);
  }

  drawBees(t, dt, p) {
    const ctx = this.ctx;
    const hive = this.hive;
    const night = this.darkness() > 0.6;
    const tx = this.texel;
    for (const b of this.bees) {
      b.visible = !!hive && !night;
      if (!b.visible) continue;
      b.a += dt * b.sp;
      b.x = hive.x + Math.cos(b.a) * b.r + Math.sin(b.a * 2.3) * 8;
      b.y = hive.y + Math.sin(b.a * 1.3) * b.r * 0.5 + 10;
      const dir = Math.cos(b.a) < 0 ? 1 : -1;
      ctx.fillStyle = rgb(this.lit([250, 200, 60], p));
      ctx.fillRect(b.x - tx * 2, b.y - tx * 1.5, tx * 4, tx * 3);
      ctx.fillStyle = rgb(this.lit([40, 30, 20], p));
      ctx.fillRect(b.x - tx * 0.5, b.y - tx * 1.5, tx, tx * 3);
      ctx.fillRect(b.x + dir * tx * 1.5 - tx * 0.5, b.y - tx * 1.5, tx, tx * 3);
      ctx.fillStyle = "rgba(230,240,255,0.75)";
      ctx.fillRect(b.x - tx, b.y - tx * 3 + (Math.sin(t * 40) > 0 ? -tx : 0), tx * 2, tx * 1.5);
    }
  }

  drawButterflies(t, dt, p) {
    const ctx = this.ctx;
    const day = this.darkness() < 0.4;
    const tx = this.texel;
    for (const f of this.butterflies) {
      f.visible = day;
      if (!day) continue;
      f.t += dt;
      f.x = f.cx + Math.sin(f.t * 0.45) * this.BS * 2.2 + Math.sin(f.t * 1.3) * 10;
      f.y = f.cy + Math.sin(f.t * 0.8) * this.BS * 0.6;
      const flap = Math.abs(Math.sin(f.t * 12));
      ctx.fillStyle = rgb(this.lit(f.col, p));
      ctx.fillRect(f.x - tx * 2.5 * flap - tx, f.y - tx * 1.5, tx * 2.5 * flap + tx * 0.5, tx * 2.5);
      ctx.fillRect(f.x + tx * 0.5, f.y - tx * 1.5, tx * 2.5 * flap + tx * 0.5, tx * 2.5);
      ctx.fillStyle = "rgb(40,30,30)";
      ctx.fillRect(f.x - tx * 0.5, f.y - tx * 1.5, tx, tx * 3);
    }
  }

  drawBats(t, dt) {
    const ctx = this.ctx;
    const tx = this.texel;
    for (const b of this.bats) {
      b.t += dt;
      const x = b.cx + Math.sin(b.t * 0.6) * this.w * 0.15, y = b.cy + Math.sin(b.t * 1.7) * 30;
      const flap = Math.sin(b.t * 18) > 0 ? -tx * 2 : tx;
      ctx.fillStyle = "#2a2018";
      ctx.fillRect(x - tx, y - tx, tx * 2, tx * 2);
      ctx.fillRect(x - tx * 4, y + flap, tx * 3, tx);
      ctx.fillRect(x + tx, y + flap, tx * 3, tx);
    }
  }

  drawParticles(dt, p, life) {
    const ctx = this.ctx;
    const kind = this.def.particles;
    const { w, h, BS } = this;
    if (life && dt > 0) {
      if (kind === "leaves" && Math.random() < dt * 0.6 && this.def.fore.tree) {
        const x = this.colX(this.treeCol) + (Math.random() - 0.5) * BS * 4;
        this.particles.push({ kind: "leaf", x, y: this.topOf(this.treeCol) - BS * 5, vx: -10, vy: 16 + Math.random() * 10, life: 7, age: 0, size: this.texel * 2, col: this.def.leaf, ph: Math.random() * 6 });
      }
      if (kind === "petals" && Math.random() < dt * 4) {
        this.particles.push({ kind: "leaf", x: Math.random() * w * 1.1, y: -10, vx: -14, vy: 22 + Math.random() * 14, life: 14, age: 0, size: this.texel * 1.6, col: Math.random() > 0.5 ? [250, 190, 214] : [255, 220, 234], ph: Math.random() * 6 });
      }
      if (kind === "snow" && Math.random() < dt * 14) {
        this.particles.push({ kind: "snow", x: Math.random() * w * 1.2, y: -10, vx: -8, vy: 24 + Math.random() * 20, life: 16, age: 0, size: 1.5 + Math.random() * 2.5, col: [255, 255, 255], ph: Math.random() * 6 });
      }
      if (kind === "dust" && Math.random() < dt * 3) {
        this.particles.push({ kind: "snow", x: w + 10, y: this.groundY - Math.random() * BS * 4, vx: -60 - Math.random() * 40, vy: (Math.random() - 0.5) * 6, life: 12, age: 0, size: 1.5 + Math.random() * 1.5, col: [236, 214, 160], ph: Math.random() * 6 });
      }
      if (kind === "ash" && Math.random() < dt * 10) {
        this.particles.push({ kind: "snow", x: Math.random() * w, y: Math.random() * h * 0.8, vx: (Math.random() - 0.5) * 10, vy: 6 + Math.random() * 8, life: 8, age: 0, size: 1.5 + Math.random() * 2, col: Math.random() > 0.7 ? [255, 160, 80] : [200, 190, 190], ph: Math.random() * 6 });
      }
      if (kind === "portal" && Math.random() < dt * 8) {
        this.particles.push({ kind: "glowdot", x: Math.random() * w, y: this.groundY - Math.random() * BS, vx: (Math.random() - 0.5) * 8, vy: -20 - Math.random() * 20, life: 4, age: 0, size: 2 + Math.random() * 2, col: [200, 120, 255] });
      }
      if (kind === "spores" && Math.random() < dt * 6) {
        this.particles.push({ kind: "glowdot", x: Math.random() * w, y: h * 0.1 + Math.random() * h * 0.5, vx: (Math.random() - 0.5) * 6, vy: 6 + Math.random() * 6, life: 9, age: 0, size: 1.5 + Math.random() * 2, col: [180, 255, 140] });
      }
    }
    const out = [];
    for (const q of this.particles) {
      q.age += dt;
      if (q.age >= q.life) continue;
      const k = q.age / q.life;
      if (q.kind === "chunk" || q.kind === "drop") q.vy += 420 * dt;
      if (q.kind === "leaf") q.vx = -12 + Math.sin(q.age * 2 + q.ph) * 24;
      if (q.kind === "snow") q.x += Math.sin(q.age * 1.5 + q.ph) * 0.4;
      q.x += q.vx * dt;
      q.y += q.vy * dt;
      if (q.kind === "chunk" && q.y > this.groundY + 2) { q.y = this.groundY + 2; q.vy *= -0.3; q.vx *= 0.6; }
      if ((q.kind === "snow" || q.kind === "leaf") && q.y > this.groundY + BS * 0.2) continue;
      if (q.kind === "smoke") {
        const s = q.size * (1 + k * 3);
        ctx.fillStyle = rgb(mix(q.col, p.hor, 0.3), 0.32 * (1 - k));
        ctx.beginPath();
        ctx.arc(q.x, q.y, s, 0, Math.PI * 2);
        ctx.fill();
      } else if (q.kind === "ember") {
        ctx.fillStyle = rgb(q.col, 1 - k);
        ctx.fillRect(q.x, q.y, q.size * 1.5, q.size * 1.5);
      } else if (q.kind === "glowdot") {
        const a = Math.sin(k * Math.PI);
        ctx.globalCompositeOperation = "lighter";
        this.glow(q.x, q.y, q.size * 4, q.col, 0.35 * a);
        ctx.globalCompositeOperation = "source-over";
        ctx.fillStyle = rgb(q.col, a);
        ctx.fillRect(q.x - q.size / 2, q.y - q.size / 2, q.size, q.size);
      } else if (q.kind === "snow") {
        ctx.fillStyle = rgb(q.col, 0.85 * Math.min(1, (1 - k) * 4));
        ctx.fillRect(q.x, q.y, q.size, q.size);
      } else {
        ctx.fillStyle = rgb(this.lit(q.col, p), q.kind === "drop" ? 0.8 * (1 - k) : 1);
        ctx.fillRect(q.x - q.size / 2, q.y - q.size / 2, q.size, q.size);
      }
      out.push(q);
    }
    this.particles = out.length > 500 ? out.slice(-500) : out;
  }
}

const PICTURES = {};

/** Use your own image as the wallpaper (an Image), or null for none. */
export function setCustomPicture(img) {
  if (img) PICTURES.custom = img;
  else delete PICTURES.custom;
}
for (const [id, file] of [["golden", "meadow-sunset.jpg"], ["sunset", "sunset.jpg"], ["valley", "valley.webp"]]) {
  PICTURES[id] = new Image();
  PICTURES[id].src = new URL(`../img/${file}`, import.meta.url).href;
}

// Textures are shared by every world (live, thumbnails, exports).
let shared = null;
function TEXTURES() {
  shared ||= makeTextures();
  return shared;
}

const thumbs = new Map();

/** A small picture of a scene (data URL), cached. */
export function sceneThumb(id, w = 176, h = 99) {
  const key = `${id}@${w}x${h}`;
  if (thumbs.has(key)) return thumbs.get(key);
  const c = document.createElement("canvas");
  // Render at a launcher-like size, then shrink, so it looks the same.
  const world = new World(c, { offscreen: true, width: 960, height: 540, scene: id, hour: 7.2, dpr: 1 });
  world.drawStill();
  const small = document.createElement("canvas");
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  small.width = w * dpr;
  small.height = h * dpr;
  const g = small.getContext("2d");
  g.imageSmoothingQuality = "high";
  g.drawImage(c, 0, 0, small.width, small.height);
  const url = small.toDataURL("image/jpeg", 0.86);
  thumbs.set(key, url);
  return url;
}

/** A full-size still of a scene (for the in-game main menu). */
export function sceneStill(id, w, h, hour) {
  const c = document.createElement("canvas");
  const world = new World(c, { offscreen: true, width: w, height: h, scene: id, hour, dpr: 1 });
  return world.drawStill();
}
