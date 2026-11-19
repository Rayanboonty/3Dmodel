package net.practicepvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.practicepvp.kit.KitLayout;
import org.junit.jupiter.api.Test;

/** A player's kit layout is saved as text and must come back exactly (kit restoration). */
class KitLayoutTest {
    @Test
    void roundTrip() {
        KitLayout l = new KitLayout(3, List.of(new KitLayout.Entry(0, 8, 1), new KitLayout.Entry(4, 0, 16), new KitLayout.Entry(40, 1, 64)));
        KitLayout back = KitLayout.parse(3, l.serialize());
        assertEquals(l.entries, back.entries);
        assertEquals(3, back.version);
    }

    @Test
    void emptyLayout() {
        assertEquals(0, KitLayout.parse(1, "").entries.size());
    }

    @Test
    void brokenTextIsRejected() {
        assertNull(KitLayout.parse(1, "0:1"));
        assertNull(KitLayout.parse(1, "a:b:c"));
    }
}
