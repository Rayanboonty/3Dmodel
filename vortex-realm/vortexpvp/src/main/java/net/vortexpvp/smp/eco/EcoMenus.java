package net.vortexpvp.smp.eco;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.gui.Menu;
import net.vortexpvp.gui.Ui;
import net.vortexpvp.smp.Economy;
import net.vortexpvp.smp.ModeManager;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;

public final class EcoMenus {
    private static final Material FRAME = Material.GRAY_STAINED_GLASS_PANE;
    private static final Material DARK = Material.BLACK_STAINED_GLASS_PANE;
    private final VortexPvP plugin;
    private final Economy eco;

    public EcoMenus(VortexPvP plugin, Economy eco) {
        this.plugin = plugin;
        this.eco = eco;
    }

    private String money(double v) {
        return this.eco.money(v);
    }

    private static String nice(Material m) {
        String base = m.getKey().getKey().replace('_', ' ');
        StringBuilder sb = new StringBuilder();

        for (String w : base.split(" ")) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }

        return sb.toString().trim();
    }

    private ItemStack balanceItem(Player p) {
        return Items.of(Material.SUNFLOWER)
            .name("<#3DDC84><b>Your Balance")
            .lore(
                "<white>" + this.money(this.eco.balance(p.getUniqueId())),
                "",
                "<gray>Earn money with <white>/sell</white>,",
                "<gray>the <white>/ah</white> and <white>/orders</white>."
            )
            .build();
    }

    public void shop(Player p) {
        new EcoMenus.ShopMain(p).open();
    }

    private void buy(Player p, Economy.ShopItem item, int bundles) {
        double total = item.price * bundles;
        if (this.eco.balance(p.getUniqueId()) < total) {
            p.sendActionBar(Text.mm("<red>You need " + this.money(total)));
            Snd.error(p);
        } else {
            int count = item.amount * bundles;
            List<ItemStack> stacks = new ArrayList<>();
            int left = count;

            while (left > 0) {
                int n = Math.min(left, item.material.getMaxStackSize());
                stacks.add(item.stack(n));
                left -= n;
            }

            Map<Integer, ItemStack> rest = p.getInventory().addItem(stacks.toArray(new ItemStack[0]));
            if (rest.isEmpty()) {
                this.eco.take(p.getUniqueId(), total);
                p.sendActionBar(Text.mm("<gray>Bought <white>" + count + "x " + Text.esc(item.name()) + "</white> for <#3DDC84>" + this.money(total)));
                Snd.play(p, "entity.experience_orb.pickup", 0.8F, 1.6F);
            } else {
                int missing = rest.values().stream().mapToInt(ItemStack::getAmount).sum();
                ItemStack back = item.stack(1);
                back.setAmount(1);

                for (int i = 0; i < count - missing; i++) {
                    p.getInventory().removeItem(new ItemStack[]{back.clone()});
                }

                p.sendActionBar(Text.mm("<red>Your inventory is full."));
                Snd.error(p);
            }
        }
    }

    public void sell(Player p) {
        new EcoMenus.SellMenu(p).open();
    }

    public void baltop(Player p) {
        new EcoMenus.BaltopMenu(p).open();
    }

    public void shards(Player p) {
        new EcoMenus.ShardMenu(p).open();
    }

    public void admin(Player p) {
        new EcoMenus.AdminMenu(p).open();
    }

    static String id(String s) {
        return s.toLowerCase(Locale.ROOT);
    }

    private final class AdminMenu extends Menu {
        AdminMenu(Player viewer) {
            super(EcoMenus.this.plugin, viewer);
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Eco Admin");
        }

        @Override
        protected int rows() {
            return 3;
        }

        @Override
        protected void draw() {
            this.set(
                10,
                Items.of(Material.CHEST).name("<gold><b>Shop Editor").lore("<gray>Categories, items, prices.", "", "<green>▶ Click").build(),
                t -> EcoMenus.this.new CategoriesEditor(this.viewer).open()
            );
            this.set(
                12,
                Items.of(Material.EMERALD)
                    .name("<gold><b>Sell Prices")
                    .lore("<gray>What every item sells for", "<gray>" + EcoMenus.this.eco.sellPrices().size() + " items", "", "<green>▶ Click")
                    .build(),
                t -> EcoMenus.this.new WorthEditor(this.viewer, 0).open()
            );
            this.set(
                14,
                Items.of(Material.GOLD_INGOT)
                    .name("<gold><b>Auction House")
                    .lore(
                        "<gray>Tax <white>" + EcoMenus.this.eco.ahTax + "%",
                        "<gray>Max listings <white>" + EcoMenus.this.eco.ahMaxListings,
                        "<gray>Listing time <white>" + EcoMenus.this.eco.ahHours + "h",
                        "",
                        "<yellow>Click <gray>tax  <yellow>Right <gray>max  <yellow>Shift <gray>hours"
                    )
                    .build(),
                t -> {
                    if (t.isShiftClick()) {
                        this.plugin
                            .dialogs()
                            .number(this.viewer, Text.mm("<gold><b>Listing hours"), "Hours", 1.0F, 336.0F, 1.0F, EcoMenus.this.eco.ahHours, v -> {
                                EcoMenus.this.eco.ahHours = Math.round(v);
                                EcoMenus.this.eco.save();
                                EcoMenus.this.admin(this.viewer);
                            });
                    } else if (t.isRightClick()) {
                        this.plugin
                            .dialogs()
                            .number(this.viewer, Text.mm("<gold><b>Max listings"), "Per player", 1.0F, 64.0F, 1.0F, EcoMenus.this.eco.ahMaxListings, v -> {
                                EcoMenus.this.eco.ahMaxListings = Math.round(v);
                                EcoMenus.this.eco.save();
                                EcoMenus.this.admin(this.viewer);
                            });
                    } else {
                        this.plugin
                            .dialogs()
                            .number(this.viewer, Text.mm("<gold><b>Auction tax"), "Percent", 0.0F, 50.0F, 0.5F, (float)EcoMenus.this.eco.ahTax, v -> {
                                EcoMenus.this.eco.ahTax = v.floatValue();
                                EcoMenus.this.eco.save();
                                EcoMenus.this.admin(this.viewer);
                            });
                    }
                }
            );
            this.set(16, Items.of(Material.PLAYER_HEAD).name("<gold><b>Player Money").lore("<gray>/balance give|take|set", "<gray><player> <amount>").build());
            this.fill(EcoMenus.FRAME);
        }
    }

    private final class BaltopMenu extends Menu {
        BaltopMenu(Player viewer) {
            super(EcoMenus.this.plugin, viewer);
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Richest Players");
        }

        @Override
        protected int rows() {
            return 5;
        }

        @Override
        protected void draw() {
            List<Entry<UUID, Double>> list = new ArrayList<>();

            for (Entry<UUID, ModeManager.Stats> e : this.plugin.modes().allStats().entrySet()) {
                if (e.getValue().balance > 0.0) {
                    list.add(Map.entry(e.getKey(), e.getValue().balance));
                }
            }

            list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            int[] slots = new int[]{13, 21, 23, 28, 29, 30, 31, 32, 33, 34};
            String[] colors = new String[]{"<#FFD700>", "<#E0E0E0>", "<#CD7F32>"};

            for (int i = 0; i < Math.min(slots.length, list.size()); i++) {
                OfflinePlayer o = Bukkit.getOfflinePlayer(list.get(i).getKey());
                String c = i < 3 ? colors[i] : "<white>";
                this.set(
                    slots[i],
                    Items.of(Material.PLAYER_HEAD)
                        .skull(o)
                        .name(c + "<b>#" + (i + 1) + " " + Text.esc(String.valueOf(o.getName())))
                        .lore("<#3DDC84>" + EcoMenus.this.money(list.get(i).getValue()))
                        .build()
                );
            }

            int mine = -1;

            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getKey().equals(this.viewer.getUniqueId())) {
                    mine = i + 1;
                }
            }

            this.set(
                40,
                Items.of(Material.PLAYER_HEAD)
                    .skull(this.viewer)
                    .name("<gold><b>You " + (mine > 0 ? "#" + mine : ""))
                    .lore("<#3DDC84>" + EcoMenus.this.money(EcoMenus.this.eco.balance(this.viewer.getUniqueId())))
                    .build()
            );
            this.fill(EcoMenus.FRAME);
        }
    }

    /** DonutSMP-style shard shop: shards are earned by playing and by kills, spent here. */
    private final class ShardMenu extends Menu {
        ShardMenu(Player viewer) {
            super(EcoMenus.this.plugin, viewer);
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Shard Shop");
        }

        @Override
        protected int rows() {
            return 6;
        }

        @Override
        protected void draw() {
            List<Economy.ShopItem> items = EcoMenus.this.eco.shardShop;
            int[] use = Ui.fit(Math.min(items.size(), 28), 6);
            long have = EcoMenus.this.eco.shards(this.viewer.getUniqueId());

            for (int i = 0; i < items.size() && i < use.length; i++) {
                Economy.ShopItem it = items.get(i);
                long price = Math.round(it.price);
                boolean afford = have >= price;
                ItemStack icon = Items.of(it.stack(Math.min(it.amount, it.material.getMaxStackSize())))
                    .name("<white><b>" + Text.esc(it.name()))
                    .lore(
                        "<gray>Amount: <white>" + it.amount,
                        "<gray>Price: " + (afford ? "<#B57CFF>" : "<red>") + price + " shards",
                        "",
                        afford ? "<green>▶ Click to buy" : "<red>Not enough shards"
                    )
                    .build();
                this.set(use[i], icon, t -> {
                    if (!EcoMenus.this.eco.takeShards(this.viewer.getUniqueId(), price)) {
                        Snd.play(this.viewer, "entity.villager.no", 0.8F, 1.0F);
                    } else {
                        for (ItemStack left : this.viewer.getInventory().addItem(it.stack(it.amount)).values()) {
                            this.viewer.getWorld().dropItemNaturally(this.viewer.getLocation(), left);
                        }

                        Snd.play(this.viewer, "block.amethyst_block.chime", 1.0F, 1.2F);
                        this.viewer.sendMessage(Text.mm("<gray>Bought <white>" + it.amount + "x " + Text.esc(it.name()) + "</white> for <#B57CFF>" + price + " shards"));
                        this.open();
                    }
                });
            }

            this.set(
                49,
                Items.of(Material.AMETHYST_SHARD)
                    .name("<#B57CFF><b>Your Shards")
                    .lore("<white>" + have, "", "<gray>Earn shards by playing, in", "<gray>the AFK zone and by killing players.")
                    .build()
            );
            this.set(
                51,
                Items.of(Material.AMETHYST_CLUSTER)
                    .name("<#B57CFF><b>AFK Zone")
                    .lore("<gray>+" + EcoMenus.this.eco.afkAmount + " shard every " + EcoMenus.this.eco.afkSeconds + "s", "<gray>while you stay inside.", "", "<green>▶ Click to go")
                    .build(),
                t -> {
                    this.viewer.closeInventory();
                    this.viewer.performCommand("afk");
                }
            );
            this.border(EcoMenus.FRAME);
        }
    }

    private final class CategoriesEditor extends Menu {
        CategoriesEditor(Player viewer) {
            super(EcoMenus.this.plugin, viewer);
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Shop Editor");
        }

        @Override
        protected int rows() {
            return 6;
        }

        @Override
        protected void draw() {
            int[] slots = Ui.innerSlots(6);
            int i = 0;

            for (Economy.Category c : EcoMenus.this.eco.categories().values()) {
                if (i >= slots.length) {
                    break;
                }

                this.set(
                    slots[i++],
                    Items.of(c.icon)
                        .name(c.name)
                        .lore(
                            "<gray>" + c.items.size() + " items",
                            "",
                            "<yellow>Click <gray>edit items",
                            "<yellow>Right-click <gray>rename",
                            "<yellow>Shift-click <gray>icon = your hand",
                            "<red>Drop key (Q) <gray>delete"
                        )
                        .hideAll()
                        .build(),
                    t -> {
                        if (t == ClickType.DROP || t == ClickType.CONTROL_DROP) {
                            this.plugin
                                .dialogs()
                                .confirm(
                                    this.viewer,
                                    Text.mm("<red><b>Delete " + Text.plain(Text.mm(c.name)) + "?"),
                                    List.of(Text.mm("<gray>All its items go with it.")),
                                    () -> {
                                        EcoMenus.this.eco.removeCategory(c.id);
                                        EcoMenus.this.new CategoriesEditor(this.viewer).open();
                                    }
                                );
                        } else if (t.isShiftClick()) {
                            ItemStack h = this.viewer.getInventory().getItemInMainHand();
                            if (!h.isEmpty()) {
                                c.icon = h.getType();
                                EcoMenus.this.eco.save();
                            }

                            this.redraw();
                        } else if (t.isRightClick()) {
                            this.plugin.dialogs().text(this.viewer, Text.mm("<gold><b>Category name"), "Name (MiniMessage)", c.name, 64, v -> {
                                c.name = v;
                                EcoMenus.this.eco.save();
                                EcoMenus.this.new CategoriesEditor(this.viewer).open();
                            });
                        } else {
                            EcoMenus.this.new ItemsEditor(this.viewer, c).open();
                        }
                    }
                );
            }

            this.set(
                49,
                Items.of(Material.LIME_CONCRETE).name("<green><b>+ New Category").lore("<gray>Icon = the item in your hand").build(),
                t -> this.plugin.dialogs().text(this.viewer, Text.mm("<gold><b>New category"), "Name (MiniMessage)", "<gold><b>New", 64, v -> {
                    ItemStack h = this.viewer.getInventory().getItemInMainHand();
                    EcoMenus.this.eco.addCategory(v, h.isEmpty() ? Material.CHEST : h.getType());
                    EcoMenus.this.new CategoriesEditor(this.viewer).open();
                })
            );
            this.set(45, Ui.back(), t -> EcoMenus.this.admin(this.viewer));
            this.border(EcoMenus.FRAME);
        }
    }

    private final class CategoryMenu extends Menu {
        private final Economy.Category cat;
        private final int page;

        CategoryMenu(Player viewer, Economy.Category cat, int page) {
            super(EcoMenus.this.plugin, viewer);
            this.cat = cat;
            this.page = page;
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Shop » ").append(Text.mm(this.cat.name));
        }

        @Override
        protected int rows() {
            return 6;
        }

        @Override
        protected void draw() {
            int[] slots = Ui.innerSlots(6);
            int per = slots.length;
            int from = this.page * per;
            int shown = Math.min(per, this.cat.items.size() - from);
            int[] use = shown <= per ? Ui.fit(shown, 6) : slots;

            for (int i = 0; i < shown && i < use.length; i++) {
                Economy.ShopItem it = this.cat.items.get(from + i);
                boolean afford = EcoMenus.this.eco.balance(this.viewer.getUniqueId()) >= it.price;
                ItemStack icon = it.stack(Math.min(it.amount, it.material.getMaxStackSize()));
                icon = Items.of(icon)
                    .name("<white><b>" + Text.esc(it.name()))
                    .lore(
                        "<gray>Amount: <white>" + it.amount,
                        "<gray>Price: " + (afford ? "<#3DDC84>" : "<red>") + EcoMenus.this.money(it.price),
                        "",
                        "<green>▶ Click to buy",
                        "<gray>Pick how many next"
                    )
                    .build();
                this.set(use[i], icon, t -> EcoMenus.this.new QuantityMenu(this.viewer, this.cat, it, this.page).open());
            }

            this.set(45, Items.of(Material.ARROW).name("<gold><b>« Back").build(), t -> EcoMenus.this.shop(this.viewer));
            if (this.page > 0) {
                this.set(48, Ui.prev(this.page), t -> EcoMenus.this.new CategoryMenu(this.viewer, this.cat, this.page - 1).open());
            }

            this.set(49, EcoMenus.this.balanceItem(this.viewer));
            if (from + per < this.cat.items.size()) {
                this.set(50, Ui.next(this.page), t -> EcoMenus.this.new CategoryMenu(this.viewer, this.cat, this.page + 1).open());
            }

            this.set(53, Ui.close(), t -> this.viewer.closeInventory());
            this.border(EcoMenus.FRAME);
        }
    }

    private final class ItemsEditor extends Menu {
        private final Economy.Category cat;

        ItemsEditor(Player viewer, Economy.Category cat) {
            super(EcoMenus.this.plugin, viewer);
            this.cat = cat;
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Edit » ").append(Text.mm(this.cat.name));
        }

        @Override
        protected int rows() {
            return 6;
        }

        @Override
        protected void draw() {
            int[] slots = Ui.innerSlots(6);

            for (int i = 0; i < slots.length && i < this.cat.items.size(); i++) {
                Economy.ShopItem it = this.cat.items.get(i);
                int index = i;
                this.set(
                    slots[i],
                    Items.of(it.stack(Math.min(it.amount, it.material.getMaxStackSize())))
                        .name("<white><b>" + Text.esc(it.name()))
                        .lore(
                            "<gray>Amount <white>" + it.amount,
                            "<gray>Price <#3DDC84>" + EcoMenus.this.money(it.price),
                            "",
                            "<yellow>Click <gray>price",
                            "<yellow>Right-click <gray>amount",
                            "<yellow>Shift-click <gray>move left",
                            "<red>Drop key (Q) <gray>remove"
                        )
                        .build(),
                    t -> {
                        if (t == ClickType.DROP || t == ClickType.CONTROL_DROP) {
                            this.cat.items.remove(it);
                            EcoMenus.this.eco.save();
                            this.redraw();
                        } else if (t.isShiftClick()) {
                            if (index > 0) {
                                this.cat.items.remove(index);
                                this.cat.items.add(index - 1, it);
                                EcoMenus.this.eco.save();
                            }

                            this.redraw();
                        } else if (t.isRightClick()) {
                            this.plugin.dialogs().number(this.viewer, Text.mm("<gold><b>Amount"), "Items per purchase", 1.0F, 64.0F, 1.0F, it.amount, v -> {
                                it.amount = Math.round(v);
                                EcoMenus.this.eco.save();
                                EcoMenus.this.new ItemsEditor(this.viewer, this.cat).open();
                            });
                        } else {
                            this.plugin.dialogs().text(this.viewer, Text.mm("<gold><b>Price"), "Price for " + it.amount, String.valueOf(it.price), 16, v -> {
                                try {
                                    it.price = Math.max(0.0, Double.parseDouble(v.trim()));
                                    EcoMenus.this.eco.save();
                                } catch (NumberFormatException var4x) {
                                    this.viewer.sendMessage(Text.mm("<red>Not a number."));
                                }

                                EcoMenus.this.new ItemsEditor(this.viewer, this.cat).open();
                            });
                        }
                    }
                );
            }

            this.set(
                49,
                Items.of(Material.LIME_CONCRETE)
                    .name("<green><b>+ Add Held Item")
                    .lore("<gray>The item in your hand,", "<gray>its amount = the stack size.")
                    .build(),
                t -> {
                    ItemStack h = this.viewer.getInventory().getItemInMainHand();
                    if (h.isEmpty()) {
                        this.viewer.sendMessage(Text.mm("<red>Hold the item you want to sell in the shop."));
                    } else {
                        String potion = null;
                        if (h.getItemMeta() instanceof PotionMeta pm && pm.getBasePotionType() != null) {
                            potion = pm.getBasePotionType().name();
                        }

                        String pot = potion;
                        this.plugin.dialogs().text(this.viewer, Text.mm("<gold><b>Price"), "Price for " + h.getAmount() + "x", "10", 16, v -> {
                            try {
                                this.cat.items.add(new Economy.ShopItem(h.getType(), h.getAmount(), Math.max(0.0, Double.parseDouble(v.trim())), pot));
                                EcoMenus.this.eco.save();
                            } catch (NumberFormatException var5) {
                                this.viewer.sendMessage(Text.mm("<red>Not a number."));
                            }

                            EcoMenus.this.new ItemsEditor(this.viewer, this.cat).open();
                        });
                    }
                }
            );
            this.set(45, Ui.back(), t -> EcoMenus.this.new CategoriesEditor(this.viewer).open());
            this.border(EcoMenus.FRAME);
        }
    }

    private final class QuantityMenu extends Menu {
        private final Economy.Category cat;
        private final Economy.ShopItem item;
        private final int back;
        private int bundles = 1;

        QuantityMenu(Player viewer, Economy.Category cat, Economy.ShopItem item, int back) {
            super(EcoMenus.this.plugin, viewer);
            this.cat = cat;
            this.item = item;
            this.back = back;
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Buy " + Text.esc(this.item.name()));
        }

        @Override
        protected int rows() {
            return 4;
        }

        private int maxBundles() {
            int free = 0;

            for (ItemStack s : this.viewer.getInventory().getStorageContents()) {
                if (s == null || s.isEmpty()) {
                    free += this.item.material.getMaxStackSize();
                } else if (s.isSimilar(this.item.stack(1))) {
                    free += s.getMaxStackSize() - s.getAmount();
                }
            }

            int byMoney = this.item.price <= 0.0 ? 999 : (int)Math.floor(EcoMenus.this.eco.balance(this.viewer.getUniqueId()) / this.item.price);
            return Math.max(0, Math.min(Math.min(free / Math.max(1, this.item.amount), byMoney), 64));
        }

        @Override
        protected void draw() {
            int max = this.maxBundles();
            this.bundles = Math.max(1, Math.min(this.bundles, Math.max(1, max)));
            int count = this.bundles * this.item.amount;
            double total = this.bundles * this.item.price;
            ItemStack show = Items.of(this.item.stack(Math.min(64, count)))
                .name("<white><b>" + count + "x " + Text.esc(this.item.name()))
                .lore(
                    "<gray>Total: <#3DDC84>" + EcoMenus.this.money(total),
                    "<gray>Balance: <white>" + EcoMenus.this.money(EcoMenus.this.eco.balance(this.viewer.getUniqueId()))
                )
                .build();
            this.set(13, show);
            int[][] steps = new int[][]{{10, -10}, {11, -1}, {15, 1}, {16, 10}};

            for (int[] s : steps) {
                int delta = s[1];
                this.set(
                    s[0],
                    Items.of(delta < 0 ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE)
                        .name((delta < 0 ? "<red><b>" : "<green><b>+") + delta)
                        .build(),
                    t -> {
                        this.bundles = Math.max(1, this.bundles + delta);
                        this.redraw();
                    }
                );
            }

            this.set(12, Items.of(Material.LIME_STAINED_GLASS_PANE).name("<green><b>Max (" + Math.max(1, max) + ")").build(), t -> {
                this.bundles = Math.max(1, max);
                this.redraw();
            });
            this.set(
                30,
                Items.of(Material.RED_CONCRETE).name("<red><b>Cancel").build(),
                t -> EcoMenus.this.new CategoryMenu(this.viewer, this.cat, this.back).open()
            );
            this.set(
                32,
                Items.of(Material.LIME_CONCRETE).name("<green><b>Confirm").lore("<gray>Buy " + count + "x for <#3DDC84>" + EcoMenus.this.money(total)).build(),
                t -> {
                    EcoMenus.this.buy(this.viewer, this.item, this.bundles);
                    this.redraw();
                }
            );
            this.fill(EcoMenus.FRAME);
        }
    }

    private final class SellMenu extends Menu {
        private boolean done;

        SellMenu(Player viewer) {
            super(EcoMenus.this.plugin, viewer);
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Sell Items");
        }

        @Override
        protected int rows() {
            return 6;
        }

        @Override
        public boolean editable(int slot) {
            return slot < 45;
        }

        @Override
        public boolean allowBottomClicks() {
            return true;
        }

        @Override
        public boolean allowShiftInto() {
            return true;
        }

        private double total() {
            double t = 0.0;
            Inventory inv = this.getInventory();

            for (int i = 0; i < 45; i++) {
                t += EcoMenus.this.eco.value(inv.getItem(i));
            }

            return t;
        }

        @Override
        protected void draw() {
            for (int i = 45; i < 54; i++) {
                this.set(i, Items.filler(EcoMenus.DARK));
            }

            this.refreshButton();
        }

        private void refreshButton() {
            this.set(
                49,
                Items.of(Material.LIME_CONCRETE)
                    .name("<green><b>Sell: <#3DDC84>" + EcoMenus.this.money(this.total()))
                    .lore("<gray>Put items above, click to sell.", "<gray>Closing sells too; items that", "<gray>can't be sold come back.")
                    .build(),
                t -> {
                    this.finish(true);
                    this.viewer.closeInventory();
                }
            );
        }

        @Override
        public void afterEdit(InventoryClickEvent e) {
            Tasks.later(1L, this::refreshButton);
        }

        @Override
        public void onClose() {
            this.finish(true);
        }

        private void finish(boolean pay) {
            if (!this.done) {
                this.done = true;
                Inventory inv = this.getInventory();
                double total = 0.0;
                int count = 0;
                List<ItemStack> back = new ArrayList<>();

                for (int i = 0; i < 45; i++) {
                    ItemStack it = inv.getItem(i);
                    if (it != null && !it.isEmpty()) {
                        double v = pay ? EcoMenus.this.eco.value(it) : 0.0;
                        if (v > 0.0) {
                            total += v;
                            count += it.getAmount();
                        } else {
                            back.add(it);
                        }

                        inv.setItem(i, null);
                    }
                }

                if (!back.isEmpty()) {
                    for (ItemStack rest : this.viewer.getInventory().addItem(back.toArray(new ItemStack[0])).values()) {
                        this.viewer.getWorld().dropItemNaturally(this.viewer.getLocation(), rest);
                    }
                }

                if (count > 0) {
                    EcoMenus.this.eco.give(this.viewer.getUniqueId(), total);
                    this.viewer
                        .sendMessage(
                            Text.mm(
                                "<gray>Sold <white>"
                                    + count
                                    + "</white> items for <#3DDC84>"
                                    + EcoMenus.this.money(total)
                                    + "</#3DDC84> <dark_gray>• <gray>balance "
                                    + EcoMenus.this.money(EcoMenus.this.eco.balance(this.viewer.getUniqueId()))
                            )
                        );
                    Snd.play(this.viewer, "entity.experience_orb.pickup", 1.0F, 1.2F);
                }
            }
        }
    }

    private final class ShopMain extends Menu {
        ShopMain(Player viewer) {
            super(EcoMenus.this.plugin, viewer);
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Shop");
        }

        @Override
        protected int rows() {
            return Math.max(3, Math.min(6, Ui.rowsFor(EcoMenus.this.eco.categories().size()) + 1));
        }

        @Override
        protected void draw() {
            List<Economy.Category> cats = new ArrayList<>(EcoMenus.this.eco.categories().values());
            int[] slots = Ui.centered(cats.size(), this.rows() - 1);

            for (int i = 0; i < slots.length; i++) {
                Economy.Category c = cats.get(i);
                this.set(
                    slots[i],
                    Items.of(c.icon).name(c.name).lore("<gray>" + c.items.size() + " items", "", "<green>▶ Click to browse").hideAll().build(),
                    t -> EcoMenus.this.new CategoryMenu(this.viewer, c, 0).open()
                );
            }

            int last = (this.rows() - 1) * 9;
            this.set(last + 4, EcoMenus.this.balanceItem(this.viewer));
            this.set(last + 8, Ui.close(), t -> this.viewer.closeInventory());
            this.fill(EcoMenus.FRAME);
        }
    }

    private final class WorthEditor extends Menu {
        private final int page;

        WorthEditor(Player viewer, int page) {
            super(EcoMenus.this.plugin, viewer);
            this.page = page;
        }

        @Override
        protected Component title() {
            return Text.mm("<dark_gray>Sell Prices");
        }

        @Override
        protected int rows() {
            return 6;
        }

        @Override
        protected void draw() {
            List<Entry<Material, Double>> list = new ArrayList<>(EcoMenus.this.eco.sellPrices().entrySet());
            list.sort((a, b) -> a.getKey().name().compareTo(b.getKey().name()));
            int[] slots = Ui.innerSlots(6);
            int from = this.page * slots.length;

            for (int i = 0; i < slots.length && from + i < list.size(); i++) {
                Entry<Material, Double> e = list.get(from + i);
                Material m = e.getKey();
                this.set(
                    slots[i],
                    Items.of(m.isItem() ? m : Material.PAPER)
                        .name("<white><b>" + EcoMenus.nice(m))
                        .lore(
                            "<gray>Sells for <#3DDC84>" + EcoMenus.this.money(e.getValue()) + "</#3DDC84> each",
                            "",
                            "<yellow>Click <gray>change",
                            "<red>Drop key (Q) <gray>can't be sold"
                        )
                        .build(),
                    t -> {
                        if (t != ClickType.DROP && t != ClickType.CONTROL_DROP) {
                            this.plugin
                                .dialogs()
                                .text(this.viewer, Text.mm("<gold><b>" + EcoMenus.nice(m)), "Price for one", String.valueOf(e.getValue()), 16, v -> {
                                    try {
                                        EcoMenus.this.eco.setSellPrice(m, Double.parseDouble(v.trim()));
                                    } catch (NumberFormatException var4x) {
                                        this.viewer.sendMessage(Text.mm("<red>Not a number."));
                                    }

                                    EcoMenus.this.new WorthEditor(this.viewer, this.page).open();
                                });
                        } else {
                            EcoMenus.this.eco.setSellPrice(m, null);
                            this.redraw();
                        }
                    }
                );
            }

            if (this.page > 0) {
                this.set(48, Ui.prev(this.page), t -> EcoMenus.this.new WorthEditor(this.viewer, this.page - 1).open());
            }

            this.set(
                49,
                Items.of(Material.LIME_CONCRETE).name("<green><b>+ Price Held Item").build(),
                t -> {
                    ItemStack h = this.viewer.getInventory().getItemInMainHand();
                    if (h.isEmpty()) {
                        this.viewer.sendMessage(Text.mm("<red>Hold the item first."));
                    } else {
                        Double cur = EcoMenus.this.eco.sellPrice(h.getType());
                        this.plugin
                            .dialogs()
                            .text(
                                this.viewer,
                                Text.mm("<gold><b>" + EcoMenus.nice(h.getType())),
                                "Price for one",
                                cur == null ? "1" : String.valueOf(cur),
                                16,
                                v -> {
                                    try {
                                        EcoMenus.this.eco.setSellPrice(h.getType(), Double.parseDouble(v.trim()));
                                    } catch (NumberFormatException var4x) {
                                        this.viewer.sendMessage(Text.mm("<red>Not a number."));
                                    }

                                    EcoMenus.this.new WorthEditor(this.viewer, this.page).open();
                                }
                            );
                    }
                }
            );
            if (from + slots.length < list.size()) {
                this.set(50, Ui.next(this.page), t -> EcoMenus.this.new WorthEditor(this.viewer, this.page + 1).open());
            }

            this.set(45, Ui.back(), t -> EcoMenus.this.admin(this.viewer));
            this.border(EcoMenus.FRAME);
        }
    }
}
