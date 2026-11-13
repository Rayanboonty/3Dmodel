package net.vortexpvp.smp.eco;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.gui.Ui;
import net.vortexpvp.smp.Economy;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Orders implements TabExecutor {
    private final VortexPvP plugin;
    private final Economy eco;
    private final File file;
    private final List<Orders.Order> orders = new ArrayList<>();

    public Orders(VortexPvP plugin, Economy eco) {
        this.plugin = plugin;
        this.eco = eco;
        this.file = new File(plugin.getDataFolder(), "orders.yml");
    }

    public void load() {
        this.orders.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);

        for (Map<?, ?> m : y.getMapList("orders")) {
            try {
                Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
                if (mat != null) {
                    Orders.Order o = new Orders.Order(
                        String.valueOf(m.get("id")),
                        UUID.fromString(String.valueOf(m.get("buyer"))),
                        String.valueOf(m.get("name")),
                        mat,
                        ((Number)m.get("wanted")).intValue(),
                        ((Number)m.get("each")).doubleValue(),
                        ((Number)m.get("created")).longValue()
                    );
                    o.delivered = ((Number)m.get("delivered")).intValue();
                    o.collected = ((Number)m.get("collected")).intValue();
                    this.orders.add(o);
                }
            } catch (RuntimeException var6) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();

        for (Orders.Order o : this.orders) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", o.id);
            m.put("buyer", o.buyer.toString());
            m.put("name", o.buyerName);
            m.put("material", o.material.name());
            m.put("wanted", o.wanted);
            m.put("each", o.each);
            m.put("delivered", o.delivered);
            m.put("collected", o.collected);
            m.put("created", o.created);
            list.add(m);
        }

        y.set("orders", list);

        try {
            y.save(this.file);
        } catch (IOException var6) {
            this.plugin.getLogger().log(Level.WARNING, "Could not save orders.yml", (Throwable)var6);
        }
    }

    private static String nice(Material m) {
        String base = m.getKey().getKey().replace('_', ' ');
        return Character.toUpperCase(base.charAt(0)) + base.substring(1);
    }

    private static boolean plain(ItemStack it, Material m) {
        return it != null
            && it.getType() == m
            && (!it.hasItemMeta() || !it.getItemMeta().hasDisplayName() && !it.getItemMeta().hasEnchants() && !it.getItemMeta().hasLore());
    }

    private void create(Player p, Material m, int amount, double each) {
        if (amount >= 1 && !(each <= 0.0)) {
            double total = Math.round(amount * each * 100.0) / 100.0;
            long mine = this.orders.stream().filter(o -> o.buyer.equals(p.getUniqueId()) && o.left() > 0).count();
            if (mine >= 10L && !p.hasPermission("vortexpvp.admin")) {
                p.sendMessage(Text.mm("<red>You already have 10 open orders."));
            } else if (!this.eco.take(p.getUniqueId(), total)) {
                p.sendMessage(Text.mm("<red>You need " + this.eco.money(total) + " to place that order."));
            } else {
                this.orders
                    .add(
                        new Orders.Order(
                            Long.toString(System.currentTimeMillis(), 36), p.getUniqueId(), p.getName(), m, amount, each, System.currentTimeMillis()
                        )
                    );
                this.save();
                p.sendMessage(
                    Text.mm(
                        "<gold>ORDERS <dark_gray>» <gray>Ordered <white>"
                            + amount
                            + "x "
                            + nice(m)
                            + "</white> at <#3DDC84>"
                            + this.eco.money(each)
                            + "</#3DDC84> each <dark_gray>("
                            + this.eco.money(total)
                            + " held)"
                    )
                );
                Snd.play(p, "block.note_block.pling", 0.6F, 1.5F);
            }
        } else {
            p.sendMessage(Text.mm("<red>Amount and price must be above 0."));
        }
    }

    private void deliver(Player p, Orders.Order o) {
        if (o.buyer.equals(p.getUniqueId())) {
            p.sendMessage(Text.mm("<red>That's your own order."));
        } else {
            int have = 0;

            for (ItemStack it : p.getInventory().getStorageContents()) {
                if (plain(it, o.material)) {
                    have += it.getAmount();
                }
            }

            int give = Math.min(have, o.left());
            if (give <= 0) {
                p.sendMessage(Text.mm("<red>You have no plain " + nice(o.material) + " to deliver."));
                Snd.error(p);
            } else {
                int left = give;
                ItemStack[] inv = p.getInventory().getStorageContents();

                for (int i = 0; i < inv.length && left > 0; i++) {
                    if (plain(inv[i], o.material)) {
                        int take = Math.min(left, inv[i].getAmount());
                        inv[i].setAmount(inv[i].getAmount() - take);
                        left -= take;
                    }
                }

                p.getInventory().setStorageContents(inv);
                o.delivered += give;
                double pay = Math.round(give * o.each * 100.0) / 100.0;
                this.eco.give(p.getUniqueId(), pay);
                this.save();
                p.sendMessage(
                    Text.mm(
                        "<gold>ORDERS <dark_gray>» <gray>Delivered <white>" + give + "x " + nice(o.material) + "</white> - <#3DDC84>+" + this.eco.money(pay)
                    )
                );
                Snd.play(p, "entity.experience_orb.pickup", 1.0F, 1.3F);
                Player b = Bukkit.getPlayer(o.buyer);
                if (b != null) {
                    b.sendMessage(
                        Text.mm(
                            "<gold>ORDERS <dark_gray>» <white>"
                                + Text.esc(p.getName())
                                + " <gray>delivered <white>"
                                + give
                                + "x "
                                + nice(o.material)
                                + " <gray>("
                                + o.delivered
                                + "/"
                                + o.wanted
                                + ") - collect in /orders"
                        )
                    );
                }
            }
        }
    }

    private void collect(Player p, Orders.Order o) {
        int ready = o.delivered - o.collected;
        if (ready > 0) {
            int given = 0;

            while (given < ready) {
                int n = Math.min(ready - given, o.material.getMaxStackSize());
                Map<Integer, ItemStack> rest = p.getInventory().addItem(new ItemStack[]{new ItemStack(o.material, n)});
                int notIn = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
                given += n - notIn;
                if (notIn > 0) {
                    p.sendMessage(Text.mm("<yellow>Your inventory is full - collect the rest later."));
                    break;
                }
            }

            o.collected += given;
            if (o.left() <= 0 && o.collected >= o.delivered) {
                this.orders.remove(o);
            }

            this.save();
        }
    }

    private void cancel(Player p, Orders.Order o) {
        this.collect(p, o);
        double refund = Math.round(o.left() * o.each * 100.0) / 100.0;
        if (o.collected < o.delivered) {
            p.sendMessage(Text.mm("<red>Collect your delivered items first (make room)."));
        } else {
            this.orders.remove(o);
            this.eco.give(p.getUniqueId(), refund);
            this.save();
            p.sendMessage(Text.mm("<gold>ORDERS <dark_gray>» <gray>Cancelled - <#3DDC84>" + this.eco.money(refund) + "</#3DDC84> back."));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            if (!this.eco.inEco(p) && !p.hasPermission("vortexpvp.admin")) {
                p.sendMessage(Text.mm("<red>Orders are in the Eco SMP."));
                return true;
            } else if (args.length >= 3 && args[0].equalsIgnoreCase("create")) {
                ItemStack h = p.getInventory().getItemInMainHand();
                if (h.isEmpty()) {
                    p.sendMessage(Text.mm("<red>Hold the item you want to order."));
                    return true;
                } else {
                    try {
                        this.create(p, h.getType(), Integer.parseInt(args[1]), Double.parseDouble(args[2]));
                    } catch (NumberFormatException var8) {
                        p.sendMessage(Text.mm("<red>Usage: /orders create <amount> <price-each>"));
                    }

                    return true;
                }
            } else {
                new Orders.Browse(p, 0).open();
                return true;
            }
        } else {
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("create") : List.of();
    }

    static String lower(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private final class Browse extends Menu {
        private final int page;

        Browse(Player viewer, int page) {
            super(Orders.this.plugin, viewer);
            this.page = page;
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Orders");
        }

        @Override
        protected int rows() {
            return 6;
        }

        @Override
        protected void draw() {
            List<Orders.Order> open = new ArrayList<>();

            for (Orders.Order o : Orders.this.orders) {
                if (o.left() > 0) {
                    open.add(o);
                }
            }

            open.sort((a, b) -> Double.compare(b.each * b.left(), a.each * a.left()));
            int from = this.page * 45;

            for (int i = 0; i < 45 && from + i < open.size(); i++) {
                Orders.Order ox = open.get(from + i);
                double pct = (double)ox.delivered / ox.wanted;
                int bars = (int)Math.round(pct * 10.0);
                this.set(
                    i,
                    Items.of(ox.material)
                        .name("<white><b>" + Orders.nice(ox.material))
                        .lore(
                            "<gray>Price each: <#3DDC84>" + Orders.this.eco.money(ox.each),
                            "<gray>Wanted: <white>" + ox.left() + " <dark_gray>/ " + ox.wanted,
                            "<green>" + "■".repeat(bars) + "<dark_gray>" + "■".repeat(10 - bars),
                            "<gray>By: <white>" + Text.esc(ox.buyerName),
                            "",
                            ox.buyer.equals(this.viewer.getUniqueId()) ? "<yellow>Your order" : "<green>▶ Click to deliver from your inventory"
                        )
                        .hideAll()
                        .build(),
                    t -> {
                        Orders.this.deliver(this.viewer, ox);
                        this.redraw();
                    }
                );
            }

            for (int i = 45; i < 54; i++) {
                this.set(i, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
            }

            if (this.page > 0) {
                this.set(45, Ui.prev(this.page), t -> Orders.this.new Browse(this.viewer, this.page - 1).open());
            }

            this.set(
                48,
                Items.of(Material.WRITABLE_BOOK)
                    .name("<gold><b>+ New Order")
                    .lore("<gray>Hold the item you want,", "<gray>then pick amount and price.", "", "<yellow>▶ Click")
                    .build(),
                t -> {
                    ItemStack h = this.viewer.getInventory().getItemInMainHand();
                    if (h.isEmpty()) {
                        this.viewer.sendMessage(Text.mm("<red>Hold the item you want to order first."));
                    } else {
                        Material m = h.getType();
                        this.viewer.closeInventory();
                        this.plugin
                            .dialogs()
                            .number(
                                this.viewer,
                                Text.mm("<gold><b>How many " + Orders.nice(m) + "?"),
                                "Amount",
                                1.0F,
                                4096.0F,
                                1.0F,
                                64.0F,
                                amount -> this.plugin
                                    .dialogs()
                                    .text(this.viewer, Text.mm("<gold><b>Price each"), "Price for ONE " + Orders.nice(m), "1", 16, price -> {
                                        try {
                                            Orders.this.create(this.viewer, m, Math.round(amount), Double.parseDouble(price.trim()));
                                        } catch (NumberFormatException var5x) {
                                            this.viewer.sendMessage(Text.mm("<red>Not a price."));
                                        }
                                    })
                            );
                    }
                }
            );
            this.set(
                49,
                Items.of(Material.SUNFLOWER)
                    .name("<#3DDC84><b>Balance")
                    .lore("<white>" + Orders.this.eco.money(Orders.this.eco.balance(this.viewer.getUniqueId())))
                    .build()
            );
            this.set(
                50,
                Items.of(Material.CHEST).name("<gold><b>Your Orders").lore("<gray>Collect deliveries, cancel.", "", "<yellow>▶ Click").build(),
                t -> Orders.this.new Mine(this.viewer).open()
            );
            if (from + 45 < open.size()) {
                this.set(53, Ui.next(this.page), t -> Orders.this.new Browse(this.viewer, this.page + 1).open());
            }
        }
    }

    private final class Mine extends Menu {
        Mine(Player viewer) {
            super(Orders.this.plugin, viewer);
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Your Orders");
        }

        @Override
        protected int rows() {
            return 4;
        }

        @Override
        protected void draw() {
            int slot = 0;

            for (Orders.Order o : new ArrayList<>(Orders.this.orders)) {
                if (o.buyer.equals(this.viewer.getUniqueId()) && slot < 27) {
                    int ready = o.delivered - o.collected;
                    this.set(
                        slot++,
                        Items.of(o.material)
                            .name("<white><b>" + Orders.nice(o.material))
                            .lore(
                                "<gray>Delivered: <white>" + o.delivered + "/" + o.wanted,
                                "<gray>Ready to collect: <green>" + ready,
                                "<gray>Price each: <#3DDC84>" + Orders.this.eco.money(o.each),
                                "",
                                "<green>Click <gray>collect",
                                "<red>Shift-click <gray>cancel (refund the rest)"
                            )
                            .hideAll()
                            .build(),
                        t -> {
                            if (t.isShiftClick()) {
                                Orders.this.cancel(this.viewer, o);
                            } else {
                                Orders.this.collect(this.viewer, o);
                            }

                            this.redraw();
                        }
                    );
                }
            }

            for (int i = 27; i < 36; i++) {
                this.set(i, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
            }

            this.set(31, Ui.back(), t -> Orders.this.new Browse(this.viewer, 0).open());
        }
    }

    private static final class Order {
        final String id;
        final UUID buyer;
        final String buyerName;
        final Material material;
        final int wanted;
        final double each;
        int delivered;
        int collected;
        final long created;

        Order(String id, UUID buyer, String buyerName, Material material, int wanted, double each, long created) {
            this.id = id;
            this.buyer = buyer;
            this.buyerName = buyerName;
            this.material = material;
            this.wanted = wanted;
            this.each = each;
            this.created = created;
        }

        int left() {
            return this.wanted - this.delivered;
        }
    }
}
