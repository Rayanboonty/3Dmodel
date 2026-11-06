// A small software picture of an element, for the particle effects: what's
// behind it (the wallpaper, the world), its fills, gradients, borders and
// rims, its pictures and icons, and its text, clipped to its rounded shape.
// Not a full renderer; close enough that the grains an element breaks into
// carry its real colours (white text, the orange button, the frosted glass
// tinted by the wallpaper behind it).

const imgs = new Map();
/** An image by URL once it has loaded (starts loading on first ask). */
function loadedImage(url) {
  let im = imgs.get(url);
  if (!im) {
    im = new Image();
    im.decoding = "async";
    im.src = url;
    imgs.set(url, im);
  }
  return im.complete && im.naturalWidth ? im : null;
}

const sameOrigin = (u) => {
  try {
    const x = new URL(u, location.href);
    return x.origin === location.origin || x.protocol === "data:" || x.protocol === "blob:";
  } catch { return false; }
};

/** Split a CSS value at its top-level commas. */
function splitTop(s) {
  const out = [];
  let depth = 0, cur = "";
  for (const ch of s) {
    if (ch === "(") depth++;
    else if (ch === ")") depth--;
    if (ch === "," && depth === 0) { out.push(cur.trim()); cur = ""; } else cur += ch;
  }
  if (cur.trim()) out.push(cur.trim());
  return out;
}

function rgba(s) {
  const m = s && s.match(/rgba?\(([^)]+)\)/);
  if (!m) return s && s.trim().startsWith("transparent") ? [0, 0, 0, 0] : null;
  const v = m[1].split(/[\s,/]+/).filter(Boolean).map(parseFloat);
  return [v[0], v[1], v[2], v[3] ?? 1];
}
const css = (c) => `rgba(${c[0]},${c[1]},${c[2]},${c[3]})`;

const url = (s) => (s.match(/url\("?([^")]+)"?\)/) || [])[1];

function radius(v, w, h) {
  const n = parseFloat(v) || 0;
  return Math.min(String(v).endsWith("%") ? (n / 100) * Math.min(w, h) : n, w / 2, h / 2);
}

/** The element's rounded box as the current path. */
function shape(g, cs, x, y, w, h) {
  g.beginPath();
  if (g.roundRect) {
    g.roundRect(x, y, w, h, [
      radius(cs.borderTopLeftRadius, w, h), radius(cs.borderTopRightRadius, w, h),
      radius(cs.borderBottomRightRadius, w, h), radius(cs.borderBottomLeftRadius, w, h),
    ]);
  } else g.rect(x, y, w, h);
}

/** A canvas gradient for a CSS linear/radial gradient layer. */
function gradient(g, layer, x, y, w, h) {
  const kind = layer.startsWith("linear-gradient") ? "linear" : layer.startsWith("radial-gradient") ? "radial" : null;
  if (!kind) return null;
  const parts = splitTop(layer.slice(layer.indexOf("(") + 1, layer.lastIndexOf(")")));
  let angle = 180;
  if (parts.length && !/^(rgb|transparent|#)/.test(parts[0])) {
    const head = parts.shift();
    const d = head.match(/(-?[\d.]+)deg/);
    if (d) angle = parseFloat(d[1]);
    else if (head.startsWith("to ")) angle = { "to top": 0, "to right": 90, "to bottom": 180, "to left": 270 }[head] ?? 180;
  }
  const stops = parts.map((p) => {
    const c = rgba(p);
    const pos = p.match(/(-?[\d.]+)%\s*$/);
    return c ? { c, pos: pos ? parseFloat(pos[1]) / 100 : null } : null;
  }).filter(Boolean);
  if (!stops.length) return null;
  if (stops[0].pos === null) stops[0].pos = 0;
  if (stops[stops.length - 1].pos === null) stops[stops.length - 1].pos = 1;
  for (let i = 1; i < stops.length - 1; i++) {
    if (stops[i].pos !== null) continue;
    let j = i;
    while (stops[j].pos === null) j++;
    const a = stops[i - 1].pos, b = stops[j].pos;
    for (let k = i; k < j; k++) stops[k].pos = a + ((b - a) * (k - i + 1)) / (j - i + 1);
  }
  let grd;
  if (kind === "linear") {
    const rad = (angle * Math.PI) / 180;
    const dx = Math.sin(rad), dy = -Math.cos(rad);
    const half = (Math.abs(w * dx) + Math.abs(h * dy)) / 2;
    const cx = x + w / 2, cy = y + h / 2;
    grd = g.createLinearGradient(cx - dx * half, cy - dy * half, cx + dx * half, cy + dy * half);
  } else {
    grd = g.createRadialGradient(x + w / 2, y + h / 2, 0, x + w / 2, y + h / 2, Math.max(w, h) / 2);
  }
  for (const s of stops) grd.addColorStop(Math.min(1, Math.max(0, s.pos)), css(s.c));
  return grd;
}

function drawCover(g, im, r, size = "cover") {
  const iw = im.naturalWidth || im.width, ih = im.naturalHeight || im.height;
  if (!iw || !ih) return;
  if (size !== "cover" && size !== "contain") { g.drawImage(im, r.left, r.top, r.width, r.height); return; }
  const k = (size === "cover" ? Math.max : Math.min)(r.width / iw, r.height / ih);
  const w = iw * k, h = ih * k;
  g.drawImage(im, r.left + (r.width - w) / 2, r.top + (r.height - h) / 2, w, h);
}

/** Flat inset rims (the bright edge of glass panels). */
function rims(g, cs, r) {
  if (!cs.boxShadow || !cs.boxShadow.includes("inset")) return;
  for (const s of splitTop(cs.boxShadow)) {
    if (!s.includes("inset")) continue;
    const c = rgba(s);
    if (!c || c[3] < 0.05) continue;
    const nums = s.replace(/rgba?\([^)]*\)/, "").match(/-?[\d.]+px/g)?.map(parseFloat) || [];
    const [ox = 0, oy = 0, blur = 0, spread = 0] = nums;
    if (blur > 2) continue;
    g.save();
    shape(g, cs, r.left, r.top, r.width, r.height);
    g.clip();
    g.strokeStyle = g.fillStyle = css(c);
    if (spread > 0 && !ox && !oy) {
      g.lineWidth = spread * 2;
      shape(g, cs, r.left, r.top, r.width, r.height);
      g.stroke();
    } else if (oy > 0) {
      g.fillRect(r.left, r.top, r.width, oy);
    }
    g.restore();
  }
}

function drawText(g, node, cs) {
  const raw = node.nodeValue;
  if (!raw || !raw.trim()) return;
  const c = rgba(cs.color);
  if (!c || c[3] < 0.05) return;
  const range = document.createRange();
  range.selectNodeContents(node);
  const rects = [...range.getClientRects()].filter((r) => r.width > 0.5 && r.height > 0.5);
  if (!rects.length) return;
  g.fillStyle = css(c);
  g.font = `${cs.fontStyle} ${cs.fontWeight} ${cs.fontSize} ${cs.fontFamily}`;
  try { g.letterSpacing = cs.letterSpacing === "normal" ? "0px" : cs.letterSpacing; } catch { /* older engines */ }
  g.textBaseline = "middle";
  let t = raw.replace(/\s+/g, " ").trim();
  if (cs.textTransform === "uppercase") t = t.toUpperCase();
  if (rects.length === 1) {
    const r = rects[0];
    g.fillText(t, r.left, r.top + r.height / 2, r.width + 2);
    return;
  }
  // Wrapped text: fit the words to each line's box.
  const words = t.split(" ");
  let i = 0;
  for (const r of rects) {
    let line = "";
    while (i < words.length) {
      const next = line ? `${line} ${words[i]}` : words[i];
      if (line && g.measureText(next).width > r.width + 2) break;
      line = next;
      i++;
    }
    if (line) g.fillText(line, r.left, r.top + r.height / 2, r.width + 2);
  }
}

function drawSvg(g, svg, r) {
  const vb = svg.viewBox && svg.viewBox.baseVal;
  const vw = vb && vb.width ? vb.width : r.width, vh = vb && vb.height ? vb.height : r.height;
  g.save();
  g.translate(r.left, r.top);
  g.scale(r.width / vw, r.height / vh);
  if (vb) g.translate(-vb.x, -vb.y);
  for (const p of svg.querySelectorAll("path, rect, circle")) {
    const cs = getComputedStyle(p);
    if (cs.display === "none") continue;
    const fill = rgba(cs.fill);
    if (!fill || fill[3] < 0.05) continue;
    g.globalAlpha *= parseFloat(cs.opacity) || 1;
    g.fillStyle = css(fill);
    if (p.tagName === "path") { try { g.fill(new Path2D(p.getAttribute("d") || "")); } catch { /* bad path */ } }
    else if (p.tagName === "rect") g.fillRect(+p.getAttribute("x") || 0, +p.getAttribute("y") || 0, +p.getAttribute("width") || 0, +p.getAttribute("height") || 0);
    else { g.beginPath(); g.arc(+p.getAttribute("cx") || 0, +p.getAttribute("cy") || 0, +p.getAttribute("r") || 0, 0, 6.2832); g.fill(); }
  }
  g.restore();
}

/** A canvas that can be read back (another site's pictures make it unreadable). */
function readable(c) {
  try { c.getContext("2d")?.getImageData(0, 0, 1, 1); return true; } catch { return false; }
}

function paintTree(g, root, budget) {
  let count = 0;
  const walk = (n, alpha) => {
    if (++count > budget) return;
    const cs = getComputedStyle(n);
    if (cs.display === "none") return;
    const a = alpha * (cs.opacity === "" ? 1 : parseFloat(cs.opacity));
    if (!(a > 0.03)) return;
    const hidden = cs.visibility === "hidden";
    const r = n.getBoundingClientRect();
    g.globalAlpha = a;
    if (!hidden && r.width > 0.5 && r.height > 0.5) {
      if (n instanceof SVGSVGElement) { drawSvg(g, n, r); return; }
      if (n.tagName === "CANVAS") {
        if (n.width && n.height && readable(n)) {
          g.save();
          g.imageSmoothingEnabled = !n.classList.contains("avatar");
          g.drawImage(n, r.left, r.top, r.width, r.height);
          g.restore();
        }
        return;
      }
      if (n.tagName === "IMG") {
        const src = n.currentSrc || n.src;
        if (n.complete && n.naturalWidth && sameOrigin(src)) {
          g.save(); shape(g, cs, r.left, r.top, r.width, r.height); g.clip();
          drawCover(g, n, r, cs.objectFit === "cover" || cs.objectFit === "contain" ? cs.objectFit : "fill");
          g.restore();
        }
        return;
      }
      const bg = rgba(cs.backgroundColor);
      if (bg && bg[3] > 0.01) { shape(g, cs, r.left, r.top, r.width, r.height); g.fillStyle = css(bg); g.fill(); }
      if (cs.backgroundImage && cs.backgroundImage !== "none") {
        // CSS paints the last layer first.
        for (const L of splitTop(cs.backgroundImage).reverse()) {
          if (L.startsWith("url(")) {
            const u = url(L);
            const im = u && sameOrigin(u) && !u.startsWith("data:image/svg") ? loadedImage(u) : null;
            if (im) { g.save(); shape(g, cs, r.left, r.top, r.width, r.height); g.clip(); drawCover(g, im, r, cs.backgroundSize.split(",")[0].trim()); g.restore(); }
          } else {
            const grd = gradient(g, L, r.left, r.top, r.width, r.height);
            if (grd) { shape(g, cs, r.left, r.top, r.width, r.height); g.fillStyle = grd; g.fill(); }
          }
        }
      }
      const bw = parseFloat(cs.borderTopWidth) || 0;
      const bc = bw > 0 ? rgba(cs.borderTopColor) : null;
      if (bc && bc[3] > 0.02 && cs.borderTopStyle !== "none") {
        g.lineWidth = bw;
        g.strokeStyle = css(bc);
        shape(g, cs, r.left + bw / 2, r.top + bw / 2, r.width - bw, r.height - bw);
        g.stroke();
      }
      rims(g, cs, r);
    }
    // Scrolling and clipping boxes hide what overflows them.
    const clip = cs.overflow !== "visible" && cs.overflow !== "" && n !== root;
    if (clip) { g.save(); shape(g, cs, r.left, r.top, r.width, r.height); g.clip(); }
    for (const c of n.childNodes) {
      if (c.nodeType === 3) { if (!hidden) { g.globalAlpha = a; drawText(g, c, cs); } }
      else if (c.nodeType === 1) walk(c, a);
    }
    if (clip) g.restore();
  };
  walk(root, 1);
}

// The wallpaper as it looks on screen (filtered), at quarter size, made
// once and reused until the window, theme or wallpaper changes.
let bdCache = null;
function backdropPicture() {
  const bd = document.querySelector(".backdrop");
  if (!bd) return null;
  const cs = getComputedStyle(bd);
  const u = url(cs.backgroundImage || "");
  const im = u ? loadedImage(u) : null;
  if (!im) return null;
  const br = bd.getBoundingClientRect();
  const key = `${u.length}:${u.slice(-40)}:${cs.filter}:${cs.opacity}:${innerWidth}x${innerHeight}:${br.left},${br.top},${br.width}`;
  if (bdCache && bdCache.key === key) return bdCache;
  const q = 0.25;
  const c = document.createElement("canvas");
  c.width = Math.max(1, Math.ceil(innerWidth * q));
  c.height = Math.max(1, Math.ceil(innerHeight * q));
  const g = c.getContext("2d");
  g.fillStyle = getComputedStyle(document.body).backgroundColor || "#0c0a10";
  g.fillRect(0, 0, c.width, c.height);
  g.scale(q, q);
  g.globalAlpha = parseFloat(cs.opacity) || 1;
  if (cs.filter && cs.filter !== "none") g.filter = cs.filter.replace(/blur\(([\d.]+)px\)/, (m, v) => `blur(${(parseFloat(v) * q).toFixed(2)}px)`);
  // The filter works in canvas pixels: scale the blur down with the canvas.
  g.setTransform(1, 0, 0, 1, 0, 0);
  drawCover(g, im, { left: br.left * q, top: br.top * q, width: br.width * q, height: br.height * q }, "cover");
  bdCache = { key, c, q };
  return bdCache;
}

/** What shows through the element: the wallpaper, and the world where it is. */
function paintBehind(g, el, r, frosted) {
  const bd = backdropPicture();
  const world = document.getElementById("world");
  let wr = null, wop = 0;
  if (world && world.width) {
    wr = world.getBoundingClientRect();
    wop = parseFloat(getComputedStyle(world).opacity);
    if (!(wop > 0.05 && wr.right > r.left && wr.left < r.right && wr.bottom > r.top && wr.top < r.bottom)) wr = null;
  }
  g.save();
  g.fillStyle = getComputedStyle(document.body).backgroundColor || "#0c0a10";
  g.fillRect(r.left, r.top, r.width, r.height);
  if (bd) g.drawImage(bd.c, r.left * bd.q, r.top * bd.q, r.width * bd.q, r.height * bd.q, r.left, r.top, r.width, r.height);
  if (wr && !frosted) {
    g.globalAlpha = wop;
    g.drawImage(world, wr.left, wr.top, wr.width, wr.height);
  } else if (wr) {
    // Frosted glass over the world: drawing it small and stretching it
    // back up blurs it, cheaply.
    const q = 0.12;
    const s = document.createElement("canvas");
    s.width = Math.max(1, Math.ceil(r.width * q));
    s.height = Math.max(1, Math.ceil(r.height * q));
    const sg = s.getContext("2d");
    sg.imageSmoothingQuality = "high";
    sg.drawImage(world, ((r.left - wr.left) / wr.width) * world.width, ((r.top - wr.top) / wr.height) * world.height,
      (r.width / wr.width) * world.width, (r.height / wr.height) * world.height, 0, 0, s.width, s.height);
    g.globalAlpha = wop;
    g.imageSmoothingEnabled = true;
    g.imageSmoothingQuality = "high";
    g.drawImage(s, r.left, r.top, r.width, r.height);
  }
  g.restore();
}

/**
 * The element's picture: `canvas` at full size (`k` canvas pixels per CSS
 * pixel) and `data`, the same scaled down to cols x rows (one pixel per
 * grain) as RGBA bytes. Throws if it can't be drawn.
 */
export function snapshot(el, cols, rows, { budget = 900, scale = 1 } = {}) {
  const r = el.getBoundingClientRect();
  const k = Math.min(scale, 1600 / Math.max(r.width, r.height));
  const c = document.createElement("canvas");
  c.width = Math.max(1, Math.round(r.width * k));
  c.height = Math.max(1, Math.round(r.height * k));
  const g = c.getContext("2d");
  g.scale(k, k);
  g.translate(-r.left, -r.top);
  // The wallpaper card is a window straight onto the world; glass blurs.
  paintBehind(g, el, r, !el.classList.contains("hero"));
  paintTree(g, el, budget);
  // Only what's inside the element's own rounded shape.
  g.globalAlpha = 1;
  g.globalCompositeOperation = "destination-in";
  shape(g, getComputedStyle(el), r.left, r.top, r.width, r.height);
  g.fillStyle = "#000";
  g.fill();
  const s = document.createElement("canvas");
  s.width = cols;
  s.height = rows;
  const sg = s.getContext("2d", { willReadFrequently: true });
  sg.imageSmoothingEnabled = true;
  sg.imageSmoothingQuality = "high";
  sg.drawImage(c, 0, 0, cols, rows);
  return { data: sg.getImageData(0, 0, cols, rows).data, canvas: c, k };
}

// Start loading the wallpaper now so it's ready for the first effect.
try {
  const bd = document.querySelector(".backdrop");
  const u = bd && url(getComputedStyle(bd).backgroundImage || "");
  if (u) loadedImage(u);
} catch { /* no wallpaper yet */ }
