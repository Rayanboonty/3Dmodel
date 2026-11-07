// Vortex Client UI: routing, pages, liquid-glass interactions, live game state.
import { call, on, onFileDrop, isTauri, isAndroid } from "./api.js";
import { icon, hydrateIcons, drawAvatar } from "./icons.js";
import { World, SCENES, sceneThumb, sceneStill, setCustomPicture } from "./scene.js";
import { initLiquid, setMotion, morphIn, morphOut, pressedSource, swipeToDismiss } from "./liquid.js";
import { Player, defaultSkin } from "./player.js";
import { sound } from "./sound.js";
import { fx, toast } from "./fx.js";
import { dust } from "./dust.js";
import { watch as watchGlass, setEnabled as setLiquid, liquifyRail } from "./lglass.js";
import { STYLES, renderBrand, renderStyle, mount as mountLogo } from "./logo.js";
import { coverFor, updateName } from "./versions.js";

// ------------------------------------------------------------------ state --

const store = {
  get(k) { try { return localStorage.getItem(k); } catch { return null; } },
  set(k, v) { try { localStorage.setItem(k, v); } catch { /* private mode */ } },
};

const S = {
  appVersion: "",
  dataDir: "",
  settings: null,
  accounts: [],
  instances: [],
  selected: store.get("vortex.selected"),
  versions: null,
  news: null,
  runs: {},
  started: {},
  logs: {},
  route: "home",
  phase: "dawn",
  log: { open: false, instance: null, level: "all", search: "" },
  wizard: null,
  mods: { instance: null, list: [], loading: false },
  draft: null,
  side: store.get("vortex.side") || "instances",
  switcher: false,
  instSearch: "",
  instSort: "recent",
  skin: null,
  art: {},
  modsTab: store.get("vortex.modsTab") || "browse",
  browse: { query: "", sort: "relevance", hits: [], total: 0, offset: 0, loading: false, key: "", installed: {}, busy: {} },
  skins: null,
  settingsTab: store.get("vortex.settingsTab") || "general",
  fast: {},
  customWall: null,
  customWallUrl: null,
};

const ROUTES = {
  home: renderHome,
  instances: renderInstances,
  new: renderWizard,
  mods: renderMods,
  skins: renderSkins,
  accounts: renderAccounts,
  settings: renderSettings,
};
const NAV_OF = { new: "instances" };

// Android: no Skins page (offline skins need the Vortex in-game mod), and
// the phone layout with a bottom tab bar.
if (isAndroid) {
  delete ROUTES.skins;
  document.documentElement.classList.add("android");
}
const phone = () => isAndroid || matchMedia("(max-width: 760px)").matches;
function applyPhone() {
  const m = phone();
  const land = m && innerWidth > innerHeight && innerHeight < 560;
  document.body.classList.toggle("m", m);
  document.body.classList.toggle("land", land);
  const nav = document.getElementById("nav");
  // Desktop and phone portrait: a dock along the bottom. Phone landscape:
  // a slim rail on the left.
  if (nav) nav.classList.toggle("vertical", land);
}
addEventListener("resize", () => {
  const before = document.body.className;
  applyPhone();
  if (document.body.className !== before) { const nav = document.getElementById("nav"); const d = nav?.querySelector(".drop"); if (d) { delete d.dataset.top; delete d.dataset.left; d.removeAttribute("style"); } }
});

const $ = (sel, root = document) => root.querySelector(sel);
/** Minecraft versions the Vortex in-game mod is built for (see mod/versions). */
const VORTEX_MC = ["1.20.1", "1.20.4", "1.21.1", "1.21.4", "1.21.8", "1.21.11"];
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const anims = () => S.settings?.animations !== false;
const gb = (mb) => `${(mb / 1024).toFixed(mb % 1024 ? 1 : 0)} GB`;
const runOf = (name) => S.runs[name] || { phase: "idle" };
const selectedInstance = () => S.instances.find((i) => i.name === S.selected) || S.instances[0] || null;
const selectedAccount = () => S.accounts.find((a) => a.selected) || null;
const loaderIcon = (l) => ({ vanilla: "grass", fabric: "fabric", forge: "forge" }[l] || "grass");
const errText = (e) => (typeof e === "string" ? e : e?.message || JSON.stringify(e));
const block = (loader, cls = "") => `<span class="block ${loader} ${cls}">${loader === "vanilla" ? "" : icon(loaderIcon(loader), cls.includes("lg") ? 28 : 20)}</span>`;

const cover = (id) => coverFor(id, S.art);
const coverThumb = (inst, cls = "") => `<span class="cover-thumb ${cls}" style="background-image:url('${esc(cover(inst.minecraftVersion))}')">${inst.loader !== "vanilla" ? `<span class="loader-badge ${inst.loader}">${icon(loaderIcon(inst.loader), 10)}</span>` : ""}</span>`;
const fmtCount = (n) => (n >= 1e6 ? `${(n / 1e6).toFixed(n >= 1e7 ? 0 : 1)}M` : n >= 1e3 ? `${(n / 1e3).toFixed(n >= 1e4 ? 0 : 1)}k` : String(n));
const ACCENTS = {
  amber: ["#ffb547", "#f08f22", "#2b1600"],
  diamond: ["#4fd8eb", "#1fb3cc", "#03262c"],
  emerald: ["#45d46b", "#22b04b", "#03290f"],
  amethyst: ["#b58cff", "#8f5ff0", "#1b0a3a"],
  redstone: ["#ff6a5c", "#e0362c", "#330404"],
  copper: ["#ec9465", "#c8673a", "#2e1206"],
};
const capeUrl = (c) => (c && c !== "none" ? new URL(`img/capes/${c}.png`, location.href).href : null);
const CAPE_NAMES = { none: "None", vortex: "Vortex", diamond: "Diamond", ember: "Ember", emerald: "Emerald", amethyst: "Amethyst" };

function ago(secs) {
  if (!secs) return "Not played yet";
  const d = Math.floor(Date.now() / 1000) - secs;
  if (d < 60) return "Played just now";
  if (d < 3600) return `Played ${Math.floor(d / 60)} min ago`;
  if (d < 86400) return `Played ${Math.floor(d / 3600)} h ago`;
  const days = Math.floor(d / 86400);
  return `Played ${days} day${days === 1 ? "" : "s"} ago`;
}

function clock(ms) {
  const s = Math.floor(ms / 1000);
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
  return h ? `${h}:${String(m).padStart(2, "0")}:${String(sec).padStart(2, "0")}` : `${m}:${String(sec).padStart(2, "0")}`;
}

// --------------------------------------------------------- world + player --

// Phones and tablets get a lighter launcher: the wallpaper and Steve draw
// at 30 frames a second at a lower resolution, panels skip live blur, and
// particles are fewer. Desktops keep everything.
const lite = (() => {
  try {
    const q = new URLSearchParams(location.search);
    if (q.has("lite")) return q.get("lite") !== "0";
  } catch { /* no query */ }
  return isAndroid || (matchMedia("(pointer: coarse)").matches && !matchMedia("(any-pointer: fine)").matches);
})();
document.documentElement.classList.toggle("lite", lite);
dust.setLite(lite);
if (lite) Player.maxFps = 30;

const world = new World(document.getElementById("world"), lite ? { maxFps: 30, dprCap: 1.5 } : {});
const player = new Player(document.getElementById("player-layer"), world);
// Steve sits under the page (the wallpaper card is a window onto him), so
// a tap on him is picked up here: he jumps.
document.addEventListener("click", (e) => {
  if (S.route !== "home" || !e.target.closest?.("#page") || e.target.closest("button, a, input, select, .glass, .home-side, .home-row")) return;
  // His box is the union of his body parts' faces (the 3D root has no size).
  let l = 1e9, t = 1e9, rr = -1e9, bb = -1e9;
  for (const f of player.root.querySelectorAll(".pl-face")) {
    const q = f.getBoundingClientRect();
    if (!q.width) continue;
    l = Math.min(l, q.left); t = Math.min(t, q.top); rr = Math.max(rr, q.right); bb = Math.max(bb, q.bottom);
  }
  if (e.clientX >= l - 6 && e.clientX <= rr + 6 && e.clientY >= t - 6 && e.clientY <= bb) player.jump(true);
});
player.onPoke = (p) => {
  sound.pop();
};

const PHASE_ICON = { dawn: "sun", day: "sun", dusk: "sunset", night: "moon" };
world.onPhase((phase) => {
  S.phase = phase;
  document.body.classList.remove("phase-dawn", "phase-day", "phase-dusk", "phase-night");
  document.body.classList.add(`phase-${phase}`);
  $("#time-icon").innerHTML = icon(PHASE_ICON[phase], 16);
  $("#phase-text").textContent = phase[0].toUpperCase() + phase.slice(1);
});

function tick() {
  $("#time-text").textContent = world.timeLabel(!!S.settings?.clock_12h);
  const inst = selectedInstance();
  if (inst && runOf(inst.name).phase === "running" && S.route === "home") {
    const sub = $("#play .play-sub");
    if (sub && S.started[inst.name]) sub.textContent = `${inst.name} · ${clock(Date.now() - S.started[inst.name])}`;
  }
}
setInterval(tick, 1000);

/** Where an element sits on screen, ignoring any transforms (animations). */
function layoutRect(el) {
  let x = 0, y = 0, n = el;
  while (n && n !== document.body) {
    x += n.offsetLeft;
    y += n.offsetTop;
    if (getComputedStyle(n).position === "fixed") break;
    n = n.offsetParent;
  }
  return { left: x, top: y, width: el.offsetWidth, height: el.offsetHeight, right: x + el.offsetWidth, bottom: y + el.offsetHeight };
}

/** Fit the world (wallpaper + Steve) into the Home wallpaper card. */
function placeHero() {
  const hero = $("#hero");
  const on = !!hero && S.route === "home";
  placeNav();
  document.body.classList.toggle("hero-on", on);
  world.setPaused(!on);
  if (!on) return;
  const r = layoutRect(hero);
  world.setViewport(Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height));
  const rad = parseFloat(getComputedStyle(hero).borderTopLeftRadius) || 22;
  const layer = $("#player-layer");
  layer.style.clipPath = `inset(${r.top}px ${window.innerWidth - r.right}px ${window.innerHeight - r.bottom}px ${r.left}px round ${rad}px)`;
  $("#world").style.borderRadius = `${rad}px`;
  placeAnchor();
  tintGlass();
}
window.addEventListener("resize", () => requestAnimationFrame(() => { placeHero(); placeNav(); }));

/**
 * Desktop: the tab dock floats near the bottom of the wallpaper card (on
 * Home) or of the page, centred on it.
 */
/** Scroll edges: blur the top once scrolled, the bottom while there's more. */
const EDGES = ".sheet-body, .home-side";
function edgeState(el) {
  if (!el || !el.classList) return;
  const more = el.scrollHeight - el.clientHeight - el.scrollTop > 4;
  el.classList.toggle("scrolled", el.scrollTop > 2);
  el.classList.toggle("more", more);
}
document.addEventListener("scroll", (e) => { const el = e.target; if (el instanceof Element && el.matches(EDGES)) edgeState(el); }, { capture: true, passive: true });
const edgeCheck = () => $$(EDGES).forEach(edgeState);
setInterval(edgeCheck, 1200);

function placeNav() {
  requestAnimationFrame(edgeCheck);
  const nav = $("#nav");
  if (!nav) return;
  if (document.body.classList.contains("m")) { nav.style.removeProperty("--nav-x"); nav.style.removeProperty("--nav-b"); return; }
  const target = (S.route === "home" && $("#hero")) || $("#page");
  if (!target) return;
  const r = layoutRect(target);
  if (!r.width) return;
  // Too narrow for labelled tabs: icons only.
  nav.classList.toggle("tight", r.width < 560);
  nav.style.setProperty("--nav-x", `${Math.round(r.left + r.width / 2)}px`);
  nav.style.setProperty("--nav-b", `${Math.max(12, Math.round(window.innerHeight - r.bottom + 16))}px`);
}

/**
 * Phones: blur the wallpaper behind the panels once, into a small picture,
 * instead of asking the screen to blur it live on every frame.
 */
function preblurBackdrop() {
  const el = $(".backdrop");
  if (!el) return;
  const img = new Image();
  img.onload = () => {
    const make = (filter) => {
      const c = document.createElement("canvas");
      c.width = 192; c.height = Math.max(1, Math.round(192 * img.naturalHeight / img.naturalWidth));
      const g = c.getContext("2d");
      g.filter = filter;
      // Overdraw so the blur doesn't fade in from the edges.
      g.drawImage(img, -12, -12, c.width + 24, c.height + 24);
      return `url("${c.toDataURL("image/jpeg", 0.86)}")`;
    };
    try {
      el.style.setProperty("--bd-dark", make("blur(4px) brightness(0.6) saturate(1.4)"));
      el.style.setProperty("--bd-light", make("blur(4px) brightness(1.15) saturate(1.15)"));
      el.classList.add("pre");
    } catch { /* keep the live blur */ }
  };
  img.src = "img/birch-forest.jpg";
}

/** Glass over the wallpaper reads its brightness and darkens to suit. */
function tintGlass() {
  const c = $("#world");
  const v = world.viewport;
  if (!c || !v || !document.body.classList.contains("hero-on")) return;
  let ctx;
  try { ctx = c.getContext("2d"); } catch { return; }
  for (const el of $$(".hero .glassy")) {
    const r = el.getBoundingClientRect();
    if (!r.width || r.left < v.x || r.right > v.x + v.w || r.top < v.y || r.bottom > v.y + v.h) continue;
    try {
      const k = world.dpr;
      const data = ctx.getImageData(Math.round((r.left - v.x) * k), Math.round((r.top - v.y) * k), Math.max(1, Math.round(r.width * k)), Math.max(1, Math.round(r.height * k))).data;
      let sum = 0, n = 0;
      for (let i = 0; i < data.length; i += 64) { sum += (0.2126 * data[i] + 0.7152 * data[i + 1] + 0.0722 * data[i + 2]) / 255; n++; }
      const L = sum / Math.max(1, n);
      const light = document.body.classList.contains("theme-light");
      // Dark smoke over bright sky in dark mode; milky white in light mode.
      el.style.setProperty("--tint", light
        ? `rgba(255, 255, 255, ${Math.min(0.82, Math.max(0.5, 0.78 - L * 0.3)).toFixed(2)})`
        : `rgba(16, 10, 24, ${Math.min(0.5, Math.max(0.1, 0.12 + (L - 0.35) * 0.7)).toFixed(2)})`);
    } catch { /* not readable */ }
  }
}
setInterval(() => { if (S.route === "home") tintGlass(); }, lite ? 6000 : 1600);

/** Steve's spot: the middle of the Launch button, standing just behind it. */
function placeAnchor() {
  const play = S.route === "home" ? $("#play") : null;
  if (play && world.picture) {
    const r = layoutRect(play);
    world.feet = { x: Math.round(r.left + r.width / 2), y: Math.round(r.top + Math.min(26, r.height * 0.36)) };
  } else world.feet = null;
  const a = world.anchor();
  document.documentElement.style.setProperty("--ax", `${a.x}px`);
  document.documentElement.style.setProperty("--ay", `${a.y}px`);
  document.body.classList.toggle("picture", world.picture);
}

const UI_DEFAULTS = {
  glass_style: "frosted", radius: "round", motion: "bouncy", wall_blur: 16, wall_dim: 12, wall_custom: false,
  home_news: true, home_quick: true, home_recent: true, side_labels: true, intro: true,
  auto_update: true, servers: null, widgets: null, theme: "dark",
};

/** Popular servers shown until you add your own. */
const FEATURED_SERVERS = [
  { name: "Hypixel", address: "mc.hypixel.net" },
  { name: "Minemen Club", address: "minemen.club" },
  { name: "CubeCraft", address: "play.cubecraft.net" },
  { name: "Wynncraft", address: "play.wynncraft.com" },
  { name: "MCC Island", address: "play.mccisland.net" },
];

function applyLook(s = S.settings || {}) {
  // Fixed look: the Golden Meadow wallpaper, 40 px corner blur, 60% edge
  // shade, moving light on, frosted glass.
  s = { ...UI_DEFAULTS, ...s, wall_custom: false, wall_blur: 40, wall_dim: 60, world_life: true, glass_style: "frosted", glass: 60 };
  world.setScene(s.wall_custom && S.customWall ? "custom" : "golden");
  world.setDim(Math.min(0.6, (s.wall_dim ?? 12) / 100));
  placeAnchor();
  const b = document.body.classList;
  const light = s.theme === "light" || (s.theme === "system" && matchMedia("(prefers-color-scheme: light)").matches);
  b.toggle("theme-light", light);
  document.documentElement.style.colorScheme = light ? "light" : "dark";
  setTimeout(tintGlass, 30);
  const tt = $("#theme-toggle");
  if (tt) {
    const want = light ? "moon" : "sun";
    if (tt.dataset.ic !== want) {
      const first = !tt.dataset.ic;
      tt.dataset.ic = want;
      tt.innerHTML = icon(want, 16);
      // The sun sets and the moon swings in (or the other way round).
      if (!first && anims()) tt.firstElementChild.animate([
        { rotate: light ? "-100deg" : "120deg", scale: 0.3, opacity: 0 },
        { offset: 0.6, rotate: light ? "12deg" : "-10deg", scale: 1.15, opacity: 1 },
        { rotate: "0deg", scale: 1, opacity: 1 },
      ], { duration: 620, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)" });
    }
    tt.setAttribute("aria-label", light ? "Switch to dark mode" : "Switch to light mode");
  }
  for (const k of ["clear", "frosted", "smoke"]) b.toggle(`glass-${k}`, s.glass_style === k);
  for (const k of ["round", "soft", "sharp"]) b.toggle(`radius-${k}`, s.radius === k);
  b.toggle("compact-side", s.side_labels === false);
  setMotion(s.motion);
  document.documentElement.style.setProperty("--wall-blur", `${Math.max(0, Math.min(40, s.wall_blur ?? 16))}px`);
  $(".backdrop")?.style.setProperty("background-image", s.wall_custom && S.customWallUrl ? `url("${S.customWallUrl}")` : "");
  world.setMode(s.sky || "cycle");
  world.setDayLength(s.day_length_min || 10);
  world.setAnimate(s.animations !== false);
  world.setLife(s.world_life !== false);
  sound.setEnabled(s.ui_sounds !== false);
  fx.setEnabled(s.animations !== false);
  dust.setEnabled(s.animations !== false);
  document.body.classList.toggle("no-anim", s.animations === false);
  document.body.classList.toggle("no-player", s.show_player === false);
  const root = document.documentElement.style;
  const [acc, deep, ink] = ACCENTS[s.accent] || ACCENTS.amber;
  root.setProperty("--accent", acc);
  root.setProperty("--accent-deep", deep);
  root.setProperty("--accent-ink", ink);
  root.setProperty("--accent-rgb", [1, 3, 5].map((i) => parseInt(acc.slice(i, i + 2), 16)).join(", "));
  const glass = Number.isFinite(s.glass) ? s.glass : 60;
  root.setProperty("--blur", `${Math.round(6 + glass * 0.32)}px`);
  root.setProperty("--gk", (0.55 + (glass / 100) * 0.9).toFixed(2));
  const style = Math.min(STYLES.length - 1, Math.max(0, s.logo_style || 0));
  if (S.brandStyle !== style) {
    S.brandStyle = style;
  }
  tick();
}

async function loadSkin() {
  try {
    S.skin = await call("player_skin");
  } catch {
    S.skin = { dataUrl: null, slim: false, source: "none" };
  }
  const url = S.skin.dataUrl || defaultSkin();
  const cape = capeUrl(S.skin.cape);
  player.setSkin(url, S.skin.slim);
  player.setCape(cape);
  if (S.preview) { S.preview.setSkin(url, S.skin.slim); S.preview.setCape(cape); }
  drawHead($("#account-avatar"), url);
}

// Preview mode only: a couple of example skins for the Skins page.
globalThis.__vortexSkinSeeds = () => {
  const base = defaultSkin();
  return [["Vortex outfit", base, false]];
};

function drawHead(canvas, url) {
  const img = new Image();
  img.onload = () => {
    const g = canvas.getContext("2d");
    g.imageSmoothingEnabled = false;
    g.clearRect(0, 0, 8, 8);
    g.drawImage(img, 8, 8, 8, 8, 0, 0, 8, 8);
    g.drawImage(img, 40, 8, 8, 8, 0, 0, 8, 8);
  };
  img.src = url;
}

// ------------------------------------------------- liquid glass behaviour --

/** Slide a tab group's droplet to its active tab, stretching on the way. */
function liquidTabs(group, instant = false) {
  if (!group) return;
  const drop = group.querySelector(":scope > .drop");
  const on = group.querySelector(":scope > .tab.on");
  if (!drop || !on) { if (drop) drop.style.opacity = "0"; return; }
  drop.style.opacity = "1";
  if (group.classList.contains("vertical")) {
    // The leading edge moves first and the trailing edge follows, so the
    // droplet stretches toward the new tab and then settles.
    const top = on.offsetTop;
    const bottom = group.clientHeight - (on.offsetTop + on.offsetHeight);
    const prev = Number(drop.dataset.top ?? top);
    const down = top > prev;
    drop.style.setProperty("--dt", down ? "0.09s" : "0s");
    drop.style.setProperty("--db", down ? "0s" : "0.09s");
    if (instant || drop.dataset.top === undefined) drop.classList.add("still");
    drop.style.top = `${top}px`;
    drop.style.bottom = `${bottom}px`;
    if (drop.classList.contains("still")) { void drop.offsetWidth; drop.classList.remove("still"); }
    else if (prev !== top && anims()) {
      drop.classList.add("moving");
      setTimeout(() => drop.classList.remove("moving"), 300);
    }
    drop.dataset.top = top;
    return;
  }
  const left = on.offsetLeft;
  const right = group.clientWidth - (on.offsetLeft + on.offsetWidth);
  const prev = Number(drop.dataset.left ?? left);
  const goingRight = left > prev;
  drop.style.setProperty("--dl", goingRight ? "0.08s" : "0s");
  drop.style.setProperty("--dr", goingRight ? "0s" : "0.08s");
  if (instant || drop.dataset.left === undefined) {
    drop.classList.add("still");
    drop.style.left = `${left}px`;
    drop.style.right = `${right}px`;
    void drop.offsetWidth;
    drop.classList.remove("still");
  } else {
    drop.style.left = `${left}px`;
    drop.style.right = `${right}px`;
    if (prev !== left && anims()) {
      drop.classList.add("moving");
      setTimeout(() => drop.classList.remove("moving"), 260);
    }
  }
  drop.dataset.left = left;
}

function bindLiquid() {
  initLiquid({ enabled: anims, snap: (g) => liquidTabs(g) });
  document.addEventListener("pointerdown", (e) => {
    const el = e.target.closest(".lq, .btn, .tab, .play, .square, .row-item, .card, .loader, .vrow, .switch, .top-chip, .recent-chip, .news-card");
    if (!el || el.disabled) return;
    sound.click();
  });
  document.addEventListener("click", (e) => {
    const launch = e.target.closest('.play[data-action="play"]');
    if (launch && anims()) { shockwave(launch); dust.charge(launch, { duration: 1100 }); }
  });
  document.addEventListener("scroll", (e) => {
    const t = e.target;
    if (!(t instanceof Element) || !t.classList.contains("sheet-body")) return;
    t.closest(".sheet")?.classList.toggle("scrolled", t.scrollTop > 6);
  }, true);
  bindTilt();
  document.addEventListener("click", (e) => {
    const sw = e.target.closest(".switch");
    if (!sw || !anims()) return;
    sw.classList.remove("flick");
    void sw.offsetWidth;
    sw.classList.add("flick");
    setTimeout(() => sw.classList.remove("flick"), 450);
  });
  document.addEventListener("pointermove", (e) => {
    const el = e.target.closest(".hl");
    if (!el) return;
    const r = el.getBoundingClientRect();
    el.style.setProperty("--mx", `${e.clientX - r.left}px`);
    el.style.setProperty("--my", `${e.clientY - r.top}px`);
  });
}

/**
 * Lift a container's current content into a ghost laid exactly over it
 * and dissolve that into dust, so the container can take new content
 * right away. With `panels`, each frosted panel dissolves on its own
 * (one after another, left to right), which keeps their blur intact.
 */
function dustAway(box, { panels = false, duration = 640 } = {}) {
  const r = box.getBoundingClientRect();
  if (!r.width || !r.height) return;
  // Don't let quick clicks pile up ghosts.
  $$(".dust-ghost").slice(0, -1).forEach((g) => g.remove());
  const scrolls = $$(".sheet-body, .side-body, .list, .vlist", box).map((el) => [el, el.scrollTop]);
  const ghost = document.createElement("div");
  ghost.className = `${box.className} dust-ghost`.replace(/\b(enter|leave|fresh)\b/g, "");
  ghost.style.cssText = `position:fixed;left:${r.left}px;top:${r.top}px;width:${r.width}px;height:${r.height}px;margin:0;z-index:6;pointer-events:none`;
  ghost.setAttribute("aria-hidden", "true");
  while (box.firstChild) ghost.appendChild(box.firstChild);
  (box.closest(".app") || document.body).appendChild(ghost);
  for (const [el, top] of scrolls) el.scrollTop = top;
  let targets = [ghost];
  if (panels) {
    targets = $$(":scope > :not(.home), :scope > .home > .hero, :scope > .home > .home-side > *, :scope > .home > .home-row > *", ghost);
    targets.sort((a, b) => a.getBoundingClientRect().left - b.getBoundingClientRect().left);
  }
  const all = targets.map((t, i) => dust.dissolve(t, { duration, dir: "down", density: panels ? 0.85 : 0.7, delay: i * 55 }));
  Promise.all(all).then(() => ghost.remove());
  setTimeout(() => ghost.remove(), duration + targets.length * 55 + 600);
}

/** A pane's new content fades up into place (desktop tab switches). */
function paneIn(pane) {
  pane.animate([{ opacity: 0, translate: "0px 10px", filter: "blur(4px)" }, { opacity: 1, translate: "0px 0px", filter: "blur(0px)" }], { duration: 360, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)" });
}

/** Dissolve the card or row a control belongs to into dust. */
function vanish(el) {
  const card = el?.closest(".inst-card, .list-row, .skin-card, .card, .wz-item");
  return anims() && card ? dust.dissolve(card, { duration: 700, dir: "down" }) : Promise.resolve();
}

/** Two rings of light burst out of the Launch button. */
function shockwave(btn) {
  const r = btn.getBoundingClientRect();
  const rad = getComputedStyle(btn).borderTopLeftRadius;
  for (const [i, k] of [[0, 1], [1, 1.6]]) {
    const ring = document.createElement("span");
    ring.className = "shock";
    ring.style.cssText = `left:${r.left}px;top:${r.top}px;width:${r.width}px;height:${r.height}px;border-radius:${rad}`;
    document.body.appendChild(ring);
    const done = () => ring.remove();
    ring.animate([
      { opacity: 0.9, scale: "1 1", borderWidth: "3px" },
      { opacity: 0, scale: `${1 + 0.16 * k} ${1 + 0.6 * k}`, borderWidth: "1px" },
    ], { duration: 760 + i * 160, delay: i * 90, easing: "cubic-bezier(0.1, 0.7, 0.3, 1)", fill: "both" }).finished.then(done, done);
  }
}

const TILT = { ".inst-card": 5, ".skin-card": 7, ".cape": 10, ".scene-tile": 6, ".news-card": 5, ".hit": 1.6, ".recent-chip": 6, ".loader": 6 };
const TILT_SEL = Object.keys(TILT).join(", ");

/** Cards lean toward the pointer in 3D and a soft light follows it. */
function bindTilt() {
  let cur = null;
  const reset = (el) => {
    el.classList.remove("tilting");
    el.style.transform = "";
    el.querySelector(":scope > .tilt-light")?.style.setProperty("opacity", "0");
  };
  document.addEventListener("pointermove", (e) => {
    if (e.pointerType !== "mouse") return;
    const el = anims() ? e.target.closest(TILT_SEL) : null;
    if (cur && cur !== el) { reset(cur); cur = null; }
    if (!el || el.disabled) return;
    cur = el;
    const r = el.getBoundingClientRect();
    const px = (e.clientX - r.left) / r.width - 0.5, py = (e.clientY - r.top) / r.height - 0.5;
    const max = TILT[Object.keys(TILT).find((k) => el.matches(k))] || 4;
    el.classList.add("tilting");
    el.style.transform = `perspective(900px) translateY(-2px) rotateX(${(-py * max).toFixed(2)}deg) rotateY(${(px * max).toFixed(2)}deg)`;
    let light = el.querySelector(":scope > .tilt-light");
    if (!light) {
      light = document.createElement("span");
      light.className = "tilt-light";
      if (getComputedStyle(el).position === "static") el.style.position = "relative";
      el.appendChild(light);
    }
    light.style.opacity = "1";
    light.style.setProperty("--lx", `${((px + 0.5) * 100).toFixed(1)}%`);
    light.style.setProperty("--ly", `${((py + 0.5) * 100).toFixed(1)}%`);
  }, { passive: true });
  document.addEventListener("pointerleave", () => { if (cur) { reset(cur); cur = null; } });
}

// ---------------------------------------------------------------- routing --

function go(route, opts = {}) {
  if (!ROUTES[route]) route = "home";
  const same = route === S.route;
  S.route = route;
  S.switcher = false;
  closePopover();
  const navRoute = NAV_OF[route] || route;
  const wasOn = $("#nav .tab.on");
  $$("#nav .tab").forEach((b) => b.classList.toggle("on", b.dataset.route === navRoute));
  liquidTabs($("#nav"));
  const nowOn = $("#nav .tab.on");
  if (nowOn && nowOn !== wasOn && !opts.silent) popTab(nowOn);
  // Pages further down the sidebar rise from below; ones above drop in.
  const tabs = $$("#nav .tab");
  const dir = wasOn && nowOn ? Math.sign(tabs.indexOf(nowOn) - tabs.indexOf(wasOn)) : 0;
  $("#page").style.setProperty("--dir", String(dir || 1));
  document.body.classList.toggle("sheet-open", route !== "home");
  if (route !== "home") { document.body.classList.remove("hero-on"); world.setPaused(true); }
  if (!same && !opts.silent) sound.nav();
  const page = $("#page");
  // New pages grow out of the sidebar tab that opened them.
  const tabEl = $(`#nav .tab[data-route="${navRoute}"]`);
  if (tabEl) {
    const pr = page.getBoundingClientRect(), tr = tabEl.getBoundingClientRect();
    page.style.setProperty("--ox", `${Math.round(tr.left + tr.width / 2 - pr.left)}px`);
    page.style.setProperty("--oy", `${Math.round(tr.top + tr.height / 2 - pr.top)}px`);
  }
  page.classList.remove("leave");
  // On phones the page you leave breaks up into dust while the new one
  // rises underneath it; on desktop the new page simply rises in.
  const swept = !same && anims() && page.children.length && phone();
  if (swept) dustAway(page, { panels: true });
  page.classList.toggle("after-dust", !!swept);
  render({ enter: !same });
  placeNav();
}

/** A sidebar tab just became active: its icon plays its own little move
 *  and a few pixels spark off it. */
function popTab(tab) {
  if (!anims()) return;
  tab.classList.remove("pop");
  void tab.offsetWidth;
  tab.classList.add("pop");
  clearTimeout(tab._pop);
  tab._pop = setTimeout(() => tab.classList.remove("pop"), 950);
  const slot = tab.querySelector(".icon-slot");
  if (!slot) return;
  slot.querySelectorAll(".spark").forEach((o) => o.remove());
  const colors = ["var(--accent)", "#fff3d6", "var(--accent-deep)"];
  for (let i = 0; i < 7; i++) {
    const sp = document.createElement("i");
    sp.className = "spark";
    const a = (i / 7) * Math.PI * 2 + Math.random() * 0.6;
    const r = 16 + Math.random() * 9;
    const sz = Math.random() > 0.6 ? 3 : 2;
    sp.style.cssText = `width:${sz}px;height:${sz}px;background:${colors[i % 3]}`;
    slot.appendChild(sp);
    const done = () => sp.remove();
    const at = (k) => `${(Math.cos(a) * k).toFixed(1)}px ${(Math.sin(a) * k).toFixed(1)}px`;
    sp.animate([
      { translate: at(9), opacity: 0, scale: 1 },
      { offset: 0.25, opacity: 1 },
      { translate: at(r), opacity: 0, scale: 0.4 },
    ], { duration: 520 + Math.random() * 160, delay: 90, easing: "cubic-bezier(0.15, 0.8, 0.3, 1)", fill: "both" }).finished.then(done, done);
  }
}

function render(opts = {}) {
  const page = $("#page");
  // Same page, new data: update in place so tabs slide and nothing flashes.
  if (!opts.enter && !opts.full) {
    if (S.route === "mods" && $(".mods-pane", page)) return updateMods();
    if (S.route === "new" && $(".wiz", page)) return updateWizard();
  }
  if (S.preview && S.route !== "accounts") S.preview = null;
  const { html, mount } = ROUTES[S.route]();
  page.innerHTML = html;
  [...page.children].forEach((el, i) => el.style.setProperty("--i", i));
  if (opts.enter) {
    page.classList.remove("enter");
    void page.offsetWidth;
    if (anims()) page.classList.add("enter");
  }
  hydrateIcons(page);
  $$("canvas[data-avatar]", page).forEach((c) => drawAvatar(c, c.dataset.avatar));
  $$("canvas[data-head]", page).forEach((c) => drawHead(c, S.skin?.dataUrl || defaultSkin()));
  $$(".tabs", page).forEach((g) => liquidTabs(g, true));
  mount?.(page);
  if (opts.enter && anims()) cascade(page);
}

const CASCADE = ".sheet-head .row > *, .tabs.glass, .card, .hit, .skin-card, .cape, .scene-tile, .setting, .card-pad, .list-row, .srv, .qa, .w-account, .wcard, .recent-chip, .hero-chip, .loader, .vrow, .empty, .settings-pane > *, .skins-left, .acc-preview";

/** The page's pieces arrive one after another, top to bottom, instead of
 *  all at once: the title wipes up, then cards ripple in. */
function cascade(page) {
  const h1 = $(".sheet-head h1", page);
  if (h1) {
    h1.animate([{ clipPath: "inset(0 0 100% 0)", translate: "0 14px", opacity: 0 }, { clipPath: "inset(0 0 -20% 0)", translate: "0 0", opacity: 1 }],
      { duration: 620, delay: 90, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)", fill: "backwards" });
    const sub = $(".sheet-head p", page);
    sub?.animate([{ opacity: 0, translate: "0 6px", filter: "blur(4px)" }, { opacity: 1, translate: "0 0", filter: "blur(0px)" }],
      { duration: 520, delay: 190, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)", fill: "backwards" });
  }
  const vh = window.innerHeight;
  const seen = new Set();
  const items = $$(CASCADE, page).filter((el) => {
    if ([...seen].some((p) => p.contains(el))) return false;
    const r = el.getBoundingClientRect();
    if (!r.width || r.top > vh || r.bottom < 0) return false;
    seen.add(el);
    return true;
  }).sort((a, b) => {
    const ra = a.getBoundingClientRect(), rb = b.getBoundingClientRect();
    return (ra.top - rb.top) * 2 + (ra.left - rb.left) * 0.6;
  }).slice(0, 28);
  items.forEach((el, i) => {
    el.animate([
      { opacity: 0, translate: "0 18px", scale: "0.96", filter: "blur(6px)" },
      { offset: 0.6, opacity: 1, translate: "0 -2px", scale: "1.005", filter: "blur(0px)" },
      { opacity: 1, translate: "0 0", scale: "1", filter: "blur(0px)" },
    ], { duration: 640, delay: 150 + i * 32, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)", fill: "backwards" });
  });
}

const sheet = (title, sub, actions, body, extra = "") => `
  <section class="glass strong sheet hl">
    <header class="sheet-head"><div><h1>${title}</h1>${sub ? `<p>${sub}</p>` : ""}</div><div class="row">${actions || ""}</div></header>
    <div class="sheet-body">${body}</div>
    ${extra}
  </section>`;

// ------------------------------------------------------------------- home --

function playButton(inst) {
  const r = runOf(inst.name);
  // Name, plus the version and loader only when the name doesn't already say them.
  const parts = [inst.name];
  const lower = inst.name.toLowerCase();
  if (!lower.includes(inst.minecraftVersion.toLowerCase())) parts.push(inst.minecraftVersion);
  if (inst.loader !== "vanilla" && !lower.includes(inst.loader)) parts.push(inst.loaderLabel);
  const label = esc(parts.join(" · "));
  if (r.phase === "preparing") {
    const pct = r.total ? Math.round((r.done / r.total) * 100) : 0;
    return `<button class="play busy lq" id="play" data-action="stop" data-name="${esc(inst.name)}" aria-label="Cancel launch">
      <span class="play-fill" style="width:${pct}%"></span>
      <span class="play-label">${esc(r.stage || "Preparing")}</span>
      <span class="play-sub">${r.total ? `${pct}% · ` : ""}Click to cancel</span></button>`;
  }
  if (r.phase === "running") {
    const t = S.started[inst.name] ? clock(Date.now() - S.started[inst.name]) : "";
    return `<button class="play running lq" id="play" data-action="stop" data-name="${esc(inst.name)}" aria-label="Stop the game">
      <span class="play-label"><span class="run-text"><span class="live-dot" aria-hidden="true"></span>PLAYING</span><span class="stop-text"><span class="stop-glyph" aria-hidden="true"></span>STOP GAME</span></span>
      <span class="play-sub">${esc(inst.name)}${t ? ` · ${t}` : ""}</span></button>`;
  }
  return `<button class="play lq" id="play" data-action="play" data-name="${esc(inst.name)}">
    <span class="play-label">LAUNCH GAME</span>
    <span class="play-sub">${label}</span></button>`;
}

function sideBody() {
  if (S.side === "news") {
    if (!S.news) return `<div class="side-note">Loading patch notes from Mojang.</div>`;
    if (!S.news.length) return `<div class="side-note">Patch notes are unavailable offline.</div>`;
    return `<div class="tabpane">${S.news.map((n, i) => `<button class="row-item" data-action="news" data-idx="${i}">
      <span class="thumb" ${n.image_url ? `style="background-image:url('${esc(n.image_url)}')"` : ""}>${n.image_url ? "" : icon("sun", 18)}</span>
      <span class="meta"><strong>${esc(n.title)}</strong><span>${esc(n.kind === "snapshot" ? "Snapshot" : "Release")} · ${esc(n.date)}</span></span></button>`).join("")}</div>`;
  }
  if (!S.instances.length) return `<div class="side-note">No instances yet. Create one to choose a version.</div>`;
  const list = [...S.instances].sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0));
  return `<div class="tabpane">${list.map((i) => {
    const live = runOf(i.name).phase !== "idle";
    return `<button class="row-item ${i.name === S.selected ? "on" : ""}" data-action="select" data-name="${esc(i.name)}">
      ${coverThumb(i)}
      <span class="meta"><strong>${esc(i.name)}</strong><span>${esc(i.minecraftVersion)} · ${esc(i.loaderLabel)} · ${esc(ago(i.lastPlayed))}</span></span>
      ${live ? `<span class="live-dot" title="Running"></span>` : ""}</button>`;
  }).join("")}</div>`;
}

function switcherHtml(inst) {
  return `<div class="switcher glass" id="switcher" role="menu"><span class="grabber" aria-hidden="true"></span>
    <div class="menu-head"><span>Instances</span><span class="muted">${S.instances.length}</span></div>
    ${[...S.instances].sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0)).map((i) => `<button class="row-item ${i.name === inst.name ? "on" : ""}" data-action="select" data-name="${esc(i.name)}" role="menuitem">${coverThumb(i)}<span class="meta"><strong>${esc(i.name)}</strong><span>${esc(i.minecraftVersion)} · ${esc(i.loaderLabel)}</span></span>${i.name === inst.name ? `<span class="tick">${icon("check", 12)}</span>` : ""}</button>`).join("")}
    <button class="row-item" data-action="route" data-route="new" role="menuitem"><span class="cover-thumb add">${icon("plus", 16)}</span><span class="meta"><strong>New instance</strong><span>Any version, Vanilla, Fabric or Forge</span></span></button></div>`;
}

function toggleSwitcher(source) {
  if ($("#switcher")) return closeSwitcher();
  const inst = selectedInstance();
  const zone = $(".play-zone");
  if (!inst || !zone) return;
  S.switcher = true;
  zone.insertAdjacentHTML("afterbegin", switcherHtml(inst));
  const panel = $("#switcher");
  hydrateIcons(panel);
  // Fit inside the wallpaper card, above the Launch button.
  panel.style.maxHeight = `${Math.max(200, zone.offsetTop - 12 - 16)}px`;
  $$("[data-action='toggle-switcher']").forEach((b) => b.setAttribute("aria-expanded", "true"));
  morphIn(panel, source);
  swipeToDismiss(panel, () => closeSwitcher(true), { grip: ".grabber, .menu-head" });
}

function closeSwitcher(instant = false, then) {
  S.switcher = false;
  const panel = $("#switcher");
  $$("[data-action='toggle-switcher']").forEach((b) => b.setAttribute("aria-expanded", "false"));
  if (!panel) { then?.(); return; }
  panel.id = "";
  const done = () => { panel.remove(); then?.(); };
  if (instant || !anims()) done();
  else dust.dissolve(panel, { duration: 460, dir: "down", density: 0.8 }).then(done);
}

const newsVersion = (n) => n.version || (String(n.title || "").match(/(\d+(?:\.\d+)+(?:[-\s]?(?:snapshot|pre|rc)[-\s]?\d+)?|\d\dw\d\d[a-z])/i)?.[1] || "").replace(/\s+/g, "-").toLowerCase();

function newsCards() {
  const news = (S.news || []).slice(0, 3);
  if (!news.length) return `<div class="news-empty glass">${icon("sun", 18)}<span>${S.news ? "Patch notes are unavailable offline." : "Loading patch notes from Mojang."}</span></div>`;
  return news.map((n, i) => {
    const img = n.image_url || cover(newsVersion(n) || "latest");
    return `<button class="news-card" data-action="news" data-idx="${i}">
      <span class="news-art" style="background-image:url('${esc(img)}')"></span>
      <span class="news-tag ${n.kind === "snapshot" ? "snap" : ""}">${n.kind === "snapshot" ? "Snapshot" : "Release"}</span>
      <span class="cap"><strong>${esc(n.title)}</strong><span>${esc(n.date)}</span></span></button>`;
  }).join("");
}

/** "Fabric 1.21.4", minus whatever the instance name already says. */
function chipSub(i) {
  const loader = i.loaderLabel.split(" ")[0];
  const n = i.name.toLowerCase();
  const parts = [];
  if (!n.includes(loader.toLowerCase())) parts.push(loader);
  if (!n.includes(i.minecraftVersion.toLowerCase())) parts.push(i.minecraftVersion);
  return parts.join(" ") || updateName(i.minecraftVersion) || i.loaderLabel;
}

// ---------------------------------------------------------------- servers --

const serverList = () => {
  const own = (S.settings || {}).servers;
  return Array.isArray(own) && own.length ? own : FEATURED_SERVERS;
};
S.serverStatus = {};

function serverRow(sv) {
  const st = S.serverStatus[sv.address];
  const letter = esc((sv.name || sv.address).trim()[0]?.toUpperCase() || "?");
  const icon_ = st?.icon ? `<img class="srv-icon" src="${esc(st.icon)}" alt="">` : `<span class="srv-icon blank">${letter}</span>`;
  const status = !st ? `<span class="srv-count muted"><span class="spin"></span></span>`
    : st.error ? `<span class="srv-count off">Offline</span>`
    : `<span class="srv-count"><i class="dot"></i><b class="n">${fmtCount(st.online)}</b></span>`;
  return `<div class="srv" data-address="${esc(sv.address)}" title="${esc(st?.motd || sv.address)}">
    ${icon_}
    <span class="meta"><strong>${esc(sv.name || sv.address)}</strong><span>${esc(sv.address)}${st && !st.error && st.pingMs ? ` · ${st.pingMs} ms` : ""}</span></span>
    ${status}
    <button class="btn btn-sm srv-join" data-action="join-server" data-address="${esc(sv.address)}" aria-label="Join ${esc(sv.name || sv.address)}">Join</button>
  </div>`;
}

function serversBody() {
  return serverList().map(serverRow).join("");
}

/** Ask each server for its status (cached for a minute). */
function refreshServers(force = false) {
  for (const sv of serverList()) {
    const st = S.serverStatus[sv.address];
    if (!force && st && Date.now() - st.at < 60000) continue;
    call("server_status", { address: sv.address })
      .then((r) => { S.serverStatus[sv.address] = { ...r, at: Date.now() }; })
      .catch((e) => { S.serverStatus[sv.address] = { error: errText(e), at: Date.now() }; })
      .finally(() => updateServerRow(sv));
  }
}

const countedServers = new Set();

/** Swap in one server's row (the others keep their hover state), and
 *  count its players up the first time they arrive. */
function updateServerRow(sv) {
  const body = $("#servers-body");
  if (!body) return;
  const old = [...body.querySelectorAll(":scope > .srv")].find((r) => r.dataset.address === sv.address);
  const tmp = document.createElement("div");
  tmp.innerHTML = serverRow(sv);
  const row = tmp.firstElementChild;
  hydrateIcons(row);
  if (old) old.replaceWith(row); else { body.innerHTML = serversBody(); hydrateIcons(body); return; }
  const st = S.serverStatus[sv.address];
  const n = row.querySelector(".srv-count .n");
  if (!n || countedServers.has(sv.address)) return;
  countedServers.add(sv.address);
  if (!anims()) return;
  row.querySelector(".srv-count").classList.add("arrive");
  const t0 = performance.now(), total = st.online || 0;
  const tick = (now) => {
    if (!n.isConnected) return;
    const k = Math.min(1, (now - t0) / 900);
    n.textContent = fmtCount(Math.round(total * (1 - Math.pow(1 - k, 3))));
    if (k < 1) requestAnimationFrame(tick);
  };
  requestAnimationFrame(tick);
}

function addServerModal() {
  modal(`<h3>Add a server</h3><p>It shows on Home with its players online, and Join starts your selected instance straight into it.</p>
    <div class="field"><label class="label" for="sv-name">Name</label><input class="input" id="sv-name" placeholder="My SMP"></div>
    <div class="field"><label class="label" for="sv-addr">Address</label><input class="input" id="sv-addr" placeholder="play.example.net"></div>
    <div class="modal-actions"><button class="btn" id="sv-cancel">Cancel</button><button class="btn btn-accent" id="sv-save">${icon("plus", 12)} Add</button></div>`, (root) => {
    $("#sv-cancel", root).onclick = () => closeModal();
    $("#sv-save", root).onclick = async () => {
      const address = $("#sv-addr", root).value.trim();
      if (!/^[\w.-]+(:\d{1,5})?$/.test(address)) { toast("Check the address", "Like play.example.net or play.example.net:25570", "warn"); return; }
      const name = $("#sv-name", root).value.trim() || address;
      const own = Array.isArray(S.settings.servers) && S.settings.servers.length ? S.settings.servers : [];
      await saveSettings({ ...S.settings, servers: [{ name, address }, ...own.filter((x) => x.address !== address)].slice(0, 12) });
      closeModal();
      sound.success();
      if (S.route === "home") render();
    };
  });
}

// ---------------------------------------------------------------- widgets --
// Home is built from widgets in two places: the side column and the row
// under the wallpaper. Which ones show, where and in what order is yours.

const WIDGETS = {
  account: { name: "Account", desc: "Who's playing, with Switch", zones: ["side"] },
  servers: { name: "Servers", desc: "Players online, ping and Join", zones: ["side"] },
  instances: { name: "Instances", desc: "Your library, most recent first", zones: ["side"] },
  current: { name: "Selected instance", desc: "Version, loader, mods, memory", zones: ["side", "row"] },
  wardrobe: { name: "Wardrobe", desc: "Your skin and cape", zones: ["side", "row"] },
  quick: { name: "Quick actions", desc: "Mods, Skins, New, Folder", zones: ["row", "side"] },
};
const DEFAULT_LAYOUT = { side: ["account", "servers", "quick"], row: [] };

/** The Home layout, from settings (older settings map onto it). */
function homeLayout(u = { ...UI_DEFAULTS, ...(S.settings || {}) }) {
  return {
    side: DEFAULT_LAYOUT.side.filter((id) => id !== "quick" || u.home_quick !== false),
    row: [...DEFAULT_LAYOUT.row],
  };
}

function widgetHtml(id, zone, inst, acc) {
  if (id === "account") return `<div class="acct glass hl w-account">
      <canvas width="8" height="8" data-head="1"></canvas>
      <div class="meta"><strong>${esc(acc?.username || "No account")}</strong><span>${acc ? (acc.kind === "microsoft" ? "Microsoft account" : "Offline account") : "Add one to play"}</span></div>
      <button class="btn btn-sm" data-action="route" data-route="accounts">${acc ? "Switch" : "Add"}</button></div>`;
  if (id === "servers") return `<div class="lib glass hl w-grow">
      <div class="side-head"><h2>Servers</h2><button class="btn btn-sm" data-action="add-server" aria-label="Add a server">${icon("plus", 12)} Add</button></div>
      <div class="side-body servers" id="servers-body">${serversBody()}</div></div>`;
  if (id === "instances") return `<div class="lib glass hl w-grow">
      <div class="side-head"><h2>Instances</h2><button class="btn btn-sm" data-action="route" data-route="new">${icon("plus", 12)} New</button></div>
      <div class="side-body" id="side-body">${(S.side = "instances", sideBody())}</div></div>`;
  if (id === "current") {
    const body = inst ? `<div class="cur-top">${coverThumb(inst)}<div class="meta"><strong>${esc(inst.name)}</strong><span>${esc(inst.minecraftVersion)}${updateName(inst.minecraftVersion) ? ` · ${esc(updateName(inst.minecraftVersion))}` : ""}</span></div></div>
        <dl class="cur-facts"><div><dt>Loader</dt><dd>${esc(inst.loaderLabel)}</dd></div><div><dt>Mods</dt><dd>${inst.loader === "vanilla" ? "None" : esc(String(inst.modCount ?? 0))}</dd></div><div><dt>Memory</dt><dd>${gb(inst.memoryMb || S.settings?.max_memory_mb || 4096)}</dd></div><div><dt>Played</dt><dd>${esc(ago(inst.lastPlayed).replace(/^Played /, ""))}</dd></div></dl>
        <div class="row"><button class="btn btn-sm" data-action="route" data-route="mods">${icon("puzzle", 12)} Mods</button><button class="btn btn-sm" data-action="folder" data-name="${esc(inst.name)}">${icon("folder", 12)} Folder</button></div>`
      : `<p class="muted small">No instance yet.</p>`;
    return zone === "row"
      ? `<div class="hblock w-current"><div class="block-head"><h2>Selected instance</h2></div><div class="glass hl wcard">${body}</div></div>`
      : `<div class="glass hl wcard w-current"><div class="side-head flat"><h2>Selected instance</h2></div>${body}</div>`;
  }
  if (id === "wardrobe") {
    const cape = S.skin?.cape && S.skin.cape !== "none" ? CAPE_NAMES[S.skin.cape] || S.skin.cape : "No cape";
    const body = `<div class="ward"><canvas width="8" height="8" data-head="1"></canvas><div class="meta"><strong>${esc(S.skin?.source === "chosen" ? "Your skin" : "Default skin")}</strong><span>${esc(cape)} · ${S.skin?.slim ? "Slim" : "Classic"}</span></div><button class="btn btn-sm" data-action="route" data-route="skins">Change</button></div>`;
    return zone === "row"
      ? `<div class="hblock w-wardrobe"><div class="block-head"><h2>Wardrobe</h2></div><div class="glass hl wcard">${body}</div></div>`
      : `<div class="glass hl wcard w-wardrobe"><div class="side-head flat"><h2>Wardrobe</h2></div>${body}</div>`;
  }
  if (id === "news") return `<div class="hblock news-block"><div class="block-head"><h2>Latest news</h2><span class="muted small">From Mojang</span></div><div class="news-cards" id="news-cards">${newsCards()}</div></div>`;
  if (id === "quick") {
    const grid = `<div class="quick glass hl">
        <button class="qa" data-action="route" data-route="mods"><span class="qa-ico">${icon("puzzle", 16)}</span><span>Mods</span></button>
        ${isAndroid ? `<button class="qa" data-action="native" data-what="controls"><span class="qa-ico">${icon("edit", 16)}</span><span>Controls</span></button>` : `<button class="qa" data-action="route" data-route="skins"><span class="qa-ico">${icon("shirt", 16)}</span><span>Skins</span></button>`}
        <button class="qa" data-action="route" data-route="new"><span class="qa-ico">${icon("plus", 16)}</span><span>New</span></button>
        <button class="qa" data-action="folder" data-name="${esc(inst?.name || "")}" ${inst ? "" : "disabled"}><span class="qa-ico">${icon("folder", 16)}</span><span>Folder</span></button></div>`;
    return zone === "row" ? `<div class="hblock quick-block"><div class="block-head"><h2>Quick actions</h2></div>${grid}</div>` : `<div class="quick-side">${grid}</div>`;
  }
  return "";
}

/** Columns for the row: news stretches, everything else is a fixed width. */
const rowColumns = (ids) => ids.map(() => "minmax(0, 1fr)").join(" ") || "minmax(0, 1fr)";

async function saveLayout(layout) {
  const next = { ...S.settings, widgets: layout };
  if (S.draft) S.draft.widgets = layout;
  await saveSettings(next);
  if (S.route === "home") render();
}

function customizeModal() {
  const draw = () => {
    const L = homeLayout();
    const used = new Set([...L.side, ...L.row]);
    const zoneList = (zone) => L[zone].map((id, i) => `<li class="wz-item">
        <span class="wz-name"><strong>${esc(WIDGETS[id].name)}</strong><span>${esc(WIDGETS[id].desc)}</span></span>
        <span class="wz-actions">
          ${WIDGETS[id].zones.length > 1 ? `<button class="btn btn-sm btn-icon" data-wz="move" data-id="${id}" aria-label="Move ${esc(WIDGETS[id].name)} to the ${zone === "side" ? "row" : "side column"}" title="Move to the ${zone === "side" ? "row" : "side"}">⇄</button>` : ""}
          <button class="btn btn-sm btn-icon" data-wz="up" data-id="${id}" data-zone="${zone}" aria-label="Move ${esc(WIDGETS[id].name)} up" ${i === 0 ? "disabled" : ""}>↑</button>
          <button class="btn btn-sm btn-icon" data-wz="down" data-id="${id}" data-zone="${zone}" aria-label="Move ${esc(WIDGETS[id].name)} down" ${i === L[zone].length - 1 ? "disabled" : ""}>↓</button>
          <button class="btn btn-sm btn-icon" data-wz="remove" data-id="${id}" aria-label="Remove ${esc(WIDGETS[id].name)}">${icon("x", 11)}</button>
        </span></li>`).join("") || `<li class="wz-empty">Empty. Add a widget below.</li>`;
    const spare = Object.keys(WIDGETS).filter((id) => !used.has(id));
    return `<h3>Customize Home</h3><p class="muted small">Pick the widgets you want, where they go and in what order. Changes apply right away.</p>
      <div class="wz-zones">
        <section><h4>Side column</h4><ul class="wz-list">${zoneList("side")}</ul></section>
        <section><h4>Under the wallpaper</h4><ul class="wz-list">${zoneList("row")}</ul></section>
      </div>
      ${spare.length ? `<section><h4>Add a widget</h4><div class="wz-add">${spare.map((id) => `<div class="wz-spare"><span class="wz-name"><strong>${esc(WIDGETS[id].name)}</strong><span>${esc(WIDGETS[id].desc)}</span></span>
        ${WIDGETS[id].zones.map((z) => `<button class="btn btn-sm" data-wz="add" data-id="${id}" data-zone="${z}">${icon("plus", 11)} ${z === "side" ? "Side" : "Row"}</button>`).join("")}</div>`).join("")}</div></section>` : ""}
      <div class="modal-actions"><button class="btn btn-ghost" data-wz="reset">Reset to default</button><span class="grow"></span><button class="btn btn-accent" data-wz="done">Done</button></div>`;
  };
  modal(`<div class="wz" id="wz">${draw()}</div>`, (root) => {
    const box = $("#wz", root);
    hydrateIcons(box);
    box.addEventListener("click", async (e) => {
      const b = e.target.closest("[data-wz]");
      if (!b || b.disabled) return;
      const op = b.dataset.wz, id = b.dataset.id;
      if (op === "done") { closeModal(); return; }
      const L = homeLayout();
      const zoneOf = (x) => (L.side.includes(x) ? "side" : L.row.includes(x) ? "row" : null);
      if (op === "reset") Object.assign(L, JSON.parse(JSON.stringify(DEFAULT_LAYOUT)));
      if (op === "remove") { const z = zoneOf(id); L[z] = L[z].filter((x) => x !== id); }
      if (op === "add") L[b.dataset.zone].push(id);
      if (op === "move") { const z = zoneOf(id), o = z === "side" ? "row" : "side"; L[z] = L[z].filter((x) => x !== id); L[o].push(id); }
      if (op === "up" || op === "down") {
        const z = b.dataset.zone, i = L[z].indexOf(id), j = op === "up" ? i - 1 : i + 1;
        if (j >= 0 && j < L[z].length) [L[z][i], L[z][j]] = [L[z][j], L[z][i]];
      }
      sound.pop();
      await saveLayout(L);
      box.innerHTML = draw();
      hydrateIcons(box);
    });
  });
}

function renderHome() {
  const inst = selectedInstance();
  const acc = selectedAccount();
  const u = { ...UI_DEFAULTS, ...(S.settings || {}) };
  const L = homeLayout(u);
  S.side = "servers";
  S.switcher = false;
  const recent = [...S.instances].filter((i) => !inst || i.name !== inst.name).sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0)).slice(0, 3);
  const cluster = inst
    ? `<div class="play-cluster split">${playButton(inst)}<button class="play-more" data-action="toggle-switcher" aria-label="Choose instance" aria-haspopup="menu" aria-expanded="false"><span class="chev">${icon("chev", 12)}</span></button></div>`
    : `<div class="play-cluster"><button class="play" id="play" data-action="route" data-route="new"><span class="play-label">${icon("plus", 20)} CREATE</span><span class="play-sub">Pick any Minecraft version to start</span></button></div>`;
  const chips = u.home_recent ? recent.map((i) => `<button class="recent-chip glass glassy hl" data-action="play" data-name="${esc(i.name)}" title="Play ${esc(i.name)}">
      ${coverThumb(i, "sm")}<span class="meta"><strong>${esc(i.name)}</strong><span>${esc(chipSub(i))}</span></span><span class="go">${icon("play", 12)}</span></button>`).join("") : "";
  const fast = inst && S.fast[inst.name];
  const side = L.side.map((id) => widgetHtml(id, "side", inst, acc)).join("");
  const row = L.row.map((id) => widgetHtml(id, "row", inst, acc)).join("");
  return {
    html: `<div class="home ${L.row.length ? "" : "no-row"} ${L.side.length ? "" : "no-side"}">
      <section class="hero" id="hero" aria-label="Play">
        <div class="hero-fx" aria-hidden="true"></div>
        <div class="hero-top">
          ${inst ? `<span class="hero-chip glass glassy">${coverThumb(inst, "xs")}<span>${esc(inst.minecraftVersion)}${updateName(inst.minecraftVersion) ? ` · ${esc(updateName(inst.minecraftVersion))}` : ""}</span></span>` : ""}
          ${fast ? `<span class="hero-chip glass glassy fast" title="Files are ready: the game starts without checking or downloading anything">${icon("bolt", 12)}<span>Instant launch</span></span>` : ""}
          <span class="grow"></span>

        </div>
        <div class="play-zone">
          ${cluster}
          ${chips ? `<div class="recent">${chips}</div>` : ""}
        </div>
      </section>
      ${L.row.length ? `<section class="home-row" style="grid-template-columns:${rowColumns(L.row)}">${row}</section>` : ""}
      ${L.side.length ? `<aside class="home-side">${side}</aside>` : ""}
    </div>`,
    mount() {
      requestAnimationFrame(placeHero);
      setTimeout(placeHero, 700);
      if (L.side.includes("servers")) refreshServers();
    },
  };
}

/** Refresh the live parts without re-rendering (keeps animations smooth). */
function updateRunUI(name) {
  const inst = selectedInstance();
  if (inst && inst.name === name) {
    const r = runOf(name);
    player.setActivity(r.phase);
    if (S.route === "home") {
      const btn = $("#play");
      if (btn) {
        const wasPhase = btn.classList.contains("busy") ? "preparing" : btn.classList.contains("running") ? "running" : "idle";
        if (wasPhase === r.phase && r.phase === "preparing") {
          const pct = r.total ? Math.round((r.done / r.total) * 100) : 0;
          btn.querySelector(".play-fill").style.width = `${pct}%`;
          const lab = btn.querySelector(".play-label");
          const stage = r.stage || "Preparing";
          if (lab.textContent !== stage) {
            lab.textContent = stage;
            if (anims()) lab.animate([{ opacity: 0, translate: "0 10px", filter: "blur(3px)" }, { opacity: 1, translate: "0 0", filter: "blur(0px)" }], { duration: 360, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)" });
          }
          btn.querySelector(".play-sub").textContent = `${r.total ? `${pct}% · ` : ""}Click to cancel`;
        } else {
          btn.outerHTML = playButton(inst);
          const nb = $("#play");
          if (nb && r.phase === "running" && wasPhase !== "running") {
            const b = nb.getBoundingClientRect();
            dust.burst(b.left + b.width / 2, b.top + b.height / 2, { count: 320, colors: ["rgba(180,242,143,1)", "rgba(134,220,92,1)", "rgba(255,181,71,1)"] });
          }
          if (nb && anims()) {
            nb.animate([{ scale: "0.94 0.9" }, { offset: 0.45, scale: "1.03 1.05" }, { offset: 0.75, scale: "0.995 0.99" }, { scale: "1 1" }], { duration: 620, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)" });
            for (const [i, el] of [...nb.querySelectorAll(".play-label, .play-sub")].entries()) {
              el.animate([{ opacity: 0, translate: "0 14px", filter: "blur(4px)" }, { opacity: 1, translate: "0 0", filter: "blur(0px)" }], { duration: 460, delay: 40 + i * 60, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)", fill: "backwards" });
            }
          }
        }
      }
    }
  }
  // The library only changes when a game starts or stops, not on progress.
  const phase = runOf(name).phase;
  if (S.lastPhase?.[name] !== phase) {
    (S.lastPhase ||= {})[name] = phase;
    if (S.route === "home" && S.side === "instances") {
      const body = $("#side-body");
      if (body) { body.innerHTML = sideBody(); hydrateIcons(body); }
    }
  }
}

// -------------------------------------------------------------- instances --

function renderInstances() {
  const q = S.instSearch.toLowerCase();
  const list = S.instances.filter((i) => !q || `${i.name} ${i.minecraftVersion} ${i.loader}`.toLowerCase().includes(q));
  if (S.instSort === "recent") list.sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0));
  if (S.instSort === "name") list.sort((a, b) => a.name.localeCompare(b.name));
  if (S.instSort === "version") list.sort((a, b) => b.minecraftVersion.localeCompare(a.minecraftVersion, undefined, { numeric: true }));
  const cards = list.map((i) => {
    const r = runOf(i.name);
    const kind = S.versions?.versions.find((v) => v.id === i.minecraftVersion)?.kind || "release";
    return `<div class="card inst-card ${i.name === S.selected ? "on" : ""}" data-action="select-home" data-name="${esc(i.name)}" role="button" tabindex="0">
      <div class="inst-cover" style="background-image:url('${esc(cover(i.minecraftVersion))}')">
        <span class="inst-update">${esc(updateName(i.minecraftVersion))}</span>
        ${r.phase !== "idle" ? `<span class="chip live"><span class="live-dot"></span>${r.phase === "running" ? "Playing" : "Starting"}</span>` : ""}
        <button class="inst-play lq" data-action="play" data-name="${esc(i.name)}" aria-label="Play ${esc(i.name)}">${icon("play", 16)}</button>
      </div>
      <div class="inst-body">
        <div class="card-name">${esc(i.name)}</div>
        <div class="inst-meta">
          <span class="chip"><span class="kind ${kind}">${esc(i.minecraftVersion)}</span></span>
          <span class="chip">${i.loader !== "vanilla" ? icon(loaderIcon(i.loader), 11) : ""}${esc(i.loaderLabel)}</span>
          ${i.modCount ? `<span class="chip">${i.modCount} mods</span>` : ""}
        </div>
        <div class="card-actions">
          <span class="card-sub">${esc(ago(i.lastPlayed))}</span>
          <button class="btn btn-sm btn-icon btn-ghost lq" data-action="edit" data-name="${esc(i.name)}" aria-label="Edit ${esc(i.name)}">${icon("edit", 12)}</button>
          <button class="btn btn-sm btn-icon btn-ghost lq" data-action="folder" data-name="${esc(i.name)}" aria-label="Open folder">${icon("folder", 12)}</button>
          <button class="btn btn-sm btn-icon btn-ghost btn-danger lq" data-action="delete" data-name="${esc(i.name)}" aria-label="Delete ${esc(i.name)}">${icon("trash", 12)}</button>
        </div>
      </div></div>`;
  }).join("");
  return {
    html: sheet(
      "Instances",
      "Each instance has its own worlds, mods and settings.",
      `<input class="input" id="inst-search" type="search" placeholder="Search" value="${esc(S.instSearch)}" aria-label="Search instances" style="width:200px">
       <div class="tabs sm glass"><span class="drop" aria-hidden="true"></span>${["recent", "name", "version"].map((s) => `<button class="tab ${S.instSort === s ? "on" : ""}" data-action="inst-sort" data-sort="${s}">${s}</button>`).join("")}</div>
       <button class="btn btn-accent lq" data-action="route" data-route="new">${icon("plus", 14)} New instance</button>`,
      list.length ? `<div class="cards">${cards}</div>` : `<div class="empty"><h3>No instances found</h3><p>Try another search, or create a new instance.</p></div>`,
    ),
    mount(root) {
      const input = $("#inst-search", root);
      input.addEventListener("input", () => {
        S.instSearch = input.value;
        const pos = input.selectionStart;
        render();
        const again = $("#inst-search");
        again.focus();
        again.setSelectionRange(pos, pos);
      });
    },
  };
}

// ----------------------------------------------------------------- wizard --

function newWizard() {
  return {
    step: 1,
    kinds: { release: true, snapshot: false, beta: false, alpha: false },
    search: "",
    version: S.versions?.latestRelease || null,
    loader: "vanilla",
    loaderVersions: {},
    loaderVersion: null,
    name: "",
    nameTouched: false,
    memory: S.settings?.max_memory_mb || 4096,
  };
}

const autoName = (w) => `${w.version || "Minecraft"}${{ vanilla: "", fabric: " Fabric", forge: " Forge" }[w.loader]}`;

function renderWizard() {
  const w = (S.wizard ||= newWizard());
  if (!w.nameTouched) w.name = autoName(w);
  const steps = ["Version", "Loader", "Details"];
  const stepper = `<div class="steps">${steps.map((s, i) => `${i ? '<span class="step-gap"></span>' : ""}<span class="step ${w.step === i + 1 ? "on" : ""} ${w.step > i + 1 ? "done" : ""}"><b>${w.step > i + 1 ? icon("check", 12) : i + 1}</b>${s}</span>`).join("")}</div>`;
  let body;
  if (w.step === 1) {
    if (!S.versions) {
      body = `<div class="empty"><div class="bar" style="width:260px"><i></i></div><p>Loading Mojang's version list.</p></div>`;
    } else {
      const q = w.search.trim().toLowerCase();
      const rows = S.versions.versions.filter((v) => (w.kinds[v.kind] ?? w.kinds.snapshot) && (!q || v.id.toLowerCase().includes(q)));
      body = `<div class="row">
          <div class="tabs sm glass" role="group" aria-label="Version types"><span class="drop" aria-hidden="true" style="opacity:0"></span>${["release", "snapshot", "beta", "alpha"].map((k) => `<button class="tab ${w.kinds[k] ? "pick" : ""}" style="${w.kinds[k] ? "color:var(--ink);background:rgba(255,255,255,.14)" : ""}" data-action="wiz-kind" data-kind="${k}" aria-pressed="${w.kinds[k]}">${k}</button>`).join("")}</div>
          <input class="input grow" id="wiz-search" type="search" placeholder="Search ${S.versions.versions.length} versions" value="${esc(w.search)}" aria-label="Search versions">
          <button class="btn btn-sm lq" data-action="wiz-pick" data-id="${esc(S.versions.latestRelease)}">Latest release</button>
          <button class="btn btn-sm lq" data-action="wiz-pick" data-id="${esc(S.versions.latestSnapshot)}">Latest snapshot</button>
        </div>
        <div class="wiz-pick">
          <div class="vlist" role="listbox" aria-label="Minecraft versions">
            ${rows.map((v) => `<button class="vrow ${w.version === v.id ? "on" : ""}" role="option" aria-selected="${w.version === v.id}" data-action="wiz-pick" data-id="${esc(v.id)}"><span class="vid">${esc(v.id)}</span><span class="vname">${esc(updateName(v.id))}</span><span class="kind ${v.kind}">${v.kind === "release" ? "Release" : v.kind === "snapshot" ? "Snapshot" : v.kind === "beta" ? "Beta" : "Alpha"}</span><span class="vdate">${esc(v.date)}</span></button>`).join("") || `<div class="empty"><p>No versions match.</p></div>`}
          </div>
          ${w.version ? `<div class="vpreview">
            <div class="vpreview-art" style="background-image:url('${esc(cover(w.version))}')"></div>
            <div class="vpreview-text"><span class="label">${esc(updateName(w.version))}</span><strong>Minecraft ${esc(w.version)}</strong>
            <span class="muted">${esc(S.versions.versions.find((v) => v.id === w.version)?.date || "")}</span></div>
          </div>` : ""}
        </div>
        <div class="row"><span class="muted">${rows.length} shown. Selected: <strong style="color:var(--ink)">${esc(w.version || "none")}</strong></span><span class="grow"></span><button class="btn btn-accent lq" data-action="wiz-next" ${w.version ? "" : "disabled"}>Next</button></div>`;
    }
  } else if (w.step === 2) {
    const card = (kind, title, desc) => {
      const lv = w.loaderVersions[kind];
      const none = kind !== "vanilla" && Array.isArray(lv) && lv.length === 0;
      const state = kind === "vanilla" ? "Always available" : lv === undefined ? "Checking" : lv === "error" ? "Couldn't check. Are you offline?" : none ? `Not available for ${w.version}` : `${lv.length} build${lv.length === 1 ? "" : "s"}`;
      return `<button class="loader ${w.loader === kind ? "on" : ""}" data-action="wiz-loader" data-kind="${kind}" ${none ? "disabled" : ""}>
        ${block(kind, "lg")}<h3>${title}</h3><p>${desc}</p><span class="chip">${esc(state)}</span></button>`;
    };
    const lv = w.loaderVersions[w.loader];
    const rec = Array.isArray(lv) ? lv.find((x) => x.recommended) : null;
    body = `<div class="loaders">
        ${card("vanilla", "Vanilla", "Minecraft exactly as Mojang ships it.")}
        ${card("fabric", "Fabric", "Lightweight modding for performance, minimap and PvP mods.")}
        ${card("forge", "Forge", "The classic loader for big content mods and modpacks.")}
      </div>
      ${w.loader !== "vanilla" && Array.isArray(lv) && lv.length ? `<div class="field" style="max-width:420px"><label class="label" for="wiz-lv">${w.loader === "forge" ? "Forge build" : "Fabric loader"}</label>
        <select class="input" id="wiz-lv"><option value="">Recommended${rec ? ` (${esc(rec.display)})` : ""}</option>
        ${lv.slice(0, 60).map((x) => `<option value="${esc(x.id)}" ${w.loaderVersion === x.id ? "selected" : ""}>${esc(x.display)}${x.recommended ? ", recommended" : x.latest ? ", latest" : ""}${x.stable ? "" : " (beta)"}</option>`).join("")}</select></div>` : ""}
      <div class="row"><button class="btn lq" data-action="wiz-back">Back</button><span class="grow"></span><button class="btn btn-accent lq" data-action="wiz-next">Next</button></div>`;
  } else {
    const pct = ((w.memory - 1024) / (16384 - 1024)) * 100;
    body = `<div class="field"><label class="label" for="wiz-name">Name</label><input class="input" id="wiz-name" maxlength="48" value="${esc(w.name)}"></div>
      <div class="field"><label class="label" for="wiz-mem">Memory</label><div class="row"><input class="range grow" id="wiz-mem" type="range" min="1024" max="16384" step="512" value="${w.memory}" style="--fill:${pct}%"><span class="value" id="wiz-mem-v">${gb(w.memory)}</span></div></div>
      <div class="summary">
        <span class="label">Minecraft</span><strong>${esc(w.version)}</strong>
        <span class="label">Loader</span><strong>${esc(w.loader === "vanilla" ? "Vanilla" : `${w.loader === "forge" ? "Forge" : "Fabric"} ${w.loaderVersion || "(recommended)"}`)}</strong>
        <span class="label">Java</span><strong>Downloaded for you</strong>
      </div>
      <div class="row"><button class="btn lq" data-action="wiz-back">Back</button><span class="grow"></span><button class="btn btn-accent lq" data-action="wiz-create">${icon("check", 14)} Create instance</button></div>`;
  }
  return {
    html: sheet("New instance", "Every Minecraft version ever released, with Fabric or Forge if you want mods.", stepper, `<div class="wiz">${body}</div>`),
    mount(root) {
      const search = $("#wiz-search", root);
      if (search) search.addEventListener("input", () => {
        w.search = search.value;
        const pos = search.selectionStart;
        updateWizard();
        const again = $("#wiz-search");
        again.focus();
        again.setSelectionRange(pos, pos);
      });
      // Bring the selected version into view only the first time.
      if (!w.scrolled && $(".vrow.on", root)) { $(".vrow.on", root).scrollIntoView({ block: "center" }); w.scrolled = true; }
      $("#wiz-lv", root)?.addEventListener("change", (e) => (w.loaderVersion = e.target.value || null));
      $("#wiz-name", root)?.addEventListener("input", (e) => { w.name = e.target.value; w.nameTouched = true; });
      const mem = $("#wiz-mem", root);
      if (mem) mem.addEventListener("input", () => {
        w.memory = Number(mem.value);
        mem.style.setProperty("--fill", `${((w.memory - 1024) / (16384 - 1024)) * 100}%`);
        $("#wiz-mem-v").textContent = gb(w.memory);
      });
    },
  };
}

/** Update the wizard in place: no page rebuild, no replayed animation,
 *  and the version list keeps its scroll position. */
function updateWizard(animate = false) {
  const page = $("#page");
  const wiz = $(".wiz", page);
  if (S.route !== "new" || !wiz) { render({ full: true }); return; }
  const scroll = $(".vlist", page)?.scrollTop;
  const { html, mount } = renderWizard();
  const tpl = document.createElement("template");
  tpl.innerHTML = html;
  wiz.innerHTML = tpl.content.querySelector(".wiz").innerHTML;
  const steps = $(".steps", page), nextSteps = tpl.content.querySelector(".steps");
  if (steps && nextSteps) steps.innerHTML = nextSteps.innerHTML;
  hydrateIcons(page);
  mount?.(page);
  const list = $(".vlist", page);
  if (list && scroll != null && !animate) list.scrollTop = scroll;
  if (animate && anims()) wiz.animate([{ opacity: 0, translate: "0px 10px", filter: "blur(6px)" }, { opacity: 1, translate: "0px 0px", filter: "blur(0px)" }], { duration: 380, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)" });
}

/** Update the Mods page in place: header, tab states (the droplet slides)
 *  and the list, without rebuilding the page. */
function updateMods() {
  const page = $("#page");
  const pane = $(".mods-pane", page);
  if (!pane) { render({ full: true }); return; }
  const { html, mount } = renderMods();
  const tpl = document.createElement("template");
  tpl.innerHTML = html;
  const next = tpl.content;
  const nextPane = next.querySelector(".mods-pane");
  if (!nextPane) { render({ full: true }); return; }
  const head = $(".sheet-head", page), nextHead = next.querySelector(".sheet-head");
  if (head && nextHead && head.innerHTML !== nextHead.innerHTML) head.innerHTML = nextHead.innerHTML;
  for (const id of ["#mods-tabs", "#kind-tabs"]) {
    const cur = $(id, page), nx = next.querySelector(id);
    if (!cur || !nx) continue;
    const a = [...cur.querySelectorAll(":scope > .tab")], b = [...nx.querySelectorAll(":scope > .tab")];
    a.forEach((t, i) => {
      if (!b[i]) return;
      t.classList.toggle("on", b[i].classList.contains("on"));
      if (t.innerHTML !== b[i].innerHTML) t.innerHTML = b[i].innerHTML;
    });
    liquidTabs(cur);
  }
  const scroll = $(".sheet-body", page)?.scrollTop;
  const kindChanged = pane.dataset.view !== `${S.modsTab}|${S.mods.kind}|${S.mods.instance}`;
  const swept = kindChanged && anims() && pane.children.length && phone();
  if (swept) dustAway(pane, { duration: 560 });
  pane.innerHTML = nextPane.innerHTML;
  if (swept) dust.reveal(pane, { duration: 560, dir: "down" });
  else if (kindChanged && anims()) paneIn(pane);
  pane.dataset.view = `${S.modsTab}|${S.mods.kind}|${S.mods.instance}`;
  hydrateIcons(page);
  mount?.(page);
  if (!kindChanged && scroll != null) $(".sheet-body", page).scrollTop = scroll;

}

function loadLoaderVersions(w) {
  for (const kind of ["fabric", "forge"]) {
    if (w.loaderVersions[kind] !== undefined) continue;
    const forVersion = w.version;
    call("loader_versions", { loader: kind, game: forVersion })
      .then((list) => { if (w.version === forVersion) w.loaderVersions[kind] = list; })
      .catch(() => { if (w.version === forVersion) w.loaderVersions[kind] = "error"; })
      .finally(() => { if (S.route === "new" && w.step === 2) updateWizard(); });
  }
}

// ------------------------------------------------------------------- mods --

const KINDS = [["mod", "Mods"], ["resourcepack", "Resource packs"], ["shader", "Shaders"]];
const KIND_ONE = { mod: "mod", resourcepack: "resource pack", shader: "shader" };
const HIDDEN_TAGS = new Set(["babric", "rift", "legacy-fabric", "ornithe", "bta-babric", "java", "minecraft", "iris", "optifine", "canvas", "vanilla", "bukkit", "paper", "spigot", "purpur", "folia", "sponge", "velocity", "bungeecord", "waterfall", "datapack", "nilloader", "risugami"]);
const SORTS = [["relevance", "Relevance"], ["downloads", "Downloads"], ["follows", "Follows"], ["updated", "Updated"], ["newest", "Newest"]];

function modsInstance() {
  if (!S.mods.instance || !S.instances.some((i) => i.name === S.mods.instance)) {
    const sel = selectedInstance();
    const modded = S.instances.filter((i) => i.loader !== "vanilla");
    S.mods.instance = sel && sel.loader !== "vanilla" ? sel.name : modded[0]?.name || sel?.name || null;
  }
  const inst = S.instances.find((i) => i.name === S.mods.instance) || null;
  if (inst && inst.loader === "vanilla" && (S.mods.kind || "mod") === "mod") S.mods.kind = "resourcepack";
  S.mods.kind ||= inst?.loader === "vanilla" ? "resourcepack" : "mod";
  return inst;
}

function contentRow(m, inst) {
  const iconHtml = m.iconUrl
    ? `<img class="proj-icon sm" src="${esc(m.iconUrl)}" alt="" loading="lazy" onerror="this.replaceWith(Object.assign(document.createElement('span'),{className:'proj-icon sm blank'}))">`
    : `<span class="proj-icon sm blank">${icon(S.mods.kind === "mod" ? loaderIcon(m.loader === "forge" ? "forge" : "fabric") : "grid", 16)}</span>`;
  const isVortex = /^vortex-mods/.test(m.file);
  return `<div class="list-row ${m.enabled ? "" : "off"}">
    ${iconHtml}
    <div class="meta"><strong>${esc(m.name)}${m.version ? ` <span class="chip">${esc(m.version)}</span>` : ""}${isVortex ? ` <span class="chip accent">Vortex</span>` : ""}</strong><span>${esc(m.description || m.file)}</span></div>
    <button class="switch" role="switch" aria-checked="${m.enabled}" aria-label="Enable ${esc(m.name)}" data-action="mod-toggle" data-file="${esc(m.file)}" data-on="${m.enabled}"></button>
    <button class="btn btn-sm btn-icon btn-ghost btn-danger lq" data-action="mod-delete" data-file="${esc(m.file)}" aria-label="Delete ${esc(m.name)}">${icon("trash", 12)}</button>
  </div>`;
}

function installedBody(inst) {
  const kind = S.mods.kind;
  if (kind === "mod" && inst.loader === "vanilla") {
    return `<div class="empty">${block("vanilla", "lg")}<h3>Vanilla doesn't load mods</h3><p>Resource packs and shaders still work. For mods, make a Fabric or Forge instance.</p><button class="btn btn-accent lq" data-action="route" data-route="new">${icon("plus", 14)} New modded instance</button></div>`;
  }
  if (S.mods.loading) return `<div class="empty"><div class="bar" style="width:240px"><i></i></div></div>`;
  const list = S.mods.list;
  if (!list.length) {
    return `<div class="empty"><h3>No ${KIND_ONE[kind]}s yet</h3><p>Find some on the Browse tab${kind === "mod" && isTauri ? ", or drop .jar files on this window" : ""}.</p><button class="btn btn-accent lq" data-action="mods-tab" data-tab="browse">${icon("search", 14)} Browse ${KIND_ONE[kind]}s</button></div>`;
  }
  return `<div class="list">${list.map((m) => contentRow(m, inst)).join("")}</div>
    ${isTauri && kind === "mod" ? `<div class="dropzone" id="dropzone">Drop .jar files on this window to install them</div>` : ""}`;
}

function hitCard(h, inst) {
  const kind = S.mods.kind;
  const installed = (S.browse.installed[kind] || []).includes(h.id);
  const busy = S.browse.busy[h.id];
  const btn = busy
    ? `<button class="btn btn-sm lq" disabled><span class="spin"></span> Installing</button>`
    : installed
      ? `<button class="btn btn-sm btn-ghost lq" disabled>${icon("check", 12)} Installed</button>`
      : `<button class="btn btn-sm btn-accent lq" data-action="mr-install" data-id="${esc(h.id)}">${icon("download", 12)} Install</button>`;
  return `<article class="hit" data-action="mr-open" data-id="${esc(h.id)}" tabindex="0" role="button">
    ${h.iconUrl ? `<img class="proj-icon" src="${esc(h.iconUrl)}" alt="" loading="lazy" onerror="this.replaceWith(Object.assign(document.createElement('span'),{className:'proj-icon blank'}))">` : `<span class="proj-icon blank"></span>`}
    <div class="hit-main">
      <div class="hit-title"><strong>${esc(h.title)}</strong><span>by ${esc(h.author)}</span></div>
      <p>${esc(h.description)}</p>
      <div class="hit-meta">
        <span title="Downloads">${icon("download", 11)} ${fmtCount(h.downloads)}</span>
        <span title="Follows">${icon("heart", 11)} ${fmtCount(h.follows)}</span>
        ${h.categories.filter((c) => !HIDDEN_TAGS.has(c)).slice(0, 2).map((c) => `<span class="tag">${esc(c.replace(/-/g, " "))}</span>`).join("")}
      </div>
    </div>
    <div class="hit-side">${btn}</div>
  </article>`;
}

function browseBody(inst) {
  const kind = S.mods.kind;
  if (kind === "mod" && inst.loader === "vanilla") return installedBody(inst);
  const b = S.browse;
  const key = `${inst.name}|${kind}|${b.query}|${b.sort}`;
  if (b.key !== key && !b.loading) searchModrinth(true);
  const results = b.hits.length
    ? `<div class="hits">${b.hits.map((h) => hitCard(h, inst)).join("")}</div>
       ${b.hits.length < b.total ? `<div class="row" style="justify-content:center"><button class="btn lq" data-action="mr-more" ${b.loading ? "disabled" : ""}>${b.loading ? "Loading" : `Show more (${fmtCount(b.total - b.hits.length)} left)`}</button></div>` : ""}`
    : b.loading
      ? `<div class="hits">${Array.from({ length: 6 }, () => `<div class="hit skeleton"><span class="proj-icon blank"></span><div class="hit-main"><i></i><i></i><i></i></div></div>`).join("")}</div>`
      : `<div class="empty"><h3>Nothing found</h3><p>${b.error ? esc(b.error) : `No ${KIND_ONE[kind]}s match for Minecraft ${esc(inst.minecraftVersion)}${kind === "mod" ? ` with ${esc(inst.loaderLabel.split(" ")[0])}` : ""}.`}</p></div>`;
  return `<div class="browse-bar">
      <div class="search-field">${icon("search", 14)}<input class="input" id="mr-search" type="search" placeholder="Search Modrinth for ${KIND_ONE[kind]}s" value="${esc(b.query)}" aria-label="Search Modrinth"></div>
      <select class="input" id="mr-sort" aria-label="Sort">${SORTS.map(([v, l]) => `<option value="${v}" ${b.sort === v ? "selected" : ""}>${l}</option>`).join("")}</select>
    </div>
    <p class="muted small">Showing ${KIND_ONE[kind]}s for Minecraft ${esc(inst.minecraftVersion)}${kind === "mod" ? ` · ${esc(inst.loaderLabel.split(" ")[0])}` : ""}. From modrinth.com.</p>
    ${results}`;
}

function renderMods() {
  const inst = modsInstance();
  if (!inst) return { html: sheet("Mods", "", "", `<div class="empty"><h3>No instances yet</h3><p>Create an instance first, then add mods to it.</p><button class="btn btn-accent lq" data-action="route" data-route="new">${icon("plus", 14)} New instance</button></div>`) };
  if (S.mods.loadedFor !== `${inst.name}|${S.mods.kind}`) loadMods();
  const kind = S.mods.kind;
  const picker = `<label class="inst-picker">${coverThumb(inst, "sm")}<select class="input" id="mods-inst" aria-label="Instance">${S.instances.map((i) => `<option value="${esc(i.name)}" ${i.name === inst.name ? "selected" : ""}>${esc(i.name)} · ${esc(i.loaderLabel)}</option>`).join("")}</select></label>`;
  const folder = { mod: "folder-mods", resourcepack: "folder-packs", shader: "folder-shaders" }[kind];
  const count = S.mods.list.length;
  return {
    html: sheet("Mods", `${esc(inst.name)} · Minecraft ${esc(inst.minecraftVersion)} · ${esc(inst.loaderLabel)}`, `${picker}
      <button class="btn lq" data-action="${folder}">${icon("folder", 14)} Open folder</button>`,
      `<div class="mods-tools">
        <div class="tabs glass" id="mods-tabs"><span class="drop" aria-hidden="true"></span>
          <button class="tab ${S.modsTab === "browse" ? "on" : ""}" data-action="mods-tab" data-tab="browse">${icon("search", 13)} Browse</button>
          <button class="tab ${S.modsTab === "installed" ? "on" : ""}" data-action="mods-tab" data-tab="installed">${icon("check", 13)} Installed${count ? ` <b class="count">${count}</b>` : ""}</button>
        </div>
        <div class="tabs sm glass" id="kind-tabs"><span class="drop" aria-hidden="true"></span>
          ${KINDS.map(([k, l]) => `<button class="tab ${kind === k ? "on" : ""}" data-action="mods-kind" data-kind="${k}">${l}</button>`).join("")}
        </div>
      </div>
      <div class="mods-pane" data-view="${esc(`${S.modsTab}|${S.mods.kind}|${S.mods.instance}`)}">${S.modsTab === "browse" ? browseBody(inst) : installedBody(inst)}</div>`),
    mount(root) {
      $("#mods-inst", root).addEventListener("change", (e) => {
        S.mods.instance = e.target.value;
        S.mods.list = [];
        S.browse.hits = [];
        render();
      });
      const search = $("#mr-search", root);
      if (search) {
        let timer;
        search.addEventListener("input", () => {
          clearTimeout(timer);
          timer = setTimeout(() => {
            S.browse.query = search.value.trim();
            const pos = search.selectionStart;
            render();
            const again = $("#mr-search");
            again?.focus();
            again?.setSelectionRange(pos, pos);
          }, 320);
        });
      }
      $("#mr-sort", root)?.addEventListener("change", (e) => { S.browse.sort = e.target.value; render(); });
    },
  };
}

async function loadMods() {
  const name = S.mods.instance;
  const kind = S.mods.kind;
  const inst = S.instances.find((i) => i.name === name);
  if (!inst) return;
  S.mods.loadedFor = `${name}|${kind}`;
  S.mods.loading = true;
  try {
    const [list, installed] = await Promise.all([call("list_mods", { name, kind }), call("modrinth_installed", { instance: name }).catch(() => ({}))]);
    if (S.mods.instance === name && S.mods.kind === kind) {
      S.mods.list = list;
      S.browse.installed = installed;
    }
  } catch (e) {
    toast("Couldn't read the folder", errText(e), "error");
  } finally {
    S.mods.loading = false;
    if (S.route === "mods") render();
  }
}

async function searchModrinth(reset) {
  const inst = S.instances.find((i) => i.name === S.mods.instance);
  if (!inst) return;
  const b = S.browse;
  const kind = S.mods.kind;
  const key = `${inst.name}|${kind}|${b.query}|${b.sort}`;
  if (reset) { b.hits = []; b.offset = 0; b.total = 0; }
  b.key = key;
  b.loading = true;
  b.error = null;
  try {
    const page = await call("modrinth_search", { query: b.query, kind, instance: inst.name, sort: b.sort, offset: reset ? 0 : b.hits.length });
    if (b.key !== key) return;
    b.hits = reset ? page.hits : [...b.hits, ...page.hits];
    b.total = page.total;
  } catch (e) {
    if (b.key === key) b.error = `Couldn't reach Modrinth: ${errText(e)}`;
  } finally {
    if (b.key === key) {
      b.loading = false;
      if (S.route === "mods" && S.modsTab === "browse") {
        const pane = $(".mods-pane");
        if (pane) { pane.innerHTML = browseBody(inst); hydrateIcons(pane); bindBrowseInputs(pane); }
      }
    }
  }
}

function bindBrowseInputs(root) {
  const search = $("#mr-search", root);
  if (search && !search.dataset.bound) {
    search.dataset.bound = "1";
    let timer;
    search.addEventListener("input", () => {
      clearTimeout(timer);
      timer = setTimeout(() => { S.browse.query = search.value.trim(); searchModrinth(true); }, 320);
    });
  }
  const sort = $("#mr-sort", root);
  if (sort && !sort.dataset.bound) {
    sort.dataset.bound = "1";
    sort.addEventListener("change", (e) => { S.browse.sort = e.target.value; searchModrinth(true); });
  }
}

async function installProject(id, versionId = null) {
  const inst = S.instances.find((i) => i.name === S.mods.instance);
  if (!inst || S.browse.busy[id]) return;
  const kind = S.mods.kind;
  S.browse.busy[id] = true;
  refreshBrowseCard(id);
  try {
    const res = await call("modrinth_install", { instance: inst.name, projectId: id, versionId, kind });
    (S.browse.installed[kind] ||= []).push(id);
    sound.success();
    const extra = res.installed.length > 1 ? ` with ${res.installed.slice(1).join(", ")}` : "";
    toast("Installed", `${res.installed[0]}${extra} added to ${inst.name}.`, "success");
    S.mods.loadedFor = null;
    refreshInstances().catch(() => {});
  } catch (e) {
    sound.error();
    toast("Couldn't install", errText(e), "error", 7000);
  } finally {
    delete S.browse.busy[id];
    refreshBrowseCard(id);
  }
}

function refreshBrowseCard(id) {
  const inst = S.instances.find((i) => i.name === S.mods.instance);
  const h = S.browse.hits.find((x) => x.id === id);
  const el = $(`.hit[data-id="${CSS.escape(id)}"]`);
  if (inst && h && el) {
    el.outerHTML = hitCard(h, inst);
    hydrateIcons($(`.hit[data-id="${CSS.escape(id)}"]`));
  }
  const mbtn = $(`#mr-modal-install`);
  if (mbtn && mbtn.dataset.id === id) {
    const installed = (S.browse.installed[S.mods.kind] || []).includes(id);
    mbtn.disabled = !!S.browse.busy[id] || installed;
    mbtn.innerHTML = S.browse.busy[id] ? `<span class="spin"></span> Installing` : installed ? `${icon("check", 14)} Installed` : `${icon("download", 14)} Install latest`;
  }
}

async function openProject(id) {
  const inst = S.instances.find((i) => i.name === S.mods.instance);
  const hit = S.browse.hits.find((x) => x.id === id);
  modal(`<div class="proj-head">${hit?.iconUrl ? `<img class="proj-icon lg" src="${esc(hit.iconUrl)}" alt="">` : `<span class="proj-icon lg blank"></span>`}
      <div><h3>${esc(hit?.title || "Loading")}</h3><p class="muted">${esc(hit?.description || "")}</p></div></div>
    <div class="bar"><i></i></div>`);
  try {
    const { project: p, versions } = await call("modrinth_project", { id, kind: S.mods.kind, instance: inst?.name });
    if (!$("#modal-root").children.length) return;
    const installed = (S.browse.installed[S.mods.kind] || []).includes(id);
    const links = [["Modrinth", p.pageUrl], ["Source", p.sourceUrl], ["Wiki", p.wikiUrl], ["Discord", p.discordUrl]].filter(([, u]) => u);
    const box = $("#modal-root .modal");
    box.classList.add("wide");
    box.innerHTML = `<div class="proj-head">${p.iconUrl ? `<img class="proj-icon lg" src="${esc(p.iconUrl)}" alt="">` : `<span class="proj-icon lg blank"></span>`}
        <div class="grow"><h3>${esc(p.title)}</h3><p class="muted">${esc(p.description)}</p>
          <div class="hit-meta"><span>${icon("download", 11)} ${fmtCount(p.downloads)} downloads</span><span>${icon("heart", 11)} ${fmtCount(p.follows)}</span>${p.license ? `<span class="tag">${esc(p.license)}</span>` : ""}<span class="tag">Updated ${esc(p.updated)}</span></div></div></div>
      ${p.gallery.length ? `<div class="gallery">${p.gallery.slice(0, 4).map((g) => `<div class="shot" style="background-image:url('${esc(g.url)}')" title="${esc(g.title || "")}"></div>`).join("")}</div>` : ""}
      <div class="section"><h2>Versions for ${esc(inst?.name || "")}</h2>
        ${versions.length ? `<div class="list versions">${versions.slice(0, 8).map((v) => `<div class="list-row">
          <div class="meta"><strong>${esc(v.name || v.number)} <span class="kind ${v.kind === "release" ? "release" : "snapshot"}">${esc(v.kind)}</span></strong><span>${esc(v.number)} · ${esc(v.date)} · ${fmtCount(v.downloads)} downloads</span></div>
          <button class="btn btn-sm lq" data-action="mr-install-version" data-id="${esc(p.id)}" data-version="${esc(v.id)}">Install</button></div>`).join("")}</div>`
          : `<p class="muted">No version of ${esc(p.title)} works with Minecraft ${esc(inst?.minecraftVersion || "")}${S.mods.kind === "mod" ? ` and ${esc(inst?.loaderLabel.split(" ")[0] || "")}` : ""}.</p>`}
      </div>
      <div class="modal-actions">${links.map(([l, u]) => `<button class="btn btn-sm btn-ghost lq" data-action="open-url" data-url="${esc(u)}">${icon("link", 12)} ${l}</button>`).join("")}<span class="grow"></span>
        <button class="btn lq" id="mr-close">Close</button>
        <button class="btn btn-accent lq" id="mr-modal-install" data-id="${esc(p.id)}" ${installed || !versions.length ? "disabled" : ""}>${installed ? `${icon("check", 14)} Installed` : `${icon("download", 14)} Install latest`}</button></div>`;
    hydrateIcons(box);
    $("#mr-close").onclick = closeModal;
    $("#mr-modal-install").onclick = () => installProject(p.id);
  } catch (e) {
    closeModal();
    toast("Couldn't load the project", errText(e), "error");
  }
}

// ------------------------------------------------------------------ skins --

/** A flat front view of a skin (head, body, arms, legs) for thumbnails. */
function drawSkinFront(canvas, url, slim) {
  const img = new Image();
  img.onload = () => {
    const g = canvas.getContext("2d");
    g.imageSmoothingEnabled = false;
    g.clearRect(0, 0, canvas.width, canvas.height);
    const s = canvas.width / 16;
    const legacy = img.height === 32;
    const aw = slim ? 3 : 4;
    const part = (sx, sy, w, h, dx, dy, flip) => {
      g.save();
      if (flip) { g.translate((dx + w) * s, dy * s); g.scale(-1, 1); g.drawImage(img, sx, sy, w, h, 0, 0, w * s, h * s); }
      else g.drawImage(img, sx, sy, w, h, dx * s, dy * s, w * s, h * s);
      g.restore();
    };
    const x0 = 4;
    part(8, 8, 8, 8, x0, 0); part(40, 8, 8, 8, x0, 0);
    part(20, 20, 8, 12, x0, 8); if (!legacy) part(20, 36, 8, 12, x0, 8);
    part(44, 20, aw, 12, x0 - aw, 8); if (!legacy) part(44, 36, aw, 12, x0 - aw, 8);
    if (legacy) part(44, 20, aw, 12, x0 + 8, 8, true); else { part(36, 52, aw, 12, x0 + 8, 8); part(52, 52, aw, 12, x0 + 8, 8); }
    part(4, 20, 4, 12, x0, 20); if (!legacy) part(4, 36, 4, 12, x0, 20);
    if (legacy) part(4, 20, 4, 12, x0 + 4, 20, true); else { part(20, 52, 4, 12, x0 + 4, 20); part(4, 52, 4, 12, x0 + 4, 20); }
  };
  img.src = url;
}

async function loadSkins() {
  try {
    S.skins = await call("skin_library");
  } catch (e) {
    S.skins = { skins: [], applied: null, cape: "none", capes: Object.keys(CAPE_NAMES), microsoft: false };
    toast("Couldn't read your skins", errText(e), "error");
  }
  if (S.route === "skins") render();
}

function renderSkins() {
  if (!S.skins) loadSkins();
  const lib = S.skins || { skins: [], applied: null, cape: "none", capes: Object.keys(CAPE_NAMES), microsoft: false };
  const acc = selectedAccount();
  const skinCards = lib.skins.map((k) => `<div class="skin-card ${lib.applied === k.id ? "on" : ""}">
      <button class="skin-art" data-action="skin-use" data-id="${esc(k.id)}" aria-label="Use ${esc(k.name)}"><canvas width="64" height="128" data-skin="${esc(k.id)}"></canvas>${lib.applied === k.id ? `<span class="chip accent">In use</span>` : ""}</button>
      <div class="skin-meta"><strong title="${esc(k.name)}">${esc(k.name)}</strong>
        <div class="seg sm" role="group" aria-label="Model">
          <button class="${k.slim ? "" : "on"}" data-action="skin-model" data-id="${esc(k.id)}" data-slim="false">Classic</button>
          <button class="${k.slim ? "on" : ""}" data-action="skin-model" data-id="${esc(k.id)}" data-slim="true">Slim</button>
        </div></div>
      <div class="skin-actions">
        <button class="btn btn-sm ${lib.applied === k.id ? "btn-ghost" : "btn-accent"} lq grow" data-action="skin-use" data-id="${esc(k.id)}" ${lib.applied === k.id ? "disabled" : ""}>${lib.applied === k.id ? "In use" : "Use"}</button>
        <button class="btn btn-sm btn-icon btn-ghost btn-danger lq" data-action="skin-remove" data-id="${esc(k.id)}" aria-label="Delete ${esc(k.name)}">${icon("trash", 12)}</button>
      </div></div>`).join("");
  const capeCards = lib.capes.map((c) => `<button class="cape ${lib.cape === c ? "on" : ""}" data-action="cape" data-cape="${c}" aria-pressed="${lib.cape === c}">
      ${c === "none" ? `<span class="cape-art none">${icon("x", 16)}</span>` : `<span class="cape-art" style="background-image:url('img/capes/${c}.png')"></span>`}
      <span>${CAPE_NAMES[c] || c}</span></button>`).join("");
  const where = lib.microsoft
    ? "Using a skin uploads it to your Microsoft account, so everyone sees it."
    : `Offline accounts: Vortex mods show your skin and cape in-game on Fabric ${VORTEX_MC.join(", ")}. Only you see them.`;
  return {
    html: sheet("Skins", acc ? `${esc(acc.username)} · ${acc.kind === "microsoft" ? "Microsoft account" : "Offline account"}` : "Add an account to use skins.", `<button class="btn lq" data-action="route" data-route="accounts">${icon("user", 14)} Accounts</button>
        <button class="btn lq" data-action="folder-skins">${icon("folder", 14)} Open folder</button>`, `
      <div class="skins-layout">
        <div class="pedestal" id="pedestal"><div class="who">${esc(acc?.username || "No account")}</div><div class="hint">Drag to turn</div></div>
        <div class="skins-side">
          <div class="section"><div class="section-head"><h2>Your skins</h2><span class="muted small">${where}</span></div>
            <div class="skin-grid">${skinCards}
              <label class="skin-card add" tabindex="0"><input type="file" id="skin-file" accept="image/png" hidden><span class="add-art">${icon("plus", 22)}</span><strong>Upload a skin</strong><span class="muted small">64×64 PNG${isTauri ? ", or drop it on the window" : ""}</span></label>
            </div>
          </div>
          <div class="section"><h2>Copy a player's skin</h2>
            <div class="row"><input class="input grow" id="skin-player" maxlength="16" placeholder="Java Edition username" aria-label="Username"><button class="btn lq" data-action="skin-player">${icon("download", 14)} Get skin</button></div>
          </div>
          <div class="section"><h2>Cape</h2><div class="capes">${capeCards}</div></div>
        </div>
      </div>`),
    mount(root) {
      const ped = $("#pedestal", root);
      S.preview = new Player(ped, null, { preview: true, unit: 9 });
      S.preview.setSkin(S.skin?.dataUrl || defaultSkin(), S.skin?.slim);
      S.preview.setCape(capeUrl(lib.cape));
      S.preview.facing = 200;
      $$("canvas[data-skin]", root).forEach((c) => {
        const k = lib.skins.find((x) => x.id === c.dataset.skin);
        if (k) drawSkinFront(c, k.dataUrl, k.slim);
      });
      $("#skin-file", root).addEventListener("change", (e) => {
        const file = e.target.files?.[0];
        if (!file) return;
        const reader = new FileReader();
        reader.onload = () => addSkin(String(reader.result), file.name.replace(/\.png$/i, ""));
        reader.readAsDataURL(file);
      });
      $("#skin-player", root).addEventListener("keydown", (e) => { if (e.key === "Enter") actions["skin-player"](); });
    },
  };
}

async function addSkin(dataUrl, name) {
  const img = new Image();
  img.onload = async () => {
    if (img.width !== 64 || (img.height !== 64 && img.height !== 32)) {
      sound.error();
      toast("That isn't a skin", `Skins are 64×64 PNGs. This one is ${img.width}×${img.height}.`, "error");
      return;
    }
    // Guess the model: slim skins leave the 4th arm column empty.
    const c = document.createElement("canvas");
    c.width = 64; c.height = 64;
    const g = c.getContext("2d");
    g.drawImage(img, 0, 0);
    const slim = img.height === 64 && g.getImageData(54, 20, 2, 12).data.every((v, i) => i % 4 !== 3 || v === 0);
    try {
      S.skins = await call("skin_add", { dataUrl, name, slim });
      sound.success();
      toast("Skin added", `${name} is in your skins. Press Use to wear it.`, "success");
      render();
    } catch (e) {
      sound.error();
      toast("Couldn't add the skin", errText(e), "error");
    }
  };
  img.src = dataUrl;
}

// --------------------------------------------------------------- accounts --

function renderAccounts() {
  const acc = selectedAccount();
  const rows = S.accounts.map((a) => `<div class="list-row">
      ${a.selected ? `<canvas class="avatar" width="8" height="8" data-head="1" style="width:40px;height:40px"></canvas>` : `<canvas class="avatar" width="8" height="8" data-avatar="${esc(a.uuid)}" style="width:40px;height:40px"></canvas>`}
      <div class="meta"><strong>${esc(a.username)}${a.selected ? ` <span class="chip accent">In use</span>` : ""}</strong><span>${a.kind === "microsoft" ? "Microsoft account" : "Offline account, for singleplayer and LAN"}</span></div>
      ${a.selected ? "" : `<button class="btn btn-sm lq" data-action="acc-use" data-uuid="${esc(a.uuid)}">Use</button>`}
      <button class="btn btn-sm btn-icon btn-danger lq" data-action="acc-remove" data-uuid="${esc(a.uuid)}" aria-label="Remove ${esc(a.username)}">${icon("trash", 12)}</button>
    </div>`).join("");
  const skinNote = !S.skin || S.skin.source === "none" ? "Vortex outfit" : S.skin.source === "account" ? "Your skin" : "Default skin";
  return {
    html: sheet("Accounts", "Sign in with Microsoft to play online, or add an offline name for singleplayer.", "", `
      <div class="accounts-layout">
        <div class="pedestal" id="pedestal"><div class="who">${esc(acc?.username || "No account")}</div><div class="hint">${skinNote}. Drag to turn.</div></div>
        <div>
          <div class="section"><h2>Your accounts</h2>${S.accounts.length ? `<div class="list">${rows}</div>` : `<p class="muted">No accounts yet.</p>`}</div>
          <div class="section"><h2>Add an account</h2>
            <div class="settings-grid">
              <div class="card-pad"><h3>Offline</h3>
                <p class="muted" style="margin:0">Any name. Online-mode servers won't let it join.</p>
                <div class="row"><input class="input grow" id="offline-name" maxlength="16" placeholder="Username" aria-label="Offline username"><button class="btn btn-accent lq" data-action="acc-offline">Add</button></div>
              </div>
              <div class="card-pad"><h3>Microsoft</h3>
                <p class="muted" style="margin:0">Use the account that owns Minecraft: Java Edition.</p>
                <div><button class="btn lq" data-action="acc-ms">Sign in with Microsoft</button></div>
                ${S.settings?.microsoft_client_id || isAndroid ? "" : `<span class="muted" style="font-size:13px">Add your Azure client ID in Settings first.</span>`}
              </div>
            </div>
          </div>
        </div>
      </div>`),
    mount(root) {
      const ped = $("#pedestal", root);
      S.preview = new Player(ped, null, { preview: true, unit: 9 });
      S.preview.setSkin(S.skin?.dataUrl || defaultSkin(), S.skin?.slim);
      $("#offline-name", root).addEventListener("keydown", (e) => { if (e.key === "Enter") actions["acc-offline"](); });
    },
  };
}

// --------------------------------------------------------------- settings --

const SKY_MODES = [["cycle", "Cycle"], ["clock", "Clock"], ["dawn", "Dawn"], ["day", "Day"], ["dusk", "Dusk"], ["night", "Night"]];

function skyControls(d) {
  return `<div class="tabs sm glass" id="sky-tabs" role="group" aria-label="Time of day"><span class="drop" aria-hidden="true"></span>${SKY_MODES.map(([m, l]) => `<button class="tab ${d.sky === m ? "on" : ""}" data-action="set-sky" data-sky="${m}">${l}</button>`).join("")}</div>
    <div class="field" ${d.sky === "cycle" ? "" : "hidden"} id="daylen-field"><label class="label" for="s-day">Length of a day</label><div class="row"><input class="range grow" id="s-day" type="range" min="2" max="40" step="1" value="${d.day_length_min}" style="--fill:${((d.day_length_min - 2) / 38) * 100}%"><span class="value" id="s-day-v">${d.day_length_min} min</span></div></div>`;
}

const SETTINGS_TABS = isAndroid
  ? [["general", "General"], ["appearance", "Appearance"], ["game", "Game"], ["data", "Storage"]]
  : [["general", "General"], ["appearance", "Appearance"], ["game", "Game"], ["ingame", "In-game"], ["data", "Account & data"]];
const WINDOW_SIZES = [["", "Minecraft default (854 × 480)"], ["1280x720", "1280 × 720"], ["1600x900", "1600 × 900"], ["1920x1080", "1920 × 1080"], ["2560x1440", "2560 × 1440"], ["custom", "Custom"]];

const swRow = (d, key, title, desc) => `<div class="setting"><div><strong>${title}</strong>${desc ? `<span>${desc}</span>` : ""}</div><button class="switch" role="switch" aria-checked="${!!d[key]}" data-action="set-bool" data-key="${key}" aria-label="${title}"></button></div>`;
const segRow = (d, key, title, options, desc = "") => `<div class="setting stack"><div><strong>${title}</strong>${desc ? `<span>${desc}</span>` : ""}</div>
  <div class="seg" role="group" aria-label="${title}">${options.map(([v, l]) => `<button class="${String(d[key]) === String(v) ? "on" : ""}" data-action="set-choice" data-key="${key}" data-value="${v}">${l}</button>`).join("")}</div></div>`;
const rangeRow = (id, label, value, lo, hi, step, text) => `<div class="field"><label class="label" for="${id}">${label}</label><div class="row"><input class="range grow" id="${id}" type="range" min="${lo}" max="${hi}" step="${step}" value="${value}" style="--fill:${((value - lo) / (hi - lo)) * 100}%"><span class="value" id="${id}-v">${text}</span></div></div>`;

function settingsBody(d) {
  const tab = S.settingsTab;
  if (tab === "appearance") {
    const u = { ...UI_DEFAULTS, ...d };
    const wall = u.wall_custom && S.customWallUrl ? S.customWallUrl : "img/birch-forest.jpg";
    return `<div class="settings-grid">
      <div class="card-pad"><h3>Feel</h3>
        ${segRow(u, "radius", "Corners", [["round", "Round"], ["soft", "Soft"], ["sharp", "Sharp"]])}
        ${segRow(u, "motion", "Motion", [["bouncy", "Bouncy"], ["smooth", "Smooth"]], "How presses, menus and tabs spring")}
      </div>
      <div class="card-pad"><h3>Theme and colour</h3>
        ${segRow(u, "theme", "Mode", [["dark", "Dark"], ["light", "Light"], ["system", "System"]], "System follows Windows")}
        <div class="setting stack"><div><strong>Accent</strong><span>Buttons, highlights and the in-game mod menu</span></div>
          <div class="swatches">${Object.entries(ACCENTS).map(([k, [c]]) => `<button class="swatch ${d.accent === k ? "on" : ""}" style="--c:${c}" data-action="set-choice" data-key="accent" data-value="${k}" aria-label="${k}" title="${k[0].toUpperCase() + k.slice(1)}"></button>`).join("")}</div></div>
      </div>
      <div class="card-pad"><h3>Home</h3>
        ${swRow(u, "home_recent", "Recent instances", "Under the Launch button")}
        ${swRow(u, "side_labels", "Sidebar labels", "Off for a slim icon sidebar")}
        ${swRow(d, "show_player", "3D player", "Your skin, standing at the Launch button")}
        ${swRow(u, "intro", "Entrance", "He runs in and catches his breath when the launcher opens")}
      </div>
    </div>`;
  }
  if (tab === "game" && isAndroid) {
    const hi = S.ramMax || 4096;
    const max = Math.min(d.max_memory_mb, hi);
    return `<div class="settings-grid">
      <div class="card-pad"><h3>Memory</h3>
        ${rangeRow("s-max", "Memory for the game", max, 1024, hi, 256, gb(max))}
        <span class="muted small">This phone has ${gb(S.deviceRamMb || hi)}. Leave room for Android: about half is a good start.</span>
      </div>
      <div class="card-pad"><h3>Touch and graphics</h3>
        <div class="setting"><div><strong>Touch controls</strong><span>Move, resize and add on-screen buttons</span></div><button class="btn btn-sm" data-action="native" data-what="controls">${icon("edit", 12)} Edit</button></div>
        <div class="setting"><div><strong>Renderer, resolution and more</strong><span>Graphics backend, render scale, gyroscope, mouse and Java options</span></div><button class="btn btn-sm" data-action="native" data-what="settings">${icon("gear", 12)} Open</button></div>
      </div>
      <div class="card-pad"><h3>Mod downloads</h3>
        ${swRow(d, "mods_with_dependencies", "Install required mods", "Adds the libraries a mod needs, like Fabric API")}
        ${swRow(d, "mods_allow_unstable", "Prefer beta versions", "Otherwise betas are used only when there's nothing else")}
      </div>
    </div>`;
  }
  if (tab === "data" && isAndroid) {
    return `<div class="settings-grid">
      <div class="card-pad"><h3>Storage</h3>
        <div class="field"><span class="label">Game folder</span><code class="path">${esc(S.dataDir)}</code></div>
        <div class="row wrap"><button class="btn btn-sm lq" data-action="folder-data">${icon("folder", 12)} Open</button><button class="btn btn-sm lq" data-action="native" data-what="logs">${icon("log", 12)} Share logs</button></div>
      </div>
      <div class="card-pad"><h3>About</h3>
        <div class="setting"><div><strong>Vortex Client ${esc(S.appVersion)}</strong><span>Built on Amethyst (LGPL-3.0), the successor of PojavLauncher, which runs Java Edition on Android</span></div></div>
      </div>
    </div>`;
  }
  if (tab === "game") {
    const size = d.game_width && d.game_height ? `${d.game_width}x${d.game_height}` : "";
    const preset = WINDOW_SIZES.some(([v]) => v === size) ? size : "custom";
    return `<div class="settings-grid">
      <div class="card-pad"><h3>Memory</h3>
        ${rangeRow("s-max", "Maximum memory", d.max_memory_mb, 1024, 32768, 512, gb(d.max_memory_mb))}
        ${rangeRow("s-min", "Minimum memory", d.min_memory_mb, 256, 8192, 256, gb(d.min_memory_mb))}
        <span class="muted small">Instances can set their own maximum.</span>
      </div>
      <div class="card-pad"><h3>Game window</h3>
        <div class="field"><label class="label" for="s-size">Size</label><select class="input" id="s-size">${WINDOW_SIZES.map(([v, l]) => `<option value="${v}" ${preset === v && (v !== "custom" || size) ? "selected" : ""}>${l}</option>`).join("")}</select></div>
        <div class="row" id="s-custom" ${preset === "custom" && size ? "" : "hidden"}><input class="input grow" id="s-w" type="number" min="320" max="7680" placeholder="Width" value="${d.game_width || ""}"><span class="muted">×</span><input class="input grow" id="s-h" type="number" min="240" max="4320" placeholder="Height" value="${d.game_height || ""}"></div>
        ${swRow(d, "fullscreen", "Start fullscreen", "F11 switches in-game")}
      </div>
      <div class="card-pad"><h3>Java</h3>
        <div class="field"><label class="label" for="s-java">Java</label><input class="input" id="s-java" placeholder="Automatic (recommended)" value="${esc(d.java_path || "")}"></div>
        <div class="field"><label class="label" for="s-jvm">Extra JVM arguments</label><input class="input" id="s-jvm" placeholder="Optional, for example -XX:+UseG1GC" value="${esc((d.extra_jvm_args || []).join(" "))}"></div>
        <span class="muted small">Vortex downloads the exact Java each version needs.</span>
      </div>
      <div class="card-pad"><h3>Launch speed</h3>
        ${swRow(d, "fast_launch", "Fast launch", "Starts from the files the last launch checked, with no downloads or hashing. A full check still runs every few days")}
        ${swRow(d, "tuned_jvm", "Tuned Java", "Low-pause garbage collector and class sharing on Java 17+, for a quicker start and smoother play")}
        <div class="row wrap"><button class="btn btn-sm" data-action="verify-files">${icon("check", 12)} Check files on next launch</button></div>
      </div>
      <div class="card-pad"><h3>Mod downloads</h3>
        ${swRow(d, "mods_with_dependencies", "Install required mods", "Adds the libraries a mod needs, like Fabric API")}
        ${swRow(d, "mods_allow_unstable", "Prefer beta versions", "Otherwise betas are used only when there's nothing else")}
        ${rangeRow("s-dl", "Parallel downloads", d.download_concurrency, 1, 64, 1, String(d.download_concurrency))}
      </div>
    </div>`;
  }
  if (tab === "ingame") {
    return `<div class="settings-grid">
      <div class="card-pad wide"><h3>Vortex mods</h3>
        ${swRow(d, "vortex_mods", "Add Vortex mods to the game", `On Fabric ${VORTEX_MC.join(", ")} instances. Fabric API is installed with them`)}
        ${swRow(d, "vortex_menu", "Vortex main menu", "Replaces Minecraft's title screen with your background and logo, and restyles buttons")}
        ${swRow(d, "performance_mods", "Performance mods", "Adds Sodium, Lithium, FerriteCore, EntityCulling and ImmediatelyFast to Fabric instances (ModernFix on Forge) for a much higher frame rate. Each is added once; remove one and it stays removed")}
        <div class="keys">
          <div><kbd>Right Shift</kbd><span>Mod menu</span></div>
          <div><kbd>C</kbd><span>Zoom (scroll to adjust)</span></div>
          <div><kbd>Left Alt</kbd><span>Freelook</span></div>
          <div><kbd>Ctrl</kbd><span>Sprint (Toggle Sprint on)</span></div>
        </div>
        <p class="muted small">Every feature is turned on and off in the mod menu, where you can also move HUD elements. Keys can be changed in Minecraft's Controls.</p>
      </div>
      <div class="card-pad"><h3>What's included</h3>
        <ul class="features">${INGAME_FEATURES.map(([n, dsc]) => `<li><strong>${n}</strong><span>${dsc}</span></li>`).join("")}</ul>
      </div>
      <div class="card-pad"><h3>When the game starts</h3>
        ${segRow(d, "after_launch", "Launcher", [["keep", "Stay open"], ["minimize", "Minimize"], ["hide", "Hide"]], "Hidden launchers come back when the game closes")}
        ${swRow(d, "open_log_on_launch", "Open the game log", "Shows Minecraft's output as it starts")}
      </div>
    </div>`;
  }
  if (tab === "data") {
    return `<div class="settings-grid">
      <div class="card-pad"><h3>Microsoft sign-in</h3>
        <div class="field"><label class="label" for="s-client">Azure client ID</label><input class="input" id="s-client" placeholder="00000000-0000-0000-0000-000000000000" value="${esc(d.microsoft_client_id || "")}"></div>
        <span class="muted small">Register an app in the Azure portal and have it approved for the Minecraft API. The README has the steps.</span>
      </div>
      <div class="card-pad"><h3>Storage</h3>
        <div class="field"><span class="label">Data folder</span><code class="path">${esc(S.dataDir)}</code></div>
        <div class="row wrap"><button class="btn btn-sm lq" data-action="folder-data">${icon("folder", 12)} Data</button><button class="btn btn-sm lq" data-action="folder-skins">${icon("folder", 12)} Skins</button><button class="btn btn-sm lq" data-action="folder-runtimes">${icon("folder", 12)} Java runtimes</button></div>
        <span class="muted small">Vortex Client ${esc(S.appVersion)}${isTauri ? "" : ", preview mode"}</span>
      </div>
      <div class="card-pad"><h3>Updates</h3>
        <div class="setting"><div><strong>Vortex Client ${esc(S.appVersion)}</strong><span>New versions install in a few seconds and keep your instances and settings</span></div><button class="btn btn-sm" data-action="check-update">Check now</button></div>
        ${swRow({ ...UI_DEFAULTS, ...d }, "auto_update", "Check automatically", "Looks for a new version each time the launcher opens")}
      </div>
    </div>`;
  }
  return `<div class="settings-grid">
    <div class="card-pad"><h3>Motion and sound</h3>
      ${swRow(d, "animations", "Animations", "The moving world, panel motion and particles")}
      ${swRow(d, "ui_sounds", "Sounds", "Clicks, launch sounds and alerts")}
    </div>
  </div>`;
}

const INGAME_FEATURES = [
  ["HUD", "Keystrokes, CPS, FPS, ping, coordinates, direction, armor, potions, saturation, memory, server, day counter, clock, speed, session timer, pack display"],
  ["PvP", "Toggle sprint and sneak, target HUD, item counter, reach and combo display, custom crosshair, hit particles, no hurt cam, low fire"],
  ["Camera", "Zoom with scroll, freelook, fullbright, your own nametag in third person"],
  ["World", "Block outline colour, time changer and clear weather (only on your screen)"],
  ["Screen", "Hide boss bars, hide the scoreboard"],
  ["Chat", "Timestamps"],
  ["Look", "Vortex main menu and buttons, your skin and cape on offline accounts"],
];

const glassLabel = (v) => (v < 25 ? "Clear" : v < 55 ? "Light" : v < 80 ? "Frosted" : "Heavy");

function renderSettings() {
  const d = (S.draft ||= JSON.parse(JSON.stringify(S.settings)));
  const dirty = JSON.stringify(d) !== JSON.stringify(S.settings);
  return {
    html: sheet("Settings", "", `<div class="tabs glass" id="settings-tabs"><span class="drop" aria-hidden="true"></span>${SETTINGS_TABS.map(([k, l]) => `<button class="tab ${S.settingsTab === k ? "on" : ""}" data-action="settings-tab" data-tab="${k}">${l}</button>`).join("")}</div>`,
      `<div class="settings-pane">${settingsBody(d)}</div>`,
      `<div class="savebar glass" id="savebar" ${dirty ? "" : "hidden"}><span>Unsaved changes</span><button class="btn btn-sm lq" data-action="settings-reset">Discard</button><button class="btn btn-sm btn-accent lq" data-action="settings-save">${icon("check", 12)} Save</button></div>`),
    mount(root) { mountSettings(root, d); },
  };
}

function mountSettings(root, d) {
  const range = (id, key, fmt, lo, hi, live) => {
    const el = $(id, root);
    if (!el) return;
    el.addEventListener("input", () => {
      d[key] = Number(el.value);
      el.style.setProperty("--fill", `${((d[key] - lo) / (hi - lo)) * 100}%`);
      $(`${id}-v`, root).textContent = fmt(d[key]);
      if (live) live(d[key]);
      markDirty();
    });
  };
  range("#s-max", "max_memory_mb", gb, 1024, isAndroid ? S.ramMax || 4096 : 32768);
  range("#s-min", "min_memory_mb", gb, 256, 8192);
  range("#s-dl", "download_concurrency", String, 1, 64);
  range("#s-day", "day_length_min", (v) => `${v} min`, 2, 40, (v) => world.setDayLength(v));
  range("#s-glass", "glass", glassLabel, 0, 100, () => applyLook(d));
  range("#s-wblur", "wall_blur", (v) => `${v} px`, 0, 40, (v) => { applyLook(d); $(".wall-corners", root)?.style.setProperty("--wb", `${v}px`); });
  range("#s-wdim", "wall_dim", (v) => `${v}%`, 0, 60, () => applyLook(d));
  $("#wall-file", root)?.addEventListener("change", (e) => { const f = e.target.files?.[0]; if (f) useWallpaper(f); });
  const text = (id, fn) => $(id, root)?.addEventListener("input", (e) => { fn(e.target.value); markDirty(); });
  text("#s-java", (v) => (d.java_path = v.trim() || null));
  text("#s-jvm", (v) => (d.extra_jvm_args = v.split(/\s+/).filter(Boolean)));
  text("#s-client", (v) => (d.microsoft_client_id = v.trim() || null));
  text("#s-w", (v) => (d.game_width = Number(v) || null));
  text("#s-h", (v) => (d.game_height = Number(v) || null));
  $("#s-size", root)?.addEventListener("change", (e) => {
    const v = e.target.value;
    $("#s-custom", root).hidden = v !== "custom";
    if (v === "custom") { d.game_width ||= 1280; d.game_height ||= 720; $("#s-w", root).value = d.game_width; $("#s-h", root).value = d.game_height; }
    else if (!v) { d.game_width = null; d.game_height = null; }
    else { const [w, h] = v.split("x").map(Number); d.game_width = w; d.game_height = h; }
    markDirty();
  });
  $$("[data-logo]", root).forEach((el) => mountLogo(el, renderStyle(Number(el.dataset.logo), 2.1)));
  $$("[data-scene]", root).forEach((el) => {
    const url = sceneThumb(el.dataset.scene, 176, 99);
    el.style.backgroundImage = `url('${url}')`;
  });
}

function refreshSettingsPane() {
  const pane = $("#page .settings-pane");
  if (!pane) return;
  pane.innerHTML = settingsBody(S.draft);
  pane.classList.remove("fresh");
  void pane.offsetWidth;
  pane.classList.add("fresh");
  hydrateIcons(pane);
  $$(".tabs", pane).forEach((g) => liquidTabs(g, true));
  mountSettings(pane, S.draft);
}

// ----------------------------------------------------- custom wallpaper --
// Your own wallpaper lives in the launcher's browser storage (IndexedDB).

const wallStore = {
  db() {
    return new Promise((resolve, reject) => {
      const req = indexedDB.open("vortex", 1);
      req.onupgradeneeded = () => req.result.createObjectStore("files");
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  },
  async op(mode, fn) {
    const db = await this.db();
    return new Promise((resolve, reject) => {
      const tx = db.transaction("files", mode);
      const req = fn(tx.objectStore("files"));
      tx.oncomplete = () => resolve(req?.result);
      tx.onerror = () => reject(tx.error);
    });
  },
  get() { return this.op("readonly", (st) => st.get("wallpaper")).catch(() => null); },
  put(blob) { return this.op("readwrite", (st) => st.put(blob, "wallpaper")); },
  del() { return this.op("readwrite", (st) => st.delete("wallpaper")).catch(() => {}); },
};

function setWallImage(blob) {
  if (S.customWallUrl) URL.revokeObjectURL(S.customWallUrl);
  S.customWall = null;
  S.customWallUrl = null;
  setCustomPicture(null);
  if (!blob) return Promise.resolve(false);
  return new Promise((resolve) => {
    const url = URL.createObjectURL(blob);
    const img = new Image();
    img.onload = () => {
      S.customWall = img;
      S.customWallUrl = url;
      setCustomPicture(img);
      resolve(true);
    };
    img.onerror = () => { URL.revokeObjectURL(url); resolve(false); };
    img.src = url;
  });
}

/** Shrink a picked image to at most 2560 px wide and keep it. */
async function useWallpaper(file) {
  try {
    const img = await new Promise((resolve, reject) => {
      const i = new Image();
      i.onload = () => resolve(i);
      i.onerror = reject;
      i.src = URL.createObjectURL(file);
    });
    const k = Math.min(1, 2560 / img.naturalWidth);
    const c = document.createElement("canvas");
    c.width = Math.round(img.naturalWidth * k);
    c.height = Math.round(img.naturalHeight * k);
    const g = c.getContext("2d");
    g.imageSmoothingQuality = "high";
    g.drawImage(img, 0, 0, c.width, c.height);
    URL.revokeObjectURL(img.src);
    const blob = await new Promise((resolve) => c.toBlob(resolve, "image/jpeg", 0.92));
    await wallStore.put(blob);
    await setWallImage(blob);
    S.draft.wall_custom = true;
    applyLook(S.draft);
    refreshSettingsPane();
    markDirty();
    sound.success();
  } catch (e) {
    toast("Couldn't use that image", errText(e), "error");
  }
}

function markDirty() {
  const dirty = JSON.stringify(S.draft) !== JSON.stringify(S.settings);
  const bar = $("#savebar");
  if (bar) bar.hidden = !dirty;
}

async function saveSettings(next) {
  S.settings = await call("save_settings", { settings: next });
  applyLook();
}

// ---------------------------------------------------------------- popover --

function closePopover() {
  $("#popover-root").innerHTML = "";
}

function openTimePopover() {
  const root = $("#popover-root");
  if (root.children.length) return closePopover();
  const d = { ...S.settings };
  root.innerHTML = `<div class="popover glass" role="dialog" aria-label="Time of day"><span class="label">Time of day</span>${skyControls(d)}</div>`;
  hydrateIcons(root);
  liquidTabs($("#sky-tabs", root), true);
  const day = $("#s-day", root);
  day?.addEventListener("input", () => {
    $("#s-day-v", root).textContent = `${day.value} min`;
    day.style.setProperty("--fill", `${((day.value - 2) / 38) * 100}%`);
    world.setDayLength(Number(day.value));
  });
  day?.addEventListener("change", () => saveSettings({ ...S.settings, day_length_min: Number(day.value) }).catch(() => {}));
}

// ----------------------------------------------------------------- modals --

function modal(html, onMount) {
  const root = $("#modal-root");
  const source = pressedSource();
  root.querySelectorAll(".scrim.closing").forEach((n) => n.remove());
  const isNew = !root.querySelector(".scrim");
  root.innerHTML = `<div class="scrim"><div class="modal glass hl" role="dialog" aria-modal="true"><span class="grabber" aria-hidden="true"></span>${html}</div></div>`;
  hydrateIcons(root);
  const scrim = root.firstElementChild;
  const box = scrim.firstElementChild;
  box._source = source;
  scrim.addEventListener("click", (e) => { if (e.target === scrim) closeModal(); });
  swipeToDismiss(box, () => closeModal(true), { grip: ".grabber" });
  onMount?.(root);
  if (isNew) morphIn(box, source);
  // Focus the first field; buttons stay unfocused so no ring shows on open.
  setTimeout(() => root.querySelector("input")?.focus({ preventScroll: true }), 60);
}

/** Close the dialog: it shrinks back into the button that opened it. */
function closeModal(instant = false) {
  const scrim = $("#modal-root .scrim:not(.closing)");
  if (!scrim) return;
  scrim.classList.add("closing");
  const box = scrim.querySelector(".modal");
  // Opened with the mouse: don't leave a focus ring on the card behind.
  if (box?._source && document.activeElement && !document.activeElement.matches("input, textarea, select")) document.activeElement.blur();
  if (instant || !anims()) { scrim.remove(); return; }
  dust.dissolve(box, { duration: 620, dir: "down" }).then(() => scrim.remove());
}

function confirmModal(title, text, okLabel = "Delete") {
  return new Promise((resolve) => {
    modal(`<h3>${esc(title)}</h3><p>${esc(text)}</p><div class="modal-actions"><button class="btn lq" id="m-no">Cancel</button><button class="btn btn-danger lq" id="m-yes">${esc(okLabel)}</button></div>`, (root) => {
      $("#m-no", root).onclick = () => { closeModal(); resolve(false); };
      $("#m-yes", root).onclick = () => { closeModal(); resolve(true); };
    });
  });
}

function editModal(inst) {
  const mem = inst.memoryMb || S.settings.max_memory_mb;
  modal(`<h3>${esc(inst.name)}</h3>
    <div class="setting"><div><strong>Custom memory</strong><span>Otherwise ${gb(S.settings.max_memory_mb)} from Settings</span></div><button class="switch" role="switch" id="e-on" aria-checked="${!!inst.memoryMb}" aria-label="Custom memory"></button></div>
    <div class="row" id="e-row"><input class="range grow" id="e-mem" type="range" min="1024" max="32768" step="512" value="${mem}" style="--fill:${((mem - 1024) / 31744) * 100}%"><span class="value" id="e-mem-v">${gb(mem)}</span></div>
    <div class="field"><label class="label" for="e-server">Join a server on start</label><input class="input" id="e-server" placeholder="host or host:port" value="${esc(inst.joinServer || "")}"></div>
    <div class="field"><label class="label" for="e-jvm">JVM arguments</label><input class="input" id="e-jvm" placeholder="Optional" value="${esc(inst.jvmArgs || "")}"></div>
    <div class="modal-actions"><button class="btn lq" id="e-cancel">Cancel</button><button class="btn btn-accent lq" id="e-save">${icon("check", 14)} Save</button></div>`, (root) => {
    const sw = $("#e-on", root), range = $("#e-mem", root), row = $("#e-row", root);
    const sync = () => (row.style.opacity = sw.getAttribute("aria-checked") === "true" ? "1" : "0.35");
    sync();
    sw.onclick = () => { const v = sw.getAttribute("aria-checked") !== "true"; sw.setAttribute("aria-checked", v); sound.toggle(v); sync(); };
    range.oninput = () => { range.style.setProperty("--fill", `${((range.value - 1024) / 31744) * 100}%`); $("#e-mem-v").textContent = gb(Number(range.value)); };
    $("#e-cancel", root).onclick = closeModal;
    $("#e-save", root).onclick = async () => {
      try {
        const updated = await call("update_instance", {
          name: inst.name,
          memoryMb: sw.getAttribute("aria-checked") === "true" ? Number(range.value) : null,
          joinServer: $("#e-server", root).value.trim() || null,
          jvmArgs: $("#e-jvm", root).value,
        });
        S.instances = S.instances.map((i) => (i.name === inst.name ? updated : i));
        closeModal();
        sound.success();
        toast("Saved", `${inst.name} is updated.`, "success");
        render();
      } catch (e) {
        sound.error();
        toast("Couldn't save", errText(e), "error");
      }
    };
  });
}

// ---------------------------------------------------------------- actions --

async function refreshInstances() {
  S.instances = await call("list_instances");
  if (!S.instances.some((i) => i.name === S.selected)) S.selected = S.instances[0]?.name || null;
  fillLogInstances();
}

function select(name) {
  S.selected = name;
  store.set("vortex.selected", name);
  player.setActivity(runOf(name).phase);
  warmSelected();
}

/** Ask the core to pre-load the selected instance's game files. */
function warmSelected() {
  const inst = selectedInstance();
  if (!inst || S.fast[inst.name] !== undefined) return;
  S.fast[inst.name] = false;
  call("warm_up", { name: inst.name })
    .then((bytes) => {
      S.fast[inst.name] = bytes > 0;
      if (bytes > 0 && S.route === "home" && selectedInstance()?.name === inst.name && !$(".hero-chip.fast")) {
        $(".hero-top")?.insertAdjacentHTML("beforeend", `<span class="hero-chip glass glassy fast pop-in" title="Files are ready: the game starts without checking or downloading anything">${icon("bolt", 12)}<span>Instant launch</span></span>`);
      }
    })
    .catch(() => { delete S.fast[inst.name]; });
}

// ---------------------------------------------------------------- updates --

let updateOffered = false;

/** Look for a newer launcher; offer it in a pop-up. */
async function checkForUpdate(manual = false) {
  try {
    const u = await call("check_update");
    if (!u.available) {
      if (manual) toast("You're up to date", `Vortex Client ${u.current} is the latest version.`, "success");
      return;
    }
    if (updateOffered && !manual) return;
    updateOffered = true;
    // Found on its own at start: install straight away (unless a game is
    // running), so every player gets the new version without a choice.
    const busy = Object.values(S.runs).some((r) => r.phase !== "idle");
    showUpdate(u, { auto: !manual && !busy });
  } catch (e) {
    if (manual) toast("Couldn't check for updates", errText(e), "warn");
  }
}

function showUpdate(u, { auto = false } = {}) {
  const notes = (u.notes || "").split("\n").filter((l) => !/^\s*#/.test(l)).map((l) => l.replace(/^[*\-\s]+/, "").trim()).filter(Boolean).slice(0, 6);
  const mb = u.size ? `${(u.size / 1048576).toFixed(1)} MB` : "";
  modal(`<div class="upd">
      <div class="upd-head"><img class="upd-logo" src="img/logo.png" alt=""><div><span class="upd-kicker">Update available</span><h3>Vortex Client ${esc(u.version)}</h3><p class="muted small">You have ${esc(u.current)}${u.published ? ` · released ${esc(u.published)}` : ""}${mb ? ` · ${mb}` : ""}</p></div></div>
      ${notes.length ? `<ul class="upd-notes">${notes.map((n) => `<li>${esc(n)}</li>`).join("")}</ul>` : ""}
      <div class="upd-progress" id="upd-progress" hidden><div class="upd-bar"><i id="upd-fill"></i></div><span id="upd-text">Starting download</span></div>
      <div class="modal-actions" id="upd-actions"><button class="btn" id="upd-later">Later</button><button class="btn btn-accent" id="upd-now">${icon("download", 12)} ${isAndroid ? "Update" : "Update and restart"}</button></div>
    </div>`, (root) => {
    $("#upd-later", root).onclick = () => closeModal();
    if (auto) setTimeout(() => $("#upd-now", root)?.click(), 700);
    $("#upd-now", root).onclick = async () => {
      if (Object.values(S.runs).some((r) => r.phase !== "idle")) { toast("Close the game first", "The launcher restarts to finish updating.", "warn"); return; }
      $("#upd-actions", root).hidden = true;
      $("#upd-progress", root).hidden = false;
      try {
        await call("install_update", { url: u.url, version: u.version });
      } catch (e) {
        $("#upd-actions", root).hidden = false;
        $("#upd-text", root).textContent = `Update failed: ${errText(e)}`;
        sound.error();
      }
    };
  });
}

on("update-progress", (p) => {
  const fill = $("#upd-fill"), text = $("#upd-text");
  if (!fill || !text) return;
  if (p.stage === "download") {
    const pct = p.total ? Math.round((p.done / p.total) * 100) : 0;
    fill.style.width = `${pct}%`;
    text.textContent = p.total ? `Downloading ${pct}% · ${(p.done / 1048576).toFixed(1)} of ${(p.total / 1048576).toFixed(1)} MB` : "Downloading";
  } else if (p.stage === "install") { fill.style.width = "100%"; text.textContent = "Installing"; }
  else if (p.stage === "restart") { text.textContent = "Restarting into the new version"; }
  else if (p.stage === "android") { fill.style.width = "100%"; text.textContent = "Tap Install to finish. Your instances and settings stay."; }
});

/** Swap the news cards in place (no page re-render). */
function refreshNews() {
  const box = $("#news-cards");
  if (!box) return;
  box.innerHTML = newsCards();
  hydrateIcons(box);
  if (anims()) [...box.children].forEach((c, i) => c.animate([{ opacity: 0, translate: "0px 10px", filter: "blur(6px)" }, { opacity: 1, translate: "0px 0px", filter: "blur(0px)" }], { duration: 480, delay: i * 70, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)", fill: "backwards" }));
}

/** The VC logo as a canvas (for the in-game menu). */
function logoCanvas() {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const c = document.createElement("canvas");
      c.width = img.naturalWidth;
      c.height = img.naturalHeight;
      c.getContext("2d").drawImage(img, 0, 0);
      resolve(c);
    };
    img.onerror = reject;
    img.src = "img/logo.png";
  });
}

/** Give the game the launcher's background and logo for the Vortex main menu. */
async function exportMenuArt(inst) {
  const s = S.settings || {};
  if (!inst || inst.loader !== "fabric" || !VORTEX_MC.includes(inst.minecraftVersion) || s.vortex_mods === false || s.vortex_menu === false) return;
  try {
    const custom = s.wall_custom && S.customWall;
    const sig = `${custom ? `custom:${S.customWall.naturalWidth}x${S.customWall.naturalHeight}:${S.customWallUrl?.length}` : "golden"}:${s.wall_dim ?? 12}:v3`;
    if (store.get("vortex.menuArt") === sig) return; // already given to the game
    const bg = sceneStill(custom ? "custom" : "golden", 1600, 900, world.fixedTime ? undefined : world.hour());
    const logo = await logoCanvas();
    await call("save_menu_art", { background: bg.toDataURL("image/png"), logo: logo.toDataURL("image/png") });
    store.set("vortex.menuArt", sig);
  } catch (e) {
    console.warn("menu art", e);
  }
}

async function play(name, server = null) {
  if (runOf(name).phase !== "idle") return;
  if (!selectedAccount()) {
    sound.error();
    toast("Add an account first", "Offline accounts work for singleplayer.", "warn");
    go("accounts");
    return;
  }
  select(name);
  S.runs[name] = { phase: "preparing", stage: "Starting", done: 0, total: 0 };
  S.logs[name] = [];
  S.log.instance = name;
  fillLogInstances();
  if (S.log.open) renderLog();
  if (S.route !== "home") go("home");
  updateRunUI(name);
  try {
    await exportMenuArt(S.instances.find((i) => i.name === name));
    await call("launch_instance", { name, server });
  } catch (e) {
    S.runs[name] = { phase: "idle" };
    updateRunUI(name);
    sound.error();
    toast("Couldn't start the game", errText(e), "error", 7000);
  }
}

const actions = {
  route(el) { go(el.dataset.route); },
  "join-server"(el, e) {
    e.stopPropagation();
    const inst = selectedInstance();
    if (!inst) { toast("Create an instance first", "Pick a version to play on the server with.", "warn"); go("new"); return; }
    toast(`Joining ${el.dataset.address}`, `With ${inst.name}. Servers decide which versions they let in.`, "info", 3500);
    play(inst.name, el.dataset.address);
  },
  "add-server"() { addServerModal(); },
  "customize-home"() { customizeModal(); },
  "check-update"() { checkForUpdate(true); },
  "wall-pick"() { $("#wall-file")?.click(); },
  async "wall-reset"() {
    S.draft.wall_custom = false;
    await wallStore.del();
    setWallImage(null);
    applyLook(S.draft);
    refreshSettingsPane();
    markDirty();
  },
  async "verify-files"() {
    for (const i of S.instances) await call("verify_instance", { name: i.name }).catch(() => {});
    S.fast = {};
    sound.success();
    toast("Files will be checked", "The next launch of each instance checks and repairs every file.", "success");
  },
  play(el, e) { e.stopPropagation(); play(el.dataset.name); },
  async stop(el, e) { e.stopPropagation(); await call("stop_instance", { name: el.dataset.name }); },
  "select-home"(el) { select(el.dataset.name); go("home"); },
  select(el) {
    select(el.dataset.name);
    sound.pop();
    if ($("#switcher")) closeSwitcher(false, () => render());
    else render();
  },
  "toggle-switcher"(el) { toggleSwitcher(el); },
  side(el) {
    S.side = el.dataset.side;
    store.set("vortex.side", S.side);
    $$("#side-tabs .tab").forEach((t) => t.classList.toggle("on", t.dataset.side === S.side));
    liquidTabs($("#side-tabs"));
    const body = $("#side-body");
    body.innerHTML = sideBody();
    body.querySelector(".tabpane")?.classList.add("fresh");
    hydrateIcons(body);
  },
  edit(el, e) {
    e.stopPropagation();
    const inst = S.instances.find((i) => i.name === el.dataset.name);
    if (inst) editModal(inst);
  },
  folder(el, e) {
    e.stopPropagation();
    call("open_path", { target: "instance", name: el.dataset.name }).catch((er) => toast("Couldn't open the folder", errText(er), "error"));
  },
  "folder-mods"() { call("open_path", { target: "mods", name: S.mods.instance }).catch((er) => toast("Couldn't open the folder", errText(er), "error")); },
  native(el) { call("open_native", { what: el.dataset.what }).catch((er) => toast("Couldn't open it", errText(er), "error")); },
  "folder-data"() { call("open_path", { target: "data" }).catch((er) => toast("Couldn't open the folder", errText(er), "error")); },
  async delete(el, e) {
    e.stopPropagation();
    const name = el.dataset.name;
    if (!(await confirmModal(`Delete ${name}?`, "Its worlds, mods and settings are removed from this computer. This can't be undone."))) return;
    try {
      const gone = vanish(el);
      S.instances = await call("delete_instance", { name });
      await gone;
      if (S.selected === name) S.selected = S.instances[0]?.name || null;
      sound.pop();
      toast("Deleted", name, "info");
      fillLogInstances();
      render();
    } catch (er) {
      sound.error();
      toast("Couldn't delete", errText(er), "error");
    }
  },
  news(el) {
    const n = S.news?.[Number(el.dataset.idx)];
    if (!n) return;
    modal(`${n.image_url ? `<div class="news-hero" style="background-image:url('${esc(n.image_url)}')"></div>` : ""}
      <div class="row"><span class="chip ${n.kind === "snapshot" ? "accent" : ""}">${n.kind === "snapshot" ? "Snapshot" : "Release"}</span><span class="muted">${esc(n.date)}</span></div>
      <h3>${esc(n.title)}</h3><p>${esc(n.summary)}</p>
      <div class="modal-actions"><button class="btn lq" id="n-close">Close</button><button class="btn btn-accent lq" id="n-new">Play ${esc(n.version)}</button></div>`, (root) => {
      $("#n-close", root).onclick = closeModal;
      $("#n-new", root).onclick = () => {
        closeModal();
        S.wizard = newWizard();
        S.wizard.version = n.version;
        if (n.kind === "snapshot") S.wizard.kinds.snapshot = true;
        go("new");
      };
    });
  },
  "inst-sort"(el) { S.instSort = el.dataset.sort; render(); },
  "wiz-kind"(el) { const w = S.wizard; w.kinds[el.dataset.kind] = !w.kinds[el.dataset.kind]; updateWizard(); },
  "wiz-pick"(el) {
    const w = S.wizard;
    if (w.version !== el.dataset.id) { w.version = el.dataset.id; w.loaderVersions = {}; w.loaderVersion = null; }
    const kind = S.versions?.versions.find((v) => v.id === w.version)?.kind;
    if (kind && !w.kinds[kind]) w.kinds[kind] = true;
    updateWizard();
  },
  "wiz-next"() { const w = S.wizard; if (w.step === 1) loadLoaderVersions(w); w.step = Math.min(3, w.step + 1); w.scrolled = false; updateWizard(true); },
  "wiz-back"() { S.wizard.step = Math.max(1, S.wizard.step - 1); S.wizard.scrolled = false; updateWizard(true); },
  "wiz-loader"(el) { const w = S.wizard; w.loader = el.dataset.kind; w.loaderVersion = null; updateWizard(); },
  async "wiz-create"(el) {
    const w = S.wizard;
    const name = (w.name || autoName(w)).trim();
    el.disabled = true;
    try {
      const inst = await call("create_instance", {
        name,
        version: w.version,
        loader: w.loader,
        loaderVersion: w.loader === "vanilla" ? null : w.loaderVersion,
        memoryMb: w.memory !== S.settings.max_memory_mb ? w.memory : null,
      });
      await refreshInstances();
      select(inst.name);
      S.wizard = null;
      sound.success();
      toast("Instance created", `${inst.name} is ready. Press Play to install and launch it.`, "success");
      go("home");
      setTimeout(() => player.wave(2), 600);
    } catch (e) {
      el.disabled = false;
      sound.error();
      toast("Couldn't create the instance", errText(e), "error");
    }
  },
  async "mod-toggle"(el) {
    const enabled = el.dataset.on !== "true";
    sound.toggle(enabled);
    el.setAttribute("aria-checked", enabled);
    try {
      S.mods.list = await call("toggle_mod", { name: S.mods.instance, file: el.dataset.file, enabled, kind: S.mods.kind });
      setTimeout(render, 380);
    } catch (e) {
      toast("Couldn't change the mod", errText(e), "error");
    }
  },
  async "mod-delete"(el) {
    if (!(await confirmModal("Delete this mod?", el.dataset.file))) return;
    try {
      const gone = vanish(el);
      S.mods.list = await call("delete_mod", { name: S.mods.instance, file: el.dataset.file, kind: S.mods.kind });
      await gone;
      sound.pop();
      render();
    } catch (e) {
      toast("Couldn't delete the mod", errText(e), "error");
    }
  },
  "mods-refresh"() { S.mods.loadedFor = null; render(); },
  "mods-tab"(el) {
    S.modsTab = el.dataset.tab;
    store.set("vortex.modsTab", S.modsTab);
    render();
  },
  "mods-kind"(el) {
    S.mods.kind = el.dataset.kind;
    S.mods.list = [];
    S.browse.hits = [];
    S.browse.key = "";
    render();
  },
  "mr-install"(el, e) { e.stopPropagation(); installProject(el.dataset.id); },
  "mr-install-version"(el) { installProject(el.dataset.id, el.dataset.version); },
  "mr-open"(el) { openProject(el.dataset.id); },
  "mr-more"() { searchModrinth(false); },
  "open-url"(el) { call("open_url", { url: el.dataset.url }).catch((er) => toast("Couldn't open the link", errText(er), "error")); },
  "folder-packs"() { call("open_path", { target: "resourcepacks", name: S.mods.instance }).catch((er) => toast("Couldn't open the folder", errText(er), "error")); },
  "folder-shaders"() { call("open_path", { target: "shaderpacks", name: S.mods.instance }).catch((er) => toast("Couldn't open the folder", errText(er), "error")); },
  "folder-skins"() { call("open_path", { target: "skins" }).catch((er) => toast("Couldn't open the folder", errText(er), "error")); },
  "folder-runtimes"() { call("open_path", { target: "runtimes" }).catch((er) => toast("Couldn't open the folder", errText(er), "error")); },
  async "skin-use"(el) {
    if (!selectedAccount()) { toast("Add an account first", "Skins belong to an account.", "warn"); go("accounts"); return; }
    try {
      S.skins = await call("skin_apply", { id: el.dataset.id });
      await loadSkin();
      sound.success();
      toast("Skin changed", S.skins.microsoft ? "Uploaded to your Microsoft account." : "You'll wear it in-game with Vortex mods.", "success");
      render();
      player.wave(1.6);
    } catch (e) {
      sound.error();
      toast("Couldn't change the skin", errText(e), "error", 7000);
    }
  },
  async "skin-model"(el) {
    S.skins = await call("skin_update", { id: el.dataset.id, slim: el.dataset.slim === "true" });
    if (S.skins.applied === el.dataset.id) await loadSkin();
    render();
  },
  async "skin-remove"(el) {
    const k = S.skins?.skins.find((x) => x.id === el.dataset.id);
    if (!(await confirmModal(`Delete ${k?.name || "this skin"}?`, "It's removed from your skins on this computer.", "Delete"))) return;
    const gone = vanish(el);
    S.skins = await call("skin_remove", { id: el.dataset.id });
    await gone;
    await loadSkin();
    render();
  },
  async "skin-player"() {
    const input = $("#skin-player");
    const name = input?.value.trim();
    if (!name) return;
    try {
      S.skins = await call("skin_from_player", { username: name });
      sound.success();
      toast("Skin copied", `${name}'s skin is in your skins.`, "success");
      render();
    } catch (e) {
      sound.error();
      toast("Couldn't get that skin", errText(e), "error");
    }
  },
  async cape(el) {
    if (!selectedAccount()) { toast("Add an account first", "Capes belong to an account.", "warn"); return; }
    S.skins = await call("set_cape", { cape: el.dataset.cape });
    await loadSkin();
    sound.pop();
    render();
  },
  "settings-tab"(el) {
    S.settingsTab = el.dataset.tab;
    store.set("vortex.settingsTab", S.settingsTab);
    const group = $("#settings-tabs");
    $$(".tab", group).forEach((t) => t.classList.toggle("on", t === el));
    liquidTabs(group);
    const pane = $("#page .settings-pane");
    const swept = pane && anims() && phone();
    if (swept) dustAway(pane, { duration: 560 });
    refreshSettingsPane();
    if (swept) dust.reveal(pane, { duration: 560, dir: "down" });
    else if (pane && anims()) paneIn(pane);
  },
  "settings-noop"() {},
  "set-choice"(el) {
    const key = el.dataset.key;
    const v = el.dataset.num ? Number(el.dataset.value) : el.dataset.value;
    S.draft[key] = v;
    sound.pop();
    $$(`[data-action="set-choice"][data-key="${key}"]`).forEach((b) => {
      const on = String(b.dataset.value) === String(v);
      b.classList.toggle("on", on);
      if (b.hasAttribute("aria-pressed")) b.setAttribute("aria-pressed", on);
    });
    applyLook(S.draft);
    markDirty();
  },
  async "acc-use"(el) {
    S.accounts = await call("select_account", { uuid: el.dataset.uuid });
    sound.success();
    updateAccount();
    await loadSkin();
    render();
  },
  async "acc-remove"(el) {
    const acc = S.accounts.find((a) => a.uuid === el.dataset.uuid);
    if (!(await confirmModal(`Remove ${acc?.username}?`, "You can add it again later.", "Remove"))) return;
    const gone = vanish(el);
    S.accounts = await call("remove_account", { uuid: el.dataset.uuid });
    await gone;
    updateAccount();
    await loadSkin();
    render();
  },
  async "acc-offline"() {
    const input = $("#offline-name");
    const name = input?.value.trim();
    if (!name) return;
    try {
      S.accounts = await call("add_offline_account", { username: name });
      sound.success();
      toast("Account added", `You're playing as ${name}.`, "success");
      updateAccount();
      await loadSkin();
      render();
    } catch (e) {
      sound.error();
      toast("Couldn't add the account", errText(e), "error");
    }
  },
  async "acc-ms"() {
    modal(`<h3>Sign in with Microsoft</h3><div class="bar"><i></i></div><p id="ms-text">Asking Microsoft for a sign-in code.</p><div class="modal-actions"><button class="btn lq" id="ms-close">Close</button></div>`, (root) => {
      $("#ms-close", root).onclick = closeModal;
    });
    try {
      S.accounts = await call("microsoft_login");
      closeModal();
      sound.success();
      toast("Signed in", `Welcome, ${selectedAccount()?.username}.`, "success");
      updateAccount();
      await loadSkin();
      render();
    } catch (e) {
      closeModal();
      sound.error();
      toast("Microsoft sign-in failed", errText(e), "error", 8000);
    }
  },
  "set-sky"(el) {
    const mode = el.dataset.sky;
    world.setMode(mode);
    const group = el.closest(".tabs");
    $$(".tab", group).forEach((t) => t.classList.toggle("on", t === el));
    liquidTabs(group);
    const field = group.parentElement.querySelector("#daylen-field");
    if (field) field.hidden = mode !== "cycle";
    if (S.route === "settings" && S.draft && !el.closest(".popover")) {
      S.draft.sky = mode;
      markDirty();
    } else {
      saveSettings({ ...S.settings, sky: mode }).catch(() => {});
      if (S.draft) S.draft.sky = mode;
    }
  },
  "set-bool"(el) {
    const key = el.dataset.key;
    const cur = key in S.draft ? S.draft[key] : UI_DEFAULTS[key];
    S.draft[key] = !cur;
    sound.toggle(S.draft[key]);
    el.setAttribute("aria-checked", S.draft[key]);
    applyLook(S.draft);
    markDirty();
  },
  "settings-reset"() { S.draft = null; applyLook(); render(); },
  async "settings-save"() {
    try {
      await saveSettings(S.draft);
      S.draft = null;
      sound.success();
      toast("Settings saved", "", "success", 2400);
      render();
    } catch (e) {
      sound.error();
      toast("Couldn't save settings", errText(e), "error");
    }
  },
};

// ------------------------------------------------------------------- log --

const MAX_LINES = 5000;

function fillLogInstances() {
  const sel = $("#log-instance");
  const cur = S.log.instance || S.selected;
  sel.innerHTML = S.instances.map((i) => `<option value="${esc(i.name)}" ${i.name === cur ? "selected" : ""}>${esc(i.name)}</option>`).join("");
  S.log.instance = sel.value || null;
}

function lineVisible(l) {
  const lv = S.log.level;
  if (lv === "warn" && l.level !== "warn" && l.level !== "error") return false;
  if (lv === "error" && l.level !== "error") return false;
  return !S.log.search || l.text.toLowerCase().includes(S.log.search);
}

function lineEl(l) {
  const d = document.createElement("div");
  d.className = `ln ${l.level}`;
  d.textContent = l.text;
  return d;
}

function renderLog() {
  const body = $("#log-body");
  const lines = (S.logs[S.log.instance] || []).filter(lineVisible).slice(-1500);
  body.innerHTML = "";
  if (!lines.length) {
    body.innerHTML = `<div class="log-empty">Nothing here yet. Press Play and the game's output appears here.</div>`;
    return;
  }
  const frag = document.createDocumentFragment();
  lines.forEach((l) => frag.appendChild(lineEl(l)));
  body.appendChild(frag);
  body.scrollTop = body.scrollHeight;
}

function openLog(name) {
  if (name) { S.log.instance = name; fillLogInstances(); $("#log-instance").value = name; }
  S.log.open = true;
  $("#log").classList.add("open");
  $("#log-badge").hidden = true;
  renderLog();
  requestAnimationFrame(() => liquidTabs($("#log-filter"), true));
}

function toggleLog() {
  if (S.log.open) { S.log.open = false; $("#log").classList.remove("open"); }
  else openLog();
}

// ------------------------------------------------------------ live events --

function bindEvents() {
  on("launch-progress", (p) => {
    const r = S.runs[p.instance];
    if (!r || r.phase === "running") return;
    S.runs[p.instance] = { phase: "preparing", stage: p.stage, done: p.done, total: p.total };
    updateRunUI(p.instance);
  });
  on("game-state", (ev) => {
    const name = ev.instance;
    if (ev.state === "preparing") {
      S.runs[name] = { ...(S.runs[name] || {}), phase: "preparing", stage: S.runs[name]?.stage || "Preparing" };
    } else if (ev.state === "running") {
      if (S.settings?.open_log_on_launch) openLog(name);
      S.runs[name] = { phase: "running" };
      S.started[name] = Date.now();
      sound.launch();
      toast("Minecraft is running", `${name} started. Have fun.`, "success");
    } else {
      const failed = ev.state === "failed" || (ev.code != null && ev.code !== 0);
      const wasRunning = S.runs[name]?.phase === "running";
      S.runs[name] = { phase: "idle" };
      delete S.started[name];
      if (failed) {
        sound.error();
        toast(ev.state === "failed" ? "Couldn't start the game" : "The game crashed", ev.error || `Exit code ${ev.code}. The log has the details.`, "error", 9000);
        if (ev.state !== "failed") openLog(name);
      } else if (wasRunning) toast("Game closed", name, "info", 2500);
      refreshInstances().then(() => { if (S.route === "instances") render(); else updateRunUI(name); });
    }
    updateRunUI(name);
  });
  on("game-log", (ev) => {
    const list = (S.logs[ev.instance] ||= []);
    list.push(...ev.lines);
    if (list.length > MAX_LINES) list.splice(0, list.length - MAX_LINES);
    if (S.log.open && S.log.instance === ev.instance) {
      const body = $("#log-body");
      const stick = body.scrollHeight - body.scrollTop - body.clientHeight < 40;
      body.querySelector(".log-empty")?.remove();
      const frag = document.createDocumentFragment();
      ev.lines.filter(lineVisible).forEach((l) => frag.appendChild(lineEl(l)));
      body.appendChild(frag);
      while (body.childElementCount > 1500) body.firstElementChild.remove();
      if (stick) body.scrollTop = body.scrollHeight;
    } else if (ev.lines.some((l) => l.level === "error")) {
      $("#log-badge").hidden = false;
    }
  });
  on("device-code", (ev) => {
    const text = $("#ms-text");
    if (!text) return;
    const box = text.closest(".modal");
    box.innerHTML = `<h3>Sign in with Microsoft</h3><p>Open the Microsoft page and enter this code.</p>
      <div class="code">${esc(ev.userCode)}</div>
      <div class="row" style="justify-content:center"><button class="btn btn-sm lq" id="ms-copy">${icon("copy", 12)} Copy code</button><button class="btn btn-sm btn-accent lq" id="ms-open">${icon("link", 12)} Open ${esc(ev.verificationUri.replace("https://", ""))}</button></div>
      <div class="bar"><i></i></div><p style="text-align:center">Waiting for you to finish signing in.</p>`;
    $("#ms-copy").onclick = () => {
      navigator.clipboard?.writeText(ev.userCode).then(() => toast("Copied", ev.userCode, "success", 1800), () => {});
    };
    $("#ms-open").onclick = () => call("open_url", { url: ev.verificationUri });
    call("open_url", { url: ev.verificationUri }).catch(() => {});
  });
  onFileDrop(async (paths, type) => {
    const zone = $("#dropzone");
    if (type) { zone?.classList.toggle("hot", type === "enter" || type === "over"); return; }
    zone?.classList.remove("hot");
    if (S.route === "skins" && paths?.length) {
      try {
        S.skins = await call("skin_import_files", { paths: paths.filter((f) => /\.png$/i.test(f)) });
        sound.success();
        toast("Skin added", "Press Use to wear it.", "success");
        render();
      } catch (e) {
        toast("Couldn't add the skin", errText(e), "error");
      }
      return;
    }
    if (S.route !== "mods" || !S.mods.instance || !paths?.length) return;
    try {
      S.mods.list = await call("import_mods", { name: S.mods.instance, paths });
      sound.success();
      toast("Mods installed", `${paths.length} file${paths.length === 1 ? "" : "s"} added.`, "success");
      await refreshInstances();
      render();
    } catch (e) {
      toast("Couldn't install the mods", errText(e), "error");
    }
  });
}

// ------------------------------------------------------------------ chrome --

function updateAccount() {
  const a = selectedAccount();
  $("#account-name").textContent = a ? a.username : "No account";
  player.setName(a ? a.username : "");
}

function bindChrome() {
  hydrateIcons(document);
  bindLiquid();
  if (isAndroid) $('#nav .tab[data-route="skins"]')?.remove();
  applyPhone();
  matchMedia("(max-width: 760px)").addEventListener?.("change", () => { applyPhone(); liquidTabs($("#nav"), true); requestAnimationFrame(placeHero); });
  $$("#nav .tab").forEach((b) => b.addEventListener("click", () => go(b.dataset.route)));
  // Android's back button: close what's open, then go Home, then leave.
  window.__vortexBack = () => {
    if ($("#modal-root .scrim:not(.closing)")) { closeModal(); return true; }
    if ($("#popover-root").children.length) { closePopover(); return true; }
    if ($("#switcher")) { closeSwitcher(); return true; }
    if (S.log.open) { toggleLog(); return true; }
    if (S.route === "new") { go("instances"); return true; }
    if (S.route !== "home") { go("home"); return true; }
    return false;
  };
  if (isAndroid) on("android-resume", async () => {
    try {
      S.instances = await call("list_instances");
      S.accounts = await call("bootstrap").then((b) => b.accounts);
      if (!S.instances.some((i) => i.name === S.selected)) S.selected = S.instances[0]?.name || null;
      updateAccount();
      if (!$("#modal-root .scrim")) render();
    } catch { /* keep what's shown */ }
  });
  $("#account-chip").addEventListener("click", () => go("accounts"));
  $("#time-chip").addEventListener("click", openTimePopover);
  $("#log-toggle").addEventListener("click", toggleLog);
  $("#theme-toggle")?.addEventListener("click", async () => {
    const cur = document.body.classList.contains("theme-light") ? "light" : "dark";
    const next = { ...S.settings, theme: cur === "light" ? "dark" : "light" };
    if (S.draft) S.draft.theme = next.theme;
    sound.toggle(next.theme === "light");
    const btn = $("#theme-toggle").getBoundingClientRect();
    const x = btn.left + btn.width / 2, y = btn.top + btn.height / 2;
    const swap = () => { S.settings = next; applyLook(); };
    if (document.startViewTransition && anims()) {
      // The new theme floods out from the button with a soft edge, like
      // light filling a room (a warm sunrise glow going light, a violet
      // dusk going dark), while the old one sinks back behind it.
      const root = document.documentElement;
      const soft = 0.16 * Math.max(innerWidth, innerHeight);
      const r = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y)) + soft;
      const toLight = next.theme === "light";
      root.style.setProperty("--vx", `${x}px`);
      root.style.setProperty("--vy", `${y}px`);
      root.style.setProperty("--vend", `${Math.ceil(r)}px`);
      root.style.setProperty("--vsoft", `${Math.round(soft)}px`);
      root.classList.add("theme-vt");
      const vt = document.startViewTransition(swap);
      vt.ready.then(() => {
        const ease = (k) => (k < 0.5 ? 4 * k * k * k : 1 - Math.pow(-2 * k + 2, 3) / 2);
        dust.glowRing(x, y, (k) => r * ease(k) - soft * 0.35, { duration: 1000, width: soft, light: toLight });
        dust.ring(x, y, (k) => r * ease(k) - soft * 0.4, { duration: 1000, perFrame: 14,
          colors: toLight ? ["rgba(255,226,170,1)", "rgba(255,196,110,1)", "rgba(255,244,222,1)"] : ["rgba(170,150,255,1)", "rgba(120,110,230,1)", "rgba(210,200,255,1)"] });
      }, () => {});
      vt.finished.finally(() => root.classList.remove("theme-vt"));
    } else swap();
    try { await saveSettings(next); } catch { /* kept locally */ }
  });
  matchMedia("(prefers-color-scheme: light)").addEventListener?.("change", () => applyLook());
  $("#log-close").addEventListener("click", toggleLog);
  $("#log-clear").addEventListener("click", () => { S.logs[S.log.instance] = []; renderLog(); });
  $("#log-copy").addEventListener("click", () => {
    const text = (S.logs[S.log.instance] || []).map((l) => l.text).join("\n");
    navigator.clipboard?.writeText(text).then(() => toast("Copied", "The log is on your clipboard.", "success", 1800), () => toast("Couldn't copy", "Select the text and copy it instead.", "warn"));
  });
  $("#log-instance").addEventListener("change", (e) => { S.log.instance = e.target.value; renderLog(); });
  $$("#log-filter .tab").forEach((b) => b.addEventListener("click", () => {
    S.log.level = b.dataset.level;
    $$("#log-filter .tab").forEach((x) => x.classList.toggle("on", x === b));
    liquidTabs($("#log-filter"));
    renderLog();
  }));
  $("#log-search").addEventListener("input", (e) => { S.log.search = e.target.value.toLowerCase(); renderLog(); });
  swipeToDismiss($("#log"), () => { if (S.log.open) toggleLog(); }, { grip: ".log-head .title" });

  document.addEventListener("click", (e) => {
    const el = e.target.closest("[data-action]");
    if (el && !el.disabled) {
      const fn = actions[el.dataset.action];
      if (fn) fn(el, e);
    }
    if (!e.target.closest(".popover, #time-chip")) closePopover();
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      if ($("#modal-root .scrim:not(.closing)")) closeModal();
      else if ($("#popover-root").children.length) closePopover();
      else if ($("#switcher")) closeSwitcher();
      else if (S.log.open) toggleLog();
    }
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "l") { e.preventDefault(); toggleLog(); }
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") { const inst = selectedInstance(); if (inst) play(inst.name); }
    if (e.altKey && /^[1-5]$/.test(e.key)) go(["home", "instances", "mods", "skins", "settings"][Number(e.key) - 1]);
  });
  window.addEventListener("resize", () => {
    liquidTabs($("#nav"), true);
    $$("#page .tabs").forEach((g) => liquidTabs(g, true));
  });
}

// ------------------------------------------------------------------- boot --

const bootT0 = performance.now();
async function boot() {
  const hint = $("#boot-hint");
  const logo = $("#boot-logo");
  if (logo && !matchMedia("(prefers-reduced-motion: reduce)").matches) {
    const go = () => dust.gather(logo, { duration: 1000, density: 1.2, spread: 1.4 });
    if (logo.complete) go(); else logo.addEventListener("load", go, { once: true });
  }
  // Light streams along inside the loading bar while it loads.
  const stopFlow = matchMedia("(prefers-reduced-motion: reduce)").matches ? () => {} : dust.flow($("#boot .bar"));
  dust.warm("img/birch-forest.jpg");
  bindChrome();
  bindEvents();
  try {
    hint.textContent = "Reading your instances";
    const b = await call("bootstrap");
    S.appVersion = b.appVersion;
    S.dataDir = b.dataDir;
    S.platform = b.platform || (isTauri ? "windows" : "preview");
    S.deviceRamMb = b.deviceRamMb || 0;
    S.ramMax = b.deviceRamMb ? Math.max(1024, Math.floor((b.deviceRamMb * 0.75) / 256) * 256) : 0;
    S.settings = { sky: "cycle", day_length_min: 10, background: "golden", vortex_menu: true, fast_launch: true, tuned_jvm: true, ...b.settings };
    if (S.settings.wall_custom) await setWallImage(await wallStore.get());
    S.accounts = b.accounts;
    S.instances = b.instances;
    if (!S.instances.some((i) => i.name === S.selected)) S.selected = S.instances[0]?.name || null;
    applyLook();
    updateAccount();
    fillLogInstances();
  } catch (e) {
    hint.textContent = `Couldn't start: ${errText(e)}`;
    return;
  }
  // The skin can come from the network: don't hold the window for it.
  const skin = loadSkin();
  await Promise.race([skin, new Promise((r) => setTimeout(r, 350))]);
  call("version_art")
    .then((list) => {
      for (const a of list) S.art[a.version] ||= a.image;
      if (["home", "instances", "mods"].includes(S.route)) render();
    })
    .catch(() => {});
  call("list_versions", { refresh: false })
    .then((v) => { S.versions = v; if (S.wizard && !S.wizard.version) S.wizard.version = v.latestRelease; if (S.route === "new") updateWizard(); })
    .catch((e) => toast("Working offline", `The version list couldn't load: ${errText(e)}`, "warn", 6000));
  call("news")
    .then((n) => { S.news = n; refreshNews(); })
    .catch(() => { S.news = []; refreshNews(); });
  warmSelected();
  if ({ ...UI_DEFAULTS, ...S.settings }.auto_update !== false) setTimeout(() => checkForUpdate(false), 2500);

  tick();
  $("#side-version").textContent = `v${S.appVersion}`;
  $("#brand-ver").textContent = `v${S.appVersion}`;
  go("home", { silent: true });
  requestAnimationFrame(() => liquidTabs($("#nav"), true));
  // iOS 26 liquid glass on every glass surface (where the engine can).
  // Phones keep it on the tab bar only (every glass panel bending a moving
  // wallpaper costs too much there).
  setLiquid(true);
  if (!lite) watchGlass();
  liquifyRail($("#nav"));
  if (lite) preblurBackdrop();
  // Let the loading bar's light run for a moment before the burst.
  const shown = performance.now() - bootT0;
  if (anims() && shown < 1400) await new Promise((r) => setTimeout(r, 1400 - shown));
  const bootEl = $("#boot");
  world.rise = 0;
  if (anims()) {
    // The bar bursts, and the grains fly out and build the launcher: each
    // piece forms where its grains land.
    stopFlow();
    const br = $(".bar", bootEl).getBoundingClientRect();
    const origin = { x: br.left + br.width / 2, y: br.top + br.height / 2 };
    const wall = dust.colorsOfImage("img/birch-forest.jpg");
    const pieces = [
      ...$$(".topbar .brand, .top-right > :not([hidden])"),
      $("#nav"),
      ...$$("#page .home > .hero"),
      ...$$("#page .home-side > *, #page .home-row > *"),
    ].filter(Boolean).map((el) => ({ el }));
    if ($("#page .hero")) pieces.push({ el: $("#world"), colors: wall.length ? wall : undefined });
    $(".boot-inner", bootEl).animate([{ opacity: 1, scale: 1 }, { opacity: 0, scale: 1.08, filter: "blur(6px)" }], { duration: 380, easing: "ease-out", fill: "forwards" });
    dust.burst(origin.x, origin.y, { count: 260, speed: 1.4 });
    requestAnimationFrame(() => dust.form(origin, pieces));
    bootEl.classList.add("out");
    setTimeout(() => bootEl.remove(), 950);
  } else bootEl.remove();
  placeHero();
  const u = { ...UI_DEFAULTS, ...S.settings };
  if (world.picture && anims() && u.intro !== false) { placeHero(); player.intro(0.45); }
  else if (world.picture) player.chooseNext();
  else setTimeout(() => player.wave(1.8), 1200);
}

boot();
