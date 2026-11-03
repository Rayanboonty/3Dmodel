// The living backdrop: a low-resolution pixel-art sky that follows the time
// of day (or a fixed phase), drawn to a small canvas and scaled up with
// crisp pixels.

const H = 180; // logical pixel rows; width follows the window's aspect

// Colour keyframes across the day (hour → palette). Interpolated smoothly.
const NIGHT = { top: "#0a0a26", mid: "#17164a", hor: "#2d2766", far: "#29245a", hill: "#1e1a46", near: "#141233", cloud: "#2f2c62", shade: "#24215a", tint: 0.75, stars: 1 };
const DAWN1 = { top: "#2b2466", mid: "#a35a8f", hor: "#ffb07a", far: "#a873b0", hill: "#714c90", near: "#432d6a", cloud: "#ffd3dd", shade: "#e99ab8", tint: 0.45, stars: 0.25 };
const DAWN2 = { top: "#4c62bd", mid: "#f2a48e", hor: "#ffe0a8", far: "#b893c6", hill: "#8063a4", near: "#4e3c7a", cloud: "#fff2ea", shade: "#f6c3cc", tint: 0.32, stars: 0 };
const DAY = { top: "#3a8ae0", mid: "#78bcf3", hor: "#d2f0ff", far: "#95bedc", hill: "#64a06e", near: "#3f7c46", cloud: "#ffffff", shade: "#d6e7f7", tint: 0.0, stars: 0 };
const DUSK1 = { top: "#3a2e78", mid: "#e0607e", hor: "#ffa45c", far: "#a75d7e", hill: "#6d3c68", near: "#3c2448", cloud: "#ffc2aa", shade: "#da7c8c", tint: 0.4, stars: 0.1 };
const DUSK2 = { top: "#15133c", mid: "#3d2b6d", hor: "#80406c", far: "#3d2c5e", hill: "#2a214d", near: "#191536", cloud: "#4b3b72", shade: "#3b2d5e", tint: 0.65, stars: 0.8 };
const KEYS = [
  [0, NIGHT], [5.0, NIGHT], [6.2, DAWN1], [7.6, DAWN2], [9.6, DAY], [16.2, DAY],
  [18.0, DUSK1], [19.6, DUSK2], [21.0, NIGHT], [24, NIGHT],
];

const FIXED = { dawn: 6.75, day: 12.5, dusk: 18.4, night: 23.2 };

export function phaseOf(hour) {
  if (hour >= 5.4 && hour < 8.6) return "dawn";
  if (hour >= 8.6 && hour < 17.3) return "day";
  if (hour >= 17.3 && hour < 20.2) return "dusk";
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
        out[k] = typeof p0[k] === "number" ? lerp(p0[k], p1[k], s) : mix(hex(p0[k]), hex(p1[k]), s);
      }
      return out;
    }
  }
  return palette(0);
}

// Small deterministic RNG so the landscape is stable between frames.
function rng(seed) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

const BAYER = [[0, 2], [3, 1]];

export class Sky {
  constructor(canvas) {
    this.canvas = canvas;
    this.ctx = canvas.getContext("2d");
    this.mode = "dawn";
    this.animate = true;
    this.parallax = 0;
    this.parallaxTarget = 0;
    this.rise = 0; // 0→1 sunrise intro
    this.last = performance.now();
    this.acc = 0;
    this.skyCache = null;
    this.skyKey = "";
    this.listeners = new Set();
    this.lastPhase = null;
    this.resize();
    window.addEventListener("resize", () => this.resize());
    window.addEventListener("mousemove", (e) => {
      this.parallaxTarget = (e.clientX / window.innerWidth - 0.5) * 2;
    });
    document.addEventListener("visibilitychange", () => {
      if (!document.hidden) this.last = performance.now();
    });
    requestAnimationFrame((t) => this.loop(t));
  }

  onPhase(fn) {
    this.listeners.add(fn);
  }

  setMode(mode) {
    this.mode = mode || "dawn";
    this.skyKey = "";
    this.emitPhase(true);
  }

  setAnimate(on) {
    this.animate = on;
    this.draw(0);
  }

  hour() {
    if (this.mode in FIXED) return FIXED[this.mode];
    const d = new Date();
    return d.getHours() + d.getMinutes() / 60 + d.getSeconds() / 3600;
  }

  phase() {
    return phaseOf(this.hour());
  }

  emitPhase(force = false) {
    const p = this.phase();
    if (force || p !== this.lastPhase) {
      this.lastPhase = p;
      this.listeners.forEach((fn) => fn(p, this.hour()));
    }
  }

  resize() {
    const aspect = Math.max(1, window.innerWidth / Math.max(1, window.innerHeight));
    this.W = Math.round(H * aspect);
    this.H = H;
    this.canvas.width = this.W;
    this.canvas.height = this.H;
    this.ctx.imageSmoothingEnabled = false;
    this.build();
    this.skyKey = "";
    this.draw(0);
  }

  // Landscape, clouds, stars: generated once per size.
  build() {
    const W = this.W;
    const r = rng(1337);
    this.stars = Array.from({ length: Math.round(W * 0.35) }, () => ({
      x: Math.floor(r() * W),
      y: Math.floor(r() * H * 0.62),
      tw: r() * Math.PI * 2,
      sp: 0.6 + r() * 2.2,
      big: r() > 0.92,
    }));
    const ridge = (base, amp, freq, seed, step) => {
      const rr = rng(seed);
      const ph = [rr() * 9, rr() * 9, rr() * 9];
      const pad = 40;
      return Array.from({ length: W + pad * 2 }, (_, i) => {
        const x = i - pad;
        const v = Math.sin(x * freq + ph[0]) * 0.55 + Math.sin(x * freq * 2.3 + ph[1]) * 0.3 + Math.sin(x * freq * 5.1 + ph[2]) * 0.15;
        return Math.round((base - v * amp) / step) * step;
      });
    };
    this.ridges = [
      { h: ridge(H * 0.6, 18, 0.018, 11, 2), par: 2, key: "far", trees: false },
      { h: ridge(H * 0.7, 14, 0.026, 23, 2), par: 5, key: "hill", trees: true },
      { h: ridge(H * 0.82, 8, 0.04, 37, 1), par: 9, key: "near", trees: false },
    ];
    const tr = rng(91);
    this.trees = Array.from({ length: Math.round(W / 14) }, () => ({ x: Math.floor(tr() * (W + 60)) - 30, s: 3 + Math.floor(tr() * 4) }));
    const cr = rng(7);
    this.clouds = Array.from({ length: Math.max(5, Math.round(W / 55)) }, (_, i) => ({
      x: cr() * W,
      y: 12 + cr() * H * 0.38,
      w: 18 + Math.floor(cr() * 26),
      speed: 1.2 + cr() * 2.6,
      layer: i % 2,
    }));
    this.birds = Array.from({ length: 4 }, (_, i) => ({ x: -20 - i * 37, y: 40 + i * 9, speed: 10 + i * 2.5, ph: i }));
    const pr = rng(5);
    this.petals = Array.from({ length: 22 }, () => ({ x: pr() * W, y: pr() * H, vx: 4 + pr() * 6, vy: 5 + pr() * 6, ph: pr() * 6 }));
    this.flies = Array.from({ length: 18 }, () => ({ x: pr() * W, y: H * 0.72 + pr() * H * 0.25, ph: pr() * 6, sp: 0.5 + pr() }));
    this.shooting = null;
  }

  // Dithered, banded sky gradient — cached until the hour or size changes.
  skyImage(p, hour) {
    const key = `${this.W}:${hour.toFixed(2)}`;
    if (key === this.skyKey && this.skyCache) return this.skyCache;
    const W = this.W;
    const img = this.ctx.createImageData(W, H);
    const bands = 22;
    for (let y = 0; y < H; y++) {
      const t = y / (H * 0.78);
      for (let x = 0; x < W; x++) {
        const tt = Math.min(1, t);
        const q = tt * bands;
        const lo = Math.floor(q);
        const frac = q - lo;
        const threshold = (BAYER[y & 1][x & 1] + 0.5) / 4;
        const band = Math.min(bands, lo + (frac > threshold ? 1 : 0)) / bands;
        const c = band < 0.55 ? mix(p.top, p.mid, band / 0.55) : mix(p.mid, p.hor, (band - 0.55) / 0.45);
        const i = (y * W + x) * 4;
        img.data[i] = c[0];
        img.data[i + 1] = c[1];
        img.data[i + 2] = c[2];
        img.data[i + 3] = 255;
      }
    }
    const off = document.createElement("canvas");
    off.width = W;
    off.height = H;
    off.getContext("2d").putImageData(img, 0, 0);
    this.skyCache = off;
    this.skyKey = key;
    return off;
  }

  disc(cx, cy, r, color) {
    const ctx = this.ctx;
    ctx.fillStyle = color;
    for (let y = -r; y <= r; y++) {
      const w = Math.floor(Math.sqrt(r * r - y * y));
      ctx.fillRect(Math.round(cx - w), Math.round(cy + y), w * 2 + 1, 1);
    }
  }

  cloud(x, y, w, p, alpha) {
    const ctx = this.ctx;
    const h = Math.max(5, Math.round(w / 4));
    const body = rgb(p.cloud, alpha);
    const shade = rgb(p.shade, alpha);
    ctx.fillStyle = shade;
    ctx.fillRect(Math.round(x + 2), Math.round(y + h - 1), w - 2, 2);
    ctx.fillStyle = body;
    ctx.fillRect(Math.round(x), Math.round(y + 2), w, h - 2);
    ctx.fillRect(Math.round(x + w * 0.18), Math.round(y - 1), Math.round(w * 0.36), 4);
    ctx.fillRect(Math.round(x + w * 0.45), Math.round(y - 3), Math.round(w * 0.3), 6);
    ctx.fillStyle = shade;
    ctx.fillRect(Math.round(x), Math.round(y + h - 1), w, 1);
  }

  loop(now) {
    const dt = Math.min(0.1, (now - this.last) / 1000);
    this.last = now;
    if (this.animate && !document.hidden) {
      this.acc += dt;
      if (this.acc >= 1 / 30) {
        this.draw(this.acc);
        this.acc = 0;
      }
    } else if (this.rise < 1) {
      this.rise = 1;
      this.draw(0);
    }
    if (Math.floor(now / 30000) !== this.lastMinuteCheck) {
      this.lastMinuteCheck = Math.floor(now / 30000);
      this.emitPhase();
      if (!this.animate) this.draw(0);
    }
    requestAnimationFrame((t) => this.loop(t));
  }

  draw(dt) {
    const { ctx, W } = this;
    const hour = this.hour();
    const p = palette(hour);
    const t = performance.now() / 1000;
    this.parallax += (this.parallaxTarget - this.parallax) * Math.min(1, dt * 3);
    if (this.rise < 1) this.rise = Math.min(1, this.rise + dt / 2.6);
    const rise = 1 - Math.pow(1 - this.rise, 3);
    const horizon = H * 0.66;

    ctx.drawImage(this.skyImage(p, hour), 0, 0);

    // Stars + shooting stars.
    if (p.stars > 0.02) {
      for (const s of this.stars) {
        const a = p.stars * (0.45 + 0.55 * Math.sin(t * s.sp + s.tw));
        if (a <= 0.05) continue;
        ctx.fillStyle = `rgba(255,248,230,${a})`;
        ctx.fillRect(s.x, s.y, 1, 1);
        if (s.big && a > 0.7) {
          ctx.fillStyle = `rgba(255,248,230,${a * 0.45})`;
          ctx.fillRect(s.x - 1, s.y, 3, 1);
          ctx.fillRect(s.x, s.y - 1, 1, 3);
        }
      }
      if (this.animate && p.stars > 0.6) {
        if (!this.shooting && Math.random() < dt * 0.08) {
          this.shooting = { x: Math.random() * W * 0.8 + W * 0.2, y: Math.random() * H * 0.25, life: 0 };
        }
        if (this.shooting) {
          const s = this.shooting;
          s.life += dt;
          s.x -= dt * 120;
          s.y += dt * 45;
          for (let i = 0; i < 8; i++) {
            ctx.fillStyle = `rgba(255,250,235,${(1 - i / 8) * (1 - s.life)})`;
            ctx.fillRect(Math.round(s.x + i * 2.6), Math.round(s.y - i), 2, 1);
          }
          if (s.life > 1) this.shooting = null;
        }
      }
    }

    // Sun (rises on the right, sets on the left) and moon.
    const sunP = (hour - 5.7) / 13.8;
    if (sunP > -0.05 && sunP < 1.05) {
      const sx = W * (0.86 - 0.72 * sunP) + this.parallax * 1.5;
      const lift = Math.sin(Math.max(0, Math.min(1, sunP)) * Math.PI);
      const sy = horizon - 8 - lift * (horizon - 26) + (1 - rise) * 60;
      const low = 1 - lift;
      const r = Math.round(9 + low * 4);
      const core = mix([255, 252, 228], [255, 190, 110], low * 0.85);
      const glow = mix([255, 240, 190], [255, 140, 90], low);
      for (let i = 4; i >= 1; i--) this.disc(sx, sy, r + i * 4, rgb(glow, 0.06 + (0.04 * (5 - i)) * (0.6 + low * 0.6)));
      if (low > 0.35 && this.animate) {
        ctx.save();
        ctx.translate(Math.round(sx), Math.round(sy));
        ctx.rotate(t * 0.05);
        ctx.fillStyle = rgb(glow, 0.12 * low);
        for (let i = 0; i < 12; i++) {
          ctx.rotate(Math.PI / 6);
          ctx.fillRect(r + 6, -1, 26 + 6 * Math.sin(t + i), 2);
        }
        ctx.restore();
      }
      this.disc(sx, sy, r, rgb(core));
      this.disc(sx - r * 0.3, sy - r * 0.3, Math.max(2, r * 0.35), "rgba(255,255,255,.35)");
    }
    const moonP = ((hour + 24 - 19.6) % 24) / 10;
    if (moonP >= 0 && moonP <= 1 && p.stars > 0.15) {
      const mx = W * (0.82 - 0.6 * moonP) + this.parallax * 1.5;
      const my = horizon - 14 - Math.sin(moonP * Math.PI) * (horizon - 40) + (1 - rise) * 60;
      this.disc(mx, my, 14, `rgba(200,210,255,${0.08 * p.stars})`);
      this.disc(mx, my, 8, `rgba(236,236,250,${p.stars})`);
      ctx.fillStyle = `rgba(190,190,215,${p.stars})`;
      ctx.fillRect(Math.round(mx - 3), Math.round(my - 2), 2, 2);
      ctx.fillRect(Math.round(mx + 2), Math.round(my + 2), 3, 2);
      ctx.fillRect(Math.round(mx - 1), Math.round(my + 4), 2, 1);
    }

    // Clouds (two depths).
    for (const c of this.clouds) {
      if (this.animate) c.x += c.speed * dt * (c.layer ? 1.6 : 1);
      if (c.x > W + 40) c.x = -c.w - 40;
      const cx = c.x + this.parallax * (c.layer ? 4 : 2);
      this.cloud(cx, c.y, c.layer ? c.w : Math.round(c.w * 0.7), p, c.layer ? 0.95 : 0.7);
    }

    // Birds at dawn and day.
    if (p.stars < 0.3 && this.animate) {
      ctx.fillStyle = rgb(mix(p.near, [20, 16, 30], 0.5));
      for (const b of this.birds) {
        b.x += b.speed * dt;
        if (b.x > W + 30) { b.x = -30 - Math.random() * 80; b.y = 30 + Math.random() * 40; }
        const flap = Math.floor(t * 5 + b.ph) % 2;
        const bx = Math.round(b.x), by = Math.round(b.y + Math.sin(t * 2 + b.ph) * 2);
        ctx.fillRect(bx, by, 1, 1);
        ctx.fillRect(bx - 1, by - 1 + flap, 1, 1);
        ctx.fillRect(bx + 1, by - 1 + flap, 1, 1);
        ctx.fillRect(bx - 2, by - 1 + flap * 2, 1, 1);
        ctx.fillRect(bx + 2, by - 1 + flap * 2, 1, 1);
      }
    }

    // Hills, back to front, with parallax.
    this.ridges.forEach((rg, li) => {
      const off = Math.round(this.parallax * rg.par);
      const col = p[rg.key];
      const light = mix(col, [255, 255, 255], 0.12);
      ctx.fillStyle = rgb(col);
      for (let x = 0; x < W; x++) {
        const y = rg.h[x + 40 + off] ?? rg.h[x + 40];
        ctx.fillRect(x, y, 1, H - y);
      }
      ctx.fillStyle = rgb(light);
      for (let x = 0; x < W; x++) {
        const y = rg.h[x + 40 + off] ?? rg.h[x + 40];
        ctx.fillRect(x, y, 1, 1);
      }
      if (rg.trees) {
        const dark = rgb(mix(col, [10, 10, 25], 0.28));
        ctx.fillStyle = dark;
        for (const tr of this.trees) {
          const x = tr.x + off;
          if (x < -10 || x > W + 10) continue;
          const base = rg.h[Math.max(0, Math.min(rg.h.length - 1, x + 40))];
          for (let row = 0; row < tr.s * 2; row++) {
            const half = Math.floor((row + 1) / 2.4);
            ctx.fillRect(x - half, base - tr.s * 2 + row, half * 2 + 1, 1);
          }
          ctx.fillRect(x, base, 1, 2);
        }
      }
      if (li === 1) {
        // Atmospheric haze between layers.
        ctx.fillStyle = rgb(p.hor, 0.12);
        ctx.fillRect(0, horizon + 6, W, H);
      }
    });

    // Foreground grass block strip, tinted by the hour.
    const grass = mix([91, 179, 58], p.near, p.tint);
    const grassLight = mix([124, 205, 80], p.near, p.tint * 0.8);
    const dirt = mix([139, 90, 43], p.near, p.tint * 0.9);
    const dirtDark = mix([110, 70, 34], p.near, p.tint * 0.9);
    const gy = H - 12;
    ctx.fillStyle = rgb(dirt);
    ctx.fillRect(0, gy + 4, W, 12);
    ctx.fillStyle = rgb(dirtDark);
    for (let x = (Math.floor(this.parallax * 12) % 6 + 6) % 6; x < W; x += 6) {
      ctx.fillRect(x, gy + 7 + ((x / 6) % 3), 2, 1);
      ctx.fillRect(x + 3, gy + 10 + ((x / 6) % 2), 1, 1);
    }
    ctx.fillStyle = rgb(grass);
    ctx.fillRect(0, gy, W, 5);
    ctx.fillStyle = rgb(grassLight);
    for (let x = 0; x < W; x += 3) {
      const sway = this.animate ? Math.round(Math.sin(t * 2 + x * 0.3) * 0.6) : 0;
      ctx.fillRect(x + sway, gy - 1 - ((x * 7) % 3 === 0 ? 1 : 0), 1, 2);
    }

    // Particles: cherry petals at dawn/dusk, fireflies at night.
    if (this.animate) {
      const ph = phaseOf(hour);
      if (ph === "dawn" || ph === "dusk") {
        for (const pt of this.petals) {
          pt.x += pt.vx * dt;
          pt.y += pt.vy * dt;
          if (pt.y > H || pt.x > W) { pt.x = Math.random() * W - 40; pt.y = -4; }
          ctx.fillStyle = Math.sin(t * 3 + pt.ph) > 0 ? "rgba(255,190,214,.9)" : "rgba(255,226,236,.9)";
          ctx.fillRect(Math.round(pt.x + Math.sin(t * 2 + pt.ph) * 3), Math.round(pt.y), 2, 1);
        }
      } else if (ph === "night") {
        for (const f of this.flies) {
          const a = Math.max(0, Math.sin(t * f.sp * 2 + f.ph));
          if (a < 0.1) continue;
          ctx.fillStyle = `rgba(220,255,140,${a})`;
          ctx.fillRect(Math.round(f.x + Math.sin(t * 0.7 + f.ph) * 6), Math.round(f.y + Math.cos(t * 0.5 + f.ph) * 3), 1, 1);
        }
      }
    }
  }
}
