// Liquid glass, in the style of iOS 26: the backdrop behind a panel is bent
// through a physically based refraction map (strongest along the rounded
// edges, like a thick glass lens), slightly blurred and saturated, with a
// bright specular rim catching the light.
//
// The refraction maths (surface profile, Snell refraction, displacement and
// specular maps) is adapted from "Liquid Glass" by Archis Vaze
// (https://github.com/archisvaze/liquid-glass), MIT License.
// It needs SVG filters in backdrop-filter, which Chromium has (WebView2 on
// Windows); elsewhere panels keep their frosted blur.

const SVG_NS = "http://www.w3.org/2000/svg";
let defs = null;
let enabled = true;
const filters = new Map(); // size key -> filter id
const tracked = new Map(); // element -> last key
let seq = 0;

/** A squircle-ish convex surface: flat in the middle, rounding at the rim. */
const convex = (x) => Math.pow(1 - Math.pow(1 - x, 4), 0.25);

function refractionProfile(thickness, bezel, ior, samples = 96) {
  const eta = 1 / ior;
  const refract = (nx, ny) => {
    const k = 1 - eta * eta * (1 - ny * ny);
    if (k < 0) return null;
    const sq = Math.sqrt(k);
    return [-(eta * ny + sq) * nx, eta - (eta * ny + sq) * ny];
  };
  const out = new Float64Array(samples);
  for (let i = 0; i < samples; i++) {
    const x = i / samples;
    const y = convex(x);
    const dx = x < 1 ? 0.0001 : -0.0001;
    const deriv = (convex(x + dx) - y) / dx;
    const mag = Math.sqrt(deriv * deriv + 1);
    const ref = refract(-deriv / mag, -1 / mag);
    out[i] = ref ? ref[0] * ((y * bezel + thickness) / ref[1]) : 0;
  }
  return out;
}

/** Walk the rim of a rounded rectangle (only the corner arcs and edge bands matter). */
function rim(w, h, r, bezel, fn) {
  const r1Sq = (r + 1) ** 2, rSq = r * r, rBSq = Math.max(r - bezel, 0) ** 2;
  const wB = w - r * 2, hB = h - r * 2;
  for (let y1 = 0; y1 < h; y1++) {
    // Rows away from the rim only touch the left and right bands.
    const inMid = y1 >= r + bezel && y1 < h - r - bezel;
    for (let x1 = 0; x1 < w; x1++) {
      if (inMid && x1 >= r + bezel && x1 < w - r - bezel) { x1 = w - r - bezel - 1; continue; }
      const x = x1 < r ? x1 - r : x1 >= w - r ? x1 - r - wB : 0;
      const y = y1 < r ? y1 - r : y1 >= h - r ? y1 - r - hB : 0;
      const dSq = x * x + y * y;
      if (dSq > r1Sq || dSq < rBSq) continue;
      const dist = Math.sqrt(dSq);
      if (dist === 0) continue;
      const op = dSq < rSq ? 1 : 1 - (dist - r) / (Math.sqrt(r1Sq) - r);
      if (op <= 0) continue;
      fn(x1, y1, x / dist, y / dist, r - dist, op);
    }
  }
}

function maps(w, h, r, bezel, profile, maxDisp) {
  const c = document.createElement("canvas");
  c.width = w; c.height = h;
  const g = c.getContext("2d");
  const disp = g.createImageData(w, h), d = disp.data;
  for (let i = 0; i < d.length; i += 4) { d[i] = 128; d[i + 1] = 128; d[i + 2] = 0; d[i + 3] = 255; }
  const S = profile.length;
  rim(w, h, r, bezel, (x1, y1, cos, sin, fromSide, op) => {
    const v = profile[Math.min(((fromSide / bezel) * S) | 0, S - 1)] || 0;
    const i = (y1 * w + x1) * 4;
    d[i] = (128 + ((-cos * v) / maxDisp) * 127 * op + 0.5) | 0;
    d[i + 1] = (128 + ((-sin * v) / maxDisp) * 127 * op + 0.5) | 0;
  });
  g.putImageData(disp, 0, 0);
  const dispUrl = c.toDataURL();

  const spec = g.createImageData(w, h), s = spec.data;
  const light = [Math.cos(Math.PI / 3), Math.sin(Math.PI / 3)];
  rim(w, h, r, bezel * 2.5, (x1, y1, cos, sin, fromSide, op) => {
    const dot = Math.abs(cos * light[0] - sin * light[1]);
    const edge = Math.sqrt(Math.max(0, 1 - (1 - fromSide) ** 2));
    const k = dot * edge;
    const col = (255 * k) | 0;
    const i = (y1 * w + x1) * 4;
    s[i] = s[i + 1] = s[i + 2] = col;
    s[i + 3] = (col * k * op) | 0;
  });
  g.putImageData(spec, 0, 0);
  return [dispUrl, c.toDataURL()];
}

function ensureDefs() {
  if (defs) return defs;
  const svg = document.createElementNS(SVG_NS, "svg");
  svg.setAttribute("width", "0");
  svg.setAttribute("height", "0");
  svg.setAttribute("aria-hidden", "true");
  svg.style.cssText = "position:absolute;width:0;height:0;overflow:hidden;pointer-events:none";
  defs = document.createElementNS(SVG_NS, "defs");
  svg.appendChild(defs);
  document.body.appendChild(svg);
  return defs;
}

/** The filter for a glass of this size and corner radius (made once, reused). */
function filterFor(w, h, radius, small) {
  const key = `${w}x${h}r${radius}${small ? "s" : ""}`;
  if (filters.has(key)) return filters.get(key);
  const r = Math.max(2, Math.floor(Math.min(radius, w / 2, h / 2)));
  const bezel = Math.max(1, Math.min(small ? 14 : 26, r - 1, Math.min(w, h) / 2 - 1));
  const profile = refractionProfile(small ? 40 : 70, bezel, 1.5);
  const maxDisp = Math.max(...Array.from(profile, Math.abs)) || 1;
  const [dispUrl, specUrl] = maps(w, h, r, bezel, profile, maxDisp);
  const id = `lg${++seq}`;
  const blur = small ? 1.4 : 5;
  const el = document.createElementNS(SVG_NS, "filter");
  el.setAttribute("id", id);
  el.setAttribute("x", "0%"); el.setAttribute("y", "0%"); el.setAttribute("width", "100%"); el.setAttribute("height", "100%");
  el.setAttribute("color-interpolation-filters", "sRGB");
  el.innerHTML = `
    <feGaussianBlur in="SourceGraphic" stdDeviation="${blur}" result="blurred"/>
    <feImage href="${dispUrl}" x="0" y="0" width="${w}" height="${h}" result="disp"/>
    <feDisplacementMap in="blurred" in2="disp" scale="${(maxDisp * 1).toFixed(2)}" xChannelSelector="R" yChannelSelector="G" result="bent"/>
    <feColorMatrix in="bent" type="saturate" values="1.6" result="bentSat"/>
    <feImage href="${specUrl}" x="0" y="0" width="${w}" height="${h}" result="spec"/>
    <feComposite in="bentSat" in2="spec" operator="in" result="specLit"/>
    <feComponentTransfer in="spec" result="specSoft"><feFuncA type="linear" slope="0.55"/></feComponentTransfer>
    <feBlend in="specLit" in2="bent" mode="normal" result="lit"/>
    <feBlend in="specSoft" in2="lit" mode="normal"/>`;
  ensureDefs().appendChild(el);
  filters.set(key, id);
  // Keep the number of filters bounded (sizes change as windows resize).
  if (filters.size > 160) {
    const [oldKey, oldId] = filters.entries().next().value;
    filters.delete(oldKey);
    defs.querySelector(`#${oldId}`)?.remove();
  }
  return id;
}

function apply(el) {
  if (!enabled || !el.isConnected) return;
  const w = Math.round(el.offsetWidth), h = Math.round(el.offsetHeight);
  if (w < 8 || h < 8 || w > 2400 || h > 1600) return;
  const cs = getComputedStyle(el);
  const radius = Math.round(parseFloat(cs.borderTopLeftRadius) || 0);
  const small = Math.min(w, h) < 90;
  const key = `${w}x${h}r${radius}`;
  if (tracked.get(el) === key) return;
  tracked.set(el, key);
  const id = filterFor(w, h, radius, small);
  el.style.setProperty("--lg", `url(#${id})`);
  el.classList.add("lg-on");
}

const ro = typeof ResizeObserver !== "undefined" ? new ResizeObserver((entries) => {
  for (const e of entries) apply(e.target);
}) : null;

/** Chromium can run SVG filters inside backdrop-filter. */
export function supported() {
  const ua = navigator.userAgentData;
  if (ua && ua.brands && ua.brands.some((b) => /Chromium/.test(b.brand))) return true;
  return /Chrome\//.test(navigator.userAgent);
}

const SELECTOR = ".glass, .top-chip, .hero-chip, .recent-chip, .toast, .modal, .popover, .switcher, .savebar, .log-dock";

/** Give liquid glass to every glass surface under `root` (and keep it fitted). */
export function liquify(root = document) {
  if (!enabled) return;
  const els = root.matches?.(SELECTOR) ? [root] : [];
  els.push(...root.querySelectorAll(SELECTOR));
  for (const el of els) {
    if (el.closest(".dust-ghost")) continue;
    apply(el);
    if (ro && !el._lgWatched) { el._lgWatched = true; ro.observe(el); }
  }
}

/** The side rail draws its glass in ::before: give the rail itself the filter. */
export function liquifyRail(rail) {
  if (!enabled || !rail) return;
  const fit = () => {
    // The glass is the rail's ::before, inset from the rail itself.
    const b = getComputedStyle(rail, "::before");
    const px = (v) => parseFloat(v) || 0;
    const w = Math.round(rail.offsetWidth - px(b.left) - px(b.right));
    const h = Math.round(rail.offsetHeight - px(b.top) - px(b.bottom));
    if (w < 8 || h < 8) return;
    const r = Math.round(px(b.borderTopLeftRadius) || 24);
    rail.style.setProperty("--lg", `url(#${filterFor(w, h, r, false)})`);
    rail.classList.add("lg-rail");
  };
  fit();
  if (ro) new ResizeObserver(fit).observe(rail);
}

export function setEnabled(on) {
  enabled = !!on && supported();
  document.documentElement.classList.toggle("liquid", enabled);
}

/** Watch the page for new glass surfaces (dialogs, menus, toasts, pages). */
export function watch() {
  if (!enabled) return;
  let pending = false;
  const mo = new MutationObserver(() => {
    if (pending) return;
    pending = true;
    requestAnimationFrame(() => { pending = false; liquify(document); });
  });
  mo.observe(document.body, { childList: true, subtree: true });
  liquify(document);
}
