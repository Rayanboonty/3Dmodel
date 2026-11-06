package net.kdt.pojavlaunch.vortex;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import net.kdt.pojavlaunch.fragments.MainMenuFragment;

import java.io.InputStream;
import java.util.WeakHashMap;

/**
 * Puts the Vortex interface over Amethyst's launcher: a full-window WebView
 * that serves the interface from the APK's assets at https://vortex.app/.
 * Amethyst's own screens (Microsoft sign-in, advanced settings, installers)
 * still work: the WebView steps aside while one of them is open and comes
 * back when the player returns to the main screen.
 */
public final class VortexWeb {
    private static final String HOST = "https://vortex.app/";
    private static final WeakHashMap<FragmentActivity, VortexWeb> ALL = new WeakHashMap<>();

    public final WebView web;
    public final VortexBridge bridge;

    @SuppressLint("SetJavaScriptEnabled")
    private VortexWeb(FragmentActivity activity) {
        web = new WebView(activity);
        web.setBackgroundColor(Color.parseColor("#0C0A10"));
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        bridge = new VortexBridge(activity, web);
        web.addJavascriptInterface(bridge, "VortexNative");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage m) {
                android.util.Log.i("VortexWeb", m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (!url.startsWith(HOST)) return null;
                String path = req.getUrl().getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                try {
                    InputStream in = activity.getAssets().open("vortex" + path);
                    WebResourceResponse r = new WebResourceResponse(mime(path), mime(path).startsWith("text") || path.endsWith(".js") ? "utf-8" : null, in);
                    java.util.HashMap<String, String> h = new java.util.HashMap<>();
                    h.put("Cache-Control", "no-cache");
                    h.put("Access-Control-Allow-Origin", "*");
                    r.setResponseHeaders(h);
                    return r;
                } catch (Exception e) {
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null, null);
                }
            }

            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String url = req.getUrl().toString();
                if (url.startsWith(HOST)) return false;
                bridge.openUrl(url);
                return true;
            }
        });
        ViewGroup root = activity.findViewById(android.R.id.content);
        root.addView(web, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.loadUrl(HOST + "index.html");

        // Step aside for Amethyst's own screens, come back on the main one.
        activity.getSupportFragmentManager().registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                boolean main = f instanceof MainMenuFragment;
                web.setVisibility(main ? View.VISIBLE : View.GONE);
                if (main) bridge.emit("native-closed", new org.json.JSONObject());
            }
        }, true);

        Window w = activity.getWindow();
        w.setStatusBarColor(Color.parseColor("#0C0A10"));
        if (Build.VERSION.SDK_INT >= 26) w.setNavigationBarColor(Color.parseColor("#0C0A10"));
    }

    private static String mime(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js")) return "text/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".webp")) return "image/webp";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".woff2")) return "font/woff2";
        if (p.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }

    /** Call from the launcher activity's onCreate, after its views exist. */
    public static void attach(FragmentActivity activity) {
        ALL.put(activity, new VortexWeb(activity));
    }

    public static void onResume(FragmentActivity activity) {
        VortexWeb v = ALL.get(activity);
        if (v != null) { v.web.onResume(); v.bridge.onResume(); }
    }

    public static void onPause(FragmentActivity activity) {
        VortexWeb v = ALL.get(activity);
        if (v != null) { v.bridge.onPause(); v.web.onPause(); }
    }

    public static void onDestroy(FragmentActivity activity) {
        VortexWeb v = ALL.remove(activity);
        if (v != null) { v.bridge.detach(); v.web.destroy(); }
    }

    /**
     * Back: the page closes its own dialog or goes Home first; only when
     * there is nothing left to close does the activity handle it.
     */
    public static boolean onBack(FragmentActivity activity, Runnable fallback) {
        VortexWeb v = ALL.get(activity);
        if (v == null || v.web.getVisibility() != View.VISIBLE) return false;
        v.web.evaluateJavascript("window.__vx&&window.__vx.back?window.__vx.back():false", (r) -> {
            if (!"true".equals(r)) fallback.run();
        });
        return true;
    }
}
