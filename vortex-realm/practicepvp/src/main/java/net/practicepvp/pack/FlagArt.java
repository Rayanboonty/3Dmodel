package net.practicepvp.pack;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

public final class FlagArt {
    public static final int W = 24;
    public static final int H = 16;
    private static final Map<String, String> SPECS = new HashMap<>();
    public static final int CELL_W = 24;
    public static final int CELL_H = 16;

    private FlagArt() {
    }

    public static boolean has(String code) {
        return SPECS.containsKey(code) || FlagArt.class.getResource("/flags/" + code.toLowerCase(Locale.ROOT) + ".png") != null;
    }

    public static BufferedImage icon(String code, String[] fallback) {
        BufferedImage real = null;

        try (InputStream in = FlagArt.class.getResourceAsStream("/flags/" + code.toLowerCase(Locale.ROOT) + ".png")) {
            if (in != null) {
                real = ImageIO.read(in);
            }
        } catch (RuntimeException | IOException var19) {
        }

        BufferedImage out = new BufferedImage(24, 16, 2);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        if (real == null) {
            g.drawImage(draw(code, fallback), 0, 0, 24, 16, null);
            g.dispose();
            return out;
        } else {
            double scale = Math.min(24.0 / real.getWidth(), 16.0 / real.getHeight());
            int w = Math.max(1, (int)Math.round(real.getWidth() * scale));
            int h = Math.max(1, (int)Math.round(real.getHeight() * scale));
            int x0 = (24 - w) / 2;
            int y0 = 16 - h;
            g.drawImage(real.getScaledInstance(w, h, 16), x0, y0, null);
            g.dispose();

            for (int y = y0; y < y0 + h; y++) {
                for (int x = x0; x < x0 + w; x++) {
                    boolean corner = (x == x0 || x == x0 + w - 1) && (y == y0 || y == y0 + h - 1);
                    if (corner) {
                        out.setRGB(x, y, 0);
                    } else {
                        int c = out.getRGB(x, y);
                        if (c >>> 24 < 128) {
                            out.setRGB(x, y, 0);
                        } else {
                            double f = 1.0;
                            if (y != y0 && x != x0) {
                                if (y == y0 + h - 1 || x == x0 + w - 1) {
                                    f = 0.72;
                                }
                            } else {
                                f = 1.18;
                            }

                            out.setRGB(x, y, 0xFF000000 | shade(c, f));
                        }
                    }
                }
            }

            return out;
        }
    }

    private static int shade(int rgb, double f) {
        int r = (int)Math.min(255L, Math.round((rgb >> 16 & 0xFF) * f + (f > 1.0 ? 12 : 0)));
        int g = (int)Math.min(255L, Math.round((rgb >> 8 & 0xFF) * f + (f > 1.0 ? 12 : 0)));
        int b = (int)Math.min(255L, Math.round((rgb & 0xFF) * f + (f > 1.0 ? 12 : 0)));
        return r << 16 | g << 8 | b;
    }

    public static BufferedImage draw(String code, String[] fallback) {
        BufferedImage img = new BufferedImage(24, 16, 2);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        String spec = SPECS.get(code);
        if (spec == null) {
            StringBuilder sb = new StringBuilder("h:");

            for (int i = 0; i < fallback.length; i++) {
                sb.append(i == 0 ? "" : ",").append(fallback[i].replace("#", ""));
            }

            spec = sb.toString();
        }

        for (String layer : spec.split(";")) {
            try {
                layer(g, layer.trim());
            } catch (RuntimeException var10) {
            }
        }

        finish(g);
        g.dispose();
        round(img);
        return img;
    }

    private static Color c(String hex) {
        return new Color(Integer.parseInt(hex.trim(), 16));
    }

    private static double f(String s) {
        return Double.parseDouble(s.trim());
    }

    private static void layer(Graphics2D g, String layer) {
        int colon = layer.indexOf(58);
        String op = colon < 0 ? layer : layer.substring(0, colon);
        String rest = colon < 0 ? "" : layer.substring(colon + 1);
        String weights = null;
        int at = rest.indexOf(64);
        if (at >= 0) {
            weights = rest.substring(at + 1);
            rest = rest.substring(0, at);
        }

        String[] a = rest.isEmpty() ? new String[0] : rest.split(",");
        switch (op) {
            case "h":
                stripes(g, a, weights, true);
                break;
            case "v":
                stripes(g, a, weights, false);
                break;
            case "stripes": {
                int n = Integer.parseInt(a[2].trim());

                for (int i = 0; i < n; i++) {
                    g.setColor(c(i % 2 == 0 ? a[0] : a[1]));
                    g.fill(new java.awt.geom.Rectangle2D.Double(0.0, (double)(16 * i) / n, 24.0, 16.0 / n + 0.05));
                }
                break;
            }
            case "rect":
                g.setColor(c(a[0]));
                g.fill(new java.awt.geom.Rectangle2D.Double(f(a[1]) * 24.0, f(a[2]) * 16.0, f(a[3]) * 24.0, f(a[4]) * 16.0));
                break;
            case "tri": {
                g.setColor(c(a[0]));
                double d = f(a[1]) * 24.0;
                g.fill(poly(0.0, 0.0, d, 8.0, 0.0, 16.0));
                break;
            }
            case "trap": {
                g.setColor(c(a[0]));
                double d = f(a[1]) * 24.0;
                g.fill(poly(0.0, 0.0, d, 5.333333333333333, d, 10.666666666666666, 0.0, 16.0));
                break;
            }
            case "disc": {
                g.setColor(c(a[0]));
                double r = f(a[3]) * 16.0;
                g.fill(new java.awt.geom.Ellipse2D.Double(f(a[1]) * 24.0 - r, f(a[2]) * 16.0 - r, r * 2.0, r * 2.0));
                break;
            }
            case "ring": {
                g.setColor(c(a[0]));
                double r = f(a[3]) * 16.0;
                g.setStroke(new BasicStroke(1.2F));
                g.draw(new java.awt.geom.Ellipse2D.Double(f(a[1]) * 24.0 - r, f(a[2]) * 16.0 - r, r * 2.0, r * 2.0));
                break;
            }
            case "ellipse": {
                g.setColor(c(a[0]));
                double rx = f(a[3]) * 16.0;
                double ry = f(a[4]) * 16.0;
                g.fill(new java.awt.geom.Ellipse2D.Double(f(a[1]) * 24.0 - rx, f(a[2]) * 16.0 - ry, rx * 2.0, ry * 2.0));
                break;
            }
            case "star":
                g.setColor(c(a[0]));
                g.fill(star(f(a[1]) * 24.0, f(a[2]) * 16.0, f(a[3]) * 16.0, 5, 0.42));
                break;
            case "pentagram":
                g.setColor(c(a[0]));
                g.setStroke(new BasicStroke(1.3F, 1, 0));
                g.draw(pentagram(f(a[1]) * 24.0, f(a[2]) * 16.0, f(a[3]) * 16.0));
                break;
            case "hexagram": {
                g.setColor(c(a[0]));
                g.setStroke(new BasicStroke(1.1F));
                double cx = f(a[1]) * 24.0;
                double cy = f(a[2]) * 16.0;
                double r = f(a[3]) * 16.0;
                g.draw(regular(cx, cy, r, 3, -Math.PI / 2));
                g.draw(regular(cx, cy, r, 3, Math.PI / 2));
                break;
            }
            case "crescent": {
                g.setColor(c(a[0]));
                double cx = f(a[1]) * 24.0;
                double cy = f(a[2]) * 16.0;
                double r = f(a[3]) * 16.0;
                double dx = r * 0.32;
                double dy = 0.0;
                if (a.length > 4 && a[4].trim().equals("u")) {
                    dx = 0.0;
                    dy = -r * 0.32;
                }

                Area moon = new Area(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, r * 2.0, r * 2.0));
                double r2 = r * 0.8;
                moon.subtract(new Area(new java.awt.geom.Ellipse2D.Double(cx + dx - r2 + (dx == 0.0 ? 0.0 : r * 0.08), cy + dy - r2, r2 * 2.0, r2 * 2.0)));
                g.fill(moon);
                break;
            }
            case "cross": {
                g.setColor(c(a[0]));
                double t = f(a[3]) * 16.0;
                g.fill(new java.awt.geom.Rectangle2D.Double(0.0, f(a[2]) * 16.0 - t / 2.0, 24.0, t));
                g.fill(new java.awt.geom.Rectangle2D.Double(f(a[1]) * 24.0 - t / 2.0, 0.0, t, 16.0));
                break;
            }
            case "ccross": {
                g.setColor(c(a[0]));
                double x = f(a[1]) * 24.0;
                double y = f(a[2]) * 16.0;
                double w = f(a[3]) * 24.0;
                double h = f(a[4]) * 16.0;
                double t = f(a[5]) * 16.0;
                g.fill(new java.awt.geom.Rectangle2D.Double(x, y + h / 2.0 - t / 2.0, w, t));
                g.fill(new java.awt.geom.Rectangle2D.Double(x + w / 2.0 - t / 2.0, y, t, h));
                break;
            }
            case "nordic": {
                g.setColor(c(a[0]));
                g.fillRect(0, 0, 24, 16);
                double cx = 8.64;
                g.setColor(c(a[1]));
                double t = 16.0 * (a.length > 2 ? 0.28 : 0.2);
                g.fill(new java.awt.geom.Rectangle2D.Double(0.0, 8.0 - t / 2.0, 24.0, t));
                g.fill(new java.awt.geom.Rectangle2D.Double(cx - t / 2.0, 0.0, t, 16.0));
                if (a.length > 2) {
                    g.setColor(c(a[2]));
                    double t2 = 2.08;
                    g.fill(new java.awt.geom.Rectangle2D.Double(0.0, 8.0 - t2 / 2.0, 24.0, t2));
                    g.fill(new java.awt.geom.Rectangle2D.Double(cx - t2 / 2.0, 0.0, t2, 16.0));
                }
                break;
            }
            case "scross": {
                g.setColor(c(a[0]));
                double s = f(a[1]) * 16.0;
                double cx = a.length > 3 ? f(a[2]) * 24.0 : 12.0;
                double cy = a.length > 3 ? f(a[3]) * 16.0 : 8.0;
                double t = s / 3.0;
                g.fill(new java.awt.geom.Rectangle2D.Double(cx - s / 2.0, cy - t / 2.0, s, t));
                g.fill(new java.awt.geom.Rectangle2D.Double(cx - t / 2.0, cy - s / 2.0, t, s));
                break;
            }
            case "saltire":
                g.setColor(c(a[0]));
                g.setStroke(new BasicStroke((float)(f(a[1]) * 16.0)));
                g.draw(new java.awt.geom.Line2D.Double(-1.0, -1.0, 25.0, 17.0));
                g.draw(new java.awt.geom.Line2D.Double(-1.0, 17.0, 25.0, -1.0));
                break;
            case "diag":
                g.setColor(c(a[0]));
                g.setStroke(new BasicStroke((float)(f(a[1]) * 16.0)));
                g.draw(new java.awt.geom.Line2D.Double(-2.0, 17.333333333333332, 26.0, -1.3333333333333333));
                break;
            case "diagr":
                g.setColor(c(a[0]));
                g.setStroke(new BasicStroke((float)(f(a[1]) * 16.0)));
                g.draw(new java.awt.geom.Line2D.Double(-2.0, -1.3333333333333333, 26.0, 17.333333333333332));
                break;
            case "diagsplit":
                g.setColor(c(a[0]));
                g.fill(poly(0.0, 0.0, 24.0, 0.0, 0.0, 16.0));
                g.setColor(c(a[1]));
                g.fill(poly(24.0, 0.0, 24.0, 16.0, 0.0, 16.0));
                break;
            case "diagsplit2":
                g.setColor(c(a[0]));
                g.fill(poly(0.0, 0.0, 24.0, 0.0, 24.0, 16.0));
                g.setColor(c(a[1]));
                g.fill(poly(0.0, 0.0, 24.0, 16.0, 0.0, 16.0));
                break;
            case "quarters":
                g.setColor(c(a[0]));
                g.fill(new java.awt.geom.Rectangle2D.Double(0.0, 0.0, 12.0, 8.0));
                g.setColor(c(a[1]));
                g.fill(new java.awt.geom.Rectangle2D.Double(12.0, 0.0, 12.0, 8.0));
                g.setColor(c(a[2]));
                g.fill(new java.awt.geom.Rectangle2D.Double(0.0, 8.0, 12.0, 8.0));
                g.setColor(c(a[3]));
                g.fill(new java.awt.geom.Rectangle2D.Double(12.0, 8.0, 12.0, 8.0));
                break;
            case "xquarters":
                g.setColor(c(a[0]));
                g.fill(poly(0.0, 0.0, 12.0, 8.0, 0.0, 16.0));
                g.fill(poly(24.0, 0.0, 12.0, 8.0, 24.0, 16.0));
                g.setColor(c(a[1]));
                g.fill(poly(0.0, 0.0, 24.0, 0.0, 12.0, 8.0));
                g.fill(poly(0.0, 16.0, 24.0, 16.0, 12.0, 8.0));
                break;
            case "sidetri":
                g.setColor(c(a[0]));
                g.fill(poly(0.0, 0.0, 12.0, 8.0, 0.0, 16.0));
                g.fill(poly(24.0, 0.0, 12.0, 8.0, 24.0, 16.0));
                break;
            case "border": {
                g.setColor(c(a[0]));
                double t = f(a[1]) * 16.0;
                Area frame = new Area(new java.awt.geom.Rectangle2D.Double(0.0, 0.0, 24.0, 16.0));
                frame.subtract(new Area(new java.awt.geom.Rectangle2D.Double(t, t, 24.0 - 2.0 * t, 16.0 - 2.0 * t)));
                g.fill(frame);
                break;
            }
            case "serrate": {
                g.setColor(c(a[0]));
                double x = f(a[1]) * 24.0;
                int n = Integer.parseInt(a[2].trim());
                g.fill(new java.awt.geom.Rectangle2D.Double(0.0, 0.0, x, 16.0));
                double depth = 2.16;

                for (int i = 0; i < n; i++) {
                    double y0 = (double)(16 * i) / n;
                    double y1 = (double)(16 * (i + 1)) / n;
                    g.fill(poly(x - 0.2, y0, x + depth, (y0 + y1) / 2.0, x - 0.2, y1));
                }
                break;
            }
            case "rays": {
                double r = Math.hypot(24.0, 16.0) + 2.0;

                for (int i = 0; i < a.length; i++) {
                    double t0 = (-Math.PI / 2) * i / a.length;
                    double t1 = (-Math.PI / 2) * (i + 1) / a.length;
                    g.setColor(c(a[i]));
                    g.fill(poly(0.0, 16.0, Math.cos(t0) * r, 16.0 + Math.sin(t0) * r, Math.cos(t1) * r, 16.0 + Math.sin(t1) * r));
                }
                break;
            }
            case "diamond": {
                g.setColor(c(a[0]));
                double cx = f(a[1]) * 24.0;
                double cy = f(a[2]) * 16.0;
                double rx = f(a[3]) * 24.0;
                double ry = f(a[4]) * 16.0;
                g.fill(poly(cx, cy - ry, cx + rx, cy, cx, cy + ry, cx - rx, cy));
                break;
            }
            case "triup": {
                g.setColor(c(a[0]));
                double cx = f(a[1]) * 24.0;
                double cy = f(a[2]) * 16.0;
                double s = f(a[3]) * 16.0;
                g.fill(poly(cx, cy - s / 2.0, cx + s / 2.0, cy + s / 2.0, cx - s / 2.0, cy + s / 2.0));
                break;
            }
            case "halfdisc": {
                double cx = f(a[2]) * 24.0;
                double cy = f(a[3]) * 16.0;
                double r = f(a[4]) * 16.0;
                g.setColor(c(a[0]));
                g.fill(new java.awt.geom.Arc2D.Double(cx - r, cy - r, r * 2.0, r * 2.0, 0.0, 180.0, 2));
                g.setColor(c(a[1]));
                g.fill(new java.awt.geom.Arc2D.Double(cx - r, cy - r, r * 2.0, r * 2.0, 180.0, 180.0, 2));
                break;
            }
            case "sun": {
                g.setColor(c(a[0]));
                double cx = f(a[1]) * 24.0;
                double cy = f(a[2]) * 16.0;
                double r = f(a[3]) * 16.0;
                g.fill(star(cx, cy, r * 1.45, 12, 0.62));
                g.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, r * 2.0, r * 2.0));
                break;
            }
            case "sunrays": {
                g.setColor(c(a[0]));
                double cx = f(a[1]) * 24.0;
                double cy = f(a[2]) * 16.0;
                double r = Math.hypot(24.0, 16.0);

                for (int i = 0; i < 8; i++) {
                    double tx = (Math.PI * 2) * i / 8.0;
                    double wx = 0.16;
                    g.fill(poly(cx, cy, cx + Math.cos(tx - wx) * r, cy + Math.sin(tx - wx) * r, cx + Math.cos(tx + wx) * r, cy + Math.sin(tx + wx) * r));
                }
                break;
            }
            case "checker": {
                double cx = f(a[3]) * 24.0;
                double cy = f(a[4]) * 16.0;
                double s = f(a[5]) * 16.0;
                int n = 4;
                double cell = s * 2.0 / n;

                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < n; j++) {
                        g.setColor(c((i + j) % 2 == 0 ? a[0] : a[1]));
                        g.fill(new java.awt.geom.Rectangle2D.Double(cx - s + i * cell, cy - s + j * cell, cell, cell));
                    }
                }
                break;
            }
            case "shield": {
                g.setColor(c(a[0]));
                double cx = f(a[1]) * 24.0;
                double cy = f(a[2]) * 16.0;
                double s = f(a[3]) * 16.0;
                Path2D p = new java.awt.geom.Path2D.Double();
                p.moveTo(cx - s * 0.8, cy - s);
                p.lineTo(cx + s * 0.8, cy - s);
                p.lineTo(cx + s * 0.8, cy + s * 0.1);
                p.quadTo(cx + s * 0.75, cy + s * 0.8, cx, cy + s * 1.1);
                p.quadTo(cx - s * 0.75, cy + s * 0.8, cx - s * 0.8, cy + s * 0.1);
                p.closePath();
                g.fill(p);
                break;
            }
            case "eagle":
                g.setColor(c(a[0]));
                g.fill(eagle(f(a[1]) * 24.0, f(a[2]) * 16.0, f(a[3]) * 16.0));
                break;
            case "leaf":
                g.setColor(c(a[0]));
                g.fill(leaf(f(a[1]) * 24.0, f(a[2]) * 16.0, f(a[3]) * 16.0));
                break;
            case "taegeuk": {
                double cx = f(a[2]) * 24.0;
                double cy = f(a[3]) * 16.0;
                double r = f(a[4]) * 16.0;
                g.setColor(c(a[1]));
                g.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - r, r * 2.0, r * 2.0));
                g.setColor(c(a[0]));
                g.fill(new java.awt.geom.Arc2D.Double(cx - r, cy - r, r * 2.0, r * 2.0, 0.0, 180.0, 2));
                double h = r / 2.0;
                g.fill(new java.awt.geom.Ellipse2D.Double(cx - r, cy - h, r, r));
                g.setColor(c(a[1]));
                g.fill(new java.awt.geom.Ellipse2D.Double(cx, cy - h, r, r));
                break;
            }
            case "ujack":
                unionJack(g, f(a[0]) * 24.0, f(a[1]) * 16.0, f(a[2]) * 24.0, f(a[3]) * 16.0);
                break;
            case "usstars": {
                g.setColor(Color.WHITE);
                double x = f(a[0]) * 24.0;
                double y = f(a[1]) * 16.0;
                double w = f(a[2]) * 24.0;
                double h = f(a[3]) * 16.0;

                for (int i = 0; i < 5; i++) {
                    for (int j = 0; j < 4; j++) {
                        double px = x + w * (i + 0.5) / 5.0;
                        double py = y + h * (j + 0.5) / 4.0;
                        g.fill(new java.awt.geom.Rectangle2D.Double(px - 0.4, py - 0.4, 0.8, 0.8));
                    }
                }
                break;
            }
            case "za":
                southAfrica(g);
                break;
            case "ag":
                antigua(g);
                break;
            case "ba":
                bosnia(g);
                break;
            case "nepal":
                nepal(g);
        }
    }

    private static void stripes(Graphics2D g, String[] colors, String weights, boolean horizontal) {
        double[] w = new double[colors.length];
        double total = 0.0;
        String[] ws = weights == null ? null : weights.split(",");

        for (int i = 0; i < colors.length; i++) {
            w[i] = ws != null && i < ws.length ? f(ws[i]) : 1.0;
            total += w[i];
        }

        double pos = 0.0;
        double size = horizontal ? 16.0 : 24.0;

        for (int i = 0; i < colors.length; i++) {
            double len = w[i] / total * size;
            g.setColor(c(colors[i]));
            if (horizontal) {
                g.fill(new java.awt.geom.Rectangle2D.Double(0.0, pos, 24.0, len + 0.05));
            } else {
                g.fill(new java.awt.geom.Rectangle2D.Double(pos, 0.0, len + 0.05, 16.0));
            }

            pos += len;
        }
    }

    private static Path2D poly(double... xy) {
        Path2D p = new java.awt.geom.Path2D.Double();
        p.moveTo(xy[0], xy[1]);

        for (int i = 2; i < xy.length; i += 2) {
            p.lineTo(xy[i], xy[i + 1]);
        }

        p.closePath();
        return p;
    }

    private static Path2D star(double cx, double cy, double r, int points, double inner) {
        Path2D p = new java.awt.geom.Path2D.Double();

        for (int i = 0; i < points * 2; i++) {
            double rr = i % 2 == 0 ? r : r * inner;
            double t = (-Math.PI / 2) + Math.PI * i / points;
            if (i == 0) {
                p.moveTo(cx + Math.cos(t) * rr, cy + Math.sin(t) * rr);
            } else {
                p.lineTo(cx + Math.cos(t) * rr, cy + Math.sin(t) * rr);
            }
        }

        p.closePath();
        return p;
    }

    private static Path2D pentagram(double cx, double cy, double r) {
        Path2D p = new java.awt.geom.Path2D.Double();

        for (int i = 0; i <= 5; i++) {
            double t = (-Math.PI / 2) + (Math.PI * 4) * i / 5.0;
            if (i == 0) {
                p.moveTo(cx + Math.cos(t) * r, cy + Math.sin(t) * r);
            } else {
                p.lineTo(cx + Math.cos(t) * r, cy + Math.sin(t) * r);
            }
        }

        return p;
    }

    private static Path2D regular(double cx, double cy, double r, int n, double start) {
        Path2D p = new java.awt.geom.Path2D.Double();

        for (int i = 0; i < n; i++) {
            double t = start + (Math.PI * 2) * i / n;
            if (i == 0) {
                p.moveTo(cx + Math.cos(t) * r, cy + Math.sin(t) * r);
            } else {
                p.lineTo(cx + Math.cos(t) * r, cy + Math.sin(t) * r);
            }
        }

        p.closePath();
        return p;
    }

    private static Shape eagle(double cx, double cy, double s) {
        Area a = new Area(new java.awt.geom.Ellipse2D.Double(cx - s * 0.28, cy - s * 0.45, s * 0.56, s * 1.1));
        a.add(new Area(new java.awt.geom.Ellipse2D.Double(cx - s * 0.2, cy - s * 0.85, s * 0.4, s * 0.4)));
        a.add(new Area(poly(cx - s * 0.2, cy - s * 0.3, cx - s * 1.15, cy - s * 0.75, cx - s * 0.95, cy + s * 0.1, cx - s * 0.2, cy + s * 0.25)));
        a.add(new Area(poly(cx + s * 0.2, cy - s * 0.3, cx + s * 1.15, cy - s * 0.75, cx + s * 0.95, cy + s * 0.1, cx + s * 0.2, cy + s * 0.25)));
        a.add(new Area(poly(cx - s * 0.25, cy + s * 0.5, cx + s * 0.25, cy + s * 0.5, cx, cy + s * 1.0)));
        return a;
    }

    private static Shape leaf(double cx, double cy, double s) {
        double[][] pts = new double[][]{
            {0.0, -1.0},
            {0.18, -0.62},
            {0.42, -0.72},
            {0.36, -0.3},
            {0.82, -0.52},
            {0.7, -0.22},
            {0.94, -0.1},
            {0.5, 0.25},
            {0.56, 0.42},
            {0.08, 0.36},
            {0.08, 0.8},
            {-0.08, 0.8},
            {-0.08, 0.36},
            {-0.56, 0.42},
            {-0.5, 0.25},
            {-0.94, -0.1},
            {-0.7, -0.22},
            {-0.82, -0.52},
            {-0.36, -0.3},
            {-0.42, -0.72},
            {-0.18, -0.62}
        };
        Path2D p = new java.awt.geom.Path2D.Double();

        for (int i = 0; i < pts.length; i++) {
            double x = cx + pts[i][0] * s;
            double y = cy + pts[i][1] * s;
            if (i == 0) {
                p.moveTo(x, y);
            } else {
                p.lineTo(x, y);
            }
        }

        p.closePath();
        return p;
    }

    private static void unionJack(Graphics2D g, double x, double y, double w, double h) {
        Shape old = g.getClip();
        g.clip(new java.awt.geom.Rectangle2D.Double(x, y, w, h));
        g.setColor(c("012169"));
        g.fill(new java.awt.geom.Rectangle2D.Double(x, y, w, h));
        float k = (float)(h / 16.0);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3.2F * k));
        g.draw(new java.awt.geom.Line2D.Double(x, y, x + w, y + h));
        g.draw(new java.awt.geom.Line2D.Double(x, y + h, x + w, y));
        g.setColor(c("C8102E"));
        g.setStroke(new BasicStroke(1.1F * k));
        g.draw(new java.awt.geom.Line2D.Double(x, y, x + w, y + h));
        g.draw(new java.awt.geom.Line2D.Double(x, y + h, x + w, y));
        g.setColor(Color.WHITE);
        double t = h * 0.34;
        g.fill(new java.awt.geom.Rectangle2D.Double(x, y + h / 2.0 - t / 2.0, w, t));
        g.fill(new java.awt.geom.Rectangle2D.Double(x + w / 2.0 - t / 2.0, y, t, h));
        g.setColor(c("C8102E"));
        double t2 = h * 0.2;
        g.fill(new java.awt.geom.Rectangle2D.Double(x, y + h / 2.0 - t2 / 2.0, w, t2));
        g.fill(new java.awt.geom.Rectangle2D.Double(x + w / 2.0 - t2 / 2.0, y, t2, h));
        g.setClip(old);
    }

    private static void southAfrica(Graphics2D g) {
        g.setColor(c("DE3831"));
        g.fillRect(0, 0, 24, 8);
        g.setColor(c("002395"));
        g.fillRect(0, 8, 24, 8);
        g.setColor(Color.WHITE);
        Path2D y = new java.awt.geom.Path2D.Double();
        y.moveTo(0.0, 0.0);
        y.lineTo(10.08, 8.0);
        y.lineTo(0.0, 16.0);
        g.setStroke(new BasicStroke(5.76F));
        g.draw(y);
        g.fill(new java.awt.geom.Rectangle2D.Double(9.600000000000001, 5.12, 14.399999999999999, 5.76));
        g.setColor(c("007749"));
        g.setStroke(new BasicStroke(3.2F));
        g.draw(y);
        g.fill(new java.awt.geom.Rectangle2D.Double(9.600000000000001, 6.4, 14.399999999999999, 3.2));
        g.setColor(c("FFB81C"));
        g.fill(poly(0.0, 2.88, 7.199999999999999, 8.0, 0.0, 13.12));
        g.setColor(Color.BLACK);
        g.fill(poly(0.0, 4.32, 5.28, 8.0, 0.0, 11.68));
    }

    private static void antigua(Graphics2D g) {
        g.setColor(c("CE1126"));
        g.fillRect(0, 0, 24, 16);
        Shape old = g.getClip();
        g.clip(poly(0.0, 0.0, 24.0, 0.0, 12.0, 16.0));
        g.setColor(Color.BLACK);
        g.fill(new java.awt.geom.Rectangle2D.Double(0.0, 0.0, 24.0, 6.72));
        g.setColor(c("0072C6"));
        g.fill(new java.awt.geom.Rectangle2D.Double(0.0, 6.72, 24.0, 3.52));
        g.setColor(Color.WHITE);
        g.fill(new java.awt.geom.Rectangle2D.Double(0.0, 10.24, 24.0, 6.4));
        g.setColor(c("FCD116"));
        g.fill(star(12.0, 6.72, 3.52, 8, 0.55));
        g.setClip(old);
    }

    private static void bosnia(Graphics2D g) {
        g.setColor(c("002395"));
        g.fillRect(0, 0, 24, 16);
        g.setColor(c("FECB00"));
        g.fill(poly(6.48, 0.0, 17.52, 0.0, 17.52, 16.0));
        g.setColor(Color.WHITE);

        for (int i = 0; i < 7; i++) {
            double t = (i + 0.3) / 7.0;
            g.fill(star(4.800000000000001 + 10.8 * t, -0.32 + 16.0 * t, 1.12, 5, 0.42));
        }
    }

    private static void nepal(Graphics2D g) {
        Path2D p = new java.awt.geom.Path2D.Double();
        double x0 = 4.32;
        p.moveTo(x0, 0.4);
        p.lineTo(x0 + 11.52, 7.36);
        p.lineTo(x0 + 4.8, 7.36);
        p.lineTo(x0 + 11.52, 15.68);
        p.lineTo(x0, 15.68);
        p.closePath();
        g.setColor(c("003893"));
        g.setStroke(new BasicStroke(2.2F, 0, 0));
        g.draw(p);
        g.setColor(c("DC143C"));
        g.fill(p);
        g.setColor(Color.WHITE);
        g.fill(star(x0 + 3.2, 4.8, 1.28, 8, 0.5));
        g.fill(star(x0 + 3.52, 12.0, 1.6, 12, 0.55));
    }

    private static void finish(Graphics2D g) {
        g.setComposite(AlphaComposite.getInstance(10));
        g.setPaint(new GradientPaint(0.0F, 0.0F, new Color(255, 255, 255, 46), 0.0F, 8.0F, new Color(255, 255, 255, 0)));
        g.fillRect(0, 0, 24, 8);
        g.setPaint(new GradientPaint(0.0F, 8.8F, new Color(0, 0, 0, 0), 0.0F, 16.0F, new Color(0, 0, 0, 52)));
        g.fillRect(0, 8, 24, 8);
        g.setComposite(AlphaComposite.SrcOver);
    }

    private static void round(BufferedImage img) {
        int[][] corners = new int[][]{{0, 0}, {23, 0}, {0, 15}, {23, 15}};

        for (int[] cr : corners) {
            img.setRGB(cr[0], cr[1], 0);
            int nx = cr[0] == 0 ? 1 : 22;
            int ny = cr[1] == 0 ? 1 : 14;
            soften(img, nx, cr[1]);
            soften(img, cr[0], ny);
        }
    }

    private static void soften(BufferedImage img, int x, int y) {
        int argb = img.getRGB(x, y);
        int a = (argb >>> 24) * 5 / 8;
        img.setRGB(x, y, a << 24 | argb & 16777215);
    }

    static {
        String data = "DZ|v:006233,FFFFFF;crescent:D21034,.5,.5,.3;star:D21034,.57,.5,.11\nAO|h:CC092F,000000;star:FFCB00,.5,.5,.17\nBJ|h:FCD116,E8112D;rect:008751,0,0,.4,1\nBW|h:75AADB,FFFFFF,000000,FFFFFF,75AADB@9,1,4,1,9\nBF|h:EF2B2D,009E49;star:FCD116,.5,.5,.18\nBI|h:CE1126;sidetri:1EB53A;saltire:FFFFFF,.13;disc:FFFFFF,.5,.5,.24;star:CE1126,.5,.5,.09\nCV|h:003893,FFFFFF,CF2027,FFFFFF,003893@6,1,1,1,3;ring:F7D116,.38,.62,.22\nCM|v:007A5E,CE1126,FCD116;star:FCD116,.5,.5,.16\nCF|h:003082,FFFFFF,289728,FFCE00;rect:D21034,.42,0,.16,1;star:FFCE00,.12,.13,.1\nTD|v:002664,FECB00,C60C30\nKM|h:FFC61E,FFFFFF,CE1126,3A75C4;tri:3D8E33,.42;crescent:FFFFFF,.15,.5,.2\nCG|diagsplit:009543,DC241F;diag:FBDE4A,.3\nCD|h:007FFF;diag:F7D618,.36;diag:CE1021,.24;star:F7D618,.18,.25,.14\nCI|v:F77F00,FFFFFF,009E60\nDJ|h:6AB2E7,12AD2B;tri:FFFFFF,.45;star:D7141A,.15,.5,.1\nEG|h:CE1126,FFFFFF,000000;eagle:C09300,.5,.5,.14\nGQ|h:3E9A00,FFFFFF,E32118;tri:0073CE,.28\nER|h:12AD2B,4189DD;tri:EA0437,1;ring:FFC726,.28,.5,.18\nSZ|h:3E5EB9,FFD900,B10C0C,FFD900,3E5EB9@3,1,8,1,3;ellipse:FFFFFF,.5,.5,.34,.16;rect:000000,.36,.4,.14,.2\nET|h:078930,FCDD09,DA121A;disc:0F47AF,.5,.5,.24;pentagram:FCDD09,.5,.5,.16\nGA|h:009E60,FCD116,3A75C4\nGM|h:CE1126,FFFFFF,0C1C8C,FFFFFF,3A7728@6,1,4,1,6\nGH|h:CE1126,FCD116,006B3F;star:000000,.5,.5,.16\nGN|v:CE1126,FCD116,009460\nGW|h:FCD116,009E49;rect:CE1126,0,0,.38,1;star:000000,.19,.5,.15\nKE|h:000000,FFFFFF,BB0000,FFFFFF,006600@6,1,6,1,6;ellipse:BB0000,.5,.5,.14,.42;ellipse:000000,.5,.5,.06,.26\nLS|h:00209F,FFFFFF,009543@3,4,3;triup:000000,.5,.5,.18\nLR|stripes:BF0A30,FFFFFF,11;rect:002868,0,0,.42,.55;star:FFFFFF,.21,.27,.16\nLY|h:E70013,000000,239E46@1,2,1;crescent:FFFFFF,.47,.5,.18;star:FFFFFF,.57,.5,.07\nMG|h:FC3D32,007E3A;rect:FFFFFF,0,0,.33,1\nMW|h:000000,CE1126,339E35;disc:CE1126,.5,.32,.16\nML|v:14B53A,FCD116,CE1126\nMR|h:D01C1F,006233,D01C1F@1,6,1;crescent:FFC400,.5,.56,.24,u;star:FFC400,.5,.36,.09\nMU|h:EA2839,1A206D,FFD500,00A551\nMA|h:C1272D;pentagram:006233,.5,.53,.32\nMZ|h:009A44,FFFFFF,000000,FFFFFF,FCE100@5,1,5,1,5;tri:D21034,.42;star:FCE100,.14,.5,.12\nNA|diagsplit:003580,009543;diag:FFFFFF,.34;diag:D21034,.24;sun:FFCE00,.18,.24,.1\nNE|h:E05206,FFFFFF,0DB02B;disc:E05206,.5,.5,.12\nNG|v:008751,FFFFFF,008751\nRW|h:00A1DE,FAD201,20603D@2,1,1;sun:FAD201,.8,.25,.1\nST|h:12AD2B,FFCE00,12AD2B@1,2,1;tri:D21034,.3;star:000000,.5,.5,.1;star:000000,.72,.5,.1\nSN|v:00853F,FDEF42,E31B23;star:00853F,.5,.5,.15\nSC|rays:003F87,FCD856,D62828,FFFFFF,007A3D\nSL|h:1EB53A,FFFFFF,0072C6\nSO|h:4189DD;star:FFFFFF,.5,.5,.28\nZA|za\nSS|h:000000,FFFFFF,DA121A,FFFFFF,078930@6,1,6,1,6;tri:0F47AF,.38;star:FCDD09,.12,.5,.11\nSD|h:D21034,FFFFFF,000000;tri:007229,.36\nTZ|diagsplit:1EB53A,00A3DD;diag:FCD116,.38;diag:000000,.26\nTG|h:006A4E,FFCE00,006A4E,FFCE00,006A4E;rect:D21034,0,0,.4,.6;star:FFFFFF,.2,.3,.15\nTN|h:E70013;disc:FFFFFF,.5,.5,.31;crescent:E70013,.5,.5,.24;star:E70013,.55,.5,.1\nUG|h:000000,FCDC04,D90000,000000,FCDC04,D90000;disc:FFFFFF,.5,.5,.24;eagle:000000,.5,.5,.12\nZM|h:198A00;rect:DE2010,.67,.38,.11,.62;rect:000000,.78,.38,.11,.62;rect:EF7D00,.89,.38,.11,.62;eagle:EF7D00,.83,.2,.12\nZW|h:319208,FFD200,DE2010,000000,DE2010,FFD200,319208;tri:000000,.46;tri:FFFFFF,.42;star:DE2010,.13,.5,.12\nEH|h:000000,FFFFFF,007A3D;tri:C4111B,.33;crescent:C4111B,.5,.5,.16;star:C4111B,.57,.5,.07\nAF|v:000000,D32011,007A36;ring:FFFFFF,.5,.5,.18\nAM|h:D90012,0033A0,F2A800\nAZ|h:0092BC,E4002B,00AF66;crescent:FFFFFF,.47,.5,.16;star:FFFFFF,.57,.5,.07\nBH|h:CE1126;serrate:FFFFFF,.25,5\nBD|h:006A4E;disc:F42A41,.45,.5,.3\nBT|diagsplit:FFD520,FF4E12;eagle:FFFFFF,.5,.5,.2\nBN|h:F7E017;diagr:FFFFFF,.3;diagr:000000,.13;disc:CF1126,.5,.5,.13\nKH|h:032EA1,E00025,032EA1@1,2,1;rect:FFFFFF,.36,.38,.28,.24\nCN|h:EE1C25;star:FFFF00,.17,.28,.17;star:FFFF00,.33,.12,.06;star:FFFF00,.41,.24,.06;star:FFFF00,.41,.4,.06;star:FFFF00,.33,.52,.06\nGE|h:FFFFFF;cross:FF0000,.5,.5,.18;scross:FF0000,.16,.22,.24;scross:FF0000,.16,.78,.24;scross:FF0000,.16,.22,.76;scross:FF0000,.16,.78,.76\nHK|h:DE2910;star:FFFFFF,.5,.5,.32\nIN|h:FF9933,FFFFFF,138808;ring:000080,.5,.5,.14\nID|h:FF0000,FFFFFF\nIR|h:239F40,FFFFFF,DA0000;disc:DA0000,.5,.5,.1\nIQ|h:CE1126,FFFFFF,000000;rect:007A3D,.32,.45,.36,.1\nIL|h:FFFFFF,0038B8,FFFFFF,0038B8,FFFFFF@2,1.5,5,1.5,2;hexagram:0038B8,.5,.5,.21\nJP|h:FFFFFF;disc:BC002D,.5,.5,.3\nJO|h:000000,FFFFFF,007A3D;tri:CE1126,.5;star:FFFFFF,.18,.5,.09\nKZ|h:00AFCA;sun:FEC50C,.5,.42,.16;rect:FEC50C,.05,.05,.05,.9\nKW|h:007A3D,FFFFFF,CE1126;trap:000000,.25\nKG|h:E8112D;sun:FFEF00,.5,.5,.24;ring:E8112D,.5,.5,.12\nLA|h:CE1126,002868,CE1126@1,2,1;disc:FFFFFF,.5,.5,.2\nLB|h:ED1C24,FFFFFF,ED1C24@1,2,1;triup:00A651,.5,.5,.34\nMO|h:00785E;disc:FFFFFF,.5,.56,.18;star:FFDE00,.5,.22,.08\nMY|stripes:CC0001,FFFFFF,14;rect:010066,0,0,.5,.57;crescent:FFCC00,.2,.28,.2;star:FFCC00,.34,.28,.1\nMV|h:D21034;rect:007E3A,.22,.22,.56,.56;crescent:FFFFFF,.53,.5,.2\nMN|v:C4272F,015197,C4272F;rect:F9CF02,.13,.28,.07,.5;disc:F9CF02,.165,.2,.06\nMM|h:FECB00,34B233,EA2839;star:FFFFFF,.5,.54,.38\nNP|nepal\nKP|h:024FA2,FFFFFF,ED1C27,FFFFFF,024FA2@3,.5,8,.5,3;disc:FFFFFF,.36,.5,.2;star:ED1C27,.36,.5,.17\nOM|h:FFFFFF,DB161B,008000;rect:DB161B,0,0,.26,1;eagle:FFFFFF,.13,.2,.1\nPK|v:FFFFFF,01411C@1,3;crescent:FFFFFF,.6,.55,.28;star:FFFFFF,.71,.37,.09\nPS|h:000000,FFFFFF,009736;tri:EE2A35,.42\nPH|h:0038A8,CE1126;tri:FFFFFF,.45;sun:FCD116,.16,.5,.09\nQA|h:8A1538;serrate:FFFFFF,.3,9\nSA|h:006C35;rect:FFFFFF,.25,.33,.5,.14;rect:FFFFFF,.25,.6,.5,.05\nSG|h:EF3340,FFFFFF;crescent:FFFFFF,.18,.26,.17;star:FFFFFF,.3,.22,.05;star:FFFFFF,.38,.3,.05;star:FFFFFF,.3,.38,.05\nKR|h:FFFFFF;taegeuk:CD2E3A,0047A0,.5,.5,.27;rect:000000,.1,.12,.1,.18;rect:000000,.8,.12,.1,.18;rect:000000,.1,.7,.1,.18;rect:000000,.8,.7,.1,.18\nLK|h:FFBE29;rect:00534E,.04,.07,.13,.86;rect:FF7300,.19,.07,.13,.86;rect:8D153A,.36,.07,.6,.86;eagle:FFBE29,.66,.5,.16\nSY|h:CE1126,FFFFFF,000000;star:007A3D,.36,.5,.1;star:007A3D,.64,.5,.1\nTW|h:FE0000;rect:000095,0,0,.5,.5;sun:FFFFFF,.25,.25,.13\nTJ|h:CC0000,FFFFFF,006600@2,3,2;triup:F8C300,.5,.5,.14\nTH|h:A51931,F4F5F8,2D2A4A,F4F5F8,A51931@1,1,2,1,1\nTL|h:DC241F;tri:FFC726,.5;tri:000000,.33;star:FFFFFF,.11,.5,.11\nTR|h:E30A17;crescent:FFFFFF,.4,.5,.26;star:FFFFFF,.56,.5,.1\nTM|h:00843D;rect:D22630,.17,0,.16,1;crescent:FFFFFF,.45,.28,.14;star:FFFFFF,.53,.28,.05\nAE|h:00732F,FFFFFF,000000;rect:FF0000,0,0,.25,1\nUZ|h:0099B5,CE1126,FFFFFF,CE1126,1EB53A@10,1,9,1,10;crescent:FFFFFF,.14,.18,.12\nVN|h:DA251D;star:FFFF00,.5,.5,.32\nYE|h:CE1126,FFFFFF,000000\nAL|h:E41E20;eagle:000000,.5,.5,.32\nAD|v:10069F,FEDF00,D50032;shield:C7B37F,.5,.5,.16\nAT|h:ED2939,FFFFFF,ED2939\nBY|h:C8313E,4AA657@2,1;rect:FFFFFF,0,0,.11,1;rect:C8313E,.03,0,.05,1\nBE|v:000000,FDDA24,EF3340\nBA|ba\nBG|h:FFFFFF,00966E,D62612\nHR|h:FF0000,FFFFFF,171796;checker:FF0000,FFFFFF,.5,.5,.2\nCY|h:FFFFFF;ellipse:D57800,.5,.42,.26,.12;rect:4E5B31,.38,.66,.24,.06\nCZ|h:FFFFFF,D7141A;tri:11457E,.5\nDK|nordic:C8102E,FFFFFF\nEE|h:0072CE,000000,FFFFFF\nFI|nordic:FFFFFF,002F6C\nFR|v:0055A4,FFFFFF,EF4135\nDE|h:000000,DD0000,FFCE00\nGR|stripes:0D5EAF,FFFFFF,9;rect:0D5EAF,0,0,.37,.56;ccross:FFFFFF,0,0,.37,.56,.11\nHU|h:CE2939,FFFFFF,477050\nIS|nordic:02529C,FFFFFF,DC1E35\nIE|v:169B62,FFFFFF,FF883E\nIT|v:009246,FFFFFF,CE2B37\nXK|h:244AA5;ellipse:D0A650,.5,.58,.2,.17;star:FFFFFF,.3,.23,.05;star:FFFFFF,.4,.19,.05;star:FFFFFF,.5,.17,.05;star:FFFFFF,.6,.19,.05;star:FFFFFF,.7,.23,.05\nLV|h:9E3039,FFFFFF,9E3039@2,1,2\nLI|h:002B7F,CE1126;triup:FFD83D,.25,.27,.12\nLT|h:FDB913,006A44,C1272D\nLU|h:ED2939,FFFFFF,00A1DE\nMT|v:FFFFFF,CF142B;scross:A0A0A0,.14,.13,.17\nMD|v:0046AE,FFD200,CC092F;eagle:8C5A2B,.5,.5,.13\nMC|h:CE1126,FFFFFF\nME|h:C40308;border:D3AE3B,.07;eagle:D3AE3B,.5,.5,.26\nNL|h:AE1C28,FFFFFF,21468B\nMK|h:D20000;sunrays:FFE600,.5,.5;disc:FFE600,.5,.5,.18;ring:D20000,.5,.5,.12\nNO|nordic:BA0C2F,FFFFFF,00205B\nPL|h:FFFFFF,DC143C\nPT|v:006600,FF0000@2,3;disc:FFE000,.4,.5,.22;shield:FF0000,.4,.5,.14\nRO|v:002B7F,FCD116,CE1126\nRU|h:FFFFFF,0039A6,D52B1E\nSM|h:FFFFFF,5EB6E4;shield:D3AE3B,.5,.5,.15\nRS|h:C6363C,0C4076,FFFFFF;shield:C6363C,.35,.45,.2\nSK|h:FFFFFF,0B4EA2,EE1C25;shield:EE1C25,.35,.5,.24\nSI|h:FFFFFF,005DA4,ED1C24;shield:005DA4,.3,.38,.18\nES|h:AA151B,F1BF00,AA151B@1,2,1;shield:AA151B,.32,.5,.17\nSE|nordic:006AA7,FECC00\nCH|h:DA291C;scross:FFFFFF,.58\nUA|h:0057B7,FFD700\nGB|ujack:0,0,1,1\nVA|v:FFE000,FFFFFF;disc:C0C0C0,.75,.5,.11\nAG|ag\nBS|h:00778B,FFC72C,00778B;tri:000000,.42\nBB|v:00267F,FFC726,00267F;triup:000000,.5,.5,.22\nBZ|h:CE1126,003F87,CE1126@1,8,1;disc:FFFFFF,.5,.5,.28\nCA|v:FF0000,FFFFFF,FF0000@1,2,1;leaf:FF0000,.5,.5,.34\nCR|h:002B7F,FFFFFF,CE1126,FFFFFF,002B7F@1,1,2,1,1\nCU|h:002A8F,FFFFFF,002A8F,FFFFFF,002A8F;tri:CF142B,.45;star:FFFFFF,.15,.5,.13\nDM|h:006B3F;cross:FCD116,.5,.5,.2;cross:000000,.5,.5,.12;cross:FFFFFF,.5,.5,.05;disc:D41C30,.5,.5,.2\nDO|quarters:002D62,CE1126,CE1126,002D62;cross:FFFFFF,.5,.5,.18\nSV|h:0047AB,FFFFFF,0047AB;disc:FFD700,.5,.5,.1\nGL|h:FFFFFF,D00C33;halfdisc:D00C33,FFFFFF,.38,.5,.3\nGD|xquarters:007A5E,FCD116;border:CE1126,.12;disc:CE1126,.5,.5,.14;star:FCD116,.5,.5,.1\nGT|v:4997D0,FFFFFF,4997D0;disc:6C9F3B,.5,.5,.12\nHT|h:00209F,D21034;rect:FFFFFF,.38,.34,.24,.32\nHN|h:0073CF,FFFFFF,0073CF;star:0073CF,.5,.5,.08\nJM|xquarters:000000,009B3A;saltire:FED100,.18\nMX|v:006847,FFFFFF,CE1126;eagle:8C5A2B,.5,.5,.15\nNI|h:0067C6,FFFFFF,0067C6;triup:C9A227,.5,.5,.13\nPA|quarters:FFFFFF,DA121A,072357,FFFFFF;star:072357,.25,.25,.12;star:DA121A,.75,.75,.12\nPR|h:ED0000,FFFFFF,ED0000,FFFFFF,ED0000;tri:0050F0,.45;star:FFFFFF,.15,.5,.13\nKN|diagsplit:009E49,CE1126;diag:FCD116,.38;diag:000000,.27;star:FFFFFF,.38,.62,.07;star:FFFFFF,.62,.38,.07\nLC|h:65CFFF;triup:FFFFFF,.5,.55,.64;triup:000000,.5,.6,.5;triup:FCD116,.5,.72,.32\nVC|v:0072C6,FCD116,009E60@1,2,1;diamond:009E60,.42,.5,.07,.16;diamond:009E60,.58,.5,.07,.16\nTT|h:CE1126;diagr:FFFFFF,.42;diagr:000000,.3\nUS|stripes:B22234,FFFFFF,13;rect:3C3B6E,0,0,.4,.54;usstars:0,0,.4,.54\nAR|h:74ACDF,FFFFFF,74ACDF;sun:F6B40E,.5,.5,.11\nBO|h:D52B1E,F9E300,007934\nBR|h:009C3B;diamond:FFDF00,.5,.5,.42,.4;disc:002776,.5,.5,.22\nCL|h:FFFFFF,D52B1E;rect:0039A6,0,0,.33,.5;star:FFFFFF,.165,.25,.13\nCO|h:FCD116,003893,CE1126@2,1,1\nEC|h:FFDD00,034EA2,ED1C24@2,1,1;eagle:8C5A2B,.5,.45,.13\nGY|h:009E49;tri:FFFFFF,1;tri:FCD116,.94;tri:000000,.52;tri:CE1126,.46\nPY|h:D52B1E,FFFFFF,0038A8;ring:FCD116,.5,.5,.12\nPE|v:D91023,FFFFFF,D91023\nSR|h:377E3F,FFFFFF,B40A2D,FFFFFF,377E3F@2,1,4,1,2;star:ECC81D,.5,.5,.18\nUY|stripes:FFFFFF,0038A8,9;rect:FFFFFF,0,0,.38,.56;sun:FCD116,.19,.28,.12\nVE|h:FFCC00,00247D,CF142B;star:FFFFFF,.36,.56,.05;star:FFFFFF,.43,.46,.05;star:FFFFFF,.57,.46,.05;star:FFFFFF,.64,.56,.05\nAU|h:012169;ujack:0,0,.5,.5;star:FFFFFF,.25,.76,.13;star:FFFFFF,.75,.24,.07;star:FFFFFF,.68,.5,.07;star:FFFFFF,.84,.42,.07;star:FFFFFF,.75,.8,.08\nFJ|h:68BFE5;ujack:0,0,.5,.5;shield:FFFFFF,.75,.55,.2\nKI|h:CE1126,003F87,FFFFFF,003F87,FFFFFF,003F87@8,1.3,1,1.3,1,1.3;sun:FFD200,.5,.4,.13\nMH|h:003893;diag:DD7500,.14;diag:FFFFFF,.07;star:FFFFFF,.2,.25,.13\nFM|h:75B2DD;star:FFFFFF,.5,.22,.09;star:FFFFFF,.5,.78,.09;star:FFFFFF,.32,.5,.09;star:FFFFFF,.68,.5,.09\nNR|h:002B7F,FFC61E,002B7F@10,1.2,10;star:FFFFFF,.25,.72,.13\nNZ|h:00247D;ujack:0,0,.5,.5;star:CC142B,.75,.25,.08;star:CC142B,.68,.5,.08;star:CC142B,.86,.45,.08;star:CC142B,.75,.78,.08\nPW|h:4AADD6;disc:FFDE00,.43,.5,.3\nPG|diagsplit2:CE1126,000000;eagle:FCD116,.68,.3,.14;star:FFFFFF,.25,.65,.06;star:FFFFFF,.32,.8,.06;star:FFFFFF,.18,.82,.06\nWS|h:CE1126;rect:002B7F,0,0,.5,.5;star:FFFFFF,.25,.15,.06;star:FFFFFF,.16,.27,.06;star:FFFFFF,.34,.27,.06;star:FFFFFF,.25,.38,.07\nSB|diagsplit:0051BA,215B33;diag:FCD116,.12;star:FFFFFF,.12,.15,.06;star:FFFFFF,.3,.15,.06;star:FFFFFF,.21,.28,.06\nTO|h:C10000;rect:FFFFFF,0,0,.42,.5;ccross:C10000,0,0,.42,.5,.1\nTV|h:5B97B1;ujack:0,0,.5,.5;star:FFCE00,.7,.3,.07;star:FFCE00,.86,.6,.07;star:FFCE00,.6,.75,.07;star:FFCE00,.8,.86,.07\nVU|h:D21034,009543;tri:FDCE12,.46;tri:000000,.4;rect:FDCE12,.4,.46,.6,.08;disc:FDCE12,.14,.5,.08\n";

        for (String line : data.split("\n")) {
            String t = line.trim();
            int bar = t.indexOf(124);
            if (bar > 0) {
                SPECS.put(t.substring(0, bar), t.substring(bar + 1));
            }
        }
    }
}
