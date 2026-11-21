package net.practicepvp.realm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.practicepvp.util.Snd;
import net.practicepvp.util.Tasks;
import net.practicepvp.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * The eight one-of-a-kind characters. Each can be owned by one player only. Shift + F (or /ability) uses the
 * active; passives and weaknesses run on their own. Powers only work in the SMP while the season runs.
 */
public final class Characters implements Listener {
    public enum Role {
        JESTER("The Jester", "#FFD23F:#FF3FA4", Material.BELL, 45,
            "A laughing shard-bearer who bends luck itself.",
            "Every 5 minutes fate rolls: a random blessing (sometimes a curse)",
            "Swap places with the nearest creature or player within 20 blocks",
            "Some of fate's rolls are curses"),
        WARDEN_BORN("The Warden-Born", "#2EC4B6:#0B3954", Material.ECHO_SHARD, 30,
            "Born in the deep dark, the sculk still hums inside you.",
            "Night vision in the dark, immune to Darkness",
            "Sonic Boom: a blast that hits everything in a 15-block line",
            "Slowed while standing in sunlight"),
        SKY_PIRATE("The Sky Pirate", "#7FDBFF:#0074D9", Material.SPYGLASS, 12,
            "Captain of a ship that sank into the clouds.",
            "No fall damage",
            "Grapple: fling yourself to the block you look at (30 blocks)",
            "Takes 25% more melee damage while standing on the ground"),
        ALCHEMIST("The Alchemist", "#B8F35B:#2E8B57", Material.BREWING_STAND, 120,
            "Brewer of forbidden mixtures, fluent in bubbling.",
            "Potions you drink last twice as long",
            "Brew a random powerful potion",
            "Golden apples refuse you"),
        HOLLOW_KING("The Hollow King", "#D9D9D9:#5E548E", Material.SKELETON_SKULL, 90,
            "A crown with no kingdom, followed by the restless dead.",
            "Undead mobs never attack you",
            "Raise 3 skeleton guards for 60 seconds",
            "Healing potions burn you instead"),
        STORMCALLER("The Stormcaller", "#FDE74C:#5BC0EB", Material.LIGHTNING_ROD, 25,
            "The sky answers when you call.",
            "Immune to lightning, Conduit Power in the rain",
            "Call lightning where you look (40 blocks)",
            "Water slows you down"),
        TRICKSTER("The Trickster", "#9B5DE5:#00BBF9", Material.ENDER_EYE, 60,
            "Nobody has seen your real face. Not even you.",
            "Invisible while sneaking",
            "Fake your death: a fake death message, then vanish with speed",
            "Glow for 5 seconds every time a player hits you"),
        IRON_HEART("The Iron Heart", "#C0C0C0:#E63946", Material.SHIELD, 60,
            "A soldier whose heart was replaced with a shard of iron.",
            "+4 hearts",
            "Shield Wall: Resistance III for 6 seconds and push enemies back",
            "Too heavy to fly with elytra");

        final String display;
        final String gradient;
        final Material icon;
        final int cooldown;
        String about;
        String passive;
        String active;
        String weakness;

        Role(String display, String gradient, Material icon, int cooldown, String about, String passive, String active, String weakness) {
            this.display = display;
            this.gradient = gradient;
            this.icon = icon;
            this.cooldown = cooldown;
            this.about = about;
            this.passive = passive;
            this.active = active;
            this.weakness = weakness;
        }

        public String id() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        public String mm() {
            return "<gradient:" + this.gradient + "><b>" + this.display + "</b></gradient>";
        }

        static Role of(String id) {
            if (id == null) {
                return null;
            }

            try {
                return valueOf(id.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final Realm realm;
    private final Random rnd = new Random();
    private final Map<UUID, Long> ready = new HashMap<>();
    private final Map<UUID, Long> confirm = new HashMap<>();
    private final Map<UUID, Integer> fate = new HashMap<>();
    private final NamespacedKey heartKey;
    private final NamespacedKey guardKey;

    Characters(Realm realm) {
        this.realm = realm;
        this.heartKey = new NamespacedKey(realm.plugin, "iron_heart");
        this.guardKey = new NamespacedKey(realm.plugin, "hollow_guard");
        this.reloadTexts();
    }

    /** Owners may rewrite the descriptions in realm-story.yml -> roles.<id>. */
    void reloadTexts() {
        for (Role r : Role.values()) {
            String b = "roles." + r.id() + ".";
            r.about = this.realm.story.getString(b + "about", r.about);
            r.passive = this.realm.story.getString(b + "passive", r.passive);
            r.active = this.realm.story.getString(b + "active", r.active);
            r.weakness = this.realm.story.getString(b + "weakness", r.weakness);
        }
    }

    String name(String id) {
        Role r = Role.of(id);
        return r == null ? "?" : r.display;
    }

    Role of(Player p) {
        Realm.PlayerState st = this.realm.stateIfAny(p.getUniqueId());
        return st == null ? null : Role.of(st.character);
    }

    /** A power only works when the season runs, the player is in the SMP and has that character. */
    private boolean is(Player p, Role r) {
        return this.realm.on(p) && this.of(p) == r;
    }

    UUID owner(Role r) {
        for (Map.Entry<UUID, Realm.PlayerState> e : this.realm.players.entrySet()) {
            if (r.id().equals(e.getValue().character)) {
                return e.getKey();
            }
        }

        return null;
    }

    // ------------------------------------------------------------------ the picker

    private static final class Holder implements InventoryHolder {
        Inventory inv;

        @Override
        public Inventory getInventory() {
            return this.inv;
        }
    }

    private static final int[] SLOTS = {10, 11, 12, 13, 14, 15, 16, 22};

    void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 36, Text.mm("<dark_purple><b>Choose your character</b> <dark_gray>(one each)"));
        Role mine = this.of(p);
        Role[] all = Role.values();
        for (int i = 0; i < all.length; i++) {
            Role r = all[i];
            UUID o = this.owner(r);
            ItemStack it = new ItemStack(r.icon);
            ItemMeta m = it.getItemMeta();
            m.displayName(Text.mm("<!i>" + r.mm()));
            List<Component> lore = new ArrayList<>();
            lore.add(Text.mm("<!i><gray><i>" + Text.esc(r.about)));
            lore.add(Component.empty());
            lore.add(Text.mm("<!i><green>Passive: <white>" + Text.esc(r.passive)));
            lore.add(Text.mm("<!i><aqua>Active <dark_gray>(Shift+F, " + r.cooldown + "s)</dark_gray>: <white>" + Text.esc(r.active)));
            lore.add(Text.mm("<!i><red>Weakness: <white>" + Text.esc(r.weakness)));
            lore.add(Component.empty());
            if (r == mine) {
                lore.add(Text.mm("<!i><gold><b>✔ This is you"));
            } else if (o != null) {
                String n = Bukkit.getOfflinePlayer(o).getName();
                lore.add(Text.mm("<!i><dark_red>✖ Taken by <white>" + Text.esc(n == null ? "someone" : n)));
            } else if (mine != null) {
                lore.add(Text.mm("<!i><gray>You already chose your character."));
            } else if (!this.realm.sees(p) || !this.realm.running()) {
                lore.add(Text.mm("<!i><light_purple>Opens when the season starts"));
            } else {
                lore.add(Text.mm("<!i><yellow>Click to choose. <gray>This can't be undone."));
            }

            m.lore(lore);
            m.addItemFlags(ItemFlag.values());
            if (r == mine) {
                m.setEnchantmentGlintOverride(true);
            }

            it.setItemMeta(m);
            h.inv.setItem(SLOTS[i], it);
        }

        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(Text.mm("<!i><gold><b>How it works"));
        im.lore(List.of(Text.mm("<!i><gray>Each character exists only once."), Text.mm("<!i><gray>Once someone takes it, it's gone."),
            Text.mm("<!i><gray>Your character has its own story: <white>/realm journal")));
        info.setItemMeta(im);
        h.inv.setItem(31, info);
        p.openInventory(h.inv);
        Snd.play(p, "block.amethyst_block.chime", 1.0F, 1.0F);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder) || !(e.getWhoClicked() instanceof Player p)) {
            return;
        }

        e.setCancelled(true);
        int idx = -1;
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == e.getRawSlot()) {
                idx = i;
            }
        }

        if (idx < 0 || idx >= Role.values().length) {
            return;
        }

        Role r = Role.values()[idx];
        if (!this.realm.running() || !this.realm.sees(p)) {
            p.sendMessage(Text.mm("<light_purple>The Unstable Realm hasn't started yet. Characters open on day one."));
            return;
        }

        if (this.of(p) != null) {
            p.sendMessage(Text.mm("<red>You already are " + this.of(p).mm() + "<red>."));
            return;
        }

        if (this.owner(r) != null) {
            p.sendMessage(Text.mm("<red>Someone already carries that shard."));
            Snd.error(p);
            return;
        }

        Long c = this.confirm.get(p.getUniqueId());
        if (c == null || System.currentTimeMillis() - c > 5000 || !r.id().equals(this.realm.state(p).flags.get("_confirm"))) {
            this.confirm.put(p.getUniqueId(), System.currentTimeMillis());
            this.realm.state(p).flags.put("_confirm", r.id());
            p.sendMessage(Text.mm("<yellow>Click " + r.mm() + " <yellow>again to become them. <gray>There is no going back."));
            Snd.play(p, Snd.HAT, 1.0F, 1.4F);
            return;
        }

        this.realm.state(p).flags.remove("_confirm");
        p.closeInventory();
        this.pick(p, r);
    }

    void pick(Player p, Role r) {
        Realm.PlayerState st = this.realm.state(p);
        st.character = r.id();
        st.step = 0;
        st.progress = 0;
        this.realm.save();
        this.realm.give(p, this.signature(r));
        this.realm.broadcast(r.mm(), "<gray>has found a bearer: <white>" + Text.esc(p.getName()),
            "<dark_purple>✦ <white>" + Text.esc(p.getName()) + " <gray>is now " + r.mm() + "<gray>. Only one may carry it.", "block.end_portal.spawn");
        p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 120, 0.5, 1, 0.5, 0.2);
        this.realm.addCounter("bearers", 1);
        Tasks.later(80L, () -> this.realm.storyEngine.beginCharacter(p));
    }

    /** The item every character starts with. */
    ItemStack signature(Role r) {
        ItemStack it = switch (r) {
            case SKY_PIRATE -> new ItemStack(Material.FIREWORK_ROCKET, 16);
            case ALCHEMIST -> new ItemStack(Material.NETHER_WART, 8);
            case TRICKSTER -> new ItemStack(Material.ENDER_PEARL, 4);
            case STORMCALLER -> new ItemStack(Material.LIGHTNING_ROD, 4);
            default -> new ItemStack(r.icon);
        };
        ItemMeta m = it.getItemMeta();
        String name = switch (r) {
            case JESTER -> "Jester's Bell";
            case WARDEN_BORN -> "Echo of the Deep";
            case SKY_PIRATE -> "Captain's Rockets";
            case ALCHEMIST -> "Wart of Many Brews";
            case HOLLOW_KING -> "Bone Crown";
            case STORMCALLER -> "Storm Rods";
            case TRICKSTER -> "Pearls of Escape";
            case IRON_HEART -> "Iron Heart's Shield";
        };
        m.displayName(Text.mm("<!i><gradient:" + r.gradient + "><b>" + name));
        m.lore(List.of(Text.mm("<!i><gray>Belongs to " + r.display), Text.mm("<!i><dark_gray>Shift + F to use your power")));
        it.setItemMeta(m);
        return it;
    }

    void who(CommandSender to) {
        to.sendMessage(Text.mm("<dark_purple><b>The bearers</b> <gray>(one per character)"));
        for (Role r : Role.values()) {
            UUID o = this.owner(r);
            String n = o == null ? null : Bukkit.getOfflinePlayer(o).getName();
            to.sendMessage(Text.mm("<dark_gray>• " + r.mm() + " <dark_gray>» " + (n == null ? "<green>free" : "<white>" + Text.esc(n))));
        }
    }

    // ------------------------------------------------------------------ actives

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (p.isSneaking() && this.realm.on(p) && this.of(p) != null) {
            e.setCancelled(true);
            this.use(p);
        }
    }

    void use(Player p) {
        Role r = this.of(p);
        if (r == null || !this.realm.on(p)) {
            p.sendMessage(Text.mm(r == null ? "<gray>You have no character. <white>/character" : "<gray>Your power only works in the SMP while the season runs."));
            return;
        }

        long now = System.currentTimeMillis();
        long at = this.ready.getOrDefault(p.getUniqueId(), 0L);
        if (now < at) {
            p.sendActionBar(Text.mm("<red>" + r.display + " power ready in <white>" + ((at - now) / 1000 + 1) + "s"));
            Snd.error(p);
            return;
        }

        boolean used = switch (r) {
            case JESTER -> this.swap(p);
            case WARDEN_BORN -> this.sonic(p);
            case SKY_PIRATE -> this.grapple(p);
            case ALCHEMIST -> this.brew(p);
            case HOLLOW_KING -> this.guards(p);
            case STORMCALLER -> this.storm(p);
            case TRICKSTER -> this.fakeDeath(p);
            case IRON_HEART -> this.wall(p);
        };
        if (used) {
            this.ready.put(p.getUniqueId(), now + r.cooldown * 1000L);
            this.realm.storyEngine.event(p, "ability");
        }
    }

    private boolean swap(Player p) {
        LivingEntity best = null;
        double bd = 400;
        for (Entity e : p.getNearbyEntities(20, 10, 20)) {
            if (e instanceof LivingEntity le && !(e instanceof org.bukkit.entity.ArmorStand) && !e.getPersistentDataContainer().has(this.realm.npcKey)) {
                double d = e.getLocation().distanceSquared(p.getLocation()) - (e instanceof Player ? 200 : 0);
                if (d < bd) {
                    bd = d;
                    best = le;
                }
            }
        }

        if (best == null) {
            p.sendActionBar(Text.mm("<yellow>Nobody to swap with..."));
            return false;
        }

        Location a = p.getLocation();
        Location b = best.getLocation();
        a.getWorld().spawnParticle(Particle.WITCH, a.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0);
        b.getWorld().spawnParticle(Particle.WITCH, b.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0);
        p.teleport(b.setDirection(a.getDirection()));
        best.teleport(a);
        Snd.play(p, "entity.illusioner.mirror_move", 1.0F, 1.2F);
        if (best instanceof Player o) {
            o.sendMessage(Text.mm("<gradient:#FFD23F:#FF3FA4><b>The Jester</b></gradient> <gray>swapped places with you. <i>Surprise!"));
        }

        return true;
    }

    private boolean sonic(Player p) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Snd.play(p, "entity.warden.sonic_boom", 1.0F, 1.0F);
        java.util.Set<Entity> hit = new java.util.HashSet<>();
        for (double d = 1; d <= 15; d += 1.0) {
            Location at = eye.clone().add(dir.clone().multiply(d));
            at.getWorld().spawnParticle(Particle.SONIC_BOOM, at, 1, 0, 0, 0, 0);
            for (Entity e : at.getWorld().getNearbyEntities(at, 1.3, 1.3, 1.3)) {
                if (e != p && e instanceof LivingEntity le && hit.add(e) && !e.getPersistentDataContainer().has(this.realm.npcKey)) {
                    le.damage(7.0, p);
                    le.setVelocity(dir.clone().multiply(1.4).setY(0.4));
                }
            }

            if (at.getBlock().getType().isOccluding()) {
                break;
            }
        }

        return true;
    }

    private boolean grapple(Player p) {
        RayTraceResult r = p.rayTraceBlocks(30);
        if (r == null || r.getHitBlock() == null) {
            p.sendActionBar(Text.mm("<aqua>Nothing to hook onto."));
            return false;
        }

        Vector to = r.getHitPosition().subtract(p.getLocation().toVector());
        double len = to.length();
        Vector v = to.normalize().multiply(Math.min(3.2, 0.9 + len * 0.09)).setY(Math.max(0.5, to.getY() / Math.max(1, len) * 2.0 + 0.4));
        p.setVelocity(v);
        Location from = p.getEyeLocation();
        for (double d = 0; d < len; d += 1.0) {
            p.getWorld().spawnParticle(Particle.CRIT, from.clone().add(to.clone().normalize().multiply(d)), 1, 0, 0, 0, 0);
        }

        Snd.play(p, "entity.fishing_bobber.throw", 1.0F, 0.6F);
        return true;
    }

    private static final PotionType[] BREWS = {PotionType.STRONG_STRENGTH, PotionType.STRONG_SWIFTNESS, PotionType.STRONG_REGENERATION,
        PotionType.LONG_FIRE_RESISTANCE, PotionType.LONG_INVISIBILITY, PotionType.STRONG_TURTLE_MASTER, PotionType.STRONG_HEALING,
        PotionType.STRONG_LEAPING, PotionType.LONG_NIGHT_VISION, PotionType.LONG_SLOW_FALLING};

    private boolean brew(Player p) {
        ItemStack it = new ItemStack(this.rnd.nextInt(4) == 0 ? Material.SPLASH_POTION : Material.POTION);
        PotionMeta m = (PotionMeta)it.getItemMeta();
        PotionType t = BREWS[this.rnd.nextInt(BREWS.length)];
        m.setBasePotionType(t);
        m.displayName(Text.mm("<!i><gradient:#B8F35B:#2E8B57>Alchemist's " + Text.esc(t.name().toLowerCase(Locale.ROOT).replace("strong_", "").replace("long_", "").replace('_', ' '))));
        it.setItemMeta(m);
        this.realm.give(p, it);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1.2, 0), 20, 0.4, 0.4, 0.4, 0);
        Snd.play(p, "block.brewing_stand.brew", 1.0F, 1.0F);
        return true;
    }

    private boolean guards(Player p) {
        for (int i = 0; i < 3; i++) {
            double a = Math.PI * 2 * i / 3;
            Location at = p.getLocation().add(Math.cos(a) * 2, 0, Math.sin(a) * 2);
            Skeleton s = p.getWorld().spawn(at, Skeleton.class, sk -> {
                sk.customName(Text.mm("<gray>Guard of " + Text.esc(p.getName())));
                sk.setCustomNameVisible(true);
                sk.setPersistent(false);
                sk.setShouldBurnInDay(false);
                sk.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                sk.getEquipment().setHelmetDropChance(0);
                sk.getEquipment().setItemInMainHandDropChance(0);
                sk.getPersistentDataContainer().set(this.guardKey, PersistentDataType.STRING, p.getUniqueId().toString());
            });
            s.getWorld().spawnParticle(Particle.SOUL, at.clone().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
            Tasks.later(1200L, () -> {
                if (s.isValid()) {
                    s.getWorld().spawnParticle(Particle.SOUL, s.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
                    s.remove();
                }
            });
        }

        Snd.play(p, "entity.skeleton_horse.ambient", 1.0F, 0.6F);
        return true;
    }

    private boolean storm(Player p) {
        RayTraceResult r = p.rayTraceBlocks(40);
        Location at = r != null && r.getHitBlock() != null ? r.getHitBlock().getLocation().add(0.5, 1, 0.5) : p.getEyeLocation().add(p.getLocation().getDirection().multiply(20));
        p.getWorld().strikeLightning(at);
        return true;
    }

    private boolean fakeDeath(Player p) {
        Component msg = Text.mm("<white>" + Text.esc(p.getName()) + " was slain");
        for (Player o : p.getWorld().getPlayers()) {
            if (this.realm.sees(o)) {
                o.sendMessage(msg);
            }
        }

        p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.02);
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 120, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1, false, false));
        Snd.play(p, "entity.player.death", 1.0F, 1.0F);
        return true;
    }

    private boolean wall(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 2, false, true));
        for (Entity e : p.getNearbyEntities(5, 3, 5)) {
            if (e instanceof LivingEntity && !e.getPersistentDataContainer().has(this.realm.npcKey)) {
                Vector push = e.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.5).setY(0.5);
                e.setVelocity(push);
            }
        }

        p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, p.getLocation().add(0, 1, 0), 60, 1.5, 0.8, 1.5, 0.1);
        Snd.play(p, "item.shield.block", 1.0F, 0.6F);
        return true;
    }

    // ------------------------------------------------------------------ passives and weaknesses (every second)

    private static final PotionEffectType[] GOOD = {PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.REGENERATION,
        PotionEffectType.JUMP_BOOST, PotionEffectType.RESISTANCE, PotionEffectType.HASTE, PotionEffectType.LUCK};
    private static final PotionEffectType[] BAD = {PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.HUNGER, PotionEffectType.NAUSEA};

    void second() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Role r = this.realm.on(p) ? this.of(p) : null;
            this.heart(p, r == Role.IRON_HEART);
            if (r == null) {
                continue;
            }

            World w = p.getWorld();
            Block at = p.getLocation().getBlock();
            boolean day = w.getEnvironment() == World.Environment.NORMAL && w.isDayTime() && !w.hasStorm();
            switch (r) {
                case JESTER -> {
                    int t = this.fate.merge(p.getUniqueId(), 1, Integer::sum);
                    if (t >= 300) {
                        this.fate.put(p.getUniqueId(), 0);
                        boolean curse = this.rnd.nextInt(4) == 0;
                        PotionEffectType e = curse ? BAD[this.rnd.nextInt(BAD.length)] : GOOD[this.rnd.nextInt(GOOD.length)];
                        p.addPotionEffect(new PotionEffect(e, 1200, curse ? 0 : this.rnd.nextInt(2), false, true));
                        p.sendActionBar(Text.mm(curse ? "<red>🎲 Fate frowns: <white>" + pretty(e) : "<gold>🎲 Fate smiles: <white>" + pretty(e)));
                        Snd.play(p, curse ? Snd.BASS : Snd.PLING, 1.0F, curse ? 0.6F : 1.6F);
                    }
                }
                case WARDEN_BORN -> {
                    if (at.getLightLevel() < 7) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false));
                    }

                    if (day && at.getLightFromSky() >= 15) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, false));
                    }
                }
                case STORMCALLER -> {
                    if (w.hasStorm() && at.getLightFromSky() >= 15) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 60, 0, true, false));
                    }

                    if (p.isInWater()) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, true, false));
                    }
                }
                case TRICKSTER -> {
                    if (p.isSneaking()) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, true, false));
                    }
                }
                default -> {
                }
            }

            long left = this.ready.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();
            if (left > 0 && left < r.cooldown * 1000L - 1500) {
                p.sendActionBar(Text.mm("<gray>" + r.display + " <dark_gray>• <white>" + (left / 1000 + 1) + "s"));
            } else if (left > -1000 && left <= 0) {
                p.sendActionBar(Text.mm("<green>✦ " + r.display + " power ready <dark_gray>(Shift + F)"));
            }
        }
    }

    private static String pretty(PotionEffectType t) {
        String k = t.getKey().getKey().replace('_', ' ');
        return Character.toUpperCase(k.charAt(0)) + k.substring(1);
    }

    private void heart(Player p, boolean on) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a == null) {
            return;
        }

        AttributeModifier have = null;
        for (AttributeModifier m : a.getModifiers()) {
            if (this.heartKey.equals(m.getKey())) {
                have = m;
            }
        }

        if (on && have == null) {
            a.addModifier(new AttributeModifier(this.heartKey, 8.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        } else if (!on && have != null) {
            a.removeModifier(have);
            if (p.getHealth() > a.getValue()) {
                p.setHealth(a.getValue());
            }
        }
    }

    // ------------------------------------------------------------------ passive listeners

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }

        Role r = this.realm.on(p) ? this.of(p) : null;
        if (r == Role.SKY_PIRATE && e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
        } else if (r == Role.STORMCALLER && (e.getCause() == EntityDamageEvent.DamageCause.LIGHTNING
            || e.getCause() == EntityDamageEvent.DamageCause.FIRE || e.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK) && p.getWorld().hasStorm()) {
            e.setCancelled(true);
            p.setFireTicks(0);
        } else if (r == Role.STORMCALLER && e.getCause() == EntityDamageEvent.DamageCause.LIGHTNING) {
            e.setCancelled(true);
            p.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p && this.realm.on(p)) {
            Role r = this.of(p);
            Entity d = e.getDamager();
            Player attacker = d instanceof Player a ? a : (d instanceof Projectile pr && pr.getShooter() instanceof Player a ? a : null);
            if (r == Role.SKY_PIRATE && p.isOnGround() && !(d instanceof Projectile)) {
                e.setDamage(e.getDamage() * 1.25);
            }

            if (r == Role.TRICKSTER && attacker != null) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
            }

            // the Hollow King's guards defend their king
            if (r == Role.HOLLOW_KING && d instanceof LivingEntity enemy) {
                for (Entity g : p.getNearbyEntities(16, 8, 16)) {
                    if (g instanceof Skeleton s && p.getUniqueId().toString().equals(s.getPersistentDataContainer().get(this.guardKey, PersistentDataType.STRING))) {
                        s.setTarget(attacker != null ? attacker : enemy);
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getTarget() instanceof Player p)) {
            return;
        }

        String guardOf = e.getEntity().getPersistentDataContainer().get(this.guardKey, PersistentDataType.STRING);
        if (guardOf != null && guardOf.equals(p.getUniqueId().toString())) {
            e.setCancelled(true);
            return;
        }

        if (this.is(p, Role.HOLLOW_KING) && Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(e.getEntity().getType())
            && !e.getEntity().getPersistentDataContainer().has(this.realm.riftKey)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotion(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p) || e.getAction() != EntityPotionEffectEvent.Action.ADDED || e.getNewEffect() == null) {
            return;
        }

        Role r = this.realm.on(p) ? this.of(p) : null;
        if (r == Role.WARDEN_BORN && e.getNewEffect().getType().equals(PotionEffectType.DARKNESS)) {
            e.setCancelled(true);
        } else if (r == Role.ALCHEMIST && e.getCause() == EntityPotionEffectEvent.Cause.POTION_DRINK && e.getNewEffect().getDuration() > 0) {
            PotionEffect x = e.getNewEffect();
            e.setCancelled(true);
            Tasks.later(1L, () -> p.addPotionEffect(new PotionEffect(x.getType(), x.getDuration() * 2, x.getAmplifier(), x.isAmbient(), x.hasParticles(), x.hasIcon())));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent e) {
        Material m = e.getItem().getType();
        if ((m == Material.GOLDEN_APPLE || m == Material.ENCHANTED_GOLDEN_APPLE) && this.is(e.getPlayer(), Role.ALCHEMIST)) {
            e.setCancelled(true);
            e.getPlayer().sendActionBar(Text.mm("<red>The gold turns to ash in your mouth. <gray>(Alchemist weakness)"));
            Snd.error(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent e) {
        if (e.getEntity() instanceof Player p && e.getRegainReason() == EntityRegainHealthEvent.RegainReason.MAGIC && this.is(p, Role.HOLLOW_KING)) {
            e.setCancelled(true);
            Tasks.later(1L, () -> p.damage(Math.min(6.0, e.getAmount())));
            p.sendActionBar(Text.mm("<red>The healing burns your hollow bones."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent e) {
        if (e.isGliding() && e.getEntity() instanceof Player p && this.is(p, Role.IRON_HEART)) {
            e.setCancelled(true);
            p.sendActionBar(Text.mm("<red>Your iron heart is too heavy to fly."));
        }
    }

    /** Used by the story to show "your weapon" lines etc. */
    static EquipmentSlot hand() {
        return EquipmentSlot.HAND;
    }
}
