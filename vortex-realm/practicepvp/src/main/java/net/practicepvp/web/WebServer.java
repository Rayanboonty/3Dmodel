package net.practicepvp.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import net.practicepvp.PracticePlugin;

public final class WebServer {
    private final PracticePlugin plugin;
    private final List<WebServer.Route> routes = new ArrayList<>();
    private HttpServer server;
    private ExecutorService pool;
    private int port;
    private volatile boolean samePort;

    public WebServer(PracticePlugin plugin) {
        this.plugin = plugin;
    }

    public void exact(String method, String path, WebServer.Handler h) {
        this.routes.add(new WebServer.Route(method, path, true, h));
    }

    public void prefix(String method, String prefix, WebServer.Handler h) {
        this.routes.add(new WebServer.Route(method, prefix, false, h));
    }

    public boolean running() {
        return this.server != null;
    }

    public int port() {
        return this.port;
    }

    public synchronized boolean start(int port) {
        this.stop();

        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 64);
        } catch (RuntimeException | IOException var3) {
            this.plugin
                .getLogger()
                .warning(
                    "Website / pack server could not open port "
                        + port
                        + ": "
                        + var3.getMessage()
                        + ". Pick a free port (Settings -> Website & Pack) - on a host, ask for an extra port."
                );
            this.server = null;
            return false;
        } catch (NoClassDefFoundError var4) {
            this.plugin
                .getLogger()
                .warning(
                    "This Java has no built-in web server (jdk.httpserver); the website and the pack hosting are off. Set resource-pack.url to an external download instead."
                );
            this.server = null;
            return false;
        }

        this.port = port;
        this.pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "VortexWeb");
            t.setDaemon(true);
            return t;
        });
        this.server.setExecutor(this.pool);
        this.server.createContext("/", this::serve);
        this.server.start();
        this.plugin.getLogger().info("Website and resource pack served on port " + port + ".");
        return true;
    }

    public synchronized void stop() {
        if (this.server != null) {
            this.server.stop(0);
            this.server = null;
        }

        if (this.pool != null) {
            this.pool.shutdownNow();
            this.pool = null;
        }
    }

    private void serve(HttpExchange ex) {
        try {
            HttpExchange e = ex;

            try {
                String path = ex.getRequestURI().getRawPath();
                String method = ex.getRequestMethod().toUpperCase(Locale.ROOT);
                Map<String, String> headers = new LinkedHashMap<>();
                ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(Locale.ROOT), v.isEmpty() ? "" : v.get(0)));

                byte[] body;
                try (InputStream in = ex.getRequestBody()) {
                    body = in.readNBytes(262144);
                }

                String ip = ex.getRemoteAddress() == null ? "?" : ex.getRemoteAddress().getAddress().getHostAddress();
                WebServer.Request req = new WebServer.Request(method, decode(path), query(ex.getRequestURI().getRawQuery()), headers, body, ip);
                WebServer.Response res = this.dispatch(req);
                res.headers().forEach((k, v) -> ex.getResponseHeaders().set(k, v));
                ex.getResponseHeaders().set("Content-Type", res.type());
                ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                ex.getResponseHeaders().set("Referrer-Policy", "no-referrer");
                boolean head = method.equals("HEAD");
                ex.sendResponseHeaders(res.status(), head ? -1L : res.body().length);
                if (!head) {
                    try (OutputStream out = ex.getResponseBody()) {
                        out.write(res.body());
                    }
                }
            } catch (Throwable var20) {
                if (ex != null) {
                    try {
                        e.close();
                    } catch (Throwable var15) {
                        var20.addSuppressed(var15);
                    }
                }

                throw var20;
            }

            if (ex != null) {
                ex.close();
            }
        } catch (IOException var21) {
        } catch (Exception var22) {
            this.plugin.getLogger().log(Level.WARNING, "Web request failed", (Throwable)var22);

            try {
                byte[] b = "Server error".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(500, b.length);
                ex.getResponseBody().write(b);
            } catch (IOException var14) {
            }
        }
    }

    public WebServer.Response dispatch(WebServer.Request req) {
        try {
            for (WebServer.Route r : this.routes) {
                boolean match = r.exact ? req.path().equals(r.prefix) : req.path().startsWith(r.prefix);
                if (match && (r.method == null || r.method.equals(req.method()))) {
                    return r.handler.handle(req);
                }
            }

            return WebServer.Response.notFound();
        } catch (Exception var5) {
            this.plugin.getLogger().log(Level.WARNING, "Web request failed: " + req.path(), (Throwable)var5);
            return WebServer.Response.text(500, "text/plain; charset=utf-8", "Server error");
        }
    }

    public static WebServer.Request request(String method, String rawTarget, Map<String, String> headers, byte[] body, String ip) {
        String path = rawTarget;
        String q = null;
        int i = rawTarget.indexOf(63);
        if (i >= 0) {
            path = rawTarget.substring(0, i);
            q = rawTarget.substring(i + 1);
        }

        return new WebServer.Request(method.toUpperCase(Locale.ROOT), decode(path), query(q), headers, body, ip);
    }

    public boolean available() {
        return this.server != null || this.samePort;
    }

    public void samePort(boolean on) {
        this.samePort = on;
    }

    public boolean onSamePort() {
        return this.samePort;
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s == null ? "/" : s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException var2) {
            return "/";
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split("&")) {
                int eq = part.indexOf(61);
                String k = decode(eq < 0 ? part : part.substring(0, eq));
                String v = eq < 0 ? "" : decode(part.substring(eq + 1));
                out.put(k, v);
            }

            return out;
        } else {
            return out;
        }
    }

    @FunctionalInterface
    public interface Handler {
        WebServer.Response handle(WebServer.Request var1) throws Exception;
    }

    public record Request(String method, String path, Map<String, String> query, Map<String, String> headers, byte[] body, String ip) {
        public String header(String name) {
            return this.headers.get(name.toLowerCase(Locale.ROOT));
        }

        public String param(String name) {
            return this.query.get(name);
        }

        public String bodyText() {
            return new String(this.body, StandardCharsets.UTF_8);
        }
    }

    public record Response(int status, String type, byte[] body, Map<String, String> headers) {
        public static WebServer.Response of(int status, String type, byte[] body) {
            return new WebServer.Response(status, type, body, new LinkedHashMap<>());
        }

        public static WebServer.Response text(int status, String type, String body) {
            return of(status, type, body.getBytes(StandardCharsets.UTF_8));
        }

        public static WebServer.Response json(String body) {
            return text(200, "application/json; charset=utf-8", body);
        }

        public static WebServer.Response json(int status, String body) {
            return text(status, "application/json; charset=utf-8", body);
        }

        public static WebServer.Response html(String body) {
            return text(200, "text/html; charset=utf-8", body);
        }

        public static WebServer.Response notFound() {
            return text(404, "text/plain; charset=utf-8", "Not found");
        }

        public WebServer.Response header(String k, String v) {
            this.headers.put(k, v);
            return this;
        }
    }

    private record Route(String method, String prefix, boolean exact, WebServer.Handler handler) {
    }
}
