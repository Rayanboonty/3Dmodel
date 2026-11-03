// Demo backend used when the UI runs in a normal browser (design previews,
// screenshots). Mirrors the Tauri commands with in-memory data.

const listeners = new Map();
const emit = (event, payload) => (listeners.get(event) || []).forEach((h) => h(payload));
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

const now = Math.floor(Date.now() / 1000);
const state = {
  settings: {
    min_memory_mb: 512, max_memory_mb: 4096, java_path: null, extra_jvm_args: [],
    microsoft_client_id: null, download_concurrency: 32, show_snapshots: false,
    show_old_versions: false, sky: "dawn", ui_sounds: true, animations: true,
  },
  accounts: [{ uuid: "5627dd98e6be3c21b8a8e92344183641", username: "Steve", kind: "offline", selected: true }],
  instances: [
    { name: "PvP Practice", minecraftVersion: "1.8.9", loader: "vanilla", loaderVersion: null, loaderLabel: "Vanilla", memoryMb: 2048, joinServer: "[YOUR SERVER]", jvmArgs: "", lastPlayed: now - 3600, gameDir: "", running: false, modCount: 0 },
    { name: "Fabric Optimized", minecraftVersion: "1.21.4", loader: "fabric", loaderVersion: null, loaderLabel: "Fabric", memoryMb: null, joinServer: null, jvmArgs: "", lastPlayed: now - 86400, gameDir: "", running: false, modCount: 3 },
    { name: "Forge Adventure", minecraftVersion: "1.20.1", loader: "forge", loaderVersion: "47.4.28", loaderLabel: "Forge 47.4.28", memoryMb: 6144, joinServer: null, jvmArgs: "", lastPlayed: now - 3 * 86400, gameDir: "", running: false, modCount: 2 },
    { name: "Survival", minecraftVersion: "26.3", loader: "vanilla", loaderVersion: null, loaderLabel: "Vanilla", memoryMb: null, joinServer: null, jvmArgs: "", lastPlayed: null, gameDir: "", running: false, modCount: 0 },
  ],
  mods: {
    "Fabric Optimized": [
      { file: "sodium.jar", enabled: true, size: 1200000, name: "Sodium", version: "0.6.13", description: "Modern rendering engine", loader: "fabric" },
      { file: "lithium.jar", enabled: true, size: 700000, name: "Lithium", version: "0.15.0", description: "Game logic optimizations", loader: "fabric" },
      { file: "zoomify.jar.disabled", enabled: false, size: 300000, name: "Zoomify", version: "2.14.2", description: "Zoom with style", loader: "fabric" },
    ],
    "Forge Adventure": [
      { file: "jei.jar", enabled: true, size: 1800000, name: "Just Enough Items", version: "15.20.0", description: "Item and recipe viewer", loader: "forge" },
      { file: "journeymap.jar", enabled: true, size: 5200000, name: "JourneyMap", version: "5.10.3", description: "Real-time mapping", loader: "forge" },
    ],
  },
  running: new Map(),
};

function versionList() {
  const ids = [
    ["26.4-snapshot-6", "snapshot", "2026-10-27"], ["26.3", "release", "2026-09-15"], ["26.2", "release", "2026-06-16"],
    ["26.1.2", "release", "2026-04-09"], ["26.1", "release", "2026-03-24"], ["1.21.11", "release", "2025-12-09"],
    ["1.21.10", "release", "2025-10-07"], ["1.21.4", "release", "2024-12-03"], ["1.21.1", "release", "2024-08-08"],
    ["1.20.4", "release", "2023-12-07"], ["1.20.1", "release", "2023-06-12"], ["1.19.4", "release", "2023-03-14"],
    ["1.18.2", "release", "2022-02-28"], ["1.16.5", "release", "2021-01-14"], ["1.12.2", "release", "2017-09-18"],
    ["1.8.9", "release", "2015-12-09"], ["1.7.10", "release", "2014-06-26"], ["1.6.4", "release", "2013-09-19"],
    ["b1.7.3", "beta", "2011-07-08"], ["b1.8.1", "beta", "2011-09-19"], ["a1.2.6", "alpha", "2010-12-03"],
    ["a1.0.4", "alpha", "2010-07-09"], ["rd-132211", "alpha", "2009-05-13"],
  ];
  return { latestRelease: "26.3", latestSnapshot: "26.4-snapshot-6", versions: ids.map(([id, kind, date]) => ({ id, kind, date })) };
}

function view(i) {
  return { ...i, running: state.running.has(i.name) };
}

async function simulateLaunch(name) {
  const inst = state.instances.find((i) => i.name === name);
  emit("game-state", { instance: name, state: "preparing" });
  const stages = [
    ["Resolving " + inst.minecraftVersion, 0, 0, 500],
    ["Libraries", 31, 31, 900],
    ["Assets", 734, 734, 1600],
    ["Checking Java runtime", 0, 0, 500],
    ["Ready", 1, 1, 300],
  ];
  for (const [stage, total, , ms] of stages) {
    if (!state.running.has(name)) return;
    const steps = Math.max(1, Math.min(12, total));
    for (let s = 0; s <= steps; s++) {
      emit("launch-progress", { instance: name, stage, done: Math.round((s / steps) * total), total });
      await wait(ms / (steps + 1));
    }
  }
  if (!state.running.has(name)) return;
  emit("game-state", { instance: name, state: "running" });
  const lines = [
    ["info", "[Render thread/INFO] Setting user: Steve"],
    ["info", "[Render thread/INFO] Backend library: LWJGL version 3.3.3"],
    ["warn", "[Render thread/WARN] Offline account: singleplayer and LAN only."],
    ["info", "[Render thread/INFO] Reloading ResourceManager: vanilla"],
    ["info", "[Render thread/INFO] Created: 1024x512x4 minecraft:textures/atlas/blocks.png-atlas"],
    ["info", "[Render thread/INFO] Sound engine started"],
  ];
  for (const [level, text] of lines) {
    if (!state.running.has(name)) return;
    await wait(350);
    emit("game-log", { instance: name, lines: [{ level, text }] });
  }
}

export const mock = {
  listen(event, handler) {
    if (!listeners.has(event)) listeners.set(event, []);
    listeners.get(event).push(handler);
    return Promise.resolve(() => {});
  },
  async invoke(cmd, args) {
    await wait(60 + Math.random() * 120);
    switch (cmd) {
      case "bootstrap":
        return { appVersion: "0.2.0", dataDir: "%APPDATA%\\VortexClient", platform: "windows", settings: { ...state.settings }, accounts: state.accounts, instances: state.instances.map(view) };
      case "list_versions":
        return versionList();
      case "loader_versions": {
        if (args.loader === "fabric") {
          const ok = !/^(1\.[0-9]\.|1\.1[0-3]|b|a|rd)/.test(args.game);
          return ok ? ["0.20.1", "0.20.0", "0.19.2"].map((v, i) => ({ id: v, display: v, stable: true, recommended: i === 0, latest: i === 0 })) : [];
        }
        if (args.loader === "forge") {
          const ok = /^1\.(6|7|8|9|1[0-9]|2[0-1])/.test(args.game);
          return ok ? ["47.4.28", "47.4.20", "47.4.10"].map((v, i) => ({ id: v, display: v, stable: true, recommended: i === 0, latest: i === 0 })) : [];
        }
        return [];
      }
      case "list_instances":
        return state.instances.map(view);
      case "create_instance": {
        if (state.instances.some((i) => i.name.toLowerCase() === args.name.toLowerCase())) throw `an instance called '${args.name}' already exists`;
        const labels = { vanilla: "Vanilla", fabric: "Fabric", forge: "Forge" };
        const inst = { name: args.name, minecraftVersion: args.version, loader: args.loader, loaderVersion: args.loaderVersion || null, loaderLabel: labels[args.loader] + (args.loaderVersion ? " " + args.loaderVersion : ""), memoryMb: args.memoryMb || null, joinServer: null, jvmArgs: "", lastPlayed: null, gameDir: "", running: false, modCount: 0 };
        state.instances.push(inst);
        return view(inst);
      }
      case "update_instance": {
        const inst = state.instances.find((i) => i.name === args.name);
        inst.memoryMb = args.memoryMb ?? null;
        inst.joinServer = args.joinServer || null;
        if (args.jvmArgs != null) inst.jvmArgs = args.jvmArgs;
        return view(inst);
      }
      case "delete_instance":
        state.instances = state.instances.filter((i) => i.name !== args.name);
        return state.instances.map(view);
      case "launch_instance": {
        if (!state.accounts.some((a) => a.selected)) throw "add an account first (Accounts page)";
        state.running.set(args.name, true);
        const inst = state.instances.find((i) => i.name === args.name);
        inst.lastPlayed = Math.floor(Date.now() / 1000);
        simulateLaunch(args.name);
        return null;
      }
      case "stop_instance":
        if (state.running.delete(args.name)) {
          setTimeout(() => emit("game-state", { instance: args.name, state: "exited", code: 0 }), 300);
        }
        return null;
      case "add_offline_account": {
        state.accounts.forEach((a) => (a.selected = false));
        state.accounts.push({ uuid: Math.random().toString(16).slice(2).padEnd(32, "0"), username: args.username, kind: "offline", selected: true });
        return state.accounts;
      }
      case "remove_account":
        state.accounts = state.accounts.filter((a) => a.uuid !== args.uuid);
        if (state.accounts.length && !state.accounts.some((a) => a.selected)) state.accounts[0].selected = true;
        return state.accounts;
      case "select_account":
        state.accounts.forEach((a) => (a.selected = a.uuid === args.uuid));
        return state.accounts;
      case "microsoft_login":
        throw "set your Microsoft (Azure) client ID in Settings first — see the README";
      case "save_settings":
        state.settings = { ...args.settings };
        return state.settings;
      case "news":
        return [
          { title: "Minecraft 26.4 Snapshot 6", version: "26.4-snapshot-6", kind: "snapshot", image_url: null, date: "2026-10-27", summary: "A new snapshot is out with fixes and tweaks." },
          { title: "Minecraft 26.3", version: "26.3", kind: "release", image_url: null, date: "2026-09-15", summary: "The latest release of Minecraft: Java Edition." },
          { title: "Minecraft 26.2", version: "26.2", kind: "release", image_url: null, date: "2026-06-16", summary: "Another update for Java Edition." },
        ];
      case "list_mods":
        return state.mods[args.name] || [];
      case "toggle_mod": {
        const m = (state.mods[args.name] || []).find((x) => x.file === args.file);
        if (m) {
          m.enabled = args.enabled;
          m.file = args.enabled ? m.file.replace(/\.disabled$/, "") : m.file + ".disabled";
        }
        return state.mods[args.name] || [];
      }
      case "delete_mod":
        state.mods[args.name] = (state.mods[args.name] || []).filter((x) => x.file !== args.file);
        return state.mods[args.name];
      case "import_mods":
        return state.mods[args.name] || [];
      case "open_path":
      case "open_url":
        return null;
      default:
        throw `unknown command ${cmd}`;
    }
  },
};
