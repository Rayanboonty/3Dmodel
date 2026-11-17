"""Builds the VortexRealm source tree (vortexrealm/) from PracticePvP (practicepvp/, the source of truth).

PracticePvP 2.x.y becomes VortexRealm 3.x.y: package net.practicepvp -> net.vortexrealm, main class
PracticePlugin -> VortexRealm, tables pp_* -> vr_*, permissions practice.* -> vortex.*, folder
plugins/PracticePvP -> plugins/VortexRealm. Files in tools/vortexrealm-extra/ (VortexMigration.java) are
laid on top. Never edit vortexrealm/ by hand: change practicepvp/ and run this again.

usage (from vortex-realm/):  python3 -I tools/make_vortex.py
then:                        mvn -q -o package -DskipTests -pl vortexrealm

Rebuilt in Nov 2026 from the committed 3.9.2 output (the original script was lost); it reproduces
vortexrealm/ 3.9.2 byte for byte from practicepvp/ 2.9.2.
"""
import os, re, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'practicepvp')
DST = os.path.join(ROOT, 'vortexrealm')
EXTRA = os.path.join(ROOT, 'tools', 'vortexrealm-extra')


def core_version():
    s = open(os.path.join(SRC, 'pom.xml'), encoding='utf-8').read()
    return re.search(r'<artifactId>PracticePvP</artifactId>\s*<version>([^<]+)</version>', s).group(1)


def realm_version(v):
    # PracticePvP 2.x.y -> VortexRealm 3.x.y
    major, rest = v.split('.', 1)
    return str(int(major) + 1) + '.' + rest


TABLES = ['kit_stats', 'players', 'friends', 'follows', 'matches', 'layouts', 'meta', 'mutes', 'cosmetics', 'ips', 'academy']
PERM = re.compile(r'(?<![\w.])practice\.(?=[a-z])')


def java_rewrite(s):
    s = s.replace('net.practicepvp', 'net.vortexrealm')
    s = re.sub(r'\bPracticePlugin\b', 'VortexRealm', s)
    s = PERM.sub('vortex.', s)
    for t in TABLES:
        s = re.sub(r'\bpp_' + t + r'\b', 'vr_' + t, s)
    s = s.replace('idx_pp_kit_elo', 'idx_vr_kit_elo')
    s = s.replace('"pp_side"', '"vr_side"').replace('"pp_hp"', '"vr_hp"')
    s = re.sub(r'\bPracticePvP\b', 'VortexRealm', s)
    return s


def rep(s, old, new, path):
    if s.count(old) != 1:
        sys.exit('patch failed in %s: %r (%d)' % (path, old[:80], s.count(old)))
    return s.replace(old, new)


def rd(p):
    return open(p, encoding='utf-8').read()


def wr(p, s):
    open(p, 'w', encoding='utf-8').write(s)


def main():
    if os.path.isdir(os.path.join(DST, 'src')):
        shutil.rmtree(os.path.join(DST, 'src'))
    os.makedirs(DST, exist_ok=True)
    shutil.copytree(os.path.join(SRC, 'src'), os.path.join(DST, 'src'))
    jroot = os.path.join(DST, 'src/main/java/net')
    os.rename(jroot + '/practicepvp', jroot + '/vortexrealm')
    jdir = jroot + '/vortexrealm'
    os.rename(jdir + '/PracticePlugin.java', jdir + '/VortexRealm.java')
    for dp, dn, fn in os.walk(jdir):
        for f in fn:
            if f.endswith('.java'):
                p = os.path.join(dp, f)
                s = rd(p)
                n = java_rewrite(s)
                if n != s:
                    wr(p, n)
    patch_main(jdir + '/VortexRealm.java')
    patch_specific(jdir)
    for dp, dn, fn in os.walk(EXTRA):
        for f in fn:
            src = os.path.join(dp, f)
            dst = os.path.join(DST, 'src', os.path.relpath(src, EXTRA))
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            shutil.copyfile(src, dst)
    resources()
    pom()
    print('VortexRealm %s source ready at %s' % (realm_version(core_version()), DST))


def patch_main(p):
    s = rd(p)
    # VortexRealm refuses to run next to PracticePvP, and moves PracticePvP data over on the first start
    s = rep(s, '''        this.vault = new DataVault(this);
        this.vault.beforeLoad();''', '''        if (this.getServer().getPluginManager().getPlugin("PracticePvP") != null) {
            this.getLogger()
                .severe(
                    "PracticePvP is installed too. VortexRealm replaces it: remove PracticePvP-*.jar from plugins/ and restart. Your PracticePvP data is moved over automatically on the first start. VortexRealm stays off until then."
                );
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        new VortexMigration(this).autoMigrate();
        this.vault = new DataVault(this);
        this.vault.beforeLoad();''', p)
    s = rep(s, '''        this.bind("pvpadmin", adminCommands);
''', '''        this.bind("pvpadmin", adminCommands);
        this.bind("vortex", new VortexMigration(this));
''', p)
    s = rep(s, '(%practice_...%)', '(%vortex_...%)', p)
    wr(p, s)


def patch_specific(jdir):
    # own backup vault, so it never mixes with a PracticePvP install's VortexRealm-Vault
    p = jdir + '/data/DataVault.java'
    s = rd(p)
    s = rep(s, '"VortexRealm-Vault");', '"VortexRealm-2-Vault");', p)
    wr(p, s)
    p = jdir + '/SelfTest.java'
    s = rd(p)
    s = rep(s, '"<gold><b>VortexRealm self test"', '"<gold><b>Vortex Realm self test"', p)
    wr(p, s)
    p = jdir + '/hook/PlaceholderHook.java'
    s = rd(p)
    s = rep(s, 'return "practice";', 'return "vortex";', p)
    wr(p, s)
    p = jdir + '/config/Opt.java'
    s = rd(p)
    s = rep(s, '"database.name", Opt.Type.STRING, "practice",', '"database.name", Opt.Type.STRING, "vortexrealm",', p)
    wr(p, s)


def resources():
    r = os.path.join(DST, 'src/main/resources')
    p = r + '/plugin.yml'
    s = rd(p)
    s = s.replace('name: PracticePvP', 'name: VortexRealm', 1)
    s = s.replace('main: net.practicepvp.PracticePlugin', 'main: net.vortexrealm.VortexRealm', 1)
    s = PERM.sub('vortex.', s)
    s = rep(s, '\ndescription: Vortex Realm practice - MCPVP-style ranked/unranked duels, LT5-HT1 tiers, Tier Tests and Tier Defense, friends, FFA, parties, kit editor.\n',
            '\ndescription: Vortex Realm - MCPVP-style practice (ranked duels, LT5-HT1 tiers, Tier Tests and Tier Defense, friends, MCPVP tab), FFA, parties, practice bots, hub and server selector for the network, web panel.\n', p)
    s = rep(s, 'authors: [PracticePvP, Vortex Realm]', 'authors: [Vortex Realm]', p)
    s = rep(s, 'description: PracticePvP admin panel.', 'description: Vortex Realm admin panel.', p)
    s = rep(s, '\n\npermissions:\n', '''
  vortex:
    description: "Vortex Realm info - /vortex migrate moves PracticePvP data."
    aliases: [vr, vortexrealm]

permissions:
''', p)
    wr(p, s)
    for name, old, new in (('messages.yml', '# PracticePvP messages', '# VortexRealm messages'),
                           ('default-kits.yml', '# PracticePvP kits.', '# VortexRealm kits.'),
                           ('web/admin.html', '"Run any PracticePvP command"', '"Run any Vortex Realm command"')):
        p = r + '/' + name
        wr(p, rep(rd(p), old, new, p))


def pom():
    s = rd(os.path.join(SRC, 'pom.xml'))
    v = core_version()
    s = s.replace('<groupId>net.practicepvp</groupId>', '<groupId>net.vortexrealm</groupId>')
    s = s.replace('<artifactId>PracticePvP</artifactId>', '<artifactId>VortexRealm</artifactId>')
    s = s.replace('<version>' + v + '</version>', '<version>' + realm_version(v) + '</version>', 1)
    s = s.replace('<name>PracticePvP</name>', '<name>VortexRealm</name>')
    wr(os.path.join(DST, 'pom.xml'), s)


if __name__ == '__main__':
    main()
