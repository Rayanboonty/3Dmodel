// The player's name built from 3D blocks, standing behind him and turning
// slowly, like a block sign at his back.

const FONT = {
  A: [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
  B: ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
  C: [".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."],
  D: ["####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."],
  E: ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
  F: ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
  G: [".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###."],
  H: ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
  I: ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"],
  J: ["..###", "...#.", "...#.", "...#.", "#..#.", "#..#.", ".##.."],
  K: ["#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"],
  L: ["#....", "#....", "#....", "#....", "#....", "#....", "#####"],
  M: ["#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"],
  N: ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
  O: [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
  P: ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
  Q: [".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"],
  R: ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
  S: [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
  T: ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
  U: ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
  V: ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
  W: ["#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"],
  X: ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
  Y: ["#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."],
  Z: ["#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"],
  0: [".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."],
  1: ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."],
  2: [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
  3: ["####.", "....#", "....#", ".###.", "....#", "....#", "####."],
  4: ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."],
  5: ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
  6: [".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."],
  7: ["#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."],
  8: [".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."],
  9: [".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."],
  _: [".....", ".....", ".....", ".....", ".....", ".....", "#####"],
};

/** Rows of the whole name as strings of '#' and '.' (1 cell gap between letters). */
function bitmap(name) {
  const rows = Array.from({ length: 7 }, () => "");
  [...name.toUpperCase()].forEach((ch, i) => {
    const g = FONT[ch] || FONT._;
    for (let r = 0; r < 7; r++) rows[r] += (i ? "." : "") + g[r];
  });
  return rows;
}

/** Horizontal runs of filled cells: [{x, y, w}], so a row of blocks is one box. */
function runs(rows) {
  const out = [];
  rows.forEach((row, y) => {
    let x = 0;
    while (x < row.length) {
      if (row[x] !== "#") { x++; continue; }
      let w = 1;
      while (row[x + w] === "#") w++;
      out.push({ x, y, w });
      x += w;
    }
  });
  return out;
}

const textures = new Map();
/** A small pixel texture for the block faces (accent-coloured, like a gold block). */
function texture(hex, shade) {
  const key = `${hex}:${shade}`;
  if (textures.has(key)) return textures.get(key);
  const n = parseInt(hex.replace("#", ""), 16);
  const base = [(n >> 16) & 255, (n >> 8) & 255, n & 255];
  const c = document.createElement("canvas");
  c.width = c.height = 8;
  const g = c.getContext("2d");
  let seed = 7;
  const rnd = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
  for (let y = 0; y < 8; y++) {
    for (let x = 0; x < 8; x++) {
      const edge = x === 0 || y === 0 ? 1.2 : x === 7 || y === 7 ? 0.55 : 1;
      const k = shade * edge * (0.88 + rnd() * 0.24);
      g.fillStyle = `rgb(${base.map((v) => Math.max(0, Math.min(255, Math.round(v * k)))).join(",")})`;
      g.fillRect(x, y, 1, 1);
    }
  }
  const url = c.toDataURL();
  textures.set(key, url);
  return url;
}

export class BlockName {
  constructor(host) {
    this.el = document.createElement("div");
    this.el.className = "pl-blockname";
    this.el.setAttribute("aria-hidden", "true");
    this.rot = document.createElement("div");
    this.rot.className = "bn-rot";
    this.el.appendChild(this.rot);
    host.prepend(this.el);
    this.name = "";
    this.cell = 0;
    this.accent = "";
    this.yaw = 0;
  }

  /** Build (or rebuild) the blocks for a name at a cell size in px. */
  set(name, cell, accent) {
    name = (name || "").replace(/[^A-Za-z0-9_]/g, "").slice(0, 16);
    if (name === this.name && Math.abs(cell - this.cell) < 0.5 && accent === this.accent) return;
    this.name = name;
    this.cell = cell;
    this.accent = accent;
    this.rot.innerHTML = "";
    this.el.hidden = !name;
    if (!name) return;
    const rows = bitmap(name);
    const cols = rows[0].length;
    const W = cols * cell, H = 7 * cell, D = cell * 2.2; // deep blocks, so the depth reads straight on
    this.width = W;
    this.height = H;
    const front = texture(accent, 1), side = texture(accent, 0.62), top = texture(accent, 1.22), bottom = texture(accent, 0.45);
    const face = (w, h, tf, tex) => {
      const f = document.createElement("div");
      f.className = "bn-face";
      f.style.cssText = `width:${w}px;height:${h}px;margin:${-h / 2}px 0 0 ${-w / 2}px;transform:${tf};background-image:url(${tex});background-size:${cell}px ${cell}px`;
      return f;
    };
    for (const r of runs(rows)) {
      const bw = r.w * cell;
      const box = document.createElement("div");
      box.className = "bn-box";
      box.style.transform = `translate3d(${(r.x * cell + bw / 2 - W / 2).toFixed(1)}px,${(r.y * cell + cell / 2 - H / 2).toFixed(1)}px,0)`;
      box.append(
        face(bw, H / 7, `translateZ(${D / 2}px)`, front),
        face(bw, D, `rotateX(90deg) translateZ(${cell / 2}px)`, top),
        face(bw, D, `rotateX(-90deg) translateZ(${cell / 2}px)`, bottom),
        face(D, cell, `rotateY(90deg) translateZ(${bw / 2}px)`, side),
        face(D, cell, `rotateY(-90deg) translateZ(${bw / 2}px)`, side),
      );
      this.rot.appendChild(box);
    }
  }

  /** Turn slowly back and forth, leaning a little toward the pointer. */
  update(now, x, y, pointer = 0, still = false) {
    if (still) {
      // A fixed three-quarter angle: the block sides and tops show, nothing moves.
      this.el.style.transform = `translate3d(${x.toFixed(1)}px,${y.toFixed(1)}px,0)`;
      // Straight on, no turn: the block depth shows through perspective.
      this.rot.style.transform = `translateZ(${(-this.cell * 2).toFixed(1)}px) rotateX(-12deg)`;
      return;
    }
    const target = Math.sin(now / 2600) * 24 + pointer * 10;
    this.yaw += (target - this.yaw) * 0.08;
    const bob = Math.sin(now / 1800) * this.cell * 0.35;
    this.el.style.transform = `translate3d(${x.toFixed(1)}px,${(y + bob).toFixed(1)}px,0)`;
    this.rot.style.transform = `rotateX(-10deg) rotateY(${this.yaw.toFixed(2)}deg)`;
  }
}
