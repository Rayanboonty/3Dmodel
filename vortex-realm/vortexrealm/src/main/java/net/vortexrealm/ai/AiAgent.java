package net.vortexrealm.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.config.Cfg;
import net.vortexrealm.config.Opt;
import net.vortexrealm.util.Text;
import net.vortexrealm.web.WebSite;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;

public final class AiAgent {
    private static final Gson GSON = new Gson();
    private static final int MAX_STEPS = 14;
    private static final Set<String> NEVER = Set.of("ai", "assistant", "webadmin");
    private static final String SYSTEM = "You are the assistant of a Minecraft server (Paper 1.21.11) built into its VortexRealm plugin, talking with the server's admins. You run the plugin for them with your tools: settings (by their path), kits (on/off, ranked, unranked, FFA), server ranks (look, permissions, perks, price, who has them), players (kick, send to lobby, verify, heal), broadcasts, reports, scoreboard designs, worlds and their modes, backups and restores, and every command of the plugin (list_commands shows them with their usage; NPCs, decorations, arenas, events, the hacker pool, screenshares and more are done through them).\n\nLook things up before you change them: find the right setting with get_settings, check the kit or rank exists. Then make the change the admin asked for. If a request is unclear, or would change many things the admin didn't name, say what you would do and ask first. A full backup is taken automatically before your first change in a conversation, so changes can be undone with a restore.\n\nAnswer briefly, in the admin's language. When you changed something, list each change as \"setting/kit/rank: old -> new\". Don't claim a change you didn't make with a tool. You can't see the game, players' screens or anything outside these tools.\n\nOperators also have full access: console runs ANY server command (vanilla and every plugin), kit_info / edit_kit / create_kit / kit_item / kit_vote edit every part of a kit (items, enchantments, armor, knockback, hit delay, cooldowns, maps, votes, effects), list_arenas / edit_arena manage arenas, read_log reads the server log, read_file and edit_yaml read and change any plugin's files. Use them to do what the admin asks end to end, but ask before anything destructive or hard to undo (bans, wipes, deleting kits or worlds).";
    private final VortexRealm plugin;
    private final File keyFile;
    private final Map<String, List<LlmClient.Msg>> conversations = new ConcurrentHashMap<>();
    private final Map<String, Long> lastUse = new ConcurrentHashMap<>();
    private final Set<String> backedUp = ConcurrentHashMap.newKeySet();
    private final File keysFile;
    private final LlmClient llm = new LlmClient();
    private final AiTools extra;
    /** The provider that answered last (shown in /ai). */
    private volatile String lastProvider;

    public AiAgent(VortexRealm plugin) {
        this.plugin = plugin;
        this.keyFile = new File(plugin.getDataFolder(), "ai-key.txt");
        this.keysFile = new File(plugin.getDataFolder(), "ai-keys.yml");
        this.extra = new AiTools(plugin);
    }

    /** The saved key of a provider: ai-keys.yml, then its environment variable, then the old ai-key.txt. */
    public String key(LlmClient.Provider p) {
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(this.keysFile);
        String k = y.getString(p.id(), "");
        if (!k.isBlank()) {
            return k.trim();
        }

        if (!p.env().isEmpty()) {
            String env = System.getenv(p.env());
            if (env != null && !env.isBlank()) {
                return env.trim();
            }
        }

        try {
            if (this.keyFile.isFile()) {
                String old = Files.readString(this.keyFile.toPath(), StandardCharsets.UTF_8).trim();
                LlmClient.Provider of = LlmClient.guess(old);
                if (!old.isEmpty() && (of == null ? p.id().equals("anthropic") : of.id().equals(p.id()))) {
                    return old;
                }
            }
        } catch (IOException var5) {
        }

        return "";
    }

    /** The providers to try, in order: the chosen one, or (auto) every one with a key, then local models. */
    public List<LlmClient.Provider> candidates() {
        String want = Cfg.s(Opt.AI_PROVIDER).trim().toLowerCase(Locale.ROOT);
        String mode = Cfg.s(Opt.AI_MODE).trim().toLowerCase(Locale.ROOT);
        List<LlmClient.Provider> out = new ArrayList<>();
        if (mode.equals("local") && (want.isEmpty() || want.equals("auto") || LlmClient.provider(want) == null || !LlmClient.provider(want).local())) {
            // local only: a model on this machine (or at ai.base-url), never an online provider
            out.add(LlmClient.provider("ollama"));
            out.add(LlmClient.provider("lmstudio"));
            return out;
        }

        if (!want.isEmpty() && !want.equals("auto")) {
            LlmClient.Provider p = LlmClient.provider(want);
            if (p != null) {
                out.add(p);
            }

            return out;
        }

        for (LlmClient.Provider p : LlmClient.PROVIDERS) {
            if (!p.local() && !this.key(p).isEmpty()) {
                out.add(p);
            }
        }

        // no key at all: a model running on this machine (Ollama, then LM Studio), if there is one
        if (!mode.equals("online") && (out.isEmpty() || Cfg.bool(Opt.AI_LOCAL_FALLBACK))) {
            out.add(LlmClient.provider("ollama"));
            out.add(LlmClient.provider("lmstudio"));
        }

        return out;
    }

    public boolean hasKey() {
        String want = Cfg.s(Opt.AI_PROVIDER).trim().toLowerCase(Locale.ROOT);
        LlmClient.Provider chosen = LlmClient.provider(want);
        if (chosen != null && chosen.local() || "local".equalsIgnoreCase(Cfg.s(Opt.AI_MODE).trim())) {
            return true;
        }

        for (LlmClient.Provider p : LlmClient.PROVIDERS) {
            if (!p.local() && !this.key(p).isEmpty()) {
                return true;
            }
        }

        return false;
    }

    /** A short status for /ai: the providers that have a key, or the local model. */
    public String status() {
        List<String> on = new ArrayList<>();
        for (LlmClient.Provider p : this.candidates()) {
            on.add(p.name());
        }

        return (this.hasKey() ? "" : "no API key - trying a local model: ") + String.join(", ", on)
            + (this.lastProvider == null ? "" : " (last answer: " + this.lastProvider + ")");
    }

    /** Saves a key. With no provider, it is told from the key's prefix (sk-ant- = Anthropic, AIza = Gemini...). */
    public String setKey(String provider, String k) throws IOException {
        LlmClient.Provider p = provider == null ? (k == null || k.isBlank() ? null : LlmClient.guess(k)) : LlmClient.provider(provider);
        if (p == null && provider == null && k != null && !k.isBlank()) {
            LlmClient.Provider chosen = LlmClient.provider(Cfg.s(Opt.AI_PROVIDER).trim());
            p = chosen != null ? chosen : null;
        }

        if (p == null && (k == null || k.isBlank()) && provider == null) {
            Files.deleteIfExists(this.keysFile.toPath());
            Files.deleteIfExists(this.keyFile.toPath());
            return "all";
        }

        if (p == null) {
            return null;
        }

        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(this.keysFile);
        y.set(p.id(), k == null || k.isBlank() ? null : k.trim());
        y.save(this.keysFile);
        return p.name();
    }

    public void setKey(String k) throws IOException {
        this.setKey(null, k);
    }

    public void reset(String session) {
        this.conversations.remove(session);
        this.backedUp.remove(session);
    }

    static LlmClient.Tool tool(String name, String description, Map<String, Object> props, List<String> required) {
        JsonObject schema = new JsonObject();
        schema.addProperty("type", "object");
        schema.add("properties", props.isEmpty() ? new JsonObject() : GSON.toJsonTree(props));
        schema.add("required", GSON.toJsonTree(required));
        return new LlmClient.Tool(name, description, schema);
    }

    static Map<String, Object> str(String desc) {
        return Map.of("type", "string", "description", desc);
    }

    private static List<LlmClient.Tool> tools(boolean op) {
        List<LlmClient.Tool> t = new ArrayList<>();
        t.add(
            tool(
                "get_settings",
                "Find plugin settings. Returns path, name, type, current value, allowed range or options, and description. Search words match the path, name, section or description; empty search lists the sections.",
                Map.of("search", str("Words to look for, e.g. 'countdown', 'ffa', 'tab'")),
                List.of()
            )
        );
        t.add(
            tool(
                "set_setting",
                "Change one setting by its exact path (from get_settings). Booleans: true/false; lists: a JSON array.",
                Map.of("path", str("The setting path"), "value", Map.of("description", "The new value (string, number, boolean or array)")),
                List.of("path", "value")
            )
        );
        t.add(tool("list_kits", "All kits (gamemodes) with id, name, enabled, ranked, unranked, ffa, players.", Map.of(), List.of()));
        t.add(
            tool(
                "set_kit",
                "Turn a kit option on or off.",
                Map.of(
                    "id",
                    str("Kit id from list_kits"),
                    "field",
                    Map.of("type", "string", "enum", List.<String>of("enabled", "ranked", "unranked", "ffa")),
                    "value",
                    Map.of("type", "boolean")
                ),
                List.of("id", "field", "value")
            )
        );
        t.add(tool("list_ranks", "Server ranks (Owner, Admin, VIP...) with their look, permissions, perks, price.", Map.of(), List.of()));
        t.add(
            tool(
                "edit_rank",
                "Change a server rank. Fields: label, from / to (gradient #RRGGBB), nameColor, icon (item name like diamond, or glyph:play), weight (tab order), price, description, inherit (another rank id), staff (true/false), bold, permissions (comma list, replaces all), perks (lines separated by \\n, replaces all), create (makes rank id), delete.",
                Map.of("id", str("Rank id"), "field", str("Field to change"), "value", str("New value (text)")),
                List.of("id", "field")
            )
        );
        t.add(tool("give_rank", "Give a player a server rank.", Map.of("player", str("Player name"), "rank", str("Rank id")), List.of("player", "rank")));
        t.add(
            tool(
                "backup",
                "Save a backup now: full (everything in one file), settings or kits.",
                Map.of("kind", Map.of("type", "string", "enum", List.<String>of("full", "settings", "kits"))),
                List.of("kind")
            )
        );
        t.add(tool("server_status", "TPS, memory, players online, matches, queues, failed plugin parts.", Map.of(), List.of()));
        t.add(tool("list_players", "Players online: name, where they are, ping, rank, country, verified, hacker pool.", Map.of(), List.of()));
        t.add(
            tool(
                "player_action",
                "Do something to an online player.",
                Map.of(
                    "name",
                    str("Player name"),
                    "action",
                    Map.of("type", "string", "enum", List.<String>of("kick", "lobby", "verify", "unverify", "heal")),
                    "reason",
                    str("Kick reason (optional)")
                ),
                List.of("name", "action")
            )
        );
        t.add(tool("broadcast", "Send a message to everyone online.", Map.of("message", str("The message")), List.of("message")));
        t.add(tool("list_reports", "Open player reports (who, why, by whom, where).", Map.of(), List.of()));
        t.add(
            tool(
                "set_scoreboard",
                "Apply a scoreboard design to the SMP sidebars.",
                Map.of(
                    "design", Map.of("type", "string", "enum", List.<String>of("CLASSIC", "MINIMAL", "BOXED", "MCPVP", "NEON", "COMPACT", "ELEGANT", "DONUT"))
                ),
                List.of("design")
            )
        );
        t.add(tool("list_worlds", "Every loaded world with its mode and player count.", Map.of(), List.of()));
        t.add(
            tool(
                "set_world_mode",
                "Give a world its mode (what it is for).",
                Map.of(
                    "world",
                    str("World name"),
                    "mode",
                    Map.of("type", "string", "enum", List.<String>of("PVP", "HUB", "NONE"))
                ),
                List.of("world", "mode")
            )
        );
        t.add(tool("list_backups", "The backups kept: full (all-in-one), kits, settings, vault, ranks.", Map.of(), List.of()));
        if (op) {
            t.add(
                tool(
                    "restore_backup",
                    "Put a backup back (the current state is backed up first). Kinds: full, kits, settings.",
                    Map.of("kind", Map.of("type", "string", "enum", List.<String>of("full", "kits", "settings")), "file", str("File name from list_backups")),
                    List.of("kind", "file")
                )
            );
            t.add(tool("list_commands", "Every command of the plugin with its description, usage and aliases. Use it before run_command.", Map.of(), List.of()));
            t.add(
                tool(
                    "run_command",
                    "Run any command of the VortexRealm plugin as the console (see list_commands) and get its output. Commands that need a player in game (menus, building where you stand) can't work from here - tell the admin to run those.",
                    Map.of("command", str("The command without the slash")),
                    List.of("command")
                )
            );
        }

        AiTools.add(t, op);
        return t;
    }

    private String run(String session, String who, boolean op, String name, JsonObject in, List<String> changes) throws Exception {
        WebSite web = this.plugin.webSite();
        if (web == null) {
            return "The admin actions aren't available (the website part failed to start).";
        } else {
            boolean changing = Set.of(
                    "set_setting", "set_kit", "edit_rank", "give_rank", "run_command", "player_action", "set_scoreboard", "set_world_mode", "restore_backup"
                )
                .contains(name) || AiTools.CHANGING.contains(name);
            if (changing && this.backedUp.add(session)) {
                JsonObject b = new JsonObject();
                b.addProperty("kind", "full");
                web.perform(who, op, "backup", b);
            }

            switch (name) {
                case "get_settings":
                    return settings(web.perform(who, op, "settings", new JsonObject()).getAsJsonArray(), in.has("search") ? in.get("search").getAsString() : "");
                case "set_setting": {
                    String path = in.get("path").getAsString();
                    Object before = valueOf(path);
                    JsonElement r = web.perform(who, op, "setting", in);
                    String msg = r.getAsJsonObject().get("message").getAsString();
                    if (msg.startsWith("Saved")) {
                        changes.add(path + ": " + before + " -> " + r.getAsJsonObject().get("value"));
                    }

                    return GSON.toJson(r);
                }
                case "list_kits":
                    return GSON.toJson(web.perform(who, op, "kits", new JsonObject()));
                case "set_kit": {
                    JsonElement r = web.perform(who, op, "kit", in);
                    if (r.getAsJsonObject().get("message").getAsString().startsWith("Saved")) {
                        changes.add("kit " + in.get("id").getAsString() + " " + in.get("field").getAsString() + " -> " + in.get("value"));
                    }

                    return GSON.toJson(r);
                }
                case "list_ranks":
                    return GSON.toJson(web.perform(who, op, "ranks", new JsonObject()));
                case "edit_rank": {
                    JsonElement r = web.perform(who, op, "rank", in);
                    String msg = r.getAsJsonObject().get("message").getAsString();
                    if (msg.startsWith("Saved") || msg.startsWith("Created") || msg.startsWith("Deleted")) {
                        changes.add(
                            "rank "
                                + in.get("id").getAsString()
                                + " "
                                + in.get("field").getAsString()
                                + (in.has("value") ? " -> " + in.get("value").getAsString() : "")
                        );
                    }

                    return GSON.toJson(r);
                }
                case "give_rank": {
                    JsonObject b = new JsonObject();
                    b.add("player", in.get("player"));
                    b.add("rank", in.get("rank"));
                    JsonElement r = web.perform(who, op, "rankset", b);
                    changes.add(in.get("player").getAsString() + " -> rank " + in.get("rank").getAsString());
                    return GSON.toJson(r);
                }
                case "backup":
                    return GSON.toJson(web.perform(who, op, "backup", in));
                case "server_status":
                    return GSON.toJson(web.perform(who, op, "status", new JsonObject()));
                case "list_players":
                    return GSON.toJson(web.perform(who, op, "players", new JsonObject()));
                case "player_action": {
                    JsonElement r = web.perform(who, op, "player", in);
                    changes.add(in.get("action").getAsString() + " " + in.get("name").getAsString());
                    return GSON.toJson(r);
                }
                case "broadcast":
                    changes.add("broadcast: " + in.get("message").getAsString());
                    return GSON.toJson(web.perform(who, op, "broadcast", in));
                case "list_reports":
                    return GSON.toJson(web.perform(who, op, "reports", new JsonObject()));
                case "set_scoreboard":
                    changes.add("scoreboard design -> " + in.get("design").getAsString());
                    return GSON.toJson(web.perform(who, op, "scoreboard", in));
                case "list_worlds":
                    return GSON.toJson(web.perform(who, op, "worlds", new JsonObject()));
                case "set_world_mode": {
                    JsonElement r = web.perform(who, op, "worldmode", in);
                    if (r.getAsJsonObject().get("message").getAsString().startsWith("Saved")) {
                        changes.add("world " + in.get("world").getAsString() + " -> " + in.get("mode").getAsString());
                    }

                    return GSON.toJson(r);
                }
                case "list_backups":
                    return GSON.toJson(web.perform(who, op, "backups", new JsonObject()));
                case "restore_backup": {
                    if (!op) {
                        return "Only operators can restore backups.";
                    }

                    JsonElement r = web.perform(who, op, "restore", in);
                    changes.add("restored " + in.get("kind").getAsString() + " backup " + in.get("file").getAsString());
                    return GSON.toJson(r);
                }
                case "list_commands":
                    return this.commands();
                case "run_command":
                    if (!op) {
                        return "Only operators can run commands.";
                    } else {
                        String cmd = in.get("command").getAsString().replaceFirst("^/", "").trim();
                        String root = cmd.split(" ")[0].toLowerCase(Locale.ROOT);
                        PluginCommand pc = this.plugin.getServer().getPluginCommand(root);
                        if (pc != null && pc.getPlugin() == this.plugin && !NEVER.contains(pc.getName())) {
                            StringBuilder out = new StringBuilder();
                            boolean found = Bukkit.getScheduler().callSyncMethod(this.plugin, () -> {
                                CommandSender s = Bukkit.createCommandSender(c -> out.append(Text.plain(c)).append('\n'));
                                return Bukkit.dispatchCommand(s, cmd);
                            }).get(15L, TimeUnit.SECONDS);
                            this.plugin.getLogger().info("[AI for " + who + "] /" + cmd);
                            changes.add("/" + cmd);
                            return found ? (out.length() == 0 ? "Done." : out.toString()) : "Unknown command.";
                        }

                        return "Not allowed: only commands of the VortexRealm plugin (see list_commands).";
                    }
                default:
                    String r = this.extra.run(who, op, name, in, changes);
                    return r == null ? "Unknown tool." : r;
            }
        }
    }

    private String commands() {
        JsonArray a = new JsonArray();

        for (String name : this.plugin.getDescription().getCommands().keySet()) {
            PluginCommand pc = this.plugin.getCommand(name);
            if (pc != null && !NEVER.contains(name)) {
                JsonObject o = new JsonObject();
                o.addProperty("name", name);
                o.addProperty("description", pc.getDescription());
                o.addProperty("usage", pc.getUsage());
                JsonArray al = new JsonArray();
                pc.getAliases().forEach(al::add);
                o.add("aliases", al);
                a.add(o);
            }
        }

        return GSON.toJson((JsonElement)a);
    }

    private static Object valueOf(String path) {
        for (Opt<?> o : Opt.all()) {
            if (o.path.equals(path)) {
                return Cfg.get(o);
            }
        }

        return "?";
    }

    private static String settings(JsonArray all, String search) {
        String q = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) {
            Map<String, Integer> sections = new LinkedHashMap<>();

            for (JsonElement e : all) {
                sections.merge(e.getAsJsonObject().get("section").getAsString(), 1, Integer::sum);
            }

            return "Sections (setting count): " + sections + ". Search with words to see settings.";
        } else {
            String[] words = q.split("\\s+");
            JsonArray out = new JsonArray();

            for (JsonElement e : all) {
                JsonObject o = e.getAsJsonObject();
                String hay = (o.get("path").getAsString()
                        + " "
                        + o.get("name").getAsString()
                        + " "
                        + o.get("section").getAsString()
                        + " "
                        + o.get("desc").getAsString())
                    .toLowerCase(Locale.ROOT);
                boolean match = true;

                for (String w : words) {
                    match &= hay.contains(w);
                }

                if (match) {
                    JsonObject c = o.deepCopy();
                    if (c.has("secret")) {
                        c.addProperty("value", "(hidden)");
                    }

                    out.add(c);
                    if (out.size() >= 40) {
                        break;
                    }
                }
            }

            return out.isEmpty() ? "No setting matches '" + search + "'. Try fewer or other words." : GSON.toJson((JsonElement)out);
        }
    }

    public AiAgent.Reply chat(String session, String who, boolean op, String text) {
        if (!Cfg.bool(Opt.AI_ENABLED)) {
            return new AiAgent.Reply("The AI assistant is turned off (Settings → Website → AI Assistant).", List.of(), false);
        }

        Long last = this.lastUse.get(session);
        if (last != null && System.currentTimeMillis() - last > 1800000L) {
            this.reset(session);
        }

        this.lastUse.put(session, System.currentTimeMillis());
        List<LlmClient.Msg> msgs = this.conversations.computeIfAbsent(session, k -> new ArrayList<>());
        synchronized (msgs) {
            if (msgs.size() > 60) {
                msgs.clear();
                this.backedUp.remove(session);
            }

            int start = msgs.size();
            msgs.add(LlmClient.Msg.user(text));
            List<String> changes = new ArrayList<>();
            StringBuilder reply = new StringBuilder();
            List<LlmClient.Tool> tools = tools(op);
            List<String> why = new ArrayList<>();
            List<LlmClient.Provider> order = this.candidates();
            for (LlmClient.Provider p : order) {
                String key = this.key(p);
                String model = this.model(p, p == order.get(0));
                if (model.isEmpty()) {
                    why.add(p.name() + ": no model set (ai.model)");
                    continue;
                }

                try {
                    for (int step = 0; step < MAX_STEPS; step++) {
                        // ai.base-url: the address of a local model, or of a proxy for the provider that was picked by name
                        boolean picked = !"auto".equalsIgnoreCase(Cfg.s(Opt.AI_PROVIDER).trim()) && !Cfg.s(Opt.AI_PROVIDER).isBlank();
                        LlmClient.Result res = this.llm.chat(p, p.local() || picked ? Cfg.s(Opt.AI_BASE_URL) : null, key, model, SYSTEM, msgs, tools, 16000);
                        LlmClient.Msg said = LlmClient.Msg.assistant(res.text());
                        said.calls.addAll(res.calls());
                        msgs.add(said);
                        if (!res.text().isBlank()) {
                            reply.append(reply.isEmpty() ? "" : "\n").append(res.text());
                        }

                        if (res.calls().isEmpty()) {
                            if (res.refused() && reply.isEmpty()) {
                                reply.append("I can't help with that one.");
                            }
                            break;
                        }

                        for (LlmClient.Call call : res.calls()) {
                            boolean error = false;
                            String output;
                            try {
                                output = this.run(session, who, op, call.name(), call.input(), changes);
                            } catch (Exception var22) {
                                output = "That failed: " + var22.getMessage();
                                error = true;
                            }

                            if (output.length() > 12000) {
                                output = output.substring(0, 12000) + " ... (cut)";
                            }

                            msgs.add(LlmClient.Msg.tool(call.id(), output, error));
                        }
                    }

                    this.lastProvider = p.name();
                    return new AiAgent.Reply(reply.isEmpty() ? "Done." : reply.toString().trim(), changes, true);
                } catch (LlmClient.Failure f) {
                    why.add(p.name() + ": " + (f.status == 401 || f.status == 403 ? "the key was refused" : (f.status == 429 ? "rate limited" : f.status + " " + f.getMessage())));
                } catch (java.net.ConnectException | java.net.http.HttpConnectTimeoutException ce) {
                    why.add(p.name() + ": not reachable" + (p.local() ? " (is it running?)" : ""));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    why.add(p.name() + ": interrupted");
                    break;
                } catch (IOException | RuntimeException e) {
                    this.plugin.getLogger().log(Level.WARNING, "AI request to " + p.name() + " failed", e);
                    why.add(p.name() + ": " + e.getMessage());
                }

                if (!changes.isEmpty()) {
                    // something was already changed: don't run the request again on another provider
                    return new AiAgent.Reply((reply.isEmpty() ? "" : reply + "\n") + "(stopped: " + String.join("; ", why) + ")", changes, false);
                }

                // the next provider starts this request again from the admin's message
                while (msgs.size() > start + 1) {
                    msgs.remove(msgs.size() - 1);
                }

                reply.setLength(0);
            }

            while (msgs.size() > start) {
                msgs.remove(msgs.size() - 1);
            }

            String hint = this.hasKey() ? "" : " Add a key with /ai key <key> (Anthropic, OpenAI, Gemini, OpenRouter, Groq, Mistral, DeepSeek, xAI...) or run a local model (Ollama).";
            return new AiAgent.Reply("The AI couldn't answer: " + String.join("; ", why) + "." + hint, changes, false);
        }
    }

    /** The model for a provider: ai.model when it suits that provider, else the provider's default. */
    private String model(LlmClient.Provider p, boolean first) {
        String m = Cfg.s(Opt.AI_MODEL).trim();
        // ai.model is for the first provider tried (the chosen one); the ones after it use their own default
        if (m.isEmpty() || !first || !p.anthropic() && m.startsWith("claude-") || p.anthropic() && !m.startsWith("claude")) {
            return p.model();
        }

        return m;
    }

    public record Reply(String text, List<String> changes, boolean ok) {
    }
}
