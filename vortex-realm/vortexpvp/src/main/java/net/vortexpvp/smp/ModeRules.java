package net.vortexpvp.smp;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.vortexpvp.VortexPvP;
import net.vortexpvp.board.BoardManager;
import net.vortexpvp.config.Cfg;
import net.vortexpvp.config.Opt;
import net.vortexpvp.fx.RankUpCelebration;
import net.vortexpvp.rank.Tier;
import net.vortexpvp.session.PlayerState;
import net.vortexpvp.session.Tp;
import net.vortexpvp.util.Items;
import net.vortexpvp.util.Snd;
import net.vortexpvp.util.Tasks;
import net.vortexpvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public final class ModeRules implements Listener, CommandExecutor, TabCompleter {
    private final VortexPvP plugin;
    private final ModeManager modes;
    private final NamespacedKey healthKey;
    private final NamespacedKey itemKey;

    public ModeRules(VortexPvP plugin, ModeManager modes) {
        this.plugin = plugin;
        this.modes = modes;
        this.healthKey = new NamespacedKey(plugin, "lifesteal_hearts");
        this.itemKey = new NamespacedKey(plugin, "smp_item");
    }

    private WorldMode mode(Player p) {
        return this.modes.mode(p.getWorld());
    }

    public void apply(Player p) {
        this.rankTag(p, null);
        if (!this.plugin.worlds().managed(p.getWorld())) {
            AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                max.removeModifier(this.healthKey);
            }

            for (PotionEffectType t : List.of(PotionEffectType.STRENGTH, PotionEffectType.WEAKNESS)) {
                PotionEffect pe = p.getPotionEffect(t);
                if (pe != null && pe.isInfinite()) {
                    p.removePotionEffect(t);
                }
            }

            ModeManager.Stats s = this.modes.stats(p.getUniqueId());
            switch (this.mode(p)) {
                case LIFESTEAL:
                    if (s.hearts <= 0 && s.eliminatedUntil <= System.currentTimeMillis()) {
                        s.hearts = this.modes.reviveHearts;
                        this.modes.dirty();
                    }

                    if (max != null) {
                        double extra = Math.max(1, s.hearts) * 2 - 20;
                        if (extra != 0.0) {
                            max.addModifier(new AttributeModifier(this.healthKey, extra, Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                        }

                        if (p.getHealth() > max.getValue()) {
                            p.setHealth(max.getValue());
                        }
                    }
                    break;
                case STRENGTH:
                    if (s.strength > 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, -1, s.strength - 1, true, false, true));
                    } else if (s.strength < 0) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, -1, -s.strength - 1, true, false, true));
                    }
                    break;
                case RANKED:
                    this.rankTag(p, Tier.forElo(s.points));
                    break;
                case HUB:
                    boolean builder = p.hasPermission("vortexpvp.admin") && p.getGameMode() == GameMode.CREATIVE;
                    if (!builder) {
                        p.setGameMode(GameMode.ADVENTURE);
                        p.setHealth(Math.max(1.0, p.getAttribute(Attribute.MAX_HEALTH) == null ? 20.0 : p.getAttribute(Attribute.MAX_HEALTH).getValue()));
                        p.setFoodLevel(20);
                    }

                    if (!this.hasSelector(p)) {
                        this.plugin.hub().giveItems(p);
                    }
            }
        }
    }

    private boolean hasSelector(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (this.modes.isSelector(it)) {
                return true;
            }
        }

        return false;
    }

    private void rankTag(Player p, Tier tier) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String name = "ppr" + p.getUniqueId().toString().replace("-", "").substring(0, 12);
        Team t = sb.getTeam(name);
        if (tier == null) {
            if (t != null) {
                t.unregister();
            }
        } else {
            if (t == null) {
                t = sb.registerNewTeam(name);
            }

            t.prefix(BoardManager.tierTag(tier).append(Text.mm(" <dark_gray>| ")));
            if (!t.hasEntry(p.getName())) {
                t.addEntry(p.getName());
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo().getWorld() != null && !e.getTo().getWorld().equals(e.getFrom().getWorld())) {
            this.modes.remember(e.getPlayer(), e.getFrom());
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onTeleportIntoLifesteal(PlayerTeleportEvent e) {
        World to = e.getTo().getWorld();
        if (to != null && !to.equals(e.getFrom().getWorld()) && this.modes.mode(to) == WorldMode.LIFESTEAL && !e.getPlayer().hasPermission("vortexpvp.admin")) {
            if (this.modes.stats(e.getPlayer().getUniqueId()).eliminatedUntil > System.currentTimeMillis()) {
                e.setCancelled(true);
                e.getPlayer().sendMessage(Text.mm("<red>You are eliminated from Lifesteal for now."));
            }
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        this.modes.syncGroup(p);
        this.apply(p);
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Tasks.later(
            2L,
            () -> {
                if (p.isOnline()) {
                    this.modes.syncGroup(p);
                    this.apply(p);
                    if (this.mode(p) == WorldMode.LIFESTEAL
                        && !p.hasPermission("vortexpvp.admin")
                        && this.modes.stats(p.getUniqueId()).eliminatedUntil > System.currentTimeMillis()) {
                        Tp.to(p, this.modes.hub());
                    }
                }
            }
        );
    }

    @EventHandler(
        priority = EventPriority.MONITOR
    )
    public void onQuit(PlayerQuitEvent e) {
        this.modes.remember(e.getPlayer(), e.getPlayer().getLocation());
        this.rankTag(e.getPlayer(), null);
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (!this.plugin.worlds().managed(p.getWorld())) {
            if (this.modes.mode(p.getWorld()) == WorldMode.LIFESTEAL
                && this.modes.stats(p.getUniqueId()).eliminatedUntil > System.currentTimeMillis()
                && !p.hasPermission("vortexpvp.admin")) {
                e.setRespawnLocation(this.modes.hub());
            }

            Tasks.later(1L, () -> {
                if (p.isOnline()) {
                    this.apply(p);
                }
            });
        }
    }

    @EventHandler(
        priority = EventPriority.MONITOR,
        ignoreCancelled = true
    )
    public void onMilk(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() == Material.MILK_BUCKET && this.mode(e.getPlayer()) == WorldMode.STRENGTH) {
            Tasks.later(1L, () -> this.apply(e.getPlayer()));
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (!this.plugin.worlds().managed(victim.getWorld())) {
            WorldMode m = this.mode(victim);
            if (m.smp() && m != WorldMode.SURVIVAL) {
                Player killer = victim.getKiller();
                if (killer != null && (killer.equals(victim) || this.modes.mode(killer.getWorld()) != m)) {
                    killer = null;
                }

                ModeManager.Stats vs = this.modes.stats(victim.getUniqueId());
                ModeManager.Stats ks = killer == null ? null : this.modes.stats(killer.getUniqueId());
                vs.deaths++;
                if (ks != null) {
                    ks.kills++;
                }

                switch (m) {
                    case LIFESTEAL:
                        this.lifesteal(victim, killer, vs, ks);
                        break;
                    case STRENGTH:
                        this.strength(victim, killer, vs, ks);
                        break;
                    case RANKED:
                        this.ranked(victim, killer, vs, ks);
                }

                this.modes.dirty();
                if (killer != null) {
                    this.apply(killer);
                }
            }
        }
    }

    private void lifesteal(Player victim, Player killer, ModeManager.Stats vs, ModeManager.Stats ks) {
        vs.hearts--;
        if (killer != null) {
            if (ks.hearts < this.modes.maxHearts) {
                ks.hearts++;
                killer.sendActionBar(Text.mm("<red>❤ +1 heart <gray>(" + ks.hearts + "/" + this.modes.maxHearts + ")"));
                Snd.play(killer, "entity.experience_orb.pickup", 1.0F, 1.2F);
            } else {
                victim.getWorld().dropItemNaturally(victim.getLocation(), this.heartItem(1));
                killer.sendActionBar(Text.mm("<red>❤ Max hearts - a heart item dropped"));
            }
        }

        if (vs.hearts <= 0) {
            vs.hearts = 0;
            vs.eliminatedUntil = System.currentTimeMillis() + this.modes.eliminationHours * 3600000L;
            Bukkit.getServer()
                .sendMessage(
                    Text.mm(
                        "<dark_red><b>☠ ELIMINATED</b> <red>"
                            + Text.esc(victim.getName())
                            + " <gray>lost their last heart"
                            + (killer != null ? " to <white>" + Text.esc(killer.getName()) : "")
                            + "<gray>!"
                    )
                );
        } else {
            victim.sendMessage(Text.mm("<red>❤ -1 heart <gray>(" + vs.hearts + " left)"));
        }
    }

    private void strength(Player victim, Player killer, ModeManager.Stats vs, ModeManager.Stats ks) {
        if (vs.strength > -this.modes.maxStrength) {
            vs.strength--;
        }

        victim.sendMessage(Text.mm("<light_purple>Strength <white>" + signed(vs.strength)));
        if (killer != null) {
            if (ks.strength < this.modes.maxStrength) {
                ks.strength++;
                killer.sendActionBar(Text.mm("<light_purple>⚔ Strength " + signed(ks.strength)));
                Snd.play(killer, "block.amethyst_block.chime", 1.0F, 1.4F);
            } else {
                victim.getWorld().dropItemNaturally(victim.getLocation(), this.shardItem(1));
            }
        }
    }

    private void ranked(Player victim, Player killer, ModeManager.Stats vs, ModeManager.Stats ks) {
        Tier before = Tier.forElo(vs.points);
        vs.points = Math.max(0, vs.points - this.modes.deathPoints);
        Tier after = Tier.forElo(vs.points);
        victim.sendMessage(
            Text.mm("<red>-" + this.modes.deathPoints + " rank points")
                .append(after != before ? Text.mm(" <gray>· now ").append(BoardManager.tierTag(after)) : Text.mm(""))
        );
        if (killer != null) {
            Tier kb = Tier.forElo(ks.points);
            int gain = this.modes.killPoints + Math.max(0, Math.min(30, (vs.points - ks.points) / 20));
            ks.points += gain;
            Tier ka = Tier.forElo(ks.points);
            killer.sendActionBar(Text.mm("<yellow>+" + gain + " rank points"));
            if (ka != kb) {
                Component where = Text.mm("<yellow>" + Text.esc(this.modes.display(killer.getWorld())));
                int pts = ks.points;
                Tasks.later(10L, () -> RankUpCelebration.play(this.plugin, killer, kb, ka, where, pts, null, false));
                if (ka.ordinal() >= Cfg.e(Opt.BROADCAST_FROM, Tier.class).ordinal()) {
                    RankUpCelebration.announce(killer, ka, where);
                }
            }
        }

        this.apply(victim);
    }

    private static String signed(int n) {
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    public ItemStack heartItem(int n) {
        ItemStack it = Items.of(Material.NETHER_STAR)
            .name("<red><b>❤ Heart")
            .lore("<gray>Right-click in Lifesteal", "<gray>to gain a heart.")
            .tag(this.itemKey, "heart")
            .glint(true)
            .build();
        it.setAmount(n);
        return it;
    }

    public ItemStack shardItem(int n) {
        ItemStack it = Items.of(Material.AMETHYST_SHARD)
            .name("<light_purple><b>Strength Shard")
            .lore("<gray>Right-click in Strength SMP", "<gray>to gain a strength level.")
            .tag(this.itemKey, "shard")
            .glint(true)
            .build();
        it.setAmount(n);
        return it;
    }

    private String itemType(ItemStack it) {
        return it != null && !it.isEmpty() && it.hasItemMeta()
            ? it.getItemMeta().getPersistentDataContainer().get(this.itemKey, PersistentDataType.STRING)
            : null;
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player p = e.getPlayer();
            ItemStack hand = e.getItem();
            if (this.modes.isSelector(hand) && !this.plugin.hub().inHub(p)) {
                e.setCancelled(true);
                this.modes.selector(p);
            } else {
                String type = this.itemType(hand);
                if (type != null) {
                    e.setCancelled(true);
                    ModeManager.Stats s = this.modes.stats(p.getUniqueId());
                    if ("heart".equals(type)) {
                        if (this.mode(p) != WorldMode.LIFESTEAL) {
                            p.sendActionBar(Text.mm("<red>Hearts only work in Lifesteal."));
                            return;
                        }

                        if (s.hearts >= this.modes.maxHearts) {
                            p.sendActionBar(Text.mm("<red>You already have the maximum hearts."));
                            return;
                        }

                        s.hearts++;
                    } else {
                        if (!"shard".equals(type)) {
                            return;
                        }

                        if (this.mode(p) != WorldMode.STRENGTH) {
                            p.sendActionBar(Text.mm("<red>Shards only work in Strength SMP."));
                            return;
                        }

                        if (s.strength >= this.modes.maxStrength) {
                            p.sendActionBar(Text.mm("<red>You are already at maximum strength."));
                            return;
                        }

                        s.strength++;
                    }

                    hand.setAmount(hand.getAmount() - 1);
                    this.modes.dirty();
                    this.apply(p);
                    Snd.play(p, "entity.player.levelup", 0.8F, 1.5F);
                    p.getWorld()
                        .spawnParticle("heart".equals(type) ? Particle.HEART : Particle.WITCH, p.getLocation().add(0.0, 1.0, 0.0), 12, 0.4, 0.5, 0.4, 0.02);
                    p.sendActionBar(
                        Text.mm("heart".equals(type) ? "<red>❤ +1 heart <gray>(" + s.hearts + ")" : "<light_purple>⚔ Strength " + signed(s.strength))
                    );
                }
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onHubInventory(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.hub(p) && !builder(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH,
        ignoreCancelled = true
    )
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (this.mode(e.getPlayer()) == WorldMode.HUB) {
            e.setCancelled(true);
        }
    }

    private boolean hub(Player p) {
        return !this.plugin.worlds().managed(p.getWorld()) && this.mode(p) == WorldMode.HUB;
    }

    private static boolean builder(Player p) {
        return p.hasPermission("vortexpvp.admin") && p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onHubDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && this.hub(p)) {
            if (this.plugin.hub().allowsDamage(e)) {
                return;
            }

            e.setCancelled(true);
            if (e.getCause() == DamageCause.VOID) {
                Tasks.sync(() -> Tp.to(p, this.modes.spawn(p.getWorld())));
            }
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onHubFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && this.hub(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onHubBreak(BlockBreakEvent e) {
        if (this.hub(e.getPlayer()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onHubPlace(BlockPlaceEvent e) {
        if (this.hub(e.getPlayer()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(
        priority = EventPriority.HIGH
    )
    public void onHubDrop(PlayerDropItemEvent e) {
        if (this.hub(e.getPlayer()) && !builder(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!name.equals("smpstats")
            || args.length < 2
            || !sender.hasPermission("vortexpvp.admin")
            || !args[0].equalsIgnoreCase("set") && !args[0].equalsIgnoreCase("revive")) {
            if (sender instanceof Player p) {
                switch (name) {
                    case "worlds":
                        this.modes.selector(p);
                        break;
                    case "practice":
                        World lobby = this.plugin.lobby().lobby().getWorld();
                        if (this.plugin.worlds().managed(p.getWorld()) && this.plugin.sessions().state(p) != PlayerState.OUTSIDE) {
                            p.sendMessage(Text.mm("<gray>You're already in Practice."));
                        } else if (lobby != null) {
                            this.modes.travel(p, lobby);
                        }
                        break;
                    case "hub":
                        World hub = this.modes.hubWorld();
                        if (hub == null) {
                            p.sendMessage(Text.mm("<red>There is no hub yet."));
                        } else {
                            this.modes.travel(p, hub);
                        }
                        break;
                    case "smp":
                    case "lifesteal":
                    case "strength":
                    case "eco":
                    case "rankedsmp":
                        WorldMode want = switch (name) {
                            case "lifesteal" -> WorldMode.LIFESTEAL;
                            case "strength" -> WorldMode.STRENGTH;
                            case "eco" -> WorldMode.ECO;
                            case "rankedsmp" -> WorldMode.RANKED;
                            default -> WorldMode.SURVIVAL;
                        };
                        World target = null;

                        for (World w : Bukkit.getWorlds()) {
                            String n = w.getName();
                            if (this.modes.mode(w) == want && !n.endsWith("_nether") && !n.endsWith("_the_end")) {
                                target = w;
                                break;
                            }
                        }

                        if (target == null) {
                            p.sendMessage(Text.mm("<red>" + want.display + " isn't open yet."));
                        } else {
                            this.modes.travel(p, target);
                        }
                        break;
                    case "withdraw":
                        this.withdraw(p, args.length > 0 ? parse(args[0], 1) : 1);
                        break;
                    case "smpstats":
                        Player t = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : p;
                        if (t == null) {
                            p.sendMessage(Text.mm("<red>That player is not online."));
                            return true;
                        }

                        ModeManager.Stats s = this.modes.stats(t.getUniqueId());
                        p.sendMessage(Text.mm("<gold><b>" + Text.esc(t.getName()) + "</b> <dark_gray>» <gray>SMP stats"));
                        p.sendMessage(
                            Text.mm(
                                " <red>❤ Hearts <white>"
                                    + s.hearts
                                    + "/"
                                    + this.modes.maxHearts
                                    + (s.eliminatedUntil > System.currentTimeMillis() ? " <dark_red>(eliminated)" : "")
                            )
                        );
                        p.sendMessage(Text.mm(" <light_purple>⚔ Strength <white>" + signed(s.strength)));
                        p.sendMessage(Text.mm(" <yellow>★ Ranked SMP <white>" + s.points + " pts ").append(BoardManager.tierTag(Tier.forElo(s.points))));
                        p.sendMessage(Text.mm(" <gray>Kills <green>" + s.kills + " <gray>Deaths <red>" + s.deaths));
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
            return this.admin(sender, args);
        }
    }

    private static int parse(String s, int def) {
        try {
            return Math.max(1, Integer.parseInt(s));
        } catch (NumberFormatException var3) {
            return def;
        }
    }

    private void withdraw(Player p, int n) {
        ModeManager.Stats s = this.modes.stats(p.getUniqueId());
        switch (this.mode(p)) {
            case LIFESTEAL:
                if (s.hearts - n < 1) {
                    p.sendMessage(Text.mm("<red>You must keep at least 1 heart."));
                    return;
                }

                s.hearts -= n;
                p.getInventory().addItem(new ItemStack[]{this.heartItem(n)}).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                p.sendMessage(Text.mm("<red>Withdrew " + n + " heart(s). <gray>(" + s.hearts + " left)"));
                break;
            case STRENGTH:
                if (s.strength - n < -this.modes.maxStrength) {
                    p.sendMessage(Text.mm("<red>Not enough strength to withdraw."));
                    return;
                }

                s.strength -= n;
                p.getInventory().addItem(new ItemStack[]{this.shardItem(n)}).values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                p.sendMessage(Text.mm("<light_purple>Withdrew " + n + " shard(s). <gray>(strength " + signed(s.strength) + ")"));
                break;
            default:
                p.sendMessage(Text.mm("<red>/withdraw works in Lifesteal and Strength SMP worlds."));
                return;
        }

        this.modes.dirty();
        this.apply(p);
    }

    private boolean admin(CommandSender sender, String[] args) {
        Player t = Bukkit.getPlayerExact(args[1]);
        OfflinePlayer off = (OfflinePlayer)(t != null ? t : Bukkit.getOfflinePlayerIfCached(args[1]));
        if (off == null) {
            sender.sendMessage(Text.mm("<red>Unknown player."));
            return true;
        } else {
            ModeManager.Stats s = this.modes.stats(off.getUniqueId());
            if (args[0].equalsIgnoreCase("revive")) {
                s.eliminatedUntil = 0L;
                s.hearts = this.modes.reviveHearts;
                sender.sendMessage(Text.mm("<green>" + Text.esc(args[1]) + " was revived with " + s.hearts + " hearts."));
            } else {
                if (args.length < 4) {
                    sender.sendMessage(Text.mm("<red>Usage: /smpstats set \\<player> \\<hearts|strength|points> \\<value>"));
                    return true;
                }

                int v;
                try {
                    v = Integer.parseInt(args[3]);
                } catch (NumberFormatException var9) {
                    sender.sendMessage(Text.mm("<red>Not a number."));
                    return true;
                }

                String e = args[2].toLowerCase(Locale.ROOT);
                switch (e) {
                    case "hearts":
                        s.hearts = Math.max(1, Math.min(this.modes.maxHearts, v));
                        break;
                    case "strength":
                        s.strength = Math.max(-this.modes.maxStrength, Math.min(this.modes.maxStrength, v));
                        break;
                    case "points":
                        s.points = Math.max(0, v);
                        break;
                    default:
                        sender.sendMessage(Text.mm("<red>hearts, strength or points"));
                        return true;
                }

                sender.sendMessage(Text.mm("<green>Updated."));
            }

            this.modes.dirty();
            if (t != null) {
                this.apply(t);
            }

            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("smpstats")) {
            if (args.length == 1) {
                if (sender.hasPermission("vortexpvp.admin")) {
                    out.add("set");
                    out.add("revive");
                }

                Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
            } else if (args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(o -> out.add(o.getName()));
            } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                out.addAll(List.of("hearts", "strength", "points"));
            }
        }

        String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }

    public Location hubSpawn() {
        return this.modes.hub();
    }
}
