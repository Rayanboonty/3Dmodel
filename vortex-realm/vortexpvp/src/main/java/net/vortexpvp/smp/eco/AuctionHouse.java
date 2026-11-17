package net.vortexpvp.smp.eco;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
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
import net.vortexpvp.util.Fmt;
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

public final class AuctionHouse implements TabExecutor {
    private final VortexPvP plugin;
    private final Economy eco;
    private final File file;
    private final List<AuctionHouse.Listing> listings = new ArrayList<>();
    private final List<AuctionHouse.Listing> expired = new ArrayList<>();
    private boolean dirty;

    public AuctionHouse(VortexPvP plugin, Economy eco) {
        this.plugin = plugin;
        this.eco = eco;
        this.file = new File(plugin.getDataFolder(), "auction-house.yml");
    }

    public void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(this.file);
        read(y.getMapList("listings"), this.listings);
        read(y.getMapList("expired"), this.expired);
    }

    private static void read(List<Map<?, ?>> raw, List<AuctionHouse.Listing> into) {
        into.clear();

        for (Map<?, ?> m : raw) {
            try {
                ItemStack it = ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(m.get("item"))));
                into.add(
                    new AuctionHouse.Listing(
                        String.valueOf(m.get("id")),
                        UUID.fromString(String.valueOf(m.get("seller"))),
                        String.valueOf(m.get("name")),
                        it,
                        ((Number)m.get("price")).doubleValue(),
                        ((Number)m.get("created")).longValue(),
                        ((Number)m.get("expires")).longValue()
                    )
                );
            } catch (RuntimeException var5) {
            }
        }
    }

    private static List<Map<String, Object>> write(List<AuctionHouse.Listing> from) {
        List<Map<String, Object>> out = new ArrayList<>();

        for (AuctionHouse.Listing l : from) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.id);
            m.put("seller", l.seller.toString());
            m.put("name", l.sellerName);
            m.put("item", Base64.getEncoder().encodeToString(l.item.serializeAsBytes()));
            m.put("price", l.price);
            m.put("created", l.created);
            m.put("expires", l.expires);
            out.add(m);
        }

        return out;
    }

    public void save() {
        if (this.dirty) {
            this.dirty = false;
            YamlConfiguration y = new YamlConfiguration();
            y.set("listings", write(this.listings));
            y.set("expired", write(this.expired));

            try {
                y.save(this.file);
            } catch (IOException var3) {
                this.plugin.getLogger().log(Level.WARNING, "Could not save auction-house.yml", (Throwable)var3);
            }
        }
    }

    public void tick() {
        long now = System.currentTimeMillis();

        for (AuctionHouse.Listing l : new ArrayList<>(this.listings)) {
            if (now >= l.expires) {
                this.listings.remove(l);
                this.expired.add(l);
                this.dirty = true;
                Player s = Bukkit.getPlayer(l.seller);
                if (s != null) {
                    s.sendMessage(Text.mm("<gold>AH <dark_gray>» <gray>Your listing expired - collect it in <white>/ah </white>→ Your Items."));
                }
            }
        }

        this.save();
    }

    private static String name(ItemStack it) {
        if (it.hasItemMeta() && it.getItemMeta().hasDisplayName()) {
            return Text.plain(it.getItemMeta().displayName());
        } else {
            String base = it.getType().getKey().getKey().replace('_', ' ');
            return Character.toUpperCase(base.charAt(0)) + base.substring(1);
        }
    }

    private void list(Player p, String priceArg) {
        if (!this.eco.inEco(p)) {
            p.sendMessage(Text.mm("<red>The auction house is in the Eco SMP."));
        } else {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.isEmpty()) {
                p.sendMessage(Text.mm("<red>Hold the item you want to sell. <gray>/ah sell <price>"));
            } else {
                double price;
                try {
                    String raw = priceArg.toLowerCase(Locale.ROOT).replace(",", "");
                    double mult = raw.endsWith("k") ? 1000.0 : (raw.endsWith("m") ? 1000000.0 : (raw.endsWith("b") ? 1.0E9 : 1.0));
                    if (mult > 1.0) {
                        raw = raw.substring(0, raw.length() - 1);
                    }

                    price = Math.floor(Double.parseDouble(raw) * mult * 100.0) / 100.0;
                } catch (NumberFormatException var12) {
                    p.sendMessage(Text.mm("<red>Not a price. <gray>/ah sell 500, /ah sell 2.5k"));
                    return;
                }

                if (price < 1.0) {
                    p.sendMessage(Text.mm("<red>The lowest price is " + this.eco.money(1.0)));
                } else {
                    long mine = this.listings.stream().filter(l -> l.seller.equals(p.getUniqueId())).count();
                    int max = p.hasPermission("vortexpvp.ah.unlimited") ? 999 : this.eco.ahMaxListings;
                    if (mine >= max) {
                        p.sendMessage(Text.mm("<red>You already have " + mine + " listings (max " + max + ")."));
                    } else {
                        ItemStack item = hand.clone();
                        p.getInventory().setItemInMainHand(null);
                        long now = System.currentTimeMillis();
                        this.listings
                            .add(
                                new AuctionHouse.Listing(
                                    Long.toString(now, 36) + Integer.toString((int)(Math.random() * 1296.0), 36),
                                    p.getUniqueId(),
                                    p.getName(),
                                    item,
                                    price,
                                    now,
                                    now + this.eco.ahHours * 3600000L
                                )
                            );
                        this.dirty = true;
                        this.save();
                        p.sendMessage(
                            Text.mm(
                                "<gold>AH <dark_gray>» <gray>Listed <white>"
                                    + item.getAmount()
                                    + "x "
                                    + Text.esc(name(item))
                                    + "</white> for <#3DDC84>"
                                    + this.eco.money(price)
                                    + "</#3DDC84> <dark_gray>("
                                    + this.eco.ahHours
                                    + "h, tax "
                                    + this.eco.ahTax
                                    + "%)"
                            )
                        );
                        Snd.play(p, "block.note_block.pling", 0.6F, 1.5F);
                    }
                }
            }
        }
    }

    private void buy(Player p, AuctionHouse.Listing l) {
        if (!this.listings.contains(l)) {
            p.sendMessage(Text.mm("<red>That item was already sold."));
        } else if (l.seller.equals(p.getUniqueId())) {
            p.sendMessage(Text.mm("<red>That's your own listing."));
        } else if (p.getInventory().firstEmpty() < 0) {
            p.sendMessage(Text.mm("<red>Make room in your inventory first."));
        } else if (!this.eco.take(p.getUniqueId(), l.price)) {
            p.sendMessage(Text.mm("<red>You need " + this.eco.money(l.price) + "."));
            Snd.error(p);
        } else {
            this.listings.remove(l);
            this.dirty = true;
            p.getInventory().addItem(new ItemStack[]{l.item.clone()});
            double paid = Math.floor(l.price * (100.0 - this.eco.ahTax)) / 100.0;
            this.eco.give(l.seller, paid);
            this.save();
            p.sendMessage(
                Text.mm(
                    "<gold>AH <dark_gray>» <gray>Bought <white>"
                        + l.item.getAmount()
                        + "x "
                        + Text.esc(name(l.item))
                        + "</white> for <#3DDC84>"
                        + this.eco.money(l.price)
                )
            );
            Snd.play(p, "entity.experience_orb.pickup", 0.8F, 1.4F);
            Player s = Bukkit.getPlayer(l.seller);
            if (s != null) {
                s.sendMessage(
                    Text.mm(
                        "<gold>AH <dark_gray>» <white>"
                            + Text.esc(p.getName())
                            + " <gray>bought your <white>"
                            + Text.esc(name(l.item))
                            + " <gray>- <#3DDC84>+"
                            + this.eco.money(paid)
                    )
                );
                Snd.play(s, "entity.experience_orb.pickup", 1.0F, 1.6F);
            }
        }
    }

    private static void giveOrDrop(Player p, ItemStack it) {
        for (ItemStack rest : p.getInventory().addItem(new ItemStack[]{it.clone()}).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), rest);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            return true;
        } else if (args.length < 2 || !args[0].equalsIgnoreCase("sell") && !args[0].equalsIgnoreCase("list")) {
            if (!this.eco.inEco(p) && !p.hasPermission("vortexpvp.admin")) {
                p.sendMessage(Text.mm("<red>The auction house is in the Eco SMP."));
                return true;
            } else {
                if (args.length >= 1 && !args[0].equalsIgnoreCase("sell")) {
                    new AuctionHouse.Market(p, 0, AuctionHouse.Sort.NEWEST, String.join(" ", args)).open();
                } else {
                    new AuctionHouse.Market(p, 0, AuctionHouse.Sort.NEWEST, null).open();
                }

                return true;
            }
        } else {
            this.list(p, args[1]);
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("sell") : List.of();
    }

    private final class Confirm extends Menu {
        private final AuctionHouse.Listing l;
        private final AuctionHouse.Market back;

        Confirm(Player viewer, AuctionHouse.Listing l, AuctionHouse.Market back) {
            super(AuctionHouse.this.plugin, viewer);
            this.l = l;
            this.back = back;
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Confirm Purchase");
        }

        @Override
        protected int rows() {
            return 3;
        }

        @Override
        protected void draw() {
            this.set(
                13,
                Items.of(this.l.item.clone())
                    .lore("<gray>Price: <#3DDC84><b>" + AuctionHouse.this.eco.money(this.l.price), "<gray>Seller: <white>" + Text.esc(this.l.sellerName))
                    .build()
            );

            for (int s : new int[]{9, 10, 11}) {
                this.set(
                    s,
                    Items.of(Material.RED_STAINED_GLASS_PANE).name("<red><b>Cancel").build(),
                    t -> AuctionHouse.this.new Market(this.viewer, 0, AuctionHouse.Sort.NEWEST, null).open()
                );
            }

            for (int s : new int[]{15, 16, 17}) {
                this.set(s, Items.of(Material.LIME_STAINED_GLASS_PANE).name("<green><b>Buy for " + AuctionHouse.this.eco.money(this.l.price)).build(), t -> {
                    AuctionHouse.this.buy(this.viewer, this.l);
                    AuctionHouse.this.new Market(this.viewer, 0, AuctionHouse.Sort.NEWEST, null).open();
                });
            }

            this.fill(Material.GRAY_STAINED_GLASS_PANE);
        }
    }

    private static final class Listing {
        final String id;
        final UUID seller;
        final String sellerName;
        final ItemStack item;
        final double price;
        final long created;
        final long expires;

        Listing(String id, UUID seller, String sellerName, ItemStack item, double price, long created, long expires) {
            this.id = id;
            this.seller = seller;
            this.sellerName = sellerName;
            this.item = item;
            this.price = price;
            this.created = created;
            this.expires = expires;
        }
    }

    private final class Market extends Menu {
        private final int page;
        private final AuctionHouse.Sort sort;
        private final String search;

        Market(Player viewer, int page, AuctionHouse.Sort sort, String search) {
            super(AuctionHouse.this.plugin, viewer);
            this.page = page;
            this.sort = sort;
            this.search = search;
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Auction House" + (this.search == null ? "" : " » " + Text.esc(this.search)));
        }

        @Override
        protected int rows() {
            return 6;
        }

        @Override
        protected void draw() {
            List<AuctionHouse.Listing> view = new ArrayList<>();

            for (AuctionHouse.Listing l : AuctionHouse.this.listings) {
                if (this.search == null || AuctionHouse.name(l.item).toLowerCase(Locale.ROOT).contains(this.search.toLowerCase(Locale.ROOT))) {
                    view.add(l);
                }
            }
            view.sort(switch (this.sort) {
                case CHEAPEST -> Comparator.comparingDouble(lx -> lx.price);
                case PRICIEST -> Comparator.comparingDouble(lx -> -lx.price);
                default -> Comparator.comparingLong(lx -> -lx.created);
            });
            int from = this.page * 45;
            long now = System.currentTimeMillis();

            for (int i = 0; i < 45 && from + i < view.size(); i++) {
                AuctionHouse.Listing lx = view.get(from + i);
                ItemStack shown = lx.item.clone();
                List<Component> lore = new ArrayList<>();
                if (shown.hasItemMeta() && shown.getItemMeta().lore() != null) {
                    lore.addAll(shown.getItemMeta().lore());
                    lore.add(Component.empty());
                }

                lore.add(Text.noItalic(Text.mm("<dark_gray>───────────────")));
                lore.add(Text.noItalic(Text.mm("<gray>Price: <#3DDC84><b>" + AuctionHouse.this.eco.money(lx.price))));
                lore.add(Text.noItalic(Text.mm("<gray>Seller: <white>" + Text.esc(lx.sellerName))));
                lore.add(Text.noItalic(Text.mm("<gray>Ends in: <white>" + Fmt.duration((lx.expires - now) / 1000L))));
                lore.add(Component.empty());
                lore.add(Text.noItalic(Text.mm(lx.seller.equals(this.viewer.getUniqueId()) ? "<yellow>Your listing" : "<green>▶ Click to buy")));
                shown.editMeta(m -> m.lore(lore));
                this.set(i, shown, t -> {
                    if (!lx.seller.equals(this.viewer.getUniqueId())) {
                        AuctionHouse.this.new Confirm(this.viewer, lx, this).open();
                    }
                });
            }

            for (int i = 45; i < 54; i++) {
                this.set(i, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
            }

            if (this.page > 0) {
                this.set(45, Ui.prev(this.page), t -> AuctionHouse.this.new Market(this.viewer, this.page - 1, this.sort, this.search).open());
            }
            this.set(
                47,
                Items.of(Material.HOPPER).name("<gold><b>Sort: <white>" + switch (this.sort) {
                    case CHEAPEST -> "Cheapest";
                    case PRICIEST -> "Most expensive";
                    default -> "Newest";
                }).lore("<yellow>▶ Click to change").build(),
                t -> AuctionHouse.this.new Market(
                        this.viewer, 0, AuctionHouse.Sort.values()[(this.sort.ordinal() + 1) % AuctionHouse.Sort.values().length], this.search
                    )
                    .open()
            );
            this.set(
                48,
                Items.of(Material.OAK_SIGN)
                    .name("<gold><b>Search")
                    .lore(
                        this.search == null ? "<gray>Find an item by name" : "<gray>Now: <white>" + Text.esc(this.search),
                        "",
                        "<yellow>Click <gray>search",
                        "<yellow>Right-click <gray>clear"
                    )
                    .build(),
                t -> {
                    if (t.isRightClick()) {
                        AuctionHouse.this.new Market(this.viewer, 0, this.sort, null).open();
                    } else {
                        this.viewer.closeInventory();
                        this.plugin
                            .dialogs()
                            .text(
                                this.viewer,
                                Text.mm("<gold><b>Search the AH"),
                                "Item name",
                                "",
                                32,
                                q -> AuctionHouse.this.new Market(this.viewer, 0, this.sort, q.isBlank() ? null : q.trim()).open()
                            );
                    }
                }
            );
            this.set(
                49,
                Items.of(Material.SUNFLOWER)
                    .name("<#3DDC84><b>Balance")
                    .lore(
                        "<white>" + AuctionHouse.this.eco.money(AuctionHouse.this.eco.balance(this.viewer.getUniqueId())),
                        "",
                        "<gray>" + AuctionHouse.this.listings.size() + " items for sale"
                    )
                    .build()
            );
            long mine = AuctionHouse.this.listings.stream().filter(lx -> lx.seller.equals(this.viewer.getUniqueId())).count()
                + AuctionHouse.this.expired.stream().filter(lx -> lx.seller.equals(this.viewer.getUniqueId())).count();
            this.set(
                50,
                Items.of(Material.CHEST)
                    .name("<gold><b>Your Items <white>(" + mine + ")")
                    .lore("<gray>Your listings and expired items.", "", "<gray>Sell: hold an item, <white>/ah sell <price>", "", "<yellow>▶ Click")
                    .build(),
                t -> AuctionHouse.this.new Mine(this.viewer).open()
            );
            this.set(51, Items.of(Material.SUNFLOWER).name("<gold><b>Refresh").build(), t -> this.redraw());
            if (from + 45 < view.size()) {
                this.set(53, Ui.next(this.page), t -> AuctionHouse.this.new Market(this.viewer, this.page + 1, this.sort, this.search).open());
            }
        }
    }

    private final class Mine extends Menu {
        Mine(Player viewer) {
            super(AuctionHouse.this.plugin, viewer);
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Your Items");
        }

        @Override
        protected int rows() {
            return 6;
        }

        @Override
        protected void draw() {
            int slot = 0;

            for (AuctionHouse.Listing l : new ArrayList<>(AuctionHouse.this.listings)) {
                if (l.seller.equals(this.viewer.getUniqueId()) && slot < 27) {
                    this.set(
                        slot++,
                        Items.of(l.item.clone())
                            .lore(
                                "<gray>Listed for <#3DDC84>" + AuctionHouse.this.eco.money(l.price),
                                "<gray>Ends in <white>" + Fmt.duration((l.expires - System.currentTimeMillis()) / 1000L),
                                "",
                                "<red>▶ Click to cancel"
                            )
                            .build(),
                        t -> {
                            if (AuctionHouse.this.listings.remove(l)) {
                                AuctionHouse.this.dirty = true;
                                AuctionHouse.giveOrDrop(this.viewer, l.item);
                                AuctionHouse.this.save();
                            }

                            this.redraw();
                        }
                    );
                }
            }

            slot = 36;

            for (AuctionHouse.Listing lx : new ArrayList<>(AuctionHouse.this.expired)) {
                if (lx.seller.equals(this.viewer.getUniqueId()) && slot < 45) {
                    this.set(slot++, Items.of(lx.item.clone()).lore("<red>Expired", "", "<green>▶ Click to collect").build(), t -> {
                        if (AuctionHouse.this.expired.remove(lx)) {
                            AuctionHouse.this.dirty = true;
                            AuctionHouse.giveOrDrop(this.viewer, lx.item);
                            AuctionHouse.this.save();
                        }

                        this.redraw();
                    });
                }
            }

            for (int i = 27; i < 36; i++) {
                this.set(i, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
            }

            this.set(31, Items.of(Material.CLOCK).name("<gold><b>Expired items below").build());

            for (int i = 45; i < 54; i++) {
                this.set(i, Items.filler(Material.GRAY_STAINED_GLASS_PANE));
            }

            this.set(49, Ui.back(), t -> AuctionHouse.this.new Market(this.viewer, 0, AuctionHouse.Sort.NEWEST, null).open());
        }
    }

    private static enum Sort {
        NEWEST,
        CHEAPEST,
        PRICIEST;
    }
}
