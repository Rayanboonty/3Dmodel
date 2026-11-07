// Particle loading for the Play button: while a launch gets ready, the
// button's progress is a river of glowing grains, thickest at its moving
// front. With no percentage yet, a swarm sweeps across instead. Drawn on a
// canvas inside the button, so nothing strays outside it.

const COLORS = [[255, 244, 220], [255, 214, 150], [255, 181, 71], [240, 143, 34]];
const sprites = COLORS.map(([r, g, b]) => {
  const c = document.createElement("canvas");
  c.width = c.height = 32;
  const x = c.getContext("2d");
  const grd = x.createRadialGradient(16, 16, 0, 16, 16, 16);
  grd.addColorStop(0, `rgba(255,250,235,0.85)`);
  grd.addColorStop(0.18, `rgba(${r},${g},${b},0.95)`);
  grd.addColorStop(0.5, `rgba(${r},${g},${b},0.28)`);
  grd.addColorStop(1, `rgba(${r},${g},${b},0)`);
  x.fillStyle = grd;
  x.fillRect(0, 0, 32, 32);
  return c;
});

/** Start the particle loading in a busy Play button (once; stops by itself). */
export function attachPlayDust(btn) {
  if (!btn || btn._pd) return;
  const track = btn.querySelector(".play-track");
  if (!track) return;
  btn._pd = true;
  btn.classList.add("pd-on");
  const cv = document.createElement("canvas");
  cv.className = "play-dust";
  track.appendChild(cv);
  const ctx = cv.getContext("2d");
  const lite = document.documentElement.classList.contains("lite") || document.body.classList.contains("m");
  const N = lite ? 110 : 240;
  let W = 0, H = 0, dpr = 1;
  const fit = () => {
    const r = track.getBoundingClientRect();
    dpr = Math.min(lite ? 1.5 : 2, window.devicePixelRatio || 1);
    W = r.width; H = r.height;
    cv.width = Math.max(1, Math.round(W * dpr));
    cv.height = Math.max(1, Math.round(H * dpr));
  };
  fit();
  const ro = typeof ResizeObserver !== "undefined" ? new ResizeObserver(fit) : null;
  ro?.observe(track);

  const parts = Array.from({ length: N }, (_, i) => ({
    x: Math.random(), y: Math.random(), sp: 0.35 + Math.random() * 0.9, s: 1.2 + Math.random() * 3.2,
    c: Math.random() < 0.35 ? 0 : 1 + ((Math.random() * 3) | 0), ph: Math.random() * 6.283, edge: i % 4 === 0,
  }));
  let shown = 0, last = performance.now(), band = -0.3;

  const frame = (now) => {
    if (!btn.isConnected || !btn.classList.contains("busy")) { ro?.disconnect(); cv.remove(); return; }
    const dt = Math.min(0.05, (now - last) / 1000);
    last = now;
    const indet = btn.classList.contains("indet");
    const target = indet ? 0 : Math.max(0, Math.min(1, Number(btn.dataset.pct || 0) / 100));
    shown += (target - shown) * Math.min(1, dt * (target < shown ? 9 : 6));
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, W, H);

    const edge = shown * W;
    // Where the grains live: the filled part, or a sweeping band.
    let lo = 0, hi = edge;
    if (indet) {
      band += dt * 0.75;
      if (band > 1.3) band = -0.3;
      lo = (band - 0.2) * W; hi = (band + 0.2) * W;
    }
    // A faint liquid tint under the grains, brightest at the front.
    if (hi - Math.max(0, lo) > 1) {
      const g = ctx.createLinearGradient(Math.max(0, lo), 0, hi, 0);
      g.addColorStop(0, indet ? "rgba(255,181,71,0)" : "rgba(240,143,34,0.34)");
      g.addColorStop(indet ? 0.5 : 0.8, "rgba(255,181,71,0.46)");
      g.addColorStop(1, indet ? "rgba(255,181,71,0)" : "rgba(255,214,150,0.6)");
      ctx.fillStyle = g;
      ctx.fillRect(Math.max(0, lo), 0, hi - Math.max(0, lo), H);
    }
    ctx.globalCompositeOperation = "lighter";
    const span = Math.max(1, hi - lo);
    for (const p of parts) {
      // Grains stream right; past the front they fade and come back in.
      p.x += (p.sp * (indet ? 0.9 : 0.55) * dt * 140) / span;
      if (p.x > 1.06) { p.x = p.edge ? 0.82 + Math.random() * 0.2 : Math.random() * 0.9; p.y = Math.random(); }
      const px = lo + p.x * span;
      if (px < -4 || px > W + 4 || span < 2) continue;
      let a = p.x > 1 ? Math.max(0, 1 - (p.x - 1) / 0.06) : 1;
      if (indet) a *= Math.max(0, 1 - Math.abs(p.x - 0.5) * 2);
      else if (!p.edge) a *= 0.55 + 0.45 * p.x;
      if (a <= 0.02) continue;
      const py = 4 + p.y * (H - 8) + Math.sin(now * 0.003 * p.sp + p.ph) * 2.4;
      const sz = p.s * 2.1;
      ctx.globalAlpha = a;
      ctx.drawImage(sprites[p.c], px - sz, py - sz, sz * 2, sz * 2);
    }
    // The bright front.
    if (!indet && edge > 2) {
      ctx.globalAlpha = 0.5 + Math.sin(now * 0.012) * 0.1;
      const fg = ctx.createRadialGradient(edge, H / 2, 0, edge, H / 2, H * 0.55);
      fg.addColorStop(0, "rgba(255,240,210,0.7)");
      fg.addColorStop(0.35, "rgba(255,190,110,0.3)");
      fg.addColorStop(1, "rgba(255,181,71,0)");
      ctx.fillStyle = fg;
      ctx.fillRect(edge - H, 0, H * 2, H);
    }
    ctx.globalAlpha = 1;
    ctx.globalCompositeOperation = "source-over";
    requestAnimationFrame(frame);
  };
  requestAnimationFrame(frame);
}
