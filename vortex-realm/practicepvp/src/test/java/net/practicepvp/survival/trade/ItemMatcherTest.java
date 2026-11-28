package net.practicepvp.survival.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class ItemMatcherTest {
    @Test
    void typosAndNicknames() {
        assertEquals(Material.DIAMOND, ItemMatcher.find("dimond"));
        assertEquals(Material.DIAMOND, ItemMatcher.find("diamonds"));
        assertEquals(Material.NETHERITE_SWORD, ItemMatcher.find("netherit sword"));
        assertEquals(Material.NETHERITE_SWORD, ItemMatcher.find("netherite_sword"));
        assertEquals(Material.ENCHANTED_GOLDEN_APPLE, ItemMatcher.find("god apple"));
        assertEquals(Material.ENDER_PEARL, ItemMatcher.find("pearls"));
        assertEquals(Material.TOTEM_OF_UNDYING, ItemMatcher.find("totme"));
        assertEquals(Material.DIAMOND_HELMET, ItemMatcher.find("diamond helmt"));
        assertEquals(Material.ELYTRA, ItemMatcher.find("elytra"));
        assertNull(ItemMatcher.find("qwertyuiop"));
    }

    @Test
    void amounts() {
        assertEquals(Map.of(Material.ENDER_PEARL, 128), Market.parseWant("2 stacks of pearls"));
        assertEquals(Map.of(Material.DIAMOND, 64), Market.parseWant("dia x64"));
        assertEquals(Map.of(Material.OBSIDIAN, 64), Market.parseWant("a stack of obby"));
        assertEquals(Map.of(Material.DIAMOND, 10, Material.MACE, 1), Market.parseWant("10 dia and 1 mace"));
        assertEquals(Map.of(Material.DIAMOND_HELMET, 1, Material.DIAMOND_CHESTPLATE, 1), Market.parseWant("diamond_helmet 1, diamond_chestplate 1"));
        assertNull(Market.parseWant("blorptastic 5"));
    }
}
