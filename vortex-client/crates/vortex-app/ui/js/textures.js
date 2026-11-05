// 16×16 block and plant textures, drawn procedurally (no image files).

export function hash(x, y, s = 0) {
  let h = (x * 374761393 + y * 668265263 + s * 2147483647) ^ 0x5bd1e995;
  h = Math.imul(h ^ (h >>> 13), 1274126177);
  return ((h ^ (h >>> 16)) >>> 0) / 4294967296;
}

export function rng(seed) {
  let s = seed >>> 0;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}

const lerp = (a, b, t) => a + (b - a) * t;
export const mix = (a, b, t) => [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)];

function texture(draw, size = 16) {
  const c = document.createElement("canvas");
  c.width = c.height = size;
  const g = c.getContext("2d");
  const img = g.createImageData(size, size);
  const put = (x, y, col, a = 255) => {
    if (x < 0 || y < 0 || x >= size || y >= size) return;
    const i = (y * size + x) * 4;
    img.data[i] = col[0]; img.data[i + 1] = col[1]; img.data[i + 2] = col[2]; img.data[i + 3] = a;
  };
  draw(put);
  g.putImageData(img, 0, 0);
  return c;
}

const pick = (list, h) => (h < 0.5 ? list[0] : list[1 + (Math.floor(((h - 0.5) / 0.5) * (list.length - 1)) % (list.length - 1))]);

export const PAL = {
  dirt: [[134, 96, 67], [121, 85, 58], [150, 109, 76], [101, 70, 47]],
  grass: [[104, 168, 50], [92, 152, 42], [118, 184, 62], [84, 140, 38]],
  jungleGrass: [[72, 160, 44], [60, 140, 36], [88, 178, 56], [50, 120, 30]],
  stone: [[124, 124, 126], [112, 112, 115], [137, 137, 139], [99, 99, 103]],
  deep: [[78, 78, 86], [68, 68, 76], [90, 90, 98], [60, 60, 68]],
  sand: [[222, 202, 146], [210, 188, 132], [232, 214, 162], [198, 174, 120]],
  sandstone: [[204, 176, 116], [190, 160, 102], [216, 190, 130], [172, 142, 88]],
  snow: [[246, 250, 255], [234, 242, 252], [255, 255, 255], [218, 230, 246]],
  ice: [[150, 190, 248], [136, 178, 240], [170, 206, 255], [120, 160, 228]],
  oakLeaf: [[72, 138, 44], [58, 118, 36], [92, 160, 56], [42, 96, 30]],
  birchLeaf: [[110, 156, 62], [94, 140, 52], [130, 174, 76], [74, 116, 40]],
  spruceLeaf: [[44, 92, 54], [36, 78, 46], [56, 108, 64], [28, 62, 38]],
  jungleLeaf: [[48, 150, 34], [38, 128, 26], [66, 172, 46], [28, 104, 20]],
  cherryLeaf: [[246, 182, 210], [236, 160, 196], [255, 206, 226], [220, 136, 178]],
  azaleaLeaf: [[96, 146, 46], [82, 128, 38], [112, 164, 58], [64, 106, 30]],
  oakLog: [[110, 85, 52], [88, 67, 39], [128, 99, 61]],
  spruceLog: [[74, 52, 30], [58, 40, 22], [90, 66, 40]],
  jungleLog: [[100, 82, 40], [80, 64, 30], [120, 98, 52]],
  cherryLog: [[66, 36, 44], [52, 26, 34], [82, 48, 56]],
  birch: [[218, 216, 208], [198, 196, 186], [234, 233, 226]],
  netherrack: [[112, 46, 46], [98, 38, 40], [128, 56, 54], [82, 30, 32]],
  crimson: [[156, 32, 36], [134, 24, 30], [178, 46, 46], [112, 18, 24]],
  warped: [[24, 132, 126], [18, 110, 106], [36, 156, 146], [12, 90, 88]],
  stemC: [[122, 50, 76], [100, 38, 62], [140, 62, 90]],
  stemW: [[56, 92, 98], [44, 74, 80], [70, 110, 116]],
  basalt: [[74, 74, 80], [62, 62, 68], [88, 88, 94], [52, 52, 58]],
  endstone: [[222, 226, 168], [210, 214, 154], [232, 236, 182], [196, 200, 140]],
  obsidian: [[22, 16, 34], [16, 12, 26], [34, 24, 52], [44, 30, 70]],
  chorus: [[110, 76, 112], [92, 62, 96], [132, 94, 134], [76, 50, 80]],
  moss: [[90, 116, 46], [78, 102, 38], [104, 130, 56], [66, 88, 32]],
  clay: [[152, 156, 172], [140, 144, 160], [164, 168, 184], [128, 132, 148]],
  planks: [[162, 130, 78], [146, 116, 68], [176, 142, 88], [124, 98, 56]],
  darkPlanks: [[94, 66, 36], [80, 56, 30], [108, 78, 44], [66, 46, 24]],
  cobble: [[122, 122, 122], [100, 100, 100], [140, 140, 140], [84, 84, 84]],
  path: [[148, 122, 66], [134, 110, 58], [162, 136, 78], [120, 98, 50]],
  cactus: [[86, 140, 44], [72, 122, 36], [100, 158, 54], [56, 100, 28]],
};

function noise(list, s, blot = 0.86, step = 1) {
  return texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = pick(list, hash(Math.floor(x / step), y, s));
      if (hash(x >> 1, y >> 1, s + 9) > blot) c = list[3] || list[1];
      put(x, y, c);
    }
  });
}

/** A block with a coloured top band over another texture (grass, snow, nylium, moss). */
function capped(top, below, s, depth = 3, highlight = [190, 236, 110]) {
  return texture((put) => {
    for (let x = 0; x < 16; x++) {
      const d = depth + (hash(x, 0, s) > 0.55 ? 1 : 0) + (hash(x, 1, s) > 0.82 ? 2 : 0);
      for (let y = 0; y < 16; y++) {
        let c;
        if (y < d) {
          c = pick(top, hash(x, y, s + 3));
          if (y === 0) c = mix(c, highlight, 0.4);
        } else {
          c = pick(below, hash(x, y, s));
          if (hash(x >> 1, y >> 1, s + 9) > 0.86) c = below[3] || below[1];
        }
        put(x, y, c);
      }
    }
  });
}

function log(list, s) {
  return texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = list[0];
      if (x % 4 === 0 || hash(x, y >> 2, s) > 0.8) c = list[1];
      else if (hash(x, y, s + 1) > 0.82) c = list[2];
      put(x, y, c);
    }
  });
}

function leaves(list, s, flowers = null) {
  return texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = pick(list, hash(x, y, s));
      if (hash(x >> 1, y >> 1, s + 3) > 0.75) c = list[3];
      if (flowers && hash(x, y, s + 8) > 0.9) c = flowers;
      put(x, y, c);
    }
  });
}

function planks(list, s) {
  return texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = pick(list, hash(x, y >> 2, s) * 0.6 + hash(x, y, s + 1) * 0.4);
      if (y % 4 === 3) c = list[3];
      if (y % 4 !== 3 && x === ((y >> 2) % 2 ? 4 : 11)) c = list[3];
      put(x, y, c);
    }
  });
}

function bricks(list, s) {
  return texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const row = y >> 2;
      const edge = y % 4 === 3 || (x + (row % 2) * 4) % 8 === 0;
      put(x, y, edge ? list[3] : pick(list, hash(x, y, s)));
    }
  });
}

function sprite(draw) {
  return texture((put) => draw(put));
}

export function makeTextures() {
  const T = {};
  T.dirt = [1, 2, 3].map((s) => noise(PAL.dirt, s));
  T.grass = [1, 2, 3].map((s) => capped(PAL.grass, PAL.dirt, s));
  T.jungleGrass = [1, 2, 3].map((s) => capped(PAL.jungleGrass, PAL.dirt, s));
  T.snowGrass = [1, 2, 3].map((s) => capped(PAL.snow, PAL.dirt, s, 4, [255, 255, 255]));
  T.podzol = [1, 2].map((s) => capped([[110, 76, 34], [96, 64, 28], [124, 88, 42], [84, 56, 24]], PAL.dirt, s, 3, [150, 110, 60]));
  T.stone = [1, 2].map((s) => noise(PAL.stone, s, 0.93, 2));
  T.deep = [3, 4].map((s) => noise(PAL.deep, s, 0.93, 2));
  T.sand = [1, 2].map((s) => noise(PAL.sand, s, 0.95));
  T.sandstone = [1, 2].map((s) => texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = pick(PAL.sandstone, hash(x, y, s));
      if (y === 3 || y === 11) c = PAL.sandstone[3];
      put(x, y, c);
    }
  }));
  T.snow = [1, 2].map((s) => noise(PAL.snow, s, 0.97));
  T.ice = [1].map((s) => texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(x, y, (x + y) % 9 === 0 ? [214, 232, 255] : pick(PAL.ice, hash(x, y, s)));
  }));
  T.coal = noise(PAL.stone, 5, 0.93, 2);
  T.netherrack = [1, 2, 3].map((s) => noise(PAL.netherrack, s, 0.8));
  T.nylium = [1, 2].map((s) => capped(PAL.crimson, PAL.netherrack, s, 4, [220, 80, 80]));
  T.warpedNylium = [1, 2].map((s) => capped(PAL.warped, PAL.netherrack, s, 4, [80, 220, 200]));
  T.basalt = [1, 2].map((s) => texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(x, y, x % 5 === 0 ? PAL.basalt[3] : pick(PAL.basalt, hash(x, y >> 2, s)));
  }));
  T.endstone = [1, 2, 3].map((s) => noise(PAL.endstone, s, 0.9));
  T.obsidian = [1, 2].map((s) => noise(PAL.obsidian, s, 0.88));
  T.moss = [1, 2].map((s) => noise(PAL.moss, s, 0.85));
  T.mossCap = [1, 2].map((s) => capped(PAL.moss, PAL.stone, s, 5, [140, 170, 70]));
  T.clay = [1].map((s) => noise(PAL.clay, s, 0.95));
  T.path = [1, 2].map((s) => capped(PAL.path, PAL.dirt, s, 3, [176, 150, 90]));
  T.cobble = [1, 2].map((s) => bricks(PAL.cobble, s));
  T.planks = planks(PAL.planks, 1);
  T.darkPlanks = planks(PAL.darkPlanks, 2);
  T.oakLog = log(PAL.oakLog, 3);
  T.spruceLog = log(PAL.spruceLog, 4);
  T.jungleLog = log(PAL.jungleLog, 5);
  T.cherryLog = log(PAL.cherryLog, 6);
  T.stemC = log(PAL.stemC, 7);
  T.stemW = log(PAL.stemW, 8);
  T.birch = texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = pick(PAL.birch, hash(x, y, 4));
      if (hash(x >> 2, y >> 1, 6) > 0.84) c = [48, 46, 42];
      put(x, y, c);
    }
  });
  T.oakLeaf = leaves(PAL.oakLeaf, 8);
  T.birchLeaf = leaves(PAL.birchLeaf, 9);
  T.spruceLeaf = leaves(PAL.spruceLeaf, 10);
  T.snowLeaf = texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = pick(PAL.spruceLeaf, hash(x, y, 11));
      if (y < 4 + (hash(x, 0, 12) > 0.5 ? 1 : 0)) c = pick(PAL.snow, hash(x, y, 13));
      put(x, y, c);
    }
  });
  T.jungleLeaf = leaves(PAL.jungleLeaf, 14, [210, 150, 60]);
  T.cherryLeaf = leaves(PAL.cherryLeaf, 15, [255, 236, 244]);
  T.azaleaLeaf = leaves(PAL.azaleaLeaf, 16, [226, 120, 196]);
  T.wart = leaves(PAL.crimson, 17);
  T.warpedWart = leaves(PAL.warped, 18);
  T.shroom = texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(x, y, (x % 5 === 0 || y % 5 === 0) ? [214, 120, 50] : pick([[255, 186, 86], [250, 170, 70], [255, 206, 120], [236, 150, 60]], hash(x, y, 19)));
  });
  T.chorus = leaves(PAL.chorus, 20);
  T.chorusFlower = texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) put(x, y, x < 2 || x > 13 || y < 2 || y > 13 ? [140, 100, 150] : pick([[200, 160, 210], [186, 144, 196], [220, 186, 228], [166, 124, 178]], hash(x, y, 21)));
  });
  T.cactus = texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = pick(PAL.cactus, hash(x, y, 22));
      if (x === 1 || x === 14) c = PAL.cactus[3];
      if ((x === 4 || x === 11) && y % 4 === 1) c = [40, 40, 30];
      put(x, y, c);
    }
  });
  T.nest = texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = [206, 164, 82];
      if (y % 5 === 0) c = [174, 132, 60];
      if (hash(x, y, 77) > 0.85) c = [222, 184, 100];
      if (y >= 7 && y <= 9 && x >= 5 && x <= 10) c = y === 8 && x > 5 && x < 10 ? [52, 34, 18] : [120, 86, 40];
      put(x, y, c);
    }
  });
  T.glass = texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const frame = x === 0 || y === 0 || x === 15 || y === 15;
      put(x, y, frame ? [200, 220, 230] : [150, 200, 230], frame ? 255 : 120);
      if (!frame && (x - y === 4 || x - y === 6)) put(x, y, [235, 250, 255], 200);
    }
  });
  T.door = texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      let c = pick(PAL.darkPlanks, hash(x, y, 31));
      if (x === 0 || x === 15) c = PAL.darkPlanks[3];
      if (y > 3 && y < 7 && x > 3 && x < 12) c = [60, 44, 24];
      put(x, y, c);
    }
  });

  // "Clean" poster-style blocks: flat faces, a few square speckles, big pixels.
  const clean = (base, specks, s, cell = 2) => texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const cx = Math.floor(x / cell), cy = Math.floor(y / cell);
      const h = hash(cx, cy, s);
      let c = base;
      if (h > 0.9) c = specks[0];
      else if (h > 0.82) c = specks[1];
      put(x, y, c);
    }
  });
  const cleanDirt = [[183, 120, 63], [[143, 90, 44], [205, 142, 80]]];
  T.cleanDirt = [11, 12, 13].map((s) => clean(cleanDirt[0], cleanDirt[1], s));
  T.cleanGrass = [11, 12, 13].map((s) => texture((put) => {
    const dirt = clean(cleanDirt[0], cleanDirt[1], s).getContext("2d").getImageData(0, 0, 16, 16).data;
    for (let x = 0; x < 16; x++) {
      const col = Math.floor(x / 2);
      const drip = 4 + (hash(col, 0, s) > 0.6 ? 2 : 0) + (hash(col, 1, s) > 0.85 ? 2 : 0);
      for (let y = 0; y < 16; y++) {
        if (y < drip) put(x, y, y < 2 ? [126, 214, 86] : hash(col, Math.floor(y / 2), s + 4) > 0.85 ? [80, 168, 48] : [96, 190, 62]);
        else {
          const i = (y * 16 + x) * 4;
          put(x, y, y === drip ? [120, 78, 36] : [dirt[i], dirt[i + 1], dirt[i + 2]]);
        }
      }
    }
  }));
  T.cleanStone = [11, 12].map((s) => texture((put) => {
    for (let y = 0; y < 16; y++) for (let x = 0; x < 16; x++) {
      const bx = Math.floor((x + (Math.floor(y / 4) % 2) * 4) / 8), by = Math.floor(y / 4);
      const h = hash(bx, by, s);
      let c = h > 0.66 ? [158, 159, 164] : h > 0.33 ? [143, 144, 149] : [128, 129, 134];
      if (y % 4 === 3) c = [110, 111, 116];
      if (hash(Math.floor(x / 2), Math.floor(y / 2), s + 2) > 0.93) c = [96, 97, 102];
      put(x, y, c);
    }
  }));
  T.cleanSand = [11].map((s) => clean([232, 214, 160], [[214, 194, 140], [244, 230, 184]], s));
  // Terracotta in the colours of a badlands cliff.
  T.terracotta = [[180, 92, 52], [196, 120, 74], [214, 160, 104], [152, 74, 44], [222, 186, 132], [168, 104, 72]].map((c, i) =>
    clean(c, [mix(c, [0, 0, 0], 0.14), mix(c, [255, 255, 255], 0.12)], 30 + i, 4));

  // Plants (transparent sprites).
  const flower = (petal, center, s, kind) => sprite((put) => {
    const stem = [62, 132, 40];
    for (let y = 8; y < 16; y++) put(7, y, stem);
    put(6, 12, stem); put(5, 11, stem); put(8, 13, stem); put(9, 12, stem);
    if (kind === "tall") {
      for (let y = 3; y < 9; y++) put(7, y, petal);
      put(6, 4, petal); put(8, 6, petal); put(7, 2, mix(petal, [255, 255, 255], 0.3));
      return;
    }
    const pts = [[6, 5], [7, 5], [8, 5], [6, 6], [8, 6], [6, 7], [7, 7], [8, 7], [7, 4], [5, 6], [9, 6], [7, 8]];
    for (const [x, y] of pts) put(x, y, hash(x, y, s) > 0.7 ? mix(petal, [0, 0, 0], 0.15) : petal);
    put(7, 6, center);
  });
  T.poppy = flower([214, 40, 36], [40, 30, 20], 1);
  T.dandelion = flower([250, 214, 44], [200, 150, 20], 2);
  T.cornflower = flower([84, 120, 230], [40, 60, 160], 3);
  T.daisy = flower([246, 246, 240], [240, 196, 40], 4);
  T.allium = flower([190, 110, 230], [150, 70, 190], 5, "tall");
  T.tulip = flower([255, 140, 170], [200, 80, 110], 6, "tall");
  const tuft = (s, cols) => sprite((put) => {
    const r = rng(s * 31 + 7);
    for (let b = 0; b < 6; b++) {
      const x0 = 2 + Math.floor(r() * 12), h = 5 + Math.floor(r() * 9);
      const lean = r() > 0.5 ? 1 : -1;
      for (let i = 0; i < h; i++) put(x0 + (i > h * 0.6 ? lean : 0), 15 - i, mix(cols[r() > 0.5 ? 0 : 3], [200, 240, 120], (i / h) * 0.35));
    }
  });
  T.tufts = [1, 2, 3].map((s) => tuft(s, PAL.grass));
  T.ferns = [4, 5].map((s) => tuft(s, PAL.jungleGrass));
  T.roots = [6, 7].map((s) => tuft(s, PAL.crimson));
  T.sprouts = [8, 9].map((s) => tuft(s, PAL.warped));
  T.deadBush = sprite((put) => {
    const c = [134, 96, 52];
    for (let i = 0; i < 9; i++) { put(7, 15 - i, c); if (i > 3) { put(7 - (i - 3), 15 - i - 1, c); put(8 + (i - 4), 15 - i, c); } }
  });
  T.petals = sprite((put) => {
    for (let i = 0; i < 14; i++) put(Math.floor(hash(i, 1, 40) * 16), 13 + Math.floor(hash(i, 2, 40) * 3), hash(i, 3, 40) > 0.5 ? [250, 190, 214] : [236, 150, 190]);
  });
  T.dripleaf = sprite((put) => {
    for (let y = 6; y < 16; y++) put(8, y, [70, 130, 40]);
    for (let x = 2; x < 14; x++) for (let y = 3; y < 6; y++) put(x, y, y === 3 ? [130, 190, 70] : [96, 160, 50]);
  });
  T.mushroom = sprite((put) => {
    for (let y = 10; y < 16; y++) put(7, y, [230, 220, 200]), put(8, y, [210, 200, 180]);
    for (let x = 4; x < 12; x++) for (let y = 6; y < 10; y++) put(x, y, hash(x, y, 50) > 0.8 ? [255, 255, 255] : [200, 40, 40]);
  });
  return T;
}
