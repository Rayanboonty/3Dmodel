"""Builds the VortexPvP 1.0.0 test plugin: a full copy of PracticePvP renamed to net.vortexpvp, with the add-on
systems from vortexpvp-plus/ laid on top (they are never overwritten by this script).
usage: python3 -I tools/make_vortexpvp.py"""
import os, re, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = ROOT + '/practicepvp'
PLUS = ROOT + '/vortexpvp-plus'
DST = ROOT + '/vortexpvp'
VERSION = '1.0.2'
CORE_VERSION = '1.7.4'

TABLES = ['kit_stats', 'players', 'friends', 'follows', 'matches', 'layouts', 'meta', 'mutes', 'cosmetics', 'ips', 'academy']

def java_rewrite(s):
    s = s.replace('net.practicepvp', 'net.vortexpvp')
    s = re.sub(r'\bPracticePlugin\b', 'VortexPvP', s)
    s = s.replace('"PracticePvP"', '"VortexPvP"')
    s = re.sub(r'(?<![\w.])practice\.(?=[a-z])', 'vortexpvp.', s)
    for t in TABLES:
        s = re.sub(r'\bpp_' + t + r'\b', 'vp_' + t, s)
    s = s.replace('idx_pp_kit_elo', 'idx_vp_kit_elo')
    s = s.replace('"pp_side"', '"vp_side"').replace('"pp_hp"', '"vp_hp"')
    s = s.replace('plugins/PracticePvP/', 'plugins/VortexPvP/')
    s = re.sub(r'\bPracticePvP\b', 'VortexPvP', s)
    # the test plugin keeps its own backups, never inside the live VortexRealm vault
    s = s.replace('VortexRealm-Vault', 'VortexPvP-Test-Vault')
    return s

def rep(s, old, new, path):
    if s.count(old) != 1:
        sys.exit('patch failed in %s: %r (%d)' % (path, old[:80], s.count(old)))
    return s.replace(old, new)

def main():
    if os.path.isdir(DST + '/src'):
        shutil.rmtree(DST + '/src')
    os.makedirs(DST, exist_ok=True)
    shutil.copytree(SRC + '/src', DST + '/src')
    jroot = DST + '/src/main/java/net'
    os.rename(jroot + '/practicepvp', jroot + '/vortexpvp')
    jdir = jroot + '/vortexpvp'
    os.rename(jdir + '/PracticePlugin.java', jdir + '/VortexPvP.java')
    for dp, dn, fn in os.walk(jdir):
        for f in fn:
            if f.endswith('.java'):
                p = os.path.join(dp, f)
                s = open(p, encoding='utf-8').read()
                n = java_rewrite(s)
                if n != s:
                    open(p, 'w', encoding='utf-8').write(n)

    patch_main(jdir + '/VortexPvP.java')
    patch_specific(jdir)
    # the add-on systems
    shutil.copytree(PLUS + '/src/main/java/net/vortexpvp/plus', jdir + '/plus')
    if os.path.isdir(PLUS + '/src/main/resources/plus'):
        shutil.copytree(PLUS + '/src/main/resources/plus', DST + '/src/main/resources/plus')
    resources()
    pom()
    print('VortexPvP source ready at', DST)

def patch_main(p):
    s = open(p, encoding='utf-8').read()
    s = rep(s, '''        this.upgrader.finish();
        this.enabledOk = true;''', '''        this.upgrader.finish();
        try {
            this.plus = new net.vortexpvp.plus.PlusModule(this);
            this.plus.enable();
        } catch (Throwable plusError) {
            this.getLogger().log(java.util.logging.Level.SEVERE, "VortexPvP add-on systems failed to start - the rest keeps working", plusError);
        }

        this.enabledOk = true;''', p)
    s = rep(s, '''    @Override
    public void onDisable() {
''', '''    private net.vortexpvp.plus.PlusModule plus;

    public net.vortexpvp.plus.PlusModule plus() {
        return this.plus;
    }

    @Override
    public void onDisable() {
        if (this.plus != null) {
            try {
                this.plus.disable();
            } catch (Throwable plusError) {
                this.getLogger().log(java.util.logging.Level.WARNING, "VortexPvP add-on systems failed to stop", plusError);
            }
        }

''', p)
    s = s.replace('(%practice_...%)', '(%vortexpvp_...%)')
    open(p, 'w', encoding='utf-8').write(s)

def patch_specific(jdir):
    p = jdir + '/data/DataVault.java'
    s = open(p, encoding='utf-8').read()
    if '"VortexPvP-Test-Vault"' not in s:
        sys.exit('vault folder not renamed in ' + p)
    p = jdir + '/hook/PlaceholderHook.java'
    s = open(p, encoding='utf-8').read()
    s = rep(s, 'return "practice";', 'return "vortexpvp";', p)
    open(p, 'w', encoding='utf-8').write(s)
    p = jdir + '/config/Opt.java'
    s = open(p, encoding='utf-8').read()
    s = rep(s, '"database.name", Opt.Type.STRING, "practice",', '"database.name", Opt.Type.STRING, "vortexpvp",', p)
    open(p, 'w', encoding='utf-8').write(s)
    # the test plugin has the practice bots and training dummies switched on
    p = jdir + '/bot/BotManager.java'
    s = open(p, encoding='utf-8').read()
    s = rep(s, 'public static final boolean COMING_SOON = true;', 'public static final boolean COMING_SOON = false;', p)
    open(p, 'w', encoding='utf-8').write(s)

def resources():
    r = DST + '/src/main/resources'
    p = r + '/plugin.yml'
    s = open(p, encoding='utf-8').read()
    s = s.replace('name: PracticePvP', 'name: VortexPvP', 1)
    s = s.replace('main: net.practicepvp.PracticePlugin', 'main: net.vortexpvp.VortexPvP', 1)
    s = re.sub(r'(?m)^description: .*$', 'description: VortexPvP - the Vortex Realm test build with every new system (seasons, rank ladder, clans, battle pass, missions, achievements, replays, drills, bounties, Discord and more).', s, count=1)
    s = s.replace('authors: [PracticePvP, Vortex Realm]', 'authors: [Vortex Realm]')
    s = re.sub(r'(?<![\w.])practice\.(?=[a-z])', 'vortexpvp.', s)
    extra = open(PLUS + '/src/main/resources/plus-commands.yml', encoding='utf-8').read()
    s = s.replace('\npermissions:\n', '\n' + extra.rstrip('\n') + '\n\npermissions:\n', 1)
    perms = open(PLUS + '/src/main/resources/plus-permissions.yml', encoding='utf-8').read()
    s = s.replace('\npermissions:\n', '\npermissions:\n' + perms.rstrip('\n') + '\n', 1)
    s = s.replace('PracticePvP admin panel.', 'VortexPvP admin panel.')
    open(p, 'w', encoding='utf-8').write(s)
    for name in ('messages.yml', 'default-kits.yml'):
        p = r + '/' + name
        s = open(p, encoding='utf-8').read()
        s = s.replace('# PracticePvP messages', '# VortexPvP messages').replace('# PracticePvP kits.', '# VortexPvP kits.')
        open(p, 'w', encoding='utf-8').write(s)
    for name in ('index.html', 'admin.html'):
        p = r + '/web/' + name
        s = open(p, encoding='utf-8').read()
        s = s.replace('PracticePvP', 'VortexPvP').replace('Practice PvP', 'VortexPvP')
        open(p, 'w', encoding='utf-8').write(s)

def pom():
    s = open(SRC + '/pom.xml', encoding='utf-8').read()
    s = s.replace('<groupId>net.practicepvp</groupId>', '<groupId>net.vortexpvp</groupId>')
    s = s.replace('<artifactId>PracticePvP</artifactId>', '<artifactId>VortexPvP</artifactId>')
    s = s.replace('<version>' + CORE_VERSION + '</version>', '<version>' + VERSION + '</version>', 1)
    s = s.replace('<name>PracticePvP</name>', '<name>VortexPvP</name>')
    open(DST + '/pom.xml', 'w', encoding='utf-8').write(s)

if __name__ == '__main__':
    main()
