package net.vortexrealm.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.arena.Arena;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.kit.Kit;
import net.vortexrealm.kit.KitCategory;
import net.vortexrealm.kit.KitManager;
import net.vortexrealm.kit.KitVote;
import net.vortexrealm.util.Items;
import net.vortexrealm.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;

/**
 * The "full access" tools of the AI assistant for operators: any server command, every kit field and item, kit votes,
 * arenas, the server log, plugin files and their YAML settings. Everything that touches the game runs on the main thread.
 */
final class AiTools {
    private static final Gson GSON = new Gson();
    /** Files the AI never reads or writes (API keys, passwords). */
    private static final Set<String> SECRET = Set.of("ai-keys.yml", "ai-key.txt", "discord.yml", "secrets.yml", "ops.json", "whitelist.json");
    private final VortexRealm plugin;

    AiTools(VortexRealm plugin) {
        this.plugin = plugin;
    }

    /** Names of the tools that change something (a backup is taken before the first one). */
    static final Set<String> CHANGING = Set.of("console", "edit_kit", "create_kit", "delete_kit", "kit_item", "kit_vote", "edit_arena", "edit_yaml");

    static void add(List<LlmClient.Tool> t, boolean op) {
        t.add(AiAgent.tool("kit_info", "Everything about one kit: all settings, every item by slot (0-35 inventory, 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand), votes and effects.", Map.of("id", AiAgent.str("Kit id")), List.of("id")));
        t.add(AiAgent.tool(
            "edit_kit",
            "Change any field of a kit. Fields: name, color (MiniMessage like <gold>), category (" + String.join("/", names(KitCategory.values())) + "), order, enabled, ranked, unranked, ffa, build, regen, hunger, rounds (first to N, 0 = default), build_height, pearl_cooldown (ticks, -1 default), wind_cooldown (ticks), hit_delay (ticks, -1 default), knockback (percent, 100 = vanilla), maps (AUTO, VANILLA, DESIGNED, CRYSTAL, MACE, MOLE, ANY or a comma list), round_throw (AUTO/ON/OFF), rounds_vote (true/false), description (lines split by \\n), effects (comma list type:amplifier:ticks, e.g. speed:0:1000000, empty clears).",
            Map.of("id", AiAgent.str("Kit id"), "field", AiAgent.str("Field name"), "value", AiAgent.str("New value")),
            List.of("id", "field", "value")
        ));
        t.add(AiAgent.tool("create_kit", "Make a new kit. With copy_from, it copies every item, setting, vote and map of that kit; otherwise it starts empty (fill it with kit_item).", Map.of("id", AiAgent.str("New kit id (a-z, 0-9, _)"), "name", AiAgent.str("Display name"), "copy_from", AiAgent.str("Kit id to copy (optional)")), List.of("id", "name")));
        t.add(AiAgent.tool(
            "kit_item",
            "Put an item into a kit slot, or empty it. Slots 0-8 hotbar, 9-35 inventory, 36 boots, 37 leggings, 38 chestplate, 39 helmet, 40 offhand (or the words helmet, chestplate, leggings, boots, offhand). Changing items resets players' saved layouts for that kit.",
            Map.of("id", AiAgent.str("Kit id"), "slot", AiAgent.str("Slot number or name"), "item", AiAgent.str("Material like diamond_sword, or 'empty'"), "amount", Map.of("type", "integer"), "enchants", AiAgent.str("Comma list name:level, e.g. sharpness:5,unbreaking:3")),
            List.of("id", "slot", "item")
        ));
        t.add(AiAgent.tool(
            "kit_vote",
            "Add or remove a pre-match vote of a kit. Kinds: limit (keep at most amount of item), remove (take item out), rounds (play first to amount), no_regen, knockback (amount = percent). remove_vote takes the vote's question or kind.",
            Map.of("id", AiAgent.str("Kit id"), "op", Map.of("type", "string", "enum", List.of("add", "remove_vote", "clear")), "kind", AiAgent.str("limit/remove/rounds/no_regen/knockback"), "item", AiAgent.str("Material for limit/remove"), "amount", Map.of("type", "integer"), "question", AiAgent.str("Text shown (optional)")),
            List.of("id", "op")
        ));
        t.add(AiAgent.tool("list_arenas", "Every duel arena: id, name, theme, pool, enabled, size, copies, busy.", Map.of(), List.of()));
        t.add(AiAgent.tool("edit_arena", "Change an arena: enabled (true/false), name, pool (VANILLA, DESIGNED, CRYSTAL, MACE, MOLE, BOT, ANY...).", Map.of("id", AiAgent.str("Arena id"), "field", AiAgent.str("enabled/name/pool"), "value", AiAgent.str("New value")), List.of("id", "field", "value")));
        t.add(AiAgent.tool("list_plugins", "Every plugin on the server with version and whether it is enabled, plus the server version.", Map.of(), List.of()));
        t.add(AiAgent.tool("read_log", "The last lines of the server log (logs/latest.log), optionally only lines containing a word.", Map.of("lines", Map.of("type", "integer"), "filter", AiAgent.str("Word to look for (optional)")), List.of()));
        if (op) {
            t.add(AiAgent.tool("delete_kit", "Delete a kit (players' ranks for it stay saved).", Map.of("id", AiAgent.str("Kit id")), List.of("id")));
            t.add(AiAgent.tool(
                "console",
                "Run ANY server command as the console (any plugin or vanilla: give, tp, gamemode, time, weather, worldborder, lp, essentials...) and get its output. Ask the admin first before anything that can't be undone (bans, deleting things, stopping the server).",
                Map.of("command", AiAgent.str("The command without the slash")),
                List.of("command")
            ));
            t.add(AiAgent.tool("read_file", "Read a text file of the server (relative to the server folder, e.g. plugins/VortexRealm/config.yml, server.properties). Folders list their files.", Map.of("path", AiAgent.str("Relative path")), List.of("path")));
            t.add(AiAgent.tool(
                "edit_yaml",
                "Set one value in a YAML file under plugins/ (any plugin's config). A copy is kept as <file>.ai-backup first. Most plugins need a reload or restart to see it; for this plugin's own settings prefer set_setting.",
                Map.of("file", AiAgent.str("Relative path, e.g. plugins/Essentials/config.yml"), "path", AiAgent.str("YAML path, e.g. teleport-cooldown"), "value", Map.of("description", "New value (string, number, boolean, array; null removes it)")),
                List.of("file", "path")
            ));
        }
    }

    private static List<String> names(Enum<?>[] all) {
        List<String> out = new ArrayList<>();
        for (Enum<?> e : all) {
            out.add(e.name());
        }

        return out;
    }

    /** Runs one of these tools, or returns null when the name isn't one of them. */
    String run(String who, boolean op, String name, JsonObject in, List<String> changes) throws Exception {
        switch (name) {
            case "kit_info":
                return this.sync(() -> this.kitInfo(str(in, "id")));
            case "edit_kit":
                return this.sync(() -> this.editKit(str(in, "id"), str(in, "field"), str(in, "value"), changes));
            case "create_kit":
                return this.sync(() -> this.createKit(str(in, "id"), str(in, "name"), str(in, "copy_from"), changes));
            case "delete_kit":
                if (!op) {
                    return "Only operators can delete kits.";
                }

                return this.sync(() -> {
                    Kit k = this.plugin.kits().get(str(in, "id"));
                    if (k == null) {
                        return "No kit '" + str(in, "id") + "'.";
                    }

                    this.plugin.queues().purgeKit(k.id);
                    this.plugin.kits().delete(k.id);
                    changes.add("deleted kit " + k.id);
                    return "Deleted kit " + k.id + ".";
                });
            case "kit_item":
                return this.sync(() -> this.kitItem(in, changes));
            case "kit_vote":
                return this.sync(() -> this.kitVote(in, changes));
            case "list_arenas":
                return this.sync(this::arenas);
            case "edit_arena":
                return this.sync(() -> this.editArena(str(in, "id"), str(in, "field"), str(in, "value"), changes));
            case "list_plugins":
                return this.sync(this::plugins);
            case "read_log":
                return this.log(in.has("lines") ? in.get("lines").getAsInt() : 60, str(in, "filter"));
            case "console":
                return op ? this.console(who, str(in, "command"), changes) : "Only operators can run console commands.";
            case "read_file":
                return op ? this.readFile(str(in, "path")) : "Only operators can read files.";
            case "edit_yaml":
                return op ? this.sync(() -> this.editYaml(str(in, "file"), str(in, "path"), in.get("value"), changes)) : "Only operators can edit files.";
            default:
                return null;
        }
    }

    private static String str(JsonObject in, String k) {
        return in.has(k) && !in.get(k).isJsonNull() ? (in.get(k).isJsonPrimitive() ? in.get(k).getAsString() : in.get(k).toString()) : "";
    }

    private String sync(Callable<String> c) throws Exception {
        return Bukkit.isPrimaryThread() ? c.call() : Bukkit.getScheduler().callSyncMethod(this.plugin, c).get(20L, TimeUnit.SECONDS);
    }

    private String console(String who, String raw, List<String> changes) throws Exception {
        String cmd = raw.replaceFirst("^/", "").trim();
        String root = cmd.split(" ")[0].toLowerCase(Locale.ROOT);
        if (root.contains(":")) {
            root = root.substring(root.indexOf(':') + 1);
        }

        for (String b : Cfg.s(Opt.AI_BLOCKED).toLowerCase(Locale.ROOT).split("[,\\s]+")) {
            if (!b.isBlank() && b.equals(root)) {
                return "'" + root + "' is blocked for the AI (Settings → ai.blocked-commands). Ask the admin to run it.";
            }
        }

        if (!Cfg.bool(Opt.AI_CONSOLE)) {
            return "Console commands are turned off for the AI (Settings → ai.console-commands).";
        }

        StringBuilder out = new StringBuilder();
        boolean found = Bukkit.getScheduler().callSyncMethod(this.plugin, () -> {
            CommandSender s = Bukkit.createCommandSender(c -> out.append(Text.plain(c)).append('\n'));
            return Bukkit.dispatchCommand(s, cmd);
        }).get(20L, TimeUnit.SECONDS);
        this.plugin.getLogger().info("[AI for " + who + "] console: /" + cmd);
        changes.add("/" + cmd);
        return found ? (out.length() == 0 ? "Done (no output)." : out.toString()) : "Unknown command.";
    }

    private Kit kit(String id) {
        return this.plugin.kits().get(id.toLowerCase(Locale.ROOT).trim());
    }

    private String kitInfo(String id) {
        Kit k = this.kit(id);
        if (k == null) {
            return "No kit '" + id + "'. Use list_kits.";
        }

        JsonObject o = new JsonObject();
        o.addProperty("id", k.id);
        o.addProperty("name", k.displayName);
        o.addProperty("color", k.color);
        o.addProperty("category", k.category.name());
        o.addProperty("order", k.order);
        o.addProperty("enabled", k.enabled);
        o.addProperty("ranked", k.ranked);
        o.addProperty("unranked", k.unranked);
        o.addProperty("ffa", k.ffa);
        o.addProperty("build", k.build);
        o.addProperty("regen", k.regen);
        o.addProperty("hunger", k.hunger);
        o.addProperty("rounds", k.roundsToWin + " (now first to " + k.rounds() + ")");
        o.addProperty("build_height", k.buildHeight);
        o.addProperty("pearl_cooldown", k.pearlCooldown);
        o.addProperty("wind_cooldown", k.windCooldown);
        o.addProperty("hit_delay", k.hitDelay + " (now " + k.hitDelayTicks() + ")");
        o.addProperty("knockback", k.knockback);
        o.addProperty("maps", k.arenaType + " (now " + k.arenaKind() + ")");
        o.addProperty("round_throw", k.throwMode);
        o.addProperty("rounds_vote", k.roundsVote);
        o.add("description", GSON.toJsonTree(k.description));
        JsonArray items = new JsonArray();
        for (int i = 0; i < k.contents.length; i++) {
            ItemStack it = k.contents[i];
            if (!Items.isEmpty(it)) {
                JsonObject e = new JsonObject();
                e.addProperty("slot", i);
                e.addProperty("item", it.getType().getKey().getKey());
                e.addProperty("amount", it.getAmount());
                if (!it.getEnchantments().isEmpty()) {
                    List<String> en = new ArrayList<>();
                    it.getEnchantments().forEach((ench, lvl) -> en.add(ench.getKey().getKey() + ":" + lvl));
                    e.addProperty("enchants", String.join(",", en));
                }

                items.add(e);
            }
        }

        o.add("items", items);
        List<String> votes = new ArrayList<>();
        for (KitVote v : k.votes) {
            votes.add(v.action().name().toLowerCase(Locale.ROOT) + ": " + v.question());
        }

        o.add("votes", GSON.toJsonTree(votes));
        List<String> fx = new ArrayList<>();
        for (PotionEffect pe : k.effects) {
            fx.add(pe.getType().getKey().getKey() + ":" + pe.getAmplifier() + ":" + pe.getDuration());
        }

        o.add("effects", GSON.toJsonTree(fx));
        return GSON.toJson((JsonElement)o);
    }

    private String editKit(String id, String field, String value, List<String> changes) {
        Kit k = this.kit(id);
        if (k == null) {
            return "No kit '" + id + "'.";
        }

        String f = field.toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        String v = value.trim();
        boolean b = v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on") || v.equalsIgnoreCase("yes");
        try {
            switch (f) {
                case "name", "display_name" -> k.displayName = v;
                case "color", "colour" -> k.color = v.startsWith("<") ? v : "<" + v + ">";
                case "category", "tab" -> k.category = KitCategory.parse(v, k.category);
                case "order" -> k.order = Integer.parseInt(v);
                case "enabled" -> {
                    k.enabled = b;
                    if (!b) {
                        this.plugin.queues().purgeKit(k.id);
                    }
                }
                case "ranked" -> k.ranked = b;
                case "unranked" -> k.unranked = b;
                case "ffa" -> {
                    k.ffa = b;
                    if (b) {
                        this.plugin.ffa().ensureArena(k);
                    }
                }
                case "build", "building" -> k.build = b;
                case "regen", "natural_regen" -> k.regen = b;
                case "hunger" -> k.hunger = b;
                case "rounds", "rounds_to_win" -> k.roundsToWin = Math.max(0, Math.min(10, Integer.parseInt(v)));
                case "build_height" -> k.buildHeight = Integer.parseInt(v) <= 0 ? -1 : Integer.parseInt(v);
                case "pearl_cooldown" -> k.pearlCooldown = Integer.parseInt(v);
                case "wind_cooldown", "wind_charge_cooldown" -> k.windCooldown = Integer.parseInt(v);
                case "hit_delay" -> k.hitDelay = Math.max(-1, Math.min(20, Integer.parseInt(v)));
                case "knockback", "kb" -> k.knockback = Math.max(0, Math.min(400, Integer.parseInt(v.replace("%", ""))));
                case "maps", "arena_type", "arenas" -> k.arenaType = v.isBlank() ? "AUTO" : v.toUpperCase(Locale.ROOT).replace(' ', ',');
                case "round_throw", "throw" -> k.throwMode = v.toUpperCase(Locale.ROOT);
                case "rounds_vote" -> k.roundsVote = b;
                case "description" -> {
                    k.description.clear();
                    k.description.addAll(Arrays.asList(v.split("\\\\n|\n")));
                }
                case "effects" -> {
                    k.effects.clear();
                    for (String e : v.split(",")) {
                        PotionEffect pe = e.isBlank() ? null : KitManager.parseEffect(e.trim());
                        if (pe != null) {
                            k.effects.add(pe);
                        }
                    }
                }
                default -> {
                    return "Unknown field '" + field + "'.";
                }
            }
        } catch (NumberFormatException nfe) {
            return "'" + v + "' isn't a number.";
        }

        this.plugin.kits().save();
        this.plugin.lobby().refreshVisibilityAll();
        changes.add("kit " + k.id + " " + f + " -> " + v);
        return "Saved: kit " + k.id + " " + f + " = " + v + ".";
    }

    private String createKit(String id, String name, String from, List<String> changes) {
        String nid = id.toLowerCase(Locale.ROOT).trim();
        if (!KitManager.ID.matcher(nid).matches()) {
            return "Bad id: use a-z, 0-9 and _.";
        } else if (this.plugin.kits().get(nid) != null) {
            return "Kit '" + nid + "' already exists.";
        }

        Kit k;
        if (!from.isBlank()) {
            Kit src = this.kit(from);
            if (src == null) {
                return "No kit '" + from + "' to copy.";
            }

            k = this.plugin.kits().copy(src, nid, name.isBlank() ? nid : name);
        } else {
            k = this.plugin.kits().create(nid, name.isBlank() ? nid : name, null);
        }

        changes.add("created kit " + k.id + (from.isBlank() ? "" : " (copy of " + from + ")"));
        return "Created kit " + k.id + ". It is enabled; fill or change it with kit_item / edit_kit.";
    }

    private static int slot(String s) {
        return switch (s.toLowerCase(Locale.ROOT).trim()) {
            case "boots", "feet" -> 36;
            case "leggings", "legs" -> 37;
            case "chestplate", "chest", "elytra" -> 38;
            case "helmet", "head" -> 39;
            case "offhand", "off_hand", "shield" -> 40;
            default -> Integer.parseInt(s.trim());
        };
    }

    private String kitItem(JsonObject in, List<String> changes) {
        Kit k = this.kit(str(in, "id"));
        if (k == null) {
            return "No kit '" + str(in, "id") + "'.";
        }

        int slot;
        try {
            slot = slot(str(in, "slot"));
        } catch (NumberFormatException e) {
            return "Bad slot '" + str(in, "slot") + "'.";
        }

        if (slot < 0 || slot >= k.contents.length) {
            return "Slot must be 0-" + (k.contents.length - 1) + ".";
        }

        String item = str(in, "item").trim();
        if (item.equalsIgnoreCase("empty") || item.equalsIgnoreCase("air") || item.isEmpty()) {
            k.contents[slot] = null;
        } else {
            Material m = Material.matchMaterial(item);
            if (m == null || !m.isItem()) {
                return "Unknown item '" + item + "'.";
            }

            ItemStack it = new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), in.has("amount") ? in.get("amount").getAsInt() : 1)));
            for (String e : str(in, "enchants").split(",")) {
                if (e.isBlank()) {
                    continue;
                }

                String[] p = e.trim().split(":");
                Enchantment en = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(p[0].toLowerCase(Locale.ROOT).replace(' ', '_')));
                if (en == null) {
                    return "Unknown enchantment '" + p[0] + "'.";
                }

                it.addUnsafeEnchantment(en, p.length > 1 ? Integer.parseInt(p[1]) : 1);
            }

            k.contents[slot] = it;
        }

        k.bumpVersion();
        this.plugin.kits().save();
        changes.add("kit " + k.id + " slot " + slot + " -> " + (k.contents[slot] == null ? "empty" : item + " x" + k.contents[slot].getAmount()));
        return "Saved slot " + slot + ".";
    }

    private String kitVote(JsonObject in, List<String> changes) {
        Kit k = this.kit(str(in, "id"));
        if (k == null) {
            return "No kit '" + str(in, "id") + "'.";
        }

        String op = str(in, "op");
        if (op.equals("clear")) {
            k.votes.clear();
            this.plugin.kits().save();
            changes.add("kit " + k.id + " votes cleared");
            return "Removed every vote of " + k.id + " (automatic votes still show; turn off rounds_vote to hide that one).";
        }

        String kind = str(in, "kind").toUpperCase(Locale.ROOT).replace(' ', '_');
        if (op.equals("remove_vote")) {
            String q = str(in, "question").toLowerCase(Locale.ROOT);
            boolean gone = k.votes.removeIf(v -> !q.isBlank() && v.question().toLowerCase(Locale.ROOT).contains(q) || q.isBlank() && v.action().name().equals(kind));
            this.plugin.kits().save();
            if (gone) {
                changes.add("kit " + k.id + " vote removed: " + (q.isBlank() ? kind : q));
            }

            return gone ? "Removed." : "No such vote.";
        }

        KitVote.Action a;
        try {
            a = KitVote.Action.valueOf(kind);
        } catch (IllegalArgumentException e) {
            return "Unknown kind '" + kind + "'.";
        }

        int amount = in.has("amount") ? in.get("amount").getAsInt() : 0;
        Material m = switch (a) {
            case ROUNDS -> Material.CLOCK;
            case NO_REGEN -> Material.GOLDEN_APPLE;
            case KNOCKBACK -> Material.PISTON;
            default -> Material.matchMaterial(str(in, "item"));
        };
        if (m == null) {
            return "Unknown item '" + str(in, "item") + "'.";
        }

        String q = str(in, "question");
        if (q.isBlank()) {
            q = switch (a) {
                case LIMIT -> "Limit to " + amount + " " + Items.pretty(m);
                case REMOVE -> "No " + Items.pretty(m);
                case ROUNDS -> "Play First To " + amount + " Rounds";
                case NO_REGEN -> "No Natural Regen";
                case KNOCKBACK -> "Knockback " + amount + "%";
            };
        }

        Material fm = m;
        k.votes.removeIf(v -> v.action() == a && (!v.itemVote() || v.material() == fm));
        k.votes.add(new KitVote(a.name().toLowerCase(Locale.ROOT) + "_" + m.getKey().getKey(), q, a, m, amount));
        this.plugin.kits().save();
        changes.add("kit " + k.id + " vote added: " + q);
        return "Added vote '" + q + "'. At most " + Cfg.i(Opt.VOTE_MAX) + " votes show per match (match.max-votes).";
    }

    private String arenas() {
        JsonArray a = new JsonArray();
        for (Arena ar : this.plugin.arenas().all()) {
            JsonObject o = new JsonObject();
            o.addProperty("id", ar.id);
            o.addProperty("name", ar.displayName);
            o.addProperty("theme", ar.theme.name());
            o.addProperty("pool", ar.pool);
            o.addProperty("enabled", ar.enabled);
            o.addProperty("size", ar.size);
            o.addProperty("copies", ar.instances.size());
            o.addProperty("busy", this.plugin.arenas().isBusy(ar));
            a.add(o);
        }

        return GSON.toJson((JsonElement)a);
    }

    private String editArena(String id, String field, String value, List<String> changes) {
        Arena a = this.plugin.arenas().get(id);
        if (a == null) {
            return "No arena '" + id + "'. Use list_arenas.";
        }

        switch (field.toLowerCase(Locale.ROOT)) {
            case "enabled" -> a.enabled = value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on");
            case "name" -> a.displayName = value;
            case "pool" -> a.pool = Arena.normPools(value);
            default -> {
                return "Unknown field '" + field + "' (enabled, name, pool).";
            }
        }

        this.plugin.arenas().save();
        changes.add("arena " + a.id + " " + field + " -> " + value);
        return "Saved.";
    }

    private String plugins() {
        JsonObject o = new JsonObject();
        o.addProperty("server", Bukkit.getName() + " " + Bukkit.getVersion());
        JsonArray a = new JsonArray();
        for (Plugin p : Bukkit.getPluginManager().getPlugins()) {
            a.add(p.getName() + " " + p.getPluginMeta().getVersion() + (p.isEnabled() ? "" : " (disabled)"));
        }

        o.add("plugins", a);
        return GSON.toJson((JsonElement)o);
    }

    private String log(int lines, String filter) throws IOException {
        File f = new File(Bukkit.getWorldContainer(), "logs/latest.log");
        if (!f.isFile()) {
            return "No logs/latest.log.";
        }

        List<String> all = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        String q = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        for (int i = all.size() - 1; i >= 0 && out.size() < Math.max(1, Math.min(300, lines)); i--) {
            String l = all.get(i);
            if (q.isEmpty() || l.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(0, l);
            }
        }

        return String.join("\n", out);
    }

    /** A path inside the server folder that isn't a secret; null when it isn't allowed. */
    private static Path safe(String rel) throws IOException {
        Path root = Bukkit.getWorldContainer().getCanonicalFile().toPath();
        Path p = root.resolve(rel.replace('\\', '/').replaceFirst("^/+", "")).toFile().getCanonicalFile().toPath();
        if (!p.startsWith(root)) {
            return null;
        }

        String n = p.getFileName() == null ? "" : p.getFileName().toString().toLowerCase(Locale.ROOT);
        return SECRET.contains(n) || n.endsWith(".jar") || n.endsWith(".key") ? null : p;
    }

    private String readFile(String rel) throws IOException {
        Path p = safe(rel);
        if (p == null) {
            return "That file can't be read by the AI.";
        } else if (Files.isDirectory(p)) {
            List<String> names = new ArrayList<>();
            try (var s = Files.list(p)) {
                s.limit(200).forEach(x -> names.add(x.getFileName() + (Files.isDirectory(x) ? "/" : "")));
            }

            return String.join("\n", names);
        } else if (!Files.isRegularFile(p)) {
            return "No such file.";
        } else if (Files.size(p) > 200000L) {
            return "Too big (" + Files.size(p) / 1024L + " KB).";
        }

        String text = Files.readString(p, StandardCharsets.UTF_8);
        // passwords and tokens in configs stay hidden
        return text.replaceAll("(?im)^(\\s*[\\w.-]*(password|token|secret|api[-_]?key)[\\w.-]*\\s*[:=]\\s*).+$", "$1(hidden)");
    }

    private String editYaml(String rel, String path, JsonElement value, List<String> changes) throws IOException {
        Path p = safe(rel);
        Path plugins = Bukkit.getWorldContainer().getCanonicalFile().toPath().resolve("plugins");
        if (p == null || !p.startsWith(plugins) || !(p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))) {
            return "Only .yml files under plugins/ can be edited (and not ones holding keys).";
        } else if (!Files.isRegularFile(p)) {
            return "No such file.";
        }

        Files.copy(p, p.resolveSibling(p.getFileName() + ".ai-backup"), StandardCopyOption.REPLACE_EXISTING);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(p.toFile());
        Object before = y.get(path);
        Object v = value == null || value.isJsonNull() ? null : GSON.fromJson(value, Object.class);
        if (v instanceof Double d && d == Math.rint(d) && !value.getAsString().contains(".")) {
            v = d.intValue();
        }

        y.set(path, v);
        y.save(p.toFile());
        changes.add(rel + " " + path + ": " + before + " -> " + v);
        boolean ours = p.startsWith(this.plugin.getDataFolder().getCanonicalFile().toPath());
        return "Saved (backup: " + p.getFileName() + ".ai-backup)." + (ours ? " Run /pvpadmin reload to apply." : " The plugin may need a reload or restart.");
    }
}
