package net.practicepvp.survival.trade;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.practicepvp.survival.VortexSMP;
import net.practicepvp.survival.config.Cfg;
import net.practicepvp.survival.config.Opt;
import net.practicepvp.survival.util.Items;
import net.practicepvp.survival.util.Snd;
import net.practicepvp.survival.util.Tasks;
import net.practicepvp.survival.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * /market: trade offers. You put in what you give (it is held safely by the server) and say what you want, e.g.
 * "diamond 128" or "diamond_helmet 1, diamond_chestplate 1, diamond_leggings 1, diamond_boots 1". Anyone who has
 * those items fills the offer with one click: they get your items, you get theirs (in /market collect, or right
 * away when online). Cancel an offer to get your items back.
 */
public final class Market implements Listener, TabExecutor {
    private final VortexSMP plugin;
    private final File file;
    private YamlConfiguration data;
    /** Items put in for an offer, waiting for the "what do you want" answer. */
    private final Map<UUID, List<ItemStack>> drafts = new HashMap<>();

    public Market(VortexSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market.yml");
        this.data = YamlConfiguration.loadConfiguration(this.file);
    }

    private void save() {
        try {
            this.data.save(this.file);
        } catch (IOException var2) {
            this.plugin.getLogger().log(Level.SEVERE, "Could not save market.yml", (Throwable)var2);
        }
    }

    private static String enc(List<ItemStack> items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items.toArray(new ItemStack[0])));
    }

    private static List<ItemStack> dec(String s) {
        List<ItemStack> out = new ArrayList<>();
        if (s != null && !s.isEmpty()) {
            for (ItemStack it : ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s))) {
                if (it != null && !it.isEmpty()) {
                    out.add(it);
                }
            }
        }

        return out;
    }

    /** "diamond 128, diamond_helmet 1" -> {DIAMOND=128, DIAMOND_HELMET=1}; null when something is not an item. */
    static Map<Material, Integer> parseWant(String s) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        for (String part : s.split("[,;]")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }

            String[] w = p.split("\\s+");
            int amount = 1;
            String name = p;
            if (w.length >= 2) {
                try {
                    amount = Integer.parseInt(w[w.length - 1]);
                    name = String.join("_", java.util.Arrays.copyOf(w, w.length - 1));
                } catch (NumberFormatException var8) {
                    try {
                        amount = Integer.parseInt(w[0]);
                        name = String.join("_", java.util.Arrays.copyOfRange(w, 1, w.length));
                    } catch (NumberFormatException var7) {
                        name = String.join("_", w);
                    }
                }
            }

            Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
            if (m == null || !m.isItem() || m.isAir() || amount <= 0 || amount > 64 * 36) {
                return null;
            }

            out.merge(m, amount, Integer::sum);
        }

        return out.isEmpty() ? null : out;
    }

    private static String describe(Map<Material, Integer> want) {
        List<String> s = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : want.entrySet()) {
            s.add(e.getValue() + "x " + e.getKey().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }

        return String.join(", ", s);
    }

    private static String describe(List<ItemStack> items) {
        Map<Material, Integer> m = new LinkedHashMap<>();
        for (ItemStack it : items) {
            m.merge(it.getType(), it.getAmount(), Integer::sum);
        }

        return describe(m);
    }

    private Map<Material, Integer> want(String id) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        ConfigurationSection w = this.data.getConfigurationSection("offers." + id + ".want");
        if (w != null) {
            for (String k : w.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m != null) {
                    out.put(m, w.getInt(k));
                }
            }
        }

        return out;
    }

    private List<String> offerIds() {
        ConfigurationSection s = this.data.getConfigurationSection("offers");
        return s == null ? new ArrayList<>() : new ArrayList<>(s.getKeys(false));
    }

    private int countOf(UUID owner) {
        int n = 0;
        for (String id : this.offerIds()) {
            if (owner.toString().equals(this.data.getString("offers." + id + ".owner"))) {
                n++;
            }
        }

        return n;
    }

    // ------------------------------------------------------------------ the board

    final class Board implements InventoryHolder {
        final Inventory inv;
        final List<String> ids = new ArrayList<>();
        final int page;

        Board(int page) {
            this.page = page;
            this.inv = Bukkit.createInventory(this, 54, Text.mm("<gold><b>Market</b> <dark_gray>» <gray>trade offers"));
        }

        @Override
        public Inventory getInventory() {
            return this.inv;
        }
    }

    final class Post implements InventoryHolder {
        final Inventory inv = Bukkit.createInventory(this, 27, Text.mm("<gold><b>New offer</b> <dark_gray>» <gray>put in what you GIVE"));

        @Override
        public Inventory getInventory() {
            return this.inv;
        }
    }

    public void openBoard(Player p, int page) {
        Board b = new Board(page);
        List<String> ids = this.offerIds();
        int from = page * 45;
        for (int i = from; i < ids.size() && i < from + 45; i++) {
            String id = ids.get(i);
            String base = "offers." + id + ".";
            List<ItemStack> give = dec(this.data.getString(base + "give"));
            if (give.isEmpty()) {
                continue;
            }

            boolean mine = p.getUniqueId().toString().equals(this.data.getString(base + "owner"));
            boolean can = !mine && this.has(p, this.want(id));
            ItemStack icon = give.get(0).clone();
            icon.setAmount(Math.min(64, Math.max(1, icon.getAmount())));
            List<String> lore = new ArrayList<>();
            lore.add("<gray>From <white>" + Text.esc(this.data.getString(base + "name", "?")));
            lore.add("");
            lore.add("<green>They give: <white>" + describe(give));
            lore.add("<gold>They want: <white>" + describe(this.want(id)));
            lore.add("");
            lore.add(mine ? "<red>▶ Shift-click to cancel (items back to you)" : (can ? "<green>▶ Click to trade" : "<gray>You don't have what they want"));
            b.inv.setItem(b.ids.size(), Items.of(icon).lore(lore).build());
            b.ids.add(id);
        }

        ItemStack pane = Items.of(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();
        for (int i = 45; i < 54; i++) {
            b.inv.setItem(i, pane);
        }

        if (page > 0) {
            b.inv.setItem(45, Items.of(Material.ARROW).name("<yellow>« Previous page").build());
        }

        if (ids.size() > from + 45) {
            b.inv.setItem(53, Items.of(Material.ARROW).name("<yellow>Next page »").build());
        }

        b.inv.setItem(48, Items.of(Material.WRITABLE_BOOK).name("<green><b>+ Post an offer</b>").lore("<gray>Put in what you give, then", "<gray>say what you want for it.").build());
        b.inv.setItem(50, Items.of(Material.CHEST).name("<aqua><b>Collect</b>").lore("<gray>Items from your filled offers.").build());
        b.inv.setItem(49, Items.of(Material.EMERALD).name("<gold><b>Market</b>").lore("<gray>" + ids.size() + " open offers", "<gray>Example: give 2 stacks of diamonds,", "<gray>want a full diamond set.").build());
        p.openInventory(b.inv);
    }

    private boolean has(Player p, Map<Material, Integer> want) {
        for (Map.Entry<Material, Integer> e : want.entrySet()) {
            int have = 0;
            for (ItemStack it : p.getInventory().getStorageContents()) {
                if (it != null && it.getType() == e.getKey()) {
                    have += it.getAmount();
                }
            }

            if (have < e.getValue()) {
                return false;
            }
        }

        return !want.isEmpty();
    }

    /** Takes the wanted items out of the player's inventory and returns the exact stacks taken. */
    private List<ItemStack> take(Player p, Map<Material, Integer> want) {
        List<ItemStack> taken = new ArrayList<>();
        ItemStack[] c = p.getInventory().getStorageContents();
        for (Map.Entry<Material, Integer> e : want.entrySet()) {
            int left = e.getValue();
            for (int i = 0; i < c.length && left > 0; i++) {
                if (c[i] != null && c[i].getType() == e.getKey()) {
                    int n = Math.min(left, c[i].getAmount());
                    ItemStack part = c[i].clone();
                    part.setAmount(n);
                    taken.add(part);
                    c[i].setAmount(c[i].getAmount() - n);
                    if (c[i].getAmount() <= 0) {
                        c[i] = null;
                    }

                    left -= n;
                }
            }
        }

        p.getInventory().setStorageContents(c);
        return taken;
    }

    private void fill(Player p, String id) {
        String base = "offers." + id + ".";
        if (!this.data.isSet(base + "owner")) {
            p.sendMessage(Text.mm("<red>That offer is gone."));
            return;
        }

        UUID owner = UUID.fromString(this.data.getString(base + "owner"));
        Map<Material, Integer> want = this.want(id);
        if (owner.equals(p.getUniqueId())) {
            return;
        } else if (!this.has(p, want)) {
            p.sendMessage(Text.mm("<red>You need: <white>" + describe(want)));
            Snd.play(p, "entity.villager.no", 0.7F, 1.0F);
            return;
        }

        List<ItemStack> give = dec(this.data.getString(base + "give"));
        String ownerName = this.data.getString(base + "name", "?");
        List<ItemStack> paid = this.take(p, want);
        // the offer is closed first, then the items move: nothing can be taken twice
        this.data.set("offers." + id, null);
        List<ItemStack> box = dec(this.data.getString("collect." + owner));
        box.addAll(paid);
        this.data.set("collect." + owner, enc(box));
        this.save();
        for (ItemStack it : give) {
            p.getInventory().addItem(it).values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
        }

        p.sendMessage(Text.mm("<green><b>✔ Trade done!</b> <gray>You got <white>" + describe(give) + "<gray> for <white>" + describe(want)));
        Snd.play(p, "entity.villager.yes", 0.8F, 1.2F);
        this.plugin.getLogger().info("Market: " + p.getName() + " filled offer " + id + " of " + ownerName + ": " + describe(give) + " for " + describe(want));
        Player o = Bukkit.getPlayer(owner);
        if (o != null) {
            this.deliver(o);
            o.sendMessage(Text.mm("<gold>⇄ <white>" + Text.esc(p.getName()) + " <gray>filled your market offer: you got <white>" + describe(want)));
            Snd.play(o, "entity.player.levelup", 0.6F, 1.6F);
        }
    }

    /** Hands over everything waiting in the player's collect box. */
    private void deliver(Player p) {
        if (this.plugin.realm().on() && !this.plugin.realm().smp(p.getWorld())) {
            // same server: bought items wait until they are in the SMP (with its inventory)
            return;
        }

        List<ItemStack> box = dec(this.data.getString("collect." + p.getUniqueId()));
        if (box.isEmpty()) {
            return;
        }

        this.data.set("collect." + p.getUniqueId(), null);
        this.save();
        for (ItemStack it : box) {
            p.getInventory().addItem(it).values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
        }

        p.sendMessage(Text.mm("<aqua>You collected <white>" + describe(box) + "<aqua> from the market."));
    }

    private void cancel(Player p, String id) {
        String base = "offers." + id + ".";
        if (!p.getUniqueId().toString().equals(this.data.getString(base + "owner")) && !p.hasPermission("vortexsmp.admin")) {
            return;
        }

        List<ItemStack> give = dec(this.data.getString(base + "give"));
        UUID owner = UUID.fromString(this.data.getString(base + "owner"));
        this.data.set("offers." + id, null);
        List<ItemStack> box = dec(this.data.getString("collect." + owner));
        box.addAll(give);
        this.data.set("collect." + owner, enc(box));
        this.save();
        Player o = Bukkit.getPlayer(owner);
        if (o != null) {
            this.deliver(o);
        }

        p.sendMessage(Text.mm("<gray>Offer cancelled - the items are back."));
    }

    // ------------------------------------------------------------------ posting

    private void startPost(Player p) {
        if (this.countOf(p.getUniqueId()) >= Cfg.i(Opt.MARKET_MAX)) {
            p.sendMessage(Text.mm("<red>You already have " + Cfg.i(Opt.MARKET_MAX) + " open offers. Cancel one first."));
            return;
        }

        p.openInventory(new Post().inv);
    }

    private void finishPost(Player p, String wantText) {
        List<ItemStack> give = this.drafts.remove(p.getUniqueId());
        if (give == null) {
            p.sendMessage(Text.mm("<red>Start with <yellow>/market post</yellow> and put in what you give."));
            return;
        }

        Map<Material, Integer> want = wantText == null ? null : parseWant(wantText);
        if (want == null) {
            p.getInventory().addItem(give.toArray(new ItemStack[0])).values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
            p.sendMessage(Text.mm("<red>I couldn't read that. <gray>Write it like <white>diamond 128</white> or <white>diamond_helmet 1, diamond_chestplate 1</white>. Your items are back."));
            return;
        }

        String id = Long.toString(System.currentTimeMillis(), 36) + p.getName().length();
        String base = "offers." + id + ".";
        this.data.set(base + "owner", p.getUniqueId().toString());
        this.data.set(base + "name", p.getName());
        this.data.set(base + "give", enc(give));
        this.data.set(base + "created", System.currentTimeMillis());
        for (Map.Entry<Material, Integer> e : want.entrySet()) {
            this.data.set(base + "want." + e.getKey().name(), e.getValue());
        }

        this.save();
        p.sendMessage(Text.mm("<green><b>✔ Offer posted!</b> <gray>You give <white>" + describe(give) + "<gray>, you want <white>" + describe(want) + "<gray>."));
        Snd.play(p, "entity.villager.trade", 0.8F, 1.0F);
        Bukkit.broadcast(Text.mm("<gold>⇄ <white>" + Text.esc(p.getName()) + " <gray>offers <white>" + describe(give) + " <gray>for <white>" + describe(want)
            + " <click:run_command:'/market'><yellow>[Market]</yellow></click>"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }

        if (e.getView().getTopInventory().getHolder(false) instanceof Board b) {
            e.setCancelled(true);
            int raw = e.getRawSlot();
            if (raw < 0 || raw >= 54) {
                return;
            }

            if (raw < 45 && raw < b.ids.size()) {
                String id = b.ids.get(raw);
                p.closeInventory();
                if (e.isShiftClick()) {
                    this.cancel(p, id);
                } else {
                    this.fill(p, id);
                }
            } else if (raw == 45 && b.page > 0) {
                this.openBoard(p, b.page - 1);
            } else if (raw == 53) {
                this.openBoard(p, b.page + 1);
            } else if (raw == 48) {
                this.startPost(p);
            } else if (raw == 50) {
                p.closeInventory();
                this.deliver(p);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder(false) instanceof Board) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder(false) instanceof Post post && e.getPlayer() instanceof Player p) {
            List<ItemStack> give = new ArrayList<>();
            for (ItemStack it : post.inv.getContents()) {
                if (it != null && !it.isEmpty()) {
                    give.add(it);
                }
            }

            post.inv.clear();
            if (give.isEmpty()) {
                return;
            }

            this.drafts.put(p.getUniqueId(), give);
            p.sendMessage(Text.mm("<gold>What do you want for <white>" + describe(give) + "<gold>?"));
            p.sendMessage(Text.mm("<gray>Type <yellow>/market want <items></yellow>, e.g. <white>/market want diamond 128</white> or <white>/market want diamond_helmet 1, diamond_chestplate 1, diamond_leggings 1, diamond_boots 1"));
            Tasks.later(2L, () -> {
                if (p.isOnline() && this.drafts.containsKey(p.getUniqueId()) && this.plugin.dialogs() != null && this.plugin.dialogs().useDialog(p)) {
                    this.plugin.dialogs().text(p, Text.mm("<gold><b>What do you want for it?"), "e.g. diamond 128 / diamond_helmet 1, diamond_chestplate 1", "", 200, s -> this.finishPost(p, s));
                }
            });
            // not answered in 3 minutes: the items go back
            Tasks.later(3600L, () -> {
                List<ItemStack> back = this.drafts.remove(p.getUniqueId());
                if (back != null && p.isOnline()) {
                    p.getInventory().addItem(back.toArray(new ItemStack[0])).values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
                    p.sendMessage(Text.mm("<gray>Your market offer wasn't finished - the items are back."));
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        List<ItemStack> back = this.drafts.remove(e.getPlayer().getUniqueId());
        if (back != null) {
            e.getPlayer().getInventory().addItem(back.toArray(new ItemStack[0])).values().forEach(l -> e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), l));
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onEnterSmp(org.bukkit.event.player.PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        if (this.plugin.realm().on()) {
            Tasks.later(20L, () -> {
                if (p.isOnline()) {
                    this.deliver(p);
                }
            });
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Tasks.later(40L, () -> {
            if (e.getPlayer().isOnline()) {
                this.deliver(e.getPlayer());
            }
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!Cfg.bool(Opt.MARKET)) {
            p.sendMessage(Text.mm("<red>The market is turned off on this server."));
            return true;
        }

        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "post", "sell", "offer" -> this.startPost(p);
            case "want" -> this.finishPost(p, args.length > 1 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null);
            case "collect" -> this.deliver(p);
            default -> this.openBoard(p, 0);
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("post", "want", "collect").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }

        return List.of();
    }

    public void shutdown() {
        for (Map.Entry<UUID, List<ItemStack>> d : this.drafts.entrySet()) {
            Player p = Bukkit.getPlayer(d.getKey());
            if (p != null) {
                p.getInventory().addItem(d.getValue().toArray(new ItemStack[0])).values().forEach(l -> p.getWorld().dropItemNaturally(p.getLocation(), l));
            }
        }

        this.drafts.clear();
    }

    Component title() {
        return Text.mm("Market");
    }
}
