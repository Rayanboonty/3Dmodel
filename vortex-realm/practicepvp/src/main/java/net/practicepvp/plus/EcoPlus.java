package net.practicepvp.plus;

import java.util.UUID;
import net.practicepvp.PracticePlugin;
import net.practicepvp.util.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * The Eco SMP extras (crates, chest shops, bid house, daily deal, world events, eco wipe) needed the old Eco SMP mode,
 * which is gone; this stands in so the other systems keep working. Every call says so instead of doing anything.
 */
public final class EcoPlus implements Listener {
    private static final String GONE = "<gray>The Eco SMP extras are not part of this version.";

    public EcoPlus(PracticePlugin plugin, PlusModule plus) {
    }

    public void giveKeys(UUID u, String type, long n) {
    }

    public void crates(Player p) {
        p.sendMessage(Text.mm(GONE));
    }

    public void deliverPending() {
    }

    public void bidCommand(Player p, String[] a) {
        p.sendMessage(Text.mm(GONE));
    }

    public void bidTick() {
    }

    public int dealPrice() {
        return 0;
    }

    public void dealMenu(Player p) {
        p.sendMessage(Text.mm(GONE));
    }

    public String startEvent(String type) {
        return "The Eco SMP extras are not part of this version.";
    }

    public void second() {
    }

    public void wipe(CommandSender s, String[] a) {
        s.sendMessage(Text.mm(GONE));
    }

    public void cleanup() {
    }

    public String selfCheck() {
        return null;
    }
}
