package net.vortexpvp.plus;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.smp.ModeManager;
import net.vortexpvp.smp.WorldMode;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Sign;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Eco SMP extras: spawner stacking (right-click a spawner with the same spawner), crates opened with keys, chest
 * shops ([Shop] signs on chests), a bid house with timed auctions, a daily deal in the shard shop, world events
 * (airdrops, meteors with ores, King of the Hill) and a season wipe that rewards the richest players first.
 */
public final class EcoPlus implements Listener {
    private final VortexPvP plugin;
    private final PlusModule plus;
    private final NamespacedKey spawnerType;
    private final NamespacedKey stackKey;
    private final Random random = new Random();
    private final Map<String, Long> airdrops = new HashMap<>();
    private Koth koth;
    private long nextEvent;
    private String wipeCode;

    private static final class Koth {
        final Location at;
        final Map<UUID, Integer> points = new HashMap<>();
        final TextDisplay label;
        int seconds;

        Koth(Location at, TextDisplay label) {
            this.at = at;
            this.label = label;
        }
    }

    public EcoPlus(VortexPvP plugin, PlusModule plus) {
        this.plugin = plugin;
        this.plus = plus;
        this.spawnerType = new NamespacedKey(plugin, "spawner_type");
        this.stackKey = new NamespacedKey(plugin, "vp_stack");
        this.nextEvent = System.currentTimeMillis() + plus.config().i("eco-events.every-minutes", 45) * 60000L;
    }

    public World ecoWorld() {
        for (World w : Bukkit.getWorlds()) {
            if (this.plugin.modes() != null && this.plugin.modes().mode(w) == WorldMode.ECO) {
                return w;
            }
        }

        return null;
    }

    private boolean inEco(Player p) {
        return this.plugin.economy() != null && this.plugin.economy().inEco(p);
    }

    // ------------------------------------------------------------------ spawner stacking

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStack(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.SPAWNER || !this.plus.config().on("spawner-stacking")) {
            return;
        }

        ItemStack hand = e.getItem();
        Player p = e.getPlayer();
        if (hand == null || hand.getType() != Material.SPAWNER || !this.inEco(p) || p.isSneaking()) {
            return;
        }

        if (this.plugin.claims() != null && !this.plugin.claims().mayUse(p, e.getClickedBlock().getLocation())) {
            return;
        }

        if (!(e.getClickedBlock().getState() instanceof CreatureSpawner cs)) {
            return;
        }

        String type = hand.hasItemMeta() ? hand.getItemMeta().getPersistentDataContainer().get(this.spawnerType, PersistentDataType.STRING) : null;
        if (type == null || cs.getSpawnedType() == null || !cs.getSpawnedType().name().equals(type)) {
            p.sendActionBar(Text.mm("<red>Stack spawners of the same mob only"));
            return;
        }

        int stack = cs.getPersistentDataContainer().getOrDefault(this.stackKey, PersistentDataType.INTEGER, 1);
        int max = this.plus.config().i("spawner-stacking.max", 10);
        if (stack >= max) {
            p.sendActionBar(Text.mm("<red>This spawner is fully stacked (" + max + ")"));
            return;
        }

        e.setCancelled(true);
        stack++;
        cs.getPersistentDataContainer().set(this.stackKey, PersistentDataType.INTEGER, stack);
        cs.setSpawnCount(4 * stack);
        cs.setMaxNearbyEntities(Math.max(6, 6 * stack));
        cs.update(true, false);
        hand.setAmount(hand.getAmount() - 1);
        p.sendActionBar(Text.mm("<green>Spawner stacked: <white>x" + stack));
        Snd.play(p, "block.anvil.use", 0.6F, 1.6F);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, e.getClickedBlock().getLocation().add(0.5, 1.1, 0.5), 12, 0.3, 0.2, 0.3, 0.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.SPAWNER && e.getBlock().getState() instanceof CreatureSpawner cs) {
            int stack = cs.getPersistentDataContainer().getOrDefault(this.stackKey, PersistentDataType.INTEGER, 1);
            if (stack > 1 && cs.getSpawnedType() != null) {
                // the extra spawners of the stack come back as items
                ItemStack it = Items.of(Material.SPAWNER).name("<yellow>" + Progression.nice(cs.getSpawnedType().name().toLowerCase(Locale.ROOT)) + " Spawner").build();
                it.editMeta(m -> m.getPersistentDataContainer().set(this.spawnerType, PersistentDataType.STRING, cs.getSpawnedType().name()));
                it.setAmount(stack - 1);
                e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), it);
            }
        }
    }

    // ------------------------------------------------------------------ crates

    private List<String> crateTypes() {
        ConfigurationSection s = this.plus.config().section("crates");
        return s == null ? List.of("vote", "rare", "legendary") : new ArrayList<>(s.getKeys(false));
    }

    private List<String> crateRewards(String type) {
        List<String> def = switch (type) {
            case "legendary" -> List.of("10:shards:1000", "10:money:25000", "15:item:NETHERITE_INGOT:2", "10:item:ENCHANTED_GOLDEN_APPLE:2", "15:unlock:win:dragon", "10:unlock:pet:sniffer",
                "15:item:TOTEM_OF_UNDYING:1", "15:title:Legendary");
            case "rare" -> List.of("20:shards:300", "20:money:5000", "15:item:DIAMOND:8", "15:item:GOLDEN_APPLE:6", "10:unlock:win:souls", "10:item:ELYTRA:1", "10:key:legendary:1");
            default -> List.of("30:shards:60", "25:money:750", "20:item:IRON_INGOT:16", "15:item:EXPERIENCE_BOTTLE:16", "5:unlock:pet:frog", "5:key:rare:1");
        };
        return this.plus.config().list("crates." + type + ".rewards", def);
    }

    public long keys(UUID u, String type) {
        return this.plus.store().get(u).count("key." + type);
    }

    public void giveKeys(UUID u, String type, long n) {
        PlayerData d = this.plus.store().get(u);
        d.add("key." + type, n);
        Player p = Bukkit.getPlayer(u);
        if (p != null) {
            p.sendMessage(Text.mm("<gold>🗝 +" + n + " " + Progression.nice(type) + " crate key" + (n == 1 ? "" : "s") + " <click:run_command:'/crate'><yellow>[open]</yellow></click>"));
        }
    }

    public void crates(Player p) {
        Gui.open(this.plugin, p, "<dark_gray>Crates", 3, g -> {
            int slot = 11;
            for (String t : this.crateTypes()) {
                long k = this.keys(p.getUniqueId(), t);
                List<String> lore = new ArrayList<>();
                lore.add("<gray>Keys: <white>" + k);
                lore.add("");
                for (String r : this.crateRewards(t)) {
                    String[] parts = r.split(":", 2);
                    lore.add("<dark_gray>• <gray>" + Text.esc(this.rewardName(parts.length > 1 ? parts[1] : r)));
                }

                lore.add("");
                lore.add(k > 0 ? "<green>▶ Click to open" : "<red>You have no key");
                Material icon = t.equals("legendary") ? Material.ENDER_CHEST : (t.equals("rare") ? Material.TRAPPED_CHEST : Material.CHEST);
                g.item(slot, Items.of(icon).name("<gold><b>" + Progression.nice(t) + " Crate").lore(lore).build(), c -> {
                    if (k > 0) {
                        this.open(p, t);
                    } else {
                        Snd.error(p);
                    }
                });
                slot += 2;
            }
        });
    }

    private String rewardName(String r) {
        String[] p = r.split(":");
        return switch (p[0]) {
            case "shards" -> p[1] + " shards";
            case "money" -> "$" + p[1];
            case "item" -> (p.length > 2 ? p[2] + "x " : "") + Progression.nice(p[1].toLowerCase(Locale.ROOT));
            case "unlock" -> Progression.unlockName(r.substring(7));
            case "title" -> "Title: " + r.substring(6);
            case "key" -> Progression.nice(p[1]) + " key";
            default -> r;
        };
    }

    private String roll(String type) {
        List<String> list = this.crateRewards(type);
        int total = 0;
        for (String r : list) {
            total += Integer.parseInt(r.split(":", 2)[0]);
        }

        int x = this.random.nextInt(Math.max(1, total));
        for (String r : list) {
            String[] parts = r.split(":", 2);
            x -= Integer.parseInt(parts[0]);
            if (x < 0) {
                return parts[1];
            }
        }

        return list.get(0).split(":", 2)[1];
    }

    public void open(Player p, String type) {
        PlayerData d = this.plus.store().get(p);
        if (d.count("key." + type) <= 0) {
            return;
        }

        d.add("key." + type, -1L);
        String won = this.roll(type);
        List<String> pool = this.crateRewards(type);
        int[] step = {0};
        Gui[] holder = new Gui[1];
        holder[0] = Gui.open(this.plugin, p, "<dark_gray>Opening " + Progression.nice(type) + " Crate...", 3, g -> {
        });
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = Tasks.timer(2L, 2L, () -> {
            step[0]++;
            if (!p.isOnline()) {
                task[0].cancel();
                this.grant(p.getUniqueId(), won);
                return;
            }

            Inventory inv = p.getOpenInventory().getTopInventory();
            boolean last = step[0] >= 20;
            for (int i = 9; i < 18; i++) {
                String r = last && i == 13 ? won : pool.get(this.random.nextInt(pool.size())).split(":", 2)[1];
                if (inv.getSize() >= 27) {
                    inv.setItem(i, Items.of(i == 13 ? Material.GOLD_BLOCK : Material.PAPER).name("<yellow>" + Text.esc(this.rewardName(r))).build());
                }
            }

            Snd.play(p, "block.note_block.hat", 0.6F, 0.8F + step[0] * 0.05F);
            if (last) {
                task[0].cancel();
                this.grant(p.getUniqueId(), won);
                p.sendMessage(Text.mm("<gold>🎁 You won: <white>" + Text.esc(this.rewardName(won))));
                Snd.play(p, "ui.toast.challenge_complete", 1.0F, 1.2F);
                if (type.equals("legendary")) {
                    Bukkit.broadcast(Text.mm("<gold>🎁 <white>" + Text.esc(p.getName()) + " <gray>won <yellow>" + Text.esc(this.rewardName(won)) + " <gray>from a Legendary crate!"));
                }

                Tasks.later(30L, () -> {
                    if (p.isOnline() && p.getOpenInventory().getTopInventory() == inv) {
                        p.closeInventory();
                    }
                });
            }
        });
    }

    public void grant(UUID u, String r) {
        String[] p = r.split(":");
        switch (p[0]) {
            case "shards" -> this.plus.progression().shards(u, Long.parseLong(p[1]));
            case "money" -> {
                if (this.plugin.economy() != null) {
                    this.plugin.economy().give(u, Double.parseDouble(p[1]));
                }
            }
            case "item" -> {
                Material m = Material.matchMaterial(p[1]);
                if (m != null) {
                    this.deliver(u, new ItemStack(m, p.length > 2 ? Integer.parseInt(p[2]) : 1));
                }
            }
            case "unlock" -> this.plus.progression().unlock(u, r.substring(7));
            case "title" -> this.plus.progression().title(u, r.substring(6));
            case "key" -> this.giveKeys(u, p[1], p.length > 2 ? Long.parseLong(p[2]) : 1L);
            default -> {
            }
        }
    }

    /** Gives an item now, or keeps it for the next join (bid house wins and returns, crate items). */
    public void deliver(UUID u, ItemStack it) {
        Player p = Bukkit.getPlayer(u);
        if (p != null && (this.inEco(p) || !this.plus.config().bool("eco.deliver-in-eco-only", true))) {
            for (ItemStack left : p.getInventory().addItem(it).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }

            return;
        }

        YamlConfiguration y = this.plus.store().doc("eco");
        List<String> pending = new ArrayList<>(y.getStringList("pending." + u));
        pending.add(Base64.getEncoder().encodeToString(it.serializeAsBytes()));
        y.set("pending." + u, pending);
        this.plus.store().dirty("eco");
        if (p != null) {
            p.sendMessage(Text.mm("<gray>An item is waiting for you - it is given when you are in the Eco SMP."));
        }
    }

    /** Every 10 seconds: items waiting for players who are now in the eco world. */
    public void deliverPending() {
        YamlConfiguration y = this.plus.store().doc("eco");
        ConfigurationSection s = y.getConfigurationSection("pending");
        if (s == null) {
            return;
        }

        for (String key : new ArrayList<>(s.getKeys(false))) {
            Player p;
            try {
                p = Bukkit.getPlayer(UUID.fromString(key));
            } catch (IllegalArgumentException var8) {
                continue;
            }

            if (p == null || !this.inEco(p) && this.plus.config().bool("eco.deliver-in-eco-only", true)) {
                continue;
            }

            for (String b64 : y.getStringList("pending." + key)) {
                try {
                    ItemStack it = ItemStack.deserializeBytes(Base64.getDecoder().decode(b64));
                    for (ItemStack left : p.getInventory().addItem(it).values()) {
                        p.getWorld().dropItemNaturally(p.getLocation(), left);
                    }
                } catch (RuntimeException var9) {
                }
            }

            y.set("pending." + key, null);
            this.plus.store().dirty("eco");
            p.sendMessage(Text.mm("<green>Items that were waiting for you have been delivered."));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Tasks.later(100L, this::deliverPending);
    }

    // ------------------------------------------------------------------ chest shops

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private Block chestOf(Block sign) {
        if (sign.getBlockData() instanceof WallSign ws) {
            Block behind = sign.getRelative(ws.getFacing().getOppositeFace());
            if (behind.getState() instanceof Container) {
                return behind;
            }
        }

        Block below = sign.getRelative(BlockFace.DOWN);
        return below.getState() instanceof Container ? below : null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSign(SignChangeEvent e) {
        String first = Text.plain(e.line(0) == null ? net.kyori.adventure.text.Component.empty() : e.line(0)).trim();
        if (!first.equalsIgnoreCase("[shop]") || !this.plus.config().on("chest-shops")) {
            return;
        }

        Player p = e.getPlayer();
        Block chest = this.chestOf(e.getBlock());
        if (!this.inEco(p) || chest == null) {
            p.sendMessage(Text.mm("<red>Put a [Shop] sign on a chest in the Eco SMP. <gray>Line 2: amount, line 3: price."));
            return;
        }

        int amount;
        double price;
        try {
            amount = Integer.parseInt(Text.plain(e.line(1)).replaceAll("[^0-9]", ""));
            price = Double.parseDouble(Text.plain(e.line(2)).replaceAll("[^0-9.]", ""));
        } catch (RuntimeException bad) {
            p.sendMessage(Text.mm("<red>Line 2 must be the amount and line 3 the price, e.g. 16 and 250."));
            return;
        }

        Material item = null;
        for (ItemStack it : ((Container)chest.getState()).getInventory().getContents()) {
            if (it != null && it.getType() != Material.AIR) {
                item = it.getType();
                break;
            }
        }

        if (item == null || amount <= 0 || amount > 3456 || price <= 0) {
            p.sendMessage(Text.mm("<red>Put the items to sell in the chest first, then place the sign."));
            return;
        }

        YamlConfiguration y = this.plus.store().doc("eco");
        String k = key(e.getBlock());
        y.set("shops." + k + ".owner", p.getUniqueId().toString());
        y.set("shops." + k + ".name", p.getName());
        y.set("shops." + k + ".item", item.name());
        y.set("shops." + k + ".amount", amount);
        y.set("shops." + k + ".price", price);
        y.set("shops." + k + ".chest", key(chest));
        this.plus.store().dirty("eco");
        e.line(0, Text.mm("<dark_green><b>[Shop]"));
        e.line(1, Text.mm("<black>" + amount + "x " + Progression.nice(item.name().toLowerCase(Locale.ROOT))));
        e.line(2, Text.mm("<dark_blue>" + this.plugin.economy().money(price)));
        e.line(3, Text.mm("<gray>" + p.getName()));
        p.sendMessage(Text.mm("<green>Chest shop made: " + amount + "x " + Progression.nice(item.name().toLowerCase(Locale.ROOT)) + " for " + this.plugin.economy().money(price)));
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onShopClick(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || !(e.getClickedBlock().getState() instanceof Sign)) {
            return;
        }

        YamlConfiguration y = this.plus.store().doc("eco");
        String k = key(e.getClickedBlock());
        ConfigurationSection s = y.getConfigurationSection("shops." + k);
        if (s == null) {
            return;
        }

        e.setCancelled(true);
        Player p = e.getPlayer();
        UUID owner = UUID.fromString(s.getString("owner"));
        Block chest = this.chestOf(e.getClickedBlock());
        if (chest == null) {
            return;
        }

        if (owner.equals(p.getUniqueId())) {
            p.sendMessage(Text.mm("<gray>This is your shop. Fill the chest to keep it stocked; break the sign to close it."));
            return;
        }

        Material m = Material.matchMaterial(s.getString("item", "STONE"));
        int amount = s.getInt("amount");
        double price = s.getDouble("price");
        Inventory inv = ((Container)chest.getState()).getInventory();
        if (m == null || !inv.containsAtLeast(new ItemStack(m), amount)) {
            p.sendMessage(Text.mm("<red>This shop is out of stock."));
            return;
        }

        if (!this.plugin.economy().take(p.getUniqueId(), price)) {
            p.sendMessage(Text.mm("<red>You need " + this.plugin.economy().money(price) + "."));
            return;
        }

        this.plugin.economy().give(owner, price);
        inv.removeItem(new ItemStack(m, amount));
        for (ItemStack left : p.getInventory().addItem(new ItemStack(m, amount)).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }

        p.sendMessage(Text.mm("<green>Bought " + amount + "x " + Progression.nice(m.name().toLowerCase(Locale.ROOT)) + " for " + this.plugin.economy().money(price)));
        Player o = Bukkit.getPlayer(owner);
        if (o != null) {
            o.sendMessage(Text.mm("<green>" + Text.esc(p.getName()) + " bought " + amount + "x " + Progression.nice(m.name().toLowerCase(Locale.ROOT)) + " from your shop (+"
                + this.plugin.economy().money(price) + ")"));
        }

        Snd.play(p, "entity.experience_orb.pickup", 1.0F, 1.0F);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShopBreak(BlockBreakEvent e) {
        YamlConfiguration y = this.plus.store().doc("eco");
        ConfigurationSection shops = y.getConfigurationSection("shops");
        if (shops == null) {
            return;
        }

        String k = key(e.getBlock());
        for (String sk : shops.getKeys(false)) {
            if (sk.equals(k) || k.equals(shops.getString(sk + ".chest"))) {
                boolean owner = e.getPlayer().getUniqueId().toString().equals(shops.getString(sk + ".owner")) || e.getPlayer().hasPermission("vortexpvp.admin");
                if (!owner) {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage(Text.mm("<red>That is " + Text.esc(shops.getString(sk + ".name", "someone")) + "'s shop."));
                    return;
                }

                y.set("shops." + sk, null);
                this.plus.store().dirty("eco");
                e.getPlayer().sendMessage(Text.mm("<gray>Shop closed."));
                return;
            }
        }
    }

    // ------------------------------------------------------------------ bid house

    public void bidCommand(Player p, String[] a) {
        if (!this.inEco(p) && !p.hasPermission("vortexpvp.admin")) {
            p.sendMessage(Text.mm("<red>The bid house is part of the Eco SMP."));
            return;
        }

        if (a.length >= 2 && a[0].equalsIgnoreCase("sell")) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            double start;
            int minutes;
            try {
                start = Double.parseDouble(a[1]);
                minutes = a.length > 2 ? Math.max(5, Math.min(1440, Integer.parseInt(a[2]))) : 60;
            } catch (NumberFormatException bad) {
                p.sendMessage(Text.mm("<red>/bid sell <starting price> [minutes]"));
                return;
            }

            if (hand.getType() == Material.AIR || start <= 0) {
                p.sendMessage(Text.mm("<red>Hold the item you want to auction."));
                return;
            }

            YamlConfiguration y = this.plus.store().doc("eco");
            int id = y.getInt("bid-next", 1);
            y.set("bid-next", id + 1);
            String b = "bids." + id;
            y.set(b + ".seller", p.getUniqueId().toString());
            y.set(b + ".seller-name", p.getName());
            y.set(b + ".item", Base64.getEncoder().encodeToString(hand.serializeAsBytes()));
            y.set(b + ".price", start);
            y.set(b + ".ends", System.currentTimeMillis() + minutes * 60000L);
            this.plus.store().dirty("eco");
            p.getInventory().setItemInMainHand(null);
            p.sendMessage(Text.mm("<green>Auction #" + id + " started: " + this.plugin.economy().money(start) + " starting bid, " + minutes + " minutes."));
            return;
        }

        this.bidMenu(p);
    }

    public void bidMenu(Player p) {
        YamlConfiguration y = this.plus.store().doc("eco");
        ConfigurationSection bids = y.getConfigurationSection("bids");
        Gui.open(this.plugin, p, "<dark_gray>Bid House", 6, g -> {
            int[] slots = g.inner();
            int i = 0;
            if (bids != null) {
                for (String id : bids.getKeys(false)) {
                    if (i >= slots.length) {
                        break;
                    }

                    ConfigurationSection b = bids.getConfigurationSection(id);
                    ItemStack it;
                    try {
                        it = ItemStack.deserializeBytes(Base64.getDecoder().decode(b.getString("item", "")));
                    } catch (RuntimeException var13) {
                        continue;
                    }

                    double price = b.getDouble("price");
                    String bidder = b.getString("bidder-name");
                    double next = bidder == null ? price : Math.ceil(price * 1.1 + 1);
                    long left = Math.max(0L, b.getLong("ends") - System.currentTimeMillis()) / 1000L;
                    boolean mine = p.getUniqueId().toString().equals(b.getString("seller"));
                    ItemStack shown = it.clone();
                    shown.editMeta(m -> {
                        List<net.kyori.adventure.text.Component> lore = m.lore() == null ? new ArrayList<>() : new ArrayList<>(m.lore());
                        lore.add(Text.noItalic(Text.mm("<dark_gray>───────────")));
                        lore.add(Text.noItalic(Text.mm("<gray>Seller: <white>" + Text.esc(b.getString("seller-name", "?")))));
                        lore.add(Text.noItalic(Text.mm("<gray>" + (bidder == null ? "Starting bid" : "Top bid") + ": <gold>" + this.plugin.economy().money(price)
                            + (bidder == null ? "" : " <gray>by <white>" + Text.esc(bidder)))));
                        lore.add(Text.noItalic(Text.mm("<gray>Ends in: <white>" + left / 3600 + "h " + left / 60 % 60 + "m " + left % 60 + "s")));
                        lore.add(Text.noItalic(Text.mm(mine ? "<gray>Your auction" : "<green>▶ Click to bid " + this.plugin.economy().money(next))));
                        m.lore(lore);
                    });
                    g.item(slots[i++], shown, c -> {
                        if (!mine) {
                            this.bid(p, id, next);
                            this.bidMenu(p);
                        }
                    });
                }
            }

            g.item(49, Items.of(Material.BOOK).name("<aqua>Sell something").lore("<gray>Hold an item and type", "<white>/bid sell <starting price> [minutes]").build());
        });
    }

    private void bid(Player p, String id, double amount) {
        YamlConfiguration y = this.plus.store().doc("eco");
        String b = "bids." + id;
        if (!y.contains(b) || y.getLong(b + ".ends") < System.currentTimeMillis()) {
            p.sendMessage(Text.mm("<red>That auction has ended."));
            return;
        }

        if (!this.plugin.economy().take(p.getUniqueId(), amount)) {
            p.sendMessage(Text.mm("<red>You need " + this.plugin.economy().money(amount) + " to bid."));
            return;
        }

        String prev = y.getString(b + ".bidder");
        if (prev != null) {
            UUID pu = UUID.fromString(prev);
            this.plugin.economy().give(pu, y.getDouble(b + ".price"));
            Player pp = Bukkit.getPlayer(pu);
            if (pp != null) {
                pp.sendMessage(Text.mm("<yellow>You were outbid on auction #" + id + " - your money was refunded. <click:run_command:'/bid'><aqua>[bid house]</aqua></click>"));
            }
        }

        y.set(b + ".bidder", p.getUniqueId().toString());
        y.set(b + ".bidder-name", p.getName());
        y.set(b + ".price", amount);
        // a bid in the last minute adds a minute (no sniping)
        if (y.getLong(b + ".ends") - System.currentTimeMillis() < 60000L) {
            y.set(b + ".ends", System.currentTimeMillis() + 60000L);
        }

        this.plus.store().dirty("eco");
        p.sendMessage(Text.mm("<green>You are the top bidder on #" + id + " with " + this.plugin.economy().money(amount) + "."));
    }

    /** Every 10 seconds: ends finished auctions. */
    public void bidTick() {
        YamlConfiguration y = this.plus.store().doc("eco");
        ConfigurationSection bids = y.getConfigurationSection("bids");
        if (bids == null) {
            return;
        }

        for (String id : new ArrayList<>(bids.getKeys(false))) {
            ConfigurationSection b = bids.getConfigurationSection(id);
            if (b.getLong("ends") > System.currentTimeMillis()) {
                continue;
            }

            ItemStack it;
            try {
                it = ItemStack.deserializeBytes(Base64.getDecoder().decode(b.getString("item", "")));
            } catch (RuntimeException var9) {
                y.set("bids." + id, null);
                continue;
            }

            UUID seller = UUID.fromString(b.getString("seller"));
            String bidder = b.getString("bidder");
            if (bidder == null) {
                this.deliver(seller, it);
            } else {
                this.deliver(UUID.fromString(bidder), it);
                this.plugin.economy().give(seller, b.getDouble("price"));
                Player s = Bukkit.getPlayer(seller);
                if (s != null) {
                    s.sendMessage(Text.mm("<green>Auction #" + id + " sold to " + Text.esc(b.getString("bidder-name")) + " for " + this.plugin.economy().money(b.getDouble("price"))));
                }

                Player w = Bukkit.getPlayer(UUID.fromString(bidder));
                if (w != null) {
                    w.sendMessage(Text.mm("<green>You won auction #" + id + "!"));
                }
            }

            y.set("bids." + id, null);
            this.plus.store().dirty("eco");
        }
    }

    // ------------------------------------------------------------------ daily deal

    private String[] deal() {
        List<String> pool = this.plus.config().list("deal.pool", List.of("ELYTRA:1:900", "NETHERITE_INGOT:1:700", "ENCHANTED_GOLDEN_APPLE:1:500", "TOTEM_OF_UNDYING:1:400",
            "DIAMOND:16:350", "SHULKER_BOX:1:450", "BEACON:1:800", "TRIDENT:1:600", "MENDING_BOOK:1:300", "EXPERIENCE_BOTTLE:64:250"));
        long day = LocalDate.now().toEpochDay();
        return pool.get((int)Math.floorMod(day * 2654435761L, pool.size())).split(":");
    }

    public int dealPrice() {
        return (int)Math.round(Integer.parseInt(this.deal()[2]) * (1.0 - this.plus.config().d("deal.discount", 0.3)));
    }

    public void dealMenu(Player p) {
        String[] d = this.deal();
        boolean bought = this.plus.store().get(p).count("deal." + LocalDate.now().toEpochDay()) > 0;
        Material m = d[0].equals("MENDING_BOOK") ? Material.ENCHANTED_BOOK : Material.matchMaterial(d[0]);
        int price = this.dealPrice();
        Gui.open(this.plugin, p, "<dark_gray>Daily Deal", 3, g -> g.item(13, Items.of(m == null ? Material.CHEST : m).amount(Integer.parseInt(d[1]))
            .name("<gold><b>Today's deal: <white>" + d[1] + "x " + Progression.nice(d[0].toLowerCase(Locale.ROOT)))
            .lore("<gray>Normally <strikethrough>" + d[2] + "</strikethrough> shards", "<light_purple>Today: " + price + " shards", "<gray>One per player, a new deal every day.", "",
                bought ? "<green>✔ Bought today" : "<yellow>▶ Click to buy")
            .glint(true).build(), c -> {
                if (bought) {
                    return;
                }

                if (this.plugin.economy() == null || !this.plugin.economy().takeShards(p.getUniqueId(), price)) {
                    p.sendMessage(Text.mm("<red>You need " + price + " shards."));
                    return;
                }

                ItemStack it;
                if (d[0].equals("MENDING_BOOK")) {
                    it = new ItemStack(Material.ENCHANTED_BOOK);
                    it.editMeta(org.bukkit.inventory.meta.EnchantmentStorageMeta.class, em -> em.addStoredEnchant(org.bukkit.enchantments.Enchantment.MENDING, 1, true));
                } else {
                    it = new ItemStack(m == null ? Material.CHEST : m, Integer.parseInt(d[1]));
                }

                this.plus.store().get(p).add("deal." + LocalDate.now().toEpochDay(), 1L);
                this.deliver(p.getUniqueId(), it);
                p.sendMessage(Text.mm("<green>Deal bought!"));
                this.dealMenu(p);
            }));
    }

    // ------------------------------------------------------------------ world events

    private Location randomSpot(World w) {
        Location spawn = this.plugin.modes().spawn(w);
        if (spawn == null) {
            spawn = w.getSpawnLocation();
        }

        int r = this.plus.config().i("eco-events.radius", 350);
        for (int i = 0; i < 20; i++) {
            int x = spawn.getBlockX() + this.random.nextInt(r * 2) - r;
            int z = spawn.getBlockZ() + this.random.nextInt(r * 2) - r;
            if (Math.abs(x - spawn.getBlockX()) < 80 && Math.abs(z - spawn.getBlockZ()) < 80) {
                continue;
            }

            if (this.plugin.claims() != null && this.plugin.claims().claimAt(w, x, z) != null) {
                continue;
            }

            Block top = w.getHighestBlockAt(x, z);
            if (top.isLiquid() || top.getY() < w.getMinHeight() + 5) {
                continue;
            }

            return top.getLocation().add(0.5, 1.0, 0.5);
        }

        return null;
    }

    private void ecoBroadcast(String mini) {
        World w = this.ecoWorld();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (w != null && p.getWorld() == w || p.hasPermission("vortexpvp.admin") && this.plus.config().bool("eco-events.tell-admins", false)) {
                p.sendMessage(Text.mm(mini));
            }
        }
    }

    public String startEvent(String type) {
        World w = this.ecoWorld();
        if (w == null) {
            return "There is no Eco SMP world.";
        }

        Location at = this.randomSpot(w);
        if (at == null) {
            return "No free spot found (everything near spawn is claimed or water).";
        }

        String coords = at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ();
        switch (type) {
            case "airdrop" -> {
                this.ecoBroadcast("<gold><b>📦 AIRDROP</b> <gray>incoming at <white>" + coords + "</white> - first come, first served!");
                BlockDisplay fall = w.spawn(at.clone().add(-0.5, 40.0, -0.5), BlockDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setBlock(Material.CHEST.createBlockData());
                    d.setTeleportDuration(3);
                });
                int[] t = {0};
                org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
                task[0] = Tasks.timer(2L, 2L, () -> {
                    t[0]++;
                    Location now = at.clone().add(-0.5, Math.max(0.0, 40.0 - t[0] * 1.0), -0.5);
                    fall.teleport(now);
                    w.spawnParticle(Particle.CLOUD, now.clone().add(0.5, 1.0, 0.5), 3, 0.2, 0.2, 0.2, 0.01);
                    if (t[0] >= 40) {
                        task[0].cancel();
                        fall.remove();
                        Block b = at.getBlock();
                        b.setType(Material.CHEST);
                        if (b.getState() instanceof Chest c) {
                            this.fillLoot(c.getBlockInventory());
                        }

                        w.spawnParticle(Particle.FIREWORK, at, 60, 0.5, 0.5, 0.5, 0.2);
                        w.playSound(at, "entity.generic.explode", 1.0F, 1.2F);
                        this.airdrops.put(key(b), System.currentTimeMillis());
                    }
                });
            }
            case "meteor" -> {
                this.ecoBroadcast("<red><b>☄ METEOR</b> <gray>falling near <white>" + coords + "</white> - it is full of ores!");
                int[] t = {0};
                org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
                Location from = at.clone().add(-40.0, 60.0, -20.0);
                task[0] = Tasks.timer(1L, 1L, () -> {
                    t[0]++;
                    double f = t[0] / 60.0;
                    Location now = from.clone().add(at.clone().subtract(from).toVector().multiply(f));
                    w.spawnParticle(Particle.FLAME, now, 25, 0.6, 0.6, 0.6, 0.05);
                    w.spawnParticle(Particle.LARGE_SMOKE, now, 10, 0.5, 0.5, 0.5, 0.02);
                    if (t[0] >= 60) {
                        task[0].cancel();
                        w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 3);
                        w.playSound(at, "entity.generic.explode", 3.0F, 0.6F);
                        this.meteor(at);
                    }
                });
            }
            case "koth" -> {
                this.stopKoth(false);
                TextDisplay label = w.spawn(at.clone().add(0.0, 3.0, 0.0), TextDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                    d.text(Text.mm("<gold><b>KING OF THE HILL</b>\n<gray>Stand here alone to score"));
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.5F, 1.5F, 1.5F), new Quaternionf()));
                });
                this.koth = new Koth(at, label);
                this.ecoBroadcast("<gold><b>👑 KING OF THE HILL</b> <gray>at <white>" + coords + "</white> for 5 minutes - hold it alone to win shards and a rare key!");
            }
            default -> {
                return "Unknown event: airdrop, meteor or koth.";
            }
        }

        return null;
    }

    private void fillLoot(Inventory inv) {
        List<String> loot = this.plus.config().list("airdrop.loot", List.of("DIAMOND:3-8", "GOLDEN_APPLE:2-4", "IRON_INGOT:8-24", "EXPERIENCE_BOTTLE:8-24", "ENDER_PEARL:2-6",
            "ENCHANTED_GOLDEN_APPLE:0-1", "NETHERITE_SCRAP:0-2", "EMERALD:4-12"));
        for (String l : loot) {
            String[] p = l.split(":");
            Material m = Material.matchMaterial(p[0]);
            String[] r = p.length > 1 ? p[1].split("-") : new String[]{"1"};
            int lo = Integer.parseInt(r[0]);
            int hi = r.length > 1 ? Integer.parseInt(r[1]) : lo;
            int n = lo + this.random.nextInt(Math.max(1, hi - lo + 1));
            if (m != null && n > 0) {
                inv.setItem(this.random.nextInt(inv.getSize()), new ItemStack(m, Math.min(64, n)));
            }
        }

        if (this.random.nextInt(4) == 0) {
            ItemStack key = Items.of(Material.TRIPWIRE_HOOK).name("<gold>Rare Crate Key <gray>(right-click)").glint(true).build();
            key.editMeta(m -> m.getPersistentDataContainer().set(this.stackKey, PersistentDataType.INTEGER, -7));
            inv.addItem(key);
        }
    }

    @EventHandler
    public void onKeyItem(PlayerInteractEvent e) {
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.TRIPWIRE_HOOK && it.hasItemMeta()
            && Integer.valueOf(-7).equals(it.getItemMeta().getPersistentDataContainer().get(this.stackKey, PersistentDataType.INTEGER))) {
            e.setCancelled(true);
            it.setAmount(it.getAmount() - 1);
            this.giveKeys(e.getPlayer().getUniqueId(), "rare", 1L);
        }
    }

    @EventHandler
    public void onDropEmpty(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Chest c && this.airdrops.containsKey(key(c.getBlock())) && e.getInventory().isEmpty()) {
            this.airdrops.remove(key(c.getBlock()));
            c.getBlock().setType(Material.AIR);
            c.getWorld().spawnParticle(Particle.CLOUD, c.getLocation().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private void meteor(Location at) {
        World w = at.getWorld();
        Material[] ores = {Material.DIAMOND_ORE, Material.GOLD_ORE, Material.IRON_ORE, Material.ANCIENT_DEBRIS, Material.EMERALD_ORE, Material.REDSTONE_ORE};
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = 0; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx * dx + (dy - 1.5) * (dy - 1.5) + dz * dz > 6.5) {
                        continue;
                    }

                    Block b = w.getBlockAt(at.getBlockX() + dx, at.getBlockY() + dy, at.getBlockZ() + dz);
                    if (!b.getType().isAir() && !b.isPassable()) {
                        continue;
                    }

                    boolean inside = Math.abs(dx) < 2 && dy > 0 && dy < 3 && Math.abs(dz) < 2;
                    b.setType(inside && this.random.nextInt(3) > 0 ? ores[this.random.nextInt(ores.length)] : (this.random.nextBoolean() ? Material.MAGMA_BLOCK : Material.BLACKSTONE), false);
                }
            }
        }
    }

    private void stopKoth(boolean reward) {
        Koth k = this.koth;
        this.koth = null;
        if (k == null) {
            return;
        }

        k.label.remove();
        if (reward) {
            UUID best = null;
            int top = 0;
            for (Map.Entry<UUID, Integer> en : k.points.entrySet()) {
                if (en.getValue() > top) {
                    top = en.getValue();
                    best = en.getKey();
                }
            }

            if (best != null) {
                String n = Bukkit.getOfflinePlayer(best).getName();
                this.plus.progression().shards(best, this.plus.config().i("eco-events.koth-shards", 250));
                this.giveKeys(best, "rare", 1L);
                this.ecoBroadcast("<gold>👑 <white>" + Text.esc(n == null ? "?" : n) + " <gray>won King of the Hill with <white>" + top + "</white> seconds!");
            } else {
                this.ecoBroadcast("<gray>King of the Hill ended - nobody held it.");
            }
        }
    }

    /** Every second: the hill, automatic events, cleanup of old airdrops. */
    public void second() {
        Koth k = this.koth;
        if (k != null) {
            k.seconds++;
            List<Player> on = new ArrayList<>();
            for (Player p : k.at.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(k.at) <= 16.0 && !p.isDead()) {
                    on.add(p);
                }
            }

            if (on.size() == 1) {
                k.points.merge(on.get(0).getUniqueId(), 1, Integer::sum);
            }

            for (Player p : on) {
                p.sendActionBar(Text.mm(on.size() > 1 ? "<red>Contested!" : "<gold>👑 Holding the hill: <white>" + k.points.getOrDefault(p.getUniqueId(), 0) + "s"));
            }

            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8 + k.seconds * 0.2;
                k.at.getWorld().spawnParticle(Particle.END_ROD, k.at.clone().add(Math.cos(a) * 4, 0.2, Math.sin(a) * 4), 1, 0, 0, 0, 0);
            }

            if (k.seconds >= this.plus.config().i("eco-events.koth-seconds", 300)) {
                this.stopKoth(true);
            }
        }

        this.airdrops.entrySet().removeIf(en -> {
            if (System.currentTimeMillis() - en.getValue() < 600000L) {
                return false;
            }

            String[] p = en.getKey().split(";");
            World w = Bukkit.getWorld(p[0]);
            if (w != null) {
                Block b = w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                if (b.getType() == Material.CHEST) {
                    if (b.getState() instanceof Chest c) {
                        c.getBlockInventory().clear();
                    }

                    b.setType(Material.AIR);
                }
            }

            return true;
        });
        if (this.plus.config().bool("eco-events.auto", true) && System.currentTimeMillis() >= this.nextEvent) {
            this.nextEvent = System.currentTimeMillis() + this.plus.config().i("eco-events.every-minutes", 45) * 60000L;
            World w = this.ecoWorld();
            if (w != null && w.getPlayers().size() >= this.plus.config().i("eco-events.min-players", 2)) {
                String[] types = {"airdrop", "meteor", "koth"};
                this.startEvent(types[this.random.nextInt(types.length)]);
            }
        }
    }

    // ------------------------------------------------------------------ season wipe

    public void wipe(CommandSender s, String[] a) {
        if (a.length < 2 || !a[0].equalsIgnoreCase("confirm") || !a[1].equals(this.wipeCode)) {
            this.wipeCode = Integer.toString(1000 + this.random.nextInt(9000));
            s.sendMessage(Text.mm("<red><b>Eco season wipe</b> <gray>resets every balance and shard count (a backup is saved first). The 3 richest players get a title and a legendary key."));
            s.sendMessage(Text.mm("<gray>Type <white>/ecowipe confirm " + this.wipeCode + "</white> to do it."));
            return;
        }

        this.wipeCode = null;
        Map<UUID, ModeManager.Stats> all = this.plugin.modes().allStats();
        YamlConfiguration backup = new YamlConfiguration();
        List<Map.Entry<UUID, Double>> rich = new ArrayList<>();
        for (Map.Entry<UUID, ModeManager.Stats> en : all.entrySet()) {
            double bal = this.plugin.economy().balance(en.getKey());
            backup.set(en.getKey() + ".balance", bal);
            backup.set(en.getKey() + ".shards", en.getValue().shards);
            rich.add(Map.entry(en.getKey(), bal + en.getValue().shards * 10.0));
        }

        try {
            backup.save(new File(this.plus.store().dir(), "eco-wipe-" + System.currentTimeMillis() + ".yml"));
        } catch (java.io.IOException ex) {
            s.sendMessage(Text.mm("<red>Could not save the backup - nothing was wiped."));
            return;
        }

        rich.sort((x, y) -> Double.compare(y.getValue(), x.getValue()));
        YamlConfiguration eco = this.plus.store().doc("eco");
        int season = eco.getInt("eco-season", 1);
        String[] titles = {"Eco S" + season + " Tycoon", "Eco S" + season + " Mogul", "Eco S" + season + " Baron"};
        for (int i = 0; i < Math.min(3, rich.size()); i++) {
            this.plus.progression().title(rich.get(i).getKey(), titles[i]);
            this.giveKeys(rich.get(i).getKey(), "legendary", 1L);
        }

        for (Map.Entry<UUID, ModeManager.Stats> en : all.entrySet()) {
            double bal = this.plugin.economy().balance(en.getKey());
            if (bal > 0) {
                this.plugin.economy().take(en.getKey(), bal);
            }

            en.getValue().shards = 0L;
        }

        this.plugin.modes().dirty();
        eco.set("eco-season", season + 1);
        eco.set("bids", null);
        this.plus.store().dirty("eco");
        Bukkit.broadcast(Text.mm("<gold><b>ECO SEASON " + season + " IS OVER!</b> <gray>Balances were reset. Congratulations to the richest players - a new season starts now!"));
        s.sendMessage(Text.mm("<green>Wiped " + all.size() + " accounts. Backup saved in plus/."));
    }

    public void cleanup() {
        this.stopKoth(false);
    }

    public String selfCheck() {
        int total = 0;
        for (String t : this.crateTypes()) {
            for (String r : this.crateRewards(t)) {
                total += Integer.parseInt(r.split(":", 2)[0]);
                if (this.rewardName(r.split(":", 2)[1]).isBlank()) {
                    return "reward name " + r;
                }
            }
        }

        if (total <= 0) {
            return "crate weights";
        }

        return this.deal().length == 3 ? null : "deal format";
    }
}
