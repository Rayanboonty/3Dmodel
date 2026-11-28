package net.vortexrealm.ai;

import java.io.IOException;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.vortexrealm.VortexRealm;
import net.vortexrealm.util.Tasks;
import net.vortexrealm.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class AiCommand implements TabExecutor {
    private final VortexRealm plugin;
    private final AiAgent ai;

    public AiCommand(VortexRealm plugin, AiAgent ai) {
        this.plugin = plugin;
        this.ai = ai;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vortex.admin")) {
            sender.sendMessage(Text.mm("<red>You don't have permission."));
            return true;
        } else if (args.length == 0) {
            sender.sendMessage(
                Text.mm(
                    "<#FF8A3D><b>AI Assistant</b> <gray>"
                        + (this.ai.hasKey() ? "<green>ready" : "<yellow>no API key")
                        + "<dark_gray> • <gray>/ai <what you want> <dark_gray>• <gray>/ai reset <dark_gray>• <gray>/ai key [provider] <key> <dark_gray>• <gray>/ai providers"
                )
            );
            sender.sendMessage(Text.mm("<gray>Using: <white>").append(Component.text(this.ai.status())));
            sender.sendMessage(
                Text.mm(
                    "<gray>Examples: <white>/ai turn off FFA for every kit<gray>, <white>/ai give VIP the lobby fly permission<gray>, <white>/ai make matches first to 3 rounds"
                )
            );
            return true;
        } else {
            String session = sender instanceof Player p ? "game:" + p.getUniqueId() : "console";
            if (args[0].equalsIgnoreCase("setup")) {
                this.setup(sender, args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "");
                return true;
            } else if (args[0].equalsIgnoreCase("mode")) {
                if (!sender.isOp() && sender instanceof Player) {
                    sender.sendMessage(Text.mm("<red>Only operators can change the AI mode."));
                } else if (args.length < 2 || !List.of("online", "local", "both").contains(args[1].toLowerCase(java.util.Locale.ROOT))) {
                    sender.sendMessage(Text.mm("<gray>AI mode: <white>" + net.vortexrealm.config.Cfg.s(net.vortexrealm.config.Opt.AI_MODE) + " <dark_gray>• <gray>/ai mode online|local|both"));
                } else {
                    net.vortexrealm.config.Cfg.set(net.vortexrealm.config.Opt.AI_MODE, args[1].toLowerCase(java.util.Locale.ROOT));
                    sender.sendMessage(Text.mm("<green>AI mode set to <white>" + args[1].toLowerCase(java.util.Locale.ROOT) + "<green>. <gray>Now using: <white>" + Text.esc(this.ai.status())));
                }

                return true;
            } else if (args[0].equalsIgnoreCase("providers")) {
                for (LlmClient.Provider p : LlmClient.PROVIDERS) {
                    boolean has = p.local() || !this.ai.key(p).isEmpty();
                    sender.sendMessage(Text.mm((has ? "<green>● " : "<dark_gray>○ ") + "<white>" + p.id() + " <gray>- " + Text.esc(p.name())
                        + (p.local() ? " <dark_gray>(local, no key)" : (has ? " <green>key set" : (p.env().isEmpty() ? "" : " <dark_gray>(key or $" + p.env() + ")")))));
                }

                sender.sendMessage(Text.mm("<gray>Pick one with <white>ai.provider</white> in Settings (auto = every one with a key, in turn)."));
                return true;
            } else if (args[0].equalsIgnoreCase("key")) {
                if (sender instanceof Player && !sender.isOp()) {
                    sender.sendMessage(Text.mm("<red>Only operators can set API keys."));
                    return true;
                } else {
                    try {
                        // /ai key <key>, /ai key <provider> <key>, /ai key <provider> (removes it), /ai key (removes all)
                        String provider = args.length > 2 || args.length == 2 && LlmClient.provider(args[1]) != null ? args[1] : null;
                        String key = args.length > 2 ? args[2] : (args.length == 2 && provider == null ? args[1] : "");
                        String saved = this.ai.setKey(provider, key);
                        if (saved == null) {
                            sender.sendMessage(Text.mm("<red>Couldn't tell which provider that key is for. <gray>Use <white>/ai key \\<provider> \\<key></white> (see /ai providers)."));
                        } else if (key.isEmpty()) {
                            sender.sendMessage(Text.mm("<yellow>Removed the key" + (saved.equals("all") ? "s." : " of " + Text.esc(saved) + ".")));
                        } else {
                            sender.sendMessage(Text.mm("<green>" + Text.esc(saved) + " key saved. <gray>Try <white>/ai hello"));
                            if (sender instanceof Player) {
                                sender.sendMessage(Text.mm("<gray>Tip: setting it from the console keeps it out of your chat history."));
                            }
                        }
                    } catch (IOException var8) {
                        sender.sendMessage(Text.mm("<red>Couldn't save the key: " + Text.esc(var8.getMessage())));
                    }

                    return true;
                }
            } else if (args[0].equalsIgnoreCase("reset")) {
                this.ai.reset(session);
                sender.sendMessage(Text.mm("<gray>New conversation."));
                return true;
            } else {
                String text = String.join(" ", args);
                boolean op = sender.isOp() || sender instanceof ConsoleCommandSender;
                sender.sendMessage(Text.mm("<dark_gray>[<#FF8A3D>AI</#FF8A3D>] <gray><i>thinking..."));
                Tasks.async(() -> {
                    AiAgent.Reply r = this.ai.chat(session, sender.getName(), op, text);
                    Tasks.sync(() -> {
                        for (String line : r.text().split("\n")) {
                            if (!line.isBlank()) {
                                sender.sendMessage(Text.mm("<dark_gray>[<#FF8A3D>AI</#FF8A3D>]</dark_gray> <white>").append((Component)Component.text(line)));
                            }
                        }

                        if (!r.changes().isEmpty()) {
                            sender.sendMessage(Text.mm("<gray>Changed:"));

                            for (String c : r.changes()) {
                                sender.sendMessage(Text.mm(" <dark_gray>» <#FFB547>").append((Component)Component.text(c)));
                            }

                            sender.sendMessage(Text.mm("<dark_gray>(a full backup was saved first - /pa fullrestore latest puts everything back)"));
                        }
                    });
                });
                return true;
            }
        }
    }

    /** /ai setup [local|online]: how to get the assistant running, with a key or with a free model on this machine. */
    private void setup(CommandSender s, String what) {
        String mode = net.vortexrealm.config.Cfg.s(net.vortexrealm.config.Opt.AI_MODE);
        s.sendMessage(Text.mm("<#FF8A3D><b>AI Setup</b> <dark_gray>• <gray>mode <white>" + Text.esc(mode) + " <dark_gray>• <gray>" + Text.esc(this.ai.status())));
        if (!what.equals("online")) {
            s.sendMessage(Text.mm("<gold><b>Local (free, no key)</b> <gray>- runs on your own PC or VPS:"));
            s.sendMessage(Text.mm("<gray>1. Install Ollama: <white>https://ollama.com/download</white> <dark_gray>(Linux: curl -fsSL https://ollama.com/install.sh | sh)"));
            s.sendMessage(Text.mm("<gray>2. Get a model with tool use: <white>ollama pull qwen2.5:7b</white> <dark_gray>(small PC: qwen2.5:3b, strong PC: qwen2.5:14b or llama3.1:8b)"));
            s.sendMessage(Text.mm("<gray>3. Same machine as the server: nothing else. Another machine: start it with <white>OLLAMA_HOST=0.0.0.0 ollama serve</white>"));
            s.sendMessage(Text.mm("<gray>   and set <white>ai.base-url</white> to <white>http://<that-ip>:11434/v1</white> (open port 11434 to the server only)."));
            s.sendMessage(Text.mm("<gray>4. Set <white>ai.model</white> to the model you pulled, then <white>/ai mode local</white> <gray>(or <white>both</white>)."));
            s.sendMessage(Text.mm("<dark_gray>Game hosts (like Bao Host) can't run Ollama inside the server: run it on your own PC or a VPS and use its address."));
        }

        if (!what.equals("local")) {
            s.sendMessage(Text.mm("<gold><b>Online (API key)</b> <gray>- best answers:"));
            s.sendMessage(Text.mm("<gray>1. Get a key from Anthropic, OpenAI, Gemini, OpenRouter, Groq... <dark_gray>(/ai providers)"));
            s.sendMessage(Text.mm("<gray>2. In the console: <white>/ai key <the key></white> <dark_gray>(the provider is told from the key)"));
            s.sendMessage(Text.mm("<gray>3. <white>/ai mode online</white> <gray>or <white>both</white> <gray>(online first, local when it fails)."));
        }

        s.sendMessage(Text.mm("<gray>Try: <white>/ai make a kit like sword but with knockback 150% and 2 golden apples"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("reset", "key", "providers", "setup", "mode");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            return List.of("online", "local", "both");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("setup")) {
            return List.of("local", "online");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("key")) {
            List<String> ids = new java.util.ArrayList<>();
            for (LlmClient.Provider p : LlmClient.PROVIDERS) {
                if (!p.local()) {
                    ids.add(p.id());
                }
            }

            return ids;
        } else {
            return List.of();
        }
    }
}
