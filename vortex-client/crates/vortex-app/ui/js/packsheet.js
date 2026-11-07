// The pack install sheet: when an instance's first launch adds its PvP,
// Normal or Performance mods, a liquid glass sheet shows each mod arriving.
// Dust streams into the mod being downloaded, bursts off it when it lands,
// and gathers into the progress orb; the sheet breaks into dust when done.

import { icon } from "./icons.js";
import { dust } from "./dust.js";
import { sound } from "./sound.js";

const GOLD = ["rgba(255,226,170,1)", "rgba(255,181,71,1)", "rgba(240,143,34,1)"];
const GREEN = ["rgba(196,246,160,1)", "rgba(134,220,92,1)", "rgba(255,210,140,1)"];
const RING = 2 * Math.PI * 27;

let sheet = null; // the open sheet's state

const esc = (s) => String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[c]);
const center = (el) => {
  const r = el.getBoundingClientRect();
  return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
};

function stateLabel(st, version) {
  return {
    pending: "Waiting",
    active: "Downloading",
    added: "Added",
    skipped: `No build for ${version}`,
    failed: "Couldn't download, tries again next launch",
  }[st] || "";
}

function chipHtml(m) {
  return `<li class="pk-chip" data-id="${esc(m.id)}" data-state="pending" title="${esc(m.name)}">
    <span class="pk-dot" aria-hidden="true"></span><span class="pk-name">${esc(m.name)}</span>
    <span class="pk-fill" aria-hidden="true"></span></li>`;
}

/**
 * Open the sheet for a pack install.
 * ev: { instance, pack, version, mods: [{ id, name }] }
 * meta: { title, icon } for the pack.
 */
function open(ev, meta, { animate = true } = {}) {
  close(true);
  const wrap = document.createElement("div");
  wrap.className = "pk-wrap";
  wrap.innerHTML = `
    <section class="pk glass strong" role="dialog" aria-modal="false" aria-labelledby="pk-title">
      <header class="pk-head">
        <div class="pk-orb" aria-hidden="true">
          <svg viewBox="0 0 64 64"><circle class="pk-track" cx="32" cy="32" r="27"/><circle class="pk-arc" cx="32" cy="32" r="27" stroke-dasharray="${RING}" stroke-dashoffset="${RING}"/></svg>
          <span class="pk-orb-icon">${icon(meta.icon || "download", 22)}</span>
        </div>
        <div class="pk-titles">
          <div class="pk-kicker">Setting up ${esc(ev.instance)}</div>
          <h2 id="pk-title">${esc(meta.title)} pack</h2>
          <p class="pk-sub" aria-live="polite">Getting ready</p>
        </div>
        <div class="pk-count"><b>0</b><span>/${ev.mods.length}</span></div>
      </header>
      <div class="pk-bar" role="progressbar" aria-valuemin="0" aria-valuemax="${ev.mods.length}" aria-valuenow="0"><i></i></div>
      <ul class="pk-grid">${ev.mods.map(chipHtml).join("")}</ul>
      <footer class="pk-foot">
        <span class="pk-note">From Modrinth, matched to ${esc(ev.version || "this version")}. The game starts right after.</span>
        <button class="btn btn-sm pk-hide" type="button">Hide</button>
      </footer>
    </section>`;
  document.body.appendChild(wrap);
  const el = wrap.querySelector(".pk");
  sheet = {
    instance: ev.instance, version: ev.version || "", wrap, el, total: ev.mods.length, finished: 0, added: 0,
    names: Object.fromEntries(ev.mods.map((m) => [m.id, m.name])), active: null, stopFlow: () => {}, timers: [],
  };
  el.querySelector(".pk-hide").addEventListener("click", () => { sound.click(); close(); });

  if (animate) {
    wrap.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 380, easing: "ease-out" });
    el.animate(
      [{ opacity: 0, scale: "0.9", translate: "0 26px", filter: "blur(10px)" }, { offset: 0.6, opacity: 1, scale: "1.015", filter: "blur(0px)" }, { opacity: 1, scale: "1", translate: "0 0" }],
      { duration: 640, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)" },
    );
    dust.gather(el, { duration: 820, density: 0.55, spread: 0.6 });
    [...el.querySelectorAll(".pk-chip")].forEach((c, i) => c.animate(
      [{ opacity: 0, translate: "0 10px", scale: "0.92" }, { opacity: 1, translate: "0 0", scale: "1" }],
      { duration: 420, delay: 180 + Math.min(i, 30) * 22, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)", fill: "backwards" },
    ));
  }
  sheet.stopFlow = dust.flow(el.querySelector(".pk-bar"));
  // A slow trickle of dust into the orb while it works.
  const orb = el.querySelector(".pk-orb");
  const trickle = setInterval(() => {
    if (!sheet || sheet.el !== el || !sheet.active) return;
    dust.charge(orb, { count: 26, duration: 700, colors: GOLD });
  }, 650);
  sheet.timers.push(() => clearInterval(trickle));
}

function setProgress() {
  const s = sheet;
  const k = s.total ? s.finished / s.total : 0;
  s.el.querySelector(".pk-arc").setAttribute("stroke-dashoffset", String(RING * (1 - k)));
  s.el.querySelector(".pk-bar > i").style.width = `${Math.max(k * 100, s.active ? 3 : 0)}%`;
  s.el.querySelector(".pk-bar").setAttribute("aria-valuenow", String(s.finished));
  const b = s.el.querySelector(".pk-count b");
  if (b.textContent !== String(s.finished)) {
    b.textContent = String(s.finished);
    b.animate([{ translate: "0 -5px", opacity: 0.35 }, { translate: "0 0", opacity: 1 }], { duration: 240, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)" });
  }
}

function setSub(text) {
  const p = sheet.el.querySelector(".pk-sub");
  if (p.textContent === text) return;
  p.textContent = text;
  p.animate([{ opacity: 0, translate: "0 6px" }, { opacity: 1, translate: "0 0" }], { duration: 260, easing: "ease-out" });
}

/** A mod changed state. ev: { instance, id, state } */
function mod(ev) {
  const s = sheet;
  if (!s || s.instance !== ev.instance) return;
  const chip = s.el.querySelector(`.pk-chip[data-id="${CSS.escape(ev.id)}"]`);
  if (!chip) return;
  chip.dataset.state = ev.state;
  chip.title = `${s.names[ev.id] || ""}: ${stateLabel(ev.state, s.version)}`;
  const name = s.names[ev.id] || "mod";
  if (ev.state === "active") {
    s.active = ev.id;
    chip.style.setProperty("--p", "0");
    const grid = s.el.querySelector(".pk-grid");
    const cr = chip.getBoundingClientRect(), gr = grid.getBoundingClientRect();
    if (cr.top < gr.top || cr.bottom > gr.bottom) grid.scrollTo({ top: grid.scrollTop + (cr.top - gr.top) - gr.height / 2 + cr.height / 2, behavior: "smooth" });
    dust.charge(chip, { count: 70, duration: 650, colors: GOLD });
    setSub(`Adding ${name}`);
    setProgress();
    return;
  }
  if (s.active === ev.id) s.active = null;
  s.finished += 1;
  const { x, y } = center(chip);
  if (ev.state === "added") {
    s.added += 1;
    chip.querySelector(".pk-dot").innerHTML = icon("check", 10);
    chip.animate([{ scale: "1" }, { offset: 0.35, scale: "1.09" }, { offset: 0.7, scale: "0.98" }, { scale: "1" }], { duration: 480, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)" });
    dust.burst(x, y, { count: 70, colors: GOLD, speed: 0.55 });
    sound.pop();
  } else {
    chip.querySelector(".pk-dot").innerHTML = icon("x", 10);
    dust.burst(x, y, { count: 18, colors: ["rgba(200,200,215,1)", "rgba(150,150,170,1)"], speed: 0.3 });
  }
  setProgress();
}

/** Byte progress of the mod being downloaded (from launch-progress). */
function bytes(p) {
  const s = sheet;
  if (!s || s.instance !== p.instance || !s.active || !p.total) return;
  const chip = s.el.querySelector(`.pk-chip[data-id="${CSS.escape(s.active)}"]`);
  chip?.style.setProperty("--p", String(Math.min(1, p.done / p.total)));
}

/** All done. ev: { instance, added, skipped, failed } */
function done(ev, meta) {
  const s = sheet;
  if (!s || s.instance !== ev.instance) return;
  s.active = null;
  s.finished = s.total;
  setProgress();
  s.el.classList.add("pk-done");
  s.el.querySelector("#pk-title").textContent = `${meta.title} pack ready`;
  const bits = [`${ev.added} mod${ev.added === 1 ? "" : "s"} added`];
  if (ev.skipped) bits.push(`${ev.skipped} not made for ${s.version}`);
  if (ev.failed) bits.push(`${ev.failed} will retry next launch`);
  setSub(bits.join(" · "));
  s.el.querySelector(".pk-orb-icon").innerHTML = icon("check", 22);
  const orb = s.el.querySelector(".pk-orb");
  const { x, y } = center(orb);
  orb.animate([{ scale: "1" }, { offset: 0.3, scale: "1.18" }, { offset: 0.65, scale: "0.96" }, { scale: "1" }], { duration: 640, easing: "cubic-bezier(0.2, 0.9, 0.25, 1)" });
  dust.burst(x, y, { count: 340, colors: GREEN, speed: 1.1 });
  dust.glowRing(x, y, (k) => 20 + k * 260, { duration: 900, width: 120 });
  sound.success();
  const t = setTimeout(() => close(), 1900);
  s.timers.push(() => clearTimeout(t));
}

/** Close the sheet; it breaks into dust unless `now`. */
function close(now = false) {
  const s = sheet;
  if (!s) return;
  sheet = null;
  s.stopFlow();
  s.timers.forEach((f) => f());
  if (now || !s.el.isConnected) { s.wrap.remove(); return; }
  s.wrap.classList.add("pk-leaving");
  dust.shatter(s.el, { duration: 760, density: 0.8 }).then(() => s.wrap.remove());
  setTimeout(() => s.wrap.remove(), 2200);
}

export const packSheet = {
  open, mod, bytes, done, close,
  /** The instance whose pack the sheet shows, if open. */
  get instance() { return sheet?.instance || null; },
};
