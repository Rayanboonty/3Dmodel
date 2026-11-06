// Liquid glass motion, in the spirit of iOS 26: controls swell and glow
// under the pointer and lean toward a drag, then settle on a real spring;
// a tab's droplet lifts into a lens you can slide between tabs; menus and
// dialogs grow out of the button that opened them and shrink back into it;
// sheets and menus can be swiped down to close.

let enabledFn = () => true;
let bouncy = true;
let snapTabs = () => {};
let lastPress = null;
let lastPressAt = 0;

/** Wire everything up once. */
export function initLiquid({ enabled, snap }) {
  enabledFn = enabled || enabledFn;
  snapTabs = snap || snapTabs;
  document.addEventListener("pointerdown", onDown, true);
  document.addEventListener("click", onClickCapture, true);
}

export function setMotion(kind) { bouncy = kind !== "smooth"; }

/** The control pressed most recently (what a new menu should grow from). */
export function pressedSource() {
  return lastPress && lastPress.isConnected && performance.now() - lastPressAt < 1500 ? lastPress : null;
}

const PRESSABLE = ".btn, .play, .square, .recent-chip, .top-chip, .news-card, .row-item, .swatch, .seg > button, .scene-tile, .cape, .card, .hit, .chip-btn, .wall-pick, .loader, .vrow, .qa, .hero-chip, .play-more, .srv";

// ------------------------------------------------------------ the spring --

const springs = new Map();
let raf = 0;

function springFor(el) {
  let s = springs.get(el);
  if (!s) {
    s = { x: [1, 1, 0, 0], v: [0, 0, 0, 0], t: [1, 1, 0, 0] };
    springs.set(el, s);
  }
  return s;
}

function aim(el, sx, sy, tx, ty) {
  const s = springFor(el);
  s.t = [sx, sy, tx, ty];
  if (!raf) { last = performance.now(); raf = requestAnimationFrame(step); }
}

let last = 0;
function step(now) {
  const dt = Math.min(0.032, (now - last) / 1000);
  last = now;
  const k = bouncy ? 560 : 470;
  const c = bouncy ? 21 : 38;
  for (const [el, s] of springs) {
    let still = true;
    for (let i = 0; i < 4; i++) {
      const a = k * (s.t[i] - s.x[i]) - c * s.v[i];
      s.v[i] += a * dt;
      s.x[i] += s.v[i] * dt;
      const eps = i < 2 ? 0.0006 : 0.05;
      if (Math.abs(s.v[i]) > eps * 10 || Math.abs(s.t[i] - s.x[i]) > eps) still = false;
    }
    const rest = s.t[0] === 1 && s.t[1] === 1 && s.t[2] === 0 && s.t[3] === 0;
    if (still && rest) {
      el.style.scale = "";
      el.style.translate = "";
      springs.delete(el);
      continue;
    }
    el.style.scale = `${s.x[0].toFixed(4)} ${s.x[1].toFixed(4)}`;
    el.style.translate = `${s.x[2].toFixed(2)}px ${s.x[3].toFixed(2)}px`;
  }
  raf = springs.size ? requestAnimationFrame(step) : 0;
}

// --------------------------------------------------------------- presses --

function glowAt(el, x, y) {
  const r = el.getBoundingClientRect();
  const g = document.createElement("span");
  g.className = "lq-glow";
  g.style.left = `${x - r.left}px`;
  g.style.top = `${y - r.top}px`;
  g.style.setProperty("--gs", `${Math.min(240, Math.max(r.width, r.height) * 1.6)}px`);
  if (getComputedStyle(el).position === "static") el.style.position = "relative";
  el.classList.add("lq-host");
  el.appendChild(g);
  return g;
}

function release(g) {
  if (!g) return;
  g.classList.add("out");
  setTimeout(() => g.remove(), 520);
}

function onDown(e) {
  if (e.button !== 0) return;
  const tab = e.target.closest(".tabs > .tab");
  if (tab && enabledFn()) return tabDown(e, tab);
  const el = e.target.closest(PRESSABLE);
  if (!el || el.disabled || el.getAttribute("aria-disabled") === "true") return;
  lastPress = el;
  lastPressAt = performance.now();
  if (!enabledFn()) return;
  const r = el.getBoundingClientRect();
  const big = Math.max(r.width, r.height);
  // Small controls swell more than big cards; the joined Launch button and
  // its arrow dip in instead, so they never separate.
  const solid = el.matches(".play, .play-more");
  const grow = solid ? 0.975 : 1 + Math.min(0.075, 7 / big);
  const glow = glowAt(el, e.clientX, e.clientY);
  const x0 = e.clientX, y0 = e.clientY;
  aim(el, grow, grow, 0, 0);
  const move = (m) => {
    if (solid) return;
    const dx = m.clientX - x0, dy = m.clientY - y0;
    const lim = Math.min(9, big * 0.08);
    const tx = Math.max(-lim, Math.min(lim, dx * 0.16));
    const ty = Math.max(-lim, Math.min(lim, dy * 0.16));
    // Stretch toward the drag like a drop of liquid.
    const sx = grow + Math.min(0.05, Math.abs(dx) / 900) - Math.min(0.025, Math.abs(dy) / 1600);
    const sy = grow + Math.min(0.05, Math.abs(dy) / 900) - Math.min(0.025, Math.abs(dx) / 1600);
    aim(el, sx, sy, tx, ty);
    const rr = el.getBoundingClientRect();
    glow.style.left = `${m.clientX - rr.left}px`;
    glow.style.top = `${m.clientY - rr.top}px`;
  };
  const up = () => {
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", up);
    window.removeEventListener("pointercancel", up);
    release(glow);
    aim(el, 1, 1, 0, 0);
    if (!el.isConnected) springs.delete(el);
  };
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", up);
  window.addEventListener("pointercancel", up);
}

// -------------------------------------------------- the tab bar droplet --

let swallowClick = false;

function onClickCapture(e) {
  if (swallowClick && e.isTrusted) {
    swallowClick = false;
    e.stopPropagation();
    e.preventDefault();
  }
}

function tabDown(e, tab) {
  const group = tab.parentElement;
  const drop = group.querySelector(":scope > .drop");
  lastPress = tab;
  lastPressAt = performance.now();
  if (!drop) return;
  const vertical = group.classList.contains("vertical");
  const tabs = [...group.querySelectorAll(":scope > .tab")];
  const gr = group.getBoundingClientRect();
  const span = (t) => (vertical ? [t.offsetTop, t.offsetTop + t.offsetHeight] : [t.offsetLeft, t.offsetLeft + t.offsetWidth]);
  const size = vertical ? group.clientHeight : group.clientWidth;
  const place = (a, b) => {
    if (vertical) { drop.style.top = `${a}px`; drop.style.bottom = `${size - b}px`; }
    else { drop.style.left = `${a}px`; drop.style.right = `${size - b}px`; }
  };
  const [a0, b0] = span(tab);
  const len = b0 - a0;
  // The leading edge moves first and the trailing one follows, whichever
  // way the droplet is going, so it stretches instead of collapsing.
  const dr = drop.getBoundingClientRect();
  const curStart = vertical ? dr.top - gr.top : dr.left - gr.left;
  const forward = a0 > curStart;
  drop.style.setProperty(vertical ? "--dt" : "--dl", forward ? "0.08s" : "0s");
  drop.style.setProperty(vertical ? "--db" : "--dr", forward ? "0s" : "0.08s");
  drop.classList.add("lifted");
  drop.style.opacity = "1";
  place(a0, b0);
  const p0 = vertical ? e.clientY : e.clientX;
  let dragging = false;
  let lastP = p0, lastT = performance.now(), vel = 0;
  const pos = (ev) => (vertical ? ev.clientY - gr.top : ev.clientX - gr.left);
  const move = (m) => {
    const p = vertical ? m.clientY : m.clientX;
    if (!dragging && Math.abs(p - p0) < 6) return;
    if (!dragging) { dragging = true; drop.classList.add("dragging"); }
    const now = performance.now();
    vel = (p - lastP) / Math.max(1, now - lastT);
    lastP = p; lastT = now;
    const c = Math.max(len / 2, Math.min(size - len / 2, pos(m)));
    place(c - len / 2, c + len / 2);
    const st = Math.min(0.22, Math.abs(vel) * 0.12);
    drop.style.setProperty("--stretch", (1 + st).toFixed(3));
    drop.style.setProperty("--squash", (1 - st * 0.45).toFixed(3));
  };
  const up = (u) => {
    window.removeEventListener("pointermove", move);
    window.removeEventListener("pointerup", up);
    window.removeEventListener("pointercancel", up);
    drop.classList.remove("lifted", "dragging");
    drop.style.removeProperty("--stretch");
    drop.style.removeProperty("--squash");
    if (u.type === "pointercancel") return snapTabs(group);
    if (!dragging) return; // a plain tap: the tab's own click handles it
    const p = pos(u);
    const target = tabs.reduce((best, t) => {
      const [a, b] = span(t);
      const d = Math.abs((a + b) / 2 - p);
      return d < best.d ? { t, d } : best;
    }, { t: tab, d: Infinity }).t;
    swallowClick = true;
    setTimeout(() => { swallowClick = false; }, 0);
    if (target.classList.contains("on")) snapTabs(group);
    else setTimeout(() => target.click(), 0);
  };
  window.addEventListener("pointermove", move);
  window.addEventListener("pointerup", up);
  window.addEventListener("pointercancel", up);
}

// ------------------------------------------------------------ morphing --

const EASE_OUT = "cubic-bezier(0.2, 0.9, 0.25, 1)";

/** Where (inside or outside the panel) a source button's centre sits. */
function originFor(panel, source) {
  const p = panel.getBoundingClientRect();
  const s = source?.isConnected ? source.getBoundingClientRect() : null;
  if (!s || !s.width || !p.width) return { ox: p.width / 2, oy: p.height, sx: 0.88, sy: 0.82 };
  return {
    ox: s.left + s.width / 2 - p.left,
    oy: s.top + s.height / 2 - p.top,
    sx: Math.max(0.08, Math.min(1, s.width / p.width)),
    sy: Math.max(0.08, Math.min(1, s.height / p.height)),
  };
}

/** Grow a panel out of `source` (a button): it expands from the button's
 *  own spot, so it pops out of it rather than sliding across. */
export function morphIn(panel, source) {
  if (!enabledFn() || !panel) return;
  panel.classList.add("morph");
  const o = originFor(panel, source);
  panel.style.transformOrigin = `${o.ox.toFixed(1)}px ${o.oy.toFixed(1)}px`;
  const over = bouncy ? ["1.02 1.025", "0.995 0.993"] : ["1.005 1.005", "1 1"];
  const a = panel.animate(
    [
      { scale: `${o.sx.toFixed(3)} ${o.sy.toFixed(3)}`, opacity: 0, filter: "blur(10px)" },
      { offset: 0.18, opacity: 1 },
      { offset: 0.55, scale: over[0], filter: "blur(0px)" },
      { offset: 0.8, scale: over[1] },
      { scale: "1 1", opacity: 1, filter: "blur(0px)" },
    ],
    { duration: bouncy ? 560 : 420, easing: EASE_OUT },
  );
  a.finished.then(() => { panel.style.transformOrigin = ""; }, () => {});
  // The content arrives just after the glass.
  for (const [i, child] of [...panel.children].entries()) {
    if (child.classList.contains("drop") || child.classList.contains("grabber") || child.classList.contains("lq-sheen")) continue;
    child.animate([{ opacity: 0, translate: "0px 6px" }, { opacity: 1, translate: "0px 0px" }], {
      duration: 320, delay: 120 + Math.min(i, 8) * 22, easing: EASE_OUT, fill: "backwards",
    });
  }
}

/** Shrink a panel back into `source` (or away), then resolve. */
export function morphOut(panel, source) {
  if (!panel || !enabledFn()) return Promise.resolve();
  const o = originFor(panel, source);
  panel.style.transformOrigin = `${o.ox.toFixed(1)}px ${o.oy.toFixed(1)}px`;
  const a = panel.animate(
    [{ scale: "1 1", opacity: 1, filter: "blur(0px)" }, { scale: `${o.sx.toFixed(3)} ${o.sy.toFixed(3)}`, opacity: 0, filter: "blur(8px)" }],
    { duration: 260, easing: "cubic-bezier(0.4, 0, 0.7, 0.2)", fill: "forwards" },
  );
  return a.finished.catch(() => {});
}

// ------------------------------------------------------ swipe to dismiss --

const INTERACTIVE = "button, input, select, textarea, a, label, .range, [data-action], .log-body, .side-body, .list, .gallery";

/**
 * Let a panel be dragged down to close it. It follows the pointer with
 * rubber-banding, shrinks a little as it goes, and either flies off (far or
 * fast enough) or springs back.
 */
export function swipeToDismiss(panel, close, { grip = null } = {}) {
  if (!panel) return;
  panel.addEventListener("pointerdown", (e) => {
    if (e.button !== 0 || !enabledFn()) return;
    const onGrip = grip && e.target.closest(grip);
    if (!onGrip && e.target.closest(INTERACTIVE)) return;
    // Only from the top area of tall panels, so text stays selectable.
    const r = panel.getBoundingClientRect();
    if (!onGrip && e.clientY - r.top > Math.max(90, r.height * 0.35)) return;
    const y0 = e.clientY;
    let dy = 0, vel = 0, lastY = y0, lastT = performance.now(), live = false;
    const move = (m) => {
      const raw = m.clientY - y0;
      if (!live && Math.abs(raw) < 5) return;
      if (!live) { live = true; panel.classList.add("swiping"); panel.setPointerCapture?.(e.pointerId); }
      const now = performance.now();
      vel = (m.clientY - lastY) / Math.max(1, now - lastT);
      lastY = m.clientY; lastT = now;
      dy = raw > 0 ? raw : -Math.pow(-raw, 0.7);
      const k = Math.max(0, dy);
      panel.style.translate = `0px ${dy.toFixed(1)}px`;
      panel.style.scale = `${(1 - Math.min(0.08, k / 2600)).toFixed(4)} ${(1 - Math.min(0.06, k / 3200) + Math.min(0.02, Math.abs(vel) * 0.01)).toFixed(4)}`;
      panel.style.opacity = String(Math.max(0.55, 1 - k / 900));
    };
    const up = () => {
      window.removeEventListener("pointermove", move);
      window.removeEventListener("pointerup", up);
      window.removeEventListener("pointercancel", up);
      if (!live) return;
      panel.classList.remove("swiping");
      const from = { translate: panel.style.translate, scale: panel.style.scale, opacity: panel.style.opacity };
      panel.style.translate = panel.style.scale = panel.style.opacity = "";
      if (dy > 120 || (dy > 30 && vel > 0.65)) {
        const fly = panel.animate([from, { translate: `0px ${dy + window.innerHeight * 0.35}px`, scale: "0.9 0.92", opacity: 0 }], { duration: 280, easing: "cubic-bezier(0.4, 0, 1, 1)", fill: "forwards" });
        const done = () => { close(true); setTimeout(() => fly.cancel(), 650); };
        fly.finished.then(done, done);
      } else {
        panel.animate([from, { translate: "0px -8px", scale: "1.01 0.99", offset: 0.55 }, { translate: "0px 0px", scale: "1 1", opacity: 1 }], { duration: bouncy ? 520 : 360, easing: EASE_OUT });
      }
    };
    window.addEventListener("pointermove", move);
    window.addEventListener("pointerup", up);
    window.addEventListener("pointercancel", up);
  });
}
