// The launcher's commands on Android, on top of Amethyst. The native side
// (window.VortexNative, see android/overlay/java/.../VortexBridge.java) gives
// profiles, accounts, launching, files and network; everything else (the
// Mojang version list, Fabric and Forge versions, Modrinth, server pings,
// skins) is done here with it, in the same shapes the Windows app returns.

const N = window.VortexNative;
export const isAndroid = !!N;

const listeners = new Map();
const emit = (event, payload) => (listeners.get(event) || []).forEach((h) => { try { h(payload); } catch (e) { console.error(e); } });

// Slow native calls answer through window.__vx.done(id, ok, result).
const pending = new Map();
let seq = 0;
function nat(method, ...args) {
  return new Promise((resolve, reject) => {
    const id = `c${++seq}`;
    pending.set(id, { resolve, reject });
    N[method](id, ...args);
  });
}

window.__vx = {
  done(id, ok, result) {
    const p = pending.get(id);
    if (!p) return;
    pending.delete(id);
    ok ? p.resolve(result) : p.reject(String(result || "failed"));
  },
  emit(name, payload) { onNative(name, payload || {}); },
  back() { return window.__vortexBack ? !!window.__vortexBack() : false; },
};

const wait = (ms) => new Promise((r) => setTimeout(r, ms));
const UPDATE_REPO = "Rayanboonty/3Dmodel";

/** 0.10.2 vs 0.9.9: like the Windows updater, by numbers. */
function cmpVersion(a, b) {
  const p = (v) => String(v).replace(/^v/, "").split(/[-+]/)[0].split(".").map((x) => parseInt(x, 10) || 0);
  const x = p(a), y = p(b);
  for (let i = 0; i < Math.max(x.length, y.length); i++) { const d = (x[i] || 0) - (y[i] || 0); if (d) return d; }
  return 0;
}

// Download progress events from the native side, by call id.
const downloadWatch = new Map();
function onDownload(id, fn) { downloadWatch.set(id, fn); return () => downloadWatch.delete(id); }

async function http(url, { method = "GET", body = "", headers = {}, base64 = false } = {}) {
  const r = await nat("http", method, url, body, JSON.stringify(headers), base64);
  if (r.status >= 400) throw new Error(`${new URL(url).host} answered ${r.status}`);
  return r.body;
}
const getJson = async (url) => JSON.parse(await http(url));

// ------------------------------------------------------------- state --

let snap = null;
const read = () => (snap = JSON.parse(N.state() || "{}"));
const vortexDir = () => `${snap.gameHome}/vortex`;
const SETTINGS_FILE = () => `${vortexDir()}/settings.json`;

const DEFAULTS = {
  min_memory_mb: 512, max_memory_mb: 2048, java_path: null, extra_jvm_args: [], microsoft_client_id: null,
  download_concurrency: 16, show_snapshots: false, show_old_versions: false, sky: "cycle", day_length_min: 10,
  ui_sounds: true, animations: true, game_width: null, game_height: null, fullscreen: false, after_launch: "keep",
  vortex_mods: false, open_log_on_launch: false, accent: "amber", glass: 60, logo_style: 0, background: "golden",
  theme: "dark", vortex_menu: false, show_player: true, world_life: true, clock_12h: false,
  mods_allow_unstable: false, mods_with_dependencies: true, fast_launch: false, tuned_jvm: true,
};

function loadSettings() {
  let s = {};
  try { s = JSON.parse(N.readText(SETTINGS_FILE()) || "{}"); } catch { s = {}; }
  const out = { ...DEFAULTS, ...s };
  if (snap.prefs?.allocation) out.max_memory_mb = snap.prefs.allocation;
  return out;
}

// ------------------------------------------------------------ versions --

let manifest = null;
async function versionManifest() {
  if (manifest) return manifest;
  const m = await getJson("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
  const kind = (t) => ({ release: "release", snapshot: "snapshot", old_beta: "beta", old_alpha: "alpha" }[t] || "other");
  manifest = { latestRelease: m.latest.release, latestSnapshot: m.latest.snapshot, versions: m.versions.map((v) => ({ id: v.id, kind: kind(v.type), date: v.releaseTime.slice(0, 10) })) };
  return manifest;
}

/** What a profile's version id means: Minecraft version and loader. */
function parseVersion(id) {
  id = id || "";
  if (id === "latest-release") return { mc: manifest?.latestRelease || "latest", loader: "vanilla", lv: null };
  if (id === "latest-snapshot") return { mc: manifest?.latestSnapshot || "snapshot", loader: "vanilla", lv: null };
  let m;
  if ((m = id.match(/^(fabric|quilt)-loader-([^-]+)-(.+)$/))) return { mc: m[3], loader: m[1], lv: m[2] };
  if ((m = id.match(/^neoforge-(\d+)\.(\d+)\.(.+)$/))) return { mc: `1.${m[1]}${m[2] !== "0" ? "." + m[2] : ""}`, loader: "neoforge", lv: `${m[1]}.${m[2]}.${m[3]}` };
  if ((m = id.match(/^(\d[\w.]*?)-forge-?(?:\1-)?(.+)$/))) return { mc: m[1], loader: "forge", lv: m[2] };
  if ((m = id.match(/^(\d[\w.]*?)-OptiFine_(.+)$/))) return { mc: m[1], loader: "optifine", lv: m[2] };
  return { mc: id, loader: "vanilla", lv: null };
}

const LOADER_LABEL = { vanilla: "Vanilla", fabric: "Fabric", quilt: "Quilt", forge: "Forge", neoforge: "NeoForge", optifine: "OptiFine" };

let byName = new Map();
function instances() {
  read();
  byName = new Map();
  const used = new Set();
  const list = [];
  for (const p of snap.profiles || []) {
    const v = parseVersion(p.lastVersionId);
    let name = (p.name || "").trim() || `${LOADER_LABEL[v.loader]} ${v.mc}`;
    let n = name, k = 2;
    while (used.has(n.toLowerCase())) n = `${name} (${k++})`;
    used.add(n.toLowerCase());
    const lastPlayed = p.lastUsed ? Math.floor(Date.parse(p.lastUsed) / 1000) || null : null;
    const inst = {
      name: n, key: p.key, minecraftVersion: v.mc, loader: v.loader === "quilt" ? "fabric" : v.loader === "neoforge" ? "forge" : v.loader,
      loaderVersion: v.lv, loaderLabel: LOADER_LABEL[v.loader] + (v.lv && v.loader !== "fabric" && v.loader !== "quilt" ? " " + v.lv : ""),
      memoryMb: null, joinServer: null, jvmArgs: p.javaArgs || "", lastPlayed, gameDir: p.path, running: false, modCount: p.modCount || 0,
    };
    byName.set(n, inst);
    list.push(inst);
  }
  list.sort((a, b) => (b.lastPlayed || 0) - (a.lastPlayed || 0));
  return list;
}

function inst(name) {
  if (!byName.has(name)) instances();
  const i = byName.get(name);
  if (!i) throw `there's no instance called ${name}`;
  return i;
}

/** Whether a Minecraft version's game files are already on the phone. */
function installed(mc) {
  try { return JSON.parse(N.list(`${snap.versionsDir}/${mc}`) || "[]").some((f) => f.name === `${mc}.jar` && f.size > 0); } catch { return false; }
}

const slug = (s) => s.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "instance";

// ------------------------------------------------------------ accounts --

function accounts() {
  read();
  return (snap.accounts || []).map((a) => ({ uuid: a.uuid, username: a.username, kind: a.kind, selected: a.selected }));
}

async function until(test, ms = 4000) {
  const t0 = Date.now();
  while (Date.now() - t0 < ms) {
    const v = test();
    if (v) return v;
    await wait(150);
  }
  return test();
}

// -------------------------------------------------------------- launch --

let launching = null;
let started = false;
let watchdog = 0;
function onNative(name, p) {
  if (name === "progress" && launching) {
    if (p.state === "update") emit("launch-progress", { instance: launching, stage: p.text || "Preparing", done: p.percent || 0, total: 100 });
  } else if (name === "game-started" && launching && !started) {
    // The game's screen opened over the launcher: now it's really running.
    started = true;
    clearInterval(watchdog);
    emit("game-state", { instance: launching, state: "running" });
  } else if (name === "resume") {
    if (launching && started) { emit("game-state", { instance: launching, state: "exited", code: 0 }); launching = null; started = false; }
    emit("android-resume", {});
  } else if (name === "native-closed") {
    nativeClosed++;
    emit("android-resume", {});
  } else if (name === "download") {
    downloadWatch.get(p.id)?.(p);
  } else if (name === "accounts") {
    emit("android-accounts", {});
  }
}
let nativeClosed = 0;

// ------------------------------------------------------------ Modrinth --

const MR = "https://api.modrinth.com/v2";
const KIND_DIR = { mod: "mods", resourcepack: "resourcepacks", shader: "shaderpacks" };
const indexPath = (i) => `${i.gameDir}/vortex-modrinth.json`;
function readIndex(i) {
  try { return JSON.parse(N.readText(indexPath(i)) || "{}").files || {}; } catch { return {}; }
}
function writeIndex(i, files) { N.writeText(indexPath(i), JSON.stringify({ files }, null, 1)); }

function mrLoader(i, kind) {
  if (kind === "shader") return null;
  if (kind === "resourcepack") return null;
  return { fabric: "fabric", forge: "forge", quilt: "quilt" }[i.loader] || null;
}

async function mrVersions(projectId, i, kind) {
  const q = new URLSearchParams();
  q.set("game_versions", JSON.stringify([i.minecraftVersion]));
  const l = mrLoader(i, kind);
  if (l) q.set("loaders", JSON.stringify(l === "quilt" ? ["quilt", "fabric"] : [l]));
  return getJson(`${MR}/project/${projectId}/version?${q}`);
}

const versionView = (v) => {
  const f = v.files.find((x) => x.primary) || v.files[0];
  return { id: v.id, name: v.name, number: v.version_number, kind: v.version_type, gameVersions: v.game_versions, loaders: v.loaders, date: (v.date_published || "").slice(0, 10), downloads: v.downloads, file: f?.filename || null, size: f?.size || null };
};

async function mrInstall(i, projectId, versionId, kind, seen = new Set()) {
  if (seen.has(projectId)) return { installed: [], already: [] };
  seen.add(projectId);
  const files = readIndex(i);
  const project = await getJson(`${MR}/project/${projectId}`);
  if (Object.values(files).some((e) => e.projectId === projectId) && !versionId) return { installed: [], already: [project.title] };
  let v;
  if (versionId) v = await getJson(`${MR}/version/${versionId}`);
  else {
    const vs = await mrVersions(projectId, i, kind);
    v = vs.find((x) => x.version_type === "release") || vs[0];
  }
  if (!v) throw `${project.title} has no version for Minecraft ${i.minecraftVersion}`;
  const f = v.files.find((x) => x.primary) || v.files[0];
  const dir = `${i.gameDir}/${KIND_DIR[kind]}`;
  await nat("download", f.url, `${dir}/${f.filename}`);
  for (const [name, e] of Object.entries(files)) if (e.projectId === projectId && name !== f.filename) { N.remove(`${dir}/${name}`); delete files[name]; }
  files[f.filename] = { projectId, versionId: v.id, title: project.title, iconUrl: project.icon_url || null, kind };
  writeIndex(i, files);
  const out = { installed: [project.title], already: [] };
  if (kind === "mod" && loadSettings().mods_with_dependencies !== false) {
    for (const d of v.dependencies || []) {
      if (d.dependency_type !== "required" || !d.project_id) continue;
      try {
        const r = await mrInstall(i, d.project_id, null, "mod", seen);
        out.installed.push(...r.installed);
      } catch { /* a missing dependency doesn't undo the mod */ }
    }
  }
  return out;
}

// ------------------------------------------------------------- servers --

function motdText(d) {
  if (!d) return "";
  if (typeof d === "string") return d.replace(/§./g, "");
  return ((d.text || "") + (d.extra || []).map(motdText).join("")).replace(/§./g, "");
}

// ---------------------------------------------------------- the commands --

export const android = {
  listen(event, handler) {
    if (!listeners.has(event)) listeners.set(event, []);
    listeners.get(event).push(handler);
    return Promise.resolve(() => {});
  },

  async invoke(cmd, a = {}) {
    switch (cmd) {
      case "bootstrap": {
        read();
        versionManifest().catch(() => {});
        return { appVersion: snap.appVersion || "1.0", dataDir: snap.gameHome, platform: "android", settings: loadSettings(), accounts: accounts(), instances: instances(), device: snap.device, deviceRamMb: snap.deviceRamMb };
      }
      case "list_instances": return instances();
      case "list_versions": return versionManifest();
      case "loader_versions": {
        if (a.loader === "fabric") {
          const list = await getJson(`https://meta.fabricmc.net/v2/versions/loader/${encodeURIComponent(a.game)}`).catch(() => []);
          const firstStable = list.findIndex((x) => x.loader.stable);
          return list.slice(0, 40).map((x, n) => ({ id: x.loader.version, display: x.loader.version, stable: x.loader.stable, recommended: n === firstStable, latest: n === 0 }));
        }
        if (a.loader === "forge") {
          const all = await nat("forgeVersions").catch(() => []);
          const mine = all.filter((v) => v.startsWith(a.game + "-")).map((v) => v.slice(a.game.length + 1));
          return mine.slice(0, 40).map((v, n) => ({ id: v, display: v, stable: true, recommended: n === 0, latest: n === 0 }));
        }
        return [];
      }
      case "create_instance": {
        instances();
        if ([...byName.keys()].some((n) => n.toLowerCase() === a.name.toLowerCase())) throw `an instance called '${a.name}' already exists`;
        let versionId = a.version;
        if (a.loader === "fabric") versionId = await nat("installFabric", "fabric", a.version, a.loaderVersion || "");
        else if (a.loader === "forge") versionId = `${a.version}-forge-${a.loaderVersion}`;
        const gameDir = `amethyst://custom_instances/${slug(a.name)}`;
        const key = N.saveProfile("", JSON.stringify({ name: a.name, lastVersionId: versionId, gameDir }));
        if (!key) throw "couldn't save the instance";
        if (a.loader === "forge") {
          // Amethyst runs Forge's own installer; it takes a minute or two.
          await nat("installForge", `${a.version}-${a.loaderVersion}`);
        }
        instances();
        return [...byName.values()].find((x) => x.key === key);
      }
      case "update_instance": {
        const i = inst(a.name);
        N.saveProfile(i.key, JSON.stringify({ javaArgs: a.jvmArgs || "" }));
        instances();
        return inst(a.name);
      }
      case "delete_instance": {
        const i = inst(a.name);
        N.deleteProfile(i.key, true);
        return instances();
      }
      case "launch_instance": {
        const i = inst(a.name);
        if (!accounts().length) throw "add an account first (Accounts page)";
        N.saveProfile(i.key, JSON.stringify({ lastUsed: new Date().toISOString().replace(/\.\d+Z$/, "Z") }));
        // Minecraft's files only download for a Microsoft account that owns
        // the game; offline accounts can play versions already downloaded.
        const acc = accounts().find((x) => x.selected);
        if (acc && acc.kind !== "microsoft" && !installed(i.minecraftVersion)) {
          throw `Sign in with Microsoft once to download Minecraft ${i.minecraftVersion}. After that your offline accounts can play it too.`;
        }
        launching = a.name;
        started = false;
        emit("game-state", { instance: a.name, state: "preparing" });
        emit("launch-progress", { instance: a.name, stage: `Getting ${i.minecraftVersion} ready`, done: 0, total: 0 });
        N.launch(i.key);
        if (a.server) N.toast(`Join ${a.server} from Multiplayer once the game opens`);
        // If the game never opens (Amethyst showed an error, or the launch
        // was refused), go back to Launch instead of pretending it's playing.
        clearInterval(watchdog);
        const t0 = Date.now();
        watchdog = setInterval(() => {
          if (started || launching !== a.name) return clearInterval(watchdog);
          if (Date.now() - t0 > 6000 && N.taskCount() === 0) {
            clearInterval(watchdog);
            launching = null;
            emit("game-state", { instance: a.name, state: "exited", code: 0 });
          }
        }, 1000);
        return null;
      }
      case "stop_instance":
        launching = null;
        emit("game-state", { instance: a.name, state: "exited", code: 0 });
        return null;
      case "verify_instance": case "warm_up": case "save_menu_art": case "set_cape":
        return null;

      case "add_offline_account": {
        N.addOffline(a.username);
        await until(() => accounts().some((x) => x.username === a.username));
        N.selectAccount(a.username);
        await until(() => accounts().find((x) => x.username === a.username)?.selected, 2000);
        return accounts();
      }
      case "select_account": {
        const acc = accounts().find((x) => x.uuid === a.uuid);
        if (acc) { N.selectAccount(acc.username); await until(() => accounts().find((x) => x.uuid === a.uuid)?.selected, 2000); }
        return accounts();
      }
      case "remove_account": {
        const acc = accounts().find((x) => x.uuid === a.uuid);
        if (acc) { N.removeAccount(acc.username); await until(() => !accounts().some((x) => x.uuid === a.uuid)); }
        return accounts();
      }
      case "microsoft_login": {
        const before = new Set(accounts().filter((x) => x.kind === "microsoft").map((x) => x.uuid));
        const closed = nativeClosed;
        N.microsoftLogin();
        // The sign-in page is Amethyst's; once it closes, the account is
        // finished in the background.
        const t0 = Date.now();
        for (;;) {
          await wait(500);
          const fresh = accounts().find((x) => x.kind === "microsoft" && !before.has(x.uuid));
          if (fresh) { N.selectAccount(fresh.username); await wait(300); return accounts(); }
          if (nativeClosed > closed && Date.now() - t0 > 4000) {
            const t1 = Date.now();
            while (Date.now() - t1 < 25000) {
              await wait(500);
              const f2 = accounts().find((x) => x.kind === "microsoft" && !before.has(x.uuid));
              if (f2) { N.selectAccount(f2.username); await wait(300); return accounts(); }
            }
            throw "sign-in was cancelled or didn't finish";
          }
          if (Date.now() - t0 > 10 * 60000) throw "sign-in timed out";
        }
      }

      case "save_settings": {
        read();
        const s = { ...a.settings };
        N.writeText(SETTINGS_FILE(), JSON.stringify(s, null, 1));
        if (s.max_memory_mb && s.max_memory_mb !== snap.prefs?.allocation) N.setPref("allocation", "int", String(Math.round(s.max_memory_mb)));
        return s;
      }

      case "server_status": {
        const [host, port] = a.address.split(":");
        const st = await nat("ping", host, Number(port) || 25565).catch(() => { throw new Error(`${a.address} didn't answer`); });
        return { address: a.address, online: st.players?.online ?? 0, max: st.players?.max ?? 0, motd: motdText(st.description), version: st.version?.name || "", icon: st.favicon || null, pingMs: st.pingMs };
      }

      case "news": {
        const j = await getJson("https://launchercontent.mojang.com/v2/javaPatchNotes.json");
        return (j.entries || []).slice(0, 6).map((e) => ({ title: e.title, version: e.version, kind: e.type === "snapshot" ? "snapshot" : "release", image_url: e.image?.url ? `https://launchercontent.mojang.com${e.image.url}` : null, date: (e.date || "").slice(0, 10), summary: e.shortText || "" }));
      }
      case "version_art": {
        const j = await getJson("https://launchercontent.mojang.com/v2/javaPatchNotes.json").catch(() => ({ entries: [] }));
        return (j.entries || []).filter((e) => e.image?.url).map((e) => ({ version: e.version, image: `https://launchercontent.mojang.com${e.image.url}` }));
      }

      case "list_mods": {
        const i = inst(a.name);
        const kind = a.kind || "mod";
        const files = readIndex(i);
        const raw = await nat("modInfo", `${i.gameDir}/${KIND_DIR[kind]}`);
        return raw.map((m) => {
          const base = m.file.replace(/\.disabled$/, "");
          const e = files[base];
          return { file: m.file, enabled: m.enabled, size: m.size, name: m.name || e?.title || base.replace(/\.(jar|zip)$/, ""), version: m.version || "", description: m.description || "", loader: m.loader || i.loader, iconUrl: e?.iconUrl || null, projectId: e?.projectId || null };
        }).sort((x, y) => x.name.localeCompare(y.name));
      }
      case "toggle_mod": {
        const i = inst(a.name);
        const dir = `${i.gameDir}/${KIND_DIR[a.kind || "mod"]}`;
        const to = a.enabled ? a.file.replace(/\.disabled$/, "") : a.file.endsWith(".disabled") ? a.file : `${a.file}.disabled`;
        if (to !== a.file) N.rename(`${dir}/${a.file}`, `${dir}/${to}`);
        return android.invoke("list_mods", { name: a.name, kind: a.kind });
      }
      case "delete_mod": {
        const i = inst(a.name);
        const kind = a.kind || "mod";
        N.remove(`${i.gameDir}/${KIND_DIR[kind]}/${a.file}`);
        const files = readIndex(i);
        delete files[a.file.replace(/\.disabled$/, "")];
        writeIndex(i, files);
        return android.invoke("list_mods", { name: a.name, kind });
      }
      case "import_mods": {
        const i = inst(a.name);
        await nat("pickFiles", `${i.gameDir}/${KIND_DIR[a.kind || "mod"]}`);
        return android.invoke("list_mods", { name: a.name, kind: a.kind });
      }

      case "modrinth_search": {
        const i = a.instance ? inst(a.instance) : null;
        const facets = [[`project_type:${a.kind}`]];
        if (i) facets.push([`versions:${i.minecraftVersion}`]);
        const l = i ? mrLoader(i, a.kind) : null;
        if (l) facets.push([`categories:${l}`]);
        if (a.category) facets.push([`categories:${a.category}`]);
        const q = new URLSearchParams({ query: a.query || "", facets: JSON.stringify(facets), index: a.sort || "relevance", offset: String(a.offset || 0), limit: "24" });
        const r = await getJson(`${MR}/search?${q}`);
        return {
          hits: r.hits.map((h) => ({ id: h.project_id, slug: h.slug, title: h.title, description: h.description, author: h.author, downloads: h.downloads, follows: h.follows, iconUrl: h.icon_url || null, categories: h.display_categories || h.categories || [], updated: (h.date_modified || "").slice(0, 10), gallery: h.featured_gallery || h.gallery?.[0] || null, color: h.color != null ? `#${h.color.toString(16).padStart(6, "0")}` : null })),
          offset: r.offset, total: r.total_hits,
        };
      }
      case "modrinth_project": {
        const p = await getJson(`${MR}/project/${a.id}`);
        const i = a.instance ? inst(a.instance) : null;
        const versions = i ? (await mrVersions(p.id, i, a.kind)).slice(0, 30).map(versionView) : [];
        return {
          project: { id: p.id, slug: p.slug, title: p.title, description: p.description, kind: a.kind, downloads: p.downloads, follows: p.followers, iconUrl: p.icon_url || null, categories: p.categories || [], updated: (p.updated || "").slice(0, 10), license: p.license?.id || null, pageUrl: `https://modrinth.com/${p.project_type}/${p.slug}`, sourceUrl: p.source_url || null, wikiUrl: p.wiki_url || null, discordUrl: p.discord_url || null, gallery: (p.gallery || []).map((g) => ({ url: g.url, title: g.title || null })) },
          versions,
        };
      }
      case "modrinth_install": {
        const i = inst(a.instance);
        return mrInstall(i, a.projectId, a.versionId || null, a.kind);
      }
      case "modrinth_installed": {
        const i = inst(a.instance);
        const out = { mod: [], resourcepack: [], shader: [] };
        for (const e of Object.values(readIndex(i))) (out[e.kind] ||= []).push(e.projectId);
        return out;
      }

      case "player_skin": {
        const acc = accounts().find((x) => x.selected);
        if (!acc || acc.kind !== "microsoft") return { dataUrl: null, slim: false, source: "none", cape: null };
        try {
          const prof = await getJson(`https://sessionserver.mojang.com/session/minecraft/profile/${acc.uuid}`);
          const tex = JSON.parse(atob(prof.properties.find((x) => x.name === "textures").value)).textures;
          const b64 = await http(tex.SKIN.url.replace(/^http:/, "https:"), { base64: true });
          return { dataUrl: `data:image/png;base64,${b64}`, slim: tex.SKIN.metadata?.model === "slim", source: "account", cape: null };
        } catch {
          return { dataUrl: null, slim: false, source: "none", cape: null };
        }
      }
      case "skin_library":
        return { skins: [], applied: null, cape: "none", capes: ["none"], microsoft: false };

      case "check_update": {
        read();
        const current = snap.appVersion || "0";
        const list = await getJson(`https://api.github.com/repos/${UPDATE_REPO}/releases?per_page=30`);
        const best = list.filter((r) => !r.draft && !r.prerelease && r.tag_name.startsWith("vortex-v"))
          .sort((a, b) => cmpVersion(b.tag_name.slice(8), a.tag_name.slice(8)))[0];
        if (!best) return { available: false, current, version: current };
        const version = best.tag_name.slice(8);
        const want = { "arm64-v8a": "arm64", "armeabi-v7a": "armv7", "x86_64": "x86_64" }[snap.abi] || "universal";
        const asset = best.assets.find((x) => x.name === `Vortex-Client-android-${want}.apk`) || best.assets.find((x) => x.name === "Vortex-Client-android-universal.apk");
        return { available: !!asset && cmpVersion(version, current) > 0, current, version, notes: (best.body || "").trim(), published: (best.published_at || "").slice(0, 10), url: asset?.browser_download_url || null, size: asset?.size || 0, page: best.html_url };
      }
      case "install_update": {
        read();
        const dest = `${snap.cacheDir}/vortex-update.apk`;
        const id = `c${++seq}`;
        const off = onDownload(id, (d) => emit("update-progress", { stage: "download", done: d.done, total: d.total }));
        try {
          await new Promise((resolve, reject) => { pending.set(id, { resolve, reject }); N.download(id, a.url, dest); });
        } finally { off(); }
        emit("update-progress", { stage: "android", done: 1, total: 1 });
        N.installApk(dest);
        return null;
      }

      case "open_path": {
        read();
        let path = snap.gameHome;
        if (a.name) { try { path = inst(a.name).gameDir; } catch { /* the main folder */ } }
        const sub = { mods: "mods", resourcepacks: "resourcepacks", shaderpacks: "shaderpacks" }[a.target];
        if (sub && a.name) path = `${path}/${sub}`;
        N.openNative(`folder:${path}`);
        return null;
      }
      case "open_url":
        N.openUrl(a.url);
        return null;
      case "open_native":
        N.openNative(a.what);
        return null;
      default:
        throw `${cmd} isn't available on Android`;
    }
  },
};
