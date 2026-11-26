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
NAME = 'CMCPVP'
FEED = 'https://raw.githubusercontent.com/Rayanboonty/3Dmodel/refs/heads/claude/vortex-realm-pvp-plugin-2x1tq1/vortex-realm/dist/update.json'
OLD_FEED = 'https://raw.githubusercontent.com/mroudani-cyber/claud/ccr-7054e872-gpe1kq/cmcpvp-handoff/update.json'
# C MCPvP has its own version line (1.0.0 = launch); bump it for every C MCPvP release
EDITION_VERSION = '1.0.17'
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
    strip_unused(jdir)
    resources()
    pom()
    devortex()
    print('CMCPvP %s source ready at %s' % (edition_version(core_version()), DST))


# every Vortex name, longest first (links to this repo's update feed are kept as they are)
DEVORTEX = [
    ('Vortex Realm', 'C MCPvP'), ('VORTEX REALM', 'C MCPVP'), ('Vortex realm', 'C MCPvP'),
    ('VortexRealm', 'CMCPvP'), ('VortexPvP', 'CMCPvP'), ('VortexSMP', 'CmcSmp'), ('VortexMC', 'CMCPvP'),
    ('VortexIsles', 'CrystalIsles'), ('Vortex Isles', 'Crystal Isles'), ('VORTEX_ISLES', 'CRYSTAL_ISLES'),
    ('VortexUpdater', 'CmcUpdater'), ('VortexBot', 'CMCBot'), ('VortexMigration', 'CmcMigration'),
    ('VORTEX_', 'CMC_'), ('VORTEX', 'CMC'), ('Vortex', 'CMC'),
    ('vortexrealm', 'cmcpvp'), ('vortexsmps', 'cmcpvp'), ('vortexsmp', 'cmcsmp'), ('vortexpvp', 'cmcpvp'),
    ('vortexplus', 'cmcplus'), ('vortex', 'cmcpvp'),
]
URL = re.compile(r'https?://[^\s"\'<>)]*')


def devortex_text(s):
    kept = []

    def keep(m):
        kept.append(m.group(0))
        return '\x00%d\x00' % (len(kept) - 1)

    s = URL.sub(lambda m: keep(m) if 'raw.githubusercontent.com' in m.group(0) or 'github.com/apps' in m.group(0) else m.group(0), s)
    s = s.replace('"Vor" + "texRealm"', '\x01')
    for a, b in DEVORTEX:
        s = s.replace(a, b)
    s = s.replace('\x01', '"Vor" + "texRealm"')
    return re.sub('\x00(\\d+)\x00', lambda m: kept[int(m.group(1))], s)


def devortex():
    """C MCPvP shows no Vortex name anywhere: classes, messages, menus, worlds, pack names, files."""
    root = os.path.join(DST, 'src')
    for dp, dn, fn in os.walk(root):
        for f in fn:
            if f.endswith(('.java', '.yml', '.yaml', '.html', '.js', '.css', '.json', '.txt', '.properties', '.md', '.mcmeta')):
                fp = os.path.join(dp, f)
                s = rd(fp)
                n = devortex_text(s)
                if n != s:
                    wr(fp, n)
    # files and folders with the name
    for dp, dn, fn in os.walk(root, topdown=False):
        for name in fn + dn:
            if 'vortex' in name.lower():
                os.rename(os.path.join(dp, name), os.path.join(dp, devortex_text(name)))
    left = []
    for dp, dn, fn in os.walk(root):
        for f in fn:
            fp = os.path.join(dp, f)
            if f.endswith(('.java', '.yml', '.html', '.js', '.json', '.properties')):
                for i, line in enumerate(rd(fp).splitlines()):
                    if 'vortex' in URL.sub('', line).lower() and '"Vor" + "texRealm"' not in line:
                        left.append('%s:%d: %s' % (fp, i + 1, line.strip()[:120]))
    if left:
        sys.exit('Vortex names left:\n' + '\n'.join(left[:30]))
    # the name is written as one word everywhere: CMCPVP
    for dp, dn, fn in os.walk(root):
        for f in fn:
            if f.endswith(('.java', '.yml', '.yaml', '.html', '.js', '.css', '.json', '.txt', '.properties', '.md')):
                fp = os.path.join(dp, f)
                s = rd(fp)
                n = s.replace('C MCPvP', 'CMCPVP').replace('C MCPVP', 'CMCPVP').replace('C-MCPvP', 'CMCPVP')
                if n != s:
                    wr(fp, n)
    two_tone_tab()


def two_tone_tab():
    """The one tab of CMCPVP: the Competitive design in the logo's colours - CMC white, PVP red."""
    p = os.path.join(DST, 'src/main/java/net/cmcpvp/social/TabDesigns.java')
    s = rd(p)
    a = s.index('"COMPETITIVE",')
    b = s.index('new TabDesigns.Design(', a)
    part = s[a:b]
    part = rep(part, '"<b><gradient:#FFD24A:#FF9F1C><server></gradient></b> <dark_gray>|</dark_gray> <#7FE3FF>Practice"',
               '"<b><white>CMC</white><#E3172B>PVP</#E3172B></b> <dark_gray>|</dark_gray> <gray>Practice"', p)
    part = part.replace('"<dark_gray><st>                                        </st>"', '"<#7A0C14><st>                                        </st>"')
    # every accent red or white: labels light red, values white, section bars deep red
    for old, new in (('#FFD24A', '#E3172B'), ('#FF9F1C', '#9E0F1A'), ('#7FE3FF', '#FF4D5E'), ('#B8860B', '#7A0C14'), ('#FFF1B0', '#FFD5D9'),
                     ('#55FF55', '#FFFFFF'), ('#9FA8FF', '#FFD5D9'), ('#2B3445', '#7A0C14')):
        part = part.replace(old, new)
    wr(p, s[:a] + part + s[b:])


def patch_specific(jdir):
    # refuse to run next to the Vortex / PracticePvP build (same commands, same tables)
    p = jdir + '/CMCPvP.java'
    s = rd(p)
    s = rep(s, '''        this.vault = new DataVault(this);
        this.vault.beforeLoad();''', '''        for (String other : java.util.List.of("PracticePvP", "Vor" + "texRealm")) {
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
    p = jdir + '/pack/PackBuilder.java'
    wr(p, rep(rd(p), '"\\"height\\": 36, \\"chars\\": [\\""', '"\\"height\\": 27, \\"chars\\": [\\""', p))
    p = jdir + '/social/TabManager.java'
    wr(p, rep(rd(p), 'line = "<logo_glyph>\\n\\n\\n" +', 'line = "<logo_glyph>\\n\\n" +', p))
    p = jdir + '/data/DataVault.java'
    wr(p, rep(rd(p), '"VortexRealm-Vault");', '"CMCPvP-Vault");', p))
    p = jdir + '/hook/PlaceholderHook.java'
    wr(p, rep(rd(p), 'return "practice";', 'return "cmcpvp";', p))
    p = jdir + '/config/Opt.java'
    s = rd(p)
    s = rep(s, '"database.name", Opt.Type.STRING, "practice",', '"database.name", Opt.Type.STRING, "cmcpvp",', p)
    # its own update feed: a C MCPvP server never downloads the Vortex jar
    # this repo's feed first (where releases are pushed now), the first account's feed after it (the newest wins)
    s = re.sub(r'(UPDATE_SOURCES_BUILT_IN = List\.of\(\s*)"[^"]+",\s*"[^"]+"', r'\1"' + FEED + '", "' + OLD_FEED + '"', s)
    if FEED not in s:
        sys.exit('could not set the update feed in Opt.java')
    s = s.replace('"general.server-ip", "play.yourserver.net"', '"general.server-ip", "play.cmcpvp.net"')
    # never send C MCPvP players to the Vortex Discord: no link until the owner sets theirs (/pvpadmin settings)
    s = rep(s, '"extras.links.discord", "https://discord.gg/knHtEeHyk"', '"extras.links.discord", ""', p)
    s = rep(s, '"Join our Discord!|discord.gg/knHtEeHyk|minecraft:amethyst_shard"', '"Join our Discord!|/links|minecraft:amethyst_shard"', p)
    # red and gold scoreboard prefix for the new brand
    wr(p, s)


def strip_unused(jdir):
    """1.0.16: C MCPvP is practice only - the AI assistant and the Unstable Realm story aren't in its jar.

    Their few outside references are cut with exact patches (rep() fails loudly if the code changed), and a tiny stub
    keeps the website's AI page compiling (it already handles "no assistant")."""
    shutil.rmtree(os.path.join(jdir, 'ai'))
    shutil.rmtree(os.path.join(jdir, 'realm'))
    os.makedirs(os.path.join(jdir, 'ai'))
    wr(os.path.join(jdir, 'ai', 'AiAgent.java'), '''package net.cmcpvp.ai;

/** C MCPvP has no in-game AI assistant: just enough for the website code to compile (it is never created). */
public final class AiAgent {
    private AiAgent() {
    }

    public boolean hasKey() {
        return false;
    }

    public void reset(String session) {
    }

    public Reply chat(String session, String who, boolean op, String text) {
        return new Reply("", java.util.List.of(), false);
    }

    public record Reply(String text, java.util.List<String> changes, boolean ok) {
    }
}
''')
    p = jdir + '/CMCPvP.java'
    s = rd(p)
    s = rep(s, 'import net.cmcpvp.ai.AiCommand;\n', '', p)
    s = rep(s, '''        this.module("ai assistant", () -> {
            this.ai = new AiAgent(this);
            this.bind("ai", new AiCommand(this, this.ai));
        });
''', '', p)
    s = rep(s, '''        this.module("Unstable Realm season", () -> {
            this.realm = new net.cmcpvp.realm.Realm(this);
            this.bind("realm", this.realm);
            this.bind("character", this.realm);
            this.bind("ability", this.realm);
            this.bind("companion", this.realm);
        });
''', '', p)
    s = rep(s, '    private net.cmcpvp.realm.Realm realm;', '    private Object realm;', p)
    s = rep(s, '    public net.cmcpvp.realm.Realm realm() {', '    public Object realm() {', p)
    s = rep(s, '''        if (this.realm != null) {
            try {
                this.realm.save();
            } catch (Throwable realmError) {
                this.getLogger().log(java.util.logging.Level.WARNING, "Could not save the Unstable Realm", realmError);
            }
        }

''', '', p)
    wr(p, s)
    # their commands leave plugin.yml
    py = os.path.join(DST, 'src/main/resources/plugin.yml')
    y = rd(py)
    for cmd in ('ai', 'realm', 'character', 'companion', 'ability'):
        y2 = re.sub(r'\n  ' + cmd + r':\n(    [^\n]*\n)+', '\n', y, count=1)
        if y2 == y:
            sys.exit('strip_unused: command ' + cmd + ' not found in plugin.yml')
        y = y2
    wr(py, y)


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


def owner_logo(r):
    """The owner's CMC PvP logo (tools/cmcpvp-art/logo-source.png): black taken out, cropped tight so CMC and PVP
    sit together. Becomes the tab logo (logo/vr.png, drawn smaller than the Vortex one) and the website logos."""
    from PIL import Image
    src = os.path.join(ROOT, 'tools/cmcpvp-art/logo-source.png')
    if not os.path.exists(src):
        return False
    im = Image.open(src).convert('RGBA')
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            cr, cg, cb, ca = px[x, y]
            m = max(cr, cg, cb)
            a = 0 if m < 28 else (255 if m > 90 else int((m - 28) * 255 / 62))
            px[x, y] = (cr, cg, cb, min(ca, a))
    # tight crop round the letters (faint specks near the edges don't count)
    im = im.crop(im.split()[3].point(lambda v: 255 if v > 160 else 0).getbbox())
    def fit(w, h):
        c = im.copy()
        c.thumbnail((w, h), Image.LANCZOS)
        out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
        out.paste(c, ((w - c.width) // 2, (h - c.height) // 2), c)
        return out
    c = im.copy()
    c.thumbnail((128, 96), Image.LANCZOS)
    c.save(os.path.join(r, 'logo/vr.png'))
    fit(595, 420).save(os.path.join(r, 'web/logo.png'))
    fit(241, 176).save(os.path.join(r, 'web/logo-mark.png'))
    return True


def logos(r):
    """C MCPvP logo pictures for the website (the owner can swap them for their own art)."""
    try:
        from PIL import Image, ImageDraw, ImageFont
    except ImportError:
        return
    if owner_logo(r):
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
