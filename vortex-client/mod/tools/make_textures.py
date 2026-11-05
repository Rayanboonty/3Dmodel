"""Draws the Vortex mod icon and capes (pixel art). Run from the repo root:
python3 mod/tools/make_textures.py
Writes into the mod's assets and the launcher UI (for the 3D preview)."""
import random
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
MOD = ROOT / "mod/src/main/resources/assets/vortex"
UI = ROOT / "crates/vortex-app/ui/img/capes"

def hexc(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)

def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3],)

# 10×16 cape faces. '.' = base, others map per design.
V_MARK = [
    "..........",
    "..........",
    ".X......X.",
    ".XX....XX.",
    "..X....X..",
    "..XX..XX..",
    "...X..X...",
    "...XXXX...",
    "....XX....",
    "..........",
    "..........",
    "..........",
    "..........",
    "..........",
    "..........",
    "..........",
]

def face(design, rnd):
    px = [[None] * 10 for _ in range(16)]
    for y in range(16):
        for x in range(10):
            px[y][x] = design(x, y, rnd)
    return px

def vortex(x, y, rnd):
    base = shade(hexc("#141a2e"), 1 + (y / 16) * 0.35 + rnd.uniform(-0.05, 0.05))
    if V_MARK[y][x] == "X":
        return shade(hexc("#ffb547"), 1.12 if x < 5 else 0.92)
    if y >= 13:
        return shade(hexc("#ffb547"), 0.55 + 0.1 * ((x + y) % 2))
    return base

def diamond(x, y, rnd):
    c = hexc("#3fd0e0")
    f = 1 + rnd.uniform(-0.12, 0.12)
    if (x + y) % 5 == 0:
        f += 0.25
    if V_MARK[y][x] == "X":
        return hexc("#f4feff")
    if x in (0, 9) or y in (0, 15):
        f -= 0.3
    return shade(c, f)

def ember(x, y, rnd):
    base = shade(hexc("#2b2328"), 1 + rnd.uniform(-0.1, 0.1))
    crack = (x * 3 + y * 7) % 11 == 0 or (x - y // 2) % 7 == 0 and y > 4
    if crack:
        return shade(hexc("#ff7a1a"), 1 + rnd.uniform(-0.1, 0.25))
    if y > 12 and rnd.random() < 0.35:
        return hexc("#ffb23d")
    return base

def emerald(x, y, rnd):
    t = y / 15
    c = hexc("#1f9d4f")
    f = 1.15 - t * 0.45 + rnd.uniform(-0.06, 0.06)
    gem = abs(x - 4.5) + abs(y - 6) <= 3.2
    if gem:
        return shade(hexc("#7dffa6"), 1.0 if x < 5 else 0.8)
    return shade(c, f)

def amethyst(x, y, rnd):
    c = hexc("#6a3fb8")
    f = 0.9 + rnd.uniform(-0.08, 0.08)
    shard = (y > 9 and abs(x - 2) < (16 - y) / 3) or (y > 7 and abs(x - 7) < (16 - y) / 4)
    if shard:
        return shade(hexc("#d6b4ff"), 1.0 if (x + y) % 2 else 0.85)
    return shade(c, f + y * 0.012)

DESIGNS = {"vortex": vortex, "diamond": diamond, "ember": ember, "emerald": emerald, "amethyst": amethyst}

def cape(name, design):
    rnd = random.Random(name)
    img = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    f = face(design, rnd)
    for y in range(16):
        for x in range(10):
            img.putpixel((1 + x, 1 + y), f[y][x])          # outside (seen from behind)
            img.putpixel((12 + (9 - x), 1 + y), shade(f[y][x], 0.8))  # inside
    edge = shade(f[8][0], 0.7)
    for y in range(16):
        img.putpixel((0, 1 + y), edge)
        img.putpixel((11, 1 + y), edge)
    for x in range(10):
        img.putpixel((1 + x, 0), shade(f[0][x], 0.9))
        img.putpixel((11 + x, 0), shade(f[15][x], 0.7))
    return img

def icon():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    v = [
        "................",
        ".KKKK......KKKK.",
        ".KAAK......KAAK.",
        ".KAAKK....KKAAK.",
        ".KKAAK....KAAKK.",
        "..KAAKK..KKAAK..",
        "..KKAAK..KAAKK..",
        "...KAAKKKKAAK...",
        "...KKAAKKAAKK...",
        "....KAAAAAAK....",
        "....KKAAAAKK....",
        ".....KAAAAK.....",
        ".....KKAAKK.....",
        "......KKKK......",
        "................",
        "................",
    ]
    for y, row in enumerate(v):
        for x, ch in enumerate(row):
            if ch == "K":
                img.putpixel((x, y), hexc("#1a1208"))
            elif ch == "A":
                img.putpixel((x, y), shade(hexc("#ffb547"), 1.15 - y * 0.03))
    return img.resize((128, 128), Image.NEAREST)

if __name__ == "__main__":
    (MOD / "textures/cape").mkdir(parents=True, exist_ok=True)
    UI.mkdir(parents=True, exist_ok=True)
    for name, d in DESIGNS.items():
        im = cape(name, d)
        im.save(MOD / f"textures/cape/{name}.png")
        im.save(UI / f"{name}.png")
    icon().save(MOD / "icon.png")
    print("ok")
