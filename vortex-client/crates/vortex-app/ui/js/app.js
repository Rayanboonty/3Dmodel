// Vortex UI controller: routing, pages, live launch state, console.
import { call, on, onFileDrop, isTauri } from "./api.js";
import { icon, hydrateIcons, drawAvatar } from "./icons.js";
import { Sky } from "./sky.js";
import { sound } from "./sound.js";
import { fx, toast } from "./fx.js";

// ------------------------------------------------------------------ state --

const S = {
  appVersion: "",
  dataDir: "",
  settings: null,
  accounts: [],
  instances: [],
  selected: localStorage.getItem("vortex.selected") || null,
  versions: null,
  news: null,
  runs: {}, // name → { phase: idle|preparing|running, stage, done, total }
  logs: {}, // name → [{ level, text }]
  route: "home",
  phase: "dawn",
  console: { open: false, instance: null, level: "all", search: "" },
  wizard: null,
  mods: { instance: null, list: [], loading: false },
  draft: null,
  menuOpen: false,
  instSearch: "",
  instSort: "recent",
};

const ROUTES = {
  home: { title: "Home", render: renderHome },
  instances: { title: "Instances", render: renderInstances },
  new: { title: "New instance", render: renderWizard },
  mods: { title: "Mods", render: renderMods },
  accounts: { title: "Accounts", render: renderAccounts },
  settings: { title: "Settings", render: renderSettings },
};

const SPLASHES = ["Now with Forge!", "Every version ever!", "Sunrise included!", "Fabric ready!", "Pixel perfect!", "Made of blocks!", "Rust-powered!", "Also try LAN!", "Good morning!"];
const TIPS = [
  "Press Ctrl+L to open the game log at any time.",
  "Each instance keeps its own saves, mods and resource packs.",
  "Vortex downloads the right Java for every Minecraft version automatically.",
  "Forge and Fabric are installed for you when you create an instance.",
  "Drop .jar files onto the Mods page to install them.",
  "Settings → Sky lets you lock the sunrise, or follow your clock.",
  "Ctrl+Enter launches the selected instance.",
  "Set a server on an instance and Vortex joins it as soon as the game starts.",
];

const $ = (sel, root = document) => root.querySelector(sel);
const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];
const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const anims = () => S.settings?.animations !== false;

function ago(secs) {
  if (!secs) return "never played";
  const d = Math.floor(Date.now() / 1000) - secs;
  if (d < 60) return "played just now";
  if (d < 3600) return `played ${Math.floor(d / 60)} min ago`;
  if (d < 86400) return `played ${Math.floor(d / 3600)} h ago`;
  const days = Math.floor(d / 86400);
  return `played ${days} day${days === 1 ? "" : "s"} ago`;
}

const gb = (mb) => `${(mb / 1024).toFixed(mb % 1024 ? 1 : 0)} GB`;
const runOf = (name) => S.runs[name] || { phase: "idle" };
const selectedInstance = () => S.instances.find((i) => i.name === S.selected) || S.instances[0] || null;
const selectedAccount = () => S.accounts.find((a) => a.selected) || null;
const loaderIcon = (l) => ({ vanilla: "grass", fabric: "fabric", forge: "forge" }[l] || "grass");

function errText(e) {
  return typeof e === "string" ? e : e?.message || JSON.stringify(e);
}

// --------------------------------------------------------------- the sky --

const sky = new Sky(document.getElementById("sky"));
sky.onPhase((phase) => {
  S.phase = phase;
  document.body.classList.remove("phase-dawn", "phase-day", "phase-dusk", "phase-night");
  document.body.classList.add(`phase-${phase}`);
  const iconName = { dawn: "sun", day: "sun", dusk: "sunset", night: "moon" }[phase];
  $("#sky-icon").innerHTML = icon(iconName, 16);
  $("#sky-label").textContent = phase[0].toUpperCase() + phase.slice(1);
  const kicker = $("#hero-greeting");
  if (kicker) kicker.textContent = greeting();
});

function greeting() {
  const who = selectedAccount()?.username;
  const base = { dawn: "Good morning", day: "Good afternoon", dusk: "Good evening", night: "Night owl mode" }[S.phase];
  return who ? `${base}, ${who}` : base;
}

function applyLook() {
  const s = S.settings || {};
  sky.setMode(s.sky || "dawn");
  sky.setAnimate(s.animations !== false);
  sound.setEnabled(s.ui_sounds !== false);
  fx.setEnabled(s.animations !== false);
  document.body.classList.toggle("no-anim", s.animations === false);
}

// ---------------------------------------------------------------- routing --

function go(route, opts = {}) {
  if (!ROUTES[route]) route = "home";
  const same = route === S.route;
  S.route = route;
  S.menuOpen = false;
  $$(".nav-btn").forEach((b) => b.classList.toggle("active", b.dataset.route === route));
  moveMarker();
  $("#page-title").textContent = ROUTES[route].title;
  if (!same && !opts.silent) sound.nav();
  const page = $("#page");
  const swap = () => {
    page.classList.remove("leave");
    render();
    page.scrollTop = 0;
  };
  if (!same && anims() && page.children.length) {
    page.classList.add("leave");
    setTimeout(swap, 130);
  } else swap();
}

function moveMarker() {
  const active = $(`.nav-btn[data-route="${S.route}"]`);
  const marker = $("#nav-marker");
  if (active && marker) marker.style.transform = `translateY(${active.offsetTop}px)`;
}

function render() {
  const page = $("#page");
  const { html, mount, sub } = ROUTES[S.route].render();
  page.innerHTML = html;
  $("#page-sub").textContent = sub || "";
  [...page.children].forEach((el, i) => el.style.setProperty("--i", i));
  page.classList.remove("enter");
  void page.offsetWidth;
  if (anims()) page.classList.add("enter");
  hydrateIcons(page);
  $$("canvas[data-avatar]", page).forEach((c) => drawAvatar(c, c.dataset.avatar));
  mount?.(page);
}

// ------------------------------------------------------------------- home --

function heroStatus(inst) {
  const r = runOf(inst.name);
  if (r.phase === "preparing") {
    const pct = r.total ? Math.round((r.done / r.total) * 100) : null;
    return `<div class="stage-text"><span>${esc(r.stage || "Preparing")}</span><span>${pct === null ? "" : pct + "%"}</span></div>
      <div class="xp ${pct === null ? "indeterminate" : ""}"><div class="xp-fill" style="width:${pct ?? 0}%"></div></div>`;
  }
  if (r.phase === "running") {
    return `<span class="running-badge"><span class="dot"></span>Playing ${esc(inst.minecraftVersion)}</span>`;
  }
  return `<span class="muted">${esc(ago(inst.lastPlayed))}${inst.joinServer ? ` · joins ${esc(inst.joinServer)}` : ""}</span>`;
}

function playButton(inst) {
  const r = runOf(inst.name);
  if (r.phase === "preparing") return `<button class="btn btn-play is-busy" id="play-btn" data-action="stop" data-name="${esc(inst.name)}">${icon("stop", 22)} Cancel</button>`;
  if (r.phase === "running") return `<button class="btn btn-play is-running" id="play-btn" data-action="stop" data-name="${esc(inst.name)}">${icon("stop", 22)} Stop</button>`;
  return `<button class="btn btn-play" id="play-btn" data-action="play" data-name="${esc(inst.name)}">${icon("play", 26)} Play</button>`;
}

function chipsFor(inst) {
  const v = S.versions?.versions.find((x) => x.id === inst.minecraftVersion);
  return `<span class="chip ${v?.kind || "release"}">${esc(inst.minecraftVersion)}</span>
    <span class="chip ${inst.loader}">${icon(loaderIcon(inst.loader), 12)} ${esc(inst.loaderLabel)}</span>
    <span class="chip java">${icon("sparkle", 12)} ${inst.memoryMb ? gb(inst.memoryMb) : gb(S.settings?.max_memory_mb || 4096)} RAM</span>
    ${inst.modCount ? `<span class="chip">${icon("puzzle", 12)} ${inst.modCount} mods</span>` : ""}`;
}

function tileHTML(inst, actions = false) {
  const live = runOf(inst.name).phase !== "idle";
  return `<div class="panel tile" data-action="select-play" data-name="${esc(inst.name)}" role="button" tabindex="0">
    ${live ? `<span class="live running-badge"><span class="dot"></span></span>` : ""}
    <div class="block ${inst.loader}">${inst.loader === "vanilla" ? "" : icon(loaderIcon(inst.loader), 30)}</div>
    <div class="tile-name">${esc(inst.name)}</div>
    <div class="tile-sub">${esc(inst.minecraftVersion)} · ${esc(inst.loaderLabel)}</div>
    <div class="tile-sub">${esc(ago(inst.lastPlayed))}</div>
    ${actions ? `<div class="tile-actions">
      <button class="btn btn-sm btn-grass" data-action="play" data-name="${esc(inst.name)}" aria-label="Play ${esc(inst.name)}">${icon("play", 12)}</button>
      <button class="btn btn-sm" data-action="edit" data-name="${esc(inst.name)}" aria-label="Edit">${icon("edit", 12)}</button>
      <button class="btn btn-sm" data-action="folder" data-name="${esc(inst.name)}" aria-label="Open folder">${icon("folder", 12)}</button>
      <span class="grow"></span>
      <button class="btn btn-sm btn-danger" data-action="delete" data-name="${esc(inst.name)}" aria-label="Delete">${icon("trash", 12)}</button>
    </div>` : ""}
  </div>`;
}

function renderHome() {
  const inst = selectedInstance();
  const acc = selectedAccount();
  if (!inst) {
    return {
      sub: "",
      html: `<div class="panel empty">
        <div class="block vanilla" style="width:84px;height:84px"></div>
        <h3>Welcome to Vortex</h3>
        <p class="muted">Pick any Minecraft version ever released — or add Fabric or Forge — and play in one click.</p>
        <button class="btn btn-primary" data-action="route" data-route="new">${icon("plus", 14)} Create your first instance</button>
      </div>`,
    };
  }
  const recent = [...S.instances].sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0)).slice(0, 5);
  const news = S.news;
  const tip = TIPS[Math.floor(Date.now() / 60000) % TIPS.length];
  return {
    sub: "",
    html: `
    ${!acc ? `<div class="panel banner"><span class="block sm vanilla"></span><div class="grow"><strong>No account yet</strong><div class="muted">Add an offline account to play singleplayer, or sign in with Microsoft.</div></div><button class="btn btn-primary btn-sm" data-action="route" data-route="accounts">Add account</button></div>` : ""}
    <div class="panel hero">
      <div>
        <div class="hero-kicker">${icon("sun", 14)}<span id="hero-greeting">${esc(greeting())}</span></div>
        <div class="switcher">
          <h2>${esc(inst.name)}</h2>
          <button class="btn btn-sm" data-action="toggle-menu" aria-haspopup="true">${icon("chev", 10)} Switch instance</button>
          ${S.menuOpen ? `<div class="panel switcher-menu">${S.instances.map((i) => `<button class="switcher-item ${i.name === inst.name ? "on" : ""}" data-action="select" data-name="${esc(i.name)}"><span class="block sm ${i.loader}">${i.loader === "vanilla" ? "" : icon(loaderIcon(i.loader), 18)}</span><span><strong>${esc(i.name)}</strong><br><span class="muted">${esc(i.minecraftVersion)} · ${esc(i.loaderLabel)}</span></span></button>`).join("")}</div>` : ""}
        </div>
        <div class="hero-meta">${chipsFor(inst)}</div>
        <div class="hero-status" id="hero-status">${heroStatus(inst)}</div>
      </div>
      <div class="hero-side">
        <span id="play-slot">${playButton(inst)}</span>
        <div class="row" style="gap:6px">
          <button class="btn btn-sm" data-action="edit" data-name="${esc(inst.name)}">${icon("edit", 12)} Edit</button>
          <button class="btn btn-sm" data-action="folder" data-name="${esc(inst.name)}">${icon("folder", 12)} Folder</button>
          <button class="btn btn-sm" data-action="open-log" data-name="${esc(inst.name)}">${icon("log", 12)} Log</button>
        </div>
      </div>
    </div>
    <div class="section-title"><h2>Jump back in</h2><button class="btn btn-sm" data-action="route" data-route="instances">All instances</button></div>
    <div class="tiles">${recent.map((i) => tileHTML(i)).join("")}
      <button class="panel tile tile-new" data-action="route" data-route="new">${icon("plus", 34)}<span>New instance</span></button>
    </div>
    <div class="section-title"><h2>Minecraft news</h2><span class="muted" style="color:var(--title-ink)">${news ? "" : "Loading…"}</span></div>
    <div class="news">${(news || []).map((n, idx) => `<button class="panel news-card" data-action="news" data-idx="${idx}">
        ${n.image_url ? `<div class="news-img" style="background-image:url('${esc(n.image_url)}')"></div>` : `<div class="news-img ph">${icon("sun", 44)}</div>`}
        <div class="news-body"><div class="row" style="gap:6px"><span class="chip ${esc(n.kind)}">${esc(n.kind)}</span><span class="muted" style="font-size:12px">${esc(n.date)}</span></div><strong>${esc(n.title)}</strong><span class="muted">${esc(n.summary)}</span></div>
      </button>`).join("")}</div>
    <div class="panel tip">${icon("sparkle", 18)}<span class="tip-text" id="tip-text">${esc(tip)}</span></div>`,
    mount(root) {
      bindTilt(root);
    },
  };
}

function bindTilt(root) {
  $$(".tile:not(.tile-new)", root).forEach((t) => {
    t.addEventListener("pointermove", (e) => {
      if (!anims()) return;
      const r = t.getBoundingClientRect();
      const x = (e.clientX - r.left) / r.width - 0.5;
      const y = (e.clientY - r.top) / r.height - 0.5;
      t.style.setProperty("--ry", `${x * 10}deg`);
      t.style.setProperty("--rx", `${-y * 10}deg`);
    });
    t.addEventListener("pointerleave", () => {
      t.style.setProperty("--ry", "0deg");
      t.style.setProperty("--rx", "0deg");
    });
    t.addEventListener("pointerenter", () => sound.hover());
  });
}

/** Refresh only the live bits (no page re-render, so animations don't restart). */
function updateRunUI(name) {
  const inst = selectedInstance();
  if (S.route === "home" && inst && inst.name === name) {
    const slot = $("#play-slot");
    if (slot) slot.innerHTML = playButton(inst);
    const status = $("#hero-status");
    if (status) {
      const r = runOf(name);
      const fill = status.querySelector(".xp-fill");
      // A new stage restarts the bar: re-render instead of animating backwards.
      const sameStage = status.dataset.stage === (r.stage || "");
      status.dataset.stage = r.stage || "";
      if (r.phase === "preparing" && fill && r.total && sameStage) {
        const pct = Math.round((r.done / r.total) * 100);
        fill.style.width = `${pct}%`;
        status.querySelector(".xp")?.classList.remove("indeterminate");
        const st = status.querySelector(".stage-text");
        if (st) st.innerHTML = `<span>${esc(r.stage)}</span><span>${pct}%</span>`;
      } else {
        status.innerHTML = heroStatus(inst);
      }
    }
  }
  if (S.route === "instances" || (S.route === "home" && runOf(name).phase !== "preparing")) {
    $$(`.tile[data-name="${CSS.escape(name)}"]`).forEach((t) => {
      const live = runOf(name).phase !== "idle";
      const badge = t.querySelector(".live");
      if (live && !badge) t.insertAdjacentHTML("afterbegin", `<span class="live running-badge"><span class="dot"></span></span>`);
      if (!live && badge) badge.remove();
    });
  }
}

// -------------------------------------------------------------- instances --

function renderInstances() {
  const q = S.instSearch.toLowerCase();
  let list = S.instances.filter((i) => !q || i.name.toLowerCase().includes(q) || i.minecraftVersion.toLowerCase().includes(q) || i.loader.includes(q));
  if (S.instSort === "recent") list.sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0));
  if (S.instSort === "name") list.sort((a, b) => a.name.localeCompare(b.name));
  if (S.instSort === "version") list.sort((a, b) => b.minecraftVersion.localeCompare(a.minecraftVersion, undefined, { numeric: true }));
  return {
    sub: `${S.instances.length} instance${S.instances.length === 1 ? "" : "s"}`,
    html: `<div class="toolbar">
        <input class="input" id="inst-search" type="search" placeholder="Search instances" value="${esc(S.instSearch)}" aria-label="Search instances">
        <div class="seg" role="group" aria-label="Sort">
          ${["recent", "name", "version"].map((s) => `<button class="seg-btn ${S.instSort === s ? "on" : ""}" data-action="inst-sort" data-sort="${s}">${s}</button>`).join("")}
        </div>
        <span class="grow"></span>
        <button class="btn btn-primary" data-action="route" data-route="new">${icon("plus", 14)} New instance</button>
      </div>
      ${list.length ? `<div class="tiles">${list.map((i) => tileHTML(i, true)).join("")}</div>` : `<div class="panel empty"><h3>No instances found</h3><p class="muted">Try a different search, or create a new instance.</p></div>`}`,
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
      bindTilt(root);
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

function autoName(w) {
  const label = { vanilla: "", fabric: " Fabric", forge: " Forge" }[w.loader];
  return `${w.version || "Minecraft"}${label}`;
}

function renderWizard() {
  const w = (S.wizard ||= newWizard());
  if (!w.nameTouched) w.name = autoName(w);
  const steps = ["Version", "Loader", "Details"];
  const stepper = `<div class="stepper">${steps.map((s, i) => `${i ? '<span class="step-line"></span>' : ""}<span class="step ${w.step === i + 1 ? "on" : ""} ${w.step > i + 1 ? "done" : ""}"><b>${w.step > i + 1 ? icon("check", 14) : i + 1}</b>${s}</span>`).join("")}</div>`;
  let body = "";
  if (w.step === 1) {
    if (!S.versions) {
      body = `<div class="empty"><div class="xp indeterminate" style="width:300px"><div class="xp-fill"></div></div><span class="muted">Loading every Minecraft version…</span></div>`;
    } else {
      const q = w.search.trim().toLowerCase();
      const rows = S.versions.versions.filter((v) => (w.kinds[v.kind] ?? w.kinds.snapshot) && (!q || v.id.toLowerCase().includes(q)));
      body = `<div class="row" style="gap:10px">
          <div class="seg" role="group" aria-label="Version types">${["release", "snapshot", "beta", "alpha"].map((k) => `<button class="seg-btn ${w.kinds[k] ? "on" : ""}" data-action="wiz-kind" data-kind="${k}">${k}</button>`).join("")}</div>
          <input class="input grow" id="wiz-search" type="search" placeholder="Search ${S.versions.versions.length} versions" value="${esc(w.search)}" aria-label="Search versions">
          <button class="btn btn-sm" data-action="wiz-pick" data-id="${esc(S.versions.latestRelease)}">${icon("sparkle", 12)} Latest release</button>
          <button class="btn btn-sm" data-action="wiz-pick" data-id="${esc(S.versions.latestSnapshot)}">Latest snapshot</button>
        </div>
        <div class="versions" id="wiz-list" role="listbox" aria-label="Minecraft versions">
          ${rows.map((v) => `<button class="vrow ${w.version === v.id ? "on" : ""}" role="option" aria-selected="${w.version === v.id}" data-action="wiz-pick" data-id="${esc(v.id)}"><span class="vid">${esc(v.id)}</span><span><span class="chip ${v.kind}">${v.kind}</span></span><span class="vdate">${esc(v.date)}</span></button>`).join("") || `<div class="empty muted">No versions match.</div>`}
        </div>
        <div class="row"><span class="muted">${rows.length} shown · selected <strong>${esc(w.version || "none")}</strong></span><span class="grow"></span><button class="btn btn-primary" data-action="wiz-next" ${w.version ? "" : "disabled"}>Next ${icon("play", 12)}</button></div>`;
    }
  } else if (w.step === 2) {
    const card = (kind, title, desc) => {
      const lv = w.loaderVersions[kind];
      const unavailable = kind !== "vanilla" && Array.isArray(lv) && lv.length === 0;
      const state = kind === "vanilla" ? "Always available" : lv === undefined ? "Checking…" : lv === "error" ? "Couldn't check — offline?" : unavailable ? `Not available for ${w.version}` : `${lv.length} build${lv.length === 1 ? "" : "s"} available`;
      return `<button class="panel loader-card ${w.loader === kind ? "on" : ""}" data-action="wiz-loader" data-kind="${kind}" ${unavailable ? "disabled" : ""}>
        <div class="block ${kind}">${kind === "vanilla" ? "" : icon(loaderIcon(kind), 30)}</div>
        <h3>${title}</h3><span class="muted">${desc}</span><span class="chip">${esc(state)}</span></button>`;
    };
    const lv = w.loaderVersions[w.loader];
    body = `<div class="loader-cards">
        ${card("vanilla", "Vanilla", "Pure Minecraft, exactly as Mojang ships it.")}
        ${card("fabric", "Fabric", "Lightweight modding — performance mods, minimaps, PvP utilities.")}
        ${card("forge", "Forge", "The classic mod loader — big content mods and modpacks.")}
      </div>
      ${w.loader !== "vanilla" && Array.isArray(lv) && lv.length ? `<div class="field" style="max-width:420px"><label for="wiz-lv">${w.loader === "forge" ? "Forge build" : "Fabric loader"}</label>
        <select class="input" id="wiz-lv"><option value="">Recommended${lv.find((x) => x.recommended) ? ` (${esc(lv.find((x) => x.recommended).display)})` : ""}</option>
        ${lv.slice(0, 60).map((x) => `<option value="${esc(x.id)}" ${w.loaderVersion === x.id ? "selected" : ""}>${esc(x.display)}${x.recommended ? " — recommended" : x.latest ? " — latest" : ""}${x.stable ? "" : " (beta)"}</option>`).join("")}</select></div>` : ""}
      <div class="row"><button class="btn" data-action="wiz-back">Back</button><span class="grow"></span><button class="btn btn-primary" data-action="wiz-next">Next ${icon("play", 12)}</button></div>`;
  } else {
    const pct = ((w.memory - 1024) / (16384 - 1024)) * 100;
    body = `<div class="field"><label for="wiz-name">Name</label><input class="input" id="wiz-name" maxlength="48" value="${esc(w.name)}"></div>
      <div class="field"><label for="wiz-mem">Memory</label><div class="row" style="gap:14px"><input class="range grow" id="wiz-mem" type="range" min="1024" max="16384" step="512" value="${w.memory}" style="--fill:${pct}%"><span class="value-bubble" id="wiz-mem-v">${gb(w.memory)}</span></div></div>
      <div class="panel" style="padding:16px 18px"><div class="summary">
        <span class="label">Minecraft</span><strong>${esc(w.version)}</strong>
        <span class="label">Loader</span><strong>${esc(w.loader === "vanilla" ? "Vanilla" : `${w.loader === "forge" ? "Forge" : "Fabric"} ${w.loaderVersion || "(recommended)"}`)}</strong>
        <span class="label">Java</span><strong>Downloaded automatically</strong>
      </div></div>
      <div class="row"><button class="btn" data-action="wiz-back">Back</button><span class="grow"></span><button class="btn btn-grass" data-action="wiz-create">${icon("check", 14)} Create instance</button></div>`;
  }
  return {
    sub: `Step ${w.step} of 3`,
    html: `${stepper}<div class="panel wizard"><div class="wizard-body stack">${body}</div></div>`,
    mount(root) {
      const search = $("#wiz-search", root);
      if (search) {
        search.addEventListener("input", () => {
          w.search = search.value;
          const pos = search.selectionStart;
          render();
          const again = $("#wiz-search");
          again.focus();
          again.setSelectionRange(pos, pos);
        });
      }
      const on = $(".vrow.on", root);
      if (on) on.scrollIntoView({ block: "center" });
      const lvSel = $("#wiz-lv", root);
      if (lvSel) lvSel.addEventListener("change", () => (w.loaderVersion = lvSel.value || null));
      const name = $("#wiz-name", root);
      if (name) name.addEventListener("input", () => { w.name = name.value; w.nameTouched = true; });
      const mem = $("#wiz-mem", root);
      if (mem) mem.addEventListener("input", () => {
        w.memory = Number(mem.value);
        mem.style.setProperty("--fill", `${((w.memory - 1024) / (16384 - 1024)) * 100}%`);
        $("#wiz-mem-v").textContent = gb(w.memory);
      });
    },
  };
}

async function loadLoaderVersions(w) {
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
  const picker = `<select class="input" id="mods-inst" aria-label="Instance">${S.instances.map((i) => `<option value="${esc(i.name)}" ${i.name === S.mods.instance ? "selected" : ""}>${esc(i.name)} — ${esc(i.loaderLabel)}</option>`).join("")}</select>`;
  if (!inst) {
    return { sub: "", html: `<div class="panel empty"><h3>No instances</h3><button class="btn btn-primary" data-action="route" data-route="new">Create one</button></div>` };
  }
  let body;
  if (inst.loader === "vanilla") {
    body = `<div class="panel empty"><div class="block vanilla" style="width:64px;height:64px"></div><h3>Vanilla doesn't load mods</h3><p class="muted">Create a Fabric or Forge instance to use mods.</p><button class="btn btn-primary" data-action="route" data-route="new">${icon("plus", 14)} New modded instance</button></div>`;
  } else if (S.mods.loading) {
    body = `<div class="panel empty"><div class="xp indeterminate" style="width:260px"><div class="xp-fill"></div></div></div>`;
  } else {
    const list = S.mods.list;
    body = `${list.length ? `<div class="list">${list.map((m) => `<div class="panel list-row ${m.enabled ? "" : "off"}">
        <div class="block sm ${m.loader === "fabric" || m.loader === "quilt" ? "fabric" : m.loader === "forge" ? "forge" : "vanilla"}">${m.loader === "unknown" ? "" : icon(m.loader === "forge" ? "forge" : "fabric", 18)}</div>
        <div class="meta"><strong>${esc(m.name)} ${m.version ? `<span class="chip">${esc(m.version)}</span>` : ""}</strong><span>${esc(m.description || m.file)}</span></div>
        <button class="switch" role="switch" aria-checked="${m.enabled}" aria-label="Enable ${esc(m.name)}" data-action="mod-toggle" data-file="${esc(m.file)}" data-on="${m.enabled}"></button>
        <button class="btn btn-sm btn-danger" data-action="mod-delete" data-file="${esc(m.file)}" aria-label="Delete ${esc(m.name)}">${icon("trash", 12)}</button>
      </div>`).join("")}</div>` : `<div class="panel empty"><h3>No mods yet</h3><p class="muted">Add ${inst.loader === "forge" ? "Forge" : "Fabric"} mods for Minecraft ${esc(inst.minecraftVersion)} to this instance's mods folder${isTauri ? ", or drop .jar files right here" : ""}.</p></div>`}
      ${isTauri ? `<div class="panel dropzone" id="dropzone">${icon("download", 18)} Drop .jar files anywhere on this page to install them</div>` : ""}`;
  }
  return {
    sub: inst ? `${S.mods.list.length} in ${inst.name}` : "",
    html: `<div class="toolbar">${picker}<span class="grow"></span>
      <button class="btn" data-action="mods-refresh">Refresh</button>
      <button class="btn btn-primary" data-action="folder-mods" ${inst.loader === "vanilla" ? "disabled" : ""}>${icon("folder", 14)} Open mods folder</button></div>${body}`,
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
  return {
    sub: `${S.accounts.length} account${S.accounts.length === 1 ? "" : "s"}`,
    html: `${S.accounts.length ? `<div class="list">${S.accounts.map((a) => `<div class="panel list-row">
        <canvas class="avatar lg" width="8" height="8" data-avatar="${esc(a.uuid)}"></canvas>
        <div class="meta"><strong>${esc(a.username)} ${a.selected ? `<span class="chip release">${icon("check", 10)} In use</span>` : ""}</strong><span>${a.kind === "microsoft" ? "Microsoft account" : "Offline account — singleplayer & LAN"}</span></div>
        ${a.selected ? "" : `<button class="btn btn-sm btn-grass" data-action="acc-use" data-uuid="${esc(a.uuid)}">Use</button>`}
        <button class="btn btn-sm btn-danger" data-action="acc-remove" data-uuid="${esc(a.uuid)}" aria-label="Remove ${esc(a.username)}">${icon("trash", 12)}</button>
      </div>`).join("")}</div>` : `<div class="panel empty"><h3>No accounts</h3><p class="muted">Add one below to start playing.</p></div>`}
      <div class="settings-grid" style="margin-top:24px">
        <div class="panel card"><h3>${icon("user", 16)} Offline account</h3>
          <p class="muted" style="margin:0">Play singleplayer and LAN with any name. Online-mode servers will refuse it.</p>
          <div class="row" style="gap:8px"><input class="input grow" id="offline-name" maxlength="16" placeholder="Username (3–16 letters, digits, _)" aria-label="Offline username"><button class="btn btn-primary" data-action="acc-offline">Add</button></div>
        </div>
        <div class="panel card"><h3>${icon("sparkle", 16)} Microsoft account</h3>
          <p class="muted" style="margin:0">Sign in with the Microsoft account that owns Minecraft: Java Edition to play online.</p>
          <div><button class="btn btn-violet" data-action="acc-ms">Sign in with Microsoft</button></div>
          ${S.settings?.microsoft_client_id ? "" : `<span class="muted" style="font-size:13px">Needs your Azure client ID in Settings first.</span>`}
        </div>
      </div>`,
    mount(root) {
      const input = $("#offline-name", root);
      input.addEventListener("keydown", (e) => { if (e.key === "Enter") actions["acc-offline"](); });
    },
  };
}

// --------------------------------------------------------------- settings --

function renderSettings() {
  const d = (S.draft ||= JSON.parse(JSON.stringify(S.settings)));
  const memPct = ((d.max_memory_mb - 1024) / (32768 - 1024)) * 100;
  const minPct = ((d.min_memory_mb - 256) / (8192 - 256)) * 100;
  const dlPct = ((d.download_concurrency - 1) / 63) * 100;
  const dirty = JSON.stringify(d) !== JSON.stringify(S.settings);
  return {
    sub: dirty ? "Unsaved changes" : "",
    html: `<div class="settings-grid">
      <div class="panel card"><h3>${icon("sun", 16)} Look &amp; feel</h3>
        <div class="field"><span class="label">Sky</span>
          <div class="seg" role="group" aria-label="Sky">${["auto", "dawn", "day", "dusk", "night"].map((m) => `<button class="seg-btn ${d.sky === m ? "on" : ""}" data-action="set-sky" data-sky="${m}">${m === "auto" ? "Follow clock" : m}</button>`).join("")}</div></div>
        <div class="setting"><div class="text"><strong>Sounds</strong><span>Clicks, launch fanfare and alerts</span></div><button class="switch" role="switch" aria-checked="${d.ui_sounds}" data-action="set-bool" data-key="ui_sounds" aria-label="Sounds"></button></div>
        <div class="setting"><div class="text"><strong>Animations</strong><span>Moving sky, transitions and particles</span></div><button class="switch" role="switch" aria-checked="${d.animations}" data-action="set-bool" data-key="animations" aria-label="Animations"></button></div>
      </div>
      <div class="panel card"><h3>${icon("gear", 16)} Game</h3>
        <div class="field"><label for="s-max">Max memory</label><div class="row" style="gap:14px"><input class="range grow" id="s-max" type="range" min="1024" max="32768" step="512" value="${d.max_memory_mb}" style="--fill:${memPct}%"><span class="value-bubble" id="s-max-v">${gb(d.max_memory_mb)}</span></div></div>
        <div class="field"><label for="s-min">Min memory</label><div class="row" style="gap:14px"><input class="range grow" id="s-min" type="range" min="256" max="8192" step="256" value="${d.min_memory_mb}" style="--fill:${minPct}%"><span class="value-bubble" id="s-min-v">${gb(d.min_memory_mb)}</span></div></div>
        <div class="field"><label for="s-java">Java</label><input class="input" id="s-java" placeholder="Automatic — recommended" value="${esc(d.java_path || "")}"></div>
        <div class="field"><label for="s-jvm">Extra JVM arguments</label><input class="input" id="s-jvm" placeholder="e.g. -XX:+UseG1GC" value="${esc((d.extra_jvm_args || []).join(" "))}"></div>
      </div>
      <div class="panel card"><h3>${icon("user", 16)} Microsoft sign-in</h3>
        <div class="field"><label for="s-client">Azure client ID</label><input class="input" id="s-client" placeholder="00000000-0000-0000-0000-000000000000" value="${esc(d.microsoft_client_id || "")}"></div>
        <span class="muted" style="font-size:13px">Register an app in the Azure portal and get it approved for the Minecraft API — the README has the steps.</span>
      </div>
      <div class="panel card"><h3>${icon("download", 16)} Downloads &amp; data</h3>
        <div class="field"><label for="s-dl">Parallel downloads</label><div class="row" style="gap:14px"><input class="range grow" id="s-dl" type="range" min="1" max="64" step="1" value="${d.download_concurrency}" style="--fill:${dlPct}%"><span class="value-bubble" id="s-dl-v">${d.download_concurrency}</span></div></div>
        <div class="field"><span class="label">Data folder</span><div class="row" style="gap:8px"><code class="grow" style="font-size:13px;word-break:break-all">${esc(S.dataDir)}</code><button class="btn btn-sm" data-action="folder-data">${icon("folder", 12)} Open</button></div></div>
        <span class="muted" style="font-size:13px">Vortex ${esc(S.appVersion)} · ${isTauri ? "desktop" : "preview mode"}</span>
      </div>
    </div>
    <div class="save-bar" id="save-bar" ${dirty ? "" : "hidden"}><span class="label" style="align-self:center">Unsaved changes</span><button class="btn btn-sm" data-action="settings-reset">Discard</button><button class="btn btn-sm btn-grass" data-action="settings-save">${icon("check", 12)} Save</button></div>`,
    mount(root) {
      const range = (id, key, fmt, lo, hi) => {
        const el = $(id, root);
        el.addEventListener("input", () => {
          d[key] = Number(el.value);
          el.style.setProperty("--fill", `${((d[key] - lo) / (hi - lo)) * 100}%`);
          $(`${id}-v`).textContent = fmt(d[key]);
          markDirty();
        });
      };
      range("#s-max", "max_memory_mb", gb, 1024, 32768);
      range("#s-min", "min_memory_mb", gb, 256, 8192);
      range("#s-dl", "download_concurrency", String, 1, 64);
      $("#s-java", root).addEventListener("input", (e) => { d.java_path = e.target.value.trim() || null; markDirty(); });
      $("#s-jvm", root).addEventListener("input", (e) => { d.extra_jvm_args = e.target.value.split(/\s+/).filter(Boolean); markDirty(); });
      $("#s-client", root).addEventListener("input", (e) => { d.microsoft_client_id = e.target.value.trim() || null; markDirty(); });
    },
  };
}

function markDirty() {
  const dirty = JSON.stringify(S.draft) !== JSON.stringify(S.settings);
  $("#page-sub").textContent = dirty ? "Unsaved changes" : "";
  const bar = $("#save-bar");
  if (bar) bar.hidden = !dirty;
}

// ----------------------------------------------------------------- modals --

function modal(html, { onMount } = {}) {
  const root = $("#modal-root");
  root.innerHTML = `<div class="modal-backdrop"><div class="panel modal" role="dialog" aria-modal="true">${html}</div></div>`;
  hydrateIcons(root);
  const backdrop = root.firstElementChild;
  backdrop.addEventListener("click", (e) => { if (e.target === backdrop) closeModal(); });
  onMount?.(root);
  setTimeout(() => root.querySelector("input, .btn")?.focus(), 50);
}

function closeModal() {
  $("#modal-root").innerHTML = "";
}

function confirmModal(title, text, okLabel = "Delete", danger = true) {
  return new Promise((resolve) => {
    modal(`<h3>${esc(title)}</h3><p class="muted" style="margin:0">${esc(text)}</p>
      <div class="modal-actions"><button class="btn" id="m-cancel">Cancel</button><button class="btn ${danger ? "btn-danger" : "btn-primary"}" id="m-ok">${esc(okLabel)}</button></div>`, {
      onMount(root) {
        $("#m-cancel", root).onclick = () => { closeModal(); resolve(false); };
        $("#m-ok", root).onclick = () => { closeModal(); resolve(true); };
      },
    });
  });
}

function editModal(inst) {
  const mem = inst.memoryMb || S.settings.max_memory_mb;
  modal(`<h3>${icon("edit", 18)} ${esc(inst.name)}</h3>
    <div class="setting"><div class="text"><strong>Custom memory</strong><span>Otherwise uses the global setting (${gb(S.settings.max_memory_mb)})</span></div><button class="switch" role="switch" id="e-memon" aria-checked="${!!inst.memoryMb}" aria-label="Custom memory"></button></div>
    <div class="row" style="gap:14px" id="e-memrow"><input class="range grow" id="e-mem" type="range" min="1024" max="32768" step="512" value="${mem}" style="--fill:${((mem - 1024) / 31744) * 100}%"><span class="value-bubble" id="e-mem-v">${gb(mem)}</span></div>
    <div class="field"><label for="e-server">Join server on start</label><input class="input" id="e-server" placeholder="host[:port] — optional" value="${esc(inst.joinServer || "")}"></div>
    <div class="field"><label for="e-jvm">JVM arguments</label><input class="input" id="e-jvm" placeholder="optional" value="${esc(inst.jvmArgs || "")}"></div>
    <div class="modal-actions"><button class="btn" id="e-cancel">Cancel</button><button class="btn btn-grass" id="e-save">${icon("check", 14)} Save</button></div>`, {
    onMount(root) {
      const sw = $("#e-memon", root), range = $("#e-mem", root), row = $("#e-memrow", root);
      const sync = () => (row.style.opacity = sw.getAttribute("aria-checked") === "true" ? "1" : "0.4");
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
          toast("Saved", `${inst.name} updated.`, "success");
          render();
        } catch (e) {
          sound.error();
          toast("Couldn't save", errText(e), "error");
        }
      };
    },
  });
}

// ---------------------------------------------------------------- actions --

async function refreshInstances() {
  S.instances = await call("list_instances");
  if (!S.instances.some((i) => i.name === S.selected)) S.selected = S.instances[0]?.name || null;
  fillConsoleInstances();
}

function select(name) {
  S.selected = name;
  localStorage.setItem("vortex.selected", name);
}

async function play(name) {
  if (runOf(name).phase !== "idle") return;
  if (!selectedAccount()) {
    sound.error();
    toast("No account", "Add an account first.", "warn");
    go("accounts");
    return;
  }
  select(name);
  S.runs[name] = { phase: "preparing", stage: "Starting", done: 0, total: 0 };
  S.logs[name] = [];
  S.console.instance = name;
  fillConsoleInstances();
  if (S.console.open) renderConsole();
  updateRunUI(name);
  try {
    await call("launch_instance", { name });
  } catch (e) {
    S.runs[name] = { phase: "idle" };
    updateRunUI(name);
    sound.error();
    toast("Couldn't launch", errText(e), "error", 7000);
  }
}

const actions = {
  route(el) { go(el.dataset.route); },
  play(el, e) {
    e.stopPropagation();
    play(el.dataset.name);
  },
  async stop(el, e) {
    e.stopPropagation();
    await call("stop_instance", { name: el.dataset.name });
  },
  "select-play"(el) {
    select(el.dataset.name);
    go("home");
  },
  select(el) {
    select(el.dataset.name);
    S.menuOpen = false;
    sound.pop();
    render();
  },
  "toggle-menu"() {
    S.menuOpen = !S.menuOpen;
    render();
  },
  edit(el, e) {
    e.stopPropagation();
    const inst = S.instances.find((i) => i.name === el.dataset.name);
    if (inst) editModal(inst);
  },
  folder(el, e) {
    e.stopPropagation();
    call("open_path", { target: "instance", name: el.dataset.name }).catch((er) => toast("Couldn't open folder", errText(er), "error"));
  },
  "folder-mods"() {
    call("open_path", { target: "mods", name: S.mods.instance }).catch((er) => toast("Couldn't open folder", errText(er), "error"));
  },
  "folder-data"() {
    call("open_path", { target: "data" }).catch((er) => toast("Couldn't open folder", errText(er), "error"));
  },
  async delete(el, e) {
    e.stopPropagation();
    const name = el.dataset.name;
    if (!(await confirmModal(`Delete ${name}?`, "Its worlds, mods and settings will be removed from this computer. This can't be undone."))) return;
    try {
      S.instances = await call("delete_instance", { name });
      if (S.selected === name) S.selected = S.instances[0]?.name || null;
      sound.pop();
      toast("Deleted", name, "info");
      fillConsoleInstances();
      render();
    } catch (er) {
      sound.error();
      toast("Couldn't delete", errText(er), "error");
    }
  },
  "open-log"(el) {
    openConsole(el.dataset.name);
  },
  news(el) {
    const n = S.news?.[Number(el.dataset.idx)];
    if (!n) return;
    modal(`${n.image_url ? `<div class="news-img" style="height:220px;background-image:url('${esc(n.image_url)}')"></div>` : ""}
      <div class="row" style="gap:8px"><span class="chip ${esc(n.kind)}">${esc(n.kind)}</span><span class="muted">${esc(n.date)}</span></div>
      <h3>${esc(n.title)}</h3><p style="margin:0">${esc(n.summary)}</p>
      <div class="modal-actions"><button class="btn" id="n-close">Close</button><button class="btn btn-primary" id="n-new">${icon("plus", 12)} Play ${esc(n.version)}</button></div>`, {
      onMount(root) {
        $("#n-close", root).onclick = closeModal;
        $("#n-new", root).onclick = () => {
          closeModal();
          S.wizard = newWizard();
          S.wizard.version = n.version;
          if (n.kind === "snapshot") S.wizard.kinds.snapshot = true;
          go("new");
        };
      },
    });
  },
  "inst-sort"(el) {
    S.instSort = el.dataset.sort;
    render();
  },
  "wiz-kind"(el) {
    const w = S.wizard;
    w.kinds[el.dataset.kind] = !w.kinds[el.dataset.kind];
    render();
  },
  "wiz-pick"(el) {
    const w = S.wizard;
    if (w.version !== el.dataset.id) {
      w.version = el.dataset.id;
      w.loaderVersions = {};
      w.loaderVersion = null;
    }
    const kind = S.versions?.versions.find((v) => v.id === w.version)?.kind;
    if (kind && !w.kinds[kind]) w.kinds[kind] = true;
    render();
  },
  "wiz-next"() {
    const w = S.wizard;
    if (w.step === 1) loadLoaderVersions(w);
    w.step = Math.min(3, w.step + 1);
    render();
  },
  "wiz-back"() {
    S.wizard.step = Math.max(1, S.wizard.step - 1);
    render();
  },
  "wiz-loader"(el) {
    const w = S.wizard;
    w.loader = el.dataset.kind;
    w.loaderVersion = null;
    render();
  },
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
      toast("Instance created!", `${inst.name} is ready — press Play to install and launch.`, "success");
      go("home");
    } catch (e) {
      el.disabled = false;
      sound.error();
      toast("Couldn't create instance", errText(e), "error");
    }
  },
  async "mod-toggle"(el) {
    const enabled = el.dataset.on !== "true";
    sound.toggle(enabled);
    el.setAttribute("aria-checked", enabled);
    try {
      S.mods.list = await call("toggle_mod", { name: S.mods.instance, file: el.dataset.file, enabled });
      setTimeout(render, 180);
    } catch (e) {
      toast("Couldn't change mod", errText(e), "error");
    }
  },
  async "mod-delete"(el) {
    if (!(await confirmModal("Delete this mod?", el.dataset.file))) return;
    try {
      S.mods.list = await call("delete_mod", { name: S.mods.instance, file: el.dataset.file });
      sound.pop();
      render();
    } catch (e) {
      toast("Couldn't delete mod", errText(e), "error");
    }
  },
  "mods-refresh"() {
    loadMods();
    render();
  },
  async "acc-use"(el) {
    S.accounts = await call("select_account", { uuid: el.dataset.uuid });
    sound.success();
    updateAccountChip();
    render();
  },
  async "acc-remove"(el) {
    const acc = S.accounts.find((a) => a.uuid === el.dataset.uuid);
    if (!(await confirmModal(`Remove ${acc?.username}?`, "You can add the account again later.", "Remove"))) return;
    S.accounts = await call("remove_account", { uuid: el.dataset.uuid });
    updateAccountChip();
    render();
  },
  async "acc-offline"() {
    const input = $("#offline-name");
    const name = input?.value.trim();
    if (!name) return;
    try {
      S.accounts = await call("add_offline_account", { username: name });
      sound.success();
      fx.burst(window.innerWidth / 2, window.innerHeight / 2, ["#5bb33a", "#ffd166", "#ffffff"], 24);
      toast("Account added", `Playing as ${name}.`, "success");
      updateAccountChip();
      render();
    } catch (e) {
      sound.error();
      toast("Couldn't add account", errText(e), "error");
    }
  },
  async "acc-ms"() {
    modal(`<h3>${icon("sparkle", 18)} Sign in with Microsoft</h3><div class="xp indeterminate"><div class="xp-fill"></div></div><p class="muted" id="ms-text" style="margin:0">Asking Microsoft for a sign-in code…</p><div class="modal-actions"><button class="btn" id="ms-close">Close</button></div>`, {
      onMount(root) { $("#ms-close", root).onclick = closeModal; },
    });
    try {
      S.accounts = await call("microsoft_login");
      closeModal();
      sound.success();
      fx.confetti(60);
      toast("Signed in", `Welcome, ${selectedAccount()?.username}!`, "success");
      updateAccountChip();
      render();
    } catch (e) {
      closeModal();
      sound.error();
      toast("Microsoft sign-in failed", errText(e), "error", 8000);
    }
  },
  "set-sky"(el) {
    S.draft.sky = el.dataset.sky;
    sky.setMode(S.draft.sky);
    render();
    markDirty();
  },
  "set-bool"(el) {
    const key = el.dataset.key;
    S.draft[key] = !S.draft[key];
    sound.toggle(S.draft[key]);
    el.setAttribute("aria-checked", S.draft[key]);
    markDirty();
  },
  "settings-reset"() {
    S.draft = null;
    applyLook();
    render();
  },
  async "settings-save"() {
    try {
      S.settings = await call("save_settings", { settings: S.draft });
      S.draft = null;
      applyLook();
      sound.success();
      toast("Settings saved", "", "success", 2500);
      render();
    } catch (e) {
      sound.error();
      toast("Couldn't save settings", errText(e), "error");
    }
  },
};

// ---------------------------------------------------------------- console --

const MAX_LINES = 5000;

function fillConsoleInstances() {
  const sel = $("#console-instance");
  const cur = S.console.instance || S.selected;
  sel.innerHTML = S.instances.map((i) => `<option value="${esc(i.name)}" ${i.name === cur ? "selected" : ""}>${esc(i.name)}</option>`).join("");
  S.console.instance = sel.value || null;
}

function lineVisible(l) {
  const lv = S.console.level;
  if (lv === "warn" && l.level !== "warn" && l.level !== "error") return false;
  if (lv === "error" && l.level !== "error") return false;
  if (S.console.search && !l.text.toLowerCase().includes(S.console.search)) return false;
  return true;
}

function lineEl(l) {
  const d = document.createElement("div");
  d.className = `ln ${l.level}`;
  d.textContent = l.text;
  return d;
}

function renderConsole() {
  const body = $("#console-body");
  const lines = (S.logs[S.console.instance] || []).filter(lineVisible).slice(-1500);
  body.innerHTML = "";
  if (!lines.length) {
    body.innerHTML = `<div class="console-empty">No output yet — press Play and the game's log appears here.</div>`;
    return;
  }
  const frag = document.createDocumentFragment();
  lines.forEach((l) => frag.appendChild(lineEl(l)));
  body.appendChild(frag);
  body.scrollTop = body.scrollHeight;
}

function openConsole(name) {
  if (name) {
    S.console.instance = name;
    fillConsoleInstances();
    $("#console-instance").value = name;
  }
  S.console.open = true;
  $("#console").classList.add("open");
  $("#log-dot").hidden = true;
  renderConsole();
}

function toggleConsole() {
  if (S.console.open) {
    S.console.open = false;
    $("#console").classList.remove("open");
  } else openConsole();
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
      sound.launch();
      fx.confetti(50);
      toast("Minecraft started", `${name} is running. Have fun!`, "success");
    } else {
      const failed = ev.state === "failed" || (ev.code != null && ev.code !== 0);
      const wasRunning = S.runs[name]?.phase === "running";
      S.runs[name] = { phase: "idle" };
      if (failed) {
        sound.error();
        toast(ev.state === "failed" ? "Launch failed" : "Game crashed", ev.error || `Exit code ${ev.code}. Check the log.`, "error", 9000);
        if (ev.state !== "failed") openConsole(name);
      } else if (wasRunning) {
        toast("Game closed", name, "info", 2500);
      }
      refreshInstances().then(() => { if (S.route === "home" || S.route === "instances") render(); });
    }
    updateRunUI(name);
  });
  on("game-log", (ev) => {
    const list = (S.logs[ev.instance] ||= []);
    list.push(...ev.lines);
    if (list.length > MAX_LINES) list.splice(0, list.length - MAX_LINES);
    if (S.console.open && S.console.instance === ev.instance) {
      const body = $("#console-body");
      const stick = body.scrollHeight - body.scrollTop - body.clientHeight < 40;
      body.querySelector(".console-empty")?.remove();
      const frag = document.createDocumentFragment();
      ev.lines.filter(lineVisible).forEach((l) => frag.appendChild(lineEl(l)));
      body.appendChild(frag);
      while (body.childElementCount > 1500) body.firstElementChild.remove();
      if (stick) body.scrollTop = body.scrollHeight;
    } else if (ev.lines.some((l) => l.level === "error")) {
      $("#log-dot").hidden = false;
    }
  });
  on("device-code", (ev) => {
    const text = $("#ms-text");
    if (!text) return;
    const modalEl = text.closest(".modal");
    modalEl.innerHTML = `<h3>${icon("sparkle", 18)} Sign in with Microsoft</h3>
      <p class="muted" style="margin:0">Open the Microsoft page and enter this code:</p>
      <div class="code-box">${esc(ev.userCode)}</div>
      <div class="row" style="justify-content:center"><button class="btn btn-sm" id="ms-copy">${icon("copy", 12)} Copy code</button><button class="btn btn-sm btn-violet" id="ms-open">${icon("link", 12)} Open ${esc(ev.verificationUri.replace("https://", ""))}</button></div>
      <div class="xp indeterminate"><div class="xp-fill"></div></div><p class="muted" style="margin:0;text-align:center">Waiting for you to finish signing in…</p>`;
    $("#ms-copy").onclick = () => { navigator.clipboard?.writeText(ev.userCode); toast("Copied", ev.userCode, "success", 1800); };
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
      fx.burst(window.innerWidth / 2, window.innerHeight / 2, ["#5bb33a", "#ffd166"], 30);
      toast("Mods installed", `${paths.length} file${paths.length === 1 ? "" : "s"} added`, "success");
      await refreshInstances();
      render();
    } catch (e) {
      toast("Couldn't install mods", errText(e), "error");
    }
  });
}

// -------------------------------------------------------------- UI chrome --

function updateAccountChip() {
  const a = selectedAccount();
  $("#account-name").textContent = a ? a.username : "No account";
  drawAvatar($("#account-avatar"), a?.uuid || "none");
}

function bindChrome() {
  hydrateIcons(document);
  $$(".nav-btn").forEach((b) => {
    b.addEventListener("click", () => go(b.dataset.route));
    b.addEventListener("pointerenter", () => sound.hover());
  });
  $("#account-chip").addEventListener("click", () => go("accounts"));
  $("#sky-chip").addEventListener("click", () => go("settings"));
  $("#log-toggle").addEventListener("click", toggleConsole);
  $("#console-close").addEventListener("click", toggleConsole);
  $("#console-clear").addEventListener("click", () => { S.logs[S.console.instance] = []; renderConsole(); });
  $("#console-copy").addEventListener("click", () => {
    const text = (S.logs[S.console.instance] || []).map((l) => l.text).join("\n");
    navigator.clipboard?.writeText(text);
    toast("Copied", "Log copied to clipboard", "success", 1800);
  });
  $("#console-instance").addEventListener("change", (e) => { S.console.instance = e.target.value; renderConsole(); });
  $$("#console-filter .seg-btn").forEach((b) => b.addEventListener("click", () => {
    S.console.level = b.dataset.level;
    $$("#console-filter .seg-btn").forEach((x) => x.classList.toggle("on", x === b));
    renderConsole();
  }));
  $("#console-search").addEventListener("input", (e) => { S.console.search = e.target.value.toLowerCase(); renderConsole(); });

  // Delegated actions, sounds and click particles.
  document.addEventListener("click", (e) => {
    const el = e.target.closest("[data-action]");
    if (!el || el.disabled) return;
    const fn = actions[el.dataset.action];
    if (fn) fn(el, e);
  });
  document.addEventListener("pointerdown", (e) => {
    const b = e.target.closest(".btn, .pill, .account-chip, .seg-btn, .tile, .loader-card, .vrow, .switcher-item, .news-card, .sky-chip");
    if (!b || b.disabled) return;
    sound.click();
    if (b.matches(".btn-play, .btn-grass, .btn-primary, .btn-violet")) {
      const colors = b.matches(".btn-play, .btn-grass") ? ["#7ccd50", "#5bb33a", "#ffd166", "#ffffff"] : ["#ffd166", "#ff8a3d", "#ffffff"];
      fx.burst(e.clientX, e.clientY, colors, b.matches(".btn-play") ? 26 : 12);
    }
  });
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      if ($("#modal-root").children.length) closeModal();
      else if (S.menuOpen) { S.menuOpen = false; render(); }
      else if (S.console.open) toggleConsole();
    }
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "l") { e.preventDefault(); toggleConsole(); }
    if ((e.ctrlKey || e.metaKey) && e.key === "Enter") {
      const inst = selectedInstance();
      if (inst) play(inst.name);
    }
    if (e.altKey && /^[1-6]$/.test(e.key)) go(Object.keys(ROUTES)[Number(e.key) - 1]);
  });
  window.addEventListener("resize", moveMarker);
  setInterval(() => {
    const t = $("#tip-text");
    if (!t) return;
    const next = TIPS[(TIPS.indexOf(t.textContent) + 1) % TIPS.length];
    t.textContent = next;
    t.style.animation = "none";
    void t.offsetWidth;
    t.style.animation = "";
  }, 9000);
}

// ------------------------------------------------------------------- boot --

async function boot() {
  const fill = $("#boot-fill");
  const hint = $("#boot-hint");
  const splash = SPLASHES[Math.floor(Math.random() * SPLASHES.length)];
  $("#splash").textContent = splash;
  $("#boot-splash").textContent = splash;
  fill.style.width = "20%";
  bindChrome();
  bindEvents();
  try {
    hint.textContent = "Reading your instances";
    const b = await call("bootstrap");
    S.appVersion = b.appVersion;
    S.dataDir = b.dataDir;
    S.settings = b.settings;
    S.accounts = b.accounts;
    S.instances = b.instances;
    if (!S.instances.some((i) => i.name === S.selected)) S.selected = S.instances[0]?.name || null;
    $("#rail-version").textContent = `v${b.appVersion}`;
    fill.style.width = "60%";
    applyLook();
    updateAccountChip();
    fillConsoleInstances();
  } catch (e) {
    hint.textContent = `Error: ${errText(e)}`;
    return;
  }
  hint.textContent = "Fetching Mojang's version list";
  call("list_versions", { refresh: false })
    .then((v) => {
      S.versions = v;
      if (S.wizard && !S.wizard.version) S.wizard.version = v.latestRelease;
      if (S.route === "new" || S.route === "home") render();
    })
    .catch((e) => toast("Offline?", `Couldn't load the version list: ${errText(e)}`, "warn", 6000));
  call("news")
    .then((n) => { S.news = n; if (S.route === "home") render(); })
    .catch(() => { S.news = []; if (S.route === "home") render(); });

  fill.style.width = "100%";
  await new Promise((r) => setTimeout(r, anims() ? 450 : 50));
  go(S.instances.length ? "home" : "new", { silent: true });
  const bootEl = $("#boot");
  document.body.classList.remove("booting");
  if (anims()) {
    sky.rise = 0;
    bootEl.classList.add("out");
    setTimeout(() => bootEl.remove(), 850);
  } else bootEl.remove();
}

boot();
