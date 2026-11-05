// A 3D Minecraft-style player built from CSS 3D cuboids textured with the
// player's skin, plus a small behaviour engine that lets it live in the world:
// it watches the cursor and the bees, wanders, mines the block in front of it,
// warms its hands at the campfire at night, carries a torch and waves.

import { BlockName } from "./nameblocks.js";

const DEG = Math.PI / 180;

/** Vortex's own default outfit (used when no skin is available). */
export function defaultSkin() {
  const c = document.createElement("canvas");
  c.width = 64; c.height = 64;
  const g = c.getContext("2d");
  const f = (col, x, y, w, h) => { g.fillStyle = col; g.fillRect(x, y, w, h); };
  const skin = "#c99468", skinD = "#b07e55", hair = "#2e2018", jacket = "#2b7f86", jacketD = "#22656b", trim = "#f2a93b", pants = "#2a3152", pantsD = "#222842", boot = "#3b2b20";
  // Head.
  f(skin, 0, 8, 32, 8); f(skinD, 0, 15, 32, 1);
  f(hair, 8, 0, 8, 8); f(hair, 0, 8, 32, 2); f(hair, 0, 10, 8, 3); f(hair, 24, 10, 8, 4); f(hair, 16, 10, 8, 3);
  f(hair, 8, 10, 1, 1); f(hair, 15, 10, 1, 1);
  f("#ffffff", 9, 12, 2, 1); f("#ffffff", 13, 12, 2, 1);
  f("#3b6fd1", 10, 12, 1, 1); f("#3b6fd1", 13, 12, 1, 1);
  f(skinD, 11, 13, 2, 1); f("#7a4a34", 11, 14, 2, 1);
  // Body.
  f(jacket, 16, 16, 24, 16); f(jacketD, 16, 16, 24, 1);
  f(trim, 23, 20, 2, 12); f(jacketD, 20, 30, 8, 2);
  f(trim, 21, 21, 1, 1); f(trim, 26, 21, 1, 1);
  // Right arm + left arm.
  for (const [u, v] of [[40, 16], [32, 48]]) {
    f(jacket, u, v, 16, 9); f(jacketD, u, v + 4, 16, 1); f(trim, u, v + 8, 16, 1);
    f(skin, u, v + 9, 16, 7); f(skinD, u, v + 15, 16, 1);
  }
  // Legs.
  for (const [u, v] of [[0, 16], [16, 48]]) {
    f(pants, u, v, 16, 16); f(pantsD, u, v + 4, 16, 1); f(boot, u, v + 13, 16, 3);
  }
  return c.toDataURL("image/png");
}

/** Normalise any skin to the 64×64 layout (legacy 64×32 skins mirror the right limbs). */
function normalise(url) {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      if (img.height === 64) return resolve(url);
      const c = document.createElement("canvas");
      c.width = 64; c.height = 64;
      const g = c.getContext("2d");
      g.imageSmoothingEnabled = false;
      g.drawImage(img, 0, 0);
      const copy = (sx, sy, w, h, dx, dy) => {
        g.save();
        g.translate(dx + w, dy);
        g.scale(-1, 1);
        g.drawImage(img, sx, sy, w, h, 0, 0, w, h);
        g.restore();
      };
      // Leg 0,16 → 16,48 and arm 40,16 → 32,48, each face mirrored.
      for (const [su, sv, du, dv] of [[0, 16, 16, 48], [40, 16, 32, 48]]) {
        copy(su + 4, sv, 4, 4, du + 4, dv); copy(su + 8, sv, 4, 4, du + 8, dv);
        copy(su, sv + 4, 4, 12, du + 8, dv + 4); copy(su + 4, sv + 4, 4, 12, du + 4, dv + 4);
        copy(su + 8, sv + 4, 4, 12, du, dv + 4); copy(su + 12, sv + 4, 4, 12, du + 12, dv + 4);
      }
      resolve(c.toDataURL("image/png"));
    };
    img.onerror = () => resolve(defaultSkin());
    img.src = url;
  });
}

function parts(slim) {
  const aw = slim ? 3 : 4;
  return {
    body: { size: [8, 12, 4], uv: [16, 16], ov: [16, 32], pivot: [0, -24, 0], offset: [0, 6, 0], inf: 0.25 },
    head: { size: [8, 8, 8], uv: [0, 0], ov: [32, 0], pivot: [0, -24, 0], offset: [0, -4, 0], inf: 0.5 },
    rarm: { size: [aw, 12, 4], uv: [40, 16], ov: [40, 32], pivot: [-(4 + aw / 2), -22, 0], offset: [0, 4, 0], inf: 0.25 },
    larm: { size: [aw, 12, 4], uv: [32, 48], ov: [48, 48], pivot: [4 + aw / 2, -22, 0], offset: [0, 4, 0], inf: 0.25 },
    rleg: { size: [4, 12, 4], uv: [0, 16], ov: [0, 32], pivot: [-2, -12, 0], offset: [0, 6, 0], inf: 0.25 },
    lleg: { size: [4, 12, 4], uv: [16, 48], ov: [0, 48], pivot: [2, -12, 0], offset: [0, 6, 0], inf: 0.25 },
  };
}

function cuboid([w, h, d], [U, V], inf, u, overlay, texW = 64, texH = 64) {
  const box = document.createElement("div");
  box.className = `pl-cube${overlay ? " pl-overlay" : ""}`;
  const W = (w + inf * 2) * u, Hh = (h + inf * 2) * u, D = (d + inf * 2) * u;
  const faces = [
    ["front", W, Hh, U + d, V + d, w, h, `translateZ(${D / 2}px)`],
    ["back", W, Hh, U + 2 * d + w, V + d, w, h, `rotateY(180deg) translateZ(${D / 2}px)`],
    ["right", D, Hh, U, V + d, d, h, `rotateY(-90deg) translateZ(${W / 2}px)`],
    ["left", D, Hh, U + d + w, V + d, d, h, `rotateY(90deg) translateZ(${W / 2}px)`],
    ["top", W, D, U + d, V, w, d, `rotateX(90deg) translateZ(${Hh / 2}px)`],
    ["bottom", W, D, U + d + w, V, w, d, `rotateX(-90deg) translateZ(${Hh / 2}px)`],
  ];
  for (const [name, fw, fh, tu, tv, tw, th, tf] of faces) {
    const el = document.createElement("div");
    el.className = `pl-face pl-${name}`;
    const sx = fw / tw, sy = fh / th;
    el.style.cssText = `width:${fw}px;height:${fh}px;left:${-fw / 2}px;top:${-fh / 2}px;transform:${tf};background-size:${texW * sx}px ${texH * sy}px;background-position:${-tu * sx}px ${-tv * sy}px;`;
    box.appendChild(el);
  }
  return box;
}

const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const approach = (cur, target, rate, dt) => cur + (target - cur) * Math.min(1, rate * dt);

export class Player {
  /**
   * @param host element to live in
   * @param world the World (null for a standalone preview)
   */
  constructor(host, world, { preview = false, unit = null } = {}) {
    this.host = host;
    this.world = world;
    this.preview = preview;
    this.fixedUnit = unit;
    this.slim = false;
    this.skinUrl = null;
    this.stage = document.createElement("div");
    this.stage.className = `pl-stage${preview ? " pl-preview" : ""}`;
    this.stage.innerHTML = `<div class="pl-shadow"></div><div class="pl-tag"></div><div class="pl-torch" hidden><i></i></div>`;
    this.root = document.createElement("div");
    this.root.className = "pl-root";
    this.root.setAttribute("role", "img");
    this.root.setAttribute("aria-label", "Your player");
    this.stage.appendChild(this.root);
    host.appendChild(this.stage);
    this.tag = this.stage.querySelector(".pl-tag");
    this.torchEl = this.stage.querySelector(".pl-torch");
    this.shadow = this.stage.querySelector(".pl-shadow");
    // On the launcher his name stands behind him in blocks.
    this.blockName = preview ? null : new BlockName(this.stage);

    // Pose and behaviour state.
    this.x = 0;
    this.y = 0;
    this.vy = 0;
    this.facing = 16;
    this.yaw = 0; this.pitch = 0;
    this.pose = { rArmX: 0, rArmZ: 0, lArmX: 0, lArmZ: 0, rLegX: 0, lLegX: 0 };
    this.walkPhase = 0;
    this.vx = 0; this.prevVx = 0; this.gait = 0; this.runK = 0; this.stance = 0;
    this.action = { type: "idle", t: 0, dur: 2.5 };
    this.activity = "idle";
    this.mouse = { x: 0, y: 0, at: 0 };
    this.dragYaw = null;
    window.addEventListener("pointermove", (e) => { this.mouse = { x: e.clientX, y: e.clientY, at: performance.now() }; });
    this.root.addEventListener("click", () => this.jump(true));
    if (preview) this.bindDrag();
    this.unit = 0;
    this.build();
    this.setSkin(null);
    this.last = performance.now();
    requestAnimationFrame((t) => this.loop(t));
  }

  bindDrag() {
    let startX = 0, startFacing = 0;
    this.root.addEventListener("pointerdown", (e) => {
      startX = e.clientX; startFacing = this.facing; this.dragYaw = true;
      this.root.setPointerCapture(e.pointerId);
    });
    this.root.addEventListener("pointermove", (e) => {
      if (this.dragYaw) this.facing = startFacing + (e.clientX - startX) * 0.6;
    });
    this.root.addEventListener("pointerup", () => (this.dragYaw = null));
  }

  currentUnit() {
    if (this.fixedUnit) return this.fixedUnit;
    const s = this.world ? this.world.scale : 3;
    return Math.max(4, Math.round(s * 2.4));
  }

  build() {
    const u = this.currentUnit();
    this.unit = u;
    this.root.innerHTML = "";
    this.parts = {};
    for (const [name, p] of Object.entries(parts(this.slim))) {
      const pivot = document.createElement("div");
      pivot.className = `pl-part pl-part-${name}`;
      const inner = document.createElement("div");
      inner.className = "pl-inner";
      inner.style.transform = `translate3d(${p.offset[0] * u}px,${p.offset[1] * u}px,${p.offset[2] * u}px)`;
      inner.appendChild(cuboid(p.size, p.uv, 0, u, false));
      inner.appendChild(cuboid(p.size, p.ov, p.inf, u, true));
      pivot.appendChild(inner);
      this.root.appendChild(pivot);
      this.parts[name] = { el: pivot, p };
    }
    // Cape: hangs from the shoulders behind the body (64×32 cape texture).
    const cape = document.createElement("div");
    cape.className = "pl-part pl-part-cape";
    const capeInner = document.createElement("div");
    capeInner.className = "pl-inner";
    capeInner.style.transform = `translate3d(0px,${8 * u}px,0px)`;
    const capeBox = cuboid([10, 16, 1], [0, 0], 0, u, false, 64, 32);
    capeBox.classList.add("pl-capebox");
    capeInner.appendChild(capeBox);
    cape.appendChild(capeInner);
    cape.hidden = !this.capeUrl;
    this.root.appendChild(cape);
    this.capeEl = cape;
    this.tag.style.setProperty("--u", `${u}px`);
    this.applySkin();
  }

  /** Show a cape (URL of a 64×32 cape texture), or none. */
  setCape(url) {
    this.capeUrl = url || null;
    if (this.capeUrl) this.root.style.setProperty("--cape", `url("${this.capeUrl}")`);
    if (this.capeEl) this.capeEl.hidden = !this.capeUrl;
  }

  async setSkin(url, slim = false) {
    const src = await normalise(url || defaultSkin());
    const rebuild = slim !== this.slim;
    this.slim = slim;
    this.skinUrl = src;
    if (rebuild) this.build();
    else this.applySkin();
  }

  applySkin() {
    if (!this.skinUrl) return;
    this.root.style.setProperty("--skin", `url("${this.skinUrl}")`);
  }

  setName(name) {
    this.tag.textContent = name || "";
    this.tag.hidden = !name;
    this.displayName = name || "";
  }

  /** idle | preparing | running */
  setActivity(a) {
    if (a === this.activity) return;
    const prev = this.activity;
    this.activity = a;
    if (this.world?.picture) {
      // Still on the wallpaper: a small cheer when the game opens, then back to the pose.
      if (a === "running" && prev !== "running" && this.action.type === "pose") this.start("cheer", 1.6);
      return;
    }
    if (a === "running" && prev !== "running") this.start("celebrate", 2.6);
    else if (a === "preparing") this.start("mine", 999);
    else if (a === "idle" && this.action.type === "mine") this.start("home", 6);
  }

  wave(seconds = 2) {
    if (this.world?.picture) return; // On the wallpaper he holds his pose.
    if (["mine", "jump", "run", "rest", "stretch", "homewave", "lean", "pose"].includes(this.action.type)) return;
    this.start("wave", seconds);
  }

  jump(fromClick = false) {
    if (this.y < -0.5) return;
    this.vy = -22;
    if (fromClick && this.onPoke) this.onPoke(this.headScreen());
  }

  start(type, dur) {
    this.action = { type, t: 0, dur, hits: 0 };
  }

  headScreen() {
    const r = this.root.getBoundingClientRect();
    return { x: r.left + r.width / 2, y: r.top };
  }

  // ---------------------------------------------------------- behaviour --

  /**
   * The opening scene: sprint in from the left edge to the Launch button,
   * lean on it out of breath, straighten up, step to the middle of the
   * button and hold a pose there.
   */
  intro() {
    const a = this.world ? this.world.anchor() : { x: 0 };
    const v = this.world?.viewport;
    this.x = (v ? v.x : 0) - a.x - this.unit * 12;
    this.introTarget = -this.unit * 9;
    this.introDone = false;
    this.start("run", 99);
  }

  chooseNext() {
    if (this.activity === "preparing") return this.start(this.world?.picture ? "idle" : "mine", 999);
    if (this.world?.picture) return this.start(this.introDone === false ? "idle" : "pose", 1e9);
    const night = this.world && this.world.darkness() > 0.55;
    const roll = Math.random();
    if (night && roll < 0.45) return this.start("campfire", 7 + Math.random() * 5);
    if (roll < 0.3) return this.start("walk", 8);
    if (roll < 0.5) return this.start("look", 3.5);
    if (roll < 0.66) return this.start("punch", 12);
    if (roll < 0.72) return this.start("wave", 1.8);
    return this.start("idle", 2.5 + Math.random() * 3);
  }

  /**
   * Move toward a screen-x offset like a person would: turn first, speed up,
   * cruise, then slow down so he arrives without sliding. `speed` is the
   * cruising speed in skin pixels per second (walk ~22, sprint ~60).
   * Returns true when there. The legs follow the distance covered (see
   * update), so the feet never skate.
   */
  walkTo(target, dt, u, speed = 22) {
    const dx = target - this.x;
    const dist = Math.abs(dx);
    if (dist < u * 0.4 && Math.abs(this.vx) < u * 1.5) { this.vx = 0; this.x = target; return true; }
    const dir = Math.sign(dx) || 1;
    this.facingTarget = dir > 0 ? 90 : -90;
    // Don't set off until he's mostly turned the right way.
    const aligned = Math.abs(this.facing - this.facingTarget) < 55;
    const maxV = u * speed;
    const accel = maxV * 3.4;
    const decel = maxV * 2.4;
    const vDes = aligned ? Math.min(maxV, Math.sqrt(2 * decel * Math.max(0, dist - u * 0.15))) : 0;
    const v = this.vx * dir;
    const nv = v < vDes ? Math.min(vDes, v + accel * dt) : Math.max(vDes, v - decel * dt);
    this.vx = nv * dir;
    const nx = this.x + this.vx * dt;
    if (Math.sign(target - nx) !== dir) { this.x = target; this.vx = 0; return true; }
    this.x = nx;
    return false;
  }

  update(dt) {
    const u = this.unit;
    const a = this.action;
    a.t += dt;
    const P = this.pose;
    let armSwing = 0, legSwing = 0;
    this.facingTarget = this.preview ? this.facing : 16;
    let rArmX = 0, rArmZ = 0, lArmX = 0, lArmZ = 0;
    let lookOverride = null;
    let walking = false;
    let running = false;
    let lean = 0;
    this.panting = false;
    this.posing = false;
    if (a.type !== "pose") this.stance = approach(this.stance || 0, 0, 6, dt);
    const world = this.world;
    const block = world ? world.scale * 6 : u * 3;

    if (this.preview) {
      if (!this.dragYaw) this.facing += dt * 18;
    } else {
      switch (a.type) {
        case "run": {
          running = true;
          walking = !this.walkTo(this.introTarget ?? 0, dt, u, 62);
          if (!walking) this.start("rest", 2.8);
          break;
        }
        case "lean":
        case "rest": {
          // Walk over (if needed), then lean on the button, out of breath.
          if (a.type === "lean" && !this.walkTo(this.introTarget ?? 0, dt, u)) { walking = true; break; }
          this.facingTarget = 0;
          // Bent over the button, both hands flat on its top edge.
          const k0 = Math.min(1, a.t / 0.35);
          const k = k0 * k0 * (3 - 2 * k0);
          lean = 17 * k;
          rArmX = 32 * k;
          lArmX = 32 * k;
          rArmZ = 20 * k;
          lArmZ = -20 * k;
          lookOverride = { yaw: Math.sin(a.t * 0.9) * 6, pitch: 30 };
          this.panting = a.type === "rest";
          if (a.t > (a.type === "rest" ? a.dur : 2.6)) this.start(a.type === "rest" ? "stretch" : "home", 1.1);
          break;
        }
        case "stretch":
          // Straighten up, arms up, then head home.
          rArmZ = 160 * Math.sin(Math.min(1, a.t / a.dur) * Math.PI);
          lArmZ = -160 * Math.sin(Math.min(1, a.t / a.dur) * Math.PI);
          lookOverride = { yaw: 0, pitch: -10 };
          if (a.t > a.dur) this.start(world?.picture ? "settle" : "homewave", 99);
          break;
        case "settle":
          // A few unhurried steps to the middle of the button, then the pose.
          walking = !this.walkTo(0, dt, u, 16);
          if (!walking) { this.introDone = true; this.start("pose", 1e9); }
          break;
        case "pose": {
          // A relaxed stance: a little turned, feet apart, arms loose and a
          // touch away from the body, chin up. Only breathing moves.
          this.facingTarget = 12;
          const k = Math.min(1, a.t / 0.9);
          const e = k * k * (3 - 2 * k);
          rArmZ = 6 * e; rArmX = -5 * e;
          lArmZ = -5 * e; lArmX = 4 * e;
          this.stance = 3.2 * e;
          lookOverride = { yaw: -5 * e, pitch: 4 * e };
          this.posing = true;
          break;
        }
        case "cheer": {
          const s = Math.sin(Math.min(1, a.t / a.dur) * Math.PI);
          rArmZ = 150 * s; lArmZ = -150 * s;
          lookOverride = { yaw: 0, pitch: -6 };
          if (a.t > 0.15 && a.t < 0.22) this.jump();
          if (a.t > a.dur) this.start("pose", 1e9), (this.action.t = 1);
          break;
        }
        case "homewave":
          walking = !this.walkTo(0, dt, u, 20);
          if (!walking) this.start("wave", 1.8);
          break;
        case "walk": {
          if (a.target == null && world?.picture) a.target = (Math.random() - 0.5) * u * 22;
          if (a.target == null) {
            const lo = world.spot("punch") + block * 1.6, hi = world.spot("fire") - block * 1.6;
            a.target = lo + Math.random() * (hi - lo);
          }
          walking = !this.walkTo(a.target, dt, u);
          if (!walking) this.start("idle", 2 + Math.random() * 2);
          break;
        }
        case "home":
          walking = !this.walkTo(0, dt, u);
          if (!walking) this.start("idle", 2);
          break;
        case "punch":
        case "mine": {
          const spot = world.spot("punch") + block * 1.3;
          if (!this.walkTo(spot, dt, u)) { walking = true; break; }
          this.facingTarget = -70;
          const cycle = (a.t % 0.42) / 0.42;
          rArmX = 70 + Math.sin(cycle * Math.PI * 2) * 40;
          lookOverride = { yaw: -10, pitch: -22 };
          if (cycle < (a.lastCycle ?? 1)) {
            const broke = world.hitBlock();
            a.hits++;
            if (broke && a.type === "punch") this.start("home", 6);
          }
          a.lastCycle = cycle;
          if (a.type === "punch" && a.hits > 8) this.start("home", 6);
          break;
        }
        case "campfire": {
          const spot = world.spot("fire") - block * 1.6;
          if (!this.walkTo(spot, dt, u)) { walking = true; break; }
          this.facingTarget = 80;
          rArmX = 55 + Math.sin(a.t * 1.5) * 3;
          lArmX = 55 + Math.sin(a.t * 1.5 + 1) * 3;
          lookOverride = { yaw: 0, pitch: -14 };
          if (a.t > a.dur) this.start("home", 6);
          break;
        }
        case "wave":
          rArmZ = 150 + Math.sin(a.t * 12) * 18;
          if (a.t > a.dur) this.start("idle", 2);
          break;
        case "celebrate":
          rArmZ = 150 + Math.sin(a.t * 12) * 18;
          lArmZ = -150 - Math.sin(a.t * 12) * 18;
          if (a.t > 0.2 && a.t < 0.3) this.jump();
          if (a.t > 1.4 && a.t < 1.5) this.jump();
          if (a.t > a.dur) this.start("home", 6);
          break;
        case "look": {
          const crit = world.critters();
          if (crit.length) {
            const head = this.headScreen();
            const c = crit.reduce((best, k) => (Math.hypot(k.x - head.x, k.y - head.y) < Math.hypot(best.x - head.x, best.y - head.y) ? k : best));
            lookOverride = { yaw: clamp((c.x - head.x) / 4, -55, 55), pitch: clamp((head.y - c.y) / 4, -25, 35) };
          } else lookOverride = { yaw: Math.sin(a.t * 1.3) * 40, pitch: 12 };
          if (a.t > a.dur) this.start("idle", 2);
          break;
        }
        default:
          if (a.t > a.dur) this.chooseNext();
      }
    }

    // At night the free right hand holds up a torch.
    this.holdingTorch = !this.preview && world && world.darkness() > 0.5 && !["mine", "punch", "campfire", "wave", "celebrate"].includes(a.type);
    if (this.holdingTorch && !walking && !rArmX) rArmX = 32;

    // Gait from real speed: the stride advances with the distance covered,
    // amplitude grows with speed, and a walk blends into a sprint.
    if (!walking) this.vx = approach(this.vx, 0, 12, dt);
    const speed = Math.abs(this.vx) / u;
    const runK = clamp((speed - 26) / 26, 0, 1);
    const stride = 11 + 9 * runK; // skin pixels per step
    this.walkPhase += (speed * dt / stride) * Math.PI;
    this.gait = approach(this.gait, clamp(speed / 14, 0, 1), 9, dt);
    this.runK = approach(this.runK, runK, 6, dt);
    const g = this.gait;
    legSwing = Math.sin(this.walkPhase) * (30 + 28 * this.runK) * g;
    armSwing = Math.sin(this.walkPhase) * (26 + 40 * this.runK) * g;
    // Lean into speed and into acceleration, back when braking.
    const acc = (this.vx - this.prevVx) / Math.max(dt, 1e-3) / u * Math.sign(this.vx || 1);
    this.prevVx = this.vx;
    lean = lean || clamp(speed * 0.16 + acc * 0.02, -6, 16);
    this.lean = approach(this.lean || 0, lean, 7, dt);
    const breath = Math.sin(performance.now() / (this.posing ? 1300 : 900));
    // Idle: a slow weight shift from one foot to the other.
    const shift = g < 0.05 ? Math.sin(performance.now() / 3400) : 0;
    P.rLegX = approach(P.rLegX, legSwing + shift * 2, 16, dt);
    P.lLegX = approach(P.lLegX, -legSwing - shift * 2, 16, dt);
    P.legZ = approach(P.legZ || 0, (this.stance || 0) * (1 - g) + 1.2 * this.runK * g, 6, dt);
    this.roll = approach(this.roll || 0, shift * 1.1 + Math.sin(this.walkPhase) * 1.6 * g, 8, dt);
    this.twist = Math.sin(this.walkPhase) * (5 + 4 * this.runK) * g;
    P.rArmX = approach(P.rArmX, rArmX || -armSwing, 16, dt);
    P.lArmX = approach(P.lArmX, lArmX || armSwing, 16, dt);
    P.rArmZ = approach(P.rArmZ, rArmZ || 3 + breath * 2, 10, dt);
    P.lArmZ = approach(P.lArmZ, lArmZ || -3 - breath * 2, 10, dt);

    // Facing turns smoothly.
    if (!this.preview) this.facing = approach(this.facing, this.facingTarget, 6, dt);

    // Head: cursor first, else action, else gentle idle.
    const head = this.headScreen();
    let ty = Math.sin(performance.now() / 2300) * 12, tp = Math.sin(performance.now() / 3100) * 4;
    const recentMouse = !this.posing && performance.now() - this.mouse.at < 2500;
    if (lookOverride) { ty = lookOverride.yaw; tp = lookOverride.pitch; }
    else if (recentMouse) {
      ty = clamp((this.mouse.x - head.x) / 5, -60, 60) - (this.facing - 16) * 0.5;
      tp = clamp((head.y - this.mouse.y) / 6, -30, 35);
    }
    // The head turns no further than a person's would, and eases there.
    this.yaw = approach(this.yaw, clamp(ty, -42, 42), 5, dt);
    this.pitch = approach(this.pitch, clamp(tp, -25, 30), 5, dt);

    // Gravity for jumps.
    if (this.y < 0 || this.vy < 0) {
      this.vy += 90 * dt;
      this.y += this.vy * dt * u;
      if (this.y > 0) { this.y = 0; this.vy = 0; }
    }

    this.walking = this.gait > 0.05;
  }

  // -------------------------------------------------------------- render --

  render() {
    const u = this.unit;
    const P = this.pose;
    const set = (name, rx = 0, ry = 0, rz = 0) => {
      const { el, p } = this.parts[name];
      el.style.transform = `translate3d(${p.pivot[0] * u}px,${p.pivot[1] * u}px,${p.pivot[2] * u}px) rotateY(${ry}deg) rotateX(${rx}deg) rotateZ(${rz}deg)`;
    };
    const now = performance.now();
    // Highest when the legs pass each other, lowest at full stride: twice a cycle.
    const g = this.gait || 0;
    const stepBob = -(0.5 + 0.5 * Math.cos(2 * this.walkPhase)) * (0.45 + 0.35 * (this.runK || 0)) * u * g;
    const idleBob = this.panting ? Math.abs(Math.sin(now / 160)) * 0.35 * u : Math.sin(now / 1100) * 0.12 * u;
    const bob = stepBob + idleBob * (1 - g);
    set("body");
    // The head stays steady while the shoulders twist.
    set("head", this.pitch, this.yaw - (this.twist || 0) * 0.8, 0);
    set("rarm", P.rArmX, 0, P.rArmZ);
    set("larm", P.lArmX, 0, P.lArmZ);
    set("rleg", P.rLegX, 0, P.legZ || 0);
    set("lleg", P.lLegX, 0, -(P.legZ || 0));
    if (this.capeEl && this.capeUrl) {
      const swing = 6 + (this.gait || 0) * (14 + 12 * (this.runK || 0) + Math.sin(this.walkPhase * 2) * 4) + Math.sin(performance.now() / 700) * 2 * (1 - (this.gait || 0)) + Math.min(20, Math.max(0, -this.vy * 0.6));
      this.capeEl.style.transform = `translate3d(0px,${-24 * u}px,${-2.6 * u}px) rotateY(180deg) rotateX(${swing}deg)`;
    }
    this.root.style.transform = `translate3d(${this.x}px,${this.y + bob}px,0) rotateX(${-7 - (this.lean || 0) * Math.cos(this.facing * DEG)}deg) rotateZ(${(this.lean || 0) * Math.sin(this.facing * DEG) * -0.6 + (this.roll || 0)}deg) rotateY(${this.facing + (this.twist || 0)}deg)`;

    if (!this.preview && this.world) {
      const a = this.world.anchor();
      this.stage.style.left = `${a.x}px`;
      this.stage.style.top = `${a.y}px`;
      const lift = Math.min(1, -this.y / (6 * u));
      this.shadow.style.transform = `translateX(${this.x}px) scale(${1 - lift * 0.4})`;
      // The name sits behind him and drifts a little against the pointer,
      // so it reads as a layer further back.
      const head = this.root.getBoundingClientRect();
      const px = clamp((this.mouse.x - (head.left + head.width / 2)) / 40, -8, 8);
      const py = clamp((this.mouse.y - head.top) / 60, -4, 4);
      this.tagX = approach(this.tagX || 0, -px, 4, 1 / 60);
      this.tagY = approach(this.tagY || 0, -py, 4, 1 / 60);
      this.tag.style.transform = `translate(calc(-50% + ${(this.x * 0.92 + this.tagX).toFixed(1)}px), ${(this.y * 0.5 + this.tagY).toFixed(1)}px)`;
      if (this.blockName) {
        if (!this.accentAt || now - this.accentAt > 1000) {
          this.accentAt = now;
          this.accentHex = getComputedStyle(document.documentElement).getPropertyValue("--accent").trim() || "#ffb547";
        }
        const cols = Math.max(1, (this.displayName || "").replace(/[^A-Za-z0-9_]/g, "").slice(0, 16).length * 6 - 1);
        const cell = Math.round(Math.min(u * 1.4, (u * 40) / cols) * 10) / 10;
        this.blockName.set(this.displayName, cell, this.accentHex);
        const h = 7 * cell;
        // Centred on him, straight on, set behind his head: the head covers the
        // lower middle of the name, so it reads as standing at his back.
        this.blockName.update(now, this.x, -(u * 31.5 + h / 2 - h * 0.35) + this.y + bob, 0, true);
      }
      // Torch at night, in the right hand (the viewer's left when facing us).
      const showTorch = !!this.holdingTorch;
      this.torchEl.hidden = !showTorch;
      if (showTorch) {
        const face = Math.cos(this.facing * DEG);
        const hx = this.x - 6 * u * face;
        const hy = -11 * u + this.y + bob;
        this.torchEl.style.transform = `translate(${hx}px, ${hy}px)`;
        this.world.setTorch(a.x + hx, a.y + hy - 6 * u, true);
      } else this.world.setTorch(0, 0, false);
    }
  }

  loop(now) {
    const dt = Math.min(0.05, (now - this.last) / 1000);
    this.last = now;
    if (!document.hidden && this.stage.isConnected) {
      if (this.currentUnit() !== this.unit) this.build();
      this.update(dt);
      this.render();
    }
    if (this.stage.isConnected) requestAnimationFrame((t) => this.loop(t));
  }
}
