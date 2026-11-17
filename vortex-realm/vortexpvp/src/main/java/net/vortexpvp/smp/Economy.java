package net.vortexpvp.smp;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.smp.eco.EcoMenus;
import net.vortexpvp.smp.eco.VaultBridge;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

public final class Economy implements CommandExecutor, TabCompleter, Listener {
    private final VortexPvP plugin;
    private final ModeManager modes;
    private final File file;
    private final Map<Material, Double> sell = new EnumMap<>(Material.class);
    private final Map<String, Economy.Category> shop = new LinkedHashMap<>();
    private String symbol = "$";
    private double mobReward = 2.0;
    private double killStealPercent = 5.0;
    public double ahTax = 5.0;
    public int ahMaxListings = 8;
    public int ahHours = 48;
    /** auto | builtin | external (see VaultBridge). */
    private String provider = "auto";
    private VaultBridge vault;
    /** Modes whose worlds use the economy (/sell, /shop, kill rewards, shards). */
    private final Set<WorldMode> ecoModes = EnumSet.of(WorldMode.ECO);
    /** /pay at or above this asks for a confirmation click. */
    private double payConfirm = 100000.0;
    private int shardsPerKill = 1;
    private int minutesPerShard = 10;
    public final List<Economy.ShopItem> shardShop = new ArrayList<>();
    /** AFK zone of the Eco Spawn: shards every afkSeconds. */
    public int afkSeconds = 60;
    public int afkAmount = 1;
    /** No shards for killing the same player again within this many seconds (anti-farm). */
    private int killCooldown = 300;
    private final Map<String, Long> lastKillReward = new java.util.HashMap<>();
    private static org.bukkit.NamespacedKey spawnerKey;

    public Economy(VortexPvP plugin, ModeManager modes) {
        this.plugin = plugin;
        this.modes = modes;
        this.file = new File(plugin.getDataFolder(), "eco.yml");
        spawnerKey = new org.bukkit.NamespacedKey(plugin, "spawner_type");
    }

    /**
     * A spawner bought in the shard shop keeps its mob when placed: vanilla only applies a spawner item's data for
     * operators, so the type is set here from the item's tag.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawnerPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        ItemStack it = e.getItemInHand();
        if (spawnerKey == null || it.getType() != Material.SPAWNER || !it.hasItemMeta()) {
            return;
        }

        String type = it.getItemMeta().getPersistentDataContainer().get(spawnerKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (type != null && e.getBlockPlaced().getState() instanceof org.bukkit.block.CreatureSpawner cs) {
            try {
                cs.setSpawnedType(org.bukkit.entity.EntityType.valueOf(type));
                cs.update(true, false);
            } catch (IllegalArgumentException var5) {
            }
        }
    }

    public void load() {
        if (!this.file.exists()) {
            this.writeDefaults();
        }

        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        if (y.getInt("version", 1) < 3) {
            // DonutSMP-style additions: Vault provider, economy worlds, pay confirmation, shards
            YamlConfiguration d = defaults();

            for (String k : List.of("provider", "worlds", "pay-confirm-above", "shards")) {
                if (!y.contains(k)) {
                    y.set(k, d.get(k));
                }
            }

            if (y.getInt("version", 1) >= 2) {
                y.set("version", 3);
            }

            try {
                y.save(this.file);
            } catch (IOException var13) {
                this.plugin.getLogger().log(Level.WARNING, "Could not upgrade eco.yml", (Throwable)var13);
            }
        }

        boolean upgrade = y.getInt("version", 1) < 2;
        if (upgrade) {
            YamlConfiguration d = defaults();

            for (String k : List.of("shop", "auction-house")) {
                y.set(k, d.get(k));
            }

            ConfigurationSection ds = d.getConfigurationSection("sell");
            if (ds != null) {
                for (String k : ds.getKeys(false)) {
                    if (!y.contains("sell." + k)) {
                        y.set("sell." + k, ds.getDouble(k));
                    }
                }
            }

            y.set("version", 3);

            try {
                y.save(this.file);
            } catch (IOException var12) {
                this.plugin.getLogger().log(Level.WARNING, "Could not upgrade eco.yml", (Throwable)var12);
            }
        }

        if (y.getInt("version", 1) < 4) {
            // 1.6.0: many more sellable items and shop blocks, spawners and books in the shard shop, AFK shards.
            // Only what's missing is added: prices and items the admin changed stay as they are.
            YamlConfiguration d = defaults();
            ConfigurationSection ds = d.getConfigurationSection("sell");
            if (ds != null) {
                for (String k : ds.getKeys(false)) {
                    if (!y.contains("sell." + k)) {
                        y.set("sell." + k, ds.getDouble(k));
                    }
                }
            }

            ConfigurationSection dsh = d.getConfigurationSection("shop");
            if (dsh != null) {
                for (String id : dsh.getKeys(false)) {
                    if (!y.contains("shop." + id)) {
                        y.set("shop." + id, dsh.get(id));
                    } else {
                        List<String> have = new ArrayList<>(y.getStringList("shop." + id + ".items"));
                        java.util.Set<String> keys = new java.util.HashSet<>();
                        for (String line : have) {
                            keys.add(line.split(":")[0].toUpperCase(Locale.ROOT));
                        }

                        for (String line : dsh.getStringList(id + ".items")) {
                            if (keys.add(line.split(":")[0].toUpperCase(Locale.ROOT))) {
                                have.add(line);
                            }
                        }

                        y.set("shop." + id + ".items", have);
                    }
                }
            }

            List<String> shards = new ArrayList<>(y.getStringList("shards.shop"));
            java.util.Set<String> skeys = new java.util.HashSet<>();
            for (String line : shards) {
                skeys.add(line.split(":")[0].toUpperCase(Locale.ROOT));
            }

            for (String line : d.getStringList("shards.shop")) {
                if (skeys.add(line.split(":")[0].toUpperCase(Locale.ROOT))) {
                    shards.add(line);
                }
            }

            y.set("shards.shop", shards);
            if (y.getInt("shards.per-kill", 1) == 1) {
                y.set("shards.per-kill", d.getInt("shards.per-kill"));
            }

            for (String k : List.of("shards.kill-cooldown-seconds", "shards.afk-seconds", "shards.afk-amount")) {
                if (!y.contains(k)) {
                    y.set(k, d.get(k));
                }
            }

            y.set("version", 4);

            try {
                y.save(this.file);
            } catch (IOException var14) {
                this.plugin.getLogger().log(Level.WARNING, "Could not upgrade eco.yml", (Throwable)var14);
            }
        }

        this.symbol = y.getString("currency-symbol", "$");
        this.mobReward = y.getDouble("mob-kill-reward", 2.0);
        this.killStealPercent = y.getDouble("player-kill-steal-percent", 5.0);
        this.ahTax = y.getDouble("auction-house.tax-percent", 5.0);
        this.ahMaxListings = y.getInt("auction-house.max-listings", 8);
        this.ahHours = y.getInt("auction-house.listing-hours", 48);
        this.provider = y.getString("provider", "auto").toLowerCase(Locale.ROOT);
        this.ecoModes.clear();

        for (String m : y.getStringList("worlds")) {
            WorldMode wm = WorldMode.parse(m, null);
            if (wm != null && wm.smp()) {
                this.ecoModes.add(wm);
            }
        }

        if (this.ecoModes.isEmpty()) {
            this.ecoModes.add(WorldMode.ECO);
        }

        this.payConfirm = y.getDouble("pay-confirm-above", 100000.0);
        this.shardsPerKill = y.getInt("shards.per-kill", 1);
        this.minutesPerShard = Math.max(1, y.getInt("shards.minutes-per-shard", 10));
        this.killCooldown = Math.max(0, y.getInt("shards.kill-cooldown-seconds", 300));
        this.afkSeconds = Math.max(5, y.getInt("shards.afk-seconds", 60));
        this.afkAmount = Math.max(1, y.getInt("shards.afk-amount", 1));
        this.shardShop.clear();

        for (String line : y.getStringList("shards.shop")) {
            Economy.ShopItem it = Economy.ShopItem.parse(line);
            if (it != null) {
                this.shardShop.add(it);
            }
        }

        this.sell.clear();
        ConfigurationSection s = y.getConfigurationSection("sell");
        if (s != null) {
            for (String kx : s.getKeys(false)) {
                Material m = Material.matchMaterial(kx);
                if (m != null) {
                    this.sell.put(m, s.getDouble(kx));
                }
            }
        }

        this.shop.clear();
        ConfigurationSection sh = y.getConfigurationSection("shop");
        if (sh != null) {
            for (String id : sh.getKeys(false)) {
                Material icon = Material.matchMaterial(sh.getString(id + ".icon", "CHEST"));
                Economy.Category c = new Economy.Category(id, sh.getString(id + ".name", id), icon == null ? Material.CHEST : icon);

                for (String line : sh.getStringList(id + ".items")) {
                    Economy.ShopItem it = Economy.ShopItem.parse(line);
                    if (it != null) {
                        c.items.add(it);
                    }
                }

                this.shop.put(id, c);
            }
        }

        this.checkPrices();
    }

    /**
     * A sell price at or above what the shop asks for one item would print money (buy, sell, repeat): such prices
     * are lowered to 60% of the shop price.
     */
    private void checkPrices() {
        Map<Material, Double> cheapest = new EnumMap<>(Material.class);
        for (Economy.Category c : this.shop.values()) {
            for (Economy.ShopItem it : c.items) {
                if (it.potion == null && it.amount > 0 && it.price > 0.0) {
                    cheapest.merge(it.material, it.price / it.amount, Math::min);
                }
            }
        }

        List<String> fixed = new ArrayList<>();
        cheapest.forEach((m, unit) -> {
            Double sellAt = this.sell.get(m);
            if (sellAt != null && sellAt >= unit) {
                double v = Math.floor(unit * 0.6 * 100.0) / 100.0;
                if (v <= 0.0) {
                    this.sell.remove(m);
                } else {
                    this.sell.put(m, v);
                }

                fixed.add(m.name());
            }
        });
        if (!fixed.isEmpty()) {
            this.plugin.getLogger().warning("Sell prices at or above the shop price were lowered (buy-and-sell loop): " + String.join(", ", fixed));
            this.save();
        }
    }

    public void save() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        y.set("version", 4);
        y.set("currency-symbol", this.symbol);
        y.set("mob-kill-reward", this.mobReward);
        y.set("player-kill-steal-percent", this.killStealPercent);
        y.set("auction-house.tax-percent", this.ahTax);
        y.set("auction-house.max-listings", this.ahMaxListings);
        y.set("auction-house.listing-hours", this.ahHours);
        y.set("sell", null);
        this.sell.forEach((m, v) -> y.set("sell." + m.name(), v));
        y.set("shop", null);

        for (Economy.Category c : this.shop.values()) {
            String b = "shop." + c.id + ".";
            y.set(b + "name", c.name);
            y.set(b + "icon", c.icon.name());
            List<String> lines = new ArrayList<>();

            for (Economy.ShopItem it : c.items) {
                lines.add(it.serialize());
            }

            y.set(b + "items", lines);
        }

        try {
            y.save(this.file);
        } catch (IOException var8) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save eco.yml", (Throwable)var8);
        }
    }

    private void writeDefaults() {
        try {
            defaults().save(this.file);
        } catch (IOException var2) {
            this.plugin.getLogger().log(Level.WARNING, "Could not write eco.yml", (Throwable)var2);
        }
    }

    private static YamlConfiguration defaults() {
        YamlConfiguration y = new YamlConfiguration();
        y.options()
            .setHeader(
                List.of(
                    "Eco SMP economy (edit everything in game: /ecoadmin).",
                    "sell: price for ONE item. shop items: MATERIAL:amount:price (potions: SPLASH_POTION#STRONG_HARMING:1:90).",
                    "provider: auto = use another Vault economy if one is installed (ours is offered to Vault otherwise),",
                    "builtin = always ours (and other plugins use it through Vault), external = only another plugin's.",
                    "worlds: SMP modes that use the economy (ECO, SURVIVAL, LIFESTEAL, STRENGTH, RANKED).",
                    "shards: second currency for playing (minutes-per-shard) and kills (per-kill), spent in /shards."
                )
            );
        y.set("version", 4);
        y.set("provider", "auto");
        y.set("worlds", List.of("ECO"));
        y.set("pay-confirm-above", 100000.0);
        y.set("shards.per-kill", 3);
        y.set("shards.kill-cooldown-seconds", 300);
        y.set("shards.afk-seconds", 60);
        y.set("shards.afk-amount", 1);
        y.set("shards.minutes-per-shard", 10);
        y.set(
            "shards.shop",
            List.of(
                "SHULKER_BOX:1:40",
                "TOTEM_OF_UNDYING:1:60",
                "ENCHANTED_GOLDEN_APPLE:1:80",
                "EXPERIENCE_BOTTLE:64:25",
                "NETHERITE_UPGRADE_SMITHING_TEMPLATE:1:50",
                "SPONGE:8:30",
                "BEACON:1:150",
                "ELYTRA:1:400",
                "SPAWNER#PIG:1:150",
                "SPAWNER#COW:1:150",
                "SPAWNER#ZOMBIE:1:200",
                "SPAWNER#SKELETON:1:200",
                "SPAWNER#SPIDER:1:200",
                "SPAWNER#CREEPER:1:250",
                "SPAWNER#BLAZE:1:400",
                "SPAWNER#IRON_GOLEM:1:600",
                "ENCHANTED_BOOK#MENDING_1:1:80",
                "ENCHANTED_BOOK#UNBREAKING_3:1:40",
                "ENCHANTED_BOOK#PROTECTION_4:1:60",
                "ENCHANTED_BOOK#SHARPNESS_5:1:60",
                "ENCHANTED_BOOK#EFFICIENCY_5:1:50",
                "ENCHANTED_BOOK#FORTUNE_3:1:70"
            )
        );
        y.set("currency-symbol", "$");
        y.set("mob-kill-reward", 2.0);
        y.set("player-kill-steal-percent", 5.0);
        y.set("auction-house.tax-percent", 5.0);
        y.set("auction-house.max-listings", 8);
        y.set("auction-house.listing-hours", 48);
        Map<String, Double> prices = new LinkedHashMap<>();
        String[] sells = new String[]{
            "DIAMOND:25",
            "EMERALD:15",
            "GOLD_INGOT:6",
            "IRON_INGOT:4",
            "COPPER_INGOT:1",
            "NETHERITE_SCRAP:120",
            "ANCIENT_DEBRIS:110",
            "NETHERITE_INGOT:520",
            "COAL:1",
            "REDSTONE:0.5",
            "LAPIS_LAZULI:0.8",
            "QUARTZ:1",
            "AMETHYST_SHARD:1",
            "WHEAT:0.5",
            "CARROT:0.3",
            "POTATO:0.3",
            "BEETROOT:0.3",
            "SUGAR_CANE:0.4",
            "PUMPKIN:1.5",
            "MELON_SLICE:0.2",
            "CACTUS:0.4",
            "BAMBOO:0.1",
            "KELP:0.1",
            "COCOA_BEANS:0.4",
            "NETHER_WART:0.6",
            "OAK_LOG:0.6",
            "SPRUCE_LOG:0.6",
            "BIRCH_LOG:0.6",
            "JUNGLE_LOG:0.6",
            "ACACIA_LOG:0.6",
            "DARK_OAK_LOG:0.6",
            "CHERRY_LOG:0.6",
            "MANGROVE_LOG:0.6",
            "COBBLESTONE:0.05",
            "COBBLED_DEEPSLATE:0.05",
            "ROTTEN_FLESH:0.1",
            "BONE:0.3",
            "STRING:0.4",
            "SPIDER_EYE:0.5",
            "GUNPOWDER:1",
            "ENDER_PEARL:5",
            "BLAZE_ROD:6",
            "GHAST_TEAR:12",
            "SLIME_BALL:2",
            "LEATHER:1",
            "FEATHER:0.3",
            "EGG:0.2",
            "INK_SAC:0.4",
            "PRISMARINE_SHARD:0.8",
            "SHULKER_SHELL:60",
            "TOTEM_OF_UNDYING:150",
            "PHANTOM_MEMBRANE:3",
            "HONEY_BOTTLE:2",
            "DIAMOND_BLOCK:225",
            "EMERALD_BLOCK:135",
            "GOLD_BLOCK:54",
            "IRON_BLOCK:36",
            "COPPER_BLOCK:9",
            "NETHERITE_BLOCK:4680",
            "COAL_BLOCK:9",
            "REDSTONE_BLOCK:4.5",
            "LAPIS_BLOCK:7.2",
            "QUARTZ_BLOCK:1.5",
            "AMETHYST_BLOCK:2",
            "RAW_IRON:3",
            "RAW_GOLD:5",
            "RAW_COPPER:0.8",
            "RAW_IRON_BLOCK:27",
            "RAW_GOLD_BLOCK:45",
            "RAW_COPPER_BLOCK:7.2",
            "GOLD_NUGGET:0.6",
            "IRON_NUGGET:0.4",
            "CHARCOAL:0.8",
            "PALE_OAK_LOG:0.6",
            "CRIMSON_STEM:0.6",
            "WARPED_STEM:0.6",
            "BAMBOO_BLOCK:0.9",
            "MELON:1.8",
            "SWEET_BERRIES:0.3",
            "GLOW_BERRIES:0.4",
            "CHORUS_FRUIT:0.5",
            "BROWN_MUSHROOM:0.4",
            "RED_MUSHROOM:0.4",
            "APPLE:0.5",
            "HAY_BLOCK:4.5",
            "DRIED_KELP:0.15",
            "SEA_PICKLE:0.3",
            "VINE:0.1",
            "LILY_PAD:0.3",
            "CARVED_PUMPKIN:1.5",
            "HONEYCOMB:2",
            "BLAZE_POWDER:3",
            "MAGMA_CREAM:3",
            "RABBIT_HIDE:0.6",
            "RABBIT_FOOT:4",
            "PRISMARINE_CRYSTALS:1",
            "NAUTILUS_SHELL:20",
            "BREEZE_ROD:8",
            "WITHER_SKELETON_SKULL:250",
            "NETHER_STAR:2500",
            "DRAGON_BREATH:10",
            "TURTLE_SCUTE:6",
            "ARMADILLO_SCUTE:3",
            "BEEF:0.4",
            "PORKCHOP:0.4",
            "CHICKEN:0.3",
            "MUTTON:0.3",
            "RABBIT:0.3",
            "COD:0.3",
            "SALMON:0.4",
            "TROPICAL_FISH:0.6",
            "PUFFERFISH:0.6",
            "WHITE_WOOL:0.4",
            "ECHO_SHARD:40",
            "RESIN_CLUMP:1",
            "STONE:0.08",
            "DEEPSLATE:0.08",
            "ANDESITE:0.05",
            "DIORITE:0.05",
            "GRANITE:0.05",
            "TUFF:0.05",
            "CALCITE:0.1",
            "SAND:0.05",
            "RED_SAND:0.06",
            "GRAVEL:0.05",
            "FLINT:0.3",
            "CLAY_BALL:0.2",
            "OBSIDIAN:2",
            "NETHERRACK:0.02",
            "SOUL_SAND:0.1",
            "BLACKSTONE:0.06",
            "BASALT:0.06",
            "END_STONE:0.1",
            "ICE:0.3",
            "PACKED_ICE:1.5",
            "SNOWBALL:0.05",
            "GLOWSTONE_DUST:0.6"
        };

        for (String s : sells) {
            String[] p = s.split(":");
            prices.put(p[0], Double.parseDouble(p[1]));
        }

        prices.forEach((k, v) -> y.set("sell." + k, v));
        cat(
            y,
            "crystal",
            "<#C77DFF><b>Crystal PvP",
            "END_CRYSTAL",
            "END_CRYSTAL:1:40",
            "END_CRYSTAL:16:600",
            "OBSIDIAN:16:160",
            "OBSIDIAN:64:600",
            "RESPAWN_ANCHOR:1:150",
            "GLOWSTONE:16:120",
            "TOTEM_OF_UNDYING:1:800",
            "ENDER_PEARL:16:240",
            "EXPERIENCE_BOTTLE:64:480",
            "GOLDEN_APPLE:8:480"
        );
        cat(
            y,
            "combat",
            "<red><b>Combat & Gear",
            "NETHERITE_SWORD",
            "DIAMOND_SWORD:1:150",
            "DIAMOND_AXE:1:150",
            "DIAMOND_HELMET:1:250",
            "DIAMOND_CHESTPLATE:1:400",
            "DIAMOND_LEGGINGS:1:350",
            "DIAMOND_BOOTS:1:200",
            "NETHERITE_INGOT:1:2500",
            "SHIELD:1:25",
            "BOW:1:30",
            "CROSSBOW:1:60",
            "ARROW:64:32",
            "MACE:1:6000",
            "WIND_CHARGE:16:160",
            "COBWEB:16:80"
        );
        cat(
            y,
            "potions",
            "<light_purple><b>Potions",
            "SPLASH_POTION",
            "SPLASH_POTION#STRONG_HEALING:1:30",
            "SPLASH_POTION#STRONG_HARMING:1:45",
            "POTION#STRONG_STRENGTH:1:90",
            "POTION#STRONG_SWIFTNESS:1:60",
            "POTION#LONG_FIRE_RESISTANCE:1:70",
            "POTION#LONG_SLOW_FALLING:1:50",
            "SPLASH_POTION#STRONG_POISON:1:40",
            "SPLASH_POTION#LONG_WEAKNESS:1:40",
            "POTION#STRONG_TURTLE_MASTER:1:120"
        );
        cat(
            y,
            "food",
            "<gold><b>Food",
            "COOKED_BEEF",
            "BREAD:16:12",
            "COOKED_BEEF:16:24",
            "COOKED_PORKCHOP:16:24",
            "GOLDEN_CARROT:16:60",
            "GOLDEN_APPLE:1:60",
            "ENCHANTED_GOLDEN_APPLE:1:4000",
            "BAKED_POTATO:32:20",
            "CAKE:1:15",
            "PUMPKIN_PIE:16:20"
        );
        cat(
            y,
            "blocks",
            "<green><b>Blocks",
            "GRASS_BLOCK",
            "OAK_LOG:16:16",
            "SPRUCE_LOG:16:16",
            "STONE:64:16",
            "COBBLESTONE:64:12",
            "DEEPSLATE_BRICKS:32:30",
            "STONE_BRICKS:32:20",
            "GLASS:16:12",
            "SAND:32:10",
            "BRICKS:16:16",
            "QUARTZ_BLOCK:16:40",
            "SMOOTH_STONE:32:24",
            "WHITE_CONCRETE:32:32",
            "LANTERN:8:24",
            "SEA_LANTERN:8:60",
            "DIRT:64:8",
            "NETHERRACK:64:8",
            "OAK_PLANKS:64:24",
            "SPRUCE_PLANKS:64:24",
            "BIRCH_PLANKS:64:24",
            "JUNGLE_PLANKS:64:24",
            "ACACIA_PLANKS:64:24",
            "DARK_OAK_PLANKS:64:24",
            "MANGROVE_PLANKS:64:24",
            "CHERRY_PLANKS:64:28",
            "PALE_OAK_PLANKS:64:28",
            "BAMBOO_PLANKS:64:24",
            "CRIMSON_PLANKS:64:28",
            "WARPED_PLANKS:64:28",
            "BIRCH_LOG:16:16",
            "DARK_OAK_LOG:16:16",
            "CHERRY_LOG:16:18",
            "PALE_OAK_LOG:16:18",
            "MANGROVE_LOG:16:16",
            "ANDESITE:64:12",
            "POLISHED_ANDESITE:64:18",
            "DIORITE:64:12",
            "POLISHED_DIORITE:64:18",
            "GRANITE:64:12",
            "POLISHED_GRANITE:64:18",
            "TUFF:64:14",
            "TUFF_BRICKS:32:22",
            "CALCITE:32:16",
            "DEEPSLATE_TILES:32:32",
            "POLISHED_DEEPSLATE:32:28",
            "COBBLED_DEEPSLATE:64:12",
            "SANDSTONE:32:16",
            "SMOOTH_SANDSTONE:32:20",
            "CUT_SANDSTONE:32:20",
            "RED_SANDSTONE:32:18",
            "RED_SAND:32:12",
            "GRAVEL:32:10",
            "MUD_BRICKS:32:22",
            "PACKED_MUD:32:18",
            "PRISMARINE:16:30",
            "PRISMARINE_BRICKS:16:36",
            "DARK_PRISMARINE:16:40",
            "END_STONE_BRICKS:32:44",
            "PURPUR_BLOCK:16:40",
            "BLACKSTONE:64:16",
            "POLISHED_BLACKSTONE_BRICKS:32:30",
            "NETHER_BRICKS:32:28",
            "RED_NETHER_BRICKS:32:32",
            "BASALT:32:14",
            "POLISHED_BASALT:32:20",
            "SMOOTH_QUARTZ:16:44",
            "QUARTZ_BRICKS:16:44",
            "QUARTZ_PILLAR:16:44",
            "CUT_COPPER:16:36",
            "OXIDIZED_COPPER:16:40",
            "COPPER_BULB:4:30",
            "TERRACOTTA:32:20",
            "MOSS_BLOCK:16:16",
            "SNOW_BLOCK:32:16",
            "PACKED_ICE:16:30",
            "BLUE_ICE:8:60",
            "CLAY:16:14",
            "GLOWSTONE:16:40",
            "SHROOMLIGHT:8:32",
            "OCHRE_FROGLIGHT:8:40",
            "TINTED_GLASS:16:48",
            "GLASS_PANE:32:12",
            "OBSIDIAN:16:160",
            "CRYING_OBSIDIAN:8:120"
        );
        cat(
            y,
            "farming",
            "<yellow><b>Farming",
            "WHEAT",
            "WHEAT_SEEDS:32:8",
            "CARROT:16:10",
            "POTATO:16:10",
            "SUGAR_CANE:16:14",
            "MELON_SEEDS:8:12",
            "PUMPKIN_SEEDS:8:12",
            "NETHER_WART:16:20",
            "BONE_MEAL:32:16",
            "OAK_SAPLING:8:10",
            "WATER_BUCKET:1:20",
            "COW_SPAWN_EGG:1:400",
            "HOPPER:1:40",
            "COMPOSTER:1:10"
        );
        cat(
            y,
            "nether",
            "<#FF6B35><b>Nether & End",
            "NETHERRACK",
            "BLAZE_ROD:8:80",
            "NETHER_WART:16:20",
            "GHAST_TEAR:4:80",
            "MAGMA_CREAM:8:40",
            "SOUL_SAND:16:24",
            "QUARTZ:32:40",
            "END_STONE:32:40",
            "SHULKER_BOX:1:600",
            "ELYTRA:1:5000",
            "FIREWORK_ROCKET:64:120"
        );
        cat(
            y,
            "special",
            "<aqua><b>Special",
            "EXPERIENCE_BOTTLE",
            "EXPERIENCE_BOTTLE:16:60",
            "NAME_TAG:1:50",
            "SADDLE:1:60",
            "LEAD:4:40",
            "ANVIL:1:150",
            "ENCHANTING_TABLE:1:300",
            "BOOKSHELF:8:120",
            "BEACON:1:12000",
            "TRIDENT:1:1500"
        );
        cat(
            y,
            "colors",
            "<#FF6FB5><b>Colours",
            "RED_CONCRETE",
            "WHITE_CONCRETE:32:32",
            "ORANGE_CONCRETE:32:32",
            "MAGENTA_CONCRETE:32:32",
            "LIGHT_BLUE_CONCRETE:32:32",
            "YELLOW_CONCRETE:32:32",
            "LIME_CONCRETE:32:32",
            "PINK_CONCRETE:32:32",
            "GRAY_CONCRETE:32:32",
            "LIGHT_GRAY_CONCRETE:32:32",
            "CYAN_CONCRETE:32:32",
            "PURPLE_CONCRETE:32:32",
            "BLUE_CONCRETE:32:32",
            "BROWN_CONCRETE:32:32",
            "GREEN_CONCRETE:32:32",
            "RED_CONCRETE:32:32",
            "BLACK_CONCRETE:32:32",
            "WHITE_WOOL:32:24",
            "ORANGE_WOOL:32:24",
            "MAGENTA_WOOL:32:24",
            "LIGHT_BLUE_WOOL:32:24",
            "YELLOW_WOOL:32:24",
            "LIME_WOOL:32:24",
            "PINK_WOOL:32:24",
            "GRAY_WOOL:32:24",
            "LIGHT_GRAY_WOOL:32:24",
            "CYAN_WOOL:32:24",
            "PURPLE_WOOL:32:24",
            "BLUE_WOOL:32:24",
            "BROWN_WOOL:32:24",
            "GREEN_WOOL:32:24",
            "RED_WOOL:32:24",
            "BLACK_WOOL:32:24",
            "WHITE_TERRACOTTA:32:24",
            "ORANGE_TERRACOTTA:32:24",
            "MAGENTA_TERRACOTTA:32:24",
            "LIGHT_BLUE_TERRACOTTA:32:24",
            "YELLOW_TERRACOTTA:32:24",
            "LIME_TERRACOTTA:32:24",
            "PINK_TERRACOTTA:32:24",
            "GRAY_TERRACOTTA:32:24",
            "LIGHT_GRAY_TERRACOTTA:32:24",
            "CYAN_TERRACOTTA:32:24",
            "PURPLE_TERRACOTTA:32:24",
            "BLUE_TERRACOTTA:32:24",
            "BROWN_TERRACOTTA:32:24",
            "GREEN_TERRACOTTA:32:24",
            "RED_TERRACOTTA:32:24",
            "BLACK_TERRACOTTA:32:24",
            "WHITE_STAINED_GLASS:16:16",
            "ORANGE_STAINED_GLASS:16:16",
            "MAGENTA_STAINED_GLASS:16:16",
            "LIGHT_BLUE_STAINED_GLASS:16:16",
            "YELLOW_STAINED_GLASS:16:16",
            "LIME_STAINED_GLASS:16:16",
            "PINK_STAINED_GLASS:16:16",
            "GRAY_STAINED_GLASS:16:16",
            "LIGHT_GRAY_STAINED_GLASS:16:16",
            "CYAN_STAINED_GLASS:16:16",
            "PURPLE_STAINED_GLASS:16:16",
            "BLUE_STAINED_GLASS:16:16",
            "BROWN_STAINED_GLASS:16:16",
            "GREEN_STAINED_GLASS:16:16",
            "RED_STAINED_GLASS:16:16",
            "BLACK_STAINED_GLASS:16:16",
            "WHITE_CONCRETE_POWDER:32:24",
            "ORANGE_CONCRETE_POWDER:32:24",
            "MAGENTA_CONCRETE_POWDER:32:24",
            "LIGHT_BLUE_CONCRETE_POWDER:32:24",
            "YELLOW_CONCRETE_POWDER:32:24",
            "LIME_CONCRETE_POWDER:32:24",
            "PINK_CONCRETE_POWDER:32:24",
            "GRAY_CONCRETE_POWDER:32:24",
            "LIGHT_GRAY_CONCRETE_POWDER:32:24",
            "CYAN_CONCRETE_POWDER:32:24",
            "PURPLE_CONCRETE_POWDER:32:24",
            "BLUE_CONCRETE_POWDER:32:24",
            "BROWN_CONCRETE_POWDER:32:24",
            "GREEN_CONCRETE_POWDER:32:24",
            "RED_CONCRETE_POWDER:32:24",
            "BLACK_CONCRETE_POWDER:32:24"
        );
        cat(
            y,
            "decor",
            "<#E9C46A><b>Decoration",
            "LANTERN",
            "LANTERN:8:24",
            "SOUL_LANTERN:8:28",
            "IRON_CHAIN:16:24",
            "CANDLE:8:16",
            "WHITE_BANNER:1:12",
            "FLOWER_POT:8:8",
            "PAINTING:4:16",
            "ITEM_FRAME:8:16",
            "GLOW_ITEM_FRAME:4:20",
            "BOOKSHELF:8:120",
            "CHISELED_BOOKSHELF:4:80",
            "CAMPFIRE:2:20",
            "SOUL_CAMPFIRE:2:24",
            "END_ROD:8:40",
            "SEA_LANTERN:8:60",
            "POPPY:16:8",
            "DANDELION:16:8",
            "CORNFLOWER:16:10",
            "LILY_OF_THE_VALLEY:16:10",
            "SUNFLOWER:8:12",
            "AZALEA:4:16",
            "FLOWERING_AZALEA:4:20",
            "MOSS_CARPET:16:10",
            "WHITE_CARPET:16:10",
            "DECORATED_POT:1:20",
            "ARMOR_STAND:2:20",
            "SCAFFOLDING:32:24",
            "LADDER:32:12",
            "OAK_FENCE:32:20",
            "OAK_TRAPDOOR:16:16",
            "OAK_DOOR:4:8",
            "IRON_BARS:32:40"
        );
        cat(
            y,
            "redstone",
            "<#FF3B3B><b>Redstone",
            "REDSTONE",
            "REDSTONE:64:40",
            "REDSTONE_TORCH:16:12",
            "REPEATER:8:24",
            "COMPARATOR:4:30",
            "PISTON:4:40",
            "STICKY_PISTON:4:60",
            "OBSERVER:4:40",
            "HOPPER:4:160",
            "DROPPER:4:24",
            "DISPENSER:4:36",
            "LEVER:8:8",
            "STONE_BUTTON:8:6",
            "OAK_PRESSURE_PLATE:8:8",
            "TRIPWIRE_HOOK:4:16",
            "DAYLIGHT_DETECTOR:2:30",
            "TARGET:4:24",
            "RAIL:32:40",
            "POWERED_RAIL:16:80",
            "DETECTOR_RAIL:8:40",
            "MINECART:1:30",
            "CHEST:8:24",
            "BARREL:8:24",
            "TNT:4:60",
            "NOTE_BLOCK:4:16",
            "CRAFTER:1:80"
        );
        return y;
    }

    private static void cat(YamlConfiguration y, String id, String name, String icon, String... items) {
        y.set("shop." + id + ".name", name);
        y.set("shop." + id + ".icon", icon);
        y.set("shop." + id + ".items", List.of(items));
    }

    public String money(double v) {
        if (Math.abs(v) >= 1.0E9) {
            return this.symbol + String.format(Locale.US, "%.2fB", v / 1.0E9);
        } else if (Math.abs(v) >= 1000000.0) {
            return this.symbol + String.format(Locale.US, "%.2fM", v / 1000000.0);
        } else {
            return Math.abs(v) >= 10000.0 ? this.symbol + String.format(Locale.US, "%.1fK", v / 1000.0) : this.symbol + String.format(Locale.US, "%,.2f", v);
        }
    }

    /** Connects to Vault (call once Vault has loaded). */
    public void hookVault() {
        if (this.vault != null) {
            this.vault.unregister();
        }

        this.vault = VaultBridge.hook(this.plugin, this, this.provider);
    }

    /** Where the money lives right now (self test, /ecoadmin). */
    public String providerStatus() {
        return this.vault == null ? "built-in (no Vault)" : this.vault.status();
    }

    private boolean external() {
        return this.vault != null && this.vault.external();
    }

    public double balance(UUID u) {
        return this.external() ? this.vault.balance(u) : this.builtinBalance(u);
    }

    public void give(UUID u, double v) {
        if (this.external()) {
            this.vault.deposit(u, v);
        } else {
            this.builtinGive(u, v);
        }
    }

    public boolean take(UUID u, double v) {
        return this.external() ? this.vault.withdraw(u, v) : this.builtinTake(u, v);
    }

    public double builtinBalance(UUID u) {
        return Math.max(0.0, this.modes.stats(u).balance);
    }

    public synchronized void builtinGive(UUID u, double v) {
        ModeManager.Stats s = this.modes.stats(u);
        s.balance = Math.max(0.0, s.balance) + v;
        this.modes.dirty();
    }

    public synchronized boolean builtinTake(UUID u, double v) {
        ModeManager.Stats s = this.modes.stats(u);
        if (s.balance < v - 1.0E-9) {
            return false;
        } else {
            s.balance -= v;
            this.modes.dirty();
            return true;
        }
    }

    public String builtinMoney(double v) {
        return this.money(v);
    }

    public long shards(UUID u) {
        return this.modes.stats(u).shards;
    }

    public boolean takeShards(UUID u, long n) {
        ModeManager.Stats s = this.modes.stats(u);
        if (s.shards < n) {
            return false;
        } else {
            s.shards -= n;
            this.modes.dirty();
            return true;
        }
    }

    public void giveShards(UUID u, long n) {
        this.modes.stats(u).shards += n;
        this.modes.dirty();
    }

    /** Every minute: playtime in the economy turns into shards. */
    public void tickShards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (this.inEco(p) && !p.isDead()) {
                ModeManager.Stats s = this.modes.stats(p.getUniqueId());
                if (++s.shardMinutes >= this.minutesPerShard) {
                    s.shardMinutes = 0;
                    s.shards++;
                    p.sendActionBar(Text.mm("<#B57CFF>+1 Shard <dark_gray>• <gray>" + s.shards + " total"));
                    Snd.play(p, "block.amethyst_block.chime", 0.8F, 1.4F);
                }

                this.modes.dirty();
            }
        }
    }

    public Double sellPrice(Material m) {
        return this.sell.get(m);
    }

    public void setSellPrice(Material m, Double price) {
        if (price != null && !(price <= 0.0)) {
            this.sell.put(m, price);
        } else {
            this.sell.remove(m);
        }

        this.save();
    }

    public Map<Material, Double> sellPrices() {
        return this.sell;
    }

    public Map<String, Economy.Category> categories() {
        return this.shop;
    }

    public Economy.Category addCategory(String name, Material icon) {
        String id = name.replaceAll("<[^>]+>", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (id.isEmpty() || this.shop.containsKey(id)) {
            id = "cat" + (this.shop.size() + 1);
        }

        Economy.Category c = new Economy.Category(id, name, icon);
        this.shop.put(id, c);
        this.save();
        return c;
    }

    public void removeCategory(String id) {
        this.shop.remove(id);
        this.save();
    }

    public boolean inEco(Player p) {
        return !this.plugin.worlds().managed(p.getWorld()) && this.ecoModes.contains(this.modes.mode(p.getWorld()));
    }

    /**
     * What an item sells for. Never sold: renamed or enchanted items, anything holding other items (a filled
     * shulker box, a bundle) so nothing inside is lost. Damaged items sell for what is left of them.
     */
    public double value(ItemStack it) {
        if (it == null || it.isEmpty()) {
            return 0.0;
        }

        Double price = this.sell.get(it.getType());
        if (price == null || price <= 0.0) {
            return 0.0;
        }

        double each = price;
        if (it.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta m = it.getItemMeta();
            if (m.hasDisplayName() || m.hasEnchants()) {
                return 0.0;
            }

            if (m instanceof org.bukkit.inventory.meta.BlockStateMeta bsm && bsm.hasBlockState()
                && bsm.getBlockState() instanceof org.bukkit.inventory.InventoryHolder holder && !holder.getInventory().isEmpty()) {
                return 0.0;
            }

            if (m instanceof org.bukkit.inventory.meta.BundleMeta bm && bm.hasItems()) {
                return 0.0;
            }

            int max = it.getType().getMaxDurability();
            if (max > 0 && m instanceof org.bukkit.inventory.meta.Damageable dmg && dmg.hasDamage()) {
                each = price * Math.max(0.0, 1.0 - (double)dmg.getDamage() / max);
            }
        }

        return Math.floor(each * it.getAmount() * 100.0) / 100.0;
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onMobKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null && e.getEntity() instanceof Enemy && this.inEco(killer) && !(this.mobReward <= 0.0)) {
            this.give(killer.getUniqueId(), this.mobReward);
            killer.sendActionBar(Text.mm("<#3DDC84>+" + this.money(this.mobReward)));
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onPlayerKill(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim) && this.inEco(victim) && this.inEco(killer) && this.shardsPerKill > 0) {
            String pair = killer.getUniqueId() + ">" + victim.getUniqueId();
            long now = System.currentTimeMillis();
            Long last = this.lastKillReward.get(pair);
            if (last == null || now - last >= this.killCooldown * 1000L) {
                this.lastKillReward.put(pair, now);
                this.giveShards(killer.getUniqueId(), this.shardsPerKill);
                killer.sendActionBar(Text.mm("<#B57CFF>+" + this.shardsPerKill + " Shard" + (this.shardsPerKill == 1 ? "" : "s") + " <gray>for killing <white>" + Text.esc(victim.getName())));
            }
        }

        if (killer != null && !killer.equals(victim) && this.inEco(victim) && this.inEco(killer) && !(this.killStealPercent <= 0.0)) {
            double stolen = Math.floor(this.balance(victim.getUniqueId()) * this.killStealPercent) / 100.0;
            if (!(stolen <= 0.0) && this.take(victim.getUniqueId(), stolen)) {
                this.give(killer.getUniqueId(), stolen);
                killer.sendMessage(Text.mm("<#3DDC84>+" + this.money(stolen) + " <gray>from " + Text.esc(victim.getName())));
                victim.sendMessage(Text.mm("<red>-" + this.money(stolen) + " <gray>(killed by " + Text.esc(killer.getName()) + ")"));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!name.equals("balance")
            || args.length < 3
            || !sender.hasPermission("vortexpvp.admin")
            || !args[0].equalsIgnoreCase("give") && !args[0].equalsIgnoreCase("set") && !args[0].equalsIgnoreCase("take")) {
            if (sender instanceof Player p) {
                EcoMenus var14 = new EcoMenus(this.plugin, this);
                switch (name) {
                    case "balance":
                        Player t = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : p;
                        if (t == null) {
                            p.sendMessage(Text.mm("<red>That player is not online."));
                        } else {
                            p.sendMessage(Text.mm("<#3DDC84><b>$</b> <gray>" + Text.esc(t.getName()) + ": <white>" + this.money(this.balance(t.getUniqueId()))));
                        }
                        break;
                    case "pay":
                        this.pay(p, args);
                        break;
                    case "baltop":
                        var14.baltop(p);
                        break;
                    case "shards":
                        if (!this.inEco(p)) {
                            p.sendMessage(Text.mm("<#B57CFF><b>\u2726</b> <gray>Shards: <white>" + this.shards(p.getUniqueId()) + " <dark_gray>(the shard shop is in the economy worlds)"));
                        } else {
                            var14.shards(p);
                        }
                        break;
                    case "worth":
                        ItemStack hand = p.getInventory().getItemInMainHand();
                        Double price = hand.isEmpty() ? null : this.sell.get(hand.getType());
                        if (price == null) {
                            p.sendMessage(Text.mm("<gray>That can't be sold."));
                        } else {
                            p.sendMessage(
                                Text.mm(
                                    "<gray>Worth: <#3DDC84>"
                                        + this.money(price)
                                        + "</#3DDC84> each <dark_gray>• <gray>stack of "
                                        + hand.getAmount()
                                        + ": <#3DDC84>"
                                        + this.money(price * hand.getAmount())
                                )
                            );
                        }
                        break;
                    case "sell":
                        if (!this.inEco(p)) {
                            p.sendMessage(Text.mm("<red>/sell works in the Eco SMP."));
                        } else if (args.length > 0 && args[0].equalsIgnoreCase("hand")) {
                            this.sellSlots(p, false);
                        } else if (args.length > 0 && args[0].equalsIgnoreCase("all")) {
                            this.sellSlots(p, true);
                        } else {
                            var14.sell(p);
                        }
                        break;
                    case "shop":
                        if (!this.inEco(p)) {
                            p.sendMessage(Text.mm("<red>The shop is in the Eco SMP."));
                        } else {
                            var14.shop(p);
                        }
                        break;
                    case "ecoadmin":
                        if (!p.hasPermission("vortexpvp.admin")) {
                            p.sendMessage(Text.mm("<red>You don't have permission."));
                        } else {
                            var14.admin(p);
                        }
                        break;
                    default:
                        return false;
                }

                return true;
            } else {
                sender.sendMessage("Players only.");
                return true;
            }
        } else {
            OfflinePlayer tx = Bukkit.getOfflinePlayerIfCached(args[1]);
            if (tx == null) {
                sender.sendMessage(Text.mm("<red>Unknown player."));
                return true;
            } else {
                try {
                    double v = Double.parseDouble(args[2]);
                    UUID tu = tx.getUniqueId();
                    String hand = args[0].toLowerCase(Locale.ROOT);
                    double now = this.balance(tu);
                    double want = Math.max(0.0, switch (hand) {
                        case "give" -> now + v;
                        case "take" -> now - v;
                        default -> v;
                    });
                    if (want > now) {
                        this.give(tu, want - now);
                    } else if (want < now) {
                        this.take(tu, now - want);
                    }

                    sender.sendMessage(Text.mm("<green>" + Text.esc(args[1]) + " now has " + this.money(this.balance(tu))));
                } catch (NumberFormatException var12) {
                    sender.sendMessage(Text.mm("<red>Not a number."));
                }

                return true;
            }
        }
    }

    private void pay(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(Text.mm("<red>Usage: /pay \\<player> \\<amount>"));
        } else {
            Player t = Bukkit.getPlayerExact(args[0]);
            if (t != null && !t.equals(p)) {
                double v;
                try {
                    String raw = args[1].toLowerCase(Locale.ROOT).replace(",", "");
                    double mult = raw.endsWith("k") ? 1000.0 : (raw.endsWith("m") ? 1000000.0 : (raw.endsWith("b") ? 1.0E9 : 1.0));
                    if (mult > 1.0) {
                        raw = raw.substring(0, raw.length() - 1);
                    }

                    v = Math.floor(Double.parseDouble(raw) * mult * 100.0) / 100.0;
                } catch (NumberFormatException var9) {
                    p.sendMessage(Text.mm("<red>Not a number."));
                    return;
                }

                if (v >= this.payConfirm && (args.length < 3 || !args[2].equalsIgnoreCase("confirm"))) {
                    String cmd = "/pay " + t.getName() + " " + String.format(Locale.ROOT, "%.2f", v) + " confirm";
                    p.sendMessage(
                        Text.mm(
                            "<gray>Send <#3DDC84>"
                                + this.money(v)
                                + "</#3DDC84> to <white>"
                                + Text.esc(t.getName())
                                + "</white>? <click:run_command:'"
                                + cmd
                                + "'><hover:show_text:'<gray>Click to confirm'><green><b>[CONFIRM]</b></green></hover></click>"
                        )
                    );
                    return;
                }

                if (!(v <= 0.0) && this.take(p.getUniqueId(), v)) {
                    this.give(t.getUniqueId(), v);
                    p.sendMessage(Text.mm("<gray>Sent <#3DDC84>" + this.money(v) + "</#3DDC84> to <white>" + Text.esc(t.getName())));
                    t.sendMessage(Text.mm("<gray>Received <#3DDC84>" + this.money(v) + "</#3DDC84> from <white>" + Text.esc(p.getName())));
                    Snd.play(t, "entity.experience_orb.pickup", 1.0F, 1.4F);
                } else {
                    p.sendMessage(Text.mm("<red>You don't have " + this.money(v) + "."));
                }
            } else {
                p.sendMessage(Text.mm("<red>That player is not online."));
            }
        }
    }

    private void sellSlots(Player p, boolean all) {
        double total = 0.0;
        int count = 0;
        ItemStack[] inv = p.getInventory().getStorageContents();

        for (int i = 0; i < inv.length; i++) {
            ItemStack it = inv[i];
            if (it != null && !it.isEmpty() && (all || i == p.getInventory().getHeldItemSlot())) {
                double v = this.value(it);
                if (!(v <= 0.0)) {
                    total += v;
                    count += it.getAmount();
                    p.getInventory().setItem(i, null);
                }
            }
        }

        if (count == 0) {
            p.sendMessage(Text.mm("<red>Nothing to sell" + (all ? "." : " in your hand.") + " <gray>(/sell opens the sell chest)"));
        } else {
            this.give(p.getUniqueId(), total);
            p.sendMessage(
                Text.mm(
                    "<gray>Sold <white>"
                        + count
                        + "</white> items for <#3DDC84>"
                        + this.money(total)
                        + "</#3DDC84> <dark_gray>• <gray>balance "
                        + this.money(this.balance(p.getUniqueId()))
                )
            );
            Snd.play(p, "entity.experience_orb.pickup", 1.0F, 1.2F);
        }
    }

    public void shop(Player p, String categoryId) {
        new EcoMenus(this.plugin, this).shop(p);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        String n = command.getName().toLowerCase(Locale.ROOT);
        if (args.length != 1 || !n.equals("pay") && !n.equals("balance")) {
            if (args.length == 2 && n.equals("balance")) {
                Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
            } else if (args.length == 1 && n.equals("sell")) {
                out.addAll(List.of("hand", "all"));
            }
        } else {
            Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
            if (n.equals("balance") && sender.hasPermission("vortexpvp.admin")) {
                out.addAll(List.of("give", "set", "take"));
            }
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    public static final class Category {
        public final String id;
        public String name;
        public Material icon;
        public final List<Economy.ShopItem> items = new ArrayList<>();

        Category(String id, String name, Material icon) {
            this.id = id;
            this.name = name;
            this.icon = icon;
        }
    }

    public static final class ShopItem {
        public Material material;
        public int amount;
        public double price;
        public String potion;

        public ShopItem(Material material, int amount, double price, String potion) {
            this.material = material;
            this.amount = amount;
            this.price = price;
            this.potion = potion;
        }

        public ItemStack stack(int count) {
            ItemStack it = new ItemStack(this.material, Math.max(1, count));
            if (this.potion != null && this.material == Material.SPAWNER) {
                // SPAWNER#ZOMBIE: a zombie spawner
                String type = this.potion.toUpperCase(Locale.ROOT);
                try {
                    org.bukkit.entity.EntityType et = org.bukkit.entity.EntityType.valueOf(type);
                    it.editMeta(m -> {
                        if (m instanceof org.bukkit.inventory.meta.BlockStateMeta bsm && bsm.getBlockState() instanceof org.bukkit.block.CreatureSpawner cs) {
                            cs.setSpawnedType(et);
                            bsm.setBlockState(cs);
                        }

                        if (spawnerKey != null) {
                            m.getPersistentDataContainer().set(spawnerKey, org.bukkit.persistence.PersistentDataType.STRING, et.name());
                        }

                        m.lore(List.of(Text.mm("<!i><gray>Spawns: <white>" + nice(et.name()))));
                    });
                } catch (IllegalArgumentException var5) {
                }

                return it;
            }

            if (this.potion != null && this.material == Material.ENCHANTED_BOOK) {
                // ENCHANTED_BOOK#MENDING_1: the enchantment and its level
                int cut = this.potion.lastIndexOf('_');
                String ench = cut > 0 ? this.potion.substring(0, cut) : this.potion;
                int level = 1;
                try {
                    level = cut > 0 ? Integer.parseInt(this.potion.substring(cut + 1)) : 1;
                } catch (NumberFormatException var6) {
                    ench = this.potion;
                }

                org.bukkit.enchantments.Enchantment e = org.bukkit.Registry.ENCHANTMENT.get(org.bukkit.NamespacedKey.minecraft(ench.toLowerCase(Locale.ROOT)));
                int lv = level;
                if (e != null) {
                    it.editMeta(org.bukkit.inventory.meta.EnchantmentStorageMeta.class, m -> m.addStoredEnchant(e, lv, true));
                }

                return it;
            }

            if (this.potion != null && it.getItemMeta() instanceof PotionMeta) {
                try {
                    PotionType type = PotionType.valueOf(this.potion.toUpperCase(Locale.ROOT));
                    it.editMeta(PotionMeta.class, m -> m.setBasePotionType(type));
                } catch (IllegalArgumentException var4) {
                }
            }

            return it;
        }

        static String nice(String key) {
            StringBuilder sb = new StringBuilder();
            for (String w : key.toLowerCase(Locale.ROOT).split("_")) {
                if (!w.isEmpty()) {
                    sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
                }
            }

            return sb.toString().trim();
        }

        public String name() {
            if (this.potion != null && this.material == Material.SPAWNER) {
                return nice(this.potion) + " Spawner";
            } else if (this.potion != null && this.material == Material.ENCHANTED_BOOK) {
                int cut = this.potion.lastIndexOf('_');
                String lv = cut > 0 ? this.potion.substring(cut + 1) : "";
                String[] roman = {"", "I", "II", "III", "IV", "V"};
                String r = lv.matches("[1-5]") ? roman[Integer.parseInt(lv)] : lv;
                return "Book: " + nice(cut > 0 ? this.potion.substring(0, cut) : this.potion) + (r.isEmpty() ? "" : " " + r);
            }

            String base = this.material.getKey().getKey().replace('_', ' ');
            if (this.potion != null) {
                base = base + " (" + this.potion.toLowerCase(Locale.ROOT).replace('_', ' ') + ")";
            }

            StringBuilder sb = new StringBuilder();

            for (String w : base.split(" ")) {
                if (!w.isEmpty()) {
                    sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
                }
            }

            return sb.toString().trim();
        }

        String serialize() {
            return this.material.name() + (this.potion == null ? "" : "#" + this.potion) + ":" + this.amount + ":" + this.price;
        }

        public static Economy.ShopItem parse(String line) {
            String[] p = line.split(":");
            if (p.length != 3) {
                return null;
            } else {
                String mat = p[0];
                String potion = null;
                int hash = mat.indexOf(35);
                if (hash > 0) {
                    potion = mat.substring(hash + 1);
                    mat = mat.substring(0, hash);
                }

                Material m = Material.matchMaterial(mat);
                if (m != null && m.isItem()) {
                    try {
                        return new Economy.ShopItem(m, Integer.parseInt(p[1].trim()), Double.parseDouble(p[2].trim()), potion);
                    } catch (NumberFormatException var7) {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
    }
}
