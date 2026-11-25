"""Continent pictures for the country picker and the website, drawn from the real world map.

Data: Natural Earth 1:110m countries (public domain), ne_110m_admin_0_countries.geojson.
Run once:  python3 -I tools/cmcpvp-art/continents.py <countries.geojson>
Writes practicepvp/src/main/resources/continents/{eu,af,as,na,sa,oc}.png (64 px, item icons in the pack)
and practicepvp/src/main/resources/continents/web/*.png (256 px, the website).
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'practicepvp/src/main/resources/continents')

# code, Natural Earth continent, longitude/latitude window (drops far-away islands and overseas parts)
CONTINENTS = [
    ('eu', 'Europe', (-25, 50, 34, 72)),
    ('af', 'Africa', (-26, 60, -36, 38)),
    ('as', 'Asia', (26, 180, -11, 78)),
    ('na', 'North America', (-170, -50, 7, 84)),
    ('sa', 'South America', (-82, -34, -56, 13)),
    ('oc', 'Oceania', (110, 180, -48, 0)),
]
# Russia is "Europe" in the data: its part east of the Urals belongs to the Asia picture
EXTRA = {'Asia': {'Russia'}}

TOP = (255, 92, 108)
BOTTOM = (142, 12, 26)
EDGE = (255, 255, 255)


def clip(poly, box):
    """Sutherland-Hodgman clip of a lon/lat ring to the window."""
    x0, x1, y0, y1 = box
    edges = [lambda p: p[0] >= x0, lambda p: p[0] <= x1, lambda p: p[1] >= y0, lambda p: p[1] <= y1]
    cuts = [lambda a, b: cut(a, b, 0, x0), lambda a, b: cut(a, b, 0, x1), lambda a, b: cut(a, b, 1, y0), lambda a, b: cut(a, b, 1, y1)]
    out = poly
    for inside, at in zip(edges, cuts):
        src, out = out, []
        if not src:
            break
        prev = src[-1]
        for cur in src:
            if inside(cur):
                if not inside(prev):
                    out.append(at(prev, cur))
                out.append(cur)
            elif inside(prev):
                out.append(at(prev, cur))
            prev = cur
    return out


def cut(a, b, axis, v):
    t = (v - a[axis]) / ((b[axis] - a[axis]) or 1e-9)
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def rings(features, name, box):
    for f in features:
        pr = f['properties']
        if pr['CONTINENT'] != name and pr['NAME'] not in EXTRA.get(name, ()):
            continue
        g = f['geometry']
        polys = g['coordinates'] if g['type'] == 'MultiPolygon' else [g['coordinates']]
        for poly in polys:
            ring = clip([tuple(p) for p in poly[0]], box)
            if len(ring) >= 3:
                yield ring


def draw(rs, size):
    # equal-looking shapes: longitudes squeezed by the cosine of the middle latitude
    lat0 = sum(p[1] for r in rs for p in r) / sum(len(r) for r in rs)
    k = math.cos(math.radians(lat0))
    pts = [(p[0] * k, -p[1]) for r in rs for p in r]
    minx, maxx = min(p[0] for p in pts), max(p[0] for p in pts)
    miny, maxy = min(p[1] for p in pts), max(p[1] for p in pts)
    ss = 4
    big = size * ss
    pad = big * 0.08
    scale = (big - 2 * pad) / max(maxx - minx, maxy - miny)
    ox = (big - (maxx - minx) * scale) / 2
    oy = (big - (maxy - miny) * scale) / 2
    mask = Image.new('L', (big, big), 0)
    d = ImageDraw.Draw(mask)
    for r in rs:
        d.polygon([((p[0] * k - minx) * scale + ox, (-p[1] - miny) * scale + oy) for p in r], fill=255)
    grow = max(3, int(big / 64) * 2 + 1)
    edge = mask.filter(ImageFilter.MaxFilter(grow))
    fill = Image.new('RGBA', (big, big))
    for y in range(big):
        t = y / (big - 1)
        c = tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3))
        fill.paste(c + (255,), (0, y, big, y + 1))
    out = Image.new('RGBA', (big, big), (0, 0, 0, 0))
    out.paste(EDGE + (255,), (0, 0), edge)
    out.paste(fill, (0, 0), mask)
    return out.resize((size, size), Image.LANCZOS)


def main(path):
    feats = json.load(open(path, encoding='utf-8'))['features']
    os.makedirs(os.path.join(OUT, 'web'), exist_ok=True)
    for code, name, box in CONTINENTS:
        rs = list(rings(feats, name, box))
        draw(rs, 64).save(os.path.join(OUT, code + '.png'), optimize=True)
        draw(rs, 256).save(os.path.join(OUT, 'web', code + '.png'), optimize=True)
        print(code, name, len(rs), 'shapes')


if __name__ == '__main__':
    main(sys.argv[1])
