package net.kdt.pojavlaunch.vortex;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.FragmentActivity;

import com.kdt.mcgui.mcAccountSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.JavaGUILauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.fragments.MicrosoftLoginFragment;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.modloaders.ForgeDownloadTask;
import net.kdt.pojavlaunch.modloaders.ForgeUtils;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.progresskeeper.ProgressListener;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * The Vortex interface (a web page in a WebView) talks to Amethyst through
 * this object, exposed to JavaScript as {@code window.VortexNative}.
 *
 * Quick calls return a JSON string. Slow ones take a call id and answer
 * later through {@code window.__vx.done(id, ok, json)}. Native events (launch
 * progress, the app coming back to the front) go to
 * {@code window.__vx.emit(name, json)}.
 */
public class VortexBridge {
    private static final String TAG = "VortexBridge";
    private static final String[] PROGRESS_KEYS = {"download_minecraft", "unpack_runtime", "install_modpack", "authenticate_microsoft", "download_version_list"};

    private final FragmentActivity activity;
    private final WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final List<ProgressListener> listeners = new ArrayList<>();
    private ActivityResultLauncher<String[]> picker;
    private String pickId, pickDest;
    public boolean launching = false;

    public VortexBridge(FragmentActivity activity, WebView web) {
        this.activity = activity;
        this.web = web;
        picker = activity.registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), this::onPicked);
        for (String key : PROGRESS_KEYS) {
            ProgressListener l = new ProgressListener() {
                @Override public void onProgressStarted() { emit("progress", obj("key", key, "state", "start")); }
                @Override public void onProgressUpdated(int progress, int resid, Object... va) {
                    String text = "";
                    try { if (resid != 0 && resid != -1) text = activity.getString(resid, va); } catch (Exception ignored) {}
                    emit("progress", obj("key", key, "state", "update", "percent", progress, "text", text));
                }
                @Override public void onProgressEnded() { emit("progress", obj("key", key, "state", "end")); }
            };
            listeners.add(l);
            ProgressKeeper.addListener(key, l);
        }
        ProgressKeeper.addTaskCountListener(count -> emit("tasks", obj("count", count)));
    }

    public void detach() {
        for (int i = 0; i < PROGRESS_KEYS.length; i++) ProgressKeeper.removeListener(PROGRESS_KEYS[i], listeners.get(i));
        pool.shutdownNow();
    }

    // ------------------------------------------------------------ plumbing --

    private static JSONObject obj(Object... kv) {
        JSONObject o = new JSONObject();
        try { for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1]); } catch (Exception ignored) {}
        return o;
    }

    /** Send an event to the page. */
    public void emit(String name, JSONObject payload) {
        String js = "window.__vx&&window.__vx.emit(" + JSONObject.quote(name) + "," + payload + ")";
        ui.post(() -> web.evaluateJavascript(js, null));
    }

    private void done(String id, boolean ok, Object result) {
        String json = result == null ? "null" : (result instanceof String ? JSONObject.quote((String) result) : result.toString());
        String js = "window.__vx&&window.__vx.done(" + JSONObject.quote(id) + "," + ok + "," + json + ")";
        ui.post(() -> web.evaluateJavascript(js, null));
    }

    private interface Job { Object run() throws Exception; }

    private void async(String id, Job job) {
        pool.execute(() -> {
            try { done(id, true, job.run()); }
            catch (Throwable e) {
                Log.w(TAG, "call failed", e);
                done(id, false, e.getMessage() != null ? e.getMessage() : e.toString());
            }
        });
    }

    private mcAccountSpinner spinner() { return activity.findViewById(R.id.account_spinner); }

    // --------------------------------------------------------------- state --

    @JavascriptInterface
    public String state() {
        try {
            if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
            JSONObject out = new JSONObject();
            String version = "";
            try { version = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0).versionName; } catch (Exception ignored) {}
            out.put("appVersion", version);
            out.put("dataDir", Tools.DIR_GAME_HOME);
            out.put("gameHome", Tools.DIR_GAME_HOME);
            out.put("versionsDir", Tools.DIR_HOME_VERSION);
            out.put("cacheDir", activity.getCacheDir().getAbsolutePath());
            out.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "");
            out.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            out.put("sdk", Build.VERSION.SDK_INT);
            out.put("deviceRamMb", Tools.getTotalDeviceMemory(activity));
            out.put("selectedProfile", LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, ""));
            JSONArray profiles = new JSONArray();
            for (Map.Entry<String, MinecraftProfile> e : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
                MinecraftProfile p = e.getValue();
                File dir = Tools.getGameDirPath(p);
                profiles.put(obj("key", e.getKey(), "name", p.name == null ? "" : p.name, "lastVersionId", p.lastVersionId == null ? "" : p.lastVersionId,
                        "gameDir", p.gameDir == null ? "" : p.gameDir, "path", dir.getAbsolutePath(), "created", p.created == null ? "" : p.created,
                        "lastUsed", p.lastUsed == null ? "" : p.lastUsed, "javaArgs", p.javaArgs == null ? "" : p.javaArgs,
                        "renderer", p.pojavRendererName == null ? "" : p.pojavRendererName, "modCount", countJars(new File(dir, "mods"))));
            }
            out.put("profiles", profiles);
            mcAccountSpinner sp = spinner();
            MinecraftAccount cur = sp == null ? null : sp.getSelectedAccount();
            JSONArray accounts = new JSONArray();
            File accDir = new File(Tools.DIR_ACCOUNT_NEW);
            String[] files = accDir.list();
            if (files != null) for (String f : files) {
                if (!f.endsWith(".json")) continue;
                MinecraftAccount a = MinecraftAccount.load(f.substring(0, f.length() - 5));
                if (a == null) continue;
                accounts.put(obj("uuid", a.profileId == null ? a.username : a.profileId.replace("-", ""), "username", a.username,
                        "kind", a.isMicrosoft ? "microsoft" : "offline", "selected", cur != null && cur.username.equals(a.username),
                        "face", a.skinFaceBase64 == null ? "" : a.skinFaceBase64));
            }
            out.put("accounts", accounts);
            JSONObject prefs = new JSONObject();
            prefs.put("allocation", LauncherPreferences.DEFAULT_PREF.getInt("allocation", LauncherPreferences.PREF_RAM_ALLOCATION));
            prefs.put("resolutionRatio", LauncherPreferences.DEFAULT_PREF.getInt("resolutionRatio", Math.round(LauncherPreferences.PREF_SCALE_FACTOR * 100)));
            prefs.put("renderer", LauncherPreferences.DEFAULT_PREF.getString("renderer", ""));
            prefs.put("sustainedPerformance", LauncherPreferences.DEFAULT_PREF.getBoolean("sustainedPerformance", LauncherPreferences.PREF_SUSTAINED_PERFORMANCE));
            prefs.put("bigCoreAffinity", LauncherPreferences.DEFAULT_PREF.getBoolean("bigCoreAffinity", LauncherPreferences.PREF_BIG_CORE_AFFINITY));
            prefs.put("javaArgs", LauncherPreferences.DEFAULT_PREF.getString("javaArgs", ""));
            out.put("prefs", prefs);
            return out.toString();
        } catch (Exception e) {
            Log.e(TAG, "state", e);
            return "{}";
        }
    }

    private static int countJars(File dir) {
        String[] l = dir.list();
        if (l == null) return 0;
        int n = 0;
        for (String s : l) if (s.endsWith(".jar")) n++;
        return n;
    }

    @JavascriptInterface
    public void setPref(String key, String type, String value) {
        android.content.SharedPreferences.Editor e = LauncherPreferences.DEFAULT_PREF.edit();
        switch (type) {
            case "int": e.putInt(key, Integer.parseInt(value)); break;
            case "bool": e.putBoolean(key, Boolean.parseBoolean(value)); break;
            default: e.putString(key, value);
        }
        e.apply();
        LauncherPreferences.loadPreferences(activity);
    }

    // ------------------------------------------------------------ profiles --

    @JavascriptInterface
    public void selectProfile(String key) {
        LauncherPreferences.DEFAULT_PREF.edit().putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, key).apply();
        ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, key);
    }

    /** Create (empty key) or update a profile from JSON; returns its key. */
    @JavascriptInterface
    public String saveProfile(String key, String json) {
        try {
            if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
            JSONObject o = new JSONObject(json);
            MinecraftProfile p = key == null || key.isEmpty() ? null : LauncherProfiles.mainProfileJson.profiles.get(key);
            if (p == null) {
                p = new MinecraftProfile();
                p.created = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(new java.util.Date());
                key = LauncherProfiles.getFreeProfileKey();
            }
            if (o.has("name")) p.name = o.getString("name");
            if (o.has("lastVersionId")) p.lastVersionId = o.getString("lastVersionId");
            if (o.has("gameDir")) p.gameDir = o.getString("gameDir");
            if (o.has("icon")) p.icon = o.getString("icon");
            if (o.has("javaArgs")) p.javaArgs = o.getString("javaArgs").isEmpty() ? null : o.getString("javaArgs");
            if (o.has("lastUsed")) p.lastUsed = o.getString("lastUsed");
            if (o.has("renderer")) p.pojavRendererName = o.getString("renderer").isEmpty() ? null : o.getString("renderer");
            LauncherProfiles.mainProfileJson.profiles.put(key, p);
            LauncherProfiles.write();
            if (p.gameDir != null) Tools.getGameDirPath(p).mkdirs();
            return key;
        } catch (Exception e) {
            Log.e(TAG, "saveProfile", e);
            return "";
        }
    }

    @JavascriptInterface
    public boolean deleteProfile(String key, boolean files) {
        if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load();
        MinecraftProfile p = LauncherProfiles.mainProfileJson.profiles.remove(key);
        if (p == null) return false;
        LauncherProfiles.write();
        // Only an instance's own folder is removed, never the shared .minecraft.
        if (files && p.gameDir != null) {
            File dir = Tools.getGameDirPath(p);
            if (dir.getAbsolutePath().contains("/custom_instances/")) deleteTree(dir);
        }
        return true;
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    // ------------------------------------------------------------ accounts --

    private int accountIndex(String username) {
        mcAccountSpinner sp = spinner();
        if (sp == null || sp.getAdapter() == null) return -1;
        for (int i = 1; i < sp.getAdapter().getCount(); i++) {
            Object item = sp.getAdapter().getItem(i);
            if (username.equals(String.valueOf(item))) return i;
        }
        return -1;
    }

    @JavascriptInterface
    public void selectAccount(String username) {
        ui.post(() -> { int i = accountIndex(username); if (i > 0) spinner().setSelection(i); });
    }

    @JavascriptInterface
    public void removeAccount(String username) {
        ui.post(() -> {
            int i = accountIndex(username);
            if (i <= 0) return;
            spinner().setSelection(i);
            spinner().removeCurrentAccount();
            emit("accounts", new JSONObject());
        });
    }

    @JavascriptInterface
    public void addOffline(String username) {
        ui.post(() -> ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{username, ""}));
    }

    @JavascriptInterface
    public void microsoftLogin() {
        ui.post(() -> Tools.swapFragment(activity, MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));
    }

    // -------------------------------------------------------------- launch --

    @JavascriptInterface
    public void launch(String key) {
        ui.post(() -> {
            selectProfile(key);
            launching = true;
            ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true);
        });
    }

    @JavascriptInterface
    public int taskCount() { return ProgressKeeper.getTaskCount(); }

    // ------------------------------------------------------- native screens --

    @JavascriptInterface
    public void openNative(String what) {
        ui.post(() -> {
            try {
                switch (what) {
                    case "settings":
                        Tools.swapFragment(activity, LauncherPreferenceFragment.class, "SETTINGS_FRAGMENT", null); break;
                    case "controls":
                        activity.startActivity(new Intent(activity, CustomControlsActivity.class)); break;
                    case "logs":
                        Tools.shareLog(activity); break;
                    case "folder":
                        Tools.openPath(activity, new File(Tools.DIR_GAME_HOME), false); break;
                    default:
                        if (what.startsWith("folder:")) Tools.openPath(activity, new File(what.substring(7)), false);
                }
            } catch (Exception e) {
                Toast.makeText(activity, e.toString(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @JavascriptInterface
    public void openUrl(String url) {
        ui.post(() -> {
            try { activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception e) { Toast.makeText(activity, e.toString(), Toast.LENGTH_SHORT).show(); }
        });
    }

    /** Open Android's installer for a downloaded update. */
    @JavascriptInterface
    public void installApk(String path) {
        ui.post(() -> {
            try {
                File f = new File(path);
                Uri uri = androidx.core.content.FileProvider.getUriForFile(activity, activity.getPackageName() + ".vortexupdate", f);
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "application/vnd.android.package-archive");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(i);
            } catch (Exception e) {
                Toast.makeText(activity, "Couldn't open the installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @JavascriptInterface
    public void toast(String text) {
        ui.post(() -> Toast.makeText(activity, text, Toast.LENGTH_SHORT).show());
    }

    // ------------------------------------------------------------- network --

    /** GET or POST; answers {status, body} (body as text, or base64 when asked). */
    @JavascriptInterface
    public void http(String id, String method, String url, String body, String headersJson, boolean base64) {
        async(id, () -> {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod(method == null || method.isEmpty() ? "GET" : method);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "VortexClient-Android/1.0 (vortexclient.dev)");
            if (headersJson != null && !headersJson.isEmpty()) {
                JSONObject h = new JSONObject(headersJson);
                for (java.util.Iterator<String> it = h.keys(); it.hasNext(); ) { String k = it.next(); c.setRequestProperty(k, h.getString(k)); }
            }
            if (body != null && !body.isEmpty()) {
                c.setDoOutput(true);
                try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
            }
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            byte[] data = in == null ? new byte[0] : readAll(in);
            return obj("status", status, "body", base64 ? Base64.encodeToString(data, Base64.NO_WRAP) : new String(data, StandardCharsets.UTF_8));
        });
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }

    /** Download a file, reporting {id, done, total} as "download" events. */
    @JavascriptInterface
    public void download(String id, String url, String dest) {
        async(id, () -> {
            File out = new File(dest);
            File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            File part = new File(dest + ".part");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestProperty("User-Agent", "VortexClient-Android/1.0 (vortexclient.dev)");
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            if (c.getResponseCode() >= 400) throw new java.io.IOException("the server answered " + c.getResponseCode());
            long total = c.getContentLengthLong(), done = 0, last = 0;
            try (InputStream in = c.getInputStream(); FileOutputStream fo = new FileOutputStream(part)) {
                byte[] buf = new byte[131072];
                int n;
                while ((n = in.read(buf)) > 0) {
                    fo.write(buf, 0, n);
                    done += n;
                    if (done - last > 262144) { last = done; emit("download", obj("id", id, "done", done, "total", total)); }
                }
            }
            if (out.exists()) out.delete();
            if (!part.renameTo(out)) throw new java.io.IOException("couldn't save " + out.getName());
            return obj("path", out.getAbsolutePath(), "size", done);
        });
    }

    /** Minecraft's server list ping: the status JSON plus the round trip in ms. */
    @JavascriptInterface
    public void ping(String id, String host, int port) {
        async(id, () -> {
            try (Socket s = new Socket()) {
                long t0 = System.currentTimeMillis();
                s.connect(new InetSocketAddress(host, port), 5000);
                s.setSoTimeout(6000);
                DataOutputStream out = new DataOutputStream(s.getOutputStream());
                DataInputStream in = new DataInputStream(s.getInputStream());
                ByteArrayOutputStream hs = new ByteArrayOutputStream();
                DataOutputStream h = new DataOutputStream(hs);
                writeVarInt(h, 0); writeVarInt(h, 767);
                byte[] hb = host.getBytes(StandardCharsets.UTF_8);
                writeVarInt(h, hb.length); h.write(hb); h.writeShort(port); writeVarInt(h, 1);
                writeVarInt(out, hs.size()); out.write(hs.toByteArray());
                out.writeByte(1); out.writeByte(0);
                out.flush();
                readVarInt(in); readVarInt(in);
                int len = readVarInt(in);
                byte[] json = new byte[len];
                in.readFully(json);
                long ms = System.currentTimeMillis() - t0;
                JSONObject st = new JSONObject(new String(json, StandardCharsets.UTF_8));
                st.put("pingMs", ms);
                return st;
            }
        });
    }

    private static void writeVarInt(DataOutputStream o, int v) throws java.io.IOException {
        while ((v & ~0x7F) != 0) { o.writeByte((v & 0x7F) | 0x80); v >>>= 7; }
        o.writeByte(v);
    }

    private static int readVarInt(DataInputStream in) throws java.io.IOException {
        int v = 0, n = 0, b;
        do { b = in.readByte(); v |= (b & 0x7F) << (7 * n++); if (n > 5) throw new java.io.IOException("bad reply"); } while ((b & 0x80) != 0);
        return v;
    }

    // --------------------------------------------------------------- files --

    @JavascriptInterface
    public String list(String dir) {
        JSONArray a = new JSONArray();
        File[] kids = new File(dir).listFiles();
        if (kids != null) for (File k : kids) a.put(obj("name", k.getName(), "dir", k.isDirectory(), "size", k.length(), "mtime", k.lastModified() / 1000));
        return a.toString();
    }

    @JavascriptInterface
    public boolean remove(String path) { File f = new File(path); if (f.isDirectory()) deleteTree(f); return !f.exists() || f.delete(); }

    @JavascriptInterface
    public boolean rename(String from, String to) { return new File(from).renameTo(new File(to)); }

    @JavascriptInterface
    public String readText(String path) {
        try { return new String(readAll(new java.io.FileInputStream(path)), StandardCharsets.UTF_8); } catch (Exception e) { return null; }
    }

    @JavascriptInterface
    public boolean writeText(String path, String text) {
        try {
            File f = new File(path);
            if (f.getParentFile() != null) f.getParentFile().mkdirs();
            try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(text.getBytes(StandardCharsets.UTF_8)); }
            return true;
        } catch (Exception e) { return false; }
    }

    /** Name, version and description of the mods in a folder, from inside each jar. */
    @JavascriptInterface
    public void modInfo(String id, String dir) {
        async(id, () -> {
            JSONArray out = new JSONArray();
            File[] kids = new File(dir).listFiles();
            if (kids == null) return out;
            for (File k : kids) {
                String n = k.getName();
                if (k.isDirectory() || !(n.endsWith(".jar") || n.endsWith(".jar.disabled") || n.endsWith(".zip") || n.endsWith(".zip.disabled"))) continue;
                JSONObject o = obj("file", n, "size", k.length(), "enabled", !n.endsWith(".disabled"));
                if (n.contains(".jar")) try (ZipFile z = new ZipFile(k)) {
                    ZipEntry fm = z.getEntry("fabric.mod.json");
                    if (fm == null) fm = z.getEntry("quilt.mod.json");
                    if (fm != null) {
                        JSONObject j = new JSONObject(new String(readAll(z.getInputStream(fm)), StandardCharsets.UTF_8));
                        JSONObject q = j.optJSONObject("quilt_loader");
                        JSONObject meta = q != null ? q.optJSONObject("metadata") : null;
                        o.put("name", meta != null ? meta.optString("name") : j.optString("name"));
                        o.put("version", q != null ? q.optString("version") : j.optString("version"));
                        o.put("description", meta != null ? meta.optString("description") : j.optString("description"));
                        o.put("loader", q != null ? "quilt" : "fabric");
                    } else {
                        ZipEntry toml = z.getEntry("META-INF/mods.toml");
                        if (toml == null) toml = z.getEntry("META-INF/neoforge.mods.toml");
                        if (toml != null) {
                            String t = new String(readAll(z.getInputStream(toml)), StandardCharsets.UTF_8);
                            o.put("name", tomlValue(t, "displayName"));
                            o.put("version", tomlValue(t, "version"));
                            o.put("description", tomlValue(t, "description"));
                            o.put("loader", "forge");
                        }
                    }
                } catch (Exception ignored) {}
                out.put(o);
            }
            return out;
        });
    }

    private static String tomlValue(String toml, String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^\\s*" + key + "\\s*=\\s*(?:'''([\\s\\S]*?)'''|\"([^\"]*)\"|'([^']*)')").matcher(toml);
        if (!m.find()) return "";
        String v = m.group(1) != null ? m.group(1) : m.group(2) != null ? m.group(2) : m.group(3);
        return v == null || v.contains("${") ? "" : v.trim();
    }

    /** Let the player pick files and copy them into a folder. */
    @JavascriptInterface
    public void pickFiles(String id, String dest) {
        ui.post(() -> {
            pickId = id;
            pickDest = dest;
            picker.launch(new String[]{"*/*"});
        });
    }

    private void onPicked(List<Uri> uris) {
        String id = pickId, dest = pickDest;
        pickId = null;
        if (id == null) return;
        async(id, () -> {
            JSONArray names = new JSONArray();
            if (uris == null) return names;
            new File(dest).mkdirs();
            for (Uri u : uris) {
                String name = Tools.getFileName(activity, u);
                if (name == null) name = "file-" + System.currentTimeMillis() + ".jar";
                try (InputStream in = activity.getContentResolver().openInputStream(u); FileOutputStream fo = new FileOutputStream(new File(dest, name))) {
                    byte[] buf = new byte[131072];
                    int n;
                    while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
                }
                names.put(name);
            }
            return names;
        });
    }

    // ------------------------------------------------------------ installers --

    /** Install a Fabric (or Quilt) version JSON; answers the version id. */
    @JavascriptInterface
    public void installFabric(String id, String loader, String gameVersion, String loaderVersion) {
        async(id, () -> {
            FabriclikeUtils utils = "quilt".equals(loader) ? FabriclikeUtils.QUILT_UTILS : FabriclikeUtils.FABRIC_UTILS;
            HttpURLConnection c = (HttpURLConnection) new URL(utils.createJsonDownloadUrl(gameVersion, loaderVersion)).openConnection();
            c.setRequestProperty("User-Agent", "VortexClient-Android/1.0");
            if (c.getResponseCode() >= 400) throw new java.io.IOException(utils.getName() + " has no build for " + gameVersion);
            String json = new String(readAll(c.getInputStream()), StandardCharsets.UTF_8);
            String versionId = new JSONObject(json).getString("id");
            File dir = new File(Tools.DIR_HOME_VERSION, versionId);
            dir.mkdirs();
            try (FileOutputStream fo = new FileOutputStream(new File(dir, versionId + ".json"))) { fo.write(json.getBytes(StandardCharsets.UTF_8)); }
            return versionId;
        });
    }

    /** Download Forge's installer and run it automatically (Amethyst's way). */
    @JavascriptInterface
    public void installForge(String id, String fullVersion) {
        ForgeDownloadTask task = new ForgeDownloadTask(new ModloaderDownloadListener() {
            @Override public void onDownloadFinished(File file) {
                ui.post(() -> {
                    Intent i = new Intent(activity, JavaGUILauncherActivity.class);
                    ForgeUtils.addAutoInstallArgs(i, file, false);
                    activity.startActivity(i);
                    done(id, true, null);
                });
            }
            @Override public void onDataNotAvailable() { done(id, false, "Forge has no installer for " + fullVersion); }
            @Override public void onDownloadError(Exception e) { done(id, false, String.valueOf(e.getMessage())); }
        }, fullVersion);
        pool.execute(task);
    }

    @JavascriptInterface
    public void forgeVersions(String id) {
        async(id, () -> new JSONArray(ForgeUtils.downloadForgeVersions()));
    }

    /** The launcher went behind another screen: after Launch, that's the game. */
    public void onPause() {
        if (launching) emit("game-started", new JSONObject());
    }

    /** Called when the launcher comes back to the front. */
    @SuppressLint("NewApi")
    public void onResume() {
        emit("resume", obj("launching", launching));
        launching = false;
    }

    public static View.OnClickListener none() { return v -> {}; }
}
