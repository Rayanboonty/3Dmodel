"""Check the Vortex mixins against one Minecraft version's real classes.

The Java compiler and the mixin annotation processor only check that a
target method's *name* exists; a handler whose parameters don't match the
target (a method that gained or lost a parameter between versions) is only
found when the game starts, as a crash. This reads every mixin in the merged
sources for a version and compares it with the descriptors in that version's
mapped Minecraft jar (from Loom's cache).

    python3 mod/tools/check_mixins.py 1.20.4      (after gradle build -Pmc=1.20.4)

Exits 1 and lists the problems if any mixin doesn't fit.
"""
import glob
import re
import subprocess
import sys
from functools import lru_cache
from pathlib import Path

MOD = Path(__file__).resolve().parents[1]
PRIM = {"void": "V", "boolean": "Z", "byte": "B", "char": "C", "short": "S", "int": "I", "long": "J", "float": "F", "double": "D"}


def jar_for(mc):
    hits = sorted(glob.glob(f"{Path.home()}/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged/{mc}-*/minecraft-merged-{mc}-*.jar"))
    hits = [h for h in hits if "sources" not in h]
    if not hits:
        sys.exit(f"no mapped jar for {mc}: run gradle build -Pmc={mc} first")
    return hits[0]


@lru_cache(maxsize=None)
def methods(jar, cls):
    """name -> list of descriptors, for a class (and its superclasses)."""
    out = subprocess.run(["javap", "-p", "-s", "-cp", jar, cls], capture_output=True, text=True).stdout
    found = {}
    name = None
    for line in out.splitlines():
        line = line.strip()
        m = re.match(r"^(?:[\w$<>,.\[\] ]+ )?([\w$<>]+)\((.*)\)(?: throws .*)?;$", line)
        if m:
            name = m.group(1)
            continue
        if line.startswith("descriptor:") and name:
            found.setdefault(name, []).append(line.split(":", 1)[1].strip())
            name = None
    sup = re.search(r"class [\w.$]+(?:<[^{]*?>)? extends ([\w.$]+)", out)
    if sup and sup.group(1) not in ("java.lang.Object", "java.lang.Record", "java.lang.Enum"):
        for k, v in methods(jar, sup.group(1)).items():
            found.setdefault(k, []).extend(v)
    return found


def class_exists(jar, cls):
    return subprocess.run(["javap", "-cp", jar, cls], capture_output=True, text=True).returncode == 0


def params_of(desc):
    inner = desc[1:desc.index(")")]
    out, i = [], 0
    while i < len(inner):
        j = i
        while inner[j] == "[":
            j += 1
        if inner[j] == "L":
            j = inner.index(";", j)
        out.append(inner[i:j + 1])
        i = j + 1
    return out


def to_desc(t, imports, pkg, jar):
    t = re.sub(r"<.*>", "", t).strip()
    arr = ""
    while t.endswith("[]"):
        arr += "["
        t = t[:-2]
    if t in PRIM:
        return arr + PRIM[t]
    if t.endswith("..."):
        return arr + "[" + to_desc(t[:-3], imports, pkg, jar)
    head, _, rest = t.partition(".")
    if head in imports:
        fq = imports[head] + ("$" + rest.replace(".", "$") if rest else "")
    elif "." in t and t[0].islower():
        fq = t
    elif t in ("String", "Object", "Integer", "Boolean", "Float", "Double", "Long"):
        fq = "java.lang." + t
    else:
        fq = pkg + "." + t
    return arr + "L" + fq.replace(".", "/") + ";"


def check(mc):
    jar = jar_for(mc)
    src = MOD / "build/merged" / mc / "java/dev/vortexclient/mod/mixin"
    problems = []
    for f in sorted(src.glob("*.java")):
        text = f.read_text()
        imports = {m.group(1).split(".")[-1]: m.group(1) for m in re.finditer(r"^import ([\w.]+);", text, re.M)}
        pkg = "dev.vortexclient.mod.mixin"
        # Each @Mixin(...) applies to the class body that follows it.
        for block in re.split(r"(?=@Mixin\()", text)[1:]:
            target = re.match(r"@Mixin\(([\w.]+)\.class", block).group(1)
            head, _, rest = target.partition(".")
            cls = imports.get(head, target) + ("$" + rest if rest else "")
            if not class_exists(jar, cls):
                problems.append(f"{f.name}: class {cls} doesn't exist")
                continue
            ms = methods(jar, cls)
            for ann in re.finditer(r"@(Inject|ModifyArg|ModifyArgs|Redirect|ModifyVariable)\((.*?)\)\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:private|public|protected)\s+(?:static\s+)?[\w<>\[\]., ]+\s+(vortex\$\w+)\(([^)]*)\)", block, re.S):
                kind, body, handler, hparams = ann.groups()
                optional = re.search(r"require\s*=\s*0", body)
                mm = re.search(r'method\s*=\s*"([^"]+)"', body)
                if not mm:
                    continue
                spec = mm.group(1)
                name, _, desc = spec.partition("(")
                desc = "(" + desc if desc else ""
                tag = f"{f.name} {handler} -> {cls.split('.')[-1]}.{spec}"
                cands = ms.get(name, [])
                if desc:
                    cands = [d for d in cands if d == desc]
                if not cands:
                    problems.append(f"{tag}: no such method" + (" (optional)" if optional else ""))
                    continue
                # The thing called inside the method, when there is one.
                tm = re.search(r'target\s*=\s*"L([\w/$]+);([\w$<>]+)(\([^"]*)"', body)
                if tm and kind != "Inject":
                    owner, tname, tdesc = tm.group(1).replace("/", "."), tm.group(2), tm.group(3)
                    if tdesc not in methods(jar, owner).get(tname, []):
                        problems.append(f"{tag}: calls {owner.split('.')[-1]}.{tname}{tdesc}, which doesn't exist" + (" (optional)" if optional else ""))
                if kind == "Inject":
                    hp = [p.strip() for p in re.split(r",(?![^<]*>)", hparams) if p.strip()]
                    types = [re.sub(r"\s+\w+$", "", p).replace("final ", "").strip() for p in hp]
                    types = [t for t in types if not t.startswith("CallbackInfo")]
                    want = [to_desc(t, imports, pkg, jar) for t in types]
                    # What the method returns must match CallbackInfoReturnable<X>.
                    rm = re.search(r"CallbackInfoReturnable<([\w.]+)>", hparams)
                    if rm:
                        box = {"Float": "F", "Double": "D", "Integer": "I", "Boolean": "Z", "Long": "J", "Short": "S", "Byte": "B", "Character": "C"}
                        rt = box.get(rm.group(1)) or to_desc(rm.group(1), imports, pkg, jar)
                        if not any(d.split(")")[1] == rt for d in cands):
                            problems.append(f"{tag}: handler expects a {rm.group(1)} result, the method returns " + " or ".join(d.split(")")[1] for d in cands))
                    if not any(params_of(d) == want for d in cands):
                        problems.append(f"{tag}: handler takes ({', '.join(types)}), the method takes " + " or ".join("(" + ", ".join(p.split('/')[-1].rstrip(';') for p in params_of(d)) + ")" for d in cands))
    return problems


if __name__ == "__main__":
    mc = sys.argv[1] if len(sys.argv) > 1 else "1.21.4"
    probs = check(mc)
    for p in probs:
        print("  " + p)
    print(f"{mc}: {'OK' if not probs else str(len(probs)) + ' problem(s)'}")
    sys.exit(1 if [p for p in probs if "(optional)" not in p] else 0)
