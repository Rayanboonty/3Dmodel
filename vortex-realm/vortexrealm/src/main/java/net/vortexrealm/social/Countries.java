package net.vortexrealm.social;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.dialog.LobbyDialogs;
import net.vortexrealm.pack.Glyphs;
import net.vortexrealm.util.ClientVersion;
import net.vortexrealm.util.Snd;
import net.vortexrealm.util.Text;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

public final class Countries {
    public static final Map<String, String> CONTINENTS = new LinkedHashMap<>();
    private static final String DATA = "DZ|Algeria|AF|006233,FFFFFF,D21034\nAO|Angola|AF|CC092F,000000,FFCB00\nBJ|Benin|AF|008751,FCD116,E8112D\nBW|Botswana|AF|75AADB,FFFFFF,000000\nBF|Burkina Faso|AF|EF2B2D,009E49,FCD116\nBI|Burundi|AF|CE1126,FFFFFF,1EB53A\nCV|Cape Verde|AF|003893,FFFFFF,CF2027\nCM|Cameroon|AF|007A5E,CE1126,FCD116\nCF|Central African Republic|AF|003082,FFFFFF,289728,FFCE00\nTD|Chad|AF|002664,FECB00,C60C30\nKM|Comoros|AF|3A75C4,FFC61E,CE1126\nCG|Congo|AF|009543,FBDE4A,DC241F\nCD|DR Congo|AF|007FFF,F7D618,CE1021\nCI|Côte d'Ivoire|AF|F77F00,FFFFFF,009E60\nDJ|Djibouti|AF|6AB2E7,FFFFFF,12AD2B\nEG|Egypt|AF|CE1126,FFFFFF,000000\nGQ|Equatorial Guinea|AF|3E9A00,FFFFFF,E32118\nER|Eritrea|AF|12AD2B,4189DD,EA0437\nSZ|Eswatini|AF|3E5EB9,FFD900,B10C0C\nET|Ethiopia|AF|078930,FCDD09,DA121A\nGA|Gabon|AF|009E60,FCD116,3A75C4\nGM|Gambia|AF|CE1126,0C1C8C,3A7728\nGH|Ghana|AF|CE1126,FCD116,006B3F\nGN|Guinea|AF|CE1126,FCD116,009460\nGW|Guinea-Bissau|AF|CE1126,FCD116,009E49\nKE|Kenya|AF|000000,BB0000,006600\nLS|Lesotho|AF|00209F,FFFFFF,009543\nLR|Liberia|AF|BF0A30,FFFFFF,002868\nLY|Libya|AF|E70013,000000,239E46\nMG|Madagascar|AF|FFFFFF,FC3D32,007E3A\nMW|Malawi|AF|000000,CE1126,339E35\nML|Mali|AF|14B53A,FCD116,CE1126\nMR|Mauritania|AF|D01C1F,006233,FFC400\nMU|Mauritius|AF|EA2839,1A206D,FFD500,00A551\nMA|Morocco|AF|C1272D,006233\nMZ|Mozambique|AF|009A44,000000,FCE100,D21034\nNA|Namibia|AF|003580,D21034,009543\nNE|Niger|AF|E05206,FFFFFF,0DB02B\nNG|Nigeria|AF|008751,FFFFFF,008751\nRW|Rwanda|AF|00A1DE,FAD201,20603D\nST|Sao Tome and Principe|AF|12AD2B,FFCE00,D21034\nSN|Senegal|AF|00853F,FDEF42,E31B23\nSC|Seychelles|AF|003F87,FCD856,D62828,007A3D\nSL|Sierra Leone|AF|1EB53A,FFFFFF,0072C6\nSO|Somalia|AF|4189DD,FFFFFF\nZA|South Africa|AF|007749,FFB81C,DE3831,002395\nSS|South Sudan|AF|000000,DA121A,078930\nSD|Sudan|AF|D21034,FFFFFF,000000,007229\nTZ|Tanzania|AF|1EB53A,FCD116,000000,00A3DD\nTG|Togo|AF|006A4E,FFCE00,D21034\nTN|Tunisia|AF|E70013,FFFFFF\nUG|Uganda|AF|000000,FCDC04,D90000\nZM|Zambia|AF|198A00,EF7D00,DE2010\nZW|Zimbabwe|AF|319208,FFD200,DE2010,000000\nEH|Western Sahara|AF|000000,FFFFFF,007A3D,C4111B\nAF|Afghanistan|AS|000000,D32011,007A36\nAM|Armenia|AS|D90012,0033A0,F2A800\nAZ|Azerbaijan|AS|0092BC,E4002B,00AF66\nBH|Bahrain|AS|CE1126,FFFFFF\nBD|Bangladesh|AS|006A4E,F42A41\nBT|Bhutan|AS|FFD520,FF4E12\nBN|Brunei|AS|F7E017,FFFFFF,000000\nKH|Cambodia|AS|032EA1,E00025\nCN|China|AS|EE1C25,FFFF00\nGE|Georgia|AS|FFFFFF,FF0000\nHK|Hong Kong|AS|DE2910,FFFFFF\nIN|India|AS|FF9933,FFFFFF,138808\nID|Indonesia|AS|FF0000,FFFFFF\nIR|Iran|AS|239F40,FFFFFF,DA0000\nIQ|Iraq|AS|CE1126,FFFFFF,000000\nIL|Israel|AS|0038B8,FFFFFF\nJP|Japan|AS|FFFFFF,BC002D\nJO|Jordan|AS|000000,FFFFFF,007A3D,CE1126\nKZ|Kazakhstan|AS|00AFCA,FEC50C\nKW|Kuwait|AS|007A3D,FFFFFF,CE1126,000000\nKG|Kyrgyzstan|AS|E8112D,FFEF00\nLA|Laos|AS|CE1126,002868\nLB|Lebanon|AS|ED1C24,FFFFFF,00A651\nMO|Macau|AS|00785E,FFFFFF\nMY|Malaysia|AS|CC0001,FFFFFF,010066,FFCC00\nMV|Maldives|AS|D21034,007E3A\nMN|Mongolia|AS|C4272F,015197,F9CF02\nMM|Myanmar|AS|FECB00,34B233,EA2839\nNP|Nepal|AS|DC143C,003893\nKP|North Korea|AS|024FA2,ED1C27\nOM|Oman|AS|FFFFFF,DB161B,008000\nPK|Pakistan|AS|01411C,FFFFFF\nPS|Palestine|AS|000000,FFFFFF,009736,EE2A35\nPH|Philippines|AS|0038A8,CE1126,FCD116\nQA|Qatar|AS|8A1538,FFFFFF\nSA|Saudi Arabia|AS|006C35,FFFFFF\nSG|Singapore|AS|EF3340,FFFFFF\nKR|South Korea|AS|FFFFFF,CD2E3A,0047A0\nLK|Sri Lanka|AS|FFBE29,8D153A,00534E\nSY|Syria|AS|CE1126,FFFFFF,000000\nTW|Taiwan|AS|FE0000,000095\nTJ|Tajikistan|AS|CC0000,FFFFFF,006600\nTH|Thailand|AS|A51931,F4F5F8,2D2A4A\nTL|Timor-Leste|AS|DC241F,FFC726,000000\nTR|Türkiye|AS|E30A17,FFFFFF\nTM|Turkmenistan|AS|00843D,FFFFFF\nAE|United Arab Emirates|AS|00732F,FFFFFF,000000,FF0000\nUZ|Uzbekistan|AS|0099B5,FFFFFF,1EB53A\nVN|Vietnam|AS|DA251D,FFFF00\nYE|Yemen|AS|CE1126,FFFFFF,000000\nAL|Albania|EU|E41E20,000000\nAD|Andorra|EU|10069F,FEDF00,D50032\nAT|Austria|EU|ED2939,FFFFFF\nBY|Belarus|EU|C8313E,4AA657\nBE|Belgium|EU|000000,FDDA24,EF3340\nBA|Bosnia and Herzegovina|EU|002395,FECB00\nBG|Bulgaria|EU|FFFFFF,00966E,D62612\nHR|Croatia|EU|FF0000,FFFFFF,171796\nCY|Cyprus|EU|FFFFFF,D57800\nCZ|Czechia|EU|FFFFFF,D7141A,11457E\nDK|Denmark|EU|C8102E,FFFFFF\nEE|Estonia|EU|0072CE,000000,FFFFFF\nFI|Finland|EU|FFFFFF,002F6C\nFR|France|EU|0055A4,FFFFFF,EF4135\nDE|Germany|EU|000000,DD0000,FFCE00\nGR|Greece|EU|0D5EAF,FFFFFF\nHU|Hungary|EU|CE2939,FFFFFF,477050\nIS|Iceland|EU|02529C,FFFFFF,DC1E35\nIE|Ireland|EU|169B62,FFFFFF,FF883E\nIT|Italy|EU|009246,FFFFFF,CE2B37\nXK|Kosovo|EU|244AA5,D0A650\nLV|Latvia|EU|9E3039,FFFFFF\nLI|Liechtenstein|EU|002B7F,CE1126\nLT|Lithuania|EU|FDB913,006A44,C1272D\nLU|Luxembourg|EU|ED2939,FFFFFF,00A1DE\nMT|Malta|EU|FFFFFF,CF142B\nMD|Moldova|EU|0046AE,FFD200,CC092F\nMC|Monaco|EU|CE1126,FFFFFF\nME|Montenegro|EU|C40308,D3AE3B\nNL|Netherlands|EU|AE1C28,FFFFFF,21468B\nMK|North Macedonia|EU|D20000,FFE600\nNO|Norway|EU|BA0C2F,FFFFFF,00205B\nPL|Poland|EU|FFFFFF,DC143C\nPT|Portugal|EU|006600,FF0000\nRO|Romania|EU|002B7F,FCD116,CE1126\nRU|Russia|EU|FFFFFF,0039A6,D52B1E\nSM|San Marino|EU|5EB6E4,FFFFFF\nRS|Serbia|EU|C6363C,0C4076,FFFFFF\nSK|Slovakia|EU|FFFFFF,0B4EA2,EE1C25\nSI|Slovenia|EU|FFFFFF,005DA4,ED1C24\nES|Spain|EU|AA151B,F1BF00\nSE|Sweden|EU|006AA7,FECC00\nCH|Switzerland|EU|DA291C,FFFFFF\nUA|Ukraine|EU|0057B7,FFD700\nGB|United Kingdom|EU|012169,FFFFFF,C8102E\nVA|Vatican City|EU|FFE000,FFFFFF\nAG|Antigua and Barbuda|NA|CE1126,000000,0072C6,FCD116\nBS|Bahamas|NA|00778B,FFC72C,000000\nBB|Barbados|NA|00267F,FFC726\nBZ|Belize|NA|003F87,CE1126\nCA|Canada|NA|FF0000,FFFFFF\nCR|Costa Rica|NA|002B7F,FFFFFF,CE1126\nCU|Cuba|NA|002A8F,FFFFFF,CF142B\nDM|Dominica|NA|006B3F,FCD116,000000\nDO|Dominican Republic|NA|002D62,FFFFFF,CE1126\nSV|El Salvador|NA|0047AB,FFFFFF\nGL|Greenland|NA|FFFFFF,D00C33\nGD|Grenada|NA|CE1126,FCD116,007A5E\nGT|Guatemala|NA|4997D0,FFFFFF\nHT|Haiti|NA|00209F,D21034\nHN|Honduras|NA|0073CF,FFFFFF\nJM|Jamaica|NA|009B3A,FED100,000000\nMX|Mexico|NA|006847,FFFFFF,CE1126\nNI|Nicaragua|NA|0067C6,FFFFFF\nPA|Panama|NA|FFFFFF,DA121A,072357\nPR|Puerto Rico|NA|ED0000,FFFFFF,0050F0\nKN|Saint Kitts and Nevis|NA|009E49,FCD116,000000,CE1126\nLC|Saint Lucia|NA|65CFFF,FFCE00,000000\nVC|Saint Vincent|NA|0072C6,FCD116,009E60\nTT|Trinidad and Tobago|NA|CE1126,FFFFFF,000000\nUS|United States|NA|B22234,FFFFFF,3C3B6E\nAR|Argentina|SA|74ACDF,FFFFFF,F6B40E\nBO|Bolivia|SA|D52B1E,F9E300,007934\nBR|Brazil|SA|009C3B,FFDF00,002776\nCL|Chile|SA|0039A6,FFFFFF,D52B1E\nCO|Colombia|SA|FCD116,003893,CE1126\nEC|Ecuador|SA|FFDD00,034EA2,ED1C24\nGY|Guyana|SA|009E49,FCD116,CE1126\nPY|Paraguay|SA|D52B1E,FFFFFF,0038A8\nPE|Peru|SA|D91023,FFFFFF\nSR|Suriname|SA|377E3F,FFFFFF,B40A2D,ECC81D\nUY|Uruguay|SA|FFFFFF,0038A8,FCD116\nVE|Venezuela|SA|FFCC00,00247D,CF142B\nAU|Australia|OC|012169,FFFFFF,E4002B\nFJ|Fiji|OC|68BFE5,002868,CE1126\nKI|Kiribati|OC|CE1126,FFD200,003F87\nMH|Marshall Islands|OC|003893,FFFFFF,DD7500\nFM|Micronesia|OC|75B2DD,FFFFFF\nNR|Nauru|OC|002B7F,FFC61E\nNZ|New Zealand|OC|00247D,FFFFFF,CC142B\nPW|Palau|OC|4AADD6,FFDE00\nPG|Papua New Guinea|OC|CE1126,000000,FCD116\nWS|Samoa|OC|CE1126,002B7F\nSB|Solomon Islands|OC|0051BA,FCD116,215B33\nTO|Tonga|OC|C10000,FFFFFF\nTV|Tuvalu|OC|012169,FFCE00,5B97B1\nVU|Vanuatu|OC|D21034,000000,009543,FDCE12\n";
    public static final Map<String, Countries.Country> ALL = new LinkedHashMap<>();
    private final VortexRealm plugin;
    private final File file;
    private final Map<UUID, String> chosen = new ConcurrentHashMap<>();
    private boolean dirty;

    public Countries(VortexRealm plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "countries.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        ConfigurationSection s = y.getConfigurationSection("players");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    this.chosen.put(UUID.fromString(k), s.getString(k, ""));
                } catch (IllegalArgumentException var6) {
                }
            }
        }
    }

    public void save() {
        if (this.dirty) {
            this.dirty = false;
            YamlConfiguration y = new YamlConfiguration();
            y.options().setHeader(List.of("Countries players picked (ISO codes)."));

            for (Entry<UUID, String> e : this.chosen.entrySet()) {
                y.set("players." + e.getKey(), e.getValue());
            }

            try {
                y.save(this.file);
            } catch (IOException var4) {
                this.plugin.getLogger().log(Level.WARNING, "Could not save countries.yml", (Throwable)var4);
            }
        }
    }

    public Countries.Country of(UUID u) {
        String c = this.chosen.get(u);
        return c == null ? null : ALL.get(c);
    }

    public boolean picked(UUID u) {
        return this.chosen.containsKey(u);
    }

    /** A country set by the network primary for a player (no forwarding). */
    public void setStored(UUID u, String code) {
        this.chosen.put(u, code == null ? "" : code.toUpperCase(Locale.ROOT));
        this.dirty = true;
        this.save();
    }

    public void set(Player p, String code) {
        if (code != null && !code.isBlank()) {
            this.chosen.put(p.getUniqueId(), code.toUpperCase(Locale.ROOT));
        } else {
            this.chosen.put(p.getUniqueId(), "");
        }

        this.dirty = true;
        this.save();
        if (this.plugin.netSync() != null) {
            this.plugin.netSync().forward("country", p.getUniqueId(), this.chosen.get(p.getUniqueId()));
        }

        try {
            this.plugin.boards().refreshNametag(p);
        } catch (RuntimeException var4) {
        }
    }

    public boolean setByCode(Player p, String code) {
        Countries.Country c = ALL.get(code.toUpperCase(Locale.ROOT));
        if (c == null) {
            for (Countries.Country x : ALL.values()) {
                if (x.name().equalsIgnoreCase(code)) {
                    c = x;
                }
            }
        }

        if (c == null) {
            return false;
        } else {
            this.set(p, c.code());
            p.sendMessage(Text.mm("<green>✔ Country set: <white>" + Text.esc(c.name()) + " ").append(this.flag(c, p)));
            return true;
        }
    }

    /** The player turned "Show My Flag" off (Settings → Privacy → Location). */
    public boolean flagHidden(UUID u) {
        net.vortexrealm.data.PvPProfile prof = this.plugin.profiles() == null ? null : this.plugin.profiles().getIfLoaded(u);
        return prof != null && !prof.on(net.vortexrealm.data.Setting.SHOW_FLAG);
    }

    public Component tag(UUID u) {
        Countries.Country c = this.flagHidden(u) ? null : this.of(u);
        return (Component)(c == null ? Component.empty() : Component.space().append(c.tag()));
    }

    /** The flag as its pack image (for viewers that have the pack), or the text tag when there is no image. */
    public Component glyphTag(UUID u) {
        Countries.Country c = this.flagHidden(u) ? null : this.of(u);
        if (c == null) {
            return Component.empty();
        }

        Component g = Glyphs.flag(c.code());
        return Component.space().append(g == null ? c.tag() : g.hoverEvent(HoverEvent.showText(Text.mm("<white>" + Text.esc(c.name())))));
    }

    public Component tag(UUID u, Player viewer) {
        Countries.Country c = this.flagHidden(u) ? null : this.of(u);
        return (Component)(c == null ? Component.empty() : Component.space().append(this.flag(c, viewer)));
    }

    public Component flag(Countries.Country c, Player viewer) {
        if (viewer != null && this.plugin.hasPack(viewer)) {
            Component g = Glyphs.flag(c.code());
            if (g != null) {
                return g.hoverEvent(HoverEvent.showText(Text.mm("<white>" + Text.esc(c.name()))));
            }
        }

        return c.tag();
    }

    private static DyeColor dye(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        DyeColor best = DyeColor.WHITE;
        double bestD = Double.MAX_VALUE;

        for (DyeColor d : DyeColor.values()) {
            int c = d.getColor().asRGB();
            double dr = (c >> 16 & 0xFF) - (rgb >> 16 & 0xFF);
            double dg = (c >> 8 & 0xFF) - (rgb >> 8 & 0xFF);
            double db = (c & 0xFF) - (rgb & 0xFF);
            double dist = dr * dr * 0.3 + dg * dg * 0.59 + db * db * 0.11;
            if (dist < bestD) {
                bestD = dist;
                best = d;
            }
        }

        return best;
    }

    public static ItemStack banner(Countries.Country c) {
        DyeColor base = dye(c.colors()[0]);
        Material m = Material.matchMaterial(base.name() + "_BANNER");
        ItemStack it = new ItemStack(m == null ? Material.WHITE_BANNER : m);
        if (c.colors().length > 1) {
            it.editMeta(BannerMeta.class, bm -> {
                bm.addPattern(new Pattern(dye(c.colors()[1]), PatternType.STRIPE_MIDDLE));
                if (c.colors().length > 2) {
                    bm.addPattern(new Pattern(dye(c.colors()[2]), PatternType.STRIPE_BOTTOM));
                }
            });
        }

        return it;
    }

    public void picker(Player p, Runnable after) {
        if (!ClientVersion.supportsDialogs(p)) {
            p.sendMessage(
                Text.mm("<gray>Type <white>/country <code></white> - e.g. <white>/country MA</white>, <white>/country US</white>, <white>/country FR</white>.")
            );
        } else {
            LobbyDialogs ui = this.plugin.dialogs().lobby();
            List<LobbyDialogs.Tile> tiles = new ArrayList<>();
            Material[] icons = new Material[]{
                Material.BLUE_BANNER, Material.GREEN_BANNER, Material.RED_BANNER, Material.WHITE_BANNER, Material.YELLOW_BANNER, Material.LIGHT_BLUE_BANNER
            };
            int i = 0;

            for (Entry<String, String> e : CONTINENTS.entrySet()) {
                long n = ALL.values().stream().filter(c -> c.continent().equals(e.getKey())).count();
                tiles.add(
                    new LobbyDialogs.Tile(
                        new ItemStack(icons[i++ % icons.length]),
                        Text.mm("<white><b>" + e.getValue()),
                        Text.mm("<gray>" + n + " countries"),
                        List.of(Text.mm("<white><b>" + e.getValue()), Component.empty(), Text.mm("<green>▶ Click to choose")),
                        () -> this.continent(p, e.getKey(), after)
                    )
                );
            }

            Countries.Country mine = this.of(p.getUniqueId());
            ui.screen(
                p,
                LobbyDialogs.title("Your Country", mine == null ? "Shown next to your name" : mine.name()),
                tiles,
                List.of(Text.mm("<gray>Your country shows next to your name in the tab list, on your nametag and in your profile.")),
                null,
                List.of(
                    ui.btn(
                        p,
                        "<aqua>Search",
                        "<gray>Type the name of your country",
                        () -> this.plugin.dialogs().text(p, Text.mm("<gold><b>Search country"), "Country name", "", 40, q -> this.search(p, q, after))
                    ),
                    ui.btn(p, "<gray>Don't show a country", null, () -> {
                        this.set(p, "");
                        p.closeDialog();
                        if (after != null) {
                            after.run();
                        }
                    })
                ),
                2,
                ui.close(p)
            );
        }
    }

    private void continent(Player p, String continent, Runnable after) {
        List<Countries.Country> list = new ArrayList<>();

        for (Countries.Country c : ALL.values()) {
            if (c.continent().equals(continent)) {
                list.add(c);
            }
        }

        list.sort(Comparator.comparing(Countries.Country::name));
        this.showList(p, CONTINENTS.get(continent), list, after);
    }

    private void search(Player p, String q, Runnable after) {
        String s = q.trim().toLowerCase(Locale.ROOT);
        List<Countries.Country> list = new ArrayList<>();

        for (Countries.Country c : ALL.values()) {
            if (c.name().toLowerCase(Locale.ROOT).contains(s) || c.code().equalsIgnoreCase(s)) {
                list.add(c);
            }
        }

        if (list.isEmpty()) {
            p.sendMessage(Text.mm("<red>No country matches '" + Text.esc(q) + "'."));
            this.picker(p, after);
        } else {
            this.showList(p, "Search: " + q, list, after);
        }
    }

    private void showList(Player p, String title, List<Countries.Country> list, Runnable after) {
        LobbyDialogs ui = this.plugin.dialogs().lobby();
        List<LobbyDialogs.Tile> tiles = new ArrayList<>();

        for (Countries.Country c : list) {
            tiles.add(
                new LobbyDialogs.Tile(
                    banner(c),
                    Text.mm("<white>" + Text.esc(c.name())),
                    this.flag(c, p),
                    List.of(Text.mm("<white><b>" + Text.esc(c.name())), c.tag(), Component.empty(), Text.mm("<green>▶ Click to pick")),
                    () -> {
                        this.set(p, c.code());
                        p.closeDialog();
                        p.sendMessage(Text.mm("<green>✔ Country set: <white>" + Text.esc(c.name()) + " ").append(this.flag(c, p)));
                        Snd.play(p, "block.note_block.pling", 0.6F, 1.5F);
                        if (after != null) {
                            after.run();
                        }
                    }
                )
            );
        }

        ui.screen(
            p,
            LobbyDialogs.title("Your Country", Text.esc(title)),
            tiles,
            List.of(),
            null,
            List.of(ui.btn(p, "<gray>« Continents", null, () -> this.picker(p, after))),
            1,
            ui.close(p)
        );
    }

    static {
        CONTINENTS.put("EU", "Europe");
        CONTINENTS.put("AF", "Africa");
        CONTINENTS.put("AS", "Asia");
        CONTINENTS.put("NA", "North America");
        CONTINENTS.put("SA", "South America");
        CONTINENTS.put("OC", "Oceania");

        for (String line : "DZ|Algeria|AF|006233,FFFFFF,D21034\nAO|Angola|AF|CC092F,000000,FFCB00\nBJ|Benin|AF|008751,FCD116,E8112D\nBW|Botswana|AF|75AADB,FFFFFF,000000\nBF|Burkina Faso|AF|EF2B2D,009E49,FCD116\nBI|Burundi|AF|CE1126,FFFFFF,1EB53A\nCV|Cape Verde|AF|003893,FFFFFF,CF2027\nCM|Cameroon|AF|007A5E,CE1126,FCD116\nCF|Central African Republic|AF|003082,FFFFFF,289728,FFCE00\nTD|Chad|AF|002664,FECB00,C60C30\nKM|Comoros|AF|3A75C4,FFC61E,CE1126\nCG|Congo|AF|009543,FBDE4A,DC241F\nCD|DR Congo|AF|007FFF,F7D618,CE1021\nCI|Côte d'Ivoire|AF|F77F00,FFFFFF,009E60\nDJ|Djibouti|AF|6AB2E7,FFFFFF,12AD2B\nEG|Egypt|AF|CE1126,FFFFFF,000000\nGQ|Equatorial Guinea|AF|3E9A00,FFFFFF,E32118\nER|Eritrea|AF|12AD2B,4189DD,EA0437\nSZ|Eswatini|AF|3E5EB9,FFD900,B10C0C\nET|Ethiopia|AF|078930,FCDD09,DA121A\nGA|Gabon|AF|009E60,FCD116,3A75C4\nGM|Gambia|AF|CE1126,0C1C8C,3A7728\nGH|Ghana|AF|CE1126,FCD116,006B3F\nGN|Guinea|AF|CE1126,FCD116,009460\nGW|Guinea-Bissau|AF|CE1126,FCD116,009E49\nKE|Kenya|AF|000000,BB0000,006600\nLS|Lesotho|AF|00209F,FFFFFF,009543\nLR|Liberia|AF|BF0A30,FFFFFF,002868\nLY|Libya|AF|E70013,000000,239E46\nMG|Madagascar|AF|FFFFFF,FC3D32,007E3A\nMW|Malawi|AF|000000,CE1126,339E35\nML|Mali|AF|14B53A,FCD116,CE1126\nMR|Mauritania|AF|D01C1F,006233,FFC400\nMU|Mauritius|AF|EA2839,1A206D,FFD500,00A551\nMA|Morocco|AF|C1272D,006233\nMZ|Mozambique|AF|009A44,000000,FCE100,D21034\nNA|Namibia|AF|003580,D21034,009543\nNE|Niger|AF|E05206,FFFFFF,0DB02B\nNG|Nigeria|AF|008751,FFFFFF,008751\nRW|Rwanda|AF|00A1DE,FAD201,20603D\nST|Sao Tome and Principe|AF|12AD2B,FFCE00,D21034\nSN|Senegal|AF|00853F,FDEF42,E31B23\nSC|Seychelles|AF|003F87,FCD856,D62828,007A3D\nSL|Sierra Leone|AF|1EB53A,FFFFFF,0072C6\nSO|Somalia|AF|4189DD,FFFFFF\nZA|South Africa|AF|007749,FFB81C,DE3831,002395\nSS|South Sudan|AF|000000,DA121A,078930\nSD|Sudan|AF|D21034,FFFFFF,000000,007229\nTZ|Tanzania|AF|1EB53A,FCD116,000000,00A3DD\nTG|Togo|AF|006A4E,FFCE00,D21034\nTN|Tunisia|AF|E70013,FFFFFF\nUG|Uganda|AF|000000,FCDC04,D90000\nZM|Zambia|AF|198A00,EF7D00,DE2010\nZW|Zimbabwe|AF|319208,FFD200,DE2010,000000\nEH|Western Sahara|AF|000000,FFFFFF,007A3D,C4111B\nAF|Afghanistan|AS|000000,D32011,007A36\nAM|Armenia|AS|D90012,0033A0,F2A800\nAZ|Azerbaijan|AS|0092BC,E4002B,00AF66\nBH|Bahrain|AS|CE1126,FFFFFF\nBD|Bangladesh|AS|006A4E,F42A41\nBT|Bhutan|AS|FFD520,FF4E12\nBN|Brunei|AS|F7E017,FFFFFF,000000\nKH|Cambodia|AS|032EA1,E00025\nCN|China|AS|EE1C25,FFFF00\nGE|Georgia|AS|FFFFFF,FF0000\nHK|Hong Kong|AS|DE2910,FFFFFF\nIN|India|AS|FF9933,FFFFFF,138808\nID|Indonesia|AS|FF0000,FFFFFF\nIR|Iran|AS|239F40,FFFFFF,DA0000\nIQ|Iraq|AS|CE1126,FFFFFF,000000\nIL|Israel|AS|0038B8,FFFFFF\nJP|Japan|AS|FFFFFF,BC002D\nJO|Jordan|AS|000000,FFFFFF,007A3D,CE1126\nKZ|Kazakhstan|AS|00AFCA,FEC50C\nKW|Kuwait|AS|007A3D,FFFFFF,CE1126,000000\nKG|Kyrgyzstan|AS|E8112D,FFEF00\nLA|Laos|AS|CE1126,002868\nLB|Lebanon|AS|ED1C24,FFFFFF,00A651\nMO|Macau|AS|00785E,FFFFFF\nMY|Malaysia|AS|CC0001,FFFFFF,010066,FFCC00\nMV|Maldives|AS|D21034,007E3A\nMN|Mongolia|AS|C4272F,015197,F9CF02\nMM|Myanmar|AS|FECB00,34B233,EA2839\nNP|Nepal|AS|DC143C,003893\nKP|North Korea|AS|024FA2,ED1C27\nOM|Oman|AS|FFFFFF,DB161B,008000\nPK|Pakistan|AS|01411C,FFFFFF\nPS|Palestine|AS|000000,FFFFFF,009736,EE2A35\nPH|Philippines|AS|0038A8,CE1126,FCD116\nQA|Qatar|AS|8A1538,FFFFFF\nSA|Saudi Arabia|AS|006C35,FFFFFF\nSG|Singapore|AS|EF3340,FFFFFF\nKR|South Korea|AS|FFFFFF,CD2E3A,0047A0\nLK|Sri Lanka|AS|FFBE29,8D153A,00534E\nSY|Syria|AS|CE1126,FFFFFF,000000\nTW|Taiwan|AS|FE0000,000095\nTJ|Tajikistan|AS|CC0000,FFFFFF,006600\nTH|Thailand|AS|A51931,F4F5F8,2D2A4A\nTL|Timor-Leste|AS|DC241F,FFC726,000000\nTR|Türkiye|AS|E30A17,FFFFFF\nTM|Turkmenistan|AS|00843D,FFFFFF\nAE|United Arab Emirates|AS|00732F,FFFFFF,000000,FF0000\nUZ|Uzbekistan|AS|0099B5,FFFFFF,1EB53A\nVN|Vietnam|AS|DA251D,FFFF00\nYE|Yemen|AS|CE1126,FFFFFF,000000\nAL|Albania|EU|E41E20,000000\nAD|Andorra|EU|10069F,FEDF00,D50032\nAT|Austria|EU|ED2939,FFFFFF\nBY|Belarus|EU|C8313E,4AA657\nBE|Belgium|EU|000000,FDDA24,EF3340\nBA|Bosnia and Herzegovina|EU|002395,FECB00\nBG|Bulgaria|EU|FFFFFF,00966E,D62612\nHR|Croatia|EU|FF0000,FFFFFF,171796\nCY|Cyprus|EU|FFFFFF,D57800\nCZ|Czechia|EU|FFFFFF,D7141A,11457E\nDK|Denmark|EU|C8102E,FFFFFF\nEE|Estonia|EU|0072CE,000000,FFFFFF\nFI|Finland|EU|FFFFFF,002F6C\nFR|France|EU|0055A4,FFFFFF,EF4135\nDE|Germany|EU|000000,DD0000,FFCE00\nGR|Greece|EU|0D5EAF,FFFFFF\nHU|Hungary|EU|CE2939,FFFFFF,477050\nIS|Iceland|EU|02529C,FFFFFF,DC1E35\nIE|Ireland|EU|169B62,FFFFFF,FF883E\nIT|Italy|EU|009246,FFFFFF,CE2B37\nXK|Kosovo|EU|244AA5,D0A650\nLV|Latvia|EU|9E3039,FFFFFF\nLI|Liechtenstein|EU|002B7F,CE1126\nLT|Lithuania|EU|FDB913,006A44,C1272D\nLU|Luxembourg|EU|ED2939,FFFFFF,00A1DE\nMT|Malta|EU|FFFFFF,CF142B\nMD|Moldova|EU|0046AE,FFD200,CC092F\nMC|Monaco|EU|CE1126,FFFFFF\nME|Montenegro|EU|C40308,D3AE3B\nNL|Netherlands|EU|AE1C28,FFFFFF,21468B\nMK|North Macedonia|EU|D20000,FFE600\nNO|Norway|EU|BA0C2F,FFFFFF,00205B\nPL|Poland|EU|FFFFFF,DC143C\nPT|Portugal|EU|006600,FF0000\nRO|Romania|EU|002B7F,FCD116,CE1126\nRU|Russia|EU|FFFFFF,0039A6,D52B1E\nSM|San Marino|EU|5EB6E4,FFFFFF\nRS|Serbia|EU|C6363C,0C4076,FFFFFF\nSK|Slovakia|EU|FFFFFF,0B4EA2,EE1C25\nSI|Slovenia|EU|FFFFFF,005DA4,ED1C24\nES|Spain|EU|AA151B,F1BF00\nSE|Sweden|EU|006AA7,FECC00\nCH|Switzerland|EU|DA291C,FFFFFF\nUA|Ukraine|EU|0057B7,FFD700\nGB|United Kingdom|EU|012169,FFFFFF,C8102E\nVA|Vatican City|EU|FFE000,FFFFFF\nAG|Antigua and Barbuda|NA|CE1126,000000,0072C6,FCD116\nBS|Bahamas|NA|00778B,FFC72C,000000\nBB|Barbados|NA|00267F,FFC726\nBZ|Belize|NA|003F87,CE1126\nCA|Canada|NA|FF0000,FFFFFF\nCR|Costa Rica|NA|002B7F,FFFFFF,CE1126\nCU|Cuba|NA|002A8F,FFFFFF,CF142B\nDM|Dominica|NA|006B3F,FCD116,000000\nDO|Dominican Republic|NA|002D62,FFFFFF,CE1126\nSV|El Salvador|NA|0047AB,FFFFFF\nGL|Greenland|NA|FFFFFF,D00C33\nGD|Grenada|NA|CE1126,FCD116,007A5E\nGT|Guatemala|NA|4997D0,FFFFFF\nHT|Haiti|NA|00209F,D21034\nHN|Honduras|NA|0073CF,FFFFFF\nJM|Jamaica|NA|009B3A,FED100,000000\nMX|Mexico|NA|006847,FFFFFF,CE1126\nNI|Nicaragua|NA|0067C6,FFFFFF\nPA|Panama|NA|FFFFFF,DA121A,072357\nPR|Puerto Rico|NA|ED0000,FFFFFF,0050F0\nKN|Saint Kitts and Nevis|NA|009E49,FCD116,000000,CE1126\nLC|Saint Lucia|NA|65CFFF,FFCE00,000000\nVC|Saint Vincent|NA|0072C6,FCD116,009E60\nTT|Trinidad and Tobago|NA|CE1126,FFFFFF,000000\nUS|United States|NA|B22234,FFFFFF,3C3B6E\nAR|Argentina|SA|74ACDF,FFFFFF,F6B40E\nBO|Bolivia|SA|D52B1E,F9E300,007934\nBR|Brazil|SA|009C3B,FFDF00,002776\nCL|Chile|SA|0039A6,FFFFFF,D52B1E\nCO|Colombia|SA|FCD116,003893,CE1126\nEC|Ecuador|SA|FFDD00,034EA2,ED1C24\nGY|Guyana|SA|009E49,FCD116,CE1126\nPY|Paraguay|SA|D52B1E,FFFFFF,0038A8\nPE|Peru|SA|D91023,FFFFFF\nSR|Suriname|SA|377E3F,FFFFFF,B40A2D,ECC81D\nUY|Uruguay|SA|FFFFFF,0038A8,FCD116\nVE|Venezuela|SA|FFCC00,00247D,CF142B\nAU|Australia|OC|012169,FFFFFF,E4002B\nFJ|Fiji|OC|68BFE5,002868,CE1126\nKI|Kiribati|OC|CE1126,FFD200,003F87\nMH|Marshall Islands|OC|003893,FFFFFF,DD7500\nFM|Micronesia|OC|75B2DD,FFFFFF\nNR|Nauru|OC|002B7F,FFC61E\nNZ|New Zealand|OC|00247D,FFFFFF,CC142B\nPW|Palau|OC|4AADD6,FFDE00\nPG|Papua New Guinea|OC|CE1126,000000,FCD116\nWS|Samoa|OC|CE1126,002B7F\nSB|Solomon Islands|OC|0051BA,FCD116,215B33\nTO|Tonga|OC|C10000,FFFFFF\nTV|Tuvalu|OC|012169,FFCE00,5B97B1\nVU|Vanuatu|OC|D21034,000000,009543,FDCE12\n"
            .split("\n")) {
            String[] p = line.trim().split("\\|");
            if (p.length >= 4) {
                String[] cols = p[3].split(",");

                for (int i = 0; i < cols.length; i++) {
                    cols[i] = "#" + cols[i].trim();
                }

                ALL.put(p[0], new Countries.Country(p[0], p[1], p[2], cols));
            }
        }
    }

    public record Country(String code, String name, String continent, String[] colors) {
        public Component tag() {
            return this.colors.length == 1
                ? Text.mm("<b><" + this.colors[0] + ">" + this.code + "</b>")
                : Text.mm("<b><gradient:" + String.join(":", this.colors) + ">" + this.code + "</gradient></b>");
        }
    }
}
