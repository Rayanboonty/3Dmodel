#!/usr/bin/env python3
"""Writes dist/update.json (the auto-update manifest) for the newest jars in dist/.

usage: python3 -I tools/make_update_json.py [base-url]
base-url: where the jars can be downloaded; default = the GitHub raw folder of dist/.
"""
import hashlib, json, os, re, sys

DIST = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'dist')
BASE = sys.argv[1] if len(sys.argv) > 1 else \
    'https://raw.githubusercontent.com/Rayanboonty/3Dmodel/refs/heads/claude/vortex-realm-pvp-plugin-2x1tq1/vortex-realm/dist'
NOTES = {
    'VortexRealm': 'SMP selector: "Asleep" status with a sleep note and wake-on-join for free hosts',
    'PracticePvP': 'SMP selector: "Asleep" status with a sleep note and wake-on-join for free hosts',
    'VortexSMP': 'Auto-updates',
}


def ver(v):
    return [int(x) for x in re.findall(r'\d+', v)]


best = {}
for f in os.listdir(DIST):
    m = re.fullmatch(r'(VortexRealm|PracticePvP|VortexSMP)-([0-9.]+)\.jar', f)
    if m and (m.group(1) not in best or ver(m.group(2)) > ver(best[m.group(1)][0])):
        best[m.group(1)] = (m.group(2), f)

out = {}
for name, (v, f) in sorted(best.items()):
    with open(os.path.join(DIST, f), 'rb') as fh:
        sha = hashlib.sha256(fh.read()).hexdigest()
    out[name] = {'version': v, 'url': BASE.rstrip('/') + '/' + f, 'sha256': sha, 'notes': NOTES.get(name, '')}

with open(os.path.join(DIST, 'update.json'), 'w') as fh:
    json.dump(out, fh, indent=2)
    fh.write('\n')
print(json.dumps(out, indent=2))
