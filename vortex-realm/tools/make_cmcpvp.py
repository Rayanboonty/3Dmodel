"""Builds the C MCPvP source tree (cmcpvp/) from PracticePvP (practicepvp/, the source of truth).

C MCPvP ("Cracked Minecraft PvP") is the practice-only build: same code, with edition.properties switching the SMP,
hub, selector and season parts off. Its version is EDITION_VERSION below (its own line, 1.0.0 at launch):
package net.practicepvp -> net.cmcpvp, main class PracticePlugin -> CMCPvP, permissions practice.* -> cmcpvp.*,
folder plugins/CMCPvP, database "cmcpvp", its own update feed (never the Vortex one), "Vortex Realm" -> "C MCPvP".
Never edit cmcpvp/ by hand: change practicepvp/ and run this again.

usage (from vortex-realm/):  python3 -I tools/make_cmcpvp.py
then:                        mvn -q -o package -DskipTests -pl cmcpvp
"""
import os, re, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'practicepvp')
DST = os.path.join(ROOT, 'cmcpvp')
NAME = 'C MCPvP'
FEED = 'https://raw.githubusercontent.com/mroudani-cyber/claud/ccr-7054e872-gpe1kq/cmcpvp-handoff/update.json'
# C MCPvP has its own version line (1.0.0 = launch); bump it for every C MCPvP release
EDITION_VERSION = '1.0.6'
PERM = re.compile(r'(?<![\w.])practice\.(?=[a-z])')


def core_version():
    s = open(os.path.join(SRC, 'pom.xml'), encoding='utf-8').read()
    return re.search(r'<artifactId>PracticePvP</artifactId>\s*<version>([^<]+)</version>', s).group(1)


def edition_version(v):
    return EDITION_VERSION


def rd(p):
    return open(p, encoding='utf-8').read()


def wr(p, s):
    open(p, 'w', encoding='utf-8').write(s)


def rep(s, old, new, path):
    if s.count(old) < 1:
        sys.exit('patch failed in %s: %r' % (path, old[:80]))
    return s.replace(old, new)


def brand(s):
    s = s.replace('Vortex Realm', NAME).replace('VORTEX REALM', 'C MCPVP')
    return s


def java_rewrite(s):
    s = s.replace('net.practicepvp', 'net.cmcpvp')
    s = s.replace('play.vortexrealm.net', 'play.cmcpvp.net').replace('https://vortexrealm.net', 'https://cmcpvp.net')
    s = re.sub(r'\bPracticePlugin\b', 'CMCPvP', s)
    s = PERM.sub('cmcpvp.', s)
    s = re.sub(r'\bPracticePvP\b', 'CMCPvP', s)
    return brand(s)


def main():
    if os.path.isdir(os.path.join(DST, 'src')):
        shutil.rmtree(os.path.join(DST, 'src'))
    os.makedirs(DST, exist_ok=True)
    shutil.copytree(os.path.join(SRC, 'src'), os.path.join(DST, 'src'))
    shutil.rmtree(os.path.join(DST, 'src', 'test'), ignore_errors=True)
    jroot = os.path.join(DST, 'src/main/java/net')
    os.rename(jroot + '/practicepvp', jroot + '/cmcpvp')
    jdir = jroot + '/cmcpvp'
    os.rename(jdir + '/PracticePlugin.java', jdir + '/CMCPvP.java')
    for dp, dn, fn in os.walk(jdir):
        for f in fn:
            if f.endswith('.java'):
                p = os.path.join(dp, f)
                s = rd(p)
                n = java_rewrite(s)
                if n != s:
                    wr(p, n)
    patch_specific(jdir)
    resources()
    pom()
    print('CMCPvP %s source ready at %s' % (edition_version(core_version()), DST))


def patch_specific(jdir):
    # refuse to run next to the Vortex / PracticePvP build (same commands, same tables)
    p = jdir + '/CMCPvP.java'
    s = rd(p)
    s = rep(s, '''        this.vault = new DataVault(this);
        this.vault.beforeLoad();''', '''        for (String other : java.util.List.of("PracticePvP", "VortexRealm")) {
            if (this.getServer().getPluginManager().getPlugin(other) != null) {
                this.getLogger().severe(other + " is installed too. C MCPvP is its own server: remove " + other + "-*.jar from plugins/ and restart.");
                this.getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }

        this.vault = new DataVault(this);
        this.vault.beforeLoad();''', p)
    s = s.replace('(%practice_...%)', '(%cmcpvp_...%)')
    wr(p, s)
    p = jdir + '/data/DataVault.java'
    wr(p, rep(rd(p), '"VortexRealm-Vault");', '"CMCPvP-Vault");', p))
    p = jdir + '/hook/PlaceholderHook.java'
    wr(p, rep(rd(p), 'return "practice";', 'return "cmcpvp";', p))
    p = jdir + '/config/Opt.java'
    s = rd(p)
    s = rep(s, '"database.name", Opt.Type.STRING, "practice",', '"database.name", Opt.Type.STRING, "cmcpvp",', p)
    # its own update feed: a C MCPvP server never downloads the Vortex jar
    s = re.sub(r'(UPDATE_SOURCES_BUILT_IN = List\.of\(\s*)"[^"]+",\s*"[^"]+"', r'\1"' + FEED + '"', s)
    if FEED not in s:
        sys.exit('could not set the update feed in Opt.java')
    s = s.replace('"general.server-ip", "play.yourserver.net"', '"general.server-ip", "play.cmcpvp.net"')
    # never send C MCPvP players to the Vortex Discord: no link until the owner sets theirs (/pvpadmin settings)
    s = rep(s, '"extras.links.discord", "https://discord.gg/knHtEeHyk"', '"extras.links.discord", ""', p)
    s = rep(s, '"Join our Discord!|discord.gg/knHtEeHyk|minecraft:amethyst_shard"', '"Join our Discord!|/links|minecraft:amethyst_shard"', p)
    # red and gold scoreboard prefix for the new brand
    wr(p, s)


def resources():
    r = os.path.join(DST, 'src/main/resources')
    wr(r + '/edition.properties', 'practice-only=true\nname=' + NAME + '\n')
    # the SMP season story is not part of C MCPvP (the old club spawn map stays: it can still be chosen next to The Fracture)
    for f in ('realm-story.yml',):
        fp = os.path.join(r, f)
        if os.path.exists(fp):
            os.remove(fp)
    p = r + '/plugin.yml'
    s = rd(p)
    s = s.replace('name: PracticePvP', 'name: CMCPvP', 1)
    s = s.replace('main: net.practicepvp.PracticePlugin', 'main: net.cmcpvp.CMCPvP', 1)
    s = PERM.sub('cmcpvp.', s)
    s = re.sub(r'\ndescription: [^\n]*\n', '\ndescription: C MCPvP - Cracked Minecraft PvP. Practice only - ranked and unranked duels, LT5-HT1 tiers, FFA, parties, bots, events.\n', s, count=1)
    s = re.sub(r'authors: \[[^\]]*\]', 'authors: [C MCPvP]', s, count=1)
    s = re.sub(r'website: [^\n]*', 'website: https://cmcpvp.net', s, count=1)
    wr(p, brand(s))
    for dp, dn, fn in os.walk(r):
        for f in fn:
            if f.endswith(('.yml', '.html', '.txt', '.properties')) and f != 'plugin.yml':
                fp = os.path.join(dp, f)
                s = rd(fp)
                n = brand(s).replace('# PracticePvP', '# C MCPvP')
                if f == 'index.html':
                    n = n.replace('play.vortexrealm.net', 'play.cmcpvp.net').replace('https://discord.gg/knHtEeHyk', '#')
                    n = n.replace('tournaments and SMP worlds', 'tournaments and events')
                if n != s:
                    wr(fp, n)
    logos(r)


def logos(r):
    """C MCPvP logo pictures for the website (the owner can swap them for their own art)."""
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError:
        return
    def font(size):
        for f in ('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', '/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf'):
            if os.path.exists(f):
                return ImageFont.truetype(f, size)
        return ImageFont.load_default()
    for name, (w, h), size in (('logo.png', (595, 420), 150), ('logo-mark.png', (241, 176), 70)):
        img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        f = font(size)
        txt = 'C MCPvP' if name == 'logo.png' else 'CM'
        tw, th = d.textbbox((0, 0), txt, font=f)[2:]
        x, y = (w - tw) // 2, (h - th) // 2 - (20 if name == 'logo.png' else 0)
        for dx in range(-6, 7, 2):
            for dy in range(-6, 7, 2):
                d.text((x + dx, y + dy), txt, font=f, fill=(20, 8, 8, 255))
        # crimson to gold
        grad = Image.new('RGBA', (w, h))
        gd = ImageDraw.Draw(grad)
        for i in range(h):
            t = i / max(1, h - 1)
            gd.line([(0, i), (w, i)], fill=(int(255 - 10 * t), int(46 + 140 * t), int(77 - 40 * t), 255))
        mask = Image.new('L', (w, h), 0)
        ImageDraw.Draw(mask).text((x, y), txt, font=f, fill=255)
        img.paste(grad, (0, 0), mask)
        if name == 'logo.png':
            sf = font(34)
            sub = 'CRACKED MINECRAFT PVP'
            sw = d.textbbox((0, 0), sub, font=sf)[2]
            d.text(((w - sw) // 2, y + th + 40), sub, font=sf, fill=(255, 210, 120, 255))
        img.save(os.path.join(r, 'web', name))


def pom():
    s = rd(os.path.join(SRC, 'pom.xml'))
    v = core_version()
    s = s.replace('<groupId>net.practicepvp</groupId>', '<groupId>net.cmcpvp</groupId>')
    s = s.replace('<artifactId>PracticePvP</artifactId>', '<artifactId>CMCPvP</artifactId>')
    s = s.replace('<version>' + v + '</version>', '<version>' + edition_version(v) + '</version>', 1)
    s = s.replace('<name>PracticePvP</name>', '<name>CMCPvP</name>')
    wr(os.path.join(DST, 'pom.xml'), s)


if __name__ == '__main__':
    main()
