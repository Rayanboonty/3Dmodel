package net.vortexrealm.cosmetics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The cosmetic models: chubby pets that follow you, little shoulder pets and animated wings. Each model is a list of
 * boxes (a block texture stretched to a size), built in "pixels" (16 = one block) like a Blockbench model, in groups
 * that animate together (legs, tail, head, left / right wing). Pure data: the in-game rig ({@link CosmeticRig}) turns
 * every box into a block display, and tools/preview renders them without a server.
 *
 * <p>Model space: x to the right, y up, z forward (the way the pet looks); the origin is on the ground under its middle
 * (pets) or on the wearer's spine at chest height (wings).
 */
public final class CosmeticModels {
    public enum Kind {
        PET,
        SHOULDER,
        WINGS
    }

    /** One box: block (a block state), min corner and size in pixels, its group, an own rotation (degrees, about its centre). */
    public record Part(String block, float x, float y, float z, float w, float h, float d, String group, float rx, float ry, float rz, boolean glow) {
        Part(String block, float x, float y, float z, float w, float h, float d, String group) {
            this(block, x, y, z, w, h, d, group, 0, 0, 0, false);
        }
    }

    /** A cosmetic: its id, name, kind, hours of play to unlock it, its boxes, and the pivots of its groups (pixels). */
    public record Model(String id, String name, Kind kind, double hours, String icon, List<Part> parts, Map<String, float[]> pivots, float scale, boolean fat) {
        public float[] pivot(String group) {
            float[] p = this.pivots.get(group);
            return p == null ? new float[]{0, 0, 0} : p;
        }

        /** Height of the model in pixels (for the roll axis). */
        public float height() {
            float top = 0;
            for (Part p : this.parts) {
                top = Math.max(top, p.y + p.h);
            }

            return top;
        }
    }

    private static final Map<String, Model> ALL = new LinkedHashMap<>();
    private static final Map<String, Model> COLLARED = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 3.13.3: a pet wearing a collar in a dye colour ("red", "light_blue"...): a band around the bottom of its head
     * with a little gold tag in front. The same model object comes back for the same pet and colour.
     */
    public static Model withCollar(Model m, String color) {
        if (m == null || color == null || color.isEmpty() || m.kind() != Kind.PET) {
            return m;
        }

        return COLLARED.computeIfAbsent(m.id() + "#" + color, k -> {
            Part head = null;
            for (Part p : m.parts()) {
                if ("head".equals(p.group()) && (head == null || p.w() * p.h() * p.d() > head.w() * head.h() * head.d())) {
                    head = p;
                }
            }

            if (head == null) {
                return m;
            }

            List<Part> parts = new ArrayList<>(m.parts());
            float pad = 0.45f;
            float y0 = head.y() - 0.1f;
            parts.add(new Part(color + "_concrete", head.x() - pad, y0, head.z() - pad, head.w() + pad * 2, 1.7f, head.d() + pad * 2, "head"));
            float front = head.z() + head.d() + pad;
            parts.add(new Part("gold_block", -0.9f, y0 - 1.5f, front - 0.5f, 1.8f, 1.8f, 0.7f, "head", 0, 0, 45, false));
            return new Model(m.id(), m.name(), m.kind(), m.hours(), m.icon(), List.copyOf(parts), m.pivots(), m.scale(), m.fat());
        });
    }

    public static Map<String, Model> all() {
        return ALL;
    }

    public static Model get(String id) {
        return id == null ? null : ALL.get(id);
    }

    public static List<Model> of(Kind k) {
        List<Model> out = new ArrayList<>();
        for (Model m : ALL.values()) {
            if (m.kind == k) {
                out.add(m);
            }
        }

        return out;
    }

    // ------------------------------------------------------------------ builder

    private static final class B {
        final List<Part> parts = new ArrayList<>();
        final Map<String, float[]> pivots = new LinkedHashMap<>();
        String group = "body";

        B g(String group, float px, float py, float pz) {
            this.group = group;
            this.pivots.put(group, new float[]{px, py, pz});
            return this;
        }

        B box(String block, float x0, float y0, float z0, float x1, float y1, float z1) {
            this.parts.add(new Part(block, Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0), this.group));
            return this;
        }

        B rot(String block, float x0, float y0, float z0, float x1, float y1, float z1, float rx, float ry, float rz) {
            this.parts.add(new Part(block, Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0), this.group, rx, ry, rz, false));
            return this;
        }

        B glow(String block, float x0, float y0, float z0, float x1, float y1, float z1, float rx, float ry, float rz) {
            this.parts.add(new Part(block, Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0), this.group, rx, ry, rz, true));
            return this;
        }

        /** The same box mirrored to the other side (x to -x). */
        B pair(String block, float x0, float y0, float z0, float x1, float y1, float z1) {
            this.box(block, x0, y0, z0, x1, y1, z1);
            return this.box(block, -x1, y0, z0, -x0, y1, z1);
        }

        void add(String id, String name, Kind kind, double hours, String icon, float scale, boolean fat) {
            ALL.put(id, new Model(id, name, kind, hours, icon, List.copyOf(this.parts), Map.copyOf(this.pivots), scale, fat));
        }
    }

    /** Four stubby legs (groups legFL, legFR, legBL, legBR) with their hips as pivots. */
    private static void legs(B b, String fur, String paw, float half, float front, float back, float len, float w) {
        float[][] spots = {{half, front}, {-half, front}, {half, back}, {-half, back}};
        String[] names = {"legFL", "legFR", "legBL", "legBR"};
        for (int i = 0; i < 4; i++) {
            float x = spots[i][0];
            float z = spots[i][1];
            b.g(names[i], x, len + 1, z);
            b.box(fur, x - w / 2, 1.2f, z - w / 2, x + w / 2, len + 1.5f, z + w / 2);
            b.box(paw, x - w / 2 - 0.15f, 0, z - w / 2 - 0.15f, x + w / 2 + 0.15f, 1.2f, z + w / 2 + 0.15f);
        }
    }

    /** Two eyes with a white shine, a nose, and pink blush on the cheeks, on a face at z = face. */
    private static void face(B b, float face, float eyeY, float eyeX, String eye, String nose, float noseY, float noseZ, boolean blush) {
        b.pair(eye, eyeX, eyeY, face - 0.2f, eyeX + 1.6f, eyeY + 1.8f, face + 0.25f);
        b.pair("white_concrete", eyeX + 0.9f, eyeY + 1.0f, face + 0.25f, eyeX + 1.5f, eyeY + 1.6f, face + 0.35f);
        b.box(nose, -1.0f, noseY, noseZ - 0.3f, 1.0f, noseY + 1.2f, noseZ + 0.4f);
        if (blush) {
            b.pair("pink_concrete", eyeX + 1.7f, eyeY - 1.4f, face - 0.1f, eyeX + 3.0f, eyeY - 0.6f, face + 0.2f);
        }
    }

    static {
        // ============================================================ PETS (chubby, they follow you)
        {
            // Chonky Dog: a round shiba with a curly tail
            B b = new B();
            b.g("body", 0, 8, 0);
            b.box("orange_wool", -7, 2.5f, -8, 7, 13, 7);
            b.box("white_wool", -5, 3, 6.8f, 5, 10, 7.6f);
            b.box("white_wool", -5.5f, 2.2f, -6, 5.5f, 3.2f, 5);
            b.g("head", 0, 11, 5);
            b.box("orange_wool", -5.5f, 9, 4, 5.5f, 18.5f, 12.5f);
            b.box("white_wool", -3.5f, 9.2f, 12.3f, 3.5f, 13.5f, 15.5f);
            face(b, 12.5f, 14.2f, 1.8f, "black_concrete", "black_concrete", 12.4f, 15.6f, true);
            b.rot("orange_wool", 1.8f, 18, 6.5f, 5.3f, 21.5f, 9.5f, 0, 0, -12);
            b.rot("orange_wool", -5.3f, 18, 6.5f, -1.8f, 21.5f, 9.5f, 0, 0, 12);
            b.rot("pink_wool", 2.6f, 18.3f, 9.3f, 4.4f, 20.6f, 9.7f, 0, 0, -12);
            b.rot("pink_wool", -4.4f, 18.3f, 9.3f, -2.6f, 20.6f, 9.7f, 0, 0, 12);
            b.g("tail", 0, 11.5f, -8);
            b.rot("orange_wool", -1.6f, 11, -12, 1.6f, 14.2f, -7.5f, -40, 0, 0);
            b.rot("white_wool", -1.4f, 14.6f, -12.4f, 1.4f, 16.4f, -9.8f, -60, 0, 0);
            legs(b, "orange_wool", "white_wool", 4.2f, 3.2f, -5.2f, 2.5f, 3.2f);
            b.add("chonky_dog", "Chonky Dog", Kind.PET, 1, "BONE", 1.0f, true);
        }
        {
            // Chonky Cat: a fat grey tabby with stripes, whiskers and a long tail
            B b = new B();
            b.g("body", 0, 8, 0);
            b.box("light_gray_wool", -7.5f, 2.2f, -8, 7.5f, 13.5f, 6.5f);
            b.box("white_wool", -5, 3, 6.3f, 5, 10.5f, 7.1f);
            for (float z : new float[]{-5, -1.5f, 2}) {
                b.box("gray_wool", -5.5f, 13.5f, z, 5.5f, 13.9f, z + 1.4f);
            }

            b.g("head", 0, 11, 5);
            b.box("light_gray_wool", -5.8f, 9, 3.5f, 5.8f, 18, 11.5f);
            b.box("white_wool", -3, 9.2f, 11.3f, 3, 12.5f, 13.2f);
            face(b, 11.5f, 13.6f, 1.6f, "lime_concrete", "pink_concrete", 12.0f, 13.3f, true);
            b.pair("white_concrete", 3.2f, 11.4f, 12.6f, 7.0f, 11.7f, 12.9f);
            b.pair("white_concrete", 3.2f, 12.4f, 12.6f, 6.6f, 12.7f, 12.9f);
            b.rot("light_gray_wool", 2.2f, 17.6f, 6.2f, 5.4f, 21, 8.8f, 0, 0, -18);
            b.rot("light_gray_wool", -5.4f, 17.6f, 6.2f, -2.2f, 21, 8.8f, 0, 0, 18);
            b.rot("pink_wool", 3.0f, 17.9f, 8.6f, 4.6f, 20.2f, 9.0f, 0, 0, -18);
            b.rot("pink_wool", -4.6f, 17.9f, 8.6f, -3.0f, 20.2f, 9.0f, 0, 0, 18);
            b.g("tail", 0, 6, -8);
            b.rot("light_gray_wool", -1.4f, 4.5f, -16, 1.4f, 7.3f, -7.5f, 15, 20, 0);
            b.rot("gray_wool", -1.5f, 6.5f, -18.5f, 1.5f, 12, -15.5f, -20, 20, 0);
            legs(b, "light_gray_wool", "white_wool", 4.5f, 2.5f, -5.5f, 2.2f, 3.2f);
            b.add("chonky_cat", "Chonky Cat", Kind.PET, 2, "STRING", 1.0f, true);
        }
        {
            // Chonky Bunny: a white puffball with long pink-lined ears and a cotton tail
            B b = new B();
            b.g("body", 0, 7, 0);
            b.box("white_wool", -6.5f, 2, -7, 6.5f, 12, 6);
            b.g("head", 0, 10, 4);
            b.box("white_wool", -5, 8.5f, 3, 5, 16.5f, 10.5f);
            face(b, 10.5f, 12.3f, 1.5f, "black_concrete", "pink_concrete", 11.0f, 10.8f, true);
            b.rot("white_wool", 1.0f, 16, 5.2f, 3.6f, 24, 7.4f, -8, 0, -10);
            b.rot("white_wool", -3.6f, 16, 5.2f, -1.0f, 24, 7.4f, -8, 0, 10);
            b.rot("pink_wool", 1.6f, 16.8f, 7.3f, 3.0f, 23.2f, 7.6f, -8, 0, -10);
            b.rot("pink_wool", -3.0f, 16.8f, 7.3f, -1.6f, 23.2f, 7.6f, -8, 0, 10);
            b.g("tail", 0, 6, -7);
            b.box("white_concrete_powder", -2, 5, -9.5f, 2, 9, -6.5f);
            legs(b, "white_wool", "pink_wool", 4, 3, -4.5f, 1.8f, 3.2f);
            b.add("chonky_bunny", "Chonky Bunny", Kind.PET, 4, "RABBIT_FOOT", 1.0f, true);
        }
        {
            // Chonky Pig: pink and round with a snout and a curly tail
            B b = new B();
            b.g("body", 0, 8, 0);
            b.box("pink_terracotta", -7, 3, -8, 7, 13, 7);
            b.g("head", 0, 10, 6);
            b.box("pink_terracotta", -5.5f, 7.5f, 5, 5.5f, 16, 12.5f);
            b.box("pink_concrete", -2.5f, 9, 12.3f, 2.5f, 12, 14.3f);
            b.pair("brown_concrete", 0.6f, 10, 14.2f, 1.6f, 11, 14.5f);
            face(b, 12.5f, 13, 2, "black_concrete", "pink_concrete", 9.2f, 14.0f, false);
            b.rot("pink_terracotta", 2.5f, 15.5f, 7, 5.5f, 17.5f, 9, 30, 0, -20);
            b.rot("pink_terracotta", -5.5f, 15.5f, 7, -2.5f, 17.5f, 9, 30, 0, 20);
            b.g("tail", 0, 11, -8);
            b.rot("pink_concrete", -0.6f, 10, -10, 0.6f, 11.2f, -7.5f, 0, 0, 0);
            b.rot("pink_concrete", -0.6f, 11, -10.6f, 0.6f, 13, -9.4f, 0, 0, 0);
            legs(b, "pink_terracotta", "brown_terracotta", 4.3f, 3.5f, -5, 3, 3.2f);
            b.add("chonky_pig", "Chonky Pig", Kind.PET, 6, "CARROT_ON_A_STICK", 1.0f, true);
        }
        {
            // Chonky Penguin: an egg-shaped black and white penguin with orange feet and beak, little flippers
            B b = new B();
            b.g("body", 0, 9, 0);
            b.box("black_wool", -6.5f, 1.5f, -6, 6.5f, 17, 6);
            b.box("white_wool", -4.8f, 2, 5.8f, 4.8f, 14, 6.6f);
            b.g("head", 0, 15, 2);
            b.box("black_wool", -5, 16, -4.5f, 5, 22, 5.2f);
            b.box("white_wool", -3.6f, 16.2f, 5.0f, 3.6f, 20.4f, 5.6f);
            face(b, 5.6f, 18.4f, 1.2f, "black_concrete", "orange_concrete", 16.6f, 6.4f, true);
            b.g("legFL", 6.5f, 13, 0);
            b.rot("black_wool", 6.3f, 5.5f, -2.5f, 7.6f, 13, 2.5f, 0, 0, 12);
            b.g("legFR", -6.5f, 13, 0);
            b.rot("black_wool", -7.6f, 5.5f, -2.5f, -6.3f, 13, 2.5f, 0, 0, -12);
            b.g("legBL", 2.5f, 1.5f, 1);
            b.box("orange_concrete", 0.8f, 0, -0.5f, 4.5f, 1.5f, 5.5f);
            b.g("legBR", -2.5f, 1.5f, 1);
            b.box("orange_concrete", -4.5f, 0, -0.5f, -0.8f, 1.5f, 5.5f);
            b.g("tail", 0, 3, -6);
            b.rot("black_wool", -2, 2, -8.5f, 2, 3.4f, -5.5f, -20, 0, 0);
            b.add("chonky_penguin", "Chonky Penguin", Kind.PET, 10, "COD", 1.0f, true);
        }
        {
            // Chonky Panda: the biggest - white with black ears, eye patches, legs and shoulders
            B b = new B();
            b.g("body", 0, 9, 0);
            b.box("white_wool", -8, 3, -9, 8, 15, 7);
            b.box("black_wool", -8.3f, 9, 0.5f, 8.3f, 13.5f, 4);
            b.g("head", 0, 12, 6);
            b.box("white_wool", -6, 10, 5, 6, 19.5f, 13);
            b.pair("black_concrete", 1.2f, 13.4f, 12.8f, 4.0f, 16.6f, 13.3f);
            b.pair("white_concrete", 2.4f, 15.0f, 13.3f, 3.2f, 15.8f, 13.5f);
            b.box("black_concrete", -1.2f, 12.2f, 13.0f, 1.2f, 13.4f, 13.6f);
            b.pair("pink_concrete", 4.2f, 12.0f, 12.9f, 5.4f, 12.8f, 13.2f);
            b.pair("black_wool", 3.2f, 19, 7.5f, 6.2f, 21.8f, 10);
            b.g("tail", 0, 10, -9);
            b.box("white_wool", -1.5f, 9, -10.5f, 1.5f, 11.5f, -8.8f);
            legs(b, "black_wool", "black_concrete", 4.8f, 3.5f, -5.8f, 3, 3.8f);
            b.add("chonky_panda", "Chonky Panda", Kind.PET, 16, "BAMBOO", 1.0f, true);
        }
        {
            // Chonky Fox: orange with a white chest, dark socks and a huge fluffy white-tipped tail
            B b = new B();
            b.g("body", 0, 8, 0);
            b.box("orange_concrete_powder", -6.5f, 3, -8, 6.5f, 12.5f, 6.5f);
            b.box("white_wool", -4.5f, 3.5f, 6.3f, 4.5f, 10, 7.1f);
            b.g("head", 0, 11, 5);
            b.box("orange_concrete_powder", -5.5f, 9, 4, 5.5f, 17, 11.5f);
            b.box("white_wool", -5.6f, 9.2f, 9.5f, 5.6f, 11.8f, 11.7f);
            b.box("white_wool", -2.5f, 9.5f, 11.5f, 2.5f, 12.5f, 14.5f);
            face(b, 11.5f, 13.4f, 1.8f, "black_concrete", "black_concrete", 11.6f, 14.6f, false);
            b.rot("orange_concrete_powder", 2.2f, 17, 6.5f, 5.2f, 21, 8.5f, 0, 0, -10);
            b.rot("orange_concrete_powder", -5.2f, 17, 6.5f, -2.2f, 21, 8.5f, 0, 0, 10);
            b.rot("black_concrete", 3.0f, 19.8f, 6.7f, 4.6f, 21.2f, 8.3f, 0, 0, -10);
            b.rot("black_concrete", -4.6f, 19.8f, 6.7f, -3.0f, 21.2f, 8.3f, 0, 0, 10);
            b.g("tail", 0, 9, -8);
            b.rot("orange_concrete_powder", -3, 6.5f, -17, 3, 12.5f, -7.5f, 22, 0, 0);
            b.rot("white_wool", -2.4f, 3.6f, -20.2f, 2.4f, 8.8f, -16.4f, 22, 0, 0);
            legs(b, "orange_concrete_powder", "black_concrete", 4, 3.2f, -5.2f, 2.6f, 3);
            b.add("chonky_fox", "Chonky Fox", Kind.PET, 24, "SWEET_BERRIES", 1.0f, true);
        }

        // ============================================================ SHOULDER PETS (tiny, sit on your shoulder)
        {
            B b = new B();
            b.g("body", 0, 4, 0);
            b.box("red_wool", -2.5f, 0, -2.5f, 2.5f, 7, 3);
            b.box("yellow_wool", -2.6f, 4, 1.5f, 2.6f, 6.5f, 3.2f);
            b.g("head", 0, 7, 1);
            b.box("red_wool", -2.2f, 7, -1.5f, 2.2f, 11, 3);
            b.box("gray_concrete", -0.8f, 7.6f, 2.8f, 0.8f, 9.4f, 4.6f);
            b.pair("black_concrete", 1.5f, 9, 1.6f, 2.3f, 9.8f, 2.4f);
            b.box("red_wool", -0.6f, 10.6f, 0, 0.6f, 12.4f, 2);
            b.g("legFL", 2.6f, 6, 0);
            b.box("blue_wool", 2.4f, 1, -2.2f, 3.3f, 6.2f, 2.2f);
            b.g("legFR", -2.6f, 6, 0);
            b.box("blue_wool", -3.3f, 1, -2.2f, -2.4f, 6.2f, 2.2f);
            b.g("tail", 0, 1, -2.5f);
            b.rot("blue_wool", -1.2f, -3.5f, -3.4f, 1.2f, 1.5f, -2.2f, -25, 0, 0);
            b.add("shoulder_parrot", "Parrot", Kind.SHOULDER, 0.5, "FEATHER", 1.0f, false);
        }
        copyAsShoulder("chonky_cat", "shoulder_cat", "Tiny Cat", 3);
        copyAsShoulder("chonky_dog", "shoulder_dog", "Tiny Dog", 5);
        {
            // Bee: fuzzy stripes, see-through wings, a stinger
            B b = new B();
            b.g("body", 0, 3, 0);
            b.box("yellow_wool", -3, 0.5f, -3.5f, 3, 6, 3.5f);
            b.box("black_wool", -3.1f, 0.4f, -1.2f, 3.1f, 6.1f, 0.2f);
            b.box("black_wool", -3.1f, 0.4f, 1.6f, 3.1f, 6.1f, 2.4f);
            b.box("black_concrete", -0.4f, 2.5f, -4.6f, 0.4f, 3.3f, -3.4f);
            b.g("head", 0, 4, 3);
            b.box("yellow_wool", -2.6f, 1, 3.4f, 2.6f, 5.6f, 6.2f);
            b.pair("black_concrete", 0.9f, 3.0f, 6.0f, 2.3f, 4.6f, 6.4f);
            b.pair("black_concrete", 0.8f, 5.6f, 5.0f, 1.2f, 7.8f, 5.4f);
            b.g("legFL", 0.5f, 6, 0);
            b.glow("white_stained_glass", 0.5f, 6, -1.5f, 5.5f, 6.4f, 2.5f, 0, 0, 20);
            b.g("legFR", -0.5f, 6, 0);
            b.glow("white_stained_glass", -5.5f, 6, -1.5f, -0.5f, 6.4f, 2.5f, 0, 0, -20);
            b.add("shoulder_bee", "Bee", Kind.SHOULDER, 8, "HONEYCOMB", 1.0f, false);
        }
        {
            // Axolotl: pink, frilly gills, a wide smile
            B b = new B();
            b.g("body", 0, 2, 0);
            b.box("pink_concrete_powder", -2.5f, 0, -4.5f, 2.5f, 3.5f, 3);
            b.g("head", 0, 2, 3);
            b.box("pink_concrete_powder", -3.2f, 0, 2.8f, 3.2f, 4.2f, 7.4f);
            b.pair("black_concrete", 1.6f, 2.4f, 7.2f, 2.6f, 3.4f, 7.6f);
            b.box("magenta_concrete", -1.5f, 0.8f, 7.3f, 1.5f, 1.2f, 7.6f);
            b.pair("magenta_concrete", 3.2f, 1.5f, 3.5f, 4.6f, 5.5f, 4.2f);
            b.pair("magenta_concrete", 2.4f, 4.2f, 3.5f, 3.4f, 5.8f, 4.2f);
            b.g("tail", 0, 2, -4.5f);
            b.rot("pink_concrete_powder", -0.5f, 0.5f, -10, 0.5f, 4, -4.5f, 0, 0, 0);
            b.add("shoulder_axolotl", "Axolotl", Kind.SHOULDER, 12, "AXOLOTL_BUCKET", 1.0f, false);
        }
        copyAsShoulder("chonky_penguin", "shoulder_penguin", "Tiny Penguin", 20);

        // ============================================================ WINGS (on your back, they flap)
        wings("wings_angel", "Angel Wings", 2, "FEATHER", "white_wool", "white_concrete", "light_gray_wool", false, false);
        butterfly("wings_butterfly", "Butterfly Wings", 6, "orange_concrete", "black_concrete", "yellow_concrete");
        bat("wings_bat", "Bat Wings", 12, "red_concrete", "black_concrete", false);
        wings("wings_frost", "Frost Wings", 20, "PACKED_ICE", "light_blue_stained_glass", "white_stained_glass", "cyan_stained_glass", true, true);
        bat("wings_dragon", "Dragon Wings", 35, "purple_concrete", "black_concrete", true);
        wings("wings_phoenix", "Phoenix Wings", 50, "BLAZE_POWDER", "orange_concrete", "yellow_concrete", "red_concrete", true, false);
        butterfly("wings_void", "Void Wings", 75, "purple_stained_glass", "black_concrete", "magenta_stained_glass");
        wings("wings_golden", "Golden Wings", 100, "GOLD_INGOT", "gold_block", "yellow_concrete", "orange_concrete", true, false);
    }

    /** A shoulder version of a pet: the same boxes, shrunk. */
    private static void copyAsShoulder(String from, String id, String name, double hours) {
        Model m = ALL.get(from);
        ALL.put(id, new Model(id, name, Kind.SHOULDER, hours, m.icon(), m.parts(), m.pivots(), 0.42f, false));
    }

    /**
     * Feathered wings, built like a bird's: an arm from the spine up to the wrist, a row of short coverts along it, and
     * the secondaries and long primaries fanning down and out from it. Groups wingL / wingR, pivots at the spine.
     */
    private static void wings(String id, String name, double hours, String icon, String main, String light, String tip, boolean glow, boolean glass) {
        B b = new B();
        for (int side = -1; side <= 1; side += 2) {
            String g = side > 0 ? "wingL" : "wingR";
            b.g(g, side * 1.5f, 0, -2.5f);
            float ax0 = 1.5f, ay0 = 3, ax1 = 13, ay1 = 12;
            // the arm (leading edge)
            feather(b, g, side, main, ax0, ay0, (float)Math.hypot(ax1 - ax0, ay1 - ay0) + 1.5f, 2.8f,
                (float)Math.toDegrees(Math.atan2(ay1 - ay0, ax1 - ax0)), -3.7f, glow);
            // coverts: short and rounded, along the arm, pointing down and out
            for (int k = 0; k < 5; k++) {
                float t = (k + 0.3f) / 5f;
                feather(b, g, side, light, ax0 + (ax1 - ax0) * t, ay0 + (ay1 - ay0) * t - 0.6f, 6.5f, 3.4f, -62 + k * 4, -3.5f, glow);
            }

            // secondaries: from the inner arm, hanging down
            for (int k = 0; k < 4; k++) {
                float t = (k + 0.1f) / 5f;
                feather(b, g, side, main, ax0 + (ax1 - ax0) * t, ay0 + (ay1 - ay0) * t - 1.5f, 11 + k * 0.8f, 3.2f, -96 + k * 5, -3.25f, glow);
            }

            // primaries: from the wrist, the long fan
            float[][] prim = {{-76, 13}, {-56, 15.5f}, {-36, 16.5f}, {-16, 15.5f}, {4, 12.5f}};
            for (int k = 0; k < prim.length; k++) {
                float t = 0.72f + k * 0.07f;
                feather(b, g, side, k >= 3 ? tip : light, ax0 + (ax1 - ax0) * t, ay0 + (ay1 - ay0) * t - 0.8f, prim[k][1], 3.0f, prim[k][0], -3.0f, glow);
            }
        }

        b.add(id, name, Kind.WINGS, hours, icon, 1.0f, false);
    }

    /** One feather (a long thin plate) starting at (x, y) on the right side, pointing at angle deg; mirrored for side -1. */
    private static void feather(B b, String g, int side, String block, float x, float y, float len, float width, float deg, float z, boolean glow) {
        double a = Math.toRadians(deg);
        float cx = side * (x + (float)Math.cos(a) * len / 2);
        float cy = y + (float)Math.sin(a) * len / 2;
        b.parts.add(new Part(block, cx - len / 2, cy - width / 2, z - 0.3f, len, width, 0.6f, g, 0, 0, side * deg, glow));
    }

    /** Membrane wings (bat, dragon): bony fingers and a membrane between them; the dragon has spikes. */
    private static void bat(String id, String name, double hours, String membrane, String bone, boolean dragon) {
        B b = new B();
        for (int side = -1; side <= 1; side += 2) {
            String g = side > 0 ? "wingL" : "wingR";
            b.g(g, side * 1.5f, 0, -2.5f);
            float[][] bones = {{1.5f, 2, 22, 1.2f, 30}, {12, 9, 14, 1.0f, -20}, {12, 9, 16, 1.0f, -55}, {12, 9, 12, 1.0f, -90}};
            for (float[] f : bones) {
                float len = f[2];
                double a = Math.toRadians(f[4]);
                float cx = side * (f[0] + (float)Math.cos(a) * len / 2);
                float cy = f[1] + (float)Math.sin(a) * len / 2;
                b.parts.add(new Part(bone, cx - len / 2, cy - f[3] / 2, -3.2f, len, f[3], 1.0f, g, 0, 0, side * f[4], false));
            }

            float[][] skins = {{4, -2, 16, 9, 12}, {11, -3, 10, 9, -38}, {12, -8, 8, 10, -70}, {3, 4, 12, 6, 26}};
            for (float[] f : skins) {
                float cx = side * (f[0] + f[2] / 2);
                b.parts.add(new Part(membrane, cx - f[2] / 2, f[1] - f[3] / 2 + 3, -2.9f, f[2], f[3], 0.5f, g, 0, 0, side * f[4], false));
            }

            if (dragon) {
                for (int k = 0; k < 4; k++) {
                    float x = 4 + k * 4.5f;
                    float y = 5 + k * 2.6f;
                    b.parts.add(new Part("magenta_concrete", side * x - 0.6f, y, -3.0f, 1.2f, 2.6f, 0.8f, g, 0, 0, side * 20, true));
                }
            }
        }

        b.add(id, name, Kind.WINGS, hours, dragon ? "DRAGON_BREATH" : "PHANTOM_MEMBRANE", 1.0f, false);
    }

    /** Butterfly wings: a big rounded upper and a smaller lower lobe with a dark border and bright spots. */
    private static void butterfly(String id, String name, double hours, String fill, String border, String spot) {
        B b = new B();
        boolean glowing = fill.contains("glass");
        for (int side = -1; side <= 1; side += 2) {
            String g = side > 0 ? "wingL" : "wingR";
            b.g(g, side * 1.5f, 0, -2.5f);
            // upper lobe: rows of boxes making a rounded shape, border around
            for (int row = 0; row < 7; row++) {
                float y = row * 2.4f;
                float len = (float)(17 * Math.sin(Math.PI * (row + 1) / 8.0)) + 3;
                float x0 = 1.5f + row * 0.6f;
                b.parts.add(new Part(border, side > 0 ? x0 : -x0 - len - 0.6f, y - 0.1f, -3.0f, len + 0.6f, 2.6f, 0.5f, g, 0, 0, 0, false));
                b.parts.add(new Part(fill, side > 0 ? x0 : -x0 - len, y, -3.2f, len, 2.4f, 0.5f, g, 0, 0, 0, glowing));
            }

            for (int row = 0; row < 5; row++) {
                float y = -2.4f - row * 2.2f;
                float len = (float)(11 * Math.sin(Math.PI * (row + 1) / 6.0)) + 2;
                float x0 = 1.5f + row * 0.4f;
                b.parts.add(new Part(border, side > 0 ? x0 : -x0 - len - 0.6f, y - 0.1f, -3.0f, len + 0.6f, 2.4f, 0.5f, g, 0, 0, 0, false));
                b.parts.add(new Part(fill, side > 0 ? x0 : -x0 - len, y, -3.2f, len, 2.2f, 0.5f, g, 0, 0, 0, glowing));
            }

            for (float[] s : new float[][]{{12, 9, 3}, {7, 4, 2.4f}, {6, -6, 2.4f}, {15, 5, 1.8f}}) {
                float x = side > 0 ? s[0] : -s[0] - s[2];
                b.parts.add(new Part(spot, x, s[1], -3.4f, s[2], s[2], 0.3f, g, 0, 0, 0, true));
            }
        }

        b.add(id, name, Kind.WINGS, hours, glowing ? "ENDER_EYE" : "ORANGE_DYE", 1.0f, false);
    }

    private CosmeticModels() {
    }
}
