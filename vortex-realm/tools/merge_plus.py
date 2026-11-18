"""One-time: moves the VortexPvP add-on systems (vortexpvp-plus/) into PracticePvP (practicepvp/) as net.practicepvp.plus."""
import os, re, shutil, sys
ROOT = '/home/user/rayanboonty/3dmodel/vortex-realm'
SRC = ROOT + '/vortexpvp-plus/src/main'
DST = ROOT + '/practicepvp/src/main'
RENAME = {'season': 'rseason', 'drill': 'skilldrill', 'link': 'dverify'}

def fix_strings(s, fn):
    out = []
    i = 0
    pat = re.compile(r'"(?:\\.|[^"\\])*"')
    for m in pat.finditer(s):
        out.append(fn(s[i:m.start()], False))
        out.append(fn(m.group(0), True))
        i = m.end()
    out.append(fn(s[i:], False))
    return ''.join(out)

def java(s):
    s = s.replace('net.vortexpvp', 'net.practicepvp')
    s = re.sub(r'(?<![\w.])vortexpvp\.(?=[a-z])', 'practice.', s)
    s = s.replace('%vortexpvp_', '%practice_').replace('plugins/VortexPvP/', 'plugins/PracticePvP/')
    s = re.sub(r'\bvp_(plus_players|plus_docs|hubfun|replay_ctl)\b', r'pp_\1', s)
    def f(chunk, string):
        if string:
            chunk = chunk.replace('VortexPvP', 'PracticePvP')
            for a, b in RENAME.items():
                chunk = re.sub(r'(?<![\w/])/' + a + r'\b', '/' + b, chunk)
            return chunk
        return re.sub(r'\bVortexPvP\b', 'PracticePlugin', chunk)
    return fix_strings(s, f)

jsrc = SRC + '/java/net/vortexpvp/plus'
jdst = DST + '/java/net/practicepvp/plus'
if os.path.isdir(jdst):
    sys.exit('already merged')
os.makedirs(jdst)
for f in os.listdir(jsrc):
    s = java(open(os.path.join(jsrc, f), encoding='utf-8').read())
    if f == 'PlusModule.java':
        for a, b in RENAME.items():
            s = s.replace('"' + a + '"', '"' + b + '"')
    if f == 'PlusCommands.java':
        for a, b in RENAME.items():
            s = s.replace('case "' + a + '" ->', 'case "' + b + '" ->')
        s = s.replace('case "pet" -> this.plus.flair().menu(p, "pet");',
                      'case "pet" -> this.plugin.cosmetics().models(p, net.practicepvp.cosmetics.CosmeticModels.Kind.PET);')
    open(os.path.join(jdst, f), 'w', encoding='utf-8').write(s)

if os.path.isdir(SRC + '/resources/plus'):
    shutil.copytree(SRC + '/resources/plus', DST + '/resources/plus')

def yml(path):
    s = open(path, encoding='utf-8').read()
    s = re.sub(r'(?<![\w.])vortexpvp\.(?=[a-z])', 'practice.', s).replace('VortexPvP', 'PracticePvP')
    return s

cmds = yml(SRC + '/resources/plus-commands.yml')
cmds = cmds.replace('  season:\n', '  rseason:\n').replace('aliases: [seasons, hof]', 'aliases: [rankedseason, hof]')
cmds = cmds.replace('  drill:\n', '  skilldrill:\n').replace('aliases: [drills, practice-drill]', 'aliases: [practice-drill, trainingdrill]')
cmds = cmds.replace('  link:\n', '  dverify:\n')
perms = yml(SRC + '/resources/plus-permissions.yml')
p = DST + '/resources/plugin.yml'
s = open(p, encoding='utf-8').read()
assert s.count('\npermissions:\n') == 1
s = s.replace('\npermissions:\n', '\n' + cmds.rstrip('\n') + '\n\npermissions:\n' + perms.rstrip('\n') + '\n', 1)
open(p, 'w', encoding='utf-8').write(s)

m = DST + '/java/net/practicepvp/PracticePlugin.java'
s = open(m, encoding='utf-8').read()
def rep(s, old, new):
    if s.count(old) != 1:
        sys.exit('main patch failed: ' + old[:60])
    return s.replace(old, new)
s = rep(s, '''        this.upgrader.finish();
        this.enabledOk = true;''', '''        this.upgrader.finish();
        // 3.12.0: the add-on systems (ranked ladder and seasons, clans, 2v2, battle pass, missions, achievements,
        // replays, drills, arcade, bounties, Discord...), formerly the VortexPvP test build
        try {
            this.plus = new net.practicepvp.plus.PlusModule(this);
            this.plus.enable();
        } catch (Throwable plusError) {
            this.getLogger().log(java.util.logging.Level.SEVERE, "The add-on systems failed to start - the rest keeps working", plusError);
        }

        this.enabledOk = true;''')
s = rep(s, '''    @Override
    public void onDisable() {
''', '''    private net.practicepvp.plus.PlusModule plus;

    public net.practicepvp.plus.PlusModule plus() {
        return this.plus;
    }

    @Override
    public void onDisable() {
        if (this.plus != null) {
            try {
                this.plus.disable();
            } catch (Throwable plusError) {
                this.getLogger().log(java.util.logging.Level.WARNING, "The add-on systems failed to stop", plusError);
            }
        }

''')
open(m, 'w', encoding='utf-8').write(s)
print('merged', len(os.listdir(jdst)), 'files')
