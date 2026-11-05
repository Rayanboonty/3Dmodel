// Vortex Client UI: routing, pages, liquid-glass interactions, live game state.
import { call, on, onFileDrop, isTauri } from "./api.js";
import { icon, hydrateIcons, drawAvatar } from "./icons.js";
import { World, SCENES, sceneThumb, sceneStill } from "./scene.js";
import { Player, defaultSkin } from "./player.js";
import { sound } from "./sound.js";
import { fx, toast } from "./fx.js";
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
const NAV_OF = { new: "instances", accounts: "skins" };

const $ = (sel, root = document) => root.querySelector(sel);
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

const world = new World(document.getElementById("world"));
const player = new Player(document.getElementById("player-layer"), world);
player.onPoke = (p) => {
  sound.pop();
  fx.burst(p.x, p.y, ["#ff5a7a", "#ff9ab3", "#ffffff"], 14);
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

function placeAnchor() {
  const a = world.anchor();
  document.documentElement.style.setProperty("--ax", `${a.x}px`);
  document.documentElement.style.setProperty("--ay", `${a.y}px`);
}
window.addEventListener("resize", () => requestAnimationFrame(placeAnchor));

function applyLook(s = S.settings || {}) {
  world.setScene(s.background || "valley");
  world.setMode(s.sky || "cycle");
  world.setDayLength(s.day_length_min || 10);
  world.setAnimate(s.animations !== false);
  world.setLife(s.world_life !== false);
  sound.setEnabled(s.ui_sounds !== false);
  fx.setEnabled(s.animations !== false);
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
    mountLogo($("#brand-logo"), renderBrand(style, 3.1));
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
  document.addEventListener("pointerdown", (e) => {
    const el = e.target.closest(".lq, .btn, .tab, .play, .square, .row-item, .card, .loader, .vrow, .switch, .top-chip");
    if (!el || el.disabled) return;
    sound.click();
    if (!anims()) return;
    if (el.classList.contains("lq") || el.matches(".btn, .play, .square, .top-chip")) {
      const r = el.getBoundingClientRect();
      const ripple = document.createElement("span");
      ripple.className = "lq-ripple";
      ripple.style.left = `${e.clientX - r.left}px`;
      ripple.style.top = `${e.clientY - r.top}px`;
      if (getComputedStyle(el).position === "static") el.style.position = "relative";
      el.style.overflow = "hidden";
      el.appendChild(ripple);
      setTimeout(() => ripple.remove(), 700);
      el.classList.remove("squish");
      void el.offsetWidth;
      el.classList.add("squish");
      setTimeout(() => el.classList.remove("squish"), 560);
    }
    if (el.matches(".play, .btn-accent")) fx.burst(e.clientX, e.clientY, ["#ffd27a", "#ffb547", "#ffffff"], 14);
  });
  document.addEventListener("pointermove", (e) => {
    const el = e.target.closest(".hl");
    if (!el) return;
    const r = el.getBoundingClientRect();
    el.style.setProperty("--mx", `${e.clientX - r.left}px`);
    el.style.setProperty("--my", `${e.clientY - r.top}px`);
  });
}

// ---------------------------------------------------------------- routing --

function go(route, opts = {}) {
  if (!ROUTES[route]) route = "home";
  const same = route === S.route;
  S.route = route;
  S.switcher = false;
  closePopover();
  const navRoute = NAV_OF[route] || route;
  $$("#nav .tab").forEach((b) => b.classList.toggle("on", b.dataset.route === navRoute));
  liquidTabs($("#nav"));
  document.body.classList.toggle("sheet-open", route !== "home");
  if (!same && !opts.silent) sound.nav();
  const page = $("#page");
  const swap = () => {
    page.classList.remove("leave");
    render();
  };
  if (!same && anims() && page.children.length) {
    page.classList.add("leave");
    setTimeout(swap, 170);
  } else swap();
}

function render() {
  const page = $("#page");
  if (S.preview && S.route !== "accounts") S.preview = null;
  const { html, mount } = ROUTES[S.route]();
  page.innerHTML = html;
  [...page.children].forEach((el, i) => el.style.setProperty("--i", i));
  page.classList.remove("enter");
  void page.offsetWidth;
  if (anims()) page.classList.add("enter");
  hydrateIcons(page);
  $$("canvas[data-avatar]", page).forEach((c) => drawAvatar(c, c.dataset.avatar));
  $$("canvas[data-head]", page).forEach((c) => drawHead(c, S.skin?.dataUrl || defaultSkin()));
  $$(".tabs", page).forEach((g) => liquidTabs(g, true));
  mount?.(page);
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
      <span class="play-label"><span class="run-text">PLAYING</span><span class="stop-text">${icon("stop", 20)} STOP</span></span>
      <span class="play-sub">${esc(inst.name)}${t ? ` · ${t}` : ""}</span></button>`;
  }
  return `<button class="play lq" id="play" data-action="play" data-name="${esc(inst.name)}">
    <span class="play-label">${icon("play", 22)} PLAY</span>
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

function renderHome() {
  const inst = selectedInstance();
  const acc = selectedAccount();
  const cluster = inst
    ? `<div class="play-cluster">${playButton(inst)}
        <button class="glass square lq hl" data-action="toggle-switcher" aria-label="Choose instance" aria-expanded="${S.switcher}">${icon("grid", 20)}</button></div>`
    : `<div class="play-cluster"><button class="play lq" data-action="route" data-route="new"><span class="play-label">${icon("plus", 20)} CREATE</span><span class="play-sub">Pick any Minecraft version to start</span></button></div>`;
  const switcher = S.switcher && inst
    ? `<div class="switcher glass">${[...S.instances].sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0)).map((i) => `<button class="row-item ${i.name === inst.name ? "on" : ""}" data-action="select" data-name="${esc(i.name)}">${coverThumb(i)}<span class="meta"><strong>${esc(i.name)}</strong><span>${esc(i.minecraftVersion)} · ${esc(updateName(i.minecraftVersion))}</span></span></button>`).join("")}
        <button class="row-item" data-action="route" data-route="new"><span class="cover-thumb add">${icon("plus", 16)}</span><span class="meta"><strong>New instance</strong><span>Any version, Vanilla, Fabric or Forge</span></span></button></div>`
    : "";
  return {
    html: `<div class="home">
      ${cluster}
      ${switcher}
      <aside class="glass side hl" aria-label="Library">
        <div class="side-head">
          <div class="tabs sm" id="side-tabs"><span class="drop" aria-hidden="true"></span>
            <button class="tab ${S.side === "instances" ? "on" : ""}" data-action="side" data-side="instances">Instances</button>
            <button class="tab ${S.side === "news" ? "on" : ""}" data-action="side" data-side="news">News</button>
          </div>
          <button class="btn btn-sm lq" data-action="route" data-route="new">${icon("plus", 12)} New</button>
        </div>
        <div class="side-body" id="side-body">${sideBody()}</div>
        ${acc ? "" : `<div class="side-foot"><button class="btn btn-accent btn-sm lq" data-action="route" data-route="accounts">Add an account to play</button></div>`}
      </aside>
    </div>`,
    mount(root) {
      placeAnchor();
      const play = $("#play", root);
      if (play) play.addEventListener("pointerenter", () => player.wave(1.4));
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
          btn.querySelector(".play-label").textContent = r.stage || "Preparing";
          btn.querySelector(".play-sub").textContent = `${r.total ? `${pct}% · ` : ""}Click to cancel`;
        } else {
          btn.outerHTML = playButton(inst);
          $("#play")?.addEventListener("pointerenter", () => player.wave(1.4));
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
        render();
        const again = $("#wiz-search");
        again.focus();
        again.setSelectionRange(pos, pos);
      });
      $(".vrow.on", root)?.scrollIntoView({ block: "center" });
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

function loadLoaderVersions(w) {
  for (const kind of ["fabric", "forge"]) {
    if (w.loaderVersions[kind] !== undefined) continue;
    const forVersion = w.version;
    call("loader_versions", { loader: kind, game: forVersion })
      .then((list) => { if (w.version === forVersion) w.loaderVersions[kind] = list; })
      .catch(() => { if (w.version === forVersion) w.loaderVersions[kind] = "error"; })
      .finally(() => { if (S.route === "new" && w.step === 2) render(); });
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
      <div class="mods-pane">${S.modsTab === "browse" ? browseBody(inst) : installedBody(inst)}</div>`),
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
    : "Offline accounts: Vortex mods show your skin and cape in-game on Fabric 1.21.4. Only you see them.";
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
                ${S.settings?.microsoft_client_id ? "" : `<span class="muted" style="font-size:13px">Add your Azure client ID in Settings first.</span>`}
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

const SETTINGS_TABS = [["general", "General"], ["appearance", "Appearance"], ["game", "Game"], ["ingame", "In-game"], ["data", "Account & data"]];
const WINDOW_SIZES = [["", "Minecraft default (854 × 480)"], ["1280x720", "1280 × 720"], ["1600x900", "1600 × 900"], ["1920x1080", "1920 × 1080"], ["2560x1440", "2560 × 1440"], ["custom", "Custom"]];

const swRow = (d, key, title, desc) => `<div class="setting"><div><strong>${title}</strong>${desc ? `<span>${desc}</span>` : ""}</div><button class="switch" role="switch" aria-checked="${!!d[key]}" data-action="set-bool" data-key="${key}" aria-label="${title}"></button></div>`;
const segRow = (d, key, title, options, desc = "") => `<div class="setting stack"><div><strong>${title}</strong>${desc ? `<span>${desc}</span>` : ""}</div>
  <div class="seg" role="group" aria-label="${title}">${options.map(([v, l]) => `<button class="${String(d[key]) === String(v) ? "on" : ""}" data-action="set-choice" data-key="${key}" data-value="${v}">${l}</button>`).join("")}</div></div>`;
const rangeRow = (id, label, value, lo, hi, step, text) => `<div class="field"><label class="label" for="${id}">${label}</label><div class="row"><input class="range grow" id="${id}" type="range" min="${lo}" max="${hi}" step="${step}" value="${value}" style="--fill:${((value - lo) / (hi - lo)) * 100}%"><span class="value" id="${id}-v">${text}</span></div></div>`;

function settingsBody(d) {
  const tab = S.settingsTab;
  if (tab === "appearance") {
    return `<div class="settings-grid">
      <div class="card-pad"><h3>Background</h3><p class="muted small">The world behind the launcher. Vortex mods use it for the in-game main menu too.</p>
        <div class="scene-grid">${SCENES.map((sc) => `<button class="scene-tile ${d.background === sc.id ? "on" : ""}" data-action="set-choice" data-key="background" data-value="${sc.id}" aria-pressed="${d.background === sc.id}">
          <span class="scene-art" data-scene="${sc.id}"></span><span>${esc(sc.name)}</span></button>`).join("")}</div></div>
      <div class="card-pad"><h3>Logo</h3><p class="muted small">Shown in the top bar and on the in-game main menu.</p>
        <div class="logo-grid">${STYLES.map((st, i) => `<button class="logo-tile ${Number(d.logo_style) === i ? "on" : ""}" data-action="set-choice" data-key="logo_style" data-num="1" data-value="${i}" title="${esc(st.note)}">
          <span class="logo-art" data-logo="${i}"></span><span>${esc(st.name)}</span></button>`).join("")}</div></div>
      <div class="card-pad"><h3>Colour and glass</h3>
        <div class="setting stack"><div><strong>Accent</strong><span>Buttons, highlights and the in-game mod menu</span></div>
          <div class="swatches">${Object.entries(ACCENTS).map(([k, [c]]) => `<button class="swatch ${d.accent === k ? "on" : ""}" style="--c:${c}" data-action="set-choice" data-key="accent" data-value="${k}" aria-label="${k}" title="${k[0].toUpperCase() + k.slice(1)}"></button>`).join("")}</div></div>
        ${rangeRow("s-glass", "Glass", d.glass, 0, 100, 5, glassLabel(d.glass))}
        ${swRow(d, "show_player", "3D player on Home", "Your skin, living in the world")}
        ${swRow(d, "clock_12h", "12-hour clock", "")}
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
        ${swRow(d, "vortex_mods", "Add Vortex mods to the game", "On Fabric 1.21.4 instances. Fabric API is installed with them")}
        ${swRow(d, "vortex_menu", "Vortex main menu", "Replaces Minecraft's title screen with your background and logo, and restyles buttons")}
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
    </div>`;
  }
  return `<div class="settings-grid">
    <div class="card-pad"><h3>Time of day</h3>${skyControls(d)}</div>
    <div class="card-pad"><h3>Motion and sound</h3>
      ${swRow(d, "animations", "Animations", "The moving world, glass motion and particles")}
      ${swRow(d, "world_life", "World life", "Bees, butterflies, birds, fish and fireflies")}
      ${swRow(d, "ui_sounds", "Sounds", "Clicks, launch sounds and alerts")}
    </div>
  </div>`;
}

const INGAME_FEATURES = [
  ["HUD", "Keystrokes, CPS, FPS, ping, coordinates, direction, armor, potions, saturation, memory, server, day counter, clock, speed"],
  ["PvP", "Toggle sprint and sneak, target HUD, item counter, reach and combo display, custom crosshair, no hurt cam, low fire"],
  ["Camera", "Zoom with scroll, freelook, fullbright"],
  ["World", "Time changer and clear weather (only on your screen)"],
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
  range("#s-max", "max_memory_mb", gb, 1024, 32768);
  range("#s-min", "min_memory_mb", gb, 256, 8192);
  range("#s-dl", "download_concurrency", String, 1, 64);
  range("#s-day", "day_length_min", (v) => `${v} min`, 2, 40, (v) => world.setDayLength(v));
  range("#s-glass", "glass", glassLabel, 0, 100, () => applyLook(d));
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
  root.innerHTML = `<div class="scrim"><div class="modal glass hl" role="dialog" aria-modal="true">${html}</div></div>`;
  hydrateIcons(root);
  const scrim = root.firstElementChild;
  scrim.addEventListener("click", (e) => { if (e.target === scrim) closeModal(); });
  onMount?.(root);
  setTimeout(() => root.querySelector("input, .btn")?.focus(), 60);
}

function closeModal() {
  $("#modal-root").innerHTML = "";
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
}

/** Give the game the launcher's background and logo for the Vortex main menu. */
async function exportMenuArt(inst) {
  const s = S.settings || {};
  if (!inst || inst.loader !== "fabric" || inst.minecraftVersion !== "1.21.4" || s.vortex_mods === false || s.vortex_menu === false) return;
  try {
    const bg = sceneStill(s.background || "valley", 1600, 900, world.fixedTime ? undefined : world.hour());
    const logo = renderStyle(s.logo_style || 0, 9, { dpr: 1 });
    await call("save_menu_art", { background: bg.toDataURL("image/png"), logo: logo.toDataURL("image/png") });
  } catch (e) {
    console.warn("menu art", e);
  }
}

async function play(name) {
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
    await call("launch_instance", { name });
  } catch (e) {
    S.runs[name] = { phase: "idle" };
    updateRunUI(name);
    sound.error();
    toast("Couldn't start the game", errText(e), "error", 7000);
  }
}

const actions = {
  route(el) { go(el.dataset.route); },
  play(el, e) { e.stopPropagation(); play(el.dataset.name); },
  async stop(el, e) { e.stopPropagation(); await call("stop_instance", { name: el.dataset.name }); },
  "select-home"(el) { select(el.dataset.name); go("home"); },
  select(el) {
    select(el.dataset.name);
    S.switcher = false;
    sound.pop();
    render();
  },
  "toggle-switcher"() { S.switcher = !S.switcher; render(); },
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
  "folder-data"() { call("open_path", { target: "data" }).catch((er) => toast("Couldn't open the folder", errText(er), "error")); },
  async delete(el, e) {
    e.stopPropagation();
    const name = el.dataset.name;
    if (!(await confirmModal(`Delete ${name}?`, "Its worlds, mods and settings are removed from this computer. This can't be undone."))) return;
    try {
      S.instances = await call("delete_instance", { name });
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
  "wiz-kind"(el) { const w = S.wizard; w.kinds[el.dataset.kind] = !w.kinds[el.dataset.kind]; render(); },
  "wiz-pick"(el) {
    const w = S.wizard;
    if (w.version !== el.dataset.id) { w.version = el.dataset.id; w.loaderVersions = {}; w.loaderVersion = null; }
    const kind = S.versions?.versions.find((v) => v.id === w.version)?.kind;
    if (kind && !w.kinds[kind]) w.kinds[kind] = true;
    render();
  },
  "wiz-next"() { const w = S.wizard; if (w.step === 1) loadLoaderVersions(w); w.step = Math.min(3, w.step + 1); render(); },
  "wiz-back"() { S.wizard.step = Math.max(1, S.wizard.step - 1); render(); },
  "wiz-loader"(el) { const w = S.wizard; w.loader = el.dataset.kind; w.loaderVersion = null; render(); },
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
      fx.confetti();
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
      S.mods.list = await call("delete_mod", { name: S.mods.instance, file: el.dataset.file, kind: S.mods.kind });
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
    S.skins = await call("skin_remove", { id: el.dataset.id });
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
    const pane = $(".settings-pane");
    pane.innerHTML = settingsBody(S.draft);
    pane.classList.remove("fresh");
    void pane.offsetWidth;
    pane.classList.add("fresh");
    hydrateIcons(pane);
    $$(".tabs", pane).forEach((g) => liquidTabs(g, true));
    mountSettings(pane, S.draft);
  },
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
    if (["accent", "logo_style", "background"].includes(key)) applyLook(S.draft);
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
    S.accounts = await call("remove_account", { uuid: el.dataset.uuid });
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
      fx.confetti(50);
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
      fx.confetti(60);
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
    S.draft[key] = !S.draft[key];
    sound.toggle(S.draft[key]);
    el.setAttribute("aria-checked", S.draft[key]);
    if (["animations", "world_life", "show_player", "clock_12h"].includes(key)) applyLook(S.draft);
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
      fx.confetti(60);
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
  mountLogo($("#boot-logo"), renderStyle(0, 7));
  bindLiquid();
  $$("#nav .tab").forEach((b) => b.addEventListener("click", () => go(b.dataset.route)));
  $("#account-chip").addEventListener("click", () => go("accounts"));
  $("#time-chip").addEventListener("click", openTimePopover);
  $("#log-toggle").addEventListener("click", toggleLog);
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
      if ($("#modal-root").children.length) closeModal();
      else if ($("#popover-root").children.length) closePopover();
      else if (S.switcher) { S.switcher = false; render(); }
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

async function boot() {
  const hint = $("#boot-hint");
  bindChrome();
  bindEvents();
  try {
    hint.textContent = "Reading your instances";
    const b = await call("bootstrap");
    S.appVersion = b.appVersion;
    S.dataDir = b.dataDir;
    S.settings = { sky: "cycle", day_length_min: 10, background: "valley", vortex_menu: true, ...b.settings };
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
  hint.textContent = "Loading your skin";
  await loadSkin();
  call("version_art")
    .then((list) => {
      for (const a of list) S.art[a.version] ||= a.image;
      if (["home", "instances", "mods"].includes(S.route)) render();
    })
    .catch(() => {});
  call("list_versions", { refresh: false })
    .then((v) => { S.versions = v; if (S.wizard && !S.wizard.version) S.wizard.version = v.latestRelease; if (S.route === "new") render(); })
    .catch((e) => toast("Working offline", `The version list couldn't load: ${errText(e)}`, "warn", 6000));
  call("news")
    .then((n) => { S.news = n; if (S.route === "home" && S.side === "news") actions.side({ dataset: { side: "news" } }); })
    .catch(() => { S.news = []; });

  tick();
  go("home", { silent: true });
  requestAnimationFrame(() => liquidTabs($("#nav"), true));
  const bootEl = $("#boot");
  world.rise = 0;
  if (anims()) {
    bootEl.classList.add("out");
    setTimeout(() => bootEl.remove(), 950);
  } else bootEl.remove();
  setTimeout(() => player.wave(1.8), 1200);
}

boot();
