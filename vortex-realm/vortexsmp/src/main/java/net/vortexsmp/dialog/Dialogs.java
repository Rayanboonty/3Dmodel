package net.vortexsmp.dialog;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.ActionButton.Builder;
import io.papermc.paper.registry.data.dialog.DialogBase.DialogAfterAction;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput.OptionEntry;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback.Options;
import net.vortexsmp.VortexSMP;
import net.vortexsmp.util.ClientVersion;
import net.vortexsmp.util.Tasks;
import net.vortexsmp.util.Text;
import org.bukkit.entity.Player;

/** The native dialog screens (1.21.6+ clients): buttons, text input, choices and confirmations. */
public final class Dialogs {
    static final Options OPTS = Options.builder().uses(-1).lifetime(Duration.ofMinutes(10L)).build();
    private static final Options ONCE = Options.builder().uses(1).lifetime(Duration.ofMinutes(10L)).build();
    private final VortexSMP plugin;
    private final LobbyDialogs lobby;

    public Dialogs(VortexSMP plugin) {
        this.plugin = plugin;
        this.lobby = new LobbyDialogs(this);
    }

    public LobbyDialogs lobby() {
        return this.lobby;
    }

    public Runnable safe(Player p, Runnable r) {
        return () -> Tasks.ensureSync(() -> {
            if (p.isOnline()) {
                try {
                    r.run();
                } catch (RuntimeException var4) {
                    this.plugin.getLogger().log(Level.SEVERE, "Dialog action failed for " + p.getName(), (Throwable)var4);
                    p.closeDialog();
                }
            }
        });
    }

    public DialogAction action(Player p, Runnable r) {
        Runnable s = this.safe(p, r);
        return DialogAction.customClick((view, audience) -> s.run(), OPTS);
    }

    private DialogAction formAction(Player p, Consumer<DialogResponseView> c) {
        return DialogAction.customClick((view, audience) -> {
            Runnable s = this.safe(p, () -> c.accept(view));
            s.run();
        }, ONCE);
    }

    public static ActionButton button(Component label, Component tooltip, int width, DialogAction action) {
        Builder b = ActionButton.builder(label).width(width);
        if (tooltip != null) {
            b.tooltip(tooltip);
        }

        if (action != null) {
            b.action(action);
        }

        return b.build();
    }

    ActionButton closeButton(Player p) {
        return button(Text.mm("<red>Close"), null, 200, this.action(p, p::closeDialog));
    }

    public static void show(Player p, DialogBase base, DialogType type) {
        Dialog d = Dialog.create(b -> b.empty().base(base).type(type));
        p.showDialog(d);
    }

    public boolean useDialog(Player p) {
        return ClientVersion.supportsDialogs(p);
    }

    private boolean formsSupported(Player p) {
        if (ClientVersion.supportsDialogs(p)) {
            return true;
        } else {
            p.sendMessage(Text.mm("<red>Your Minecraft version is too old for this screen. <gray>Use the command instead."));
            return false;
        }
    }

    public void text(Player p, Component title, String label, String initial, int maxLength, Consumer<String> done) {
        if (this.formsSupported(p)) {
            DialogInput input = DialogInput.text("value", Text.mm("<gray>" + Text.esc(label)))
                .initial(initial == null ? "" : initial)
                .maxLength(Math.max(1, maxLength))
                .width(260)
                .build();
            ActionButton ok = button(Text.mm("<green><b>Save"), null, 120, this.formAction(p, view -> {
                String v = view.getText("value");
                p.closeDialog();
                done.accept(v == null ? "" : v.trim());
            }));
            ActionButton cancel = button(Text.mm("<red>Cancel"), null, 120, this.action(p, p::closeDialog));
            DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.NONE).inputs(List.of(input)).build();
            show(p, base, DialogType.confirmation(ok, cancel));
        }
    }

    public void choice(Player p, Component title, String label, List<Dialogs.Choice> options, String initial, Consumer<String> done) {
        if (this.formsSupported(p) && !options.isEmpty()) {
            List<OptionEntry> entries = new ArrayList<>();

            for (Dialogs.Choice c : options) {
                entries.add(OptionEntry.create(c.id(), Text.mm(c.label()), c.id().equals(initial)));
            }

            DialogInput input = DialogInput.singleOption("value", Text.mm("<gray>" + Text.esc(label)), entries).width(260).build();
            ActionButton ok = button(Text.mm("<green><b>Save"), null, 120, this.formAction(p, view -> {
                String v = view.getText("value");
                p.closeDialog();
                if (v != null) {
                    done.accept(v);
                }
            }));
            ActionButton cancel = button(Text.mm("<red>Cancel"), null, 120, this.action(p, p::closeDialog));
            DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.NONE).inputs(List.of(input)).build();
            show(p, base, DialogType.confirmation(ok, cancel));
        }
    }

    public void confirm(Player p, Component title, List<Component> lines, Runnable yes) {
        if (this.formsSupported(p)) {
            List<DialogBody> body = new ArrayList<>();

            for (Component c : lines) {
                body.add(DialogBody.plainMessage(c, 260));
            }

            ActionButton ok = button(Text.mm("<green><b>Confirm"), null, 120, this.action(p, () -> {
                p.closeDialog();
                yes.run();
            }));
            ActionButton cancel = button(Text.mm("<red>Cancel"), null, 120, this.action(p, p::closeDialog));
            DialogBase base = DialogBase.builder(title).canCloseWithEscape(true).pause(false).afterAction(DialogAfterAction.NONE).body(body).build();
            show(p, base, DialogType.confirmation(ok, cancel));
        }
    }

    public record Choice(String id, String label) {
    }
}
