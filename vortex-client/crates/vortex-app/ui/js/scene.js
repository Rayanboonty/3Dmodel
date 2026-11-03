// The Vortex world: a pixel-art landscape with a full day cycle, drawn at
// low resolution and scaled up with crisp pixels.
//
// Static geometry (terrain blocks, trees, hive) is rendered once per size into
// an offscreen canvas; sky, light, water, flames, critters and particles are
// animated every frame on top. Time of day tints the land; at night the
// campfire, the player's torch and fireflies light it.

export const H = 270; // logical pixel rows
const B = 6; // block size in logical pixels

// ---------------------------------------------------------------- palette --

const NIGHT = { top: "#090a24", mid: "#16174a", hor: "#2c2a68", far: "#2a2a5c", farSnow: "#5a5f96", hill: "#1d1c46", cloud: "#2e2d63", shade: "#232257", tint: [12, 16, 50], tintA: 0.64, stars: 1 };
const DAWN1 = { top: "#272466", mid: "#a1588f", hor: "#ffb07a", far: "#a876b2", farSnow: "#ffd8e6", hill: "#6f4d8f", cloud: "#ffd3dd", shade: "#e898b6", tint: [255, 120, 120], tintA: 0.2, stars: 0.3 };
const DAWN2 = { top: "#4a64c0", mid: "#f3a68e", hor: "#ffe3ab", far: "#b597c7", farSnow: "#fff1ec", hill: "#7a66a6", cloud: "#fff3ea", shade: "#f4c4cc", tint: [255, 170, 130], tintA: 0.12, stars: 0 };
const DAY = { top: "#3b8be2", mid: "#7cc0f5", hor: "#d4f1ff", far: "#90b9dc", farSnow: "#ffffff", hill: "#5f9a8a", cloud: "#ffffff", shade: "#d3e5f7", tint: [255, 255, 255], tintA: 0, stars: 0 };
const DUSK1 = { top: "#382e7a", mid: "#de5f7e", hor: "#ffa65e", far: "#a65f80", farSnow: "#ffd2c0", hill: "#6a3c6a", cloud: "#ffc3aa", shade: "#d87b8c", tint: [255, 110, 80], tintA: 0.22, stars: 0.1 };
const DUSK2 = { top: "#141340", mid: "#3c2b6e", hor: "#7d416c", far: "#3e2e60", farSnow: "#7b6c9c", hill: "#28214e", cloud: "#4a3b72", shade: "#3a2d5e", tint: [40, 24, 70], tintA: 0.5, stars: 0.8 };
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
const mix = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];
const rgb = (c, a = 1) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a})`;

function palette(hour) {
  for (let i = 0; i < KEYS.length - 1; i++) {
    const [h0, p0] = KEYS[i];
    const [h1, p1] = KEYS[i + 1];
    if (hour >= h0 && hour <= h1) {
      const t = h1 === h0 ? 0 : (hour - h0) / (h1 - h0);
      const s = t * t * (3 - 2 * t);
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
  return palette(0);
}

function rng(seed) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

const hash = (x, y) => {
  let h = (x * 374761393 + y * 668265263) ^ 0x5bd1e995;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
};

const BAYER = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]];

// Block textures (day colours; the hour tints them later).
const C = {
  grass: [[108, 189, 69], [92, 170, 56], [126, 204, 82], [80, 152, 48]],
  dirt: [[134, 92, 58], [116, 78, 48], [150, 106, 68], [102, 68, 42]],
  stone: [[118, 118, 125], [109, 109, 117], [127, 127, 134], [101, 101, 109]],
  sand: [[219, 207, 160], [204, 190, 142], [232, 222, 178]],
  log: [[104, 78, 46], [86, 63, 37], [122, 92, 56]],
  birch: [[226, 224, 214], [204, 202, 190], [52, 50, 46]],
  leaves: [[72, 142, 50], [58, 120, 40], [92, 164, 62], [46, 102, 34]],
  birchLeaves: [[112, 160, 66], [96, 142, 54], [132, 178, 80]],
  coal: [[40, 40, 44]],
  iron: [[214, 170, 140]],
};

export class World {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.land = document.createElement("canvas");
    this.lctx = this.land.getContext("2d");
    this.mode = "cycle";
    this.dayLength = 10 * 60; // seconds per in-game day
    this.cycleHour = 6.2;
    this.cycleRef = performance.now();
    this.days = 0;
    this.animate = true;
    this.parallax = 0;
    this.parallaxTarget = 0;
    this.rise = 0;
    this.last = performance.now();
    this.acc = 0;
    this.listeners = new Set();
    this.lastPhase = null;
    this.particles = [];
    this.broken = null; // { col, crack, until }
    this.torch = null;
    this.fish = null;
    this.skyKey = "";
    this.resize();
    window.addEventListener("resize", () => this.resize());
    window.addEventListener("mousemove", (e) => {
      this.parallaxTarget = (e.clientX / window.innerWidth - 0.5) * 2;
    });
    requestAnimationFrame((t) => this.loop(t));
  }

  // ------------------------------------------------------------- public --

  onPhase(fn) { this.listeners.add(fn); }

  setMode(mode) {
    const h = this.hour();
    this.mode = mode || "cycle";
    if (this.mode === "cycle") { this.cycleHour = h; this.cycleRef = performance.now(); }
    this.skyKey = "";
    this.emitPhase(true);
  }

  setDayLength(minutes) {
    const h = this.hour();
    this.dayLength = Math.max(1, Number(minutes) || 10) * 60;
    this.cycleHour = h;
    this.cycleRef = performance.now();
  }

  setAnimate(on) { this.animate = on; }

  hour() {
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

  phase() { return phaseOf(this.hour()); }

  timeLabel() {
    const h = this.hour();
    const hh = Math.floor(h);
    const mm = Math.floor((h - hh) * 60);
    return `${String(hh).padStart(2, "0")}:${String(mm).padStart(2, "0")}`;
  }

  /** Darkness 0 (noon) … 1 (midnight). */
  darkness() {
    return Math.min(1, palette(this.hour()).tintA / 0.64);
  }

  get scale() { return window.innerHeight / H; }

  /** Where the player's feet go, in screen pixels. */
  anchor() {
    return { x: this.playerX * this.scale, y: this.groundY * this.scale, scale: this.scale };
  }

  /** Logical x of named spots, relative to the player's home (screen px). */
  spot(name) {
    const col = { punch: this.punchCol, fire: this.fireCol, pond: this.pondEnd }[name];
    return ((col * B + B / 2) - this.playerX) * this.scale;
  }

  /** Visible critters near the player, in screen pixels. */
  critters() {
    const s = this.scale;
    return [...(this.bees || []), ...(this.butterflies || [])]
      .filter((c) => c.visible)
      .map((c) => ({ x: c.x * s, y: c.y * s }));
  }

  setTorch(screenX, screenY, on) {
    this.torch = on ? { x: screenX / this.scale, y: screenY / this.scale } : null;
  }

  /** One hit on the block in front of the player; it breaks after a few. */
  hitBlock() {
    const now = performance.now();
    if (this.broken && this.broken.until > now) return;
    if (!this.broken || this.broken.until) this.broken = { col: this.punchCol, crack: 0, until: 0 };
    this.broken.crack += 1;
    const x = this.punchCol * B + B / 2;
    this.spawn(x, this.groundY + 1, 3, [C.grass[0], C.dirt[0]], 22);
    if (this.broken.crack >= 4) {
      this.broken.until = now + 7000;
      this.spawn(x, this.groundY + 2, 14, [C.grass[0], C.grass[2], C.dirt[0], C.dirt[1]], 40);
      return true;
    }
    return false;
  }

  // --------------------------------------------------------- generation --

  resize() {
    const aspect = Math.max(1, window.innerWidth / Math.max(1, window.innerHeight));
    this.W = Math.round(H * aspect);
    this.canvas.width = this.W;
    this.canvas.height = H;
    this.land.width = this.W;
    this.land.height = H;
    this.ctx.imageSmoothingEnabled = false;
    this.lctx.imageSmoothingEnabled = false;
    this.build();
    this.skyKey = "";
  }

  build() {
    const W = this.W;
    const cols = Math.ceil(W / B) + 1;
    this.cols = cols;
    this.groundY = 33 * B; // 198: the plateau the player stands on
    this.playerCol = Math.round((W * 0.4) / B);
    this.playerX = this.playerCol * B + B / 2;
    const pc = this.playerCol;
    this.punchCol = pc - 3;
    this.fireCol = pc + 4;
    this.pondStart = pc - 15;
    this.pondEnd = pc - 8;

    // Column heights, in blocks above the plateau.
    const off = new Array(cols).fill(0);
    for (let c = 0; c < cols; c++) {
      const d = c - pc;
      const n = Math.sin(c * 0.33) * 1.2 + Math.sin(c * 0.11 + 2) * 1.6 + Math.sin(c * 0.71 + 1) * 0.5;
      if (Math.abs(d) <= 6) off[c] = 0;
      else if (c >= this.pondStart && c <= this.pondEnd) off[c] = -3;
      else if (d === -7) off[c] = 0;
      else if (d < -15) off[c] = Math.max(1, Math.round(1.5 + n + (-d - 16) * 0.12));
      else if (d <= 9) off[c] = 1;
      else off[c] = Math.max(1, Math.min(6, Math.round(2.2 + n + (d - 10) * 0.08)));
    }
    this.off = off;
    this.topOf = (c) => this.groundY - (off[Math.max(0, Math.min(cols - 1, c))] || 0) * B;

    // Terrain image.
    const img = this.lctx.createImageData(W, H);
    const put = (x, y, c, a = 255) => {
      if (x < 0 || y < 0 || x >= W || y >= H) return;
      const i = (y * W + x) * 4;
      img.data[i] = c[0]; img.data[i + 1] = c[1]; img.data[i + 2] = c[2]; img.data[i + 3] = a;
    };
    // Mostly the base colour, with a few darker/lighter texels: reads as a
    // block texture rather than noise.
    const pick = (list, x, y) => {
      const h = hash(x, y);
      return h < 0.62 ? list[0] : list[1 + Math.floor(((h - 0.62) / 0.38) * (list.length - 1))];
    };
    const wetSand = C.sand.map((c) => mix(c, [70, 96, 120], 0.45));
    for (let c = 0; c < cols; c++) {
      const top = this.topOf(c);
      const pond = c >= this.pondStart && c <= this.pondEnd;
      for (let y = top; y < H; y++) {
        const depth = Math.floor((y - top) / B);
        for (let x = c * B; x < c * B + B; x++) {
          const lx = x - c * B, ly = (y - top) % B;
          let col;
          if (pond) col = depth < 2 ? pick(wetSand, x, y) : pick(C.stone, Math.floor(x / 2), Math.floor(y / 2));
          else if (depth === 0) {
            if (ly < 2) col = pick(C.grass, x, y);
            else if (ly === 2 && hash(x, 7) > 0.55) col = C.grass[3];
            else col = pick(C.dirt, x, y);
          } else if (depth < 7) col = pick(C.dirt, x, y);
          else {
            col = pick(C.stone, Math.floor(x / 2), Math.floor(y / 2));
            const ore = hash(Math.floor(x / 3), Math.floor(y / 3));
            if (ore > 0.985) col = C.coal[0];
            else if (ore > 0.98) col = C.iron[0];
          }
          // Deeper ground falls into shadow.
          const deep = Math.max(0, Math.min(1, (y - (this.groundY + 2 * B)) / (H - this.groundY - 2 * B)));
          if (deep > 0) col = mix(col, [24, 18, 30], deep * 0.55);
          // Block edge shading for the blocky look.
          if (ly === B - 1 || lx === B - 1) col = mix(col, [0, 0, 0], 0.1);
          if (ly === 0 && depth > 0) col = mix(col, [255, 255, 255], 0.06);
          put(x, y, col);
        }
      }
      // Exposed side faces where the neighbour is lower: darker column edge.
      for (const [nc, edgeX] of [[c - 1, c * B], [c + 1, c * B + B - 1]]) {
        const ntop = this.topOf(nc);
        if (ntop > top) {
          for (let y = top; y < Math.min(ntop, H); y++) {
            const i = (y * W + edgeX) * 4;
            img.data[i] = img.data[i] * 0.7; img.data[i + 1] = img.data[i + 1] * 0.7; img.data[i + 2] = img.data[i + 2] * 0.7;
          }
        }
      }
    }

    // Trees (static), placed on columns relative to the player.
    this.trees = [];
    this.hive = null;
    const tree = (dc, kind, h) => {
      const c = pc + dc;
      if (c < 1 || c >= cols - 1) return;
      const base = this.topOf(c);
      const tx = c * B;
      const trunk = kind === "birch" ? C.birch : C.log;
      for (let y = base - h * B; y < base; y++) {
        for (let x = tx + 1; x < tx + B - 1; x++) {
          let col = trunk[Math.floor(hash(x, y) * 2)];
          if (kind === "birch" && hash(x * 3, y) > 0.82) col = C.birch[2];
          if (kind !== "birch" && (x - tx) % 2 === 0 && hash(x, y * 5) > 0.6) col = C.log[1];
          put(x, y, col);
        }
      }
      const leaves = kind === "birch" ? C.birchLeaves : C.leaves;
      const cw = kind === "birch" ? 4 : 5;
      const top = base - h * B - 2 * B;
      const cx = tx + B / 2;
      const rows = [[cw, 0], [cw, 1], [cw - 2, 2]];
      const canopy = [];
      for (const [wBlocks, r] of rows) {
        const y0 = top + (2 - r) * B - B;
        const x0 = Math.round(cx - (wBlocks * B) / 2);
        for (let y = y0; y < y0 + B; y++) {
          for (let x = x0; x < x0 + wBlocks * B; x++) {
            const edge = (x === x0 || x === x0 + wBlocks * B - 1) && hash(x, y) > 0.5;
            if (edge) continue;
            let col = pick(leaves, x, y);
            if (hash(x * 7, y * 3) > 0.9) col = leaves[leaves.length - 1];
            put(x, y, col);
          }
        }
        canopy.push({ x0, y0, w: wBlocks * B });
      }
      // Top tuft.
      for (let y = top - B - 3; y < top - B; y++) for (let x = cx - 4; x < cx + 4; x++) put(x, y, pick(leaves, x, y));
      this.trees.push({ x: cx, canopyTop: top - B - 3, canopyBottom: top + B, w: cw * B, kind });
      if (kind === "oak-hive") {
        // Bee nest hanging under the canopy.
        const hx = tx + B, hy = top + B + 1;
        for (let y = hy; y < hy + 5; y++) for (let x = hx; x < hx + 5; x++) {
          const stripe = (y - hy) % 2 === 1;
          put(x, y, stripe ? [196, 140, 40] : [236, 190, 74]);
        }
        put(hx + 2, hy + 3, [60, 40, 20]);
        this.hive = { x: hx + 2.5, y: hy + 2.5 };
      }
    };
    tree(11, "oak-hive", 4);
    tree(17, "birch", 5);
    tree(-21, "oak", 4);
    tree(-27, "birch", 4);
    tree(26, "oak", 5);
    tree(33, "oak", 4);
    if (!this.hive) this.hive = { x: this.playerX + 60, y: this.groundY - 40 };

    // Campfire logs (flames are animated).
    const fx = this.fireCol * B;
    const fy = this.groundY;
    for (let x = fx - 1; x < fx + B + 1; x++) { put(x, fy - 1, C.log[0]); put(x, fy - 2, C.log[2]); }
    put(fx, fy - 3, [60, 60, 60]); put(fx + B - 1, fy - 3, [60, 60, 60]);
    this.fire = { x: fx + B / 2, y: fy - 2 };
    this.lctx.putImageData(img, 0, 0);
    this.terrain = document.createElement("canvas");
    this.terrain.width = W;
    this.terrain.height = H;
    this.terrain.getContext("2d").drawImage(this.land, 0, 0);

    // Flowers and grass tufts on grass tops.
    const r = rng(42);
    this.flowers = [];
    const kinds = ["poppy", "dandelion", "cornflower", "allium", "tulip"];
    for (let c = 2; c < cols - 2; c++) {
      const d = c - pc;
      if ((c >= this.pondStart - 1 && c <= this.pondEnd + 1) || Math.abs(d) <= 2 || c === this.fireCol || c === this.punchCol) continue;
      const top = this.topOf(c);
      if (r() < 0.32) this.flowers.push({ x: c * B + 1 + Math.floor(r() * 4), y: top, kind: kinds[Math.floor(r() * kinds.length)], ph: r() * 6 });
      if (r() < 0.55) this.flowers.push({ x: c * B + Math.floor(r() * 5), y: top, kind: "grass", h: 2 + Math.floor(r() * 3), ph: r() * 6 });
    }

    // Far mountains and mid hills.
    const ridge = (base, amp, freq, seed, step) => {
      const rr = rng(seed);
      const ph = [rr() * 9, rr() * 9, rr() * 9];
      return Array.from({ length: W + 80 }, (_, i) => {
        const x = i - 40;
        const v = Math.abs(Math.sin(x * freq + ph[0])) * 0.65 + Math.sin(x * freq * 2.7 + ph[1]) * 0.25 + Math.sin(x * freq * 6.1 + ph[2]) * 0.1;
        return Math.round((base - v * amp) / step) * step;
      });
    };
    this.mountains = ridge(H * 0.6, 62, 0.012, 11, 2);
    this.hills = ridge(H * 0.68, 22, 0.024, 23, 2);
    const tr = rng(91);
    this.hillTrees = Array.from({ length: Math.round(W / 9) }, () => ({ x: Math.floor(tr() * (W + 60)) - 30, s: 3 + Math.floor(tr() * 4), kind: tr() > 0.35 ? "spruce" : "round" }));

    // Stars, clouds, critters.
    const sr = rng(1337);
    this.stars = Array.from({ length: Math.round(W * 0.5) }, () => ({ x: Math.floor(sr() * W), y: Math.floor(sr() * H * 0.62), tw: sr() * 6.28, sp: 0.5 + sr() * 2.4, big: sr() > 0.93 }));
    const cr = rng(7);
    this.clouds = Array.from({ length: Math.max(6, Math.round(W / 60)) }, (_, i) => {
      const w = 6 + Math.floor(cr() * 10), h = 2 + Math.floor(cr() * 2);
      const cells = [];
      for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
        const edge = (x === 0 || x === w - 1) && (y === 0 || y === h - 1);
        if (!edge && cr() > 0.12) cells.push([x, y]);
      }
      return { x: cr() * W, y: 14 + cr() * H * 0.34, cells, w, h, speed: 1.2 + cr() * 2.4, layer: i % 2 };
    });
    this.bees = Array.from({ length: 4 }, (_, i) => ({ ph: i * 1.7, r: 10 + i * 4, cx: 0, cy: 0, x: 0, y: 0, visible: false }));
    this.butterflies = Array.from({ length: 2 }, (_, i) => ({ ph: i * 3, x: this.playerX - 40 + i * 70, y: this.groundY - 20, visible: false, color: i ? [255, 240, 120] : [140, 200, 255] }));
    this.birds = Array.from({ length: 5 }, (_, i) => ({ x: -20 - i * 40, y: 30 + i * 8, speed: 9 + i * 2, ph: i }));
    this.flies = Array.from({ length: 22 }, () => ({ x: (this.pondStart - 6 + sr() * 30) * B, y: this.groundY - 4 - sr() * 30, ph: sr() * 6, sp: 0.5 + sr() }));
    this.leafSpawn = 0;
  }

  // ------------------------------------------------------------ effects --

  spawn(x, y, n, colors, speed) {
    for (let i = 0; i < n; i++) {
      const a = -Math.PI / 2 + (Math.random() - 0.5) * 2.2;
      const sp = speed * (0.4 + Math.random() * 0.6);
      this.particles.push({ x, y, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp, g: 90, life: 0.6 + Math.random() * 0.6, color: colors[i % colors.length], size: 1 });
    }
  }

  emitPhase(force = false) {
    const p = this.phase();
    if (force || p !== this.lastPhase) {
      this.lastPhase = p;
      this.listeners.forEach((fn) => fn(p, this.hour()));
    }
  }

  // ---------------------------------------------------------------- draw --

  loop(now) {
    const dt = Math.min(0.1, (now - this.last) / 1000);
    this.last = now;
    if (!document.hidden) {
      this.acc += dt;
      const step = this.animate ? 1 / 30 : 1;
      if (this.acc >= step) {
        this.draw(this.animate ? this.acc : 0);
        this.acc = 0;
      }
    }
    if (Math.floor(now / 1000) !== this.lastSecond) {
      this.lastSecond = Math.floor(now / 1000);
      this.emitPhase();
    }
    requestAnimationFrame((t) => this.loop(t));
  }

  skyImage(p, hour) {
    const key = `${this.W}:${hour.toFixed(2)}`;
    if (key === this.skyKey && this.skyCache) return this.skyCache;
    const W = this.W;
    const img = this.ctx.createImageData(W, H);
    const bands = 28;
    for (let y = 0; y < H; y++) {
      const t = Math.min(1, y / (H * 0.74));
      const q = t * bands;
      const lo = Math.floor(q);
      const frac = q - lo;
      for (let x = 0; x < W; x++) {
        const th = (BAYER[y & 3][x & 3] + 0.5) / 16;
        const band = Math.min(bands, lo + (frac > th ? 1 : 0)) / bands;
        const c = band < 0.58 ? mix(p.top, p.mid, band / 0.58) : mix(p.mid, p.hor, (band - 0.58) / 0.42);
        const i = (y * W + x) * 4;
        img.data[i] = c[0]; img.data[i + 1] = c[1]; img.data[i + 2] = c[2]; img.data[i + 3] = 255;
      }
    }
    const off = this.skyCache || document.createElement("canvas");
    off.width = W;
    off.height = H;
    off.getContext("2d").putImageData(img, 0, 0);
    this.skyCache = off;
    this.skyKey = key;
    return off;
  }

  disc(ctx, cx, cy, r, color) {
    ctx.fillStyle = color;
    for (let y = -r; y <= r; y++) {
      const w = Math.floor(Math.sqrt(r * r - y * y));
      ctx.fillRect(Math.round(cx - w), Math.round(cy + y), w * 2 + 1, 1);
    }
  }

  draw(dt) {
    const { ctx, W } = this;
    const hour = this.hour();
    const p = palette(hour);
    const t = performance.now() / 1000;
    const dark = Math.min(1, p.tintA / 0.64);
    this.parallax += (this.parallaxTarget - this.parallax) * Math.min(1, dt * 2.5);
    if (this.rise < 1) this.rise = Math.min(1, this.rise + dt / 2.8);
    const rise = 1 - Math.pow(1 - this.rise, 3);
    const horizon = H * 0.62;
    const px = this.parallax;

    ctx.globalCompositeOperation = "source-over";
    ctx.drawImage(this.skyImage(p, hour), 0, 0);

    // Aurora (night).
    if (p.stars > 0.6) {
      const a = (p.stars - 0.6) / 0.4;
      for (let x = 0; x < W; x += 1) {
        const y = 34 + Math.sin(x * 0.025 + t * 0.25) * 9 + Math.sin(x * 0.07 - t * 0.4) * 4;
        const h = 14 + Math.sin(x * 0.05 + t * 0.3) * 6;
        ctx.fillStyle = `rgba(90,255,190,${0.05 * a})`;
        ctx.fillRect(x, Math.round(y), 1, Math.round(h));
        ctx.fillStyle = `rgba(150,120,255,${0.035 * a})`;
        ctx.fillRect(x, Math.round(y - 6), 1, 6);
      }
    }

    // Stars and shooting stars.
    if (p.stars > 0.02) {
      for (const s of this.stars) {
        const a = p.stars * (0.45 + 0.55 * Math.sin(t * s.sp + s.tw));
        if (a <= 0.05) continue;
        ctx.fillStyle = `rgba(255,248,230,${a})`;
        ctx.fillRect(s.x, s.y, 1, 1);
        if (s.big && a > 0.7) {
          ctx.fillStyle = `rgba(255,248,230,${a * 0.4})`;
          ctx.fillRect(s.x - 1, s.y, 3, 1);
          ctx.fillRect(s.x, s.y - 1, 1, 3);
        }
      }
      if (this.animate && p.stars > 0.7) {
        if (!this.shooting && Math.random() < dt * 0.1) this.shooting = { x: W * (0.3 + Math.random() * 0.7), y: Math.random() * H * 0.25, life: 0 };
        if (this.shooting) {
          const s = this.shooting;
          s.life += dt; s.x -= dt * 130; s.y += dt * 48;
          for (let i = 0; i < 9; i++) {
            ctx.fillStyle = `rgba(255,250,235,${(1 - i / 9) * (1 - s.life)})`;
            ctx.fillRect(Math.round(s.x + i * 2.6), Math.round(s.y - i), 2, 1);
          }
          if (s.life > 1) this.shooting = null;
        }
      }
    }

    // Sun and moon on arcs.
    const sunP = (hour - 5.4) / 14.2;
    if (sunP > -0.06 && sunP < 1.06) {
      const sx = W * (0.88 - 0.76 * sunP) + px * 1.5;
      const lift = Math.sin(Math.max(0, Math.min(1, sunP)) * Math.PI);
      const sy = horizon - 4 - lift * (horizon - 28) + (1 - rise) * 70;
      const low = 1 - lift;
      const r = Math.round(10 + low * 5);
      const core = mix([255, 252, 230], [255, 186, 104], low * 0.85);
      const glow = mix([255, 242, 196], [255, 140, 90], low);
      for (let i = 5; i >= 1; i--) this.disc(ctx, sx, sy, r + i * 5, rgb(glow, (0.05 + 0.03 * (6 - i)) * (0.55 + low * 0.7)));
      if (low > 0.3) {
        ctx.save();
        ctx.translate(Math.round(sx), Math.round(sy));
        ctx.rotate(t * 0.03);
        ctx.fillStyle = rgb(glow, 0.09 * low);
        for (let i = 0; i < 14; i++) {
          ctx.rotate((Math.PI * 2) / 14);
          ctx.fillRect(r + 8, -1, 40 + 10 * Math.sin(t * 0.8 + i), 2);
        }
        ctx.restore();
      }
      this.disc(ctx, sx, sy, r, rgb(core));
      this.disc(ctx, sx - r * 0.3, sy - r * 0.3, Math.max(2, Math.round(r * 0.35)), "rgba(255,255,255,.35)");
    }
    const moonP = ((hour + 24 - 19.4) % 24) / 10.6;
    if (moonP >= 0 && moonP <= 1 && p.stars > 0.1) {
      const mx = W * (0.84 - 0.62 * moonP) + px * 1.5;
      const my = horizon - 12 - Math.sin(moonP * Math.PI) * (horizon - 40) + (1 - rise) * 70;
      this.disc(ctx, mx, my, 16, `rgba(200,210,255,${0.07 * p.stars})`);
      this.disc(ctx, mx, my, 8, `rgba(238,238,252,${p.stars})`);
      const phase = (this.days % 8) / 8;
      if (phase > 0) {
        const shift = Math.round((phase < 0.5 ? phase : phase - 1) * 32);
        ctx.save();
        ctx.beginPath();
        ctx.rect(mx - 9, my - 9, 18, 18);
        ctx.clip();
        this.disc(ctx, mx + shift, my, 8, rgb(p.top, 0.85 * p.stars));
        ctx.restore();
      }
      ctx.fillStyle = `rgba(190,192,218,${p.stars})`;
      ctx.fillRect(Math.round(mx - 3), Math.round(my - 2), 2, 2);
      ctx.fillRect(Math.round(mx + 2), Math.round(my + 2), 3, 2);
    }

    // Far clouds.
    const cloud = (c, layer) => {
      if (this.animate) c.x += c.speed * dt * (layer ? 1.5 : 0.8);
      const cw = c.w * 5;
      if (c.x > W + 40) c.x = -cw - 40;
      const cx = Math.round(c.x + px * (layer ? 5 : 2));
      const cy = Math.round(c.y);
      const cs = layer ? 5 : 4, ch = layer ? 3 : 2;
      for (const [x, y] of c.cells) {
        ctx.fillStyle = y === c.h - 1 ? rgb(p.shade, layer ? 0.95 : 0.7) : rgb(p.cloud, layer ? 0.95 : 0.7);
        ctx.fillRect(cx + x * cs, cy + y * ch, cs, ch);
      }
    };
    this.clouds.filter((c) => !c.layer).forEach((c) => cloud(c, 0));

    // Mountains with snow, then hills with trees, both hazed toward the horizon.
    const mt = mix(p.far, p.hor, 0.25);
    const snow = p.farSnow;
    const mo = Math.round(px * 2);
    for (let x = 0; x < W; x++) {
      const y = this.mountains[x + 40 + mo] ?? this.mountains[x + 40];
      ctx.fillStyle = rgb(mt);
      ctx.fillRect(x, y, 1, H - y);
      if (y < H * 0.48) {
        ctx.fillStyle = rgb(snow, 0.9);
        ctx.fillRect(x, y, 1, Math.max(1, Math.round((H * 0.48 - y) * 0.35)));
      }
    }
    ctx.fillStyle = rgb(p.hor, 0.18);
    ctx.fillRect(0, H * 0.5, W, H);
    this.clouds.filter((c) => c.layer).forEach((c) => cloud(c, 1));

    const ho = Math.round(px * 5);
    const hillCol = mix(p.hill, p.hor, 0.12);
    ctx.fillStyle = rgb(hillCol);
    for (let x = 0; x < W; x++) {
      const y = this.hills[x + 40 + ho] ?? this.hills[x + 40];
      ctx.fillRect(x, y, 1, H - y);
    }
    ctx.fillStyle = rgb(mix(hillCol, [10, 10, 30], 0.22));
    for (const tr of this.hillTrees) {
      const x = tr.x + ho;
      if (x < -10 || x > W + 10) continue;
      const base = this.hills[Math.max(0, Math.min(this.hills.length - 1, x + 40))];
      if (tr.kind === "spruce") {
        for (let row = 0; row < tr.s * 2; row++) {
          const half = Math.floor((row + 1) / 2.3);
          ctx.fillRect(x - half, base - tr.s * 2 + row, half * 2 + 1, 1);
        }
      } else {
        ctx.fillRect(x - tr.s + 1, base - tr.s * 2, tr.s * 2 - 1, tr.s + 1);
        ctx.fillRect(x - tr.s, base - tr.s * 2 + 1, tr.s * 2 + 1, tr.s - 1);
      }
      ctx.fillRect(x, base - 1, 1, 2);
    }
    ctx.fillStyle = rgb(p.hor, 0.1);
    ctx.fillRect(0, H * 0.62, W, H);

    // Birds.
    if (p.stars < 0.3 && this.animate) {
      ctx.fillStyle = rgb(mix(p.hill, [20, 16, 30], 0.5));
      for (const b of this.birds) {
        b.x += b.speed * dt;
        if (b.x > W + 30) { b.x = -30 - Math.random() * 90; b.y = 24 + Math.random() * 40; }
        const flap = Math.floor(t * 5 + b.ph) % 2;
        const bx = Math.round(b.x), by = Math.round(b.y + Math.sin(t * 2 + b.ph) * 2);
        ctx.fillRect(bx, by, 1, 1);
        ctx.fillRect(bx - 1, by - 1 + flap, 1, 1);
        ctx.fillRect(bx + 1, by - 1 + flap, 1, 1);
        ctx.fillRect(bx - 2, by - 1 + flap * 2, 1, 1);
        ctx.fillRect(bx + 2, by - 1 + flap * 2, 1, 1);
      }
    }

    // ----- Foreground land (drawn on its own canvas, then tinted). -----
    const l = this.lctx;
    l.globalCompositeOperation = "source-over";
    l.clearRect(0, 0, W, H);
    l.drawImage(this.terrain, 0, 0);

    // Pond water with sky reflection and shimmer.
    const wx0 = this.pondStart * B, wx1 = (this.pondEnd + 1) * B;
    const wy = this.groundY + B;
    const water = mix([46, 112, 196], p.hor, 0.35);
    l.fillStyle = rgb(water, 0.9);
    l.fillRect(wx0, wy, wx1 - wx0, 3);
    l.fillStyle = rgb(mix(water, [10, 30, 80], 0.3), 0.9);
    l.fillRect(wx0, wy + 3, wx1 - wx0, B);
    l.fillStyle = rgb(mix(water, [8, 20, 60], 0.55), 0.92);
    l.fillRect(wx0, wy + 3 + B, wx1 - wx0, B - 3);
    for (let i = 0; i < 6; i++) {
      const sx = wx0 + ((i * 13 + t * 6) % (wx1 - wx0 - 4));
      l.fillStyle = rgb(mix(water, [255, 255, 255], 0.55), 0.8);
      l.fillRect(Math.round(sx), wy + (i % 2), 3, 1);
    }
    l.fillStyle = "rgb(76,140,52)";
    l.fillRect(wx0 + 8, wy, 4, 1); l.fillRect(wx0 + 9, wy - 1, 2, 1);
    l.fillRect(wx1 - 14, wy, 3, 1);

    // Fish jumping.
    if (this.animate) {
      if (!this.fish && Math.random() < dt * 0.06) this.fish = { x: wx0 + 6 + Math.random() * (wx1 - wx0 - 18), t: 0 };
      if (this.fish) {
        const f = this.fish;
        f.t += dt;
        const k = f.t / 1.1;
        const fx = Math.round(f.x + k * 12), fy = Math.round(wy - Math.sin(k * Math.PI) * 9);
        l.fillStyle = "rgb(236,140,62)";
        l.fillRect(fx, fy, 3, 1); l.fillRect(fx - 1, fy - 1, 1, 1);
        if (k > 1) {
          this.spawn(fx, wy, 6, [[200, 230, 255], [255, 255, 255]], 18);
          this.fish = null;
        }
      }
    }

    // Broken block: cracks while being hit, a hole once broken.
    const now = performance.now();
    if (this.broken) {
      const bx = this.broken.col * B, by = this.groundY;
      if (this.broken.until && now < this.broken.until) {
        l.clearRect(bx, by, B, B);
        l.fillStyle = "rgb(70,48,30)";
        l.fillRect(bx, by + B - 1, B, 1);
      } else if (this.broken.until && now >= this.broken.until) {
        this.spawn(bx + B / 2, by, 6, [C.grass[0], C.grass[2]], 10);
        this.broken = null;
      } else {
        l.fillStyle = "rgba(20,12,6,.75)";
        const k = this.broken.crack;
        l.fillRect(bx + 1, by + 1, 1, Math.min(4, k + 1));
        if (k > 1) l.fillRect(bx + 3, by + 2, 2, 1);
        if (k > 2) l.fillRect(bx + 4, by + 3, 1, 2);
      }
    }

    // Flowers and grass tufts, swaying.
    for (const f of this.flowers) {
      const sway = this.animate ? Math.round(Math.sin(t * 1.6 + f.ph) * 0.7) : 0;
      if (f.kind === "grass") {
        l.fillStyle = "rgb(96,170,60)";
        l.fillRect(f.x, f.y - f.h, 1, f.h);
        l.fillRect(f.x + 2 + sway, f.y - f.h + 1, 1, f.h - 1);
        l.fillStyle = "rgb(124,196,80)";
        l.fillRect(f.x + 1 + sway, f.y - f.h - 1, 1, f.h + 1);
        continue;
      }
      l.fillStyle = "rgb(70,140,46)";
      l.fillRect(f.x, f.y - 3, 1, 3);
      const bx = f.x + sway, by = f.y - 5;
      const colors = { poppy: [222, 44, 44], dandelion: [255, 214, 60], cornflower: [80, 120, 240], allium: [190, 110, 230], tulip: [255, 140, 60] };
      const c = colors[f.kind];
      l.fillStyle = rgb(c);
      l.fillRect(bx - 1, by, 3, 2);
      l.fillRect(bx, by - 1, 1, 1);
      l.fillStyle = rgb(mix(c, [0, 0, 0], 0.35));
      l.fillRect(bx, by + 1, 1, 1);
    }

    // Campfire flames.
    const fire = this.fire;
    for (let i = 0; i < 7; i++) {
      const h = 3 + Math.round((Math.sin(t * 9 + i * 1.7) + 1) * 2);
      const x = Math.round(fire.x - 3 + i);
      l.fillStyle = i % 3 === 0 ? "rgb(255,214,90)" : "rgb(255,140,40)";
      l.fillRect(x, fire.y - h, 1, h);
    }
    l.fillStyle = "rgb(255,240,170)";
    l.fillRect(Math.round(fire.x - 1), fire.y - 3, 2, 2);

    // Tint the land by time of day.
    if (p.tintA > 0.005) {
      l.globalCompositeOperation = "source-atop";
      l.fillStyle = rgb(p.tint, p.tintA);
      l.fillRect(0, 0, W, H);
      l.globalCompositeOperation = "source-over";
    }
    ctx.drawImage(this.land, 0, 0);

    // ----- Critters and particles on top. -----
    const hive = this.hive;
    const showBees = dark < 0.55;
    this.bees.forEach((b, i) => {
      const target = i % 2 ? hive : { x: this.playerX + (i - 1.5) * 18, y: this.groundY - 10 };
      b.x = target.x + Math.cos(t * (1.2 + i * 0.2) + b.ph) * b.r;
      b.y = target.y + Math.sin(t * (2.1 + i * 0.3) + b.ph) * b.r * 0.4 - 6;
      b.visible = showBees;
      if (!showBees) return;
      const x = Math.round(b.x), y = Math.round(b.y);
      ctx.fillStyle = "rgb(250,206,60)"; ctx.fillRect(x, y, 2, 2);
      ctx.fillStyle = "rgb(40,30,20)"; ctx.fillRect(x + 1, y, 1, 2);
      if (Math.floor(t * 20 + i) % 2) { ctx.fillStyle = "rgba(255,255,255,.85)"; ctx.fillRect(x, y - 1, 2, 1); }
    });
    this.butterflies.forEach((b, i) => {
      b.visible = dark < 0.45;
      if (!b.visible) return;
      if (this.animate) {
        b.x += Math.sin(t * 0.5 + b.ph) * dt * 14;
        b.y = this.groundY - 16 - Math.sin(t * 1.3 + b.ph) * 8;
      }
      const open = Math.floor(t * 8 + i) % 2;
      ctx.fillStyle = rgb(b.color);
      ctx.fillRect(Math.round(b.x) - (open ? 2 : 1), Math.round(b.y), open ? 2 : 1, 2);
      ctx.fillRect(Math.round(b.x) + 1, Math.round(b.y), open ? 2 : 1, 2);
      ctx.fillStyle = "rgb(40,30,30)";
      ctx.fillRect(Math.round(b.x), Math.round(b.y), 1, 2);
    });

    if (this.animate) {
      // Smoke and embers from the campfire.
      if (Math.random() < dt * 6) this.particles.push({ x: fire.x + (Math.random() - 0.5) * 3, y: fire.y - 6, vx: 1.5 + Math.random() * 2, vy: -8 - Math.random() * 4, g: 0, life: 3 + Math.random() * 1.5, color: [150, 150, 160], size: 2, smoke: true });
      if (Math.random() < dt * 2) this.particles.push({ x: fire.x, y: fire.y - 6, vx: (Math.random() - 0.5) * 6, vy: -16 - Math.random() * 8, g: 0, life: 1 + Math.random(), color: [255, 180, 60], size: 1, ember: true });
      // Falling leaves.
      this.leafSpawn += dt;
      if (this.leafSpawn > 1.6) {
        this.leafSpawn = 0;
        const tr = this.trees[Math.floor(Math.random() * this.trees.length)];
        if (tr) this.particles.push({ x: tr.x + (Math.random() - 0.5) * tr.w, y: tr.canopyBottom, vx: 2, vy: 6, g: 0, life: 6, color: tr.kind === "birch" ? [140, 180, 80] : [96, 150, 56], size: 1, leaf: true, ph: Math.random() * 6 });
      }
    }
    this.particles = this.particles.filter((q) => q.life > 0);
    for (const q of this.particles) {
      q.life -= dt;
      q.vy += q.g * dt;
      q.x += (q.vx + (q.leaf ? Math.sin(t * 2 + q.ph) * 6 : 0)) * dt;
      q.y += q.vy * dt;
      if (q.leaf && q.y > this.groundY) { q.vy = 0; q.vx = 0; }
      const a = q.smoke ? Math.min(0.45, q.life / 3) * (1 - dark * 0.3) : Math.min(1, q.life * 2);
      ctx.fillStyle = rgb(q.color, a);
      ctx.fillRect(Math.round(q.x), Math.round(q.y), q.size, q.size);
    }

    // Light: campfire, torch and fireflies glow in the dark.
    ctx.globalCompositeOperation = "lighter";
    const flicker = 0.85 + Math.sin(t * 11) * 0.06 + Math.sin(t * 17) * 0.05;
    const light = (x, y, r, color, a) => {
      if (a <= 0.01) return;
      const g = ctx.createRadialGradient(x, y, 0, x, y, r);
      g.addColorStop(0, rgb(color, a));
      g.addColorStop(1, rgb(color, 0));
      ctx.fillStyle = g;
      ctx.fillRect(x - r, y - r, r * 2, r * 2);
    };
    light(fire.x, fire.y - 4, 46 * flicker, [255, 140, 50], 0.12 + dark * 0.38);
    if (this.torch) light(this.torch.x, this.torch.y, 40 * flicker, [255, 170, 80], dark * 0.42);
    if (dark > 0.5) {
      for (const f of this.flies) {
        const a = Math.max(0, Math.sin(t * f.sp * 2 + f.ph));
        if (a < 0.15) continue;
        const x = Math.round(f.x + Math.sin(t * 0.7 + f.ph) * 8), y = Math.round(f.y + Math.cos(t * 0.5 + f.ph) * 4);
        ctx.fillStyle = `rgba(220,255,140,${a})`;
        ctx.fillRect(x, y, 1, 1);
        light(x, y, 5, [190, 255, 120], 0.12 * a * dark);
      }
    }
    ctx.globalCompositeOperation = "source-over";
  }
}
