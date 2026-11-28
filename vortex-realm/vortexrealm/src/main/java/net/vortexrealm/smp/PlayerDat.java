package net.vortexrealm.smp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

/**
 * Reads a player file (world/playerdata/&lt;uuid&gt;.dat, also inside Data Vault snapshots) without the player being
 * online: inventory with armour and offhand, ender chest, XP, the world they were in and this plugin's inventory-group
 * marker. Items are handed to Paper exactly as saved (it upgrades old item data by itself).
 */
public final class PlayerDat {
    public final ItemStack[] inventory = new ItemStack[41];
    public final ItemStack[] ender = new ItemStack[27];
    public int level;
    public float exp;
    public World world;
    /** The inv_group marker of this plugin (HUB, SURVIVAL, ECO...), or null. */
    public String group;
    private final byte[] b;
    private int dataVersion;

    private PlayerDat(byte[] raw) {
        this.b = raw;
    }

    public static PlayerDat read(InputStream gz) throws IOException {
        byte[] raw;
        try (GZIPInputStream in = new GZIPInputStream(gz)) {
            raw = in.readAllBytes();
        }

        PlayerDat d = new PlayerDat(raw);
        d.parse();
        return d;
    }

    /** Stacks that aren't empty, in the inventory and the ender chest. */
    public int stacks() {
        int n = 0;
        for (ItemStack it : this.inventory) {
            n += it != null && !it.isEmpty() ? 1 : 0;
        }

        for (ItemStack it : this.ender) {
            n += it != null && !it.isEmpty() ? 1 : 0;
        }

        return n;
    }

    // ------------------------------------------------------------------ NBT

    /** A tag inside the file: where its whole entry (type, name, payload) and its payload are. */
    private record Tag(int type, int entryStart, int payloadStart, int end) {
    }

    private int pos;

    private int u8() {
        return this.b[this.pos++] & 0xFF;
    }

    private int u16() {
        int v = (this.b[this.pos] & 0xFF) << 8 | this.b[this.pos + 1] & 0xFF;
        this.pos += 2;
        return v;
    }

    private int i32(int at) {
        return ByteBuffer.wrap(this.b, at, 4).getInt();
    }

    private long i64(int at) {
        return ByteBuffer.wrap(this.b, at, 8).getLong();
    }

    private String str(int at) {
        int len = (this.b[at] & 0xFF) << 8 | this.b[at + 1] & 0xFF;
        return new String(this.b, at + 2, len, StandardCharsets.UTF_8);
    }

    /** Skips a payload of the given type, starting at {@code pos}. */
    private void skip(int type) {
        switch (type) {
            case 1 -> this.pos += 1;
            case 2 -> this.pos += 2;
            case 3, 5 -> this.pos += 4;
            case 4, 6 -> this.pos += 8;
            case 7 -> this.pos += 4 + this.i32(this.pos);
            case 8 -> this.pos += 2 + ((this.b[this.pos] & 0xFF) << 8 | this.b[this.pos + 1] & 0xFF);
            case 9 -> {
                int et = this.u8();
                int n = this.i32(this.pos);
                this.pos += 4;
                for (int i = 0; i < n; i++) {
                    this.skip(et);
                }
            }
            case 10 -> {
                while (true) {
                    int t = this.u8();
                    if (t == 0) {
                        break;
                    }

                    this.pos += 2 + ((this.b[this.pos] & 0xFF) << 8 | this.b[this.pos + 1] & 0xFF);
                    this.skip(t);
                }
            }
            case 11 -> this.pos += 4 + 4 * this.i32(this.pos);
            case 12 -> this.pos += 4 + 8 * this.i32(this.pos);
            default -> throw new IllegalStateException("bad NBT tag " + type);
        }
    }

    /** The entries of a compound whose payload starts at {@code at}. */
    private Map<String, Tag> compound(int at) {
        Map<String, Tag> out = new LinkedHashMap<>();
        this.pos = at;
        while (true) {
            int entry = this.pos;
            int t = this.u8();
            if (t == 0) {
                break;
            }

            int len = this.u16();
            String name = new String(this.b, this.pos, len, StandardCharsets.UTF_8);
            this.pos += len;
            int payload = this.pos;
            this.skip(t);
            out.put(name, new Tag(t, entry, payload, this.pos));
        }

        return out;
    }

    /** Payload starts of the compounds in a list of compounds. */
    private int[] listOfCompounds(Tag list) {
        this.pos = list.payloadStart();
        int et = this.u8();
        int n = this.i32(this.pos);
        this.pos += 4;
        if (et != 10) {
            return new int[0];
        }

        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = this.pos;
            this.skip(10);
        }

        return out;
    }

    private void parse() {
        if (this.u8() != 10) {
            throw new IllegalStateException("not a player file");
        }

        this.pos += this.u16();
        Map<String, Tag> root = this.compound(this.pos);
        Tag dv = root.get("DataVersion");
        this.dataVersion = dv == null ? 4440 : this.i32(dv.payloadStart());
        Tag t = root.get("XpLevel");
        this.level = t == null ? 0 : this.i32(t.payloadStart());
        t = root.get("XpP");
        this.exp = t == null ? 0.0F : ByteBuffer.wrap(this.b, t.payloadStart(), 4).getFloat();
        Tag most = root.get("WorldUUIDMost");
        Tag least = root.get("WorldUUIDLeast");
        if (most != null && least != null) {
            this.world = Bukkit.getWorld(new UUID(this.i64(most.payloadStart()), this.i64(least.payloadStart())));
        }

        Tag dim = root.get("Dimension");
        if (this.world == null && dim != null && dim.type() == 8) {
            String d = this.str(dim.payloadStart());
            this.world = Bukkit.getWorld(d.substring(d.indexOf(':') + 1));
        }

        Tag bv = root.get("BukkitValues");
        if (bv != null && bv.type() == 10) {
            for (Map.Entry<String, Tag> e : this.compound(bv.payloadStart()).entrySet()) {
                if (e.getKey().endsWith(":inv_group") && e.getValue().type() == 8) {
                    this.group = this.str(e.getValue().payloadStart());
                }
            }
        }

        Tag inv = root.get("Inventory");
        if (inv != null && inv.type() == 9) {
            for (int at : this.listOfCompounds(inv)) {
                Map<String, Tag> item = this.compound(at);
                int slot = this.slot(item);
                int idx = slot >= 0 && slot <= 35 ? slot : (slot >= 100 && slot <= 103 ? 36 + slot - 100 : (slot == -106 ? 40 : -1));
                if (idx >= 0) {
                    this.inventory[idx] = this.item(item);
                }
            }
        }

        // 1.21.5+: armour and offhand are kept apart
        Tag eq = root.get("equipment");
        if (eq != null && eq.type() == 10) {
            Map<String, Tag> slots = this.compound(eq.payloadStart());
            String[] names = {"feet", "legs", "chest", "head", "offhand"};
            for (int i = 0; i < names.length; i++) {
                Tag s = slots.get(names[i]);
                if (s != null && s.type() == 10) {
                    this.inventory[36 + i] = this.item(this.compound(s.payloadStart()));
                }
            }
        }

        Tag ec = root.get("EnderItems");
        if (ec != null && ec.type() == 9) {
            for (int at : this.listOfCompounds(ec)) {
                Map<String, Tag> item = this.compound(at);
                int slot = this.slot(item);
                if (slot >= 0 && slot < 27) {
                    this.ender[slot] = this.item(item);
                }
            }
        }
    }

    private int slot(Map<String, Tag> item) {
        Tag s = item.get("Slot");
        return s == null ? -999 : this.b[s.payloadStart()];
    }

    /** One item compound as Paper's item bytes (gzipped NBT with the data version), turned into an ItemStack. */
    private ItemStack item(Map<String, Tag> item) {
        try {
            ByteArrayOutputStream nbt = new ByteArrayOutputStream();
            nbt.write(10);
            nbt.write(0);
            nbt.write(0);
            for (Map.Entry<String, Tag> e : item.entrySet()) {
                if (!e.getKey().equals("Slot")) {
                    nbt.write(this.b, e.getValue().entryStart(), e.getValue().end() - e.getValue().entryStart());
                }
            }

            byte[] name = "DataVersion".getBytes(StandardCharsets.UTF_8);
            nbt.write(3);
            nbt.write(0);
            nbt.write(name.length);
            nbt.write(name);
            nbt.write(ByteBuffer.allocate(4).putInt(this.dataVersion).array());
            nbt.write(0);
            ByteArrayOutputStream gz = new ByteArrayOutputStream();
            try (GZIPOutputStream out = new GZIPOutputStream(gz)) {
                out.write(nbt.toByteArray());
            }

            ItemStack it = ItemStack.deserializeBytes(gz.toByteArray());
            return it.isEmpty() ? null : it;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
