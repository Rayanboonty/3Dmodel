package net.vortexpvp.web;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import net.kyori.adventure.key.Key;
import net.vortexpvp.VortexPvP;

public final class SamePortHttp {
    private static final int MAX_REQUEST = 262144;
    private static final List<String> METHODS = List.of("GET ", "HEAD", "POST", "OPTI");
    private final VortexPvP plugin;
    private final WebServer web;
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "VortexWeb-MC");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean enabled = true;
    private volatile boolean installed;

    public SamePortHttp(VortexPvP plugin, WebServer web) {
        this.plugin = plugin;
        this.web = web;
    }

    public void enabled(boolean on) {
        this.enabled = on && this.installed;
        this.web.samePort(this.enabled);
    }

    public boolean installed() {
        return this.installed;
    }

    public boolean install() {
        try {
            Class<?> holder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            Class<?> listener = Class.forName("io.papermc.paper.network.ChannelInitializeListener");
            Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class[]{listener}, (p, m, args) -> {
                String var4x = m.getName();
                switch (var4x) {
                    case "afterInitChannel":
                        if (this.enabled && args != null && args.length == 1 && args[0] instanceof Channel ch) {
                            try {
                                ch.pipeline().addFirst("vortex_http", new SamePortHttp.Sniffer());
                            } catch (RuntimeException var8) {
                            }
                        }

                        return null;
                    case "hashCode":
                        return System.identityHashCode(p);
                    case "equals":
                        return p == args[0];
                    case "toString":
                        return "VortexRealm same-port HTTP";
                    default:
                        return null;
                }
            });
            Method add = holder.getMethod("addListener", Key.class, listener);
            add.invoke(null, Key.key("vortexrealm", "http"), proxy);
            this.installed = true;
            this.web.samePort(this.enabled);
            this.plugin.getLogger().info("Website and resource pack are also served on the Minecraft port.");
            return true;
        } catch (LinkageError | RuntimeException | ReflectiveOperationException var5) {
            this.plugin
                .getLogger()
                .log(
                    Level.INFO,
                    "Serving the website on the Minecraft port isn't possible here (" + var5.getClass().getSimpleName() + "); the separate web port is used."
                );
            return false;
        }
    }

    public void shutdown() {
        this.enabled = false;
        this.pool.shutdownNow();

        try {
            Class<?> holder = Class.forName("io.papermc.paper.network.ChannelInitializeListenerHolder");
            holder.getMethod("removeListener", Key.class).invoke(null, Key.key("vortexrealm", "http"));
        } catch (LinkageError | RuntimeException | ReflectiveOperationException var2) {
        }
    }

    private static void write(Channel ch, WebServer.Request req, WebServer.Response res) {
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 ").append(res.status()).append(' ').append(reason(res.status())).append("\r\n");
        h.append("Content-Type: ").append(res.type()).append("\r\n");
        boolean head = req.method().equals("HEAD");
        h.append("Content-Length: ").append(res.body().length).append("\r\n");
        h.append("X-Content-Type-Options: nosniff\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n");
        res.headers().forEach((k, v) -> h.append(k).append(": ").append(v).append("\r\n"));
        h.append("\r\n");
        ByteBuf out = Unpooled.buffer();
        out.writeBytes(h.toString().getBytes(StandardCharsets.ISO_8859_1));
        if (!head) {
            out.writeBytes(res.body());
        }

        ch.writeAndFlush(out).addListener(ChannelFutureListener.CLOSE);
    }

    private static String reason(int s) {
        return switch (s) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 429 -> "Too Many Requests";
            case 503 -> "Service Unavailable";
            default -> s >= 500 ? "Server Error" : "OK";
        };
    }

    private static int indexOf(byte[] a, byte[] b) {
        label24:
        for (int i = 0; i <= a.length - b.length; i++) {
            for (int j = 0; j < b.length; j++) {
                if (a[i + j] != b[j]) {
                    continue label24;
                }
            }

            return i;
        }

        return -1;
    }

    private final class Sniffer extends ChannelInboundHandlerAdapter {
        private ByteArrayOutputStream buffered;
        private boolean http;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            if (msg instanceof ByteBuf buf) {
                if (!this.http) {
                    if (buf.readableBytes() < 4) {
                        ctx.pipeline().remove(this);
                        ctx.fireChannelRead(msg);
                        return;
                    }

                    byte[] head = new byte[4];
                    buf.getBytes(buf.readerIndex(), head);
                    String start = new String(head, StandardCharsets.US_ASCII);
                    if (!SamePortHttp.METHODS.contains(start)) {
                        ctx.pipeline().remove(this);
                        ctx.fireChannelRead(msg);
                        return;
                    }

                    this.http = true;
                    this.buffered = new ByteArrayOutputStream();
                    if (ctx.pipeline().get("timeout") != null) {
                        try {
                            ctx.pipeline().remove("timeout");
                        } catch (RuntimeException var21) {
                        }
                    }
                }

                try {
                    byte[] b = new byte[buf.readableBytes()];
                    buf.readBytes(b);
                    this.buffered.write(b, 0, b.length);
                } finally {
                    buf.release();
                }

                if (this.buffered.size() > 262144) {
                    ctx.close();
                } else {
                    byte[] all = this.buffered.toByteArray();
                    int end = SamePortHttp.indexOf(all, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    if (end >= 0) {
                        String headText = new String(all, 0, end, StandardCharsets.ISO_8859_1);
                        String[] lines = headText.split("\r\n");
                        String[] first = lines[0].split(" ");
                        if (first.length < 2) {
                            ctx.close();
                        } else {
                            Map<String, String> headers = new LinkedHashMap<>();

                            for (int i = 1; i < lines.length; i++) {
                                int c = lines[i].indexOf(58);
                                if (c > 0) {
                                    headers.put(lines[i].substring(0, c).trim().toLowerCase(Locale.ROOT), lines[i].substring(c + 1).trim());
                                }
                            }

                            int length = 0;

                            try {
                                length = Integer.parseInt(headers.getOrDefault("content-length", "0"));
                            } catch (NumberFormatException var19) {
                            }

                            int bodyStart = end + 4;
                            if (all.length - bodyStart >= length) {
                                byte[] body = new byte[Math.max(0, length)];
                                System.arraycopy(all, bodyStart, body, 0, body.length);
                                String ip = ctx.channel().remoteAddress() instanceof InetSocketAddress a ? a.getAddress().getHostAddress() : "?";
                                WebServer.Request req = WebServer.request(first[0], first[1], headers, body, ip);
                                Channel ch = ctx.channel();
                                SamePortHttp.this.pool.execute(() -> {
                                    WebServer.Response res = SamePortHttp.this.web.dispatch(req);
                                    ch.eventLoop().execute(() -> SamePortHttp.write(ch, req, res));
                                });
                            }
                        }
                    }
                }
            } else {
                ctx.pipeline().remove(this);
                ctx.fireChannelRead(msg);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            if (this.http) {
                ctx.close();
            } else {
                ctx.fireExceptionCaught(cause);
            }
        }
    }
}
