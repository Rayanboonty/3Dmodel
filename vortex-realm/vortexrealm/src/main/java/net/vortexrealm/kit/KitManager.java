package net.vortexrealm.kit;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.UseCooldown;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Text;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class KitManager {
    public static final Pattern ID = Pattern.compile("[a-z0-9_]{1,32}");
    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Kit> kits = new LinkedHashMap<>();

    public KitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "kits.yml");
    }

    public void load() {
        this.kits.clear();
        if (!this.file.exists() && this.plugin.getResource("default-kits.yml") != null) {
            try (InputStream in = this.plugin.getResource("default-kits.yml")) {
                if (in != null) {
                    Files.createDirectories(this.file.getParentFile().toPath());
                    Files.copy(in, this.file.toPath());
                    this.plugin.getLogger().info("Created kits.yml from the bundled kit set.");
                }
            } catch (IOException var10) {
                this.plugin.getLogger().log(Level.WARNING, "Could not write the bundled kits.yml - using built-in kits", (Throwable)var10);
            }
        }

        if (!this.file.exists()) {
            for (Kit k : DefaultKits.create()) {
                this.kits.put(k.id, k);
            }

            this.save();
            this.plugin.getLogger().info("Created kits.yml with " + this.kits.size() + " default kits.");
        } else {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
            ConfigurationSection root = y.getConfigurationSection("kits");
            if (root != null) {
                for (String id : root.getKeys(false)) {
                    ConfigurationSection s = root.getConfigurationSection(id);
                    if (s != null) {
                        try {
                            this.kits.put(id, this.read(id, s));
                        } catch (RuntimeException var7) {
                            this.plugin.getLogger().log(Level.SEVERE, "Kit '" + id + "' in kits.yml is invalid and was skipped.", (Throwable)var7);
                        }
                    }
                }

                int fromVersion = y.getInt("defaults-version", 1);
                if (fromVersion < 4) {
                    int added = 0;

                    for (Kit k : DefaultKits.added()) {
                        if (!this.kits.containsKey(k.id)) {
                            this.kits.put(k.id, k);
                            added++;
                        }
                    }

                    if (fromVersion < 4) {
                        Kit old = this.kits.get("mace");
                        Kit mace = DefaultKits.mace();
                        if (old != null) {
                            mace.enabled = old.enabled;
                            mace.ranked = old.ranked;
                            mace.unranked = old.unranked;
                            mace.ffa = old.ffa;
                            mace.order = old.order;
                            mace.version = old.version + 1;
                        }

                        this.kits.put("mace", mace);
                        this.kits.remove("elytramace");
                        this.plugin.getLogger().info("Mace is now the full Elytra Mace kit (the separate Elytra Mace kit was merged into it).");
                    }

                    this.save();
                    if (added > 0) {
                        this.plugin.getLogger().info("Added " + added + " new default kits (Axe, Trident, Gapple, NoDebuff, Build UHC, Combo).");
                    }
                }

                this.mergeDefaults(y);
                this.plugin.getLogger().info("Loaded " + this.kits.size() + " kits.");
            }
        }
    }

    /** What the last update did to the kits: new defaults added, untouched defaults refreshed, edited ones kept. */
    public final List<String> mergeReport = new ArrayList<>();
    private final Map<String, String> defaultHash = new HashMap<>();
    private final Set<String> knownDefaults = new java.util.LinkedHashSet<>();

    /** The bundled kit set (default-kits.yml in the jar). */
    private Map<String, Kit> bundled() {
        Map<String, Kit> out = new java.util.LinkedHashMap<>();
        try (InputStream in = this.plugin.getResource("default-kits.yml")) {
            if (in != null) {
                YamlConfiguration d = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
                ConfigurationSection root = d.getConfigurationSection("kits");
                if (root != null) {
                    for (String id : root.getKeys(false)) {
                        ConfigurationSection s = root.getConfigurationSection(id);
                        if (s != null) {
                            try {
                                out.put(id, this.read(id, s));
                            } catch (RuntimeException var9) {
                            }
                        }
                    }
                }
            }
        } catch (IOException var11) {
        }

        if (out.isEmpty()) {
            for (Kit k : DefaultKits.create()) {
                out.put(k.id, k);
            }
        }

        // kits added after the bundled file was made (1.5.0: mole)
        for (Kit k : DefaultKits.added()) {
            out.putIfAbsent(k.id, k);
        }

        return out;
    }

    /** Fingerprint of what a kit plays like (items, effects, rules), not of its name, order or queue switches. */
    static String hash(Kit k) {
        YamlConfiguration y = new YamlConfiguration();

        for (int i = 0; i < 41; i++) {
            if (!Items.isEmpty(k.contents[i])) {
                y.set("i." + i, k.contents[i]);
            }
        }

        List<String> effects = new ArrayList<>();

        for (PotionEffect pe : k.effects) {
            effects.add(pe.getType().getKey().getKey() + ":" + pe.getAmplifier() + ":" + pe.getDuration());
        }

        y.set("e", effects);
        y.set("r", k.build + "/" + k.regen + "/" + k.hunger + "/" + k.roundsToWin + "/" + k.buildHeight + "/" + k.pearlCooldown + "/" + k.windCooldown + "/" + k.hitDelay + (k.knockback != 100 ? "/kb" + k.knockback : "") + (k.roundsVote ? "" : "/norv"));

        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-1").digest(y.saveToString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(d, 0, 8);
        } catch (java.security.NoSuchAlgorithmException var4) {
            return Integer.toHexString(y.saveToString().hashCode());
        }
    }

    /**
     * Brings new and changed default kits in on an update, without ever losing an admin's work:
     * <ul>
     * <li>a default kit this server never saw is added (one the admin deleted stays deleted);</li>
     * <li>a default kit nobody edited gets the new default items, keeping its queue switches, order and arenas;</li>
     * <li>an edited kit is kept as it is, and the new default is added next to it as "&lt;id&gt;_new", switched off.</li>
     * </ul>
     */
    /** Default kits added in later versions (not in the bundled kit file of a first start). */
    private static final java.util.Set<String> NEWER_KITS = java.util.Set.of("mole");

    private void mergeDefaults(YamlConfiguration y) {
        this.mergeReport.clear();
        Map<String, Kit> bundled = this.bundled();
        ConfigurationSection hashes = y.getConfigurationSection("default-hashes");
        boolean firstRun = !y.isList("known-defaults");
        this.knownDefaults.clear();
        this.knownDefaults.addAll(y.getStringList("known-defaults"));
        this.defaultHash.clear();
        if (hashes != null) {
            for (String id : hashes.getKeys(false)) {
                this.defaultHash.put(id, hashes.getString(id));
            }
        }

        if (firstRun) {
            // older versions didn't track this: everything in this kit set counts as seen (a missing one was deleted)
            this.knownDefaults.addAll(bundled.keySet());
            // except kits newer than every kit file: they are added below like any new default
            this.knownDefaults.removeAll(NEWER_KITS);
        }

        boolean changed = firstRun;

        for (Kit d : bundled.values()) {
            String hd = hash(d);
            Kit mine = this.kits.get(d.id);
            if (!this.knownDefaults.contains(d.id)) {
                if (mine == null) {
                    this.kits.put(d.id, d);
                    this.mergeReport.add("+ " + d.id + " (new kit)");
                }

                this.knownDefaults.add(d.id);
                this.defaultHash.put(d.id, hd);
                changed = true;
            } else if (mine != null) {
                String was = this.defaultHash.get(d.id);
                if (was == null) {
                    this.defaultHash.put(d.id, hd);
                    changed = true;
                } else if (!was.equals(hd)) {
                    if (hash(mine).equals(was)) {
                        // nobody edited it: take the new default, keep the server's switches
                        d.enabled = mine.enabled;
                        d.ranked = mine.ranked;
                        d.unranked = mine.unranked;
                        d.ffa = mine.ffa;
                        d.order = mine.order;
                        d.arenaType = mine.arenaType;
                        d.arenas.clear();
                        d.arenas.addAll(mine.arenas);
                        d.version = mine.version + 1;
                        this.kits.put(d.id, d);
                        this.mergeReport.add("~ " + d.id + " (updated to the new default)");
                    } else if (!this.kits.containsKey(d.id + "_new")) {
                        Kit copy = this.read(d.id + "_new", this.section(d));
                        copy.displayName = d.displayName + " (new)";
                        copy.enabled = false;
                        this.kits.put(copy.id, copy);
                        this.mergeReport.add("= " + d.id + " (kept your edits; new default saved as " + copy.id + ", off)");
                    }

                    this.defaultHash.put(d.id, hd);
                    changed = true;
                }
            }
        }

        if (changed) {
            this.save();
        }

        if (!this.mergeReport.isEmpty()) {
            this.plugin.getLogger().info("Kits after the update: " + String.join(", ", this.mergeReport));
        }
    }

    /** A kit written to a config section (to copy it through read()). */
    private ConfigurationSection section(Kit k) {
        YamlConfiguration y = new YamlConfiguration();
        this.write(y, "k.", k);
        return y.getConfigurationSection("k");
    }

    private Kit read(String id, ConfigurationSection s) {
        Kit k = new Kit(id, s.getString("name", id));
        k.color = s.getString("color", "<gold>");
        k.sprite = s.getString("sprite", "");
        k.glyph = s.getString("glyph", "⚔");
        ItemStack icon = s.getItemStack("icon");
        k.icon = icon != null ? icon : new ItemStack(Material.DIAMOND_SWORD);
        k.category = KitCategory.parse(s.getString("category"), KitCategory.OTHER);
        k.order = s.getInt("order", 100);
        k.enabled = s.getBoolean("enabled", true);
        k.ranked = s.getBoolean("ranked", true);
        k.unranked = s.getBoolean("unranked", true);
        k.ffa = s.getBoolean("ffa", false);
        k.build = s.getBoolean("build", false);
        k.regen = s.getBoolean("natural-regen", true);
        k.hunger = s.getBoolean("hunger", true);
        k.roundsToWin = s.getInt("rounds-to-win", 0);
        k.buildHeight = s.getInt("build-height", -1);
        k.pearlCooldown = s.getInt("pearl-cooldown", -1);
        k.windCooldown = s.getInt("wind-charge-cooldown", -1);
        k.hitDelay = s.getInt("hit-delay", -1);
        k.knockback = Math.max(0, Math.min(400, s.getInt("knockback", 100)));
        k.roundsVote = s.getBoolean("rounds-vote", true);
        k.arenaType = s.getString("arena-type", "AUTO").toUpperCase(Locale.ROOT);
        k.throwMode = s.getString("round-throw", "AUTO").toUpperCase(Locale.ROOT);
        k.arenas.clear();
        k.arenas.addAll(s.getStringList("arenas"));
        k.version = s.getInt("version", 1);
        k.description.addAll(s.getStringList("description"));

        for (String v : s.getStringList("votes")) {
            KitVote vote = KitVote.parse(v);
            if (vote != null) {
                k.votes.add(vote);
            }
        }

        for (String e : s.getStringList("effects")) {
            PotionEffect pe = parseEffect(e);
            if (pe != null) {
                k.effects.add(pe);
            }
        }

        ConfigurationSection items = s.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                try {
                    int slot = Integer.parseInt(key);
                    if (slot >= 0 && slot < 41) {
                        k.contents[slot] = items.getItemStack(key);
                    }
                } catch (NumberFormatException var9) {
                }
            }
        }

        return k;
    }

    public static PotionEffect parseEffect(String s) {
        String[] p = s.split(":");
        if (p.length < 1) {
            return null;
        } else {
            PotionEffectType type = Registry.MOB_EFFECT.get(NamespacedKey.minecraft(p[0].toLowerCase(Locale.ROOT)));
            if (type == null) {
                return null;
            } else {
                int amp = p.length > 1 ? Integer.parseInt(p[1]) : 0;
                int dur = p.length > 2 ? Integer.parseInt(p[2]) : -1;
                return new PotionEffect(type, dur, amp, true, false, true);
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.options()
            .setHeader(
                List.of(
                    "VortexRealm kits. Every kit is its own game mode with its own queues and ranks.",
                    "Edit in-game with /pvpadmin -> Kits (recommended) or /kits.",
                    "category: SKILL, VANILLA or OTHER (queue menu tab). sprite: item/<name> or gui:<sprite>.",
                    "items: inventory slot -> item. 0-8 hotbar, 9-35 inventory, 36 boots, 37 leggings,",
                    "38 chestplate, 39 helmet, 40 offhand."
                )
            );
        y.set("defaults-version", 4);
        y.set("known-defaults", new ArrayList<>(this.knownDefaults));

        for (Map.Entry<String, String> e : this.defaultHash.entrySet()) {
            y.set("default-hashes." + e.getKey(), e.getValue());
        }

        for (Kit k : this.sorted()) {
            this.write(y, "kits." + k.id + ".", k);
        }

        try {
            y.save(this.file);
        } catch (IOException var9) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save kits.yml", (Throwable)var9);
        }
    }

    private void write(YamlConfiguration y, String base, Kit k) {
        {
            y.set(base + "name", k.displayName);
            y.set(base + "color", k.color);
            y.set(base + "sprite", k.sprite);
            y.set(base + "glyph", k.glyph);
            y.set(base + "icon", k.icon);
            y.set(base + "category", k.category.name());
            y.set(base + "order", k.order);
            y.set(base + "enabled", k.enabled);
            y.set(base + "ranked", k.ranked);
            y.set(base + "unranked", k.unranked);
            y.set(base + "ffa", k.ffa);
            y.set(base + "build", k.build);
            y.set(base + "natural-regen", k.regen);
            y.set(base + "hunger", k.hunger);
            y.set(base + "rounds-to-win", k.roundsToWin);
            y.set(base + "build-height", k.buildHeight);
            y.set(base + "pearl-cooldown", k.pearlCooldown);
            y.set(base + "wind-charge-cooldown", k.windCooldown);
            y.set(base + "hit-delay", k.hitDelay);
            y.set(base + "knockback", k.knockback);
            y.set(base + "rounds-vote", k.roundsVote);
            y.set(base + "arena-type", k.arenaType);
            y.set(base + "round-throw", k.throwMode);
            y.set(base + "arenas", new ArrayList<>(k.arenas));
            y.set(base + "version", k.version);
            y.set(base + "description", k.description);
            List<String> votes = new ArrayList<>();

            for (KitVote v : k.votes) {
                votes.add(v.serialize());
            }

            y.set(base + "votes", votes);
            List<String> effects = new ArrayList<>();

            for (PotionEffect pe : k.effects) {
                effects.add(pe.getType().getKey().getKey() + ":" + pe.getAmplifier() + ":" + pe.getDuration());
            }

            y.set(base + "effects", effects);

            for (int i = 0; i < 41; i++) {
                if (!Items.isEmpty(k.contents[i])) {
                    y.set(base + "items." + i, k.contents[i]);
                }
            }
        }
    }

    public Kit get(String id) {
        return id == null ? null : this.kits.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<Kit> all() {
        return this.kits.values();
    }

    public List<Kit> sorted() {
        List<Kit> list = new ArrayList<>(this.kits.values());
        list.sort(Comparator.<Kit>comparingInt(k -> k.category.ordinal()).thenComparingInt(k -> k.order).thenComparing(k -> k.id));
        return list;
    }

    public List<Kit> byCategory(KitCategory cat) {
        List<Kit> out = new ArrayList<>();

        for (Kit k : this.sorted()) {
            if (k.category == cat && k.enabled) {
                out.add(k);
            }
        }

        return out;
    }

    public List<Kit> duelKits() {
        List<Kit> out = new ArrayList<>();

        for (Kit k : this.sorted()) {
            if (k.enabled && (k.ranked || k.unranked)) {
                out.add(k);
            }
        }

        return out;
    }

    public List<Kit> ffaKits() {
        List<Kit> out = new ArrayList<>();

        for (Kit k : this.sorted()) {
            if (k.ffa) {
                out.add(k);
            }
        }

        return out;
    }

    public List<String> ids() {
        return new ArrayList<>(this.kits.keySet());
    }

    public Kit create(String id, String name, Player source) {
        Kit k = new Kit(id, name);
        k.category = KitCategory.OTHER;
        k.order = this.kits.size();
        if (source != null) {
            k.contents = copyFrom(source.getInventory());
            ItemStack hand = source.getInventory().getItemInMainHand();
            if (!Items.isEmpty(hand)) {
                k.icon = new ItemStack(hand.getType());
            }
        }

        this.kits.put(id, k);
        this.save();
        return k;
    }

    /** A new kit with every setting, item, vote and effect of {@code from}: the quickest way to make your own kit. */
    public Kit copy(Kit from, String id, String name) {
        YamlConfiguration y = new YamlConfiguration();
        this.write(y, "k.", from);
        Kit k = this.read(id, y.getConfigurationSection("k"));
        k.displayName = name;
        k.order = this.kits.size();
        k.version = 1;
        // a copy starts outside FFA: switching FFA on in the kit editor builds its FFA arena
        k.ffa = false;
        this.kits.put(id, k);
        this.save();
        return k;
    }

    public void delete(String id) {
        this.kits.remove(id);
        this.save();
    }

    public static ItemStack[] copyFrom(PlayerInventory inv) {
        ItemStack[] out = new ItemStack[41];
        ItemStack[] storage = inv.getStorageContents();

        for (int i = 0; i < 36 && i < storage.length; i++) {
            out[i] = clean(storage[i]);
        }

        out[36] = clean(inv.getBoots());
        out[37] = clean(inv.getLeggings());
        out[38] = clean(inv.getChestplate());
        out[39] = clean(inv.getHelmet());
        out[40] = clean(inv.getItemInOffHand());
        return out;
    }

    private static ItemStack clean(ItemStack it) {
        if (Items.isEmpty(it)) {
            return null;
        } else {
            ItemStack c = it.clone();
            return Items.tag(c, Items.ACTION) != null ? null : Items.withoutTag(c, Items.KIT_SLOT);
        }
    }

    private static boolean arrangeable(int slot) {
        return slot >= 0 && slot < 36 || slot == 40;
    }

    public boolean usable(Kit kit, KitLayout layout) {
        return layout != null && this.applyLayout(kit, layout, new ItemStack[41]);
    }

    public ItemStack[] arrange(Kit kit, KitLayout layout) {
        ItemStack[] out = new ItemStack[41];

        for (int i = 36; i < 40; i++) {
            out[i] = kit.contents[i] == null ? null : kit.contents[i].clone();
        }

        if (layout != null && this.applyLayout(kit, layout, out)) {
            return out;
        } else {
            for (int i = 0; i < 41; i++) {
                if (arrangeable(i)) {
                    out[i] = kit.contents[i] == null ? null : kit.contents[i].clone();
                }
            }

            return out;
        }
    }

    private boolean applyLayout(Kit kit, KitLayout layout, ItemStack[] out) {
        Map<Integer, Integer> totals = new HashMap<>();
        ItemStack[] temp = new ItemStack[41];

        for (KitLayout.Entry e : layout.entries) {
            if (arrangeable(e.orig()) && arrangeable(e.slot()) && e.amount() > 0) {
                ItemStack base = kit.contents[e.orig()];
                if (!Items.isEmpty(base) && temp[e.slot()] == null) {
                    ItemStack copy = base.clone();
                    copy.setAmount(e.amount());
                    temp[e.slot()] = copy;
                    totals.merge(e.orig(), e.amount(), Integer::sum);
                    continue;
                }

                return false;
            }

            return false;
        }

        for (int i = 0; i < 41; i++) {
            if (arrangeable(i)) {
                ItemStack base = kit.contents[i];
                int expected = Items.isEmpty(base) ? 0 : base.getAmount();
                if (totals.getOrDefault(i, 0) != expected) {
                    return false;
                }
            }
        }

        for (int ix = 0; ix < 41; ix++) {
            if (arrangeable(ix)) {
                out[ix] = temp[ix];
            }
        }

        return true;
    }

    public static void applyVotes(ItemStack[] contents, List<KitVote> votes) {
        if (votes != null) {
            for (KitVote v : votes) {
                if (!v.itemVote()) {
                    continue;
                }

                if (v.action() == KitVote.Action.REMOVE) {
                    for (int i = 0; i < contents.length; i++) {
                        if (!Items.isEmpty(contents[i]) && contents[i].getType() == v.material()) {
                            contents[i] = null;
                        }
                    }
                } else {
                    int total = 0;

                    for (ItemStack it : contents) {
                        if (!Items.isEmpty(it) && it.getType() == v.material()) {
                            total += it.getAmount();
                        }
                    }

                    int excess = total - v.amount();
                    int[] order = new int[37];
                    int n = 0;
                    int ix = 35;

                    while (ix >= 0) {
                        order[n++] = ix--;
                    }

                    order[n] = 40;

                    for (int slot : order) {
                        if (excess <= 0) {
                            break;
                        }

                        ItemStack itx = contents[slot];
                        if (!Items.isEmpty(itx) && itx.getType() == v.material()) {
                            int take = Math.min(excess, itx.getAmount());
                            excess -= take;
                            if (take >= itx.getAmount()) {
                                contents[slot] = null;
                            } else {
                                itx.setAmount(itx.getAmount() - take);
                            }
                        }
                    }
                }
            }
        }
    }

    public void apply(Player p, Kit kit, KitLayout layout, List<KitVote> votes) {
        ItemStack[] c = this.arrange(kit, layout);
        if (this.usable(kit, layout)) {
            p.sendActionBar(Text.mm("<#FFB547>✎ <#FFD27A>Your layout <dark_gray>• ").append(kit.label()));
        }

        applyVotes(c, votes);
        tuneCooldowns(c, kit);
        net.vortexrealm.cosmetics.Cosmetics cos = this.plugin instanceof net.vortexrealm.VortexRealm pp ? pp.cosmetics() : null;
        if (cos != null) {
            try {
                cos.apply(p, kit.id, c);
            } catch (RuntimeException var9) {
                this.plugin.getLogger().log(java.util.logging.Level.FINE, "Cosmetics failed for " + p.getName(), (Throwable)var9);
            }
        }

        p.setMaximumNoDamageTicks(kit.hitDelayTicks());
        PlayerInventory inv = p.getInventory();
        inv.clear();
        p.setItemOnCursor(null);
        inv.setStorageContents(Arrays.copyOfRange(c, 0, 36));
        inv.setBoots(c[36]);
        inv.setLeggings(c[37]);
        inv.setChestplate(c[38]);
        inv.setHelmet(c[39]);
        inv.setItemInOffHand(c[40]);
        inv.setHeldItemSlot(0);

        for (PotionEffect pe : kit.effects) {
            p.addPotionEffect(pe);
        }

        p.updateInventory();
    }

    private static void tuneCooldowns(ItemStack[] c, Kit kit) {
        int pearl = kit.pearlCooldownTicks();
        int wind = kit.windCooldownTicks();

        for (int i = 0; i < c.length; i++) {
            ItemStack it = c[i];
            if (it != null && !it.isEmpty()) {
                int ticks;
                int vanilla;
                if (it.getType() == Material.ENDER_PEARL) {
                    ticks = pearl;
                    vanilla = 20;
                } else {
                    if (it.getType() != Material.WIND_CHARGE) {
                        continue;
                    }

                    ticks = wind;
                    vanilla = 10;
                }

                if (ticks > 0 && ticks != vanilla) {
                    ItemStack copy = it.clone();
                    copy.setData(DataComponentTypes.USE_COOLDOWN, UseCooldown.useCooldown(ticks / 20.0F).cooldownGroup(it.getType().getKey()).build());
                    c[i] = copy;
                }
            }
        }
    }

    public ItemStack[] editorContents(Kit kit, KitLayout layout) {
        ItemStack[] arranged = this.arrange(kit, layout);
        ItemStack[] out = new ItemStack[41];
        boolean custom = this.usable(kit, layout);
        if (custom) {
            for (KitLayout.Entry e : layout.entries) {
                out[e.slot()] = Items.withIntTag(arranged[e.slot()], Items.KIT_SLOT, e.orig());
            }
        } else {
            for (int i = 0; i < 41; i++) {
                if (arrangeable(i) && !Items.isEmpty(arranged[i])) {
                    out[i] = Items.withIntTag(arranged[i], Items.KIT_SLOT, i);
                }
            }
        }

        for (int ix = 36; ix < 40; ix++) {
            out[ix] = arranged[ix];
        }

        return out;
    }

    public KitLayout layoutFrom(Kit kit, ItemStack[] storage, ItemStack offhand) {
        List<KitLayout.Entry> entries = new ArrayList<>();
        Map<Integer, Integer> totals = new HashMap<>();

        for (int slot = 0; slot < 36 && slot < storage.length; slot++) {
            ItemStack it = storage[slot];
            if (!Items.isEmpty(it)) {
                Integer orig = Items.intTag(it, Items.KIT_SLOT);
                if (orig == null || !arrangeable(orig)) {
                    return null;
                }

                entries.add(new KitLayout.Entry(orig, slot, it.getAmount()));
                totals.merge(orig, it.getAmount(), Integer::sum);
            }
        }

        if (!Items.isEmpty(offhand)) {
            Integer orig = Items.intTag(offhand, Items.KIT_SLOT);
            if (orig == null || !arrangeable(orig)) {
                return null;
            }

            entries.add(new KitLayout.Entry(orig, 40, offhand.getAmount()));
            totals.merge(orig, offhand.getAmount(), Integer::sum);
        }

        for (int i = 0; i < 41; i++) {
            if (arrangeable(i)) {
                int expected = Items.isEmpty(kit.contents[i]) ? 0 : kit.contents[i].getAmount();
                if (totals.getOrDefault(i, 0) != expected) {
                    return null;
                }
            }
        }

        return new KitLayout(kit.version, entries);
    }
}
