package net.vortexpvp.kit;

import net.vortexpvp.arena.Arena;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.util.Icons;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class Kit {
    public static final int SIZE = 41;
    public static final int BOOTS = 36;
    public static final int LEGGINGS = 37;
    public static final int CHESTPLATE = 38;
    public static final int HELMET = 39;
    public static final int OFFHAND = 40;
    public final String id;
    public String displayName;
    public String color = "<gold>";
    public String sprite;
    public String glyph = "⚔";
    public ItemStack icon = new ItemStack(Material.DIAMOND_SWORD);
    public KitCategory category = KitCategory.OTHER;
    public int order = 100;
    public boolean enabled = true;
    public boolean ranked = true;
    public boolean unranked = true;
    public boolean ffa = false;
    public boolean build = false;
    public boolean regen = true;
    public boolean hunger = true;
    public int roundsToWin = 0;
    public int buildHeight = -1;
    public int pearlCooldown = -1;
    public int windCooldown = -1;
    public int hitDelay = -1;
    public String arenaType = "AUTO";
    public String throwMode = "AUTO";
    public final Set<String> arenas = new LinkedHashSet<>();
    private String kindCache;
    private int kindVersion = -1;
    /** Sword-style kits (sword, pots, UHC): designed arenas only, shown as detailed 3D previews in the map vote. */
    private static final Set<String> DESIGNED_KITS = Set.of("sword", "pot", "nethpot", "nodebuff", "combo", "shield", "gapple", "uhc", "builduhc");
    /** Kits that play on the vanilla-terrain biome maps (MCPVP's biome selection). */
    private static final Set<String> VANILLA_KITS = Set.of("spear", "creeper", "cart", "spear_mace", "spearmace", "mace");
    public ItemStack[] contents = new ItemStack[41];
    public final List<PotionEffect> effects = new ArrayList<>();
    public final List<KitVote> votes = new ArrayList<>();
    public final List<String> description = new ArrayList<>();
    public int version = 1;

    public Kit(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public int rounds() {
        return this.roundsToWin > 0 ? this.roundsToWin : Cfg.i(Opt.ROUNDS_TO_WIN);
    }

    public int pearlCooldownTicks() {
        return this.pearlCooldown >= 0 ? this.pearlCooldown : Cfg.i(Opt.PEARL_COOLDOWN);
    }

    public int windCooldownTicks() {
        return this.windCooldown >= 0 ? this.windCooldown : Cfg.i(Opt.WIND_COOLDOWN);
    }

    public boolean throwBetweenRounds() {
        if ("ON".equalsIgnoreCase(this.throwMode)) {
            return true;
        } else if ("OFF".equalsIgnoreCase(this.throwMode)) {
            return false;
        } else {
            Opt.ThrowMode m = Cfg.e(Opt.ROUND_THROW_MODE, Opt.ThrowMode.class);

            return switch (m) {
                case ALL -> true;
                case OFF -> false;
                case SMART -> !this.build
                    && !"CRYSTAL".equals(this.arenaKind())
                    && this.countOf(Material.END_CRYSTAL) == 0
                    && this.countOf(Material.RESPAWN_ANCHOR) == 0
                    && this.countOf(Material.TNT) == 0;
            };
        }
    }

    public int hitDelayTicks() {
        return this.hitDelay >= 0 ? this.hitDelay : Cfg.i(Opt.HIT_DELAY);
    }

    /**
     * The arena families this kit plays on, comma separated: CRYSTAL (deep crystal maps), VANILLA (vanilla terrain maps),
     * DESIGNED (colosseums and designed arenas), MACE (the grand colosseum). Kits that aren't sword or vanilla kits get a mix.
     */
    public String arenaKind() {
        if (this.arenaType != null && !this.arenaType.equals("AUTO")) {
            return Arena.normPools(this.arenaType);
        } else if (this.kindCache != null && this.kindVersion == this.version) {
            return this.kindCache;
        } else {
            String k;
            if (this.countOf(Material.END_CRYSTAL) > 0 || this.countOf(Material.RESPAWN_ANCHOR) > 0) {
                k = "CRYSTAL";
            } else if (VANILLA_KITS.contains(this.id)) {
                k = "VANILLA";
            } else if (DESIGNED_KITS.contains(this.id)) {
                k = "DESIGNED";
            } else if (this.countOf(Material.MACE) > 0) {
                // other mace kits: the biome maps and the big grand colosseum
                k = "VANILLA,MACE";
            } else {
                k = "VANILLA,DESIGNED";
            }

            this.kindCache = k;
            this.kindVersion = this.version;
            return k;
        }
    }

    public Set<String> arenaPools() {
        return new LinkedHashSet<>(List.of(this.arenaKind().split(",")));
    }

    public boolean playsOn(String pool) {
        return this.arenaPools().contains(pool);
    }

    public int buildHeightOrDefault() {
        return this.buildHeight > 0 ? this.buildHeight : Cfg.i(Opt.BUILD_HEIGHT);
    }

    public String nameMM() {
        return this.color + Text.esc(this.displayName) + this.closeTag();
    }

    private String closeTag() {
        String c = this.color.trim();
        return c.startsWith("<") && c.endsWith(">") ? "</" + c.substring(1) : "<reset>";
    }

    public Component name() {
        return Text.mm(this.nameMM());
    }

    public Component iconComponent() {
        Component sprite = Icons.sprite(this.sprite);
        return sprite != null ? sprite : Text.mm(this.color + this.glyph + this.closeTag());
    }

    public Component label() {
        return this.iconComponent().append((Component)Component.space()).append(this.name());
    }

    public ItemStack iconStack() {
        return this.icon == null ? new ItemStack(Material.PAPER) : this.icon.clone();
    }

    public boolean hasContents() {
        for (ItemStack it : this.contents) {
            if (!Items.isEmpty(it)) {
                return true;
            }
        }

        return false;
    }

    public int countOf(Material m) {
        int c = 0;

        for (ItemStack it : this.contents) {
            if (!Items.isEmpty(it) && it.getType() == m) {
                c += it.getAmount();
            }
        }

        return c;
    }

    public void bumpVersion() {
        this.version++;
    }
}
