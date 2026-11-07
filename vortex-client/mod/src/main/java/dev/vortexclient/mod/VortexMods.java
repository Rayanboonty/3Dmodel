package dev.vortexclient.mod;

import java.util.Locale;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Vortex in-game mods: HUD elements, PvP tools and the mod menu. */
public class VortexMods implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("vortex");
    public static Config config = new Config();
    public static KeyBinding menuKey;
    public static KeyBinding zoomKey;

    public static boolean zooming;
    private static double zoomLevel = 4;
    private static float zoomAmount = 1f;
    private static long lastZoomFrame = System.nanoTime();
    private static boolean savedSmooth;

    private static Integer accent;

    @Override
    public void onInitializeClient() {
        config = Config.load();
        menuKey = KeyBindingHelper.registerKeyBinding(Compat.keyBinding("key.vortex.menu", GLFW.GLFW_KEY_RIGHT_SHIFT));
        zoomKey = KeyBindingHelper.registerKeyBinding(Compat.keyBinding("key.vortex.zoom", GLFW.GLFW_KEY_C));
        freelookKey = KeyBindingHelper.registerKeyBinding(Compat.keyBinding("key.vortex.freelook", GLFW.GLFW_KEY_LEFT_ALT));
        snapKey = KeyBindingHelper.registerKeyBinding(Compat.keyBinding("key.vortex.snaplook", GLFW.GLFW_KEY_V));
        NetClient.init();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            if (hintShown) return;
            hintShown = true;
            SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal("Vortex Client"), Text.literal("Press Right Shift for the mod menu"));
        }));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> { sessionStart = System.currentTimeMillis(); deathPos = null; });
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (Compat.isClient(world)) hitParticles(entity);
            return net.minecraft.util.ActionResult.PASS;
        });
        HudRenderCallback.EVENT.register((ctx, tick) -> Hud.render(ctx));
        ClientTickEvents.END_CLIENT_TICK.register(VortexMods::tick);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> applyToggles());
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Vortex Mods"), b -> client.setScreen(new ModMenuScreen(screen)))
                        .dimensions(w - 92, 6, 86, 20)
                        .build());
            }
        });
        LOG.info("Vortex mods ready: Right Shift opens the menu");
    }

    /** -Dvortex.bench=true logs the frame rate once a second in a world (for FPS tests). */
    private static final boolean BENCH = Boolean.getBoolean("vortex.bench");
    private static int benchTicks;

    private static void tick(MinecraftClient client) {
        if (BENCH && client.world != null && ++benchTicks % 20 == 0) LOG.info("Vortex bench fps={}", client.getCurrentFps());
        Stats.tick(client);
        while (menuKey.wasPressed()) {
            if (client.currentScreen == null) client.setScreen(new ModMenuScreen(null));
        }
        Module zoom = Module.get("zoom");
        boolean zoomHeld = zoomKey.isPressed();
        if (config.bool(zoom, "toggle")) {
            if (zoomHeld && !zoomWasHeld) zoomToggled = !zoomToggled;
            zoomHeld = zoomToggled;
        }
        zoomWasHeld = zoomKey.isPressed();
        boolean want = config.enabled(zoom) && zoomHeld && client.currentScreen == null;
        if (want && !zooming) {
            zoomLevel = config.num(zoom, "level");
            if (config.bool(zoom, "smooth")) {
                savedSmooth = client.options.smoothCameraEnabled;
                client.options.smoothCameraEnabled = true;
            }
        } else if (!want && zooming && config.bool(zoom, "smooth")) {
            client.options.smoothCameraEnabled = savedSmooth;
        }
        zooming = want;

        Module fl = Module.get("freelook");
        boolean flHeld = freelookKey.isPressed();
        if (config.bool(fl, "toggle")) {
            if (flHeld && !flWasHeld) flToggled = !flToggled;
            flHeld = flToggled;
        }
        flWasHeld = freelookKey.isPressed();
        boolean free = config.enabled(fl) && flHeld && client.currentScreen == null && client.player != null;
        if (free && !freelook) {
            freeYaw = client.player.getYaw();
            freePitch = client.player.getPitch();
            savedPerspective = client.options.getPerspective();
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            freelook = true;
        } else if (!free && freelook) {
            freelook = false;
            if (savedPerspective != null) client.options.setPerspective(savedPerspective);
        }

        // Snaplook: hold to see yourself from the front.
        boolean snap = config.enabled(Module.get("snaplook")) && snapKey.isPressed() && client.currentScreen == null && client.player != null && !freelook;
        if (snap && !snapping) {
            snapSaved = client.options.getPerspective();
            client.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            snapping = true;
        } else if (!snap && snapping) {
            snapping = false;
            if (snapSaved != null) client.options.setPerspective(snapSaved);
        }

        // Death coordinates: remember where you died.
        if (client.player != null) {
            boolean dead = client.player.isDead() || client.player.getHealth() <= 0;
            if (dead && !wasDead) {
                deathPos = client.player.getBlockPos();
                Module dp = Module.get("deathpos");
                if (config.enabled(dp) && config.bool(dp, "chat")) {
                    client.inGameHud.getChatHud().addMessage(Text.literal("[Vortex] You died at " + deathPos.getX() + ", " + deathPos.getY() + ", " + deathPos.getZ()).formatted(Formatting.GOLD));
                }
            }
            wasDead = dead;
            // Back at the spot (items picked up): the reminder goes.
            if (deathPos != null && !dead && client.player.getBlockPos().getSquaredDistance(deathPos) < 9) deathPos = null;
        } else {
            wasDead = false;
        }
    }

    private static boolean zoomWasHeld, zoomToggled, flWasHeld, flToggled, snapping, wasDead;
    private static Perspective snapSaved;
    public static KeyBinding snapKey;
    /** Where you last died (Death Coordinates). */
    public static net.minecraft.util.math.BlockPos deathPos;
    private static long lastGg;

    /** Auto GG: the end of a game on a minigame server. */
    static void watchChat(Text message) {
        Module m = Module.get("autogg");
        if (!config.enabled(m)) return;
        String s = message.getString();
        boolean over = s.contains("1st Killer") || s.contains("Winner:") || s.contains("Winners:") || s.contains("won the game") || s.contains("VICTORY!") || s.contains("#1 Victory Royale") || s.contains("Game Over!") || s.contains("WINNER!");
        if (!over || System.currentTimeMillis() - lastGg < 20000) return;
        lastGg = System.currentTimeMillis();
        MinecraftClient mc = MinecraftClient.getInstance();
        String msg = config.str(m, "msg");
        // A moment later, like a person would.
        new Thread(() -> {
            try { Thread.sleep(900); } catch (InterruptedException ignored) {}
            mc.execute(() -> { if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendChatMessage(msg); });
        }, "vortex-autogg").start();
    }

    // Freelook state, read by the camera and entity mixins.
    public static boolean freelook;
    public static float freeYaw, freePitch;
    private static Perspective savedPerspective;
    public static KeyBinding freelookKey;
    private static boolean hintShown;

    /** When the player joined the current world or server. */
    public static long sessionStart = System.currentTimeMillis();

    public static boolean on(String id) {
        return config.enabled(Module.get(id));
    }

    /** Block Outline colour (ARGB), or the game's own. */
    public static int outlineColor(int vanilla) {
        Module m = Module.get("blockoutline");
        if (!config.enabled(m)) return vanilla;
        int rgb = switch (config.str(m, "color")) {
            case "White" -> 0xFFFFFF;
            case "Black" -> 0x000000;
            case "Red" -> 0xFF4040;
            case "Aqua" -> 0x40E0FF;
            case "Green" -> 0x55FF55;
            case "Rainbow" -> java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 4000L) / 4000f, 0.75f, 1f) & 0xFFFFFF;
            default -> accent() & 0xFFFFFF;
        };
        int a = Math.round((float) config.num(m, "opacity") * 255f);
        return (Math.max(0, Math.min(255, a)) << 24) | rgb;
    }

    /** Hit Particles: extra crit and sharpness sparks on the entity you hit. */
    private static void hitParticles(net.minecraft.entity.Entity target) {
        Module m = Module.get("particles");
        if (!config.enabled(m)) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        int n = (int) Math.round(config.num(m, "amount"));
        for (int i = 0; i < n; i++) {
            if (config.bool(m, "crits")) mc.particleManager.addEmitter(target, net.minecraft.particle.ParticleTypes.CRIT);
            if (config.bool(m, "sharp")) mc.particleManager.addEmitter(target, net.minecraft.particle.ParticleTypes.ENCHANTED_HIT);
        }
    }

    public static boolean clearWeather() {
        return config.enabled(Module.get("weather"));
    }

    public static boolean lowFire() {
        return config.enabled(Module.get("lowfire"));
    }

    /** The time of day the Time Changer shows, or -1. */
    public static long timeOverride() {
        Module m = Module.get("timechanger");
        if (!config.enabled(m)) return -1;
        return switch (config.str(m, "time")) {
            case "Sunrise" -> 23200L;
            case "Sunset" -> 12500L;
            case "Night" -> 18000L;
            default -> 6000L;
        };
    }

    public static Text stampChat(Text message) {
        watchChat(message);
        Module m = Module.get("timestamps");
        if (!config.enabled(m)) return message;
        boolean sec = config.bool(m, "seconds");
        String f = config.bool(m, "h24") ? (sec ? "HH:mm:ss" : "HH:mm") : (sec ? "h:mm:ss" : "h:mm");
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern(f, Locale.ROOT));
        return Text.literal("[" + time + "] ").formatted(Formatting.GRAY).append(message);
    }

    /** Applies modules that change vanilla options (toggle sprint and sneak). */
    public static void applyToggles() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.options == null) return;
        Module sprint = Module.get("sprint");
        boolean on = config.enabled(sprint);
        boolean sneak = on && config.bool(sprint, "sneak");
        if (on) {
            if (config.savedSprintToggle == null) config.savedSprintToggle = mc.options.getSprintToggled().getValue();
            mc.options.getSprintToggled().setValue(true);
        } else if (config.savedSprintToggle != null) {
            mc.options.getSprintToggled().setValue(config.savedSprintToggle);
            config.savedSprintToggle = null;
        }
        if (sneak) {
            if (config.savedSneakToggle == null) config.savedSneakToggle = mc.options.getSneakToggled().getValue();
            mc.options.getSneakToggled().setValue(true);
        } else if (config.savedSneakToggle != null) {
            mc.options.getSneakToggled().setValue(config.savedSneakToggle);
            config.savedSneakToggle = null;
        }
        config.save();
    }

    /** Called from the FOV hook once per frame. */
    public static float applyZoom(float fov) {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastZoomFrame) / 1e9f);
        lastZoomFrame = now;
        float target = zooming ? (float) (1.0 / zoomLevel) : 1f;
        zoomAmount = Ui.approach(zoomAmount, target, 16f, dt);
        if (Math.abs(zoomAmount - 1f) < 0.001f) zoomAmount = 1f;
        return fov * zoomAmount;
    }

    public static void scrollZoom(double amount) {
        zoomLevel = Math.max(1.5, Math.min(30, zoomLevel * (amount > 0 ? 1.15 : 1 / 1.15)));
    }

    public static boolean scrollZooms() {
        return zooming && config.bool(Module.get("zoom"), "scroll");
    }

    public static boolean fullbright() {
        return config.enabled(Module.get("fullbright"));
    }

    public static boolean noHurtCam() {
        return config.enabled(Module.get("nohurtcam"));
    }

    /** The accent colour chosen in the launcher. */
    public static int accent() {
        if (accent == null) {
            accent = switch (System.getProperty("vortex.accent", "amber").toLowerCase(Locale.ROOT)) {
                case "diamond" -> 0xFF4FD8EB;
                case "emerald" -> 0xFF45D46B;
                case "amethyst" -> 0xFFB58CFF;
                case "redstone" -> 0xFFFF5A4F;
                case "copper" -> 0xFFE5895B;
                default -> 0xFFFFB547;
            };
        }
        return accent;
    }
}
