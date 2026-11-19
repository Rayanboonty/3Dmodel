package net.practicepvp.kit;

import java.util.ArrayList;
import java.util.List;
import net.practicepvp.util.Items;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionType;

public final class DefaultKits {
    public static final int DEFAULTS_VERSION = 4;

    private DefaultKits() {
    }

    public static List<Kit> create() {
        List<Kit> list = new ArrayList<>();
        list.add(endGame());
        list.add(sword());
        list.add(mace());
        list.add(spear());
        list.add(earlyGame());
        list.add(netheritePot());
        list.add(diamondSmp());
        list.add(creeper());
        list.add(cart());
        list.add(smp());
        list.add(crystal());
        list.add(bow());
        list.add(uhc());
        list.add(spearMace());
        list.add(shield());
        list.add(diamondPot());
        list.add(lateGame());
        list.addAll(added());
        return list;
    }

    public static List<Kit> added() {
        List<Kit> out = new ArrayList<>(List.of(axe(), trident(), gapple(), noDebuff(), buildUhc(), combo(), mole()));
        out.addAll(legacy());
        return out;
    }

    // ------------------------------------------------------------------ 3.13.0: the 1.8 legacy kits (1.8 combat)

    /** The classic 1.8 practice kits, played with 1.8 combat (no cooldown, 1.8 knockback, sword blocking). */
    public static List<Kit> legacy() {
        return List.of(noDebuff18(), debuff(), gapple18(), soup(), archer(), combo18(), buildUhc18(), classic());
    }

    private static Kit legacyKit(String id, String name, String color, String sprite, String glyph, Material icon, int order, String... description) {
        Kit k = kit(id, name, color, sprite, glyph, icon, KitCategory.LEGACY, order, description);
        k.legacy = true;
        k.arenaType = "DESIGNED";
        k.hitDelay = 20;
        return k;
    }

    private static void prot(Kit k, Material helmet, Material chest, Material legs, Material boots, int level, boolean unbreaking) {
        Object[] e = unbreaking ? new Object[]{Enchantment.PROTECTION, level, Enchantment.UNBREAKING, 3} : new Object[]{Enchantment.PROTECTION, level};
        armor(k, level > 0 ? ench(helmet, e) : it(helmet), level > 0 ? ench(chest, e) : it(chest), level > 0 ? ench(legs, e) : it(legs), level > 0 ? ench(boots, e) : it(boots));
    }

    private static void fillHeals(Kit k) {
        for (int i = 0; i < 36; i++) {
            if (k.contents[i] == null) {
                k.contents[i] = heal();
            }
        }
    }

    private static Kit noDebuff18() {
        Kit k = legacyKit("nodebuff18", "NoDebuff 1.8", "<#FF5C5C>", "gui:mob_effect/instant_health", "✙", Material.SPLASH_POTION, 1,
            "The classic: heal pots, pearls and speed", "with 1.8 combat. Pot fast, block-hit, aim pearls.");
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 2, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3)
        );
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3, Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3);
        k.contents[1] = it(Material.ENDER_PEARL, 16);
        k.contents[2] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[3] = potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE);
        k.contents[8] = it(Material.COOKED_BEEF, 64);
        k.contents[17] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[26] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[35] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        fillHeals(k);
        return k;
    }

    private static Kit debuff() {
        Kit k = legacyKit("debuff", "Debuff", "<#7CD64A>", "gui:mob_effect/poison", "☠", Material.SPIDER_EYE, 2,
            "NoDebuff plus poison and slowness", "splash pots: debuff them, then go in.");
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 2, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3)
        );
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3, Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3);
        k.contents[1] = it(Material.ENDER_PEARL, 16);
        k.contents[2] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[3] = potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE);
        k.contents[4] = potion(Material.SPLASH_POTION, PotionType.SLOWNESS);
        k.contents[5] = potion(Material.SPLASH_POTION, PotionType.POISON);
        k.contents[8] = it(Material.COOKED_BEEF, 64);
        k.contents[13] = potion(Material.SPLASH_POTION, PotionType.SLOWNESS);
        k.contents[14] = potion(Material.SPLASH_POTION, PotionType.POISON);
        k.contents[17] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[26] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[35] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        fillHeals(k);
        return k;
    }

    private static Kit gapple18() {
        Kit k = legacyKit("gapple18", "Gapple 1.8", "<#FFD23F>", "item/golden_apple", "◉", Material.GOLDEN_APPLE, 3,
            "Golden apples, strength and speed with", "1.8 combat and a spare armor set.");
        k.hunger = false;
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)
        );
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3);
        k.contents[1] = it(Material.GOLDEN_APPLE, 64);
        k.contents[2] = potion(Material.POTION, PotionType.STRONG_STRENGTH);
        k.contents[3] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[4] = potion(Material.POTION, PotionType.STRONG_STRENGTH);
        k.contents[5] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[9] = ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3);
        k.contents[10] = ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3);
        k.contents[11] = ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3);
        k.contents[12] = ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3);
        return k;
    }

    private static Kit soup() {
        Kit k = legacyKit("soup", "Soup", "<#C8874A>", "item/mushroom_stew", "♨", Material.MUSHROOM_STEW, 4,
            "Old-school soup PvP: a stew heals", "3.5 hearts at once. Refill fast, keep hitting.");
        k.hunger = false;
        k.regen = false;
        prot(k, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, 1, false);
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 1);
        for (int i = 1; i < 36; i++) {
            k.contents[i] = it(Material.MUSHROOM_STEW);
        }

        return k;
    }

    private static Kit archer() {
        Kit k = legacyKit("archer", "Archer", "<#B8E986>", "item/bow", "➶", Material.BOW, 5,
            "Bows only: Infinity and Power. Strafe,", "lead your shots, never stand still.");
        k.hunger = false;
        k.arenaType = "VANILLA,DESIGNED";
        armor(
            k,
            ench(Material.LEATHER_HELMET, Enchantment.PROTECTION, 2),
            ench(Material.LEATHER_CHESTPLATE, Enchantment.PROTECTION, 2),
            ench(Material.LEATHER_LEGGINGS, Enchantment.PROTECTION, 2),
            ench(Material.LEATHER_BOOTS, Enchantment.PROTECTION, 2, Enchantment.FEATHER_FALLING, 4)
        );
        k.contents[0] = ench(Material.BOW, Enchantment.POWER, 2, Enchantment.INFINITY, 1, Enchantment.UNBREAKING, 3);
        k.contents[1] = it(Material.GOLDEN_CARROT, 64);
        k.contents[2] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[3] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[9] = it(Material.ARROW);
        return k;
    }

    private static Kit combo18() {
        Kit k = legacyKit("combo18", "Combo 1.8", "<#FF6BE0>", "item/pufferfish", "✺", Material.PUFFERFISH, 6,
            "Hit delay 3 ticks with 1.8 knockback:", "keep them in the air, never let them land.");
        k.hunger = false;
        k.hitDelay = 3;
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)
        );
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 2, Enchantment.UNBREAKING, 3);
        k.contents[1] = it(Material.GOLDEN_APPLE, 64);
        k.contents[2] = it(Material.ENDER_PEARL, 16);
        k.contents[3] = potion(Material.POTION, PotionType.LONG_SWIFTNESS);
        k.contents[4] = potion(Material.POTION, PotionType.LONG_STRENGTH);
        return k;
    }

    /** A golden apple that heals twice as long (Regeneration II 10 s), like the UHC golden heads. */
    private static ItemStack goldenHead(int amount) {
        ItemStack s = named(it(Material.GOLDEN_APPLE, amount), "<gold><b>Golden Head</b>");
        s.setData(io.papermc.paper.datacomponent.DataComponentTypes.CONSUMABLE, io.papermc.paper.datacomponent.item.Consumable.consumable()
            .consumeSeconds(1.6F)
            .animation(io.papermc.paper.datacomponent.item.consumable.ItemUseAnimation.EAT)
            .addEffect(io.papermc.paper.datacomponent.item.consumable.ConsumeEffect.applyStatusEffects(List.of(
                new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.REGENERATION, 200, 1),
                new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.ABSORPTION, 2400, 0)
            ), 1.0F)));
        return s;
    }

    private static Kit buildUhc18() {
        Kit k = legacyKit("builduhc18", "BuildUHC 1.8", "<#FF9A3D>", "item/golden_apple", "⛰", Material.LAVA_BUCKET, 7,
            "UHC with blocks and buckets, rods and", "golden heads, no natural regeneration.");
        k.build = true;
        k.regen = false;
        k.arenaType = "VANILLA,DESIGNED";
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 2),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 2),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 2),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 2)
        );
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3);
        k.contents[1] = it(Material.FISHING_ROD);
        k.contents[2] = ench(Material.BOW, Enchantment.POWER, 3);
        k.contents[3] = it(Material.WATER_BUCKET);
        k.contents[4] = it(Material.LAVA_BUCKET);
        k.contents[5] = it(Material.GOLDEN_APPLE, 6);
        k.contents[6] = goldenHead(3);
        k.contents[7] = it(Material.COBBLESTONE, 64);
        k.contents[8] = it(Material.OAK_PLANKS, 64);
        k.contents[9] = it(Material.ARROW, 64);
        k.contents[10] = ench(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 3);
        k.contents[11] = ench(Material.DIAMOND_AXE, Enchantment.EFFICIENCY, 3);
        k.contents[12] = it(Material.WATER_BUCKET);
        k.contents[13] = it(Material.LAVA_BUCKET);
        k.contents[14] = it(Material.COBBLESTONE, 64);
        k.contents[15] = it(Material.OAK_PLANKS, 64);
        k.contents[16] = it(Material.COOKED_BEEF, 64);
        k.votes.add(new KitVote("no_lava", "No Lava Buckets", KitVote.Action.REMOVE, Material.LAVA_BUCKET, 0));
        return k;
    }

    private static Kit classic() {
        Kit k = legacyKit("classic", "Classic", "<#9FC5FF>", "item/iron_sword", "⚔", Material.IRON_SWORD, 8,
            "Iron armor, a sword, a rod and a bow:", "pure 1.8 fundamentals, no potions.");
        prot(k, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS, 0, false);
        k.contents[0] = it(Material.DIAMOND_SWORD);
        k.contents[1] = it(Material.FISHING_ROD);
        k.contents[2] = it(Material.BOW);
        k.contents[3] = it(Material.GOLDEN_APPLE, 3);
        k.contents[8] = it(Material.COOKED_BEEF, 16);
        k.contents[9] = it(Material.ARROW, 12);
        return k;
    }

    private static Kit combo() {
        Kit k = kit(
            "combo",
            "Combo",
            "<#FF4FD8>",
            "item/pufferfish",
            "✺",
            Material.PUFFERFISH,
            KitCategory.OTHER,
            6,
            "Hit delay 4 ticks (vanilla 20):",
            "chain hits, keep them in the air."
        );
        k.hunger = false;
        k.ffa = true;
        k.hitDelay = 4;
        k.pearlCooldown = 5;
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)
        );
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 2, Enchantment.UNBREAKING, 3);
        k.contents[1] = it(Material.GOLDEN_APPLE, 32);
        k.contents[2] = it(Material.ENDER_PEARL, 16);
        k.contents[3] = potion(Material.POTION, PotionType.LONG_SWIFTNESS);
        k.contents[4] = potion(Material.POTION, PotionType.LONG_STRENGTH);
        return k;
    }

    private static ItemStack rocket(int amount) {
        ItemStack s = it(Material.FIREWORK_ROCKET, amount);
        s.editMeta(FireworkMeta.class, m -> m.setPower(3));
        return s;
    }

    private static Kit axe() {
        Kit k = kit(
            "axe",
            "Axe",
            "<gray>",
            "item/diamond_axe",
            "⛏",
            Material.DIAMOND_AXE,
            KitCategory.VANILLA,
            8,
            "Axe and shield duels:",
            "disable shields, then strike."
        );
        k.ffa = true;
        k.hunger = false;
        diamondProt(k, 2);
        k.contents[40] = ench(Material.SHIELD, Enchantment.UNBREAKING, 3);
        k.contents[0] = ench(Material.DIAMOND_AXE, Enchantment.SHARPNESS, 1);
        k.contents[1] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 1);
        k.contents[2] = ench(Material.CROSSBOW, Enchantment.QUICK_CHARGE, 1);
        k.contents[3] = it(Material.BOW);
        k.contents[4] = it(Material.GOLDEN_APPLE, 6);
        k.contents[9] = it(Material.ARROW, 16);
        return k;
    }

    private static Kit trident() {
        Kit k = kit(
            "trident",
            "Trident",
            "<dark_aqua>",
            "item/trident",
            "Ψ",
            Material.TRIDENT,
            KitCategory.OTHER,
            3,
            "Riptide in the water, throw with",
            "loyalty and impale your opponent."
        );
        k.ffa = true;
        netheriteProt(k, false);
        k.contents[40] = it(Material.TOTEM_OF_UNDYING);
        k.contents[0] = named(ench(Material.TRIDENT, Enchantment.LOYALTY, 3, Enchantment.IMPALING, 5, Enchantment.UNBREAKING, 3), "<aqua>Loyalty Trident");
        k.contents[1] = named(ench(Material.TRIDENT, Enchantment.RIPTIDE, 3, Enchantment.IMPALING, 5, Enchantment.UNBREAKING, 3), "<dark_aqua>Riptide Trident");
        k.contents[2] = it(Material.WATER_BUCKET);
        k.contents[3] = ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3);
        k.contents[4] = it(Material.GOLDEN_APPLE, 16);
        k.contents[5] = it(Material.ENDER_PEARL, 8);
        k.contents[6] = it(Material.WATER_BUCKET);
        fill(k, 9, 11, it(Material.TOTEM_OF_UNDYING));
        k.votes.add(limitTotems(2));
        return k;
    }

    private static Kit gapple() {
        Kit k = kit(
            "gapple",
            "Gapple",
            "<yellow>",
            "item/golden_apple",
            "◉",
            Material.ENCHANTED_GOLDEN_APPLE,
            KitCategory.OTHER,
            4,
            "Stacked golden apples,",
            "strength and speed. Out-heal them."
        );
        k.ffa = true;
        k.hunger = false;
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)
        );
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3);
        k.contents[1] = it(Material.GOLDEN_APPLE, 64);
        k.contents[2] = potion(Material.POTION, PotionType.LONG_STRENGTH);
        k.contents[3] = potion(Material.POTION, PotionType.LONG_SWIFTNESS);
        k.contents[4] = potion(Material.POTION, PotionType.LONG_STRENGTH);
        k.contents[5] = potion(Material.POTION, PotionType.LONG_SWIFTNESS);
        return k;
    }

    private static Kit noDebuff() {
        Kit k = kit(
            "nodebuff",
            "NoDebuff",
            "<#FF6E6E>",
            "gui:mob_effect/instant_health",
            "✙",
            Material.SPLASH_POTION,
            KitCategory.OTHER,
            5,
            "Classic pot PvP: heal pots,",
            "pearls and speed. Pot fast, aim pearls."
        );
        k.ffa = true;
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 2, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 2, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3)
        );
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3, Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3);
        k.contents[1] = it(Material.ENDER_PEARL, 16);
        k.contents[2] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[3] = potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE);
        k.contents[8] = it(Material.COOKED_BEEF, 64);

        for (int i = 4; i < 39 && i < 36; i++) {
            if (k.contents[i] == null) {
                k.contents[i] = heal();
            }
        }

        k.contents[17] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[26] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        return k;
    }

    /** Mole PvP: dig through the underground of Mole Mines to find and beat the other player. */
    private static Kit mole() {
        Kit k = kit(
            "mole",
            "Mole PvP",
            "<#C9A27E>",
            "item/iron_pickaxe",
            "⛏",
            Material.IRON_PICKAXE,
            KitCategory.VANILLA,
            10,
            "Underground: dig tunnels with your",
            "pickaxe and shovel, follow the sonar",
            "and ambush the other player."
        );
        k.build = true;
        k.ffa = true;
        k.arenaType = "MOLE";
        k.buildHeight = 80;
        armor(
            k,
            ench(Material.IRON_HELMET, Enchantment.PROTECTION, 2),
            ench(Material.IRON_CHESTPLATE, Enchantment.PROTECTION, 2),
            ench(Material.IRON_LEGGINGS, Enchantment.PROTECTION, 2),
            ench(Material.IRON_BOOTS, Enchantment.PROTECTION, 2, Enchantment.FEATHER_FALLING, 3)
        );
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 1);
        k.contents[1] = ench(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3);
        k.contents[2] = ench(Material.DIAMOND_SHOVEL, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3);
        k.contents[3] = ench(Material.BOW, Enchantment.POWER, 2);
        k.contents[4] = it(Material.GOLDEN_APPLE, 6);
        k.contents[5] = it(Material.COBBLESTONE, 64);
        k.contents[6] = it(Material.TORCH, 16);
        k.contents[7] = it(Material.WATER_BUCKET);
        k.contents[8] = it(Material.COOKED_BEEF, 16);
        k.contents[9] = it(Material.ARROW, 16);
        k.contents[40] = it(Material.TORCH, 16);
        return k;
    }

    private static Kit buildUhc() {
        Kit k = kit(
            "builduhc",
            "Build UHC",
            "<#FF8A3D>",
            "item/golden_apple",
            "⛰",
            Material.WATER_BUCKET,
            KitCategory.VANILLA,
            9,
            "UHC with building: blocks, buckets,",
            "rods and no natural regeneration."
        );
        k.build = true;
        k.regen = false;
        k.ffa = true;
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 3),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 2),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 2),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 3)
        );
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3);
        k.contents[1] = it(Material.FISHING_ROD);
        k.contents[2] = ench(Material.BOW, Enchantment.POWER, 3);
        k.contents[3] = it(Material.WATER_BUCKET);
        k.contents[4] = it(Material.LAVA_BUCKET);
        k.contents[5] = it(Material.GOLDEN_APPLE, 6);
        k.contents[6] = named(it(Material.GOLDEN_APPLE, 3), "<gold>Golden Head");
        k.contents[7] = it(Material.COBBLESTONE, 64);
        k.contents[8] = it(Material.OAK_PLANKS, 64);
        k.contents[9] = it(Material.ARROW, 32);
        k.contents[10] = ench(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 3);
        k.contents[11] = ench(Material.DIAMOND_AXE, Enchantment.EFFICIENCY, 3);
        k.contents[12] = it(Material.WATER_BUCKET);
        k.contents[13] = it(Material.LAVA_BUCKET);
        k.contents[14] = it(Material.COBBLESTONE, 64);
        k.contents[15] = it(Material.COOKED_BEEF, 32);
        k.votes.add(new KitVote("no_lava", "No Lava Buckets", KitVote.Action.REMOVE, Material.LAVA_BUCKET, 0));
        return k;
    }

    private static Kit kit(String id, String name, String color, String sprite, String glyph, Material icon, KitCategory cat, int order, String... description) {
        Kit k = new Kit(id, name);
        k.color = color;
        k.sprite = sprite;
        k.glyph = glyph;
        k.icon = new ItemStack(icon);
        k.category = cat;
        k.order = order;
        k.description.addAll(List.of(description));
        return k;
    }

    private static ItemStack it(Material m) {
        return new ItemStack(m);
    }

    private static ItemStack it(Material m, int amount) {
        ItemStack s = new ItemStack(m);
        s.setAmount(Math.min(amount, s.getMaxStackSize()));
        return s;
    }

    private static ItemStack stacked(Material m, int amount) {
        ItemStack s = new ItemStack(m);
        s.editMeta(meta -> meta.setMaxStackSize(Math.min(99, Math.max(amount, 1))));
        s.setAmount(amount);
        return s;
    }

    private static ItemStack ench(Material m, Object... pairs) {
        ItemStack s = new ItemStack(m);
        s.editMeta(meta -> {
            for (int i = 0; i + 1 < pairs.length; i += 2) {
                meta.addEnchant((Enchantment)pairs[i], (Integer)pairs[i + 1], true);
            }
        });
        return s;
    }

    private static ItemStack named(ItemStack s, String mini) {
        return Items.of(s).name(mini).build();
    }

    private static ItemStack potion(Material m, PotionType type) {
        return Items.of(m).potion(type).build();
    }

    private static ItemStack heal() {
        return potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING);
    }

    private static void armor(Kit k, ItemStack helmet, ItemStack chest, ItemStack legs, ItemStack boots) {
        k.contents[39] = helmet;
        k.contents[38] = chest;
        k.contents[37] = legs;
        k.contents[36] = boots;
    }

    private static void netheriteProt(Kit k, boolean blastLegs) {
        armor(
            k,
            ench(Material.NETHERITE_HELMET, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
            ench(Material.NETHERITE_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
            blastLegs
                ? ench(Material.NETHERITE_LEGGINGS, Enchantment.BLAST_PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)
                : ench(Material.NETHERITE_LEGGINGS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
            ench(Material.NETHERITE_BOOTS, Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)
        );
    }

    private static void diamondProt(Kit k, int level) {
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, level, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, level, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, level, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, level, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3)
        );
    }

    private static void fill(Kit k, int from, int to, ItemStack item) {
        for (int i = from; i <= to; i++) {
            k.contents[i] = item.clone();
        }
    }

    private static KitVote limitTotems(int n) {
        return new KitVote("limit_totems", "Limit to " + n + " Totems", KitVote.Action.LIMIT, Material.TOTEM_OF_UNDYING, n);
    }

    private static Kit endGame() {
        Kit k = kit(
            "endgame",
            "End Game",
            "<light_purple>",
            "item/end_crystal",
            "✦",
            Material.END_CRYSTAL,
            KitCategory.SKILL,
            0,
            "Full endgame arsenal: netherite,",
            "crystals, anchors, mace and totems."
        );
        k.build = true;
        netheriteProt(k, true);
        k.contents[40] = it(Material.TOTEM_OF_UNDYING);
        k.contents[0] = ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[1] = it(Material.END_CRYSTAL, 64);
        k.contents[2] = it(Material.OBSIDIAN, 64);
        k.contents[3] = it(Material.RESPAWN_ANCHOR, 64);
        k.contents[4] = it(Material.GLOWSTONE, 64);
        k.contents[5] = it(Material.GOLDEN_APPLE, 64);
        k.contents[6] = it(Material.ENDER_PEARL, 16);
        k.contents[7] = ench(Material.MACE, Enchantment.DENSITY, 5, Enchantment.WIND_BURST, 1, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[8] = ench(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        fill(k, 9, 17, it(Material.TOTEM_OF_UNDYING));
        k.contents[18] = it(Material.END_CRYSTAL, 64);
        k.contents[19] = it(Material.OBSIDIAN, 64);
        k.contents[20] = it(Material.GLOWSTONE, 64);
        k.contents[21] = it(Material.RESPAWN_ANCHOR, 64);
        k.contents[22] = it(Material.EXPERIENCE_BOTTLE, 64);
        k.contents[23] = it(Material.EXPERIENCE_BOTTLE, 64);
        k.contents[24] = it(Material.WIND_CHARGE, 64);
        k.contents[25] = ench(Material.NETHERITE_AXE, Enchantment.SHARPNESS, 5, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[26] = it(Material.ENDER_PEARL, 16);
        fill(k, 27, 30, it(Material.TOTEM_OF_UNDYING));
        k.contents[31] = potion(Material.POTION, PotionType.STRONG_STRENGTH);
        k.contents[32] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[33] = potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE);
        k.contents[34] = it(Material.GOLDEN_APPLE, 64);
        k.contents[35] = it(Material.END_CRYSTAL, 64);
        k.votes.add(limitTotems(8));
        return k;
    }

    private static Kit sword() {
        Kit k = kit(
            "sword",
            "Sword",
            "<aqua>",
            "item/diamond_sword",
            "⚔",
            Material.DIAMOND_SWORD,
            KitCategory.SKILL,
            1,
            "Pure sword fighting.",
            "Diamond armor, diamond sword, nothing else."
        );
        k.hunger = false;
        k.ffa = true;
        armor(k, it(Material.DIAMOND_HELMET), it(Material.DIAMOND_CHESTPLATE), it(Material.DIAMOND_LEGGINGS), it(Material.DIAMOND_BOOTS));
        k.contents[0] = it(Material.DIAMOND_SWORD);
        return k;
    }

    public static Kit mace() {
        Kit k = kit(
            "mace",
            "Mace",
            "<aqua>",
            "item/mace",
            "⚒",
            Material.MACE,
            KitCategory.SKILL,
            2,
            "Elytra mace: fly with rockets, dive with",
            "density, finish with breach. Full kit."
        );
        k.ffa = true;
        armor(
            k,
            ench(Material.NETHERITE_HELMET, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
            ench(Material.ELYTRA, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
            ench(Material.NETHERITE_LEGGINGS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
            ench(Material.NETHERITE_BOOTS, Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)
        );
        k.contents[40] = it(Material.TOTEM_OF_UNDYING);
        k.contents[0] = named(
            ench(Material.MACE, Enchantment.DENSITY, 5, Enchantment.WIND_BURST, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), "<gold>Density Mace"
        );
        k.contents[1] = ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[2] = rocket(64);
        k.contents[3] = it(Material.WIND_CHARGE, 64);
        k.contents[4] = it(Material.GOLDEN_APPLE, 64);
        k.contents[5] = it(Material.ENDER_PEARL, 16);
        k.contents[6] = ench(Material.NETHERITE_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[7] = it(Material.TOTEM_OF_UNDYING);
        k.contents[8] = named(ench(Material.MACE, Enchantment.BREACH, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1), "<red>Breach Mace");
        fill(k, 9, 17, it(Material.TOTEM_OF_UNDYING));
        k.contents[18] = rocket(64);
        k.contents[19] = rocket(64);
        k.contents[20] = it(Material.WIND_CHARGE, 64);
        k.contents[21] = it(Material.WIND_CHARGE, 64);
        k.contents[22] = it(Material.GOLDEN_APPLE, 64);
        k.contents[23] = it(Material.ENDER_PEARL, 16);
        k.contents[24] = ench(Material.NETHERITE_AXE, Enchantment.SHARPNESS, 5, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[25] = ench(Material.ELYTRA, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[26] = it(Material.TOTEM_OF_UNDYING);
        k.contents[27] = potion(Material.POTION, PotionType.STRONG_STRENGTH);
        k.contents[28] = potion(Material.POTION, PotionType.STRONG_STRENGTH);
        k.contents[29] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[30] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[31] = potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE);
        k.contents[32] = potion(Material.POTION, PotionType.LONG_SLOW_FALLING);
        k.contents[33] = it(Material.EXPERIENCE_BOTTLE, 64);
        k.contents[34] = rocket(64);
        k.contents[35] = it(Material.WIND_CHARGE, 64);
        return k;
    }

    private static Kit spear() {
        Kit k = kit(
            "spear",
            "Spear",
            "<aqua>",
            "item/diamond_spear",
            "↟",
            Material.DIAMOND_SPEAR,
            KitCategory.SKILL,
            3,
            "1.21.11 spears: lunge in,",
            "poke from range, space your hits."
        );
        diamondProt(k, 4);
        k.contents[40] = ench(Material.SHIELD, Enchantment.UNBREAKING, 3);
        k.contents[0] = named(
            ench(Material.NETHERITE_SPEAR, Enchantment.LUNGE, 3, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3), "<yellow>Vanguard Spear"
        );
        k.contents[1] = ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3);
        k.contents[2] = it(Material.GOLDEN_APPLE, 16);
        k.contents[3] = it(Material.ENDER_PEARL, 8);
        return k;
    }

    private static Kit earlyGame() {
        Kit k = kit(
            "earlygame",
            "Early Game",
            "<gold>",
            "item/golden_apple",
            "❂",
            Material.GOLDEN_APPLE,
            KitCategory.SKILL,
            4,
            "Iron gear, buckets and blocks.",
            "Classic early survival fight."
        );
        k.build = true;
        armor(k, it(Material.IRON_HELMET), it(Material.IRON_CHESTPLATE), it(Material.IRON_LEGGINGS), it(Material.IRON_BOOTS));
        k.contents[40] = it(Material.SHIELD);
        k.contents[0] = it(Material.IRON_SWORD);
        k.contents[1] = it(Material.IRON_AXE);
        k.contents[2] = ench(Material.BOW, Enchantment.POWER, 1);
        k.contents[3] = it(Material.GOLDEN_APPLE, 4);
        k.contents[4] = it(Material.COBBLESTONE, 64);
        k.contents[5] = it(Material.WATER_BUCKET);
        k.contents[6] = it(Material.LAVA_BUCKET);
        k.contents[7] = it(Material.OAK_PLANKS, 64);
        k.contents[8] = it(Material.COOKED_BEEF, 16);
        k.contents[9] = it(Material.ARROW, 24);
        k.contents[10] = it(Material.IRON_PICKAXE);
        k.contents[11] = it(Material.COBBLESTONE, 64);
        return k;
    }

    private static Kit netheritePot() {
        Kit k = kit(
            "nethpot",
            "Netherite Pot",
            "<dark_purple>",
            "item/netherite_sword",
            "⚗",
            Material.NETHERITE_SWORD,
            KitCategory.VANILLA,
            0,
            "Netherite + splash healing.",
            "Totems, XP and strength potions."
        );
        netheriteProt(k, false);
        k.ffa = true;
        k.contents[40] = it(Material.TOTEM_OF_UNDYING);
        k.contents[0] = ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[1] = it(Material.ENDER_PEARL, 16);
        fill(k, 2, 5, heal());
        k.contents[6] = it(Material.GOLDEN_APPLE, 64);
        k.contents[7] = it(Material.TOTEM_OF_UNDYING);
        k.contents[8] = it(Material.EXPERIENCE_BOTTLE, 64);
        k.contents[9] = potion(Material.POTION, PotionType.STRONG_STRENGTH);
        k.contents[10] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[11] = potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE);
        k.contents[12] = potion(Material.POTION, PotionType.STRONG_STRENGTH);
        k.contents[13] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        fill(k, 14, 17, it(Material.TOTEM_OF_UNDYING));
        fill(k, 18, 35, heal());
        return k;
    }

    private static Kit diamondSmp() {
        Kit k = kit(
            "diasmp",
            "Diamond SMP",
            "<aqua>",
            "item/diamond_chestplate",
            "◆",
            Material.DIAMOND_CHESTPLATE,
            KitCategory.VANILLA,
            1,
            "Diamond gear SMP fight.",
            "Axe, shield, webs, potions."
        );
        k.build = true;
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)
        );
        k.contents[40] = ench(Material.SHIELD, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[1] = ench(Material.DIAMOND_AXE, Enchantment.SHARPNESS, 5, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3);
        k.contents[2] = it(Material.ENDER_PEARL, 16);
        k.contents[3] = it(Material.GOLDEN_APPLE, 32);
        k.contents[4] = it(Material.COBWEB, 32);
        k.contents[5] = it(Material.WATER_BUCKET);
        k.contents[6] = it(Material.TOTEM_OF_UNDYING);
        k.contents[7] = it(Material.EXPERIENCE_BOTTLE, 64);
        k.contents[8] = it(Material.COBBLESTONE, 64);
        fill(k, 9, 11, it(Material.TOTEM_OF_UNDYING));
        k.contents[12] = potion(Material.POTION, PotionType.STRONG_STRENGTH);
        k.contents[13] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[14] = potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE);
        k.contents[15] = it(Material.GOLDEN_APPLE, 32);
        k.contents[16] = it(Material.WATER_BUCKET);
        return k;
    }

    private static Kit creeper() {
        Kit k = kit(
            "creeper",
            "Creeper",
            "<green>",
            "item/creeper_spawn_egg",
            "☠",
            Material.CREEPER_SPAWN_EGG,
            KitCategory.VANILLA,
            2,
            "Spawn creepers, light them,",
            "and launch your opponent."
        );
        k.build = true;
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.BLAST_PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.BLAST_PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.BLAST_PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_BOOTS, Enchantment.BLAST_PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3)
        );
        k.contents[40] = ench(Material.SHIELD, Enchantment.UNBREAKING, 3);
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3);
        k.contents[1] = it(Material.CREEPER_SPAWN_EGG, 64);
        k.contents[2] = ench(Material.FLINT_AND_STEEL, Enchantment.UNBREAKING, 3);
        k.contents[3] = it(Material.GOLDEN_APPLE, 32);
        k.contents[4] = it(Material.ENDER_PEARL, 16);
        k.contents[5] = it(Material.TOTEM_OF_UNDYING);
        k.contents[6] = it(Material.COBBLESTONE, 64);
        k.contents[7] = it(Material.WATER_BUCKET);
        k.contents[8] = it(Material.CREEPER_SPAWN_EGG, 64);
        fill(k, 9, 12, it(Material.TOTEM_OF_UNDYING));
        k.contents[13] = it(Material.GOLDEN_APPLE, 32);
        k.votes.add(limitTotems(3));
        return k;
    }

    private static Kit cart() {
        Kit k = kit(
            "cart",
            "Cart",
            "<red>",
            "item/tnt_minecart",
            "⛟",
            Material.TNT_MINECART,
            KitCategory.VANILLA,
            3,
            "Rails + TNT minecarts.",
            "Light them with your flame bow."
        );
        k.build = true;
        armor(
            k,
            ench(Material.NETHERITE_HELMET, Enchantment.BLAST_PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.NETHERITE_CHESTPLATE, Enchantment.BLAST_PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.NETHERITE_LEGGINGS, Enchantment.BLAST_PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.NETHERITE_BOOTS, Enchantment.BLAST_PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3)
        );
        k.contents[40] = it(Material.TOTEM_OF_UNDYING);
        k.contents[0] = ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3);
        k.contents[1] = ench(Material.BOW, Enchantment.POWER, 5, Enchantment.FLAME, 1, Enchantment.UNBREAKING, 3, Enchantment.INFINITY, 1);
        k.contents[2] = stacked(Material.TNT_MINECART, 64);
        k.contents[3] = it(Material.RAIL, 64);
        k.contents[4] = it(Material.GOLDEN_APPLE, 32);
        k.contents[5] = it(Material.ENDER_PEARL, 16);
        k.contents[6] = it(Material.TOTEM_OF_UNDYING);
        k.contents[7] = it(Material.ARROW);
        k.contents[8] = it(Material.COBBLESTONE, 64);
        k.contents[9] = stacked(Material.TNT_MINECART, 64);
        k.contents[10] = it(Material.RAIL, 64);
        fill(k, 11, 14, it(Material.TOTEM_OF_UNDYING));
        k.contents[15] = it(Material.EXPERIENCE_BOTTLE, 64);
        k.contents[16] = it(Material.GOLDEN_APPLE, 32);
        k.contents[17] = it(Material.OBSIDIAN, 64);
        k.votes.add(limitTotems(3));
        return k;
    }

    private static Kit smp() {
        Kit k = kit(
            "smp",
            "SMP",
            "<dark_aqua>",
            "item/ender_pearl",
            "⬢",
            Material.ENDER_PEARL,
            KitCategory.VANILLA,
            4,
            "Netherite SMP fight.",
            "Axe, shield, webs, potions, totems."
        );
        k.build = true;
        netheriteProt(k, false);
        k.contents[40] = ench(Material.SHIELD, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[0] = ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[1] = ench(Material.NETHERITE_AXE, Enchantment.SHARPNESS, 5, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[2] = it(Material.ENDER_PEARL, 16);
        k.contents[3] = it(Material.GOLDEN_APPLE, 64);
        k.contents[4] = it(Material.COBWEB, 64);
        k.contents[5] = it(Material.WATER_BUCKET);
        k.contents[6] = potion(Material.POTION, PotionType.STRONG_STRENGTH);
        k.contents[7] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[8] = it(Material.TOTEM_OF_UNDYING);
        fill(k, 9, 14, it(Material.TOTEM_OF_UNDYING));
        k.contents[15] = it(Material.EXPERIENCE_BOTTLE, 64);
        k.contents[16] = it(Material.EXPERIENCE_BOTTLE, 64);
        k.contents[17] = potion(Material.POTION, PotionType.STRONG_STRENGTH);
        k.contents[18] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[19] = potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE);
        k.contents[20] = it(Material.GOLDEN_APPLE, 64);
        k.contents[21] = it(Material.ENDER_PEARL, 16);
        k.contents[22] = it(Material.COBWEB, 64);
        k.contents[23] = it(Material.WATER_BUCKET);
        k.votes.add(limitTotems(4));
        return k;
    }

    private static Kit crystal() {
        Kit k = kit(
            "crystal",
            "Crystal",
            "<light_purple>",
            "item/end_crystal",
            "✧",
            Material.END_CRYSTAL,
            KitCategory.VANILLA,
            5,
            "Crystal PvP: obsidian, crystals,",
            "respawn anchors and totems."
        );
        k.build = true;
        k.ffa = true;
        netheriteProt(k, true);
        k.contents[40] = it(Material.TOTEM_OF_UNDYING);
        k.contents[0] = ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[1] = it(Material.END_CRYSTAL, 64);
        k.contents[2] = it(Material.OBSIDIAN, 64);
        k.contents[3] = it(Material.RESPAWN_ANCHOR, 64);
        k.contents[4] = it(Material.GLOWSTONE, 64);
        k.contents[5] = it(Material.GOLDEN_APPLE, 64);
        k.contents[6] = it(Material.ENDER_PEARL, 16);
        k.contents[7] = it(Material.TOTEM_OF_UNDYING);
        k.contents[8] = ench(Material.NETHERITE_PICKAXE, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1);
        k.contents[9] = it(Material.END_CRYSTAL, 64);
        k.contents[10] = it(Material.OBSIDIAN, 64);
        k.contents[11] = it(Material.RESPAWN_ANCHOR, 64);
        k.contents[12] = it(Material.GLOWSTONE, 64);
        k.contents[13] = it(Material.EXPERIENCE_BOTTLE, 64);
        k.contents[14] = it(Material.EXPERIENCE_BOTTLE, 64);
        k.contents[15] = it(Material.EXPERIENCE_BOTTLE, 64);
        k.contents[16] = potion(Material.POTION, PotionType.STRONG_STRENGTH);
        k.contents[17] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        fill(k, 18, 26, it(Material.TOTEM_OF_UNDYING));
        k.contents[27] = it(Material.END_CRYSTAL, 64);
        k.contents[28] = it(Material.OBSIDIAN, 64);
        k.contents[29] = it(Material.GLOWSTONE, 64);
        k.contents[30] = it(Material.GOLDEN_APPLE, 64);
        k.contents[31] = it(Material.ENDER_PEARL, 16);
        fill(k, 32, 35, it(Material.TOTEM_OF_UNDYING));
        k.votes.add(limitTotems(8));
        return k;
    }

    private static Kit bow() {
        Kit k = kit("bow", "Bow", "<gold>", "item/bow", "➳", Material.BOW, KitCategory.VANILLA, 6, "Bow only. Aim, strafe, punch.", "Golden apples to heal.");
        k.hunger = false;
        diamondProt(k, 2);
        k.contents[0] = ench(Material.BOW, Enchantment.POWER, 5, Enchantment.PUNCH, 1, Enchantment.UNBREAKING, 3, Enchantment.INFINITY, 1);
        k.contents[1] = it(Material.GOLDEN_APPLE, 6);
        k.contents[9] = it(Material.ARROW);
        return k;
    }

    private static Kit uhc() {
        Kit k = kit(
            "uhc",
            "UHC",
            "<red>",
            "gui:hud/heart/full",
            "❤",
            Material.LAVA_BUCKET,
            KitCategory.VANILLA,
            7,
            "No natural regeneration.",
            "Golden apples, buckets, rods and blocks."
        );
        k.build = true;
        k.regen = false;
        k.ffa = true;
        armor(
            k,
            ench(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 2),
            ench(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 3),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 2),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 2, Enchantment.FEATHER_FALLING, 2)
        );
        k.contents[40] = it(Material.SHIELD);
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3);
        k.contents[1] = ench(Material.DIAMOND_AXE, Enchantment.EFFICIENCY, 3);
        k.contents[2] = ench(Material.BOW, Enchantment.POWER, 3);
        k.contents[3] = it(Material.FISHING_ROD);
        k.contents[4] = it(Material.GOLDEN_APPLE, 6);
        k.contents[5] = it(Material.WATER_BUCKET);
        k.contents[6] = it(Material.LAVA_BUCKET);
        k.contents[7] = it(Material.COBBLESTONE, 64);
        k.contents[8] = it(Material.OAK_PLANKS, 64);
        k.contents[9] = it(Material.ARROW, 32);
        k.contents[10] = it(Material.WATER_BUCKET);
        k.contents[11] = it(Material.LAVA_BUCKET);
        k.contents[12] = it(Material.COOKED_BEEF, 16);
        k.contents[13] = ench(Material.DIAMOND_PICKAXE, Enchantment.EFFICIENCY, 3);
        k.votes.add(new KitVote("no_lava", "No Lava Buckets", KitVote.Action.REMOVE, Material.LAVA_BUCKET, 0));
        return k;
    }

    private static Kit spearMace() {
        Kit k = kit(
            "spearmace",
            "Spear Mace",
            "<red>",
            "item/netherite_spear",
            "⚚",
            Material.NETHERITE_SPEAR,
            KitCategory.OTHER,
            0,
            "Poke with the spear, launch with",
            "wind charges, crush with the mace."
        );
        k.build = true;
        k.ffa = true;
        netheriteProt(k, false);
        k.contents[40] = it(Material.TOTEM_OF_UNDYING);
        k.contents[0] = named(ench(Material.NETHERITE_SPEAR, Enchantment.LUNGE, 3, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3), "<yellow>Lunge Spear");
        k.contents[1] = named(
            ench(Material.MACE, Enchantment.DENSITY, 5, Enchantment.BREACH, 4, Enchantment.WIND_BURST, 3, Enchantment.UNBREAKING, 3), "<red>Impact Mace"
        );
        k.contents[2] = ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3);
        k.contents[3] = it(Material.WIND_CHARGE, 64);
        k.contents[4] = it(Material.ENDER_PEARL, 16);
        k.contents[5] = it(Material.GOLDEN_APPLE, 64);
        k.contents[6] = it(Material.ENCHANTED_GOLDEN_APPLE, 4);
        k.contents[7] = it(Material.TOTEM_OF_UNDYING);
        k.contents[8] = it(Material.COBWEB, 64);
        fill(k, 9, 13, it(Material.TOTEM_OF_UNDYING));
        k.votes.add(limitTotems(3));
        return k;
    }

    private static Kit shield() {
        Kit k = kit(
            "shield",
            "Shield",
            "<yellow>",
            "item/diamond_axe",
            "⛨",
            Material.SHIELD,
            KitCategory.OTHER,
            1,
            "Sword + axe + shield timing.",
            "Break their shield, punish the gap."
        );
        k.hunger = false;
        diamondProt(k, 3);
        k.contents[40] = ench(Material.SHIELD, Enchantment.UNBREAKING, 3);
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 3, Enchantment.UNBREAKING, 3);
        k.contents[1] = ench(Material.DIAMOND_AXE, Enchantment.SHARPNESS, 3, Enchantment.UNBREAKING, 3);
        k.contents[2] = it(Material.GOLDEN_APPLE, 8);
        return k;
    }

    private static Kit diamondPot() {
        Kit k = kit(
            "pot",
            "Diamond Pot",
            "<#FF5555>",
            "gui:mob_effect/instant_health",
            "✚",
            Material.SPLASH_POTION,
            KitCategory.OTHER,
            2,
            "Classic pot PvP.",
            "Diamond gear, splash healing, speed."
        );
        k.icon = potion(Material.SPLASH_POTION, PotionType.STRONG_HEALING);
        k.ffa = true;
        diamondProt(k, 4);
        k.contents[0] = ench(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2, Enchantment.UNBREAKING, 3);
        k.contents[1] = it(Material.ENDER_PEARL, 16);
        k.contents[2] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[3] = potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE);
        fill(k, 4, 7, heal());
        k.contents[8] = it(Material.COOKED_BEEF, 64);
        k.contents[9] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[10] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[11] = potion(Material.POTION, PotionType.LONG_FIRE_RESISTANCE);
        fill(k, 12, 35, heal());
        return k;
    }

    private static Kit lateGame() {
        Kit k = kit(
            "lategame",
            "Late Game",
            "<gold>",
            "item/netherite_chestplate",
            "✸",
            Material.NETHERITE_CHESTPLATE,
            KitCategory.OTHER,
            3,
            "Mixed netherite/diamond endgame gear.",
            "Bow, crossbow, buckets and webs."
        );
        k.build = true;
        armor(
            k,
            ench(Material.NETHERITE_HELMET, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.NETHERITE_CHESTPLATE, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3),
            ench(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 4, Enchantment.FEATHER_FALLING, 4, Enchantment.UNBREAKING, 3)
        );
        k.contents[40] = ench(Material.SHIELD, Enchantment.UNBREAKING, 3);
        k.contents[0] = ench(Material.NETHERITE_SWORD, Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3);
        k.contents[1] = ench(Material.DIAMOND_AXE, Enchantment.SHARPNESS, 5, Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3);
        k.contents[2] = ench(Material.BOW, Enchantment.POWER, 5, Enchantment.UNBREAKING, 3);
        k.contents[3] = it(Material.ENDER_PEARL, 8);
        k.contents[4] = it(Material.GOLDEN_APPLE, 32);
        k.contents[5] = it(Material.COBWEB, 32);
        k.contents[6] = it(Material.WATER_BUCKET);
        k.contents[7] = it(Material.LAVA_BUCKET);
        k.contents[8] = it(Material.COBBLESTONE, 64);
        k.contents[9] = it(Material.ARROW, 64);
        k.contents[10] = ench(Material.CROSSBOW, Enchantment.QUICK_CHARGE, 3, Enchantment.MULTISHOT, 1, Enchantment.UNBREAKING, 3);
        k.contents[11] = it(Material.TOTEM_OF_UNDYING);
        k.contents[12] = it(Material.TOTEM_OF_UNDYING);
        k.contents[13] = it(Material.EXPERIENCE_BOTTLE, 64);
        k.contents[14] = potion(Material.POTION, PotionType.STRONG_STRENGTH);
        k.contents[15] = potion(Material.POTION, PotionType.STRONG_SWIFTNESS);
        k.contents[16] = it(Material.GOLDEN_APPLE, 16);
        k.contents[17] = it(Material.OAK_PLANKS, 64);
        return k;
    }
}
