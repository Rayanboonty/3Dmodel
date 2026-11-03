// Vortex Client UI: routing, pages, liquid-glass interactions, live game state.
import { call, on, onFileDrop, isTauri } from "./api.js";
import { icon, hydrateIcons, drawAvatar } from "./icons.js";
import { World } from "./scene.js";
import { Player, defaultSkin } from "./player.js";
import { sound } from "./sound.js";
import { fx, toast } from "./fx.js";

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
};

const ROUTES = {
  home: renderHome,
  instances: renderInstances,
  new: renderWizard,
  mods: renderMods,
  accounts: renderAccounts,
  settings: renderSettings,
};
const NAV_OF = { new: "instances" };

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
  $("#time-text").textContent = world.timeLabel();
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

function applyLook() {
  const s = S.settings || {};
  world.setMode(s.sky || "cycle");
  world.setDayLength(s.day_length_min || 10);
  world.setAnimate(s.animations !== false);
  sound.setEnabled(s.ui_sounds !== false);
  fx.setEnabled(s.animations !== false);
  document.body.classList.toggle("no-anim", s.animations === false);
}

async function loadSkin() {
  try {
    S.skin = await call("player_skin");
  } catch {
    S.skin = { dataUrl: null, slim: false, source: "none" };
  }
  const url = S.skin.dataUrl || defaultSkin();
  player.setSkin(url, S.skin.slim);
  if (S.preview) S.preview.setSkin(url, S.skin.slim);
  drawHead($("#account-avatar"), url);
}

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
      ${block(i.loader)}
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
    ? `<div class="switcher glass">${[...S.instances].sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0)).map((i) => `<button class="row-item ${i.name === inst.name ? "on" : ""}" data-action="select" data-name="${esc(i.name)}">${block(i.loader)}<span class="meta"><strong>${esc(i.name)}</strong><span>${esc(i.minecraftVersion)} · ${esc(i.loaderLabel)}</span></span></button>`).join("")}
        <button class="row-item" data-action="route" data-route="new"><span class="block" style="background:rgba(255,255,255,.08)">${icon("plus", 16)}</span><span class="meta"><strong>New instance</strong><span>Any version, Vanilla, Fabric or Forge</span></span></button></div>`
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
    return `<div class="card ${i.name === S.selected ? "on" : ""}" data-action="select-home" data-name="${esc(i.name)}" role="button" tabindex="0">
      <div class="card-top">${block(i.loader, "lg")}<div style="min-width:0"><div class="card-name">${esc(i.name)}</div><div class="card-sub">${esc(ago(i.lastPlayed))}</div></div></div>
      <div class="row" style="gap:6px">
        <span class="chip"><span class="kind ${kind}">${esc(i.minecraftVersion)}</span></span>
        <span class="chip">${esc(i.loaderLabel)}</span>
        ${i.modCount ? `<span class="chip">${i.modCount} mods</span>` : ""}
        ${r.phase !== "idle" ? `<span class="chip live"><span class="live-dot"></span>${r.phase === "running" ? "Playing" : "Starting"}</span>` : ""}
      </div>
      <div class="card-actions">
        <button class="btn btn-sm btn-accent lq" data-action="play" data-name="${esc(i.name)}">${icon("play", 12)} Play</button>
        <button class="btn btn-sm btn-icon lq" data-action="edit" data-name="${esc(i.name)}" aria-label="Edit ${esc(i.name)}">${icon("edit", 12)}</button>
        <button class="btn btn-sm btn-icon lq" data-action="folder" data-name="${esc(i.name)}" aria-label="Open folder">${icon("folder", 12)}</button>
        <span class="grow"></span>
        <button class="btn btn-sm btn-icon btn-danger lq" data-action="delete" data-name="${esc(i.name)}" aria-label="Delete ${esc(i.name)}">${icon("trash", 12)}</button>
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
        <div class="vlist" role="listbox" aria-label="Minecraft versions">
          ${rows.map((v) => `<button class="vrow ${w.version === v.id ? "on" : ""}" role="option" aria-selected="${w.version === v.id}" data-action="wiz-pick" data-id="${esc(v.id)}"><span class="vid">${esc(v.id)}</span><span class="kind ${v.kind}">${v.kind === "release" ? "Release" : v.kind === "snapshot" ? "Snapshot" : v.kind === "beta" ? "Beta" : "Alpha"}</span><span class="vdate">${esc(v.date)}</span></button>`).join("") || `<div class="empty"><p>No versions match.</p></div>`}
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

function renderMods() {
  const modded = S.instances.filter((i) => i.loader !== "vanilla");
  if (!S.mods.instance || !S.instances.some((i) => i.name === S.mods.instance)) {
    const sel = selectedInstance();
    S.mods.instance = sel && sel.loader !== "vanilla" ? sel.name : modded[0]?.name || sel?.name || null;
    if (S.mods.instance) loadMods();
  }
  const inst = S.instances.find((i) => i.name === S.mods.instance);
  if (!inst) return { html: sheet("Mods", "", "", `<div class="empty"><h3>No instances yet</h3><button class="btn btn-accent lq" data-action="route" data-route="new">Create one</button></div>`) };
  const picker = `<select class="input" id="mods-inst" aria-label="Instance" style="width:260px">${S.instances.map((i) => `<option value="${esc(i.name)}" ${i.name === S.mods.instance ? "selected" : ""}>${esc(i.name)} (${esc(i.loaderLabel)})</option>`).join("")}</select>`;
  let body;
  if (inst.loader === "vanilla") {
    body = `<div class="empty">${block("vanilla", "lg")}<h3>Vanilla doesn't load mods</h3><p>Create a Fabric or Forge instance to use mods.</p><button class="btn btn-accent lq" data-action="route" data-route="new">${icon("plus", 14)} New modded instance</button></div>`;
  } else if (S.mods.loading) {
    body = `<div class="empty"><div class="bar" style="width:240px"><i></i></div></div>`;
  } else {
    const list = S.mods.list;
    body = `${list.length ? `<div class="list">${list.map((m) => `<div class="list-row ${m.enabled ? "" : "off"}">
        ${block(m.loader === "forge" ? "forge" : m.loader === "unknown" ? "vanilla" : "fabric")}
        <div class="meta"><strong>${esc(m.name)}${m.version ? ` <span class="chip">${esc(m.version)}</span>` : ""}</strong><span>${esc(m.description || m.file)}</span></div>
        <button class="switch" role="switch" aria-checked="${m.enabled}" aria-label="Enable ${esc(m.name)}" data-action="mod-toggle" data-file="${esc(m.file)}" data-on="${m.enabled}"></button>
        <button class="btn btn-sm btn-icon btn-danger lq" data-action="mod-delete" data-file="${esc(m.file)}" aria-label="Delete ${esc(m.name)}">${icon("trash", 12)}</button>
      </div>`).join("")}</div>` : `<div class="empty"><h3>No mods yet</h3><p>Add ${inst.loader === "forge" ? "Forge" : "Fabric"} mods made for Minecraft ${esc(inst.minecraftVersion)} to the mods folder.</p></div>`}
      ${isTauri ? `<div class="dropzone" id="dropzone">Drop .jar files on this window to install them</div>` : ""}`;
  }
  return {
    html: sheet("Mods", `${S.mods.list.length} installed in ${esc(inst.name)}.`, `${picker}
      <button class="btn lq" data-action="mods-refresh">Refresh</button>
      <button class="btn btn-accent lq" data-action="folder-mods" ${inst.loader === "vanilla" ? "disabled" : ""}>${icon("folder", 14)} Open folder</button>`, body),
    mount(root) {
      $("#mods-inst", root).addEventListener("change", (e) => {
        S.mods.instance = e.target.value;
        S.mods.list = [];
        loadMods();
        render();
      });
    },
  };
}

async function loadMods() {
  const name = S.mods.instance;
  const inst = S.instances.find((i) => i.name === name);
  if (!inst || inst.loader === "vanilla") return;
  S.mods.loading = true;
  try {
    const list = await call("list_mods", { name });
    if (S.mods.instance === name) S.mods.list = list;
  } catch (e) {
    toast("Couldn't read mods", errText(e), "error");
  } finally {
    S.mods.loading = false;
    if (S.route === "mods") render();
  }
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

function renderSettings() {
  const d = (S.draft ||= JSON.parse(JSON.stringify(S.settings)));
  const dirty = JSON.stringify(d) !== JSON.stringify(S.settings);
  const pct = (v, lo, hi) => ((v - lo) / (hi - lo)) * 100;
  return {
    html: sheet("Settings", "", "", `<div class="settings-grid">
        <div class="card-pad"><h3>World and motion</h3>
          ${skyControls(d)}
          <div class="setting"><div><strong>Sounds</strong><span>Clicks, launch sounds and alerts</span></div><button class="switch" role="switch" aria-checked="${d.ui_sounds}" data-action="set-bool" data-key="ui_sounds" aria-label="Sounds"></button></div>
          <div class="setting"><div><strong>Animations</strong><span>The living world, glass motion and particles</span></div><button class="switch" role="switch" aria-checked="${d.animations}" data-action="set-bool" data-key="animations" aria-label="Animations"></button></div>
        </div>
        <div class="card-pad"><h3>Game</h3>
          <div class="field"><label class="label" for="s-max">Maximum memory</label><div class="row"><input class="range grow" id="s-max" type="range" min="1024" max="32768" step="512" value="${d.max_memory_mb}" style="--fill:${pct(d.max_memory_mb, 1024, 32768)}%"><span class="value" id="s-max-v">${gb(d.max_memory_mb)}</span></div></div>
          <div class="field"><label class="label" for="s-min">Minimum memory</label><div class="row"><input class="range grow" id="s-min" type="range" min="256" max="8192" step="256" value="${d.min_memory_mb}" style="--fill:${pct(d.min_memory_mb, 256, 8192)}%"><span class="value" id="s-min-v">${gb(d.min_memory_mb)}</span></div></div>
          <div class="field"><label class="label" for="s-java">Java</label><input class="input" id="s-java" placeholder="Automatic (recommended)" value="${esc(d.java_path || "")}"></div>
          <div class="field"><label class="label" for="s-jvm">Extra JVM arguments</label><input class="input" id="s-jvm" placeholder="Optional, for example -XX:+UseG1GC" value="${esc((d.extra_jvm_args || []).join(" "))}"></div>
        </div>
        <div class="card-pad"><h3>Microsoft sign-in</h3>
          <div class="field"><label class="label" for="s-client">Azure client ID</label><input class="input" id="s-client" placeholder="00000000-0000-0000-0000-000000000000" value="${esc(d.microsoft_client_id || "")}"></div>
          <span class="muted" style="font-size:13px">Register an app in the Azure portal and have it approved for the Minecraft API. The README has the steps.</span>
        </div>
        <div class="card-pad"><h3>Downloads and data</h3>
          <div class="field"><label class="label" for="s-dl">Parallel downloads</label><div class="row"><input class="range grow" id="s-dl" type="range" min="1" max="64" step="1" value="${d.download_concurrency}" style="--fill:${pct(d.download_concurrency, 1, 64)}%"><span class="value" id="s-dl-v">${d.download_concurrency}</span></div></div>
          <div class="field"><span class="label">Data folder</span><div class="row"><code class="grow" style="font-size:12.5px;word-break:break-all;color:var(--ink-2)">${esc(S.dataDir)}</code><button class="btn btn-sm lq" data-action="folder-data">${icon("folder", 12)} Open</button></div></div>
          <span class="muted" style="font-size:13px">Vortex Client ${esc(S.appVersion)}${isTauri ? "" : ", preview mode"}</span>
        </div>
      </div>`,
      `<div class="savebar glass" id="savebar" ${dirty ? "" : "hidden"}><span>Unsaved changes</span><button class="btn btn-sm lq" data-action="settings-reset">Discard</button><button class="btn btn-sm btn-accent lq" data-action="settings-save">${icon("check", 12)} Save</button></div>`),
    mount(root) {
      const range = (id, key, fmt, lo, hi) => {
        const el = $(id, root);
        if (!el) return;
        el.addEventListener("input", () => {
          d[key] = Number(el.value);
          el.style.setProperty("--fill", `${((d[key] - lo) / (hi - lo)) * 100}%`);
          $(`${id}-v`).textContent = fmt(d[key]);
          if (key === "day_length_min") world.setDayLength(d[key]);
          markDirty();
        });
      };
      range("#s-max", "max_memory_mb", gb, 1024, 32768);
      range("#s-min", "min_memory_mb", gb, 256, 8192);
      range("#s-dl", "download_concurrency", String, 1, 64);
      range("#s-day", "day_length_min", (v) => `${v} min`, 2, 40);
      $("#s-java", root).addEventListener("input", (e) => { d.java_path = e.target.value.trim() || null; markDirty(); });
      $("#s-jvm", root).addEventListener("input", (e) => { d.extra_jvm_args = e.target.value.split(/\s+/).filter(Boolean); markDirty(); });
      $("#s-client", root).addEventListener("input", (e) => { d.microsoft_client_id = e.target.value.trim() || null; markDirty(); });
    },
  };
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
      S.mods.list = await call("toggle_mod", { name: S.mods.instance, file: el.dataset.file, enabled });
      setTimeout(render, 380);
    } catch (e) {
      toast("Couldn't change the mod", errText(e), "error");
    }
  },
  async "mod-delete"(el) {
    if (!(await confirmModal("Delete this mod?", el.dataset.file))) return;
    try {
      S.mods.list = await call("delete_mod", { name: S.mods.instance, file: el.dataset.file });
      sound.pop();
      render();
    } catch (e) {
      toast("Couldn't delete the mod", errText(e), "error");
    }
  },
  "mods-refresh"() { loadMods(); render(); },
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
    if (e.altKey && /^[1-5]$/.test(e.key)) go(["home", "instances", "mods", "accounts", "settings"][Number(e.key) - 1]);
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
    S.settings = { sky: "cycle", day_length_min: 10, ...b.settings };
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
