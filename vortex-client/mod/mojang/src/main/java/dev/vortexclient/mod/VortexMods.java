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
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Vortex in-game mods: HUD elements, PvP tools and the mod menu. */
public class VortexMods implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("vortex");
    public static Config config = new Config();
    public static KeyMapping menuKey;
    public static KeyMapping zoomKey;

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
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            if (hintShown) return;
            hintShown = true;
            SystemToast.add(client.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("Vortex Client"), Component.literal("Press Right Shift for the mod menu"));
        }));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> sessionStart = System.currentTimeMillis());
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (Compat.isClient(world)) hitParticles(entity);
            return net.minecraft.world.InteractionResult.PASS;
        });
        HudRenderCallback.EVENT.register((ctx, tick) -> Hud.render(ctx));
        ClientTickEvents.END_CLIENT_TICK.register(VortexMods::tick);
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> applyToggles());
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            if (screen instanceof TitleScreen) {
                Screens.getButtons(screen).add(Button.builder(Component.literal("Vortex Mods"), b -> client.setScreen(new ModMenuScreen(screen)))
                        .bounds(w - 92, 6, 86, 20)
                        .build());
            }
        });
        LOG.info("Vortex mods ready: Right Shift opens the menu");
    }

    private static void tick(Minecraft client) {
        Stats.tick(client);
        while (menuKey.consumeClick()) {
            if (client.screen == null) client.setScreen(new ModMenuScreen(null));
        }
        Module zoom = Module.get("zoom");
        boolean want = config.enabled(zoom) && zoomKey.isDown() && client.screen == null;
        if (want && !zooming) {
            zoomLevel = config.num(zoom, "level");
            if (config.bool(zoom, "smooth")) {
                savedSmooth = client.options.smoothCamera;
                client.options.smoothCamera = true;
            }
        } else if (!want && zooming && config.bool(zoom, "smooth")) {
            client.options.smoothCamera = savedSmooth;
        }
        zooming = want;

        Module fl = Module.get("freelook");
        boolean free = config.enabled(fl) && freelookKey.isDown() && client.screen == null && client.player != null;
        if (free && !freelook) {
            freeYaw = client.player.getYRot();
            freePitch = client.player.getXRot();
            savedPerspective = client.options.getCameraType();
            client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            freelook = true;
        } else if (!free && freelook) {
            freelook = false;
            if (savedPerspective != null) client.options.setCameraType(savedPerspective);
        }
    }

    // Freelook state, read by the camera and entity mixins.
    public static boolean freelook;
    public static float freeYaw, freePitch;
    private static CameraType savedPerspective;
    public static KeyMapping freelookKey;
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
    private static void hitParticles(net.minecraft.world.entity.Entity target) {
        Module m = Module.get("particles");
        if (!config.enabled(m)) return;
        Minecraft mc = Minecraft.getInstance();
        int n = (int) Math.round(config.num(m, "amount"));
        for (int i = 0; i < n; i++) {
            if (config.bool(m, "crits")) mc.particleEngine.createTrackingEmitter(target, net.minecraft.core.particles.ParticleTypes.CRIT);
            if (config.bool(m, "sharp")) mc.particleEngine.createTrackingEmitter(target, net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT);
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

    public static Component stampChat(Component message) {
        Module m = Module.get("timestamps");
        if (!config.enabled(m)) return message;
        String f = config.bool(m, "h24") ? "HH:mm" : "h:mm";
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern(f, Locale.ROOT));
        return Component.literal("[" + time + "] ").withStyle(ChatFormatting.GRAY).append(message);
    }

    /** Applies modules that change vanilla options (toggle sprint and sneak). */
    public static void applyToggles() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return;
        Module sprint = Module.get("sprint");
        boolean on = config.enabled(sprint);
        boolean sneak = on && config.bool(sprint, "sneak");
        if (on) {
            if (config.savedSprintToggle == null) config.savedSprintToggle = mc.options.toggleSprint().get();
            mc.options.toggleSprint().set(true);
        } else if (config.savedSprintToggle != null) {
            mc.options.toggleSprint().set(config.savedSprintToggle);
            config.savedSprintToggle = null;
        }
        if (sneak) {
            if (config.savedSneakToggle == null) config.savedSneakToggle = mc.options.toggleCrouch().get();
            mc.options.toggleCrouch().set(true);
        } else if (config.savedSneakToggle != null) {
            mc.options.toggleCrouch().set(config.savedSneakToggle);
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
