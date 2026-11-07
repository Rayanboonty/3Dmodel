// Particle dust: things that leave break up into fine glowing dust that
// drifts away, and things that arrive gather out of it. One full-window
// canvas draws every particle with additive light, so dense dust glows.

import { snapshot } from "./snapshot.js";

const canvas = document.createElement("canvas");
canvas.id = "dust";
canvas.setAttribute("aria-hidden", "true");
document.body.appendChild(canvas);
const ctx = canvas.getContext("2d");
let dpr = 1;
let W = 0, H = 0;
let parts = [];
let toHide = [];
// Elements being broken up: the real element stays on screen and is cut
// away from the top as its rows break off.
let cuts = [];
let rings = [];
let raf = 0;
let enabled = true;
let MAX = 12000;
// Phones: fewer, cheaper particles (a lower-resolution canvas too).
let lite = false;
const LK = () => (lite ? 0.4 : 1);

function resize() {
  dpr = Math.min(lite ? 1.25 : 2, window.devicePixelRatio || 1);
  W = window.innerWidth; H = window.innerHeight;
  canvas.width = Math.round(W * dpr);
  canvas.height = Math.round(H * dpr);
}
resize();
window.addEventListener("resize", resize);

// A soft round sprite per colour: a bright core with a glow around it.
const sprites = new Map();
function sprite(color) {
  let s = sprites.get(color);
  if (s) return s;
  s = document.createElement("canvas");
  s.width = s.height = 32;
  const g = s.getContext("2d");
  const grd = g.createRadialGradient(16, 16, 0, 16, 16, 16);
  grd.addColorStop(0, color);
  grd.addColorStop(0.3, withAlpha(color, 0.75));
  grd.addColorStop(0.6, withAlpha(color, 0.18));
  grd.addColorStop(1, withAlpha(color, 0));
  g.fillStyle = grd;
  g.fillRect(0, 0, 32, 32);
  sprites.set(color, s);
  return s;
}

function withAlpha(c, a) {
  const m = c.match(/rgba?\(([^)]+)\)/);
  if (!m) return c;
  const [r, g, b] = m[1].split(",").map((x) => parseFloat(x));
  return `rgba(${r | 0},${g | 0},${b | 0},${a})`;
}

function kick() {
  if (!raf) { last = performance.now(); raf = requestAnimationFrame(step); }
}

let last = 0;
function step(now) {
  const dt = Math.min(0.033, (now - last) / 1000);
  last = now;
  const t = now / 1000;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, W, H);
  let n = 0;
  for (const p of parts) {
    if (p.asm) {
      // Building a picture: fly in along a curve and land on its own spot,
      // then show its own piece of the picture until the element appears.
      p.age += dt;
      if (p.age < 0) { parts[n++] = p; continue; }
      const k = Math.min(1, p.age / p.life);
      if (k < 1) {
        const e = 1 - Math.pow(1 - k, 3);
        const u = 1 - e;
        const x = u * u * p.ox + 2 * u * e * p.cx + e * e * (p.tx + p.cw / 2);
        const y = u * u * p.oy + 2 * u * e * p.cy + e * e * (p.ty + p.ch / 2);
        const q = Math.max(1.2, Math.max(p.cw, p.ch) * (0.45 + 0.55 * e));
        if (e < 0.7) {
          // A glowing grain in flight.
          ctx.globalAlpha = Math.min(1, k * 5) * 0.55;
          ctx.drawImage(sprite(p.color), x - q * 1.6, y - q * 1.6, q * 3.2, q * 3.2);
        }
        ctx.globalAlpha = Math.min(1, k * 5) * p.alpha;
        ctx.fillStyle = p.color;
        ctx.fillRect(x - q / 2, y - q / 2, q, q);
      } else {
        const f = now > p.until ? (now - p.until) / 240 : 0;
        if (f >= 1) continue;
        ctx.globalAlpha = 1 - f;
        const s2 = p.src;
        if (s2) ctx.drawImage(s2.c, Math.max(0, (p.tx - s2.x) * s2.k), Math.max(0, (p.ty - s2.y) * s2.k), p.cw * s2.k, p.ch * s2.k, p.tx, p.ty, p.cw + 0.6, p.ch + 0.6);
        else { ctx.fillStyle = p.color; ctx.fillRect(p.tx, p.ty, p.cw + 0.6, p.ch + 0.6); }
      }
      parts[n++] = p;
      continue;
    }
    if (p.breakAt !== undefined && now < p.breakAt) {
      // Still part of the element. Below the break line the element itself
      // shows; just above it, its grains hang on for a moment, so the edge
      // crumbles instead of being a straight cut. (On the same clock as the
      // break line, so slow frames can't open a gap between them.)
      if (now >= p.breakAt - p.jit) {
        if (p.src) {
          // A piece of the element's own picture.
          ctx.globalAlpha = 1;
          const s = p.src;
          ctx.drawImage(s.c, Math.max(0, (p.gx - s.x) * s.k), Math.max(0, (p.gy - s.y) * s.k), p.cell * s.k, p.cell * s.k, p.gx, p.gy, p.cell, p.cell);
        } else {
          ctx.globalAlpha = p.alpha;
          ctx.fillStyle = p.color;
          ctx.fillRect(p.gx, p.gy, p.cell, p.cell);
        }
      }
      parts[n++] = p;
      continue;
    }
    if (p.hold > 0) {
      p.hold -= dt;
      if (p.jit === undefined || p.hold <= p.jit) {
        ctx.globalAlpha = p.alpha;
        ctx.fillStyle = p.color;
        ctx.fillRect(p.gx, p.gy, p.cell, p.cell);
      }
      parts[n++] = p;
      continue;
    }
    p.age += dt;
    if (p.age >= p.life) continue;
    if (p.bound && (p.x > p.bound[1] || p.x < p.bound[0])) continue;
    if (p.age < 0) { parts[n++] = p; continue; }
    if (p.bz) {
      // Out of the burst along a curve and down onto its spot.
      const k = Math.min(1, p.age / p.life);
      // Fast out of the burst, slowing as it lands.
      const e = 1 - Math.pow(1 - k, 2.6);
      const u = 1 - e;
      p.x = u * u * p.ox + 2 * u * e * p.cx + e * e * p.tx;
      p.y = u * u * p.oy + 2 * u * e * p.cy + e * e * p.ty;
    } else if (p.tx !== undefined) {
      // Gathering: ease toward a target point, curling on the way.
      const k = Math.min(1, p.age / p.life);
      const e = 1 - Math.pow(1 - k, 3);
      const swirl = (1 - e) * p.curl;
      p.x = p.sx + (p.tx - p.sx) * e + Math.cos(p.ph + k * 5) * swirl;
      p.y = p.sy + (p.ty - p.sy) * e + Math.sin(p.ph + k * 5) * swirl;
    } else {
      // Drifting: a little turbulence, buoyancy, and drag.
      // Each grain wanders on its own heading, nudged by a slow swirl
      // that depends on both x and y (so there are no stripes).
      p.ph += (Math.random() - 0.5) * 7 * dt;
      const swirl = Math.sin(p.x * 0.012 + t * 0.9) * Math.cos(p.y * 0.014 - t * 0.7) * 2.2;
      const h = p.ph + swirl;
      p.vx += (Math.cos(h) * p.turb + p.wx) * dt;
      p.vy += (Math.sin(h) * p.turb + p.wy) * dt;
      p.vx *= 1 - p.drag * dt;
      p.vy *= 1 - p.drag * dt;
      p.x += p.vx * dt;
      p.y += p.vy * dt;
    }
    const k = p.age / p.life;
    // Quick fade in, long fade out, with a slow twinkle.
    const a = Math.min(1, k * 8) * Math.pow(1 - k, p.fade) * (0.75 + 0.25 * Math.sin(t * 9 + p.ph * 3));
    const size = p.bz ? p.size * (0.55 + Math.sin(Math.PI * k) * 0.9) : p.size * (p.tx !== undefined ? 0.6 + 0.4 * (1 - k) : 1 - k * 0.55);
    if (p.sq) {
      // A grain of a broken element: a round dot that shrinks as it rises.
      const q = p.size * (1 - k * 0.7);
      ctx.globalAlpha = p.alpha * Math.min(1, 1.5 * Math.pow(1 - k, 1.4));
      ctx.fillStyle = p.color;
      if (q < 2.6) ctx.fillRect(p.x - q / 2, p.y - q / 2, q, q);
      else {
        ctx.beginPath();
        ctx.arc(p.x, p.y, q / 2, 0, 6.2832);
        ctx.fill();
      }
    } else {
      ctx.globalAlpha = a * p.alpha;
      ctx.drawImage(sprite(p.color), p.x - size * 2, p.y - size * 2, size * 4, size * 4);
    }
    parts[n++] = p;
  }
  parts.length = n;
  ctx.globalAlpha = 1;
  if (cuts.length) cuts = cuts.filter((c) => cutAt(c, now));
  if (toHide.length) { for (const el of toHide) { el.style.opacity = "0"; el.style.visibility = "hidden"; } toHide = []; }
  // Soft glowing bands (the theme change's edge).
  rings = rings.filter((g) => now - g.t0 < g.dur);
  for (const g of rings) {
    const k = (now - g.t0) / g.dur;
    const rad = Math.max(1, g.radiusAt(k));
    const inner = Math.max(0, rad - g.width), outer = rad + g.width * 0.18;
    const fade = Math.sin(Math.PI * Math.min(1, k * 1.15));
    const grd = ctx.createRadialGradient(g.x, g.y, inner, g.x, g.y, outer);
    const c = g.light ? "255,214,150" : "150,130,255";
    grd.addColorStop(0, `rgba(${c},0)`);
    grd.addColorStop(0.62, `rgba(${c},${(0.16 * fade).toFixed(3)})`);
    grd.addColorStop(0.86, `rgba(${c},${(0.34 * fade).toFixed(3)})`);
    grd.addColorStop(1, `rgba(${c},0)`);
    ctx.fillStyle = grd;
    ctx.fillRect(0, 0, W, H);
  }
  raf = parts.length || rings.length || cuts.length ? requestAnimationFrame(step) : 0;
  if (!raf) ctx.clearRect(0, 0, W, H);
}

/** Move an element's break line; false once it has fully broken up. */
function cutAt(c, now) {
  const e = (now - c.t0) / 1000;
  const front = c.top + Math.max(0, Math.min(1, e / c.peel)) * c.h;
  const done = e >= c.peel + c.tail;
  if (c.paint && !done && front < c.top + c.h) {
    // Drawn from its picture (it can't stay on screen itself).
    const s = c.paint, y0 = front - c.top;
    ctx.globalAlpha = 1;
    ctx.drawImage(s.c, 0, y0 * s.k, c.w * s.k, (c.h - y0) * s.k, c.left, front, c.w, c.h - y0);
  }
  for (const t of c.els) {
    if (!t.el.isConnected) continue;
    if (done) {
      t.el.style.clipPath = t.clip;
      if (t.restore) t.restore();
      else { t.el.style.opacity = "0"; t.el.style.visibility = "hidden"; }
    } else {
      t.el.style.clipPath = `inset(${Math.max(0, front - t.top).toFixed(1)}px -90px -90px -90px)`;
    }
  }
  if (done && c.resolve) c.resolve();
  return !done;
}

function add(p) {
  if (parts.length >= MAX) return;
  parts.push({
    age: 0, life: 1.2, size: 1.6, alpha: 1, fade: 1.6, turb: 26, drag: 1.2, wx: 0, wy: -18, ph: Math.random() * 6.28,
    vx: 0, vy: 0, ...p,
  });
}

// ------------------------------------------------------------ colours --

const imgCache = new Map();

function sampleImage(url) {
  if (imgCache.has(url)) return imgCache.get(url);
  const out = [];
  imgCache.set(url, out);
  const img = new Image();
  img.crossOrigin = "anonymous";
  img.onload = () => {
    try {
      const c = document.createElement("canvas");
      c.width = c.height = 6;
      const g = c.getContext("2d", { willReadFrequently: true });
      g.drawImage(img, 0, 0, 6, 6);
      const d = g.getImageData(0, 0, 6, 6).data;
      for (let i = 0; i < d.length; i += 4) if (d[i + 3] > 100) out.push(brighten(d[i], d[i + 1], d[i + 2]));
    } catch { /* another site's image: no colours */ }
  };
  img.src = url;
  return out;
}

// Dust glows, so dark colours are lifted toward light.
function brighten(r, g, b) {
  const m = Math.max(r, g, b, 1);
  const k = Math.max(1, 120 / m);
  return `rgba(${Math.min(255, r * k) | 0},${Math.min(255, g * k) | 0},${Math.min(255, b * k) | 0},1)`;
}

function parseColor(c) {
  const m = c && c.match(/rgba?\(([^)]+)\)/);
  if (!m) return null;
  const [r, g, b, a = 1] = m[1].split(",").map((x) => parseFloat(x));
  return a < 0.15 ? null : brighten(r, g, b);
}

/** The colours an element is made of: its fills, text and pictures. */
function colorsOf(el) {
  const set = [];
  const accent = getComputedStyle(document.body).getPropertyValue("--accent").trim() || "#ffb547";
  const nodes = [el, ...el.querySelectorAll("*")].slice(0, 40);
  for (const n of nodes) {
    const cs = getComputedStyle(n);
    const bg = parseColor(cs.backgroundColor);
    if (bg) set.push(bg);
    if (cs.backgroundImage !== "none") {
      for (const m of cs.backgroundImage.matchAll(/url\("([^"]+)"\)/g)) {
        if (!m[1].startsWith("data:image/svg")) set.push(...sampleImage(m[1]));
      }
    }
    if (n.tagName === "IMG" && n.src) set.push(...sampleImage(n.src));
  }
  const acc = parseColor(hexToRgb(accent));
  const base = [acc || "rgba(255,181,71,1)", "rgba(240,143,34,1)"];
  return set.length ? [...set, ...set, ...base] : base;
}

function hexToRgb(h) {
  const m = h.match(/^#?([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i);
  return m ? `rgb(${parseInt(m[1], 16)},${parseInt(m[2], 16)},${parseInt(m[3], 16)})` : h;
}

const pick = (a) => a[(Math.random() * a.length) | 0];

function paintOf(n, cache) {
  if (cache.has(n)) return cache.get(n);
  let out = null;
  const cs = getComputedStyle(n);
  if (n.tagName === "IMG" && n.src) {
    const c = sampleImage(n.src);
    out = c.length ? c : null;
  } else if (cs.backgroundImage && cs.backgroundImage !== "none") {
    const m = cs.backgroundImage.match(/url\("([^"]+)"\)/);
    if (m && !m[1].startsWith("data:image/svg")) { const c = sampleImage(m[1]); out = c.length ? c : null; }
  }
  const solid = (str) => {
    const m = str && str.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    const [rr, g, b, a = 1] = m[1].split(",").map((v) => parseFloat(v));
    if (a <= 0.06) return null;
    // See-through glass breaks into light, glassy grains.
    if (a < 0.75) {
      const k = 0.42;
      return `rgba(${(rr + (255 - rr) * k) | 0},${(g + (255 - g) * k) | 0},${(b + (255 - b) * k) | 0},0.7)`;
    }
    return `rgba(${rr | 0},${g | 0},${b | 0},${Math.min(1, a).toFixed(2)})`;
  };
  // A plain fill, or the colour of a gradient (frosted panels tint that way).
  if (!out) { const c = solid(cs.backgroundColor) || (cs.backgroundImage.includes("gradient") ? solid(cs.backgroundImage.slice(cs.backgroundImage.indexOf("gradient"))) : null); if (c) out = [c]; }
  cache.set(n, out);
  return out;
}

/** A canvas's pixels with where it sits on screen (null if unreadable). */
function readCanvas(c) {
  if (!c || !c.width || !c.height) return null;
  try {
    const r = c.getBoundingClientRect();
    const data = c.getContext("2d").getImageData(0, 0, c.width, c.height).data;
    return { data, w: c.width, h: c.height, r };
  } catch { return null; }
}

function pixelAt(pix, x, y) {
  const px = Math.floor(((x - pix.r.left) / pix.r.width) * pix.w), py = Math.floor(((y - pix.r.top) / pix.r.height) * pix.h);
  if (px < 0 || py < 0 || px >= pix.w || py >= pix.h) return null;
  const i = (py * pix.w + px) * 4;
  return `rgba(${pix.data[i]},${pix.data[i + 1]},${pix.data[i + 2]},1)`;
}

function colorAt(el, x, y, cache) {
  let stack;
  try { stack = document.elementsFromPoint(x, y); } catch { return null; }
  for (const n of stack) {
    if (n === canvas || !el.contains(n)) continue;
    const c = paintOf(n, cache);
    if (c) return pick(c);
    if (n === el) break;
  }
  return null;
}

// ---------------------------------------------------------------- API --

export const dust = {
  setEnabled(on) { enabled = !!on; if (!on) { parts = []; } },
  setLite(on) { lite = !!on; MAX = lite ? 3200 : 12000; resize(); },
  get enabled() { return enabled; },

  /**
   * Break an element up into dust. An edge sweeps across it (left to
   * right by default); what's behind the edge is gone, and dust streams
   * off the edge and drifts away. Resolves when it's fully gone.
   */
  dissolve(el, { duration = 720, dir = "down", density = 1, keep = false, delay = 0 } = {}) {
    if (delay > 0) return new Promise((res) => setTimeout(() => this.dissolve(el, { duration, dir, density, keep }).then(res), delay));
    return this.shatter(el, { duration, density });
  },

  /**
   * Break an element into particles: it's replaced at once by a grid of
   * small grains in its own colours (so it still looks like itself), and
   * the grains break off row by row from the top and float up and away.
   * Resolves once the last row has broken off.
   */
  shatter(el, { duration = 720, density = 1, radial = 0 } = {}) {
    if (!el || !el.isConnected) return Promise.resolve();
    const r = el.getBoundingClientRect();
    if (!enabled || !r.width || !r.height) return Promise.resolve();
    const area = r.width * r.height;
    // Fine grains within a budget (big panels get bigger ones).
    const budget = Math.max(300, Math.min(MAX - parts.length, (lite ? 1500 : 8000) * density));
    const cell = Math.max(2.4, Math.min(11, Math.sqrt(area / budget)));
    const cols = Math.max(1, Math.round(r.width / cell)), rows = Math.max(1, Math.round(r.height / cell));
    const cw = r.width / cols, ch = r.height / rows;
    // The element's own picture, one pixel per grain.
    let pic = null, src = null;
    try {
      const shot = snapshot(el, cols, rows, { budget: lite ? 500 : 1400, scale: dpr });
      pic = shot.data;
      src = { c: shot.canvas, k: shot.k, x: r.left, y: r.top };
    } catch (e) { pic = null; }
    const cache = new Map();
    const fallback = pic ? null : colorsOf(el);
    if (!pic) el.classList.add("dust-sampling");
    const peel = (duration / 1000) * 0.72;
    const jitter = 0.11;
    const t0 = performance.now();
    const cx = r.left + r.width / 2, cy = r.top + r.height / 2;
    for (let j = 0; j < rows; j++) {
      const y = r.top + (j + 0.5) * ch;
      const row = (j + 0.5) / rows;
      for (let i = 0; i < cols; i++) {
        const x = r.left + (i + 0.5) * cw;
        let color, alpha = 1;
        if (pic) {
          const q = (j * cols + i) * 4;
          if (pic[q + 3] < 24) continue; // outside the rounded corners
          alpha = Math.min(1, pic[q + 3] / 255);
          let R = pic[q], G = pic[q + 1], B = pic[q + 2];
          // Dark grains catch a little light as they fly, so they read
          // against the dark background.
          const L = (0.2126 * R + 0.7152 * G + 0.0722 * B) / 255;
          if (L < 0.32) { const m = 0.38 * (1 - L / 0.32); R += (255 - R) * m; G += (255 - G) * m; B += (255 - B) * m; }
          // An explosion glows hot: every grain is pushed toward fiery gold.
          if (radial) { R += (255 - R) * 0.62; G += (205 - G) * 0.55; B += (120 - B) * 0.5; }
          color = `rgb(${R | 0},${G | 0},${B | 0})`;
        } else {
          color = colorAt(el, x, y, cache) || pick(fallback);
        }
        const jit = Math.random() * jitter;
        add({
          // Each grain flies from a random spot in its piece (no grid).
          x: x + (Math.random() - 0.5) * cw * 0.8, y: y + (Math.random() - 0.5) * ch * 0.8,
          gx: r.left + i * cw - 0.3, gy: r.top + j * ch - 0.3, cell: Math.max(cw, ch) + 0.6, src,
          breakAt: t0 + (row * peel + jit) * 1000, jit: jit * 1000, sq: true, size: Math.min(cell * 0.95, 4.6) * (0.7 + Math.random() * 0.55), color,
          ...(radial ? (() => {
            // Blown outward from the middle, fastest near the centre line.
            const dx = x - cx, dy = (y - cy) * 2.2 + (Math.random() - 0.5) * 30;
            const d = Math.hypot(dx, dy) || 1, sp = radial * (180 + Math.random() * 420);
            return { vx: (dx / d) * sp, vy: (dy / d) * sp * 0.75 - 40, turb: 70, drag: 2.1, wx: 0, wy: 60, life: 0.8 + Math.random() * 0.8 };
          })() : {
            vx: (Math.random() - 0.5) * 60 + ((x - cx) / r.width) * 46, vy: -(36 + Math.random() * 120),
            turb: 48, drag: 1.1, wx: 0, wy: -40, life: 0.6 + Math.random() * 0.55,
          }),
          alpha,
        });
      }
    }
    if (!pic) el.classList.remove("dust-sampling");
    // The element itself stays and is cut away from the top as it breaks.
    const els = [{ el, top: r.top, clip: el.style.clipPath }];
    // The wallpaper card is a window onto the world canvas, which the next
    // page covers: it's drawn from its picture on top instead.
    const paint = el.classList.contains("hero") && src ? src : null;
    if (paint) el.style.visibility = "hidden";
    return new Promise((resolve) => {
      cuts.push({ els, top: r.top, h: r.height, left: r.left, w: r.width, paint, t0, peel, tail: jitter + 0.03, resolve });
      kick();
    });
  },

  /** The old edge-sweep dissolve, kept for reference. */
  sweep(el, { duration = 720, dir = "right", density = 1, keep = false } = {}) {
    if (!el || !el.isConnected) return Promise.resolve();
    const r = el.getBoundingClientRect();
    if (!enabled || !r.width || !r.height) return Promise.resolve();
    const colors = colorsOf(el);
    const area = r.width * r.height;
    const total = Math.min(3200, Math.max(320, area / 18)) * density;
    const horiz = dir === "right" || dir === "left";
    const t0 = performance.now();
    let emitted = 0;
    let prevEdge = 0;
    el.style.willChange = "mask-image, opacity";
    return new Promise((resolve) => {
      const frame = (now) => {
        const k = Math.min(1, (now - t0) / duration);
        const e = k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2;
        const pct = -12 + e * 124;
        const deg = { right: 90, left: 270, up: 0, down: 180 }[dir];
        const m = `linear-gradient(${deg}deg, transparent ${pct.toFixed(1)}%, #000 ${(pct + 12).toFixed(1)}%)`;
        el.style.webkitMaskImage = m;
        el.style.maskImage = m;
        el.style.opacity = String(1 - Math.pow(k, 3) * 0.6);
        // Dust leaves from the edge as it passes.
        const want = Math.floor(total * e) - emitted;
        const edge = Math.max(0, Math.min(1, (pct + 6) / 100));
        for (let i = 0; i < want; i++) {
          // Spread over everything the edge crossed since the last frame,
          // so the dust is even however fast frames come.
          const at = prevEdge + (edge - prevEdge) * Math.random();
          let x, y;
          if (horiz) {
            const f = dir === "right" ? at : 1 - at;
            x = r.left + r.width * f + (Math.random() - 0.5) * 18;
            y = r.top + Math.random() * r.height;
          } else {
            const f = dir === "down" ? at : 1 - at;
            x = r.left + Math.random() * r.width;
            y = r.top + r.height * f + (Math.random() - 0.5) * 18;
          }
          // Each grain scatters a little in any direction and is carried
          // along the sweep, so the dust stays a cloud that thins out.
          const base = { right: 0, left: Math.PI, up: -Math.PI / 2, down: Math.PI / 2 }[dir];
          const a = Math.random() * 6.283, sp = 14 + Math.random() * 60;
          const push = 40 + Math.random() * 70;
          add({
            x, y,
            vx: Math.cos(a) * sp + Math.cos(base) * push, vy: Math.sin(a) * sp + Math.sin(base) * push,
            color: pick(colors), size: 0.6 + Math.random() * 1.5, life: 0.55 + Math.random() * 0.8,
            turb: 70, drag: 2.4, wx: Math.cos(base) * 30, wy: Math.sin(base) * 30 - 8, fade: 1.3,
            alpha: 0.6 + Math.random() * 0.4,
          });
        }
        emitted += Math.max(0, want);
        prevEdge = edge;
        kick();
        if (k < 1) requestAnimationFrame(frame);
        else {
          if (!keep) { el.style.opacity = "0"; }
          el.style.willChange = "";
          resolve();
        }
      };
      requestAnimationFrame(frame);
    });
  },

  /** The other half of a dissolve: uncover an element behind the same
   *  moving edge, so new content appears exactly where the dust left. */
  reveal(el, { duration = 720, dir = "right" } = {}) {
    if (!el || !enabled) return;
    const t0 = performance.now();
    const deg = { right: 90, left: 270, up: 0, down: 180 }[dir];
    const frame = (now) => {
      const k = Math.min(1, (now - t0) / duration);
      const e = k < 0.5 ? 2 * k * k : 1 - Math.pow(-2 * k + 2, 2) / 2;
      const pct = -12 + e * 124;
      const m = `linear-gradient(${deg}deg, #000 ${pct.toFixed(1)}%, transparent ${(pct + 12).toFixed(1)}%)`;
      if (k < 1) {
        el.style.webkitMaskImage = m;
        el.style.maskImage = m;
        requestAnimationFrame(frame);
      } else el.style.webkitMaskImage = el.style.maskImage = "";
    };
    frame(t0);
  },

  /** Clear a dissolve's mask so the element shows again. */
  restore(el) {
    if (!el) return;
    el.style.webkitMaskImage = el.style.maskImage = el.style.opacity = "";
  },

  /**
   * Dust gathers from around an element and lands on it as it appears
   * (the element itself fades in under the dust).
   */
  gather(el, { duration = 900, density = 1, spread = 1 } = {}) {
    if (!el || !enabled) return;
    const r = el.getBoundingClientRect();
    if (!r.width) return;
    const colors = colorsOf(el);
    const total = Math.min(1600, Math.max(200, (r.width * r.height) / 40)) * density * LK();
    const reach = Math.max(r.width, r.height) * 0.7 * spread + 60;
    for (let i = 0; i < total; i++) {
      const tx = r.left + Math.random() * r.width, ty = r.top + Math.random() * r.height;
      const a = Math.random() * 6.283, d = reach * (0.5 + Math.random() * 0.7);
      add({
        sx: tx + Math.cos(a) * d, sy: ty + Math.sin(a) * d, tx, ty, x: tx, y: ty,
        age: -Math.random() * duration * 0.0004, life: (duration / 1000) * (0.6 + Math.random() * 0.5),
        curl: 18 + Math.random() * 30, color: pick(colors), size: 0.7 + Math.random() * 1.5, fade: 0.9, alpha: 0.8,
      });
    }
    kick();
    el.animate([{ opacity: 0, filter: "blur(8px) brightness(1.6)" }, { offset: 0.55, opacity: 0.4 }, { opacity: 1, filter: "blur(0px) brightness(1)" }], { duration: duration * 1.05, easing: "cubic-bezier(0.4, 0, 0.2, 1)", fill: "backwards" });
  },

  /** Dust pulled into an element from all around it, like it's charging. */
  charge(el, { duration = 1000, count = 520, colors } = {}) {
    if (!el || !enabled) return;
    const r = el.getBoundingClientRect();
    const cs = colors || colorsOf(el);
    for (let i = 0; i < count; i++) {
      const a = Math.random() * 6.283, d = 90 + Math.random() * 220;
      const tx = r.left + r.width * (0.15 + Math.random() * 0.7), ty = r.top + r.height * (0.25 + Math.random() * 0.5);
      add({
        sx: tx + Math.cos(a) * d * 1.6, sy: ty + Math.sin(a) * d, tx, ty, x: tx, y: ty,
        age: -Math.random() * (duration / 1000) * 0.55, life: 0.55 + Math.random() * 0.4,
        curl: 26 + Math.random() * 40, color: pick(cs), size: 1 + Math.random() * 2, fade: 0.7, alpha: 1,
      });
    }
    kick();
  },

  /**
   * Glowing grains streaming along inside a loading bar, thickest around
   * its moving light. Returns a function that stops the stream.
   */
  flow(bar) {
    if (!bar || !enabled) return () => {};
    let on = true;
    const light = bar.firstElementChild;
    const colors = ["rgba(255,210,140,1)", "rgba(255,181,71,1)", "rgba(240,143,34,1)", "rgba(255,236,205,1)"];
    const tick = () => {
      if (!on || !bar.isConnected || !enabled) return;
      const r = bar.getBoundingClientRect();
      const lr = light ? light.getBoundingClientRect() : null;
      const l = lr && lr.width > 2 ? lr : r; // no fill yet: along the whole bar
      if (r.width) {
        for (let i = 0; i < 7; i++) {
          const near = Math.random() < 0.75;
          const x = near ? l.left + Math.random() * l.width : r.left + Math.random() * r.width;
          add({
            x: Math.max(r.left + 2, Math.min(r.right - 2, x)), y: r.top + 2 + Math.random() * Math.max(1, r.height - 4),
            vx: 40 + Math.random() * 120, vy: (Math.random() - 0.5) * 4, turb: 0, drag: 0.8, wx: 0, wy: 0,
            color: pick(colors), size: 0.9 + Math.random() * 1.1, life: 0.3 + Math.random() * 0.4, fade: 1.1, alpha: 1,
            bound: [r.left + 2, r.right - 3],
          });
        }
        kick();
      }
      requestAnimationFrame(tick);
    };
    tick();
    return () => { on = false; };
  },

  /**
   * An explosion that builds things: grains burst out of `origin`, curve
   * through the air and land on each target, which forms where they land.
   * targets: [{ el, colors? }]
   */
  form(origin, targets, { duration = 1250, total = 2600 } = {}) {
    if (!enabled) return;
    total *= LK();
    const items = targets.map((t) => ({ ...t, r: t.el.getBoundingClientRect() })).filter((t) => t.r.width && t.r.height);
    const area = items.reduce((n, t) => n + t.r.width * t.r.height, 0) || 1;
    const far = Math.hypot(W, H);
    for (const t of items) {
      const cs = t.colors || colorsOf(t.el);
      const cx0 = t.r.left + t.r.width / 2, cy0 = t.r.top + t.r.height / 2;
      const dist = Math.hypot(cx0 - origin.x, cy0 - origin.y);
      const delay = (dist / far) * 0.35;
      const life = (duration / 1000) * (0.75 + (dist / far) * 0.5);
      const n = Math.max(70, Math.round(total * ((t.r.width * t.r.height) / area)));
      for (let i = 0; i < n; i++) {
        const tx = t.r.left + Math.random() * t.r.width, ty = t.r.top + Math.random() * t.r.height;
        // The control point flings each grain out past the burst first.
        const a = Math.random() * 6.283, out = 90 + Math.random() * 260;
        add({
          bz: true, ox: origin.x + (Math.random() - 0.5) * 30, oy: origin.y + (Math.random() - 0.5) * 8,
          cx: origin.x + Math.cos(a) * out * 1.4, cy: origin.y + Math.sin(a) * out, tx, ty, x: origin.x, y: origin.y,
          age: -(delay + Math.random() * 0.12), life: life * (0.85 + Math.random() * 0.3),
          color: pick(cs), size: 0.7 + Math.random() * 1.6, fade: 0.6, alpha: 0.95,
        });
      }
      // The piece itself forms as its grains arrive.
      t.el.animate(
        [{ opacity: 0, filter: "blur(12px) brightness(1.6)", scale: "0.97" }, { opacity: 1, filter: "blur(0px) brightness(1)", scale: "1" }],
        { duration: 560, delay: (delay + life * 0.5) * 1000, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)", fill: "backwards" },
      );
    }
    kick();
  },

  /** Start reading a picture's colours now, so they're ready later. */
  warm(url) { sampleImage(url); },
  colorsOfImage(url) { return sampleImage(url); },

  /**
   * Blow an element apart: its own grains fly outward from the middle, with a
   * bright flash, a shockwave ring and a spray of sparks. Resolves when it's gone.
   */
  explode(el, { colors = ["rgba(255,240,205,1)", "rgba(255,200,110,1)", "rgba(255,160,60,1)"], power = 1 } = {}) {
    if (!el || !el.isConnected || !enabled) return Promise.resolve();
    const r = el.getBoundingClientRect();
    const x = r.left + r.width / 2, y = r.top + r.height / 2;
    const done = this.shatter(el, { duration: 160, density: 1.4, radial: power });
    this.glowRing(x, y, (k) => 30 + (1 - Math.pow(1 - k, 3)) * 420 * power, { duration: 1100, width: 240 });
    this.ring(x, y, (k) => 20 + (1 - Math.pow(1 - k, 2.4)) * 380 * power, { duration: 800, perFrame: 60, colors });
    this.burst(x, y, { count: 1100 * power, colors, speed: 2.8 * power });
    setTimeout(() => this.burst(x, y, { count: 380 * power, colors: ["rgba(255,255,255,1)", ...colors], speed: 1.2 * power }), 90);
    return done;
  },

  /**
   * Build an element out of its own pixels: grains of its real picture fly
   * in (from a point, or from all around when there's none) and land exactly
   * where they belong, then the element itself shows through. Resolves when
   * it has appeared.
   */
  assemble(el, { from = null, duration = 1000, delay = 0, budget = 2400, spread = 1 } = {}) {
    if (!el || !el.isConnected) return Promise.resolve();
    const r = el.getBoundingClientRect();
    if (!enabled || !r.width || !r.height) return Promise.resolve();
    const n = Math.max(120, Math.min(MAX - parts.length, budget * LK()));
    const cell = Math.max(2.2, Math.min(16, Math.sqrt((r.width * r.height) / n)));
    const cols = Math.max(1, Math.round(r.width / cell)), rows = Math.max(1, Math.round(r.height / cell));
    let shot;
    try { shot = snapshot(el, cols, rows, { budget: lite ? 500 : 1400, scale: dpr }); } catch { return Promise.resolve(); }
    return this._assemble(el, r, cols, rows, shot.data, { c: shot.canvas, k: shot.k, x: r.left, y: r.top }, { from, duration, delay, spread });
  },

  /** The same for a picture (an <img>): only its own opaque pixels. */
  assembleImage(img, { from = null, duration = 1000, delay = 0, budget = 2600, spread = 1 } = {}) {
    if (!img || !img.isConnected || !img.naturalWidth) return Promise.resolve();
    const r = img.getBoundingClientRect();
    if (!enabled || !r.width || !r.height) return Promise.resolve();
    const n = Math.max(120, Math.min(MAX - parts.length, budget * LK()));
    const cell = Math.max(1.8, Math.min(12, Math.sqrt((r.width * r.height) / n)));
    const cols = Math.max(1, Math.round(r.width / cell)), rows = Math.max(1, Math.round(r.height / cell));
    const k = Math.min(dpr, 1600 / Math.max(r.width, r.height));
    const c = document.createElement("canvas");
    c.width = Math.round(r.width * k); c.height = Math.round(r.height * k);
    c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
    const sc = document.createElement("canvas");
    sc.width = cols; sc.height = rows;
    const sg = sc.getContext("2d", { willReadFrequently: true });
    sg.drawImage(c, 0, 0, cols, rows);
    let data;
    try { data = sg.getImageData(0, 0, cols, rows).data; } catch { return Promise.resolve(); }
    return this._assemble(img, r, cols, rows, data, { c, k, x: r.left, y: r.top }, { from, duration, delay, spread });
  },

  _assemble(el, r, cols, rows, data, src, { from, duration, delay, spread }) {
    const cw = r.width / cols, ch = r.height / rows;
    const t0 = performance.now();
    const far = Math.hypot(W, H);
    const reach = Math.max(r.width, r.height) * 0.9 * spread + 120;
    let last = 0;
    const grains = [];
    for (let j = 0; j < rows; j++) {
      for (let i = 0; i < cols; i++) {
        const q = (j * cols + i) * 4;
        if (data[q + 3] < 40) continue;
        const tx = r.left + i * cw, ty = r.top + j * ch;
        let ox, oy, wait;
        if (from) {
          ox = from.x + (Math.random() - 0.5) * 24; oy = from.y + (Math.random() - 0.5) * 12;
          // Nearer pieces fill first, so the build sweeps out from the burst.
          wait = (Math.hypot(tx - from.x, ty - from.y) / far) * 0.45 + Math.random() * 0.16;
        } else {
          const a = Math.random() * 6.283, d = reach * (0.45 + Math.random() * 0.8);
          ox = tx + Math.cos(a) * d; oy = ty + Math.sin(a) * d;
          wait = Math.random() * 0.3;
        }
        // A curve: out past the line, then in.
        const mx = (ox + tx) / 2, my = (oy + ty) / 2;
        const bend = (Math.random() - 0.5) * Math.hypot(tx - ox, ty - oy) * 0.7;
        const nx = -(ty - oy), ny = tx - ox, nl = Math.hypot(nx, ny) || 1;
        const life = (duration / 1000) * (0.55 + Math.random() * 0.35);
        const startAt = delay / 1000 + wait * (duration / 1000);
        last = Math.max(last, startAt + life);
        let R = data[q], G = data[q + 1], B = data[q + 2];
        const L = (0.2126 * R + 0.7152 * G + 0.0722 * B) / 255;
        if (L < 0.25) { const m = 0.3 * (1 - L / 0.25); R += (255 - R) * m; G += (255 - G) * m; B += (255 - B) * m; }
        grains.push({
          asm: true, ox, oy, cx: mx + (nx / nl) * bend, cy: my + (ny / nl) * bend, tx, ty, cw, ch, src,
          color: `rgb(${R | 0},${G | 0},${B | 0})`, alpha: Math.min(1, data[q + 3] / 255),
          age: -startAt, life, until: 0, x: ox, y: oy,
        });
      }
    }
    const until = t0 + (last * 1000) + 60;
    for (const g of grains) { g.until = until; add(g); }
    el.style.opacity = "0";
    kick();
    return new Promise((resolve) => {
      setTimeout(() => {
        el.style.opacity = "";
        el.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 240, easing: "ease-out" });
        resolve();
      }, Math.max(0, until - performance.now()));
    });
  },

  /** A burst of dust from a point. */
  burst(x, y, { count = 220, colors = ["rgba(255,210,140,1)", "rgba(255,181,71,1)", "rgba(240,143,34,1)"], speed = 1 } = {}) {
    if (!enabled) return;
    count = Math.round(count * LK());
    for (let i = 0; i < count; i++) {
      const a = Math.random() * 6.283, sp = (40 + Math.random() * 200) * speed;
      add({ x, y, vx: Math.cos(a) * sp, vy: Math.sin(a) * sp * 0.7, color: pick(colors), size: 0.8 + Math.random() * 2, life: 0.7 + Math.random() * 0.9, drag: 2.6, turb: 50, wy: -10 });
    }
    kick();
  },

  /** A light dusting off an area (used as pages fade out). */
  shed(el, { count = 160 } = {}) {
    if (!el || !enabled) return;
    const r = el.getBoundingClientRect();
    if (!r.width) return;
    const colors = colorsOf(el);
    for (let i = 0; i < count; i++) {
      add({
        x: r.left + Math.random() * r.width, y: r.top + Math.random() * r.height,
        vx: (Math.random() - 0.5) * 50, vy: -6 - Math.random() * 30,
        color: pick(colors), size: 0.6 + Math.random() * 1.2, life: 0.5 + Math.random() * 0.6, alpha: 0.55, wy: -12, turb: 50, drag: 2,
      });
    }
    kick();
  },

  /** A soft band of light riding a growing circle. */
  glowRing(x, y, radiusAt, { duration = 1000, width = 200, light = true } = {}) {
    if (!enabled) return;
    rings.push({ x, y, radiusAt, dur: duration, width, light, t0: performance.now() });
    kick();
  },

  /** Sparkles along a growing circle (the theme reveal's edge). */
  ring(x, y, radiusAt, { duration = 720, perFrame = 34, colors = ["rgba(255,210,140,1)", "rgba(255,181,71,1)", "rgba(240,143,34,1)"] } = {}) {
    if (!enabled) return;
    const t0 = performance.now();
    const frame = (now) => {
      const k = Math.min(1, (now - t0) / duration);
      const rad = radiusAt(k);
      for (let i = 0; i < perFrame; i++) {
        const a = Math.random() * 6.283;
        const px = x + Math.cos(a) * rad, py = y + Math.sin(a) * rad;
        if (px < -20 || py < -20 || px > W + 20 || py > H + 20) continue;
        add({ x: px, y: py, vx: Math.cos(a) * 40, vy: Math.sin(a) * 40, color: pick(colors), size: 0.7 + Math.random() * 1.4, life: 0.5 + Math.random() * 0.6, alpha: 0.7 });
      }
      kick();
      if (k < 1) requestAnimationFrame(frame);
    };
    requestAnimationFrame(frame);
  },
};
