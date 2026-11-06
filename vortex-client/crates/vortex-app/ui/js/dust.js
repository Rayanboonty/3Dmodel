// Particle dust: things that leave break up into fine glowing dust that
// drifts away, and things that arrive gather out of it. One full-window
// canvas draws every particle with additive light, so dense dust glows.

const canvas = document.createElement("canvas");
canvas.id = "dust";
canvas.setAttribute("aria-hidden", "true");
document.body.appendChild(canvas);
const ctx = canvas.getContext("2d");
let dpr = 1;
let W = 0, H = 0;
let parts = [];
let raf = 0;
let enabled = true;
const MAX = 4200;

function resize() {
  dpr = Math.min(2, window.devicePixelRatio || 1);
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
  grd.addColorStop(0, "rgba(255,255,255,1)");
  grd.addColorStop(0.18, color);
  grd.addColorStop(0.5, withAlpha(color, 0.35));
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
  ctx.globalCompositeOperation = "lighter";
  let n = 0;
  for (const p of parts) {
    p.age += dt;
    if (p.age >= p.life) continue;
    if (p.age < 0) { parts[n++] = p; continue; }
    if (p.tx !== undefined) {
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
    const size = p.size * (p.tx !== undefined ? 0.6 + 0.4 * (1 - k) : 1 - k * 0.55);
    ctx.globalAlpha = a * p.alpha;
    ctx.drawImage(sprite(p.color), p.x - size * 2, p.y - size * 2, size * 4, size * 4);
    parts[n++] = p;
  }
  parts.length = n;
  ctx.globalAlpha = 1;
  ctx.globalCompositeOperation = "source-over";
  raf = parts.length ? requestAnimationFrame(step) : 0;
  if (!raf) ctx.clearRect(0, 0, W, H);
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
  const k = Math.max(1, 170 / m);
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
    if (n.childNodes.length && [...n.childNodes].some((c) => c.nodeType === 3 && c.textContent.trim())) {
      const fg = parseColor(cs.color);
      if (fg) set.push(fg);
    }
  }
  const acc = parseColor(hexToRgb(accent));
  const base = ["rgba(255,236,205,1)", acc || "rgba(255,181,71,1)", "rgba(255,255,255,1)"];
  return set.length ? [...set, ...base] : base;
}

function hexToRgb(h) {
  const m = h.match(/^#?([0-9a-f]{2})([0-9a-f]{2})([0-9a-f]{2})$/i);
  return m ? `rgb(${parseInt(m[1], 16)},${parseInt(m[2], 16)},${parseInt(m[3], 16)})` : h;
}

const pick = (a) => a[(Math.random() * a.length) | 0];

// ---------------------------------------------------------------- API --

export const dust = {
  setEnabled(on) { enabled = !!on; if (!on) { parts = []; } },
  get enabled() { return enabled; },

  /**
   * Break an element up into dust. An edge sweeps across it (left to
   * right by default); what's behind the edge is gone, and dust streams
   * off the edge and drifts away. Resolves when it's fully gone.
   */
  dissolve(el, { duration = 720, dir = "right", density = 1, keep = false } = {}) {
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
    const total = Math.min(1600, Math.max(200, (r.width * r.height) / 40)) * density;
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

  /** A burst of dust from a point. */
  burst(x, y, { count = 220, colors = ["rgba(255,210,140,1)", "rgba(255,181,71,1)", "rgba(255,255,255,1)"], speed = 1 } = {}) {
    if (!enabled) return;
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

  /** Sparkles along a growing circle (the theme reveal's edge). */
  ring(x, y, radiusAt, { duration = 720, perFrame = 34, colors = ["rgba(255,236,205,1)", "rgba(255,181,71,1)", "rgba(255,255,255,1)"] } = {}) {
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
