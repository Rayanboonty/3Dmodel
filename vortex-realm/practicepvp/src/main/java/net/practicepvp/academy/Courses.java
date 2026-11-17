package net.practicepvp.academy;

import java.util.List;
import net.practicepvp.bot.DummyType;
import org.bukkit.Material;

/** Every course of the Academy: one per gamemode, each a list of lessons (one tip or skill each). */
public final class Courses {
    private Courses() {
    }

    /** What the demo actor does in front of the camera. */
    public enum Demo {
        CRIT,
        WTAP,
        STRAFE,
        BLOCK,
        SHIELD_BREAK,
        WIND_SMASH,
        STUN_SLAM,
        DENSITY,
        ELYTRA_DIVE,
        OBSIDIAN,
        CRYSTAL,
        ANCHOR,
        TOTEM,
        POTS,
        GAPPLE,
        BOW,
        TUNNEL,
        SONAR,
        AMBUSH
    }

    /** What the player must do in the practice part. */
    public enum Goal {
        HITS("land {n} hits"),
        CRITS("land {n} critical hits"),
        COMBO("reach a {n}-hit combo"),
        SHIELD_BREAKS("break the shield {n} times"),
        SMASH("smash for {n}+ damage"),
        POPS("pop {n} totems"),
        CRYSTAL_DAMAGE("deal {n} damage with crystals"),
        ARROWS("hit the dummy with {n} arrows"),
        POTS("throw {n} healing potions"),
        BLOCKS("dig {n} blocks");

        public final String text;

        Goal(String text) {
            this.text = text;
        }

        public String describe(int n) {
            return this.text.replace("{n}", String.valueOf(n));
        }
    }

    /** One lesson: what is shown (title, tips in order), the demo, and the practice goal. */
    public record Lesson(String id, String title, List<String> tips, Demo demo, DummyType dummy, Goal goal, int amount, int seconds) {
    }

    public record Course(String id, String name, String color, Material icon, String kit, List<Lesson> lessons) {
    }

    public static final List<Course> ALL = List.of(
        new Course("sword", "Sword", "<#7FD3FF>", Material.DIAMOND_SWORD, "sword", List.of(
            new Lesson("sword.crit", "Critical hits", List.of(
                "Jump, and hit while you are FALLING.",
                "A crit does 50% more damage - look for the star particles.",
                "Don't sprint into the jump: sprint-jumping cancels crits.",
                "Time it so the hit lands just before you touch the ground."
            ), Demo.CRIT, DummyType.COMBO, Goal.CRITS, 5, 60),
            new Lesson("sword.wtap", "W-tapping", List.of(
                "After each hit, let go of W for a moment, then press it again.",
                "This resets your sprint: the next hit knocks back far again.",
                "Keep the rhythm: hit - release - press - hit.",
                "Long combos come from W-taps, not from clicking faster."
            ), Demo.WTAP, DummyType.COMBO, Goal.COMBO, 4, 60),
            new Lesson("sword.strafe", "Strafing", List.of(
                "Move sideways (A / D) while you fight, never in a straight line.",
                "Strafing makes you harder to hit and opens angles on them.",
                "Change direction when they turn toward you."
            ), Demo.STRAFE, DummyType.COMBO, Goal.HITS, 15, 60),
            new Lesson("sword.block", "Shield timing", List.of(
                "Raise the shield while YOUR weapon recharges.",
                "Lower it just before you hit - a raised shield can't attack.",
                "Watch for axes: an axe hit breaks a raised shield for 5 seconds."
            ), Demo.BLOCK, DummyType.COMBO, Goal.HITS, 10, 60)
        )),
        new Course("axe", "Axe & Shield", "<#C9A27E>", Material.DIAMOND_AXE, "axe", List.of(
            new Lesson("axe.break", "Breaking a shield", List.of(
                "Hit a raised shield with an AXE: it is disabled for 5 seconds.",
                "Swap to the axe only for the break, then back to your sword.",
                "The best moment: right after they raise it to block your hit."
            ), Demo.SHIELD_BREAK, DummyType.SHIELD, Goal.SHIELD_BREAKS, 3, 60),
            new Lesson("axe.stun", "The stun", List.of(
                "A broken shield = a free window: they can't block for 5s.",
                "Follow the break with your strongest hits (crits!).",
                "Count it: break, crit, crit, crit before the shield comes back."
            ), Demo.SHIELD_BREAK, DummyType.SHIELD, Goal.SHIELD_BREAKS, 4, 75)
        )),
        new Course("mace", "Mace", "<#B9B9C9>", Material.MACE, "mace", List.of(
            new Lesson("mace.wind", "Wind-charge smash", List.of(
                "Throw a wind charge at your feet to launch yourself up.",
                "Fall onto them and hit with the mace while falling.",
                "Every block of fall adds damage: 4 per block for the first 3, then 2, then 1.",
                "A smash also resets your fall - no fall damage on a hit."
            ), Demo.WIND_SMASH, DummyType.MACE, Goal.SMASH, 14, 75),
            new Lesson("mace.stunslam", "The stun slam", List.of(
                "While falling: hit with the AXE to break their shield...",
                "...then switch to the MACE and smash in the same fall.",
                "Put axe and mace next to each other on your hotbar.",
                "It is the hardest mace move: practise the swap speed."
            ), Demo.STUN_SLAM, DummyType.STUNSLAM, Goal.SHIELD_BREAKS, 2, 90),
            new Lesson("mace.density", "Density vs Breach", List.of(
                "Density: more damage per block fallen - best from very high.",
                "Breach: ignores part of the armor - best against netherite.",
                "Short hops? Breach. Big falls? Density."
            ), Demo.DENSITY, DummyType.MACE, Goal.SMASH, 18, 75)
        )),
        new Course("elytra", "Elytra Mace", "<#C8A2FF>", Material.ELYTRA, "mace", List.of(
            new Lesson("elytra.launch", "Launch and glide", List.of(
                "Jump, open the elytra (jump again in the air) and use a rocket.",
                "Climb high above the fight before you look for a target.",
                "Glide in circles to keep your height."
            ), Demo.ELYTRA_DIVE, DummyType.ELYTRA, Goal.HITS, 3, 90),
            new Lesson("elytra.dive", "The dive", List.of(
                "Look down at the target and dive: speed becomes fall damage.",
                "Switch to the mace on the way down.",
                "Hit at the last moment, then pull up or land."
            ), Demo.ELYTRA_DIVE, DummyType.MACE, Goal.SMASH, 20, 90)
        )),
        new Course("crystal", "Crystal PvP", "<#FF6BD5>", Material.END_CRYSTAL, "crystal", List.of(
            new Lesson("crystal.obsidian", "Placing obsidian", List.of(
                "Crystals only go on obsidian or bedrock.",
                "Place obsidian right next to their feet, one block down.",
                "Keep obsidian and crystals next to each other on the hotbar."
            ), Demo.OBSIDIAN, DummyType.CRYSTAL, Goal.CRYSTAL_DAMAGE, 20, 75),
            new Lesson("crystal.place", "Place and hit", List.of(
                "Place the crystal, then hit it at once: it explodes.",
                "Fast hands: place - hit - place - hit.",
                "Stand so the block is between you and the blast."
            ), Demo.CRYSTAL, DummyType.CRYSTAL, Goal.POPS, 2, 90),
            new Lesson("crystal.anchor", "Anchors", List.of(
                "A charged respawn anchor explodes outside the Nether.",
                "Place it, charge it with glowstone, use it - next to them.",
                "Anchors go through blocks crystals can't reach."
            ), Demo.ANCHOR, DummyType.CRYSTAL, Goal.CRYSTAL_DAMAGE, 30, 90),
            new Lesson("crystal.totem", "Totem swap", List.of(
                "After a totem pops, put a new one in your offhand AT ONCE.",
                "Bind 'swap hands' (F) and keep totems in your hotbar.",
                "The player who re-totems faster wins crystal fights."
            ), Demo.TOTEM, DummyType.CRYSTAL, Goal.POPS, 3, 90),
            new Lesson("crystal.dtap", "D-tap (double tap)", List.of(
                "A totem only saves them once: the second hit right after the pop kills.",
                "Place two crystals back to back - break the first, place and break the second at once.",
                "Keep your crosshair on the obsidian and click in rhythm: place, hit, place, hit."
            ), Demo.CRYSTAL, DummyType.DTAP, Goal.POPS, 4, 120)
        )),
        new Course("pot", "Potions", "<#FF5555>", Material.SPLASH_POTION, "nodebuff", List.of(
            new Lesson("pot.timing", "Pot timing", List.of(
                "Throw healing potions straight down at your feet.",
                "Pot when you are away from them - not in the middle of a combo.",
                "Two pots in a row heal more than one at a time."
            ), Demo.POTS, DummyType.COMBO, Goal.POTS, 3, 60)
        )),
        new Course("uhc", "UHC", "<#FFB547>", Material.GOLDEN_APPLE, "uhc", List.of(
            new Lesson("uhc.bow", "Bow pressure", List.of(
                "Use the bow when they run: every arrow forces them to react.",
                "Aim a little above them when they are far.",
                "Switch back to the sword when they come close."
            ), Demo.BOW, DummyType.COMBO, Goal.ARROWS, 3, 75),
            new Lesson("uhc.gapple", "Golden apples", List.of(
                "Eat a golden apple BEFORE you are low, not at 2 hearts.",
                "The absorption hearts take the next hits for you.",
                "Never eat in front of them: break away first."
            ), Demo.GAPPLE, DummyType.COMBO, Goal.HITS, 12, 60)
        )),
        new Course("mole", "Mole PvP", "<#C9A27E>", Material.IRON_PICKAXE, "mole", List.of(
            new Lesson("mole.tunnel", "Tunnelling", List.of(
                "Dig 2-high tunnels: one block wide is enough.",
                "Pickaxe for stone, shovel for dirt and gravel.",
                "Place torches so you know where you've been."
            ), Demo.TUNNEL, DummyType.COMBO, Goal.BLOCKS, 30, 60),
            new Lesson("mole.sonar", "Reading the sonar", List.of(
                "Every 30 seconds the sonar tells you where they are.",
                "Dig toward the direction, then stop and listen.",
                "Above you? Dig a staircase, never straight up."
            ), Demo.SONAR, DummyType.COMBO, Goal.BLOCKS, 40, 75),
            new Lesson("mole.ambush", "Ambush", List.of(
                "When you hear digging, wait next to where they come out.",
                "Hit first when they break into your tunnel.",
                "Block your tunnel behind you with cobblestone."
            ), Demo.AMBUSH, DummyType.COMBO, Goal.HITS, 8, 60)
        ))
    );

    public static Course course(String id) {
        for (Course c : ALL) {
            if (c.id().equalsIgnoreCase(id)) {
                return c;
            }
        }

        return null;
    }

    public static Lesson lesson(String id) {
        for (Course c : ALL) {
            for (Lesson l : c.lessons()) {
                if (l.id().equalsIgnoreCase(id)) {
                    return l;
                }
            }
        }

        return null;
    }

    public static Course courseOf(Lesson l) {
        for (Course c : ALL) {
            if (c.lessons().contains(l)) {
                return c;
            }
        }

        return null;
    }
}
