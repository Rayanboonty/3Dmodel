package net.vortexrealm.pack;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.geom.Path2D.Double;
import java.awt.geom.Point2D.Float;
import java.awt.image.BufferedImage;

public final class BadgeArt {
    private static final double GW = 10.0;
    private static final double GH = 14.0;
    private static final double STROKE = 2.9;

    private BadgeArt() {
    }

    private static Path2D glyphPath(char ch) {
        Path2D p = new Double();
        switch (ch) {
            case '1':
                p.moveTo(2.2, 3.4);
                p.lineTo(5.6, 1.2);
                p.lineTo(5.6, 13.6);
                break;
            case '2':
                p.moveTo(1.7, 4.2);
                p.curveTo(1.8, 0.2, 8.3, 0.2, 8.3, 4.1);
                p.curveTo(8.3, 6.6, 5.5, 8.1, 1.5, 12.55);
                p.lineTo(9.0, 12.55);
                break;
            case '3':
                p.moveTo(1.6, 3.3);
                p.curveTo(2.4, 0.2, 8.4, 0.3, 8.2, 3.6);
                p.curveTo(8.0, 6.2, 5.6, 6.8, 4.2, 6.8);
                p.curveTo(7.2, 6.8, 8.8, 8.0, 8.6, 10.2);
                p.curveTo(8.3, 13.9, 2.2, 14.0, 1.3, 10.6);
                break;
            case '4':
                p.moveTo(6.9, 13.6);
                p.lineTo(6.9, 1.0);
                p.lineTo(1.0, 9.4);
                p.lineTo(9.4, 9.4);
                break;
            case '5':
                p.moveTo(8.6, 1.45);
                p.lineTo(2.6, 1.45);
                p.lineTo(2.0, 6.5);
                p.curveTo(4.0, 5.2, 8.9, 5.0, 8.8, 9.2);
                p.curveTo(8.7, 13.8, 2.6, 14.0, 1.2, 11.0);
            case '6':
            case '7':
            case '8':
            case '9':
            case ':':
            case ';':
            case '<':
            case '=':
            case '>':
            case '?':
            case '@':
            case 'A':
            case 'B':
            case 'C':
            case 'D':
            case 'E':
            case 'F':
            case 'G':
            case 'I':
            case 'J':
            case 'K':
            case 'N':
            case 'O':
            case 'P':
            case 'Q':
            case 'S':
            case 'U':
            default:
                break;
            case 'H':
                p.moveTo(1.6, 0.4);
                p.lineTo(1.6, 13.6);
                p.moveTo(8.4, 0.4);
                p.lineTo(8.4, 13.6);
                p.moveTo(1.6, 7.0);
                p.lineTo(8.4, 7.0);
                break;
            case 'L':
                p.moveTo(1.6, 0.4);
                p.lineTo(1.6, 12.55);
                p.lineTo(9.4, 12.55);
                break;
            case 'M':
                p.moveTo(1.5, 13.6);
                p.lineTo(1.5, 1.2);
                p.lineTo(5.0, 8.4);
                p.lineTo(8.5, 1.2);
                p.lineTo(8.5, 13.6);
                break;
            case 'R':
                p.moveTo(1.6, 13.6);
                p.lineTo(1.6, 1.45);
                p.lineTo(5.6, 1.45);
                p.curveTo(9.6, 1.45, 9.6, 7.4, 5.6, 7.4);
                p.lineTo(1.6, 7.4);
                p.moveTo(5.2, 7.4);
                p.lineTo(8.9, 13.6);
                break;
            case 'T':
                p.moveTo(0.3, 1.45);
                p.lineTo(9.7, 1.45);
                p.moveTo(5.0, 1.45);
                p.lineTo(5.0, 13.6);
                break;
            case 'V':
                p.moveTo(0.8, 0.6);
                p.lineTo(5.0, 13.2);
                p.lineTo(9.2, 0.6);
        }

        return p;
    }

    public static Shape text(String text, double x, double y, double height) {
        double k = height / 14.0;
        double gap = 2.2;
        Area out = new Area();
        BasicStroke stroke = new BasicStroke(2.9F, 0, 0, 1.8F);
        double cx = 0.0;

        for (char ch : text.toCharArray()) {
            Shape s = stroke.createStrokedShape(glyphPath(ch));
            AffineTransform t = new AffineTransform();
            t.translate(x + cx * k, y);
            t.scale(k, k);
            out.add(new Area(t.createTransformedShape(s)));
            cx += 10.0 + gap;
        }

        return out;
    }

    public static double textWidth(String text, double height) {
        return text.isEmpty() ? 0.0 : (text.length() * 10.0 + (text.length() - 1) * 2.2) * height / 14.0;
    }

    private static Color rgb(int rgb, int alpha) {
        return new Color(rgb >> 16 & 0xFF, rgb >> 8 & 0xFF, rgb & 0xFF, Math.max(0, Math.min(255, alpha)));
    }

    private static int mix(int a, int b, double f) {
        int r = (int)Math.round((a >> 16 & 0xFF) * (1.0 - f) + (b >> 16 & 0xFF) * f);
        int g = (int)Math.round((a >> 8 & 0xFF) * (1.0 - f) + (b >> 8 & 0xFF) * f);
        int bl = (int)Math.round((a & 0xFF) * (1.0 - f) + (b & 0xFF) * f);
        return r << 16 | g << 8 | bl;
    }

    private static Graphics2D g2(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        return g;
    }

    public static int badgeWidth(int size) {
        return size * 11 / 8;
    }

    public static BufferedImage badge(String label, int rgb, int number, int step, int size) {
        int w = badgeWidth(size);
        BufferedImage img = new BufferedImage(w, size, 2);
        Graphics2D g = g2(img);
        int light = mix(rgb, 16777215, 0.6);
        int deep = mix(rgb, 0, 0.25);
        int darkest = mix(rgb, 0, 0.75);
        double th = size * 0.5;
        double tw = textWidth(label, th);
        double tx = w / 2.0 - tw / 2.0;
        double ty = size / 2.0 - th / 2.0;
        Shape txt = text(label, tx, ty, th);
        g.setColor(new Color(0, 0, 0, 120));
        g.fill(AffineTransform.getTranslateInstance(0.0, size * 0.035).createTransformedShape(txt));
        g.setColor(rgb(darkest, 240));
        g.setStroke(new BasicStroke((float)(size * 0.045), 1, 1));
        g.draw(txt);
        g.setPaint(
            new LinearGradientPaint(
                new java.awt.geom.Point2D.Double(0.0, ty),
                new java.awt.geom.Point2D.Double(0.0, ty + th),
                new float[]{0.0F, 0.45F, 1.0F},
                new Color[]{rgb(light, 255), rgb(rgb, 255), rgb(deep, 255)}
            )
        );
        g.fill(txt);
        Area top = new Area(txt);
        top.intersect(new Area(new java.awt.geom.Rectangle2D.Double(0.0, ty, w, th * 0.22)));
        g.setColor(new Color(255, 255, 255, 90));
        g.fill(top);
        g.dispose();
        widthPin(img);
        return img;
    }

    public static BufferedImage shineOver(BufferedImage label, double pos) {
        int w = label.getWidth();
        int h = label.getHeight();
        BufferedImage img = new BufferedImage(w, h, 2);
        Graphics2D g = g2(img);
        g.drawImage(label, 0, 0, null);
        g.setComposite(AlphaComposite.getInstance(10));
        double x = -w * 0.3 + w * 1.6 * pos;
        double bw = w * 0.12;
        Path2D band = new Double();
        band.moveTo(x, 0.0);
        band.lineTo(x + bw, 0.0);
        band.lineTo(x + bw - h * 0.6, h);
        band.lineTo(x - h * 0.6, h);
        band.closePath();
        g.setPaint(new GradientPaint((float)x, 0.0F, new Color(255, 255, 255, 0), (float)(x + bw / 2.0), 0.0F, new Color(255, 255, 255, 190), true));
        g.fill(band);
        g.dispose();
        widthPin(img);
        return img;
    }

    public static BufferedImage reveal(BufferedImage badge, double progress, int rgb) {
        int w = badge.getWidth();
        int h = badge.getHeight();
        double top = h * 0.06;
        double bottom = h * 0.94;
        double edge = bottom - (bottom - top + h * 0.12) * progress;
        double soft = h * 0.08;
        BufferedImage out = new BufferedImage(w, h, 2);
        int light = mix(rgb, 16777215, 0.75);

        for (int y = 0; y < h; y++) {
            double vis = Math.max(0.0, Math.min(1.0, (y - edge) / soft + 0.5));
            double line = Math.exp(-Math.pow((y - edge) / (h * 0.022), 2.0));
            double tint = Math.max(0.0, 1.0 - (y - edge) / (h * 0.25)) * (y > edge ? 0.35 : 0.0);

            for (int x = 0; x < w; x++) {
                int p = badge.getRGB(x, y);
                int a = p >>> 24;
                if (a != 0) {
                    int col = p & 16777215;
                    if (tint > 0.0) {
                        col = mix(col, light, tint);
                    }

                    if (line > 0.02) {
                        col = mix(col, 16777215, Math.min(1.0, line * 0.9));
                    }

                    double alpha = a * Math.min(1.0, vis + line * 0.9);
                    out.setRGB(x, y, (int)Math.round(alpha) << 24 | col);
                }
            }
        }

        widthPin(out);
        return out;
    }

    public static BufferedImage glow(int rgb, double strength, int size) {
        BufferedImage img = new BufferedImage(size, size, 2);
        Graphics2D g = g2(img);
        float c = size / 2.0F;
        int a = (int)Math.round(170.0 * strength);
        g.setPaint(
            new RadialGradientPaint(
                new Float(c, c),
                c,
                new float[]{0.0F, 0.35F, 0.7F, 1.0F},
                new Color[]{rgb(mix(rgb, 16777215, 0.35), a), rgb(rgb, (int)(a * 0.6)), rgb(rgb, (int)(a * 0.18)), rgb(rgb, 0)},
                CycleMethod.NO_CYCLE
            )
        );
        g.fillRect(0, 0, size, size);
        g.setComposite(AlphaComposite.getInstance(3, (float)(0.35 * strength)));

        for (int i = 0; i < 12; i++) {
            double t = (Math.PI * 2) * i / 12.0;
            Path2D ray = new Double();
            ray.moveTo(c, c);
            ray.lineTo(c + Math.cos(t - 0.06) * c, c + Math.sin(t - 0.06) * c);
            ray.lineTo(c + Math.cos(t + 0.06) * c, c + Math.sin(t + 0.06) * c);
            ray.closePath();
            g.setPaint(new RadialGradientPaint(new Float(c, c), c, new float[]{0.0F, 1.0F}, new Color[]{rgb(16777215, 160), rgb(rgb, 0)}));
            g.fill(ray);
        }

        g.dispose();
        widthPin(img);
        return img;
    }

    private static void widthPin(BufferedImage img) {
        int x = img.getWidth() - 1;
        if (img.getRGB(x, 0) >>> 24 == 0) {
            img.setRGB(x, 0, 16777216);
        }
    }

    public static BufferedImage seal(int size) {
        BufferedImage img = new BufferedImage(size, size, 2);
        Graphics2D g = g2(img);
        double c = size / 2.0;
        Path2D ros = new Double();
        int bumps = 8;

        for (int i = 0; i <= bumps * 8; i++) {
            double t = (Math.PI * 2) * i / (bumps * 8);
            double rr = size * (0.43 + 0.05 * Math.cos(t * bumps));
            double x = c + Math.cos(t) * rr;
            double y = c + Math.sin(t) * rr;
            if (i == 0) {
                ros.moveTo(x, y);
            } else {
                ros.lineTo(x, y);
            }
        }

        ros.closePath();
        g.setPaint(new GradientPaint(0.0F, 0.0F, new Color(6091418), 0.0F, size, new Color(1616215)));
        g.fill(ros);
        g.setColor(new Color(944951));
        g.setStroke(new BasicStroke((float)(size * 0.06)));
        g.draw(ros);
        Path2D check = new Double();
        check.moveTo(size * 0.29, size * 0.52);
        check.lineTo(size * 0.44, size * 0.66);
        check.lineTo(size * 0.72, size * 0.36);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float)(size * 0.12), 1, 1));
        g.draw(check);
        g.dispose();
        return img;
    }

    public static BufferedImage play(int w, int h) {
        int s = 8;
        BufferedImage big = new BufferedImage(w * s, h * s, 2);
        Graphics2D g = g2(big);
        double bw = w * s;
        double bh = h * s;
        double inset = bh * 0.06;
        RoundRectangle2D box = new java.awt.geom.RoundRectangle2D.Double(inset, inset, bw - inset * 2.0, bh - inset * 2.0, bh * 0.52, bh * 0.52);
        g.setPaint(new GradientPaint(0.0F, 0.0F, new Color(16722474), 0.0F, (float)bh, new Color(14155776)));
        g.fill(box);
        g.setColor(new Color(9306112));
        g.setStroke(new BasicStroke((float)(bh * 0.05)));
        g.draw(box);
        Path2D tri = new Double();
        double cx = bw / 2.0 + bh * 0.04;
        double cy = bh / 2.0;
        double th = bh * 0.42;
        tri.moveTo(cx - th * 0.45, cy - th / 2.0);
        tri.lineTo(cx + th * 0.55, cy);
        tri.lineTo(cx - th * 0.45, cy + th / 2.0);
        tri.closePath();
        g.setColor(Color.WHITE);
        g.fill(tri);
        g.dispose();
        BufferedImage img = new BufferedImage(w, h, 2);
        Graphics2D d = img.createGraphics();
        d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        d.drawImage(big.getScaledInstance(w, h, 16), 0, 0, null);
        d.dispose();
        return img;
    }

    public static BufferedImage packIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, 2);
        Graphics2D g = g2(img);
        float c = size / 2.0F;
        g.setPaint(new RadialGradientPaint(new Float(c, c * 0.8F), c, new float[]{0.0F, 1.0F}, new Color[]{new Color(3808622), new Color(853536)}));
        g.fill(new java.awt.geom.Ellipse2D.Double(size * 0.04, size * 0.04, size * 0.92, size * 0.92));

        for (int arm = 0; arm < 3; arm++) {
            Path2D spiral = new Double();

            for (int i = 0; i <= 60; i++) {
                double t = i / 60.0;
                double a = arm * Math.PI * 2.0 / 3.0 + t * Math.PI * 2.2;
                double rr = size * (0.06 + 0.36 * t);
                double x = c + Math.cos(a) * rr;
                double y = c + Math.sin(a) * rr;
                if (i == 0) {
                    spiral.moveTo(x, y);
                } else {
                    spiral.lineTo(x, y);
                }
            }

            g.setPaint(new GradientPaint(0.0F, 0.0F, new Color(16769126), size, size, new Color(16747008)));
            g.setStroke(new BasicStroke(size * 0.07F, 1, 1));
            g.draw(spiral);
        }

        g.setColor(new Color(16774338));
        g.fill(new java.awt.geom.Ellipse2D.Double(c - size * 0.07, c - size * 0.07, size * 0.14, size * 0.14));
        g.dispose();
        return img;
    }
}
