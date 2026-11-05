// Names and cover art for Minecraft versions.
//
// Releases from 1.13 on (and most snapshots) use the artwork from Mojang's
// own patch notes, fetched by the launcher. Older versions get a generated
// cover: a block landscape in the update's colours with the version number
// in blocky lettering.

import { renderLines } from "./logo.js";

/** Official update names, by version prefix (most specific first). */
const NAMES = [
  ["1.21.11", "Mounts of Mayhem"],
  ["1.21.10", "The Copper Age"], ["1.21.9", "The Copper Age"],
  ["1.21.8", "Chase the Skies"], ["1.21.7", "Chase the Skies"], ["1.21.6", "Chase the Skies"],
  ["1.21.5", "Spring to Life"],
  ["1.21.4", "The Garden Awakens"],
  ["1.21.3", "Bundles of Bravery"], ["1.21.2", "Bundles of Bravery"],
  ["1.21", "Tricky Trials"],
  ["1.20", "Trails & Tales"],
  ["1.19", "The Wild Update"],
  ["1.18", "Caves & Cliffs: Part II"],
  ["1.17", "Caves & Cliffs: Part I"],
  ["1.16", "Nether Update"],
  ["1.15", "Buzzy Bees"],
  ["1.14", "Village & Pillage"],
  ["1.13", "Update Aquatic"],
  ["1.12", "World of Color"],
  ["1.11", "Exploration Update"],
  ["1.10", "Frostburn Update"],
  ["1.9", "Combat Update"],
  ["1.8", "Bountiful Update"],
  ["1.7", "The Update that Changed the World"],
  ["1.6", "Horse Update"],
  ["1.5", "Redstone Update"],
  ["1.4", "Pretty Scary Update"],
  ["1.0", "Adventure Update"],
];

/** Cover colours and lettering material per era. */
const THEMES = {
  "1.16": { sky: ["#3a0d12", "#8c2a1c"], land: "#5a1f1f", top: "#a8323a", mat: "redstone" },
  "1.13": { sky: ["#0c4a7a", "#3fa4d8"], land: "#1d5f7a", top: "#2fb7c9", mat: "diamond" },
  "1.12": { sky: ["#4b2c86", "#e46aa8"], land: "#5c3b8a", top: "#f2b84b", mat: "amethyst" },
  "1.11": { sky: ["#1d3b2a", "#6e9b5a"], land: "#2f4a2c", top: "#6aa84f", mat: "emerald" },
  "1.10": { sky: ["#1e3b66", "#9fd0f2"], land: "#4a6f99", top: "#e8f4ff", mat: "ice" },
  "1.9": { sky: ["#3b1e1e", "#c45b3c"], land: "#4a2a24", top: "#9a3b2a", mat: "redstone" },
  "1.8": { sky: ["#1c4a3b", "#71c08f"], land: "#2b5a3a", top: "#5ec26a", mat: "emerald" },
  "1.7": { sky: ["#2a5f8f", "#9ad0ff"], land: "#5a3f2a", top: "#6fb43c", mat: "grass" },
  "1.6": { sky: ["#6b3f1e", "#e8b36a"], land: "#6b4a2a", top: "#c69a4a", mat: "gold" },
  "1.5": { sky: ["#3a0f0f", "#b23a2a"], land: "#3a2a2a", top: "#d8322a", mat: "redstone" },
  "1.4": { sky: ["#1a1030", "#6a3a8a"], land: "#2a1f3a", top: "#ff8a2a", mat: "netherite" },
  beta: { sky: ["#2a5f8f", "#a8d8ff"], land: "#5a3f2a", top: "#6fb43c", mat: "grass" },
  alpha: { sky: ["#30304a", "#8a8aa8"], land: "#4a4a52", top: "#7a7a82", mat: "stone" },
  release: { sky: ["#2f3f8f", "#f6b06a"], land: "#3a3060", top: "#ffb547", mat: "gold" },
};

export function majorOf(id) {
  const m = /^(\d+)\.(\d+)/.exec(id || "");
  return m ? `${m[1]}.${m[2]}` : null;
}

/** The update's name, or a sensible label. */
export function updateName(id) {
  if (!id) return "";
  if (/^rd-|^c0\./.test(id)) return "Classic era";
  if (/^inf-/.test(id)) return "Infdev";
  if (/^a\d/.test(id)) return "Alpha";
  if (/^b\d/.test(id)) return "Beta";
  if (/^\d{2}w\d{2}/.test(id)) return "Snapshot";
  for (const [prefix, name] of NAMES) {
    if (id === prefix || id.startsWith(`${prefix}.`) || id.startsWith(`${prefix}-`)) return name;
  }
  if (/^2\d\./.test(id)) return id.includes("snapshot") ? "Snapshot" : "Java Edition";
  return "Java Edition";
}

function themeOf(id) {
  if (/^b\d/.test(id)) return THEMES.beta;
  if (/^(a\d|rd-|c0\.|inf-)/.test(id)) return THEMES.alpha;
  return THEMES[majorOf(id)] || THEMES.release;
}

/** Short big-number label for covers: "1.8.9", "B1.7.3", "26.3". */
function coverLabel(id) {
  if (/^b\d/.test(id)) return `B${id.slice(1)}`;
  if (/^a\d/.test(id)) return `A${id.slice(1)}`;
  if (/^rd-/.test(id)) return "RD";
  const m = /^(\d+(?:\.\d+){1,2})/.exec(id);
  return m ? m[1] : id.slice(0, 6).toUpperCase();
}

const coverCache = new Map();

/** A generated cover image (data URL) for a version. */
export function generatedCover(id, w = 320, h = 180) {
  const key = `${id}@${w}x${h}`;
  if (coverCache.has(key)) return coverCache.get(key);
  const t = themeOf(id);
  const dpr = Math.min(2, window.devicePixelRatio || 1);
  const c = document.createElement("canvas");
  c.width = w * dpr;
  c.height = h * dpr;
  const g = c.getContext("2d");
  g.scale(dpr, dpr);
  const sky = g.createLinearGradient(0, 0, 0, h);
  sky.addColorStop(0, t.sky[0]);
  sky.addColorStop(1, t.sky[1]);
  g.fillStyle = sky;
  g.fillRect(0, 0, w, h);
  // Sun glow.
  const glow = g.createRadialGradient(w * 0.78, h * 0.3, 0, w * 0.78, h * 0.3, h * 0.8);
  glow.addColorStop(0, "rgba(255,240,200,0.45)");
  glow.addColorStop(1, "rgba(255,240,200,0)");
  g.fillStyle = glow;
  g.fillRect(0, 0, w, h);
  // Two layers of blocky hills.
  const bs = h / 12;
  let seed = [...id].reduce((a, ch) => a * 31 + ch.charCodeAt(0), 7) >>> 0;
  const r = () => ((seed = (seed * 1664525 + 1013904223) >>> 0) / 4294967296);
  for (const [layer, alpha, base] of [[0, 0.55, h * 0.62], [1, 1, h * 0.78]]) {
    let level = Math.floor(r() * 3);
    for (let x = 0; x < w; x += bs) {
      if (r() > 0.6) level = Math.max(0, Math.min(4, level + (r() > 0.5 ? 1 : -1)));
      const top = base - level * bs * (layer ? 0.7 : 1);
      g.globalAlpha = alpha;
      g.fillStyle = t.top;
      g.fillRect(x, top, bs + 0.5, bs * 0.35);
      g.fillStyle = t.land;
      g.fillRect(x, top + bs * 0.35, bs + 0.5, h - top);
    }
  }
  g.globalAlpha = 1;
  const shade = g.createLinearGradient(0, h * 0.5, 0, h);
  shade.addColorStop(0, "rgba(0,0,0,0)");
  shade.addColorStop(1, "rgba(0,0,0,0.45)");
  g.fillStyle = shade;
  g.fillRect(0, 0, w, h);
  // The version in blocky letters.
  const label = coverLabel(id);
  const cell = Math.min(h * 0.07, (w * 0.8) / (label.length * 5.4));
  const art = renderLines([{ text: label, mats: () => t.mat }], cell, { dpr });
  const aw = art.cssWidth, ah = art.cssHeight;
  g.drawImage(art, (w - aw) / 2, (h - ah) / 2 - h * 0.06, aw, ah);
  const url = c.toDataURL("image/png");
  coverCache.set(key, url);
  return url;
}

/**
 * Cover for a version: Mojang's patch-note art when known, else generated.
 * `art` maps version id → image URL.
 */
export function coverFor(id, art) {
  if (art && art[id]) return art[id];
  return generatedCover(id);
}
