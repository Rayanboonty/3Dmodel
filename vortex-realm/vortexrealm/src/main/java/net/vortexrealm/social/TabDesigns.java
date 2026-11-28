package net.vortexrealm.social;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import org.bukkit.Material;

/**
 * Tab list designs. Each one is a whole look: header and footer, logo colours, the gradient of each mode, which list is
 * shown (the 4-column grid, one column, or the normal player list), the section titles and how a row looks. The admin
 * picks one in /pvpadmin -> Tab Design; Classic is the MCPVP look the plugin always had.
 */
public final class TabDesigns {
    private TabDesigns() {
    }

    /**
     * How the columns look. Section titles may use {count}; null = that section's Classic title.
     *
     * @param heads player heads on the rows (false = plain default heads)
     * @param flags country flags in front of names
     * @param dots status dots (online, away, busy)
     * @param ranks server rank prefix in front of names
     * @param stats leaderboard rows show tier and value ("Name · HT3 1,240")
     * @param ping real latency bars (false = full bars on every row)
     * @param spacer text of the empty row between sections
     */
    public record Style(Map<String, String> titles, boolean heads, boolean flags, boolean dots, boolean ranks, boolean stats, boolean ping, String spacer) {
        String title(String id) {
            return this.titles.get(id);
        }
    }

    public record Design(
        String id,
        String name,
        String desc,
        Material icon,
        List<String> header,
        List<String> footer,
        List<String> brandColors,
        double brandSpeed,
        Map<String, String[]> moods,
        boolean columns,
        String where,
        String sections,
        int sectionSize,
        TabDesigns.Style style,
        List<String> preview
    ) {
    }

    private static Map<String, String> titles(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();

        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }

        return m;
    }

    private static Map<String, String[]> moods(String from, String to) {
        Map<String, String[]> m = new LinkedHashMap<>();

        for (String k : List.of("PVP", "HUB", "SURVIVAL", "LIFESTEAL", "STRENGTH", "RANKED", "ECO")) {
            m.put(k, new String[]{from, to});
        }

        return m;
    }

    private static final TabDesigns.Style CLASSIC_STYLE = new TabDesigns.Style(Map.of(), true, true, true, true, false, true, "");

    public static final List<TabDesigns.Design> DESIGNS = List.of(
        new TabDesigns.Design(
            "CLASSIC",
            "Classic",
            "The MCPVP look: animated logo, 4 columns of friends, online players and leaderboards.",
            Material.DIAMOND_SWORD,
            List.of("", "<logo>", "<brand>", "<line>", "<#B9A89A>Playing <mode> <dark_gray>•</dark_gray> <#FFB547><online></#FFB547> <#B9A89A>online", ""),
            List.of(
                "",
                "<#B9A89A>Ping <#FF8A3D><ping>ms</#FF8A3D> <dark_gray>•</dark_gray> <#B9A89A>TPS <#FF8A3D><tps></#FF8A3D> <dark_gray>•</dark_gray> <#B9A89A>Here <#FF8A3D><here></#FF8A3D>",
                "<#FF7A1A><discord></#FF7A1A> <dark_gray>•</dark_gray> <#FFB547><ip></#FFB547>",
                "<line_short>",
                ""
            ),
            List.of("#3B0F7A", "#6A2BE0", "#9B5CFF", "#4FC3FF", "#8FF6FF"),
            1.0,
            null,
            true,
            "pvp",
            "staff,friends,creators,online,top,endgame,sword,spear,mace",
            8,
            CLASSIC_STYLE,
            List.of("Animated logo + gradient line", "Columns: Staff · Friends · Online · Top · kit boards", "Heads, flags, status dots, ranks")
        ),
        new TabDesigns.Design(
            "COMPETITIVE",
            "Competitive",
            "Practice-server style: dark slate, gold and aqua section bars, every board shows tier and Elo, and your own stats in the footer.",
            Material.GOLDEN_SWORD,
            List.of("", "<logo>", "<b><gradient:#FFD24A:#FF9F1C><server></gradient></b> <dark_gray>|</dark_gray> <#7FE3FF>Practice", "<dark_gray><st>                                        </st>", ""),
            List.of(
                "",
                "<#7FE3FF>Your Elo <white><my_elo></white> <dark_gray>|</dark_gray> <#7FE3FF>Tier <my_tier> <dark_gray>|</dark_gray> <#7FE3FF>Rank <white><my_rank></white>",
                "<gray>Matches <white><matches></white> <dark_gray>|</dark_gray> <gray>In queue <white><queued></white> <dark_gray>|</dark_gray> <gray>Ping <white><ping>ms</white>",
                "<#FFD24A><ip>",
                ""
            ),
            List.of("#B8860B", "#FFD24A", "#FFF1B0", "#FFD24A", "#B8860B"),
            0.6,
            moods("#FFD24A", "#FF9F1C"),
            true,
            "pvp",
            "online,top,sword,crystal,mace,nethpot,uhc,spear",
            10,
            new TabDesigns.Style(
                titles(
                    "online", "<#2B3445>▌</#2B3445><#7FE3FF><b>ONLINE</b> <gray>{count}",
                    "top", "<#2B3445>▌</#2B3445><#FFD24A><b>TOP ELO</b>",
                    "staff", "<#2B3445>▌</#2B3445><#55FF55><b>STAFF</b>",
                    "friends", "<#2B3445>▌</#2B3445><#9FA8FF><b>FRIENDS</b>",
                    "kit", "<#2B3445>▌</#2B3445><#FFD24A><b>{kit}</b>"
                ),
                true,
                false,
                false,
                true,
                true,
                true,
                ""
            ),
            List.of("Gold server name over a rule", "Columns: Online · Top Elo · kit boards with tier and Elo", "Footer: your Elo, tier and rank")
        ),
        new TabDesigns.Design(
            "NETWORK",
            "Network",
            "Big minigame-network style: a bold server name, the normal player list sorted by rank colour, and a store / Discord / IP footer.",
            Material.NETHER_STAR,
            List.of("", "<b><gradient:#FFF36B:#FFB300><server></gradient></b>", "<gray>You are playing on <white><ip></white>", ""),
            List.of(
                "",
                "<aqua>Discord <white><discord></white> <dark_gray>•</dark_gray> <yellow>Store <white>store.<ip></white>",
                "<gray><online> players online <dark_gray>•</dark_gray> <gray>ping <ping>ms",
                ""
            ),
            List.of("#FFB300", "#FFF36B", "#FFFFFF", "#FFF36B", "#FFB300"),
            0.4,
            moods("#FFF36B", "#FFB300"),
            false,
            "pvp",
            "online",
            8,
            CLASSIC_STYLE,
            List.of("Bold yellow server name", "Normal player list, ranks first", "Footer: Discord, store, players online")
        ),
        new TabDesigns.Design(
            "MINIMAL",
            "Minimal",
            "Clean SMP style: one column, names and ping only, no skins, flags or icons, one line of header and footer.",
            Material.PAPER,
            List.of("<gray><server> <dark_gray>·</dark_gray> <white><online> online"),
            List.of("<dark_gray><ip>"),
            List.of("#D0D0D0", "#FFFFFF", "#D0D0D0"),
            0.3,
            moods("#FFFFFF", "#BBBBBB"),
            true,
            "everywhere",
            "online",
            8,
            new TabDesigns.Style(titles("online", "<white>Players <dark_gray>{count}"), false, false, false, false, false, true, ""),
            List.of("One line: server · online", "One column of names with ping", "No skins, flags, dots or ranks")
        ),
        new TabDesigns.Design(
            "NEON",
            "Neon",
            "Arcade / cyber style: fast magenta-to-cyan gradients, glowing dividers and ★ ⚡ section icons.",
            Material.GLOW_INK_SAC,
            List.of("", "<logo>", "<brand>", "<line>", "<#FF4FD8>⚡</#FF4FD8> <mood:<mode>> <#FF4FD8>⚡</#FF4FD8> <#9AF7FF><online></#9AF7FF> <#C9A6FF>online", ""),
            List.of("", "<#C9A6FF>ping <#9AF7FF><ping>ms</#9AF7FF> <#FF4FD8>✦</#FF4FD8> tps <#9AF7FF><tps></#9AF7FF>", "<#FF4FD8><discord></#FF4FD8>", "<line_short>", ""),
            List.of("#FF2BD6", "#B44DFF", "#5A6BFF", "#24D8FF", "#7CFFF4"),
            2.2,
            moods("#FF4FD8", "#24D8FF"),
            true,
            "pvp",
            "staff,friends,online,top,sword,crystal",
            8,
            new TabDesigns.Style(
                titles(
                    "online", "<#24D8FF>★</#24D8FF> <b><gradient:#FF4FD8:#24D8FF>ONLINE</gradient></b> <#C9A6FF>{count}",
                    "top", "<#FFE04F>⚡</#FFE04F> <b><gradient:#FFE04F:#FF4FD8>TOP</gradient></b>",
                    "staff", "<#7CFFB0>✚</#7CFFB0> <b><gradient:#7CFFB0:#24D8FF>STAFF</gradient></b>",
                    "friends", "<#FF4FD8>♥</#FF4FD8> <b><gradient:#FF4FD8:#B44DFF>FRIENDS</gradient></b>",
                    "creators", "<#FF4FD8>▶</#FF4FD8> <b><gradient:#FF4FD8:#FF2B6B>CREATORS</gradient></b>",
                    "kit", "<#24D8FF>✦</#24D8FF> <b><gradient:#24D8FF:#B44DFF>{kit}</gradient></b>"
                ),
                true,
                true,
                true,
                true,
                false,
                true,
                "<#5A2B7A>· · · · · ·"
            ),
            List.of("Fast magenta→cyan animated logo", "Neon ★ ⚡ ♥ section titles", "Glowing dividers between sections")
        ),
        new TabDesigns.Design(
            "STATS_WALL",
            "Stats Wall",
            "League style: all four columns are leaderboards (Elo, wins, best streak, FFA kills) and the footer shows server activity.",
            Material.TARGET,
            List.of("", "<logo>", "<b><#E8ECF5><server></#E8ECF5></b> <#5B6B8C>LEADERBOARDS", "<#3A4560>━━━━━━━━━━━━━━━━━━━━━━━━", ""),
            List.of(
                "",
                "<#8E9CC0>Matches live <white><matches></white>  <#3A4560>│</#3A4560>  <#8E9CC0>Queued <white><queued></white>  <#3A4560>│</#3A4560>  <#8E9CC0>Online <white><online></white>  <#3A4560>│</#3A4560>  <#8E9CC0>TPS <white><tps></white>",
                "<#5B6B8C><ip>",
                ""
            ),
            List.of("#5B6B8C", "#E8ECF5", "#5B6B8C"),
            0.5,
            moods("#E8ECF5", "#8E9CC0"),
            true,
            "everywhere",
            "top,top_wins,top_streak,top_ffa",
            18,
            new TabDesigns.Style(
                titles(
                    "top", "<#FFD24A>◆ <b>ELO</b>",
                    "top_wins", "<#7CFF9B>◆ <b>WINS</b>",
                    "top_streak", "<#FF8A3D>◆ <b>BEST STREAK</b>",
                    "top_ffa", "<#FF5B5B>◆ <b>FFA KILLS</b>"
                ),
                true,
                false,
                false,
                false,
                true,
                false,
                ""
            ),
            List.of("4 columns of leaderboards: Elo · Wins · Streak · FFA kills", "Values next to every name", "Footer: live matches, queue, TPS")
        ),
        new TabDesigns.Design(
            "ROYAL",
            "Royal",
            "Purple and gold court style: crowned server name, gold dividers and ♛ section titles.",
            Material.GOLD_BLOCK,
            List.of("", "<logo>", "<b><gradient:#FFE27A:#D4A017>♛ <server> ♛</gradient></b>", "<line>", "<#C9A0FF>Playing <mode> <#FFD700>•</#FFD700> <white><online></white> <#C9A0FF>online", ""),
            List.of(
                "",
                "<#FFD700>♛ <#C9A0FF>Ping <white><ping>ms</white> <#FFD700>♛ <#C9A0FF>TPS <white><tps></white> <#FFD700>♛",
                "<#B48CFF><discord> <#5B3A8C>•</#5B3A8C> <#FFD700><ip>",
                "<line_short>",
                ""
            ),
            List.of("#4B1C7A", "#7B3FC4", "#FFD700", "#FFF1A8", "#FFD700"),
            0.8,
            moods("#FFD700", "#B48CFF"),
            true,
            "pvp",
            "staff,friends,online,top,sword,crystal,mace",
            8,
            new TabDesigns.Style(
                titles(
                    "online", "<#FFD700>♛</#FFD700> <b><gradient:#E0C3FF:#9B5CFF>COURT</gradient></b> <#C9A0FF>{count}",
                    "top", "<#FFD700>♛</#FFD700> <b><gradient:#FFE27A:#D4A017>NOBLES</gradient></b>",
                    "staff", "<#FFD700>♛</#FFD700> <b><gradient:#FFE27A:#FFB300>GUARDS</gradient></b>",
                    "friends", "<#FFD700>♛</#FFD700> <b><gradient:#E0C3FF:#B48CFF>ALLIES</gradient></b>",
                    "creators", "<#FFD700>♛</#FFD700> <b><gradient:#FF9AD5:#B48CFF>HERALDS</gradient></b>",
                    "kit", "<#FFD700>♛</#FFD700> <b><gradient:#FFE27A:#B48CFF>{kit}</gradient></b>"
                ),
                true,
                true,
                true,
                true,
                true,
                true,
                "<#5B3A8C>─── <#FFD700>♦</#FFD700> ───"
            ),
            List.of("Gold ♛ crowned server name", "Court · Nobles · Guards · Allies columns", "Purple and gold dividers, tier and Elo on boards")
        ),
        new TabDesigns.Design(
            "OCEAN",
            "Ocean",
            "Calm sea style: deep blue to aqua gradients, wave dividers and a clean list.",
            Material.HEART_OF_THE_SEA,
            List.of("", "<b><gradient:#00E5FF:#0072FF><server></gradient></b>", "<#4FC3F7>≈≈≈≈≈≈≈≈≈≈≈≈≈≈≈≈≈≈≈≈", "<#B3E5FC><mode_name> <#4FC3F7>•</#4FC3F7> <white><online></white> <#B3E5FC>players", ""),
            List.of(
                "",
                "<#4FC3F7>≈ <#B3E5FC>ping <white><ping>ms</white> <#4FC3F7>≈ <#B3E5FC>tps <white><tps></white> <#4FC3F7>≈",
                "<#80DEEA><ip> <#1E6FA8>•</#1E6FA8> <#80DEEA><discord>",
                ""
            ),
            List.of("#003B73", "#0074B7", "#00B4D8", "#90E0EF", "#CAF0F8"),
            0.7,
            moods("#00E5FF", "#0072FF"),
            true,
            "pvp",
            "friends,online,top,sword,crystal,nethpot",
            8,
            new TabDesigns.Style(
                titles(
                    "online", "<#00E5FF>◆</#00E5FF> <b><gradient:#90E0EF:#00B4D8>ON BOARD</gradient></b> <#80DEEA>{count}",
                    "top", "<#00E5FF>◆</#00E5FF> <b><gradient:#CAF0F8:#00B4D8>CAPTAINS</gradient></b>",
                    "staff", "<#00E5FF>◆</#00E5FF> <b><gradient:#90E0EF:#0077B6>CREW</gradient></b>",
                    "friends", "<#00E5FF>◆</#00E5FF> <b><gradient:#90E0EF:#48CAE4>FLEET</gradient></b>",
                    "kit", "<#00E5FF>◆</#00E5FF> <b><gradient:#CAF0F8:#0096C7>{kit}</gradient></b>"
                ),
                true,
                false,
                true,
                true,
                false,
                true,
                "<#1E6FA8>≈ ≈ ≈ ≈ ≈ ≈"
            ),
            List.of("Aqua gradient title over a wave line", "On Board · Captains · Fleet columns", "Wave dividers, no flags")
        ),
        new TabDesigns.Design(
            "RETRO_ARCADE",
            "Retro Arcade",
            "8-bit arcade style: blocky ▓▒░ banner, bright primary colours, 1UP / HI-SCORE columns and an insert-coin footer.",
            Material.JUKEBOX,
            List.of("", "<#FF2E2E>▓▒░ <b><#FFE600><server></#FFE600></b> <#FF2E2E>░▒▓", "<#00E5FF>■ ■ ■ <white><mode_name></white> ■ ■ ■", "<#FFE600>PLAYERS <white><online></white>", ""),
            List.of(
                "",
                "<#FF2E2E>PING <white><ping></white>  <#00E5FF>TPS <white><tps></white>  <#7CFF4F>LIVE <white><matches></white>",
                "<#FFE600><b>INSERT COIN</b> <white>▶ <ip>",
                ""
            ),
            List.of("#FF2E2E", "#FFE600", "#00E5FF", "#7CFF4F"),
            2.6,
            moods("#FFE600", "#FF2E2E"),
            true,
            "pvp",
            "online,top,sword,crystal,mace,uhc",
            8,
            new TabDesigns.Style(
                titles(
                    "online", "<#FF2E2E>■</#FF2E2E> <#FFE600><b>1UP</b></#FFE600> <white>{count}",
                    "top", "<#00E5FF>■</#00E5FF> <#FFE600><b>HI-SCORE</b>",
                    "staff", "<#7CFF4F>■</#7CFF4F> <#7CFF4F><b>REFS</b>",
                    "friends", "<#FF7AF5>■</#FF7AF5> <#FF7AF5><b>2UP</b>",
                    "kit", "<#00E5FF>■</#00E5FF> <#FFE600><b>{kit}</b>"
                ),
                true,
                false,
                true,
                false,
                true,
                true,
                "<#3A3A3A>░░░░░░░░"
            ),
            List.of("▓▒░ blocky banner in yellow and red", "1UP · HI-SCORE columns with scores", "INSERT COIN footer")
        ),
        new TabDesigns.Design(
            "MONO_PRO",
            "Monochrome Pro",
            "Pro / esports style in white and grey: thin rules, your stats first, no colours or icons.",
            Material.QUARTZ_BLOCK,
            List.of("", "<white><b><server></b>", "<dark_gray>────────────────────", "<gray><mode_name> <dark_gray>·</dark_gray> <white><online></white> <gray>online", ""),
            List.of(
                "",
                "<gray>Elo <white><my_elo></white> <dark_gray>·</dark_gray> <gray>Tier <white><my_tier></white> <dark_gray>·</dark_gray> <gray>Rank <white><my_rank></white>",
                "<dark_gray>Ping <gray><ping>ms</gray> · TPS <gray><tps></gray> · <ip>",
                ""
            ),
            List.of("#8A8A8A", "#FFFFFF", "#8A8A8A"),
            0.4,
            moods("#FFFFFF", "#9E9E9E"),
            true,
            "pvp",
            "online,top,sword,crystal",
            10,
            new TabDesigns.Style(
                titles(
                    "online", "<white><b>ONLINE</b> <dark_gray>{count}",
                    "top", "<white><b>TOP ELO</b>",
                    "staff", "<white><b>STAFF</b>",
                    "friends", "<white><b>FRIENDS</b>",
                    "kit", "<white><b>{kit}</b>"
                ),
                true,
                false,
                false,
                false,
                true,
                true,
                "<dark_gray>──────"
            ),
            List.of("White title over a thin grey rule", "Your Elo, tier and rank first in the footer", "No icons, flags or colours")
        ),
        new TabDesigns.Design(
            "AUTUMN",
            "Autumn",
            "Warm seasonal style: orange, red and amber gradients with ✿ leaves and a cosy footer.",
            Material.PUMPKIN,
            List.of("", "<logo>", "<b><gradient:#FFB347:#FF6B35:#C1121F><server></gradient></b>", "<line>", "<#F4A261>✿ <mode> <#F4A261>✿ <#FFD8A8><online></#FFD8A8> <#F4A261>online", ""),
            List.of(
                "",
                "<#E76F51>✿ <#FFD8A8>Ping <white><ping>ms</white> <#E76F51>✿ <#FFD8A8>Here <white><here></white> <#E76F51>✿",
                "<#F4A261><discord> <#8C3B1E>•</#8C3B1E> <#FFB347><ip>",
                "<line_short>",
                ""
            ),
            List.of("#6A040F", "#C1121F", "#FF6B35", "#FFB347", "#FFE0A3"),
            1.0,
            moods("#FFB347", "#C1121F"),
            true,
            "pvp",
            "staff,friends,online,top,sword,uhc,nethpot",
            8,
            new TabDesigns.Style(
                titles(
                    "online", "<#FF6B35>✿</#FF6B35> <b><gradient:#FFD8A8:#FF6B35>HARVEST</gradient></b> <#F4A261>{count}",
                    "top", "<#FFB347>✿</#FFB347> <b><gradient:#FFE0A3:#FFB347>GOLDEN LEAVES</gradient></b>",
                    "staff", "<#C1121F>✿</#C1121F> <b><gradient:#FF8A65:#C1121F>KEEPERS</gradient></b>",
                    "friends", "<#F4A261>✿</#F4A261> <b><gradient:#FFD8A8:#E76F51>HEARTH</gradient></b>",
                    "creators", "<#E76F51>✿</#E76F51> <b><gradient:#FFB347:#E76F51>STORYTELLERS</gradient></b>",
                    "kit", "<#FF6B35>✿</#FF6B35> <b><gradient:#FFE0A3:#E76F51>{kit}</gradient></b>"
                ),
                true,
                true,
                true,
                true,
                false,
                true,
                "<#8C3B1E>✿ · ✿ · ✿"
            ),
            List.of("Orange-to-red gradient title with ✿", "Harvest · Golden Leaves · Hearth columns", "Warm dividers and a cosy footer")
        )
    );

    public static TabDesigns.Design get(String id) {
        if (id != null) {
            for (TabDesigns.Design d : DESIGNS) {
                if (d.id().equalsIgnoreCase(id)) {
                    return d;
                }
            }
        }

        return DESIGNS.get(0);
    }

    /** The design the server uses (tab.design). */
    public static TabDesigns.Design active() {
        return get(Cfg.s(Opt.TAB_DESIGN).toUpperCase(Locale.ROOT));
    }
}
