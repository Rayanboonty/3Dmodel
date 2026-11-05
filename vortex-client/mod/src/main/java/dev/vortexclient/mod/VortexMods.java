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
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.vortex.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.vortex"));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.vortex.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "category.vortex"));
        freelookKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.vortex.freelook", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "category.vortex"));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            if (hintShown) return;
            hintShown = true;
            SystemToast.add(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION,
                    Text.literal("Vortex Client"), Text.literal("Press Right Shift for the mod menu"));
        }));

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

    private static void tick(MinecraftClient client) {
        Stats.tick(client);
        while (menuKey.wasPressed()) {
            if (client.currentScreen == null) client.setScreen(new ModMenuScreen(null));
        }
        Module zoom = Module.get("zoom");
        boolean want = config.enabled(zoom) && zoomKey.isPressed() && client.currentScreen == null;
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
        boolean free = config.enabled(fl) && freelookKey.isPressed() && client.currentScreen == null && client.player != null;
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
    }

    // Freelook state, read by the camera and entity mixins.
    public static boolean freelook;
    public static float freeYaw, freePitch;
    private static Perspective savedPerspective;
    public static KeyBinding freelookKey;
    private static boolean hintShown;

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
        Module m = Module.get("timestamps");
        if (!config.enabled(m)) return message;
        String f = config.bool(m, "h24") ? "HH:mm" : "h:mm";
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
