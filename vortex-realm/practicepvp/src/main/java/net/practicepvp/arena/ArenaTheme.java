package net.practicepvp.arena;

import java.util.Locale;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.Biome;

public enum ArenaTheme {
    PLAINS("Plains", Material.GRASS_BLOCK, Biome.PLAINS, 3.0, 0, VanillaTerrain.Spec.overworld(80, 0.05, 0, 24, 0.08, Biome.PLAINS, Biome.SUNFLOWER_PLAINS)),
    FLATLANDS("Crystal Flats", Material.OBSIDIAN, Biome.PLAINS, 1.3, 0, 1.3),
    BADLANDS(
        "Badlands Mountains",
        Material.TERRACOTTA,
        Biome.BADLANDS,
        2.5,
        24,
        // the hilly badlands (hoodoos and wooded plateaus); the flat red badlands are "Badlands Red Desert"
        VanillaTerrain.Spec.overworld(90, 0.04, 20, 80, 0.08, Biome.ERODED_BADLANDS, Biome.WOODED_BADLANDS)
    ),
    BADLANDS_DESERT("Badlands Red Desert", Material.RED_SAND, Biome.BADLANDS, 2.0, 6, VanillaTerrain.Spec.overworld(80, 0.03, 0, 22, 0.03, Biome.BADLANDS)),
    DESERT("Desert", Material.SAND, Biome.DESERT, 4.0, 6, VanillaTerrain.Spec.overworld(80, 0.04, 0, 22, 0.02, Biome.DESERT)),
    SNOWY(
        "Snowy Plains",
        Material.SNOW_BLOCK,
        Biome.SNOWY_PLAINS,
        2.5,
        0,
        VanillaTerrain.Spec.overworld(80, 0.08, 0, 22, 0.1, Biome.SNOWY_PLAINS)
    ),
    CHERRY("Cherry Grove", Material.CHERRY_LEAVES, Biome.CHERRY_GROVE, 3.5, 8, VanillaTerrain.Spec.overworld(140, 0.04, 0, 45, 0.3, Biome.CHERRY_GROVE)),
    PALE("Pale Garden", Material.PALE_MOSS_BLOCK, Biome.PALE_GARDEN, 2.5, 0, VanillaTerrain.Spec.overworld(80, 0.05, 0, 28, 0.5, Biome.PALE_GARDEN)),
    CRIMSON(
        "Crimson Forest",
        Material.CRIMSON_NYLIUM,
        Biome.CRIMSON_FOREST,
        3.0,
        12,
        new VanillaTerrain.Spec(VanillaTerrain.Dim.NETHER, Set.of(Biome.CRIMSON_FOREST), 64, 0.8, 0.03, 0, 30, 0.35, false)
    ),
    END("The End Island", Material.END_STONE, Biome.END_HIGHLANDS, 2.0, 0, new VanillaTerrain.Spec(VanillaTerrain.Dim.END, Set.of(Biome.THE_END), 64, 0.5, 0.0, 0, 70, 1.0, true)),
    END_OUTER(
        "Outer End Islands",
        Material.CHORUS_FLOWER,
        Biome.END_HIGHLANDS,
        2.0,
        0,
        new VanillaTerrain.Spec(VanillaTerrain.Dim.END, Set.of(Biome.END_HIGHLANDS, Biome.END_MIDLANDS), 64, 0.5, 0.0, 0, 45, 1.0, false)
    ),
    MUSHROOM("Mushroom Fields", Material.MYCELIUM, Biome.MUSHROOM_FIELDS, 2.5, 0, VanillaTerrain.Spec.overworld(70, 0.45, 0, 24, 0.2, Biome.MUSHROOM_FIELDS)),
    COLOSSEUM_DESERT(ColosseumDesign.Style.DESERT, Material.CHISELED_SANDSTONE, Biome.DESERT),
    COLOSSEUM_TEMPLE(ColosseumDesign.Style.TEMPLE, Material.MOSSY_STONE_BRICKS, Biome.JUNGLE),
    COLOSSEUM_SPIRES(ColosseumDesign.Style.SPIRES, Material.TUFF_BRICKS, Biome.PLAINS),
    COLOSSEUM_NETHER(ColosseumDesign.Style.NETHER, Material.RED_NETHER_BRICKS, Biome.NETHER_WASTES),
    COLOSSEUM_FROST(ColosseumDesign.Style.FROST, Material.PACKED_ICE, Biome.SNOWY_PLAINS),
    COLOSSEUM_CHERRY(ColosseumDesign.Style.CHERRY, Material.CHERRY_LEAVES, Biome.CHERRY_GROVE),
    COLOSSEUM_GLADIATOR(ColosseumDesign.Style.GLADIATOR, Material.CUT_SANDSTONE, Biome.DESERT),
    GRAND_COLOSSEUM(ColosseumDesign.Style.GRAND, Material.MOSS_BLOCK, Biome.PLAINS),
    CHAMPIONS_COLOSSEUM(ColosseumDesign.Style.CHAMPIONS, Material.QUARTZ_PILLAR, Biome.PLAINS),
    DESERT_PALACE(net.practicepvp.build.ShowArenaDesign.Style.PALACE, Material.ORANGE_TERRACOTTA, Biome.DESERT),
    OLYMPUS(net.practicepvp.build.ShowArenaDesign.Style.OLYMPUS, Material.QUARTZ_PILLAR, Biome.PLAINS),
    TIMBER_FORT(net.practicepvp.build.ShowArenaDesign.Style.TIMBER_FORT, Material.STRIPPED_DARK_OAK_LOG, Biome.OLD_GROWTH_SPRUCE_TAIGA),
    MOLE_MINES(net.practicepvp.build.ShowArenaDesign.Style.MOLE_MINES, Material.IRON_PICKAXE, Biome.DRIPSTONE_CAVES),
    OPEN_SAKURA(OpenArenaDesign.Style.SAKURA, Material.CHERRY_LEAVES),
    OPEN_RUINS(OpenArenaDesign.Style.RUINS, Material.MOSSY_STONE_BRICKS),
    OPEN_OASIS(OpenArenaDesign.Style.OASIS, Material.CUT_SANDSTONE),
    OPEN_FROZEN(OpenArenaDesign.Style.FROZEN, Material.PACKED_ICE),
    OPEN_VOLCANO(OpenArenaDesign.Style.VOLCANO, Material.MAGMA_BLOCK),
    OPEN_DEEP_DARK(OpenArenaDesign.Style.DEEP_DARK, Material.SCULK),
    OPEN_MUSHROOM(OpenArenaDesign.Style.MUSHROOM, Material.RED_MUSHROOM_BLOCK),
    OPEN_SKY_CASTLE(OpenArenaDesign.Style.SKY_CASTLE, Material.STONE_BRICKS),
    DUEL_TEMPLE(net.practicepvp.build.DuelArenaDesign.Style.TEMPLE, Material.MOSSY_STONE_BRICKS, Biome.JUNGLE),
    DUEL_SAKURA(net.practicepvp.build.DuelArenaDesign.Style.SAKURA, Material.CHERRY_SAPLING, Biome.CHERRY_GROVE),
    DUEL_BASTION(net.practicepvp.build.DuelArenaDesign.Style.BASTION, Material.GILDED_BLACKSTONE, Biome.CRIMSON_FOREST),
    DUEL_HARBOR(net.practicepvp.build.DuelArenaDesign.Style.HARBOR, Material.PACKED_ICE, Biome.SNOWY_PLAINS),
    DUEL_BAZAAR(net.practicepvp.build.DuelArenaDesign.Style.BAZAAR, Material.CHISELED_SANDSTONE, Biome.DESERT),
    DUEL_COURTYARD(net.practicepvp.build.DuelArenaDesign.Style.COURTYARD, Material.STONE_BRICKS, Biome.PLAINS),
    DUEL_MUSHROOM(net.practicepvp.build.DuelArenaDesign.Style.MUSHROOM, Material.RED_MUSHROOM_BLOCK, Biome.MUSHROOM_FIELDS),
    DUEL_CITADEL(net.practicepvp.build.DuelArenaDesign.Style.CITADEL, Material.PURPUR_BLOCK, Biome.THE_END),
    UHC_MEADOW(net.practicepvp.build.DuelArenaDesign.Style.UHC_MEADOW, Material.OAK_SAPLING, Biome.MEADOW),
    UHC_CANYON(net.practicepvp.build.DuelArenaDesign.Style.UHC_CANYON, Material.RED_SAND, Biome.BADLANDS),
    UHC_TAIGA(net.practicepvp.build.DuelArenaDesign.Style.UHC_TAIGA, Material.SPRUCE_SAPLING, Biome.TAIGA),
    UHC_RUINS(net.practicepvp.build.DuelArenaDesign.Style.UHC_RUINS, Material.CRACKED_STONE_BRICKS, Biome.PLAINS),
    TRAINING_GROUNDS(net.practicepvp.build.DuelArenaDesign.Style.TRAINING, Material.TARGET, Biome.PLAINS),
    // 3.10.0: the court arenas (detailed builds round a flat floor) replace the old colosseums and show arenas
    ROYAL_COURTYARD(net.practicepvp.build.CourtArenaDesign.Style.ROYAL_COURTYARD, Material.CHISELED_STONE_BRICKS, Biome.PLAINS),
    FROSTHOLD(net.practicepvp.build.CourtArenaDesign.Style.FROSTHOLD, Material.BLUE_ICE, Biome.SNOWY_PLAINS),
    SULTAN_PALACE(net.practicepvp.build.CourtArenaDesign.Style.SULTAN_PALACE, Material.GOLD_BLOCK, Biome.DESERT),
    TITAN_COLOSSEUM(net.practicepvp.build.CourtArenaDesign.Style.TITAN_COLOSSEUM, Material.SMOOTH_SANDSTONE, Biome.PLAINS),
    HIGHLAND_PLAZA(net.practicepvp.build.CourtArenaDesign.Style.HIGHLAND_PLAZA, Material.SPRUCE_SAPLING, Biome.TAIGA),
    SAKURA_TERRACE(net.practicepvp.build.CourtArenaDesign.Style.SAKURA_TERRACE, Material.PINK_PETALS, Biome.CHERRY_GROVE),
    EMBER_SANDS(net.practicepvp.build.CourtArenaDesign.Style.EMBER_SANDS, Material.RED_NETHER_BRICKS, Biome.DESERT),
    GOTHIC_HARBOR(net.practicepvp.build.CourtArenaDesign.Style.GOTHIC_HARBOR, Material.PURPLE_STAINED_GLASS, Biome.PLAINS),
    // 3.11.0
    TROPICAL_BEACH(net.practicepvp.build.CourtArenaDesign.Style.TROPICAL_BEACH, Material.SAND, Biome.BEACH),
    JUNGLE_TEMPLE(net.practicepvp.build.CourtArenaDesign.Style.JUNGLE_TEMPLE, Material.MOSSY_STONE_BRICKS, Biome.JUNGLE),
    VOLCANO_FORGE(net.practicepvp.build.CourtArenaDesign.Style.VOLCANO_FORGE, Material.MAGMA_BLOCK, Biome.BASALT_DELTAS),
    PIRATE_COVE(net.practicepvp.build.CourtArenaDesign.Style.PIRATE_COVE, Material.BARREL, Biome.BEACH),
    PHARAOH_TOMB(net.practicepvp.build.CourtArenaDesign.Style.PHARAOH_TOMB, Material.CHISELED_SANDSTONE, Biome.DESERT),
    // 3.15.0: the pro arenas, next to the court arenas and colosseums above (those stay). Only the signature arena carries
    // the server's name.
    SIGNATURE_ARENA(net.practicepvp.build.ProArenaDesign.Theme.MEDIEVAL, net.practicepvp.Edition.NAME + " Arena", Material.RED_BANNER, Biome.PLAINS),
    FROSTFANG_BASTION(net.practicepvp.build.ProArenaDesign.Theme.FROST, "Frostfang Bastion", Material.SNOW_BLOCK, Biome.SNOWY_PLAINS),
    SUNSPIRE_PALACE(net.practicepvp.build.ProArenaDesign.Theme.DESERT, "Sunspire Palace", Material.LIGHT_BLUE_GLAZED_TERRACOTTA, Biome.DESERT),
    STONEWATCH_KEEP(net.practicepvp.build.ProArenaDesign.Theme.HIGHLAND, "Stonewatch Keep", Material.MOSSY_COBBLESTONE, Biome.TAIGA),
    BLOSSOM_COURT(net.practicepvp.build.ProArenaDesign.Theme.SAKURA, "Blossom Court", Material.CHERRY_LOG, Biome.CHERRY_GROVE),
    EMBER_CITADEL(net.practicepvp.build.ProArenaDesign.Theme.EMBER, "Ember Citadel", Material.CRIMSON_STEM, Biome.BADLANDS),
    NIGHTSPIRE_ABBEY(net.practicepvp.build.ProArenaDesign.Theme.GOTHIC, "Nightspire Abbey", Material.DEEPSLATE_BRICKS, Biome.DARK_FOREST),
    PALMSHORE_COURT(net.practicepvp.build.ProArenaDesign.Theme.TROPICAL, "Palmshore Court", Material.BAMBOO_MOSAIC, Biome.BEACH),
    JADE_TEMPLE(net.practicepvp.build.ProArenaDesign.Theme.JUNGLE, "Jade Temple", Material.OXIDIZED_COPPER, Biome.JUNGLE),
    OBSIDIAN_FORGE(net.practicepvp.build.ProArenaDesign.Theme.VOLCANO, "Obsidian Forge", Material.GILDED_BLACKSTONE, Biome.BASALT_DELTAS),
    CORSAIR_WHARF(net.practicepvp.build.ProArenaDesign.Theme.HARBOR, "Corsair's Wharf", Material.SPRUCE_PLANKS, Biome.BEACH),
    GOLDEN_DYNASTY(net.practicepvp.build.ProArenaDesign.Theme.PHARAOH, "Golden Dynasty", Material.LAPIS_BLOCK, Biome.DESERT),
    GLADIATOR_PIT(net.practicepvp.build.ProArenaDesign.Theme.GLADIATOR, "Gladiator Pit", Material.MUD_BRICKS, Biome.PLAINS),
    IMPERIAL_COLOSSEUM(net.practicepvp.build.ProArenaDesign.Theme.ROMAN, "Imperial Colosseum", Material.CHISELED_QUARTZ_BLOCK, Biome.PLAINS),
    HIGHPEAK_COLOSSEUM(net.practicepvp.build.ProArenaDesign.Theme.GRAND, "Highpeak Colosseum", Material.MACE, Biome.MEADOW),
    SUNKEN_ATLANTIS(net.practicepvp.build.ProArenaDesign.Theme.ATLANTIS, "Sunken Atlantis", Material.PRISMARINE_BRICKS, Biome.WARM_OCEAN),
    STARFALL_CITADEL(net.practicepvp.build.ProArenaDesign.Theme.STARFALL, "Starfall Citadel", Material.PURPUR_PILLAR, Biome.END_HIGHLANDS),
    CUSTOM("Custom", Material.STONE, null, 0.0, 0);

    public final double sizeScale;
    public final ColosseumDesign.Style colosseum;
    /** Non-null = an open designed arena (no walls). */
    public final OpenArenaDesign.Style open;
    /** Non-null = a show arena (oval floor, stands and a ring of buildings). */
    public final net.practicepvp.build.ShowArenaDesign.Style show;
    /** Non-null = a compact detailed duel arena (sword maps, or the UHC maps when its style is a building one). */
    public final net.practicepvp.build.DuelArenaDesign.Style duel;
    /** Non-null = a court arena (3.10.0): a flat floor inside a detailed build (castle, palace, colosseum, plaza, town). */
    public final net.practicepvp.build.CourtArenaDesign.Style court;
    /** Non-null = a pro arena (3.15.0): a layered build round a flat floor in a whole landscape. */
    public final net.practicepvp.build.ProArenaDesign.Theme pro;
    public final String display;
    public final Material icon;
    public final Biome biome;
    public final double amplitude;
    public final int mountains;
    /** Non-null = this map is cut from real vanilla terrain when arena.vanilla.enabled is on. */
    public final VanillaTerrain.Spec vanilla;

    private ArenaTheme(String display, Material icon, Biome biome, double amplitude, int mountains) {
        this(display, icon, biome, amplitude, mountains, 1.0);
    }

    private ArenaTheme(net.practicepvp.build.CourtArenaDesign.Style court, Material icon, Biome biome) {
        this.court = court;
        this.pro = null;
        this.colosseum = null;
        this.open = null;
        this.show = null;
        this.duel = null;
        this.sizeScale = 1.0;
        this.display = court.display;
        this.icon = icon;
        this.biome = biome;
        this.amplitude = 0.0;
        this.mountains = 0;
        this.vanilla = null;
    }

    private ArenaTheme(net.practicepvp.build.ProArenaDesign.Theme pro, String display, Material icon, Biome biome) {
        this.pro = pro;
        this.court = null;
        this.colosseum = null;
        this.open = null;
        this.show = null;
        this.duel = null;
        this.sizeScale = 1.0;
        this.display = display;
        this.icon = icon;
        this.biome = biome;
        this.amplitude = 0.0;
        this.mountains = 0;
        this.vanilla = null;
    }

    private ArenaTheme(net.practicepvp.build.DuelArenaDesign.Style duel, Material icon, Biome biome) {
        this.court = null;
        this.pro = null;
        this.colosseum = null;
        this.open = null;
        this.show = null;
        this.duel = duel;
        this.sizeScale = 1.0;
        this.display = duel.display;
        this.icon = icon;
        this.biome = biome;
        this.amplitude = 0.0;
        this.mountains = 0;
        this.vanilla = null;
    }

    private ArenaTheme(net.practicepvp.build.ShowArenaDesign.Style show, Material icon, Biome biome) {
        this.court = null;
        this.pro = null;
        this.colosseum = null;
        this.open = null;
        this.show = show;
        this.sizeScale = 1.0;
        this.display = show.display;
        this.icon = icon;
        this.biome = biome;
        this.amplitude = 0.0;
        this.mountains = 0;
        this.vanilla = null;
        this.duel = null;
    }

    private ArenaTheme(OpenArenaDesign.Style open, Material icon) {
        this.court = null;
        this.pro = null;
        this.colosseum = null;
        this.open = open;
        this.show = null;
        this.sizeScale = 1.0;
        this.display = open.display;
        this.icon = icon;
        this.biome = open.biome;
        this.amplitude = 0.0;
        this.mountains = 0;
        this.vanilla = null;
        this.duel = null;
    }

    private ArenaTheme(String display, Material icon, Biome biome, double amplitude, int mountains, VanillaTerrain.Spec vanilla) {
        this.court = null;
        this.pro = null;
        this.colosseum = null;
        this.open = null;
        this.show = null;
        this.sizeScale = 1.0;
        this.display = display;
        this.icon = icon;
        this.biome = biome;
        this.amplitude = amplitude;
        this.mountains = mountains;
        this.vanilla = vanilla;
        this.duel = null;
    }

    private ArenaTheme(ColosseumDesign.Style style, Material icon, Biome biome) {
        this.court = null;
        this.pro = null;
        this.sizeScale = 1.0;
        this.colosseum = style;
        this.open = null;
        this.show = null;
        this.display = style.display;
        this.icon = icon;
        this.biome = biome;
        this.amplitude = 0.0;
        this.mountains = 0;
        this.vanilla = null;
        this.duel = null;
    }

    private ArenaTheme(String display, Material icon, Biome biome, double amplitude, int mountains, double sizeScale) {
        this.court = null;
        this.pro = null;
        this.colosseum = null;
        this.open = null;
        this.show = null;
        this.sizeScale = sizeScale;
        this.display = display;
        this.icon = icon;
        this.biome = biome;
        this.amplitude = amplitude;
        this.mountains = mountains;
        this.vanilla = null;
        this.duel = null;
    }

    /**
     * Arena looks that are no longer made (2.3.0: only the vanilla biome maps, the show arenas and the colosseums,
     * like MCPVP / PvPHQ): the open arenas and the small themed duel maps. Their old maps are removed on start.
     */
    public boolean retired() {
        return this.open != null
            || this.duel != null && this != TRAINING_GROUNDS
            // 3.10.0: the old colosseums and show arenas, replaced by the court arenas (the mace colosseum, the
            // tournament colosseum and Mole PvP's mines stay)
            || this.colosseum != null && this != GRAND_COLOSSEUM && this != CHAMPIONS_COLOSSEUM
            || this.show != null && this != MOLE_MINES;
    }

    /** True when arenas of this theme are vanilla terrain right now (the theme supports it and the option is on). */
    public boolean isVanilla() {
        return this.vanilla != null && VanillaTerrain.enabled();
    }

    public static ArenaTheme parse(String s) {
        if (s == null) {
            return CUSTOM;
        } else {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException var2) {
                return CUSTOM;
            }
        }
    }
}
