// Visual effects: pixel particle bursts, block confetti and toasts.
import { icon } from "./icons.js";
import { dust } from "./dust.js";

const canvas = document.getElementById("fx");
const ctx = canvas.getContext("2d");
let particles = [];
let running = false;
let enabled = true;
const SCALE = 4; // one "pixel" = 4 screen px

function resize() {
  canvas.width = Math.ceil(window.innerWidth / SCALE);
  canvas.height = Math.ceil(window.innerHeight / SCALE);
  ctx.imageSmoothingEnabled = false;
}
resize();
window.addEventListener("resize", resize);

function loop(last) {
  const now = performance.now();
  const dt = Math.min(0.05, (now - last) / 1000);
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  particles = particles.filter((p) => p.life > 0);
  for (const p of particles) {
    p.life -= dt;
    p.vy += p.g * dt;
    p.x += p.vx * dt;
    p.y += p.vy * dt;
    p.rot += p.vr * dt;
    ctx.globalAlpha = Math.max(0, Math.min(1, p.life / p.fade));
    ctx.fillStyle = p.color;
    const s = p.size;
    if (p.spin) {
      const w = Math.max(1, Math.round(Math.abs(Math.cos(p.rot)) * s));
      ctx.fillRect(Math.round(p.x - w / 2), Math.round(p.y), w, s);
    } else {
      ctx.fillRect(Math.round(p.x), Math.round(p.y), s, s);
    }
  }
  ctx.globalAlpha = 1;
  if (particles.length) requestAnimationFrame(() => loop(now));
  else {
    running = false;
    ctx.clearRect(0, 0, canvas.width, canvas.height);
  }
}

function kick() {
  if (!running) {
    running = true;
    const now = performance.now();
    requestAnimationFrame(() => loop(now));
  }
}

export const fx = {
  setEnabled(on) {
    enabled = !!on;
  },
  /** Pixel burst from a screen point (e.g. a clicked button). */
  burst(x, y, colors = ["#ffd166", "#ff8a3d", "#ffffff"], count = 14) {
    if (!enabled) return;
    for (let i = 0; i < count; i++) {
      const a = Math.random() * Math.PI * 2;
      const sp = 25 + Math.random() * 55;
      particles.push({
        x: x / SCALE, y: y / SCALE,
        vx: Math.cos(a) * sp, vy: Math.sin(a) * sp - 25,
        g: 140, life: 0.5 + Math.random() * 0.4, fade: 0.4,
        size: Math.random() > 0.7 ? 2 : 1,
        color: colors[i % colors.length], rot: 0, vr: 0, spin: false,
      });
    }
    kick();
  },
  /** Block confetti raining from the top of the window. */
  confetti(count = 90) {
    if (!enabled) return;
    const colors = ["#5bb33a", "#8b5a2b", "#ffd166", "#ff8a3d", "#c47aff", "#6fd1ff", "#ffffff"];
    for (let i = 0; i < count; i++) {
      particles.push({
        x: Math.random() * canvas.width, y: -Math.random() * 40,
        vx: (Math.random() - 0.5) * 30, vy: 20 + Math.random() * 40,
        g: 30, life: 2.2 + Math.random() * 1.5, fade: 0.8,
        size: 2, color: colors[i % colors.length],
        rot: Math.random() * 6, vr: 4 + Math.random() * 8, spin: true,
      });
    }
    kick();
  },
};

/** Toast: kind = info | success | error | warn. */
export function toast(title, text = "", kind = "info", ms = 4200) {
  const root = document.getElementById("toasts");
  const el = document.createElement("div");
  el.className = `toast toast-${kind}`;
  const ic = { info: "sparkle", success: "check", error: "x", warn: "sparkle" }[kind] || "sparkle";
  el.innerHTML = `<span class="toast-icon">${icon(ic, 18)}</span><div class="toast-body"><strong></strong><span></span></div><span class="toast-timer"></span>`;
  el.querySelector("strong").textContent = title;
  el.querySelector(".toast-body span").textContent = text;
  el.querySelector(".toast-timer").style.animationDuration = `${ms}ms`;
  root.appendChild(el);
  let closing = false;
  const close = () => {
    if (closing || !el.isConnected) return;
    closing = true;
    // The toast breaks up into dust that drifts off to the right.
    if (dust.enabled) { el.style.animation = "none"; dust.dissolve(el, { duration: 560, dir: "right", density: 0.9 }).then(() => el.remove()); return; }
    el.classList.add("out");
    setTimeout(() => el.remove(), 260);
  };
  el.addEventListener("click", close);
  setTimeout(close, ms);
  while (root.children.length > 4) root.firstElementChild.remove();
}
