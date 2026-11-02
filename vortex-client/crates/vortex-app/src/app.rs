use std::collections::{HashMap, VecDeque};
use std::path::{Path, PathBuf};
use std::sync::mpsc::{self, Receiver, Sender};
use std::sync::Arc;

use eframe::egui::{self, Color32, RichText};
use tokio::sync::oneshot;
use vortex_core::accounts::{Account, AccountStore};
use vortex_core::auth::{self, DeviceCode};
use vortex_core::fabric::{self, LoaderInfo};
use vortex_core::instance::{Instance, Loader};
use vortex_core::logs::{LogLevel, LogLine};
use vortex_core::manifest::{VersionManifest, VersionType};
use vortex_core::paths::Paths;
use vortex_core::progress::{Progress, ProgressFn};
use vortex_core::settings::Settings;
use vortex_core::{launch, Launcher};

const ACCENT: Color32 = Color32::from_rgb(150, 90, 255);
const MAX_LOG_LINES: usize = 5000;

#[derive(Clone, Copy, PartialEq, Eq)]
enum Tab {
    Play,
    NewInstance,
    Accounts,
    Settings,
}

/// Messages from background tasks to the UI thread.
enum Event {
    Manifest(Result<Arc<VersionManifest>, String>),
    FabricLoaders { game: String, result: Result<Vec<LoaderInfo>, String> },
    Progress(Progress),
    Log(LogLine),
    Launched(String),
    GameExited { instance: String, code: Option<i32> },
    TaskFailed { instance: Option<String>, error: String },
    AccountUpdated(Account),
    DeviceCode(DeviceCode),
    LoginFinished(Result<Account, String>),
}

/// Sends events to the UI and wakes it up. Cheap to clone into tasks.
#[derive(Clone)]
struct Notifier {
    tx: Sender<Event>,
    ctx: egui::Context,
}

impl Notifier {
    fn send(&self, ev: Event) {
        let _ = self.tx.send(ev);
        self.ctx.request_repaint();
    }

    fn progress(&self) -> ProgressFn {
        let this = self.clone();
        Arc::new(move |p| this.send(Event::Progress(p)))
    }
}

struct NewInstanceForm {
    name: String,
    name_touched: bool,
    version: Option<String>,
    search: String,
    releases: bool,
    snapshots: bool,
    betas: bool,
    alphas: bool,
    fabric: bool,
    /// Loader list for (game version).
    loaders: Option<(String, Result<Vec<LoaderInfo>, String>)>,
    loaders_loading: bool,
    /// `None` = latest stable.
    loader_choice: Option<String>,
}

impl Default for NewInstanceForm {
    fn default() -> Self {
        Self {
            name: String::new(),
            name_touched: false,
            version: None,
            search: String::new(),
            releases: true,
            snapshots: false,
            betas: false,
            alphas: false,
            fabric: false,
            loaders: None,
            loaders_loading: false,
            loader_choice: None,
        }
    }
}

pub struct VortexApp {
    rt: tokio::runtime::Runtime,
    launcher: Arc<Launcher>,
    notifier: Notifier,
    rx: Receiver<Event>,

    settings: Settings,
    accounts: AccountStore,
    instances: Vec<Instance>,
    selected: Option<String>,
    tab: Tab,

    manifest: Option<Arc<VersionManifest>>,
    manifest_error: Option<String>,
    form: NewInstanceForm,

    offline_name: String,
    device_code: Option<DeviceCode>,
    login_busy: bool,

    /// An install/prepare step is in progress.
    busy: bool,
    progress: Option<Progress>,
    /// Running games by instance name → kill switch.
    running: HashMap<String, oneshot::Sender<()>>,
    logs: VecDeque<LogLine>,
    notice: Option<(bool, String)>,
    confirm_delete: bool,

    // Edit buffers.
    edit_server: String,
    edit_memory: Option<u32>,
    edit_for: Option<String>,
    jvm_args_text: String,
    java_path_text: String,
    client_id_text: String,
}

fn style(ctx: &egui::Context) {
    let mut visuals = egui::Visuals::dark();
    visuals.selection.bg_fill = ACCENT.linear_multiply(0.6);
    visuals.hyperlink_color = Color32::from_rgb(180, 140, 255);
    visuals.panel_fill = Color32::from_rgb(20, 18, 28);
    visuals.window_fill = Color32::from_rgb(24, 22, 34);
    visuals.extreme_bg_color = Color32::from_rgb(12, 11, 18);
    ctx.set_visuals(visuals);
}

fn load_accounts(path: &Path) -> (AccountStore, Option<String>) {
    match AccountStore::load(path) {
        Ok(s) => (s, None),
        Err(e) => {
            // Keep the broken file for inspection and start fresh.
            let _ = std::fs::rename(path, path.with_extension("json.broken"));
            let store = AccountStore::load(path).unwrap_or_default();
            (store, Some(format!("accounts.json was unreadable and has been reset: {e:#}")))
        }
    }
}

impl VortexApp {
    pub fn new(cc: &eframe::CreationContext<'_>) -> Self {
        style(&cc.egui_ctx);
        let paths = Paths::from_default();
        let mut notice = None;
        let settings = Settings::load(&paths.settings_file()).unwrap_or_else(|e| {
            notice = Some((true, format!("settings.json: {e:#}")));
            Settings::default()
        });
        let (accounts, acc_err) = load_accounts(&paths.accounts_file());
        if let Some(e) = acc_err {
            notice = Some((true, e));
        }
        let launcher = Arc::new(
            Launcher::with_concurrency(paths, settings.download_concurrency)
                .expect("cannot initialise the Vortex data directory"),
        );
        let rt = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(4)
            .enable_all()
            .build()
            .expect("cannot start async runtime");
        let (tx, rx) = mpsc::channel();
        let notifier = Notifier { tx, ctx: cc.egui_ctx.clone() };

        let mut app = Self {
            rt,
            launcher,
            notifier,
            rx,
            jvm_args_text: settings.extra_jvm_args.join(" "),
            java_path_text: settings
                .java_path
                .as_ref()
                .map(|p| p.display().to_string())
                .unwrap_or_default(),
            client_id_text: settings.microsoft_client_id.clone().unwrap_or_default(),
            settings,
            accounts,
            instances: Vec::new(),
            selected: None,
            tab: Tab::Play,
            manifest: None,
            manifest_error: None,
            form: NewInstanceForm::default(),
            offline_name: String::new(),
            device_code: None,
            login_busy: false,
            busy: false,
            progress: None,
            running: HashMap::new(),
            logs: VecDeque::new(),
            notice,
            confirm_delete: false,
            edit_server: String::new(),
            edit_memory: None,
            edit_for: None,
        };
        app.reload_instances();
        app.fetch_manifest(false);
        if app.instances.is_empty() {
            app.tab = Tab::NewInstance;
        }
        app
    }

    // ------------------------------------------------------------ actions --

    fn reload_instances(&mut self) {
        match self.launcher.instances().list() {
            Ok(list) => self.instances = list,
            Err(e) => self.error(format!("could not read instances: {e:#}")),
        }
        if self
            .selected
            .as_ref()
            .is_none_or(|s| !self.instances.iter().any(|i| &i.name == s))
        {
            self.selected = self.instances.first().map(|i| i.name.clone());
        }
    }

    fn fetch_manifest(&mut self, refresh: bool) {
        let launcher = self.launcher.clone();
        let n = self.notifier.clone();
        self.manifest_error = None;
        self.rt.spawn(async move {
            let r = launcher.manifest(refresh).await.map_err(|e| format!("{e:#}"));
            n.send(Event::Manifest(r));
        });
    }

    fn fetch_fabric_loaders(&mut self, game: String) {
        self.form.loaders_loading = true;
        let http = self.launcher.http().clone();
        let n = self.notifier.clone();
        self.rt.spawn(async move {
            let result = fabric::loader_versions(&http, &game).await.map_err(|e| format!("{e:#}"));
            n.send(Event::FabricLoaders { game, result });
        });
    }

    fn info(&mut self, msg: impl Into<String>) {
        self.notice = Some((false, msg.into()));
    }

    fn error(&mut self, msg: impl Into<String>) {
        let msg = msg.into();
        self.push_log(LogLine { level: LogLevel::Error, text: msg.clone() });
        self.notice = Some((true, msg));
    }

    fn push_log(&mut self, line: LogLine) {
        self.logs.push_back(line);
        while self.logs.len() > MAX_LOG_LINES {
            self.logs.pop_front();
        }
    }

    fn save_accounts(&mut self) {
        if let Err(e) = self.accounts.save() {
            self.error(format!("could not save accounts: {e:#}"));
        }
    }

    fn selected_instance(&self) -> Option<&Instance> {
        let name = self.selected.as_ref()?;
        self.instances.iter().find(|i| &i.name == name)
    }

    fn launch_selected(&mut self) {
        let Some(mut instance) = self.selected_instance().cloned() else {
            return;
        };
        let Some(account) = self.accounts.selected().cloned() else {
            self.error("Add an account first (Accounts tab).");
            self.tab = Tab::Accounts;
            return;
        };
        if self.running.contains_key(&instance.name) || self.busy {
            return;
        }
        instance.last_played = Some(auth::now_secs());
        let _ = instance.save();

        let (kill_tx, kill_rx) = oneshot::channel();
        self.running.insert(instance.name.clone(), kill_tx);
        self.busy = true;
        self.progress = None;
        self.notice = None;
        self.push_log(LogLine {
            level: LogLevel::Info,
            text: format!("=== Launching {} ({}) as {} ===", instance.name, instance.minecraft_version, account.username),
        });

        let launcher = self.launcher.clone();
        let settings = self.settings.clone();
        let n = self.notifier.clone();
        self.rt.spawn(async move {
            let name = instance.name.clone();
            if let Err(e) = run_game(launcher, instance, account, settings, kill_rx, n.clone()).await {
                n.send(Event::TaskFailed { instance: Some(name), error: format!("{e:#}") });
            }
        });
    }

    fn kill(&mut self, name: &str) {
        if let Some(k) = self.running.remove(name) {
            let _ = k.send(());
        }
    }

    fn create_instance(&mut self) {
        let Some(version) = self.form.version.clone() else {
            self.error("Pick a version first.");
            return;
        };
        let name = if self.form.name.trim().is_empty() {
            version.clone()
        } else {
            self.form.name.trim().to_string()
        };
        let loader = if self.form.fabric {
            Loader::Fabric { version: self.form.loader_choice.clone() }
        } else {
            Loader::Vanilla
        };
        match self.launcher.instances().create(Instance::new(&name, &version, loader)) {
            Ok(inst) => {
                self.info(format!("Created instance '{}'. Press PLAY to install & launch.", inst.name));
                self.selected = Some(inst.name.clone());
                self.form.name.clear();
                self.form.name_touched = false;
                self.reload_instances();
                self.tab = Tab::Play;
            }
            Err(e) => self.error(format!("{e:#}")),
        }
    }

    fn start_microsoft_login(&mut self) {
        let Some(client_id) = self.settings.client_id() else {
            self.error("Set a Microsoft (Azure) client ID in Settings first — see the README.");
            return;
        };
        self.login_busy = true;
        self.device_code = None;
        let http = self.launcher.http().clone();
        let n = self.notifier.clone();
        self.rt.spawn(async move {
            let result = async {
                let code = auth::start_device_login(&http, &client_id).await?;
                n.send(Event::DeviceCode(code.clone()));
                auth::finish_device_login(&http, &client_id, &code).await
            }
            .await
            .map_err(|e| format!("{e:#}"));
            n.send(Event::LoginFinished(result));
        });
    }

    fn handle(&mut self, ev: Event) {
        match ev {
            Event::Manifest(Ok(m)) => {
                if self.form.version.is_none() {
                    self.form.version = Some(m.latest.release.clone());
                }
                self.manifest = Some(m);
            }
            Event::Manifest(Err(e)) => self.manifest_error = Some(e),
            Event::FabricLoaders { game, result } => {
                self.form.loaders_loading = false;
                self.form.loaders = Some((game, result));
            }
            Event::Progress(p) => self.progress = Some(p),
            Event::Log(l) => self.push_log(l),
            Event::Launched(name) => {
                self.busy = false;
                self.progress = None;
                self.info(format!("{name} is running."));
            }
            Event::GameExited { instance, code } => {
                self.running.remove(&instance);
                let msg = match code {
                    Some(0) => format!("{instance} closed."),
                    Some(c) => format!("{instance} exited with code {c} — check the log below."),
                    None => format!("{instance} was stopped."),
                };
                self.push_log(LogLine { level: LogLevel::Info, text: format!("=== {msg} ===") });
                if matches!(code, Some(c) if c != 0) {
                    self.notice = Some((true, msg));
                } else {
                    self.info(msg);
                }
                self.reload_instances();
            }
            Event::TaskFailed { instance, error } => {
                self.busy = false;
                self.progress = None;
                if let Some(i) = instance {
                    self.running.remove(&i);
                }
                self.error(error);
            }
            Event::AccountUpdated(acc) => {
                if let Some(a) = self.accounts.accounts.iter_mut().find(|a| a.uuid == acc.uuid) {
                    *a = acc;
                }
                self.save_accounts();
            }
            Event::DeviceCode(code) => {
                self.notifier.ctx.copy_text(code.user_code.clone());
                self.notifier
                    .ctx
                    .open_url(egui::OpenUrl::new_tab(&code.verification_uri));
                self.device_code = Some(code);
            }
            Event::LoginFinished(result) => {
                self.login_busy = false;
                self.device_code = None;
                match result {
                    Ok(acc) => {
                        self.info(format!("Signed in as {}.", acc.username));
                        self.accounts.upsert(acc);
                        self.save_accounts();
                    }
                    Err(e) => self.error(e),
                }
            }
        }
    }

    // --------------------------------------------------------------- views --

    fn top_bar(&mut self, ui: &mut egui::Ui) {
        ui.horizontal(|ui| {
            ui.label(RichText::new("VORTEX").size(22.0).strong().color(ACCENT));
            ui.label(RichText::new("client · test UI").weak());
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                let current = self
                    .accounts
                    .selected()
                    .map(|a| format!("{} ({})", a.username, a.kind_label()))
                    .unwrap_or_else(|| "No account".into());
                let mut pick = None;
                egui::ComboBox::from_id_salt("account")
                    .selected_text(current)
                    .show_ui(ui, |ui| {
                        for a in &self.accounts.accounts {
                            let sel = self.accounts.selected.as_deref() == Some(&a.uuid);
                            if ui.selectable_label(sel, format!("{} ({})", a.username, a.kind_label())).clicked() {
                                pick = Some(a.uuid.clone());
                            }
                        }
                        if ui.button("Manage accounts…").clicked() {
                            self.tab = Tab::Accounts;
                        }
                    });
                if let Some(uuid) = pick {
                    self.accounts.select(&uuid);
                    self.save_accounts();
                }
            });
        });
    }

    fn nav(&mut self, ui: &mut egui::Ui) {
        ui.add_space(8.0);
        for (tab, label) in [
            (Tab::Play, "Play"),
            (Tab::NewInstance, "New instance"),
            (Tab::Accounts, "Accounts"),
            (Tab::Settings, "Settings"),
        ] {
            let b = egui::Button::selectable(self.tab == tab, RichText::new(label).size(15.0))
                .min_size(egui::vec2(ui.available_width(), 32.0));
            if ui.add(b).clicked() {
                self.tab = tab;
            }
        }
        ui.with_layout(egui::Layout::bottom_up(egui::Align::Min), |ui| {
            ui.small(format!("v{}", vortex_core::LAUNCHER_VERSION));
            if let Some(m) = &self.manifest {
                ui.small(format!("{} versions available", m.versions.len()));
            } else if self.manifest_error.is_some() {
                ui.small(RichText::new("offline").color(Color32::YELLOW));
            }
        });
    }

    fn console(&mut self, ui: &mut egui::Ui) {
        ui.horizontal(|ui| {
            ui.strong("Game log");
            if ui.small_button("Clear").clicked() {
                self.logs.clear();
            }
            if ui.small_button("Copy").clicked() {
                let text: Vec<&str> = self.logs.iter().map(|l| l.text.as_str()).collect();
                ui.ctx().copy_text(text.join("\n"));
            }
        });
        egui::ScrollArea::vertical()
            .auto_shrink([false, false])
            .stick_to_bottom(true)
            .show(ui, |ui| {
                for line in &self.logs {
                    let color = match line.level {
                        LogLevel::Error | LogLevel::Fatal => Color32::from_rgb(255, 110, 110),
                        LogLevel::Warn => Color32::from_rgb(240, 200, 90),
                        LogLevel::Debug | LogLevel::Trace => Color32::GRAY,
                        LogLevel::Info => Color32::from_rgb(210, 210, 220),
                    };
                    ui.label(RichText::new(&line.text).monospace().size(12.0).color(color));
                }
            });
    }

    fn play_tab(&mut self, ui: &mut egui::Ui) {
        if self.instances.is_empty() {
            ui.add_space(40.0);
            ui.vertical_centered(|ui| {
                ui.heading("No instances yet");
                ui.label("Create one to pick any Minecraft version — releases, snapshots, betas, alphas.");
                if ui.button("+ New instance").clicked() {
                    self.tab = Tab::NewInstance;
                }
            });
            return;
        }

        egui::Panel::left("instance_list")
            .resizable(true)
            .default_size(230.0)
            .show(ui, |ui| {
                ui.strong("Instances");
                ui.separator();
                egui::ScrollArea::vertical().show(ui, |ui| {
                    let mut pick = None;
                    for inst in &self.instances {
                        let running = self.running.contains_key(&inst.name);
                        let sel = self.selected.as_deref() == Some(&inst.name);
                        let text = format!(
                            "{}{}\n{} · {}",
                            if running { "[running] " } else { "" },
                            inst.name,
                            inst.minecraft_version,
                            inst.loader.label()
                        );
                        let b = egui::Button::selectable(sel, text).min_size(egui::vec2(ui.available_width(), 40.0));
                        if ui.add(b).clicked() {
                            pick = Some(inst.name.clone());
                        }
                    }
                    if let Some(p) = pick {
                        self.selected = Some(p);
                        self.confirm_delete = false;
                    }
                });
            });

        let Some(inst) = self.selected_instance().cloned() else {
            return;
        };
        if self.edit_for.as_deref() != Some(&inst.name) {
            self.edit_for = Some(inst.name.clone());
            self.edit_server = inst.join_server.clone().unwrap_or_default();
            self.edit_memory = inst.memory_mb;
        }

        ui.add_space(6.0);
        ui.heading(RichText::new(&inst.name).size(26.0).strong());
        ui.label(format!("Minecraft {} · {}", inst.minecraft_version, inst.loader.label()));
        ui.add_space(12.0);

        let running = self.running.contains_key(&inst.name);
        ui.horizontal(|ui| {
            if running && !self.busy {
                let b = egui::Button::new(RichText::new("STOP").size(20.0).strong())
                    .fill(Color32::from_rgb(170, 50, 60))
                    .min_size(egui::vec2(200.0, 48.0));
                if ui.add(b).clicked() {
                    self.kill(&inst.name);
                }
            } else {
                let b = egui::Button::new(RichText::new("PLAY").size(20.0).strong())
                    .fill(ACCENT)
                    .min_size(egui::vec2(200.0, 48.0));
                if ui.add_enabled(!self.busy, b).clicked() {
                    self.launch_selected();
                }
            }
            if self.busy {
                ui.spinner();
            }
        });

        if let Some(p) = &self.progress {
            ui.add_space(6.0);
            let text = if p.total > 0 {
                format!("{} — {}/{}", p.stage, p.done, p.total)
            } else {
                p.stage.clone()
            };
            ui.add(egui::ProgressBar::new(p.fraction()).text(text).animate(p.total == 0));
        }
        if let Some((is_err, msg)) = &self.notice {
            ui.add_space(6.0);
            let color = if *is_err { Color32::from_rgb(255, 120, 120) } else { Color32::from_rgb(140, 220, 160) };
            ui.label(RichText::new(msg).color(color));
        }

        ui.add_space(14.0);
        ui.separator();
        ui.strong("Instance options");
        egui::Grid::new("inst_opts").num_columns(2).spacing([12.0, 8.0]).show(ui, |ui| {
            ui.label("Memory");
            ui.horizontal(|ui| {
                let mut custom = self.edit_memory.is_some();
                if ui.checkbox(&mut custom, "custom").changed() {
                    self.edit_memory = custom.then_some(self.settings.max_memory_mb);
                }
                if let Some(m) = &mut self.edit_memory {
                    ui.add(egui::Slider::new(m, 512..=32768).step_by(256.0).suffix(" MB"));
                } else {
                    ui.weak(format!("default ({} MB)", self.settings.max_memory_mb));
                }
            });
            ui.end_row();
            ui.label("Join server");
            ui.add(egui::TextEdit::singleline(&mut self.edit_server).hint_text("host[:port] (optional)").desired_width(260.0));
            ui.end_row();
        });
        ui.horizontal(|ui| {
            if ui.button("Save options").clicked() {
                let mut updated = inst.clone();
                updated.memory_mb = self.edit_memory;
                let server = self.edit_server.trim();
                updated.join_server = (!server.is_empty()).then(|| server.to_string());
                match updated.save() {
                    Ok(()) => {
                        self.info("Saved.");
                        self.reload_instances();
                    }
                    Err(e) => self.error(format!("{e:#}")),
                }
            }
            if ui.button("Open folder").clicked() {
                let _ = std::fs::create_dir_all(inst.game_dir());
                open_path(&inst.game_dir());
            }
            if !running {
                if self.confirm_delete {
                    if ui.button(RichText::new("Really delete?").color(Color32::from_rgb(255, 120, 120))).clicked() {
                        match self.launcher.instances().delete(&inst) {
                            Ok(()) => {
                                self.info(format!("Deleted {}.", inst.name));
                                self.reload_instances();
                            }
                            Err(e) => self.error(format!("{e:#}")),
                        }
                        self.confirm_delete = false;
                    }
                    if ui.button("Cancel").clicked() {
                        self.confirm_delete = false;
                    }
                } else if ui.button("Delete").clicked() {
                    self.confirm_delete = true;
                }
            }
        });
        ui.weak(format!("Game folder: {}", inst.game_dir().display()));
    }

    fn new_instance_tab(&mut self, ui: &mut egui::Ui) {
        ui.heading("New instance");
        ui.add_space(6.0);
        let Some(manifest) = self.manifest.clone() else {
            match self.manifest_error.clone() {
                Some(e) => {
                    ui.colored_label(Color32::from_rgb(255, 120, 120), format!("Could not load versions: {e}"));
                    if ui.button("Retry").clicked() {
                        self.fetch_manifest(true);
                    }
                }
                None => {
                    ui.horizontal(|ui| {
                        ui.spinner();
                        ui.label("Loading Mojang's version list…");
                    });
                }
            }
            return;
        };

        let f = &mut self.form;
        ui.horizontal(|ui| {
            ui.label("Show:");
            ui.checkbox(&mut f.releases, "Releases");
            ui.checkbox(&mut f.snapshots, "Snapshots");
            ui.checkbox(&mut f.betas, "Beta");
            ui.checkbox(&mut f.alphas, "Alpha");
            ui.add(egui::TextEdit::singleline(&mut f.search).hint_text("Search versions").desired_width(160.0));
        });
        let search = f.search.trim().to_lowercase();
        let versions: Vec<_> = manifest
            .versions
            .iter()
            .filter(|v| match v.kind {
                VersionType::Release => f.releases,
                VersionType::Snapshot => f.snapshots,
                VersionType::OldBeta => f.betas,
                VersionType::OldAlpha => f.alphas,
                VersionType::Other => f.snapshots,
            })
            .filter(|v| search.is_empty() || v.id.to_lowercase().contains(&search))
            .collect();

        let mut picked = None;
        egui::Frame::group(ui.style()).show(ui, |ui| {
            ui.set_height(260.0);
            let row_h = ui.text_style_height(&egui::TextStyle::Body) + 6.0;
            egui::ScrollArea::vertical()
                .auto_shrink([false, false])
                .show_rows(ui, row_h, versions.len(), |ui, range| {
                    for v in &versions[range] {
                        let sel = f.version.as_deref() == Some(&v.id);
                        let text = format!("{:<24}  {:<9} {}", v.id, v.kind.label(), v.release_time.get(..10).unwrap_or(""));
                        if ui.selectable_label(sel, RichText::new(text).monospace()).clicked() {
                            picked = Some(v.id.clone());
                        }
                    }
                });
        });
        ui.weak(format!("{} of {} versions shown", versions.len(), manifest.versions.len()));
        if let Some(id) = picked {
            f.version = Some(id);
            f.loaders = None;
            f.loader_choice = None;
        }

        ui.add_space(8.0);
        let mut need_loaders = None;
        egui::Grid::new("new_inst").num_columns(2).spacing([12.0, 8.0]).show(ui, |ui| {
            ui.label("Version");
            ui.strong(f.version.clone().unwrap_or_else(|| "—".into()));
            ui.end_row();

            ui.label("Loader");
            ui.horizontal(|ui| {
                ui.radio_value(&mut f.fabric, false, "Vanilla");
                ui.radio_value(&mut f.fabric, true, "Fabric");
            });
            ui.end_row();

            if f.fabric {
                if let Some(v) = &f.version {
                    ui.label("Fabric loader");
                    match &f.loaders {
                        Some((game, Ok(list))) if game == v => {
                            if list.is_empty() {
                                ui.colored_label(Color32::YELLOW, "Fabric doesn't support this version (needs 1.14+).");
                            } else {
                                let text = f.loader_choice.clone().unwrap_or_else(|| "Latest stable".into());
                                egui::ComboBox::from_id_salt("loader").selected_text(text).show_ui(ui, |ui| {
                                    ui.selectable_value(&mut f.loader_choice, None, "Latest stable");
                                    for l in list.iter().take(30) {
                                        let label = if l.stable { l.version.clone() } else { format!("{} (beta)", l.version) };
                                        ui.selectable_value(&mut f.loader_choice, Some(l.version.clone()), label);
                                    }
                                });
                            }
                        }
                        Some((game, Err(e))) if game == v => {
                            ui.colored_label(Color32::from_rgb(255, 120, 120), e.as_str());
                        }
                        _ => {
                            ui.spinner();
                            if !f.loaders_loading {
                                need_loaders = Some(v.clone());
                            }
                        }
                    }
                    ui.end_row();
                }
            }

            ui.label("Name");
            if !f.name_touched {
                f.name = match (&f.version, f.fabric) {
                    (Some(v), true) => format!("{v} Fabric"),
                    (Some(v), false) => v.clone(),
                    (None, _) => String::new(),
                };
            }
            if ui.add(egui::TextEdit::singleline(&mut f.name).desired_width(260.0)).changed() {
                f.name_touched = true;
            }
            ui.end_row();
        });
        if let Some(game) = need_loaders {
            self.fetch_fabric_loaders(game);
        }

        ui.add_space(10.0);
        let can_create = self.form.version.is_some();
        if ui
            .add_enabled(can_create, egui::Button::new(RichText::new("Create instance").size(16.0)).fill(ACCENT))
            .clicked()
        {
            self.create_instance();
        }
        if let Some((true, msg)) = &self.notice {
            ui.colored_label(Color32::from_rgb(255, 120, 120), msg.as_str());
        }
    }

    fn accounts_tab(&mut self, ui: &mut egui::Ui) {
        ui.heading("Accounts");
        ui.add_space(6.0);
        let mut remove = None;
        let mut select = None;
        if self.accounts.accounts.is_empty() {
            ui.weak("No accounts yet.");
        }
        egui::Grid::new("accounts").num_columns(4).striped(true).spacing([16.0, 8.0]).show(ui, |ui| {
            for a in &self.accounts.accounts {
                let sel = self.accounts.selected.as_deref() == Some(&a.uuid);
                if ui.radio(sel, RichText::new(&a.username).strong()).clicked() {
                    select = Some(a.uuid.clone());
                }
                ui.label(a.kind_label());
                ui.monospace(auth::dashed_uuid(&a.uuid));
                if ui.small_button("Remove").clicked() {
                    remove = Some(a.uuid.clone());
                }
                ui.end_row();
            }
        });
        if let Some(u) = select {
            self.accounts.select(&u);
            self.save_accounts();
        }
        if let Some(u) = remove {
            self.accounts.remove(&u);
            self.save_accounts();
        }

        ui.add_space(16.0);
        ui.separator();
        ui.strong("Microsoft account");
        ui.label("Sign in with the Microsoft account that owns Minecraft: Java Edition.");
        ui.horizontal(|ui| {
            if ui.add_enabled(!self.login_busy, egui::Button::new("Sign in with Microsoft")).clicked() {
                self.start_microsoft_login();
            }
            if self.login_busy {
                ui.spinner();
            }
        });
        if let Some(code) = &self.device_code {
            egui::Frame::group(ui.style()).show(ui, |ui| {
                ui.label("Open this page and enter the code (it has been copied to your clipboard):");
                ui.hyperlink(&code.verification_uri);
                ui.label(RichText::new(&code.user_code).size(28.0).strong().monospace().color(ACCENT));
                ui.weak("Waiting for you to finish signing in…");
            });
        }
        if self.settings.client_id().is_none() {
            ui.weak("Requires a Microsoft (Azure) client ID — set it in Settings.");
        }

        ui.add_space(16.0);
        ui.separator();
        ui.strong("Offline account");
        ui.label("For singleplayer / LAN testing. Online-mode servers will reject it.");
        ui.horizontal(|ui| {
            ui.add(egui::TextEdit::singleline(&mut self.offline_name).hint_text("Username").desired_width(180.0));
            if ui.button("Add offline account").clicked() {
                match auth::offline_account(self.offline_name.trim()) {
                    Ok(acc) => {
                        self.info(format!("Added offline account {}.", acc.username));
                        self.accounts.upsert(acc);
                        self.save_accounts();
                        self.offline_name.clear();
                    }
                    Err(e) => self.error(format!("{e:#}")),
                }
            }
        });
        if let Some((is_err, msg)) = &self.notice {
            let color = if *is_err { Color32::from_rgb(255, 120, 120) } else { Color32::from_rgb(140, 220, 160) };
            ui.colored_label(color, msg.as_str());
        }
    }

    fn settings_tab(&mut self, ui: &mut egui::Ui) {
        ui.heading("Settings");
        ui.add_space(6.0);
        let s = &mut self.settings;
        egui::Grid::new("settings").num_columns(2).spacing([12.0, 10.0]).show(ui, |ui| {
            ui.label("Max memory");
            ui.add(egui::Slider::new(&mut s.max_memory_mb, 1024..=32768).step_by(256.0).suffix(" MB"));
            ui.end_row();
            ui.label("Min memory");
            ui.add(egui::Slider::new(&mut s.min_memory_mb, 256..=8192).step_by(256.0).suffix(" MB"));
            ui.end_row();
            ui.label("Java");
            ui.add(egui::TextEdit::singleline(&mut self.java_path_text).hint_text("automatic (recommended)").desired_width(360.0));
            ui.end_row();
            ui.label("Extra JVM args");
            ui.add(egui::TextEdit::singleline(&mut self.jvm_args_text).hint_text("e.g. -XX:+UseG1GC").desired_width(360.0));
            ui.end_row();
            ui.label("Microsoft client ID");
            ui.add(egui::TextEdit::singleline(&mut self.client_id_text).hint_text("Azure application (client) ID").desired_width(360.0));
            ui.end_row();
            ui.label("Parallel downloads");
            ui.add(egui::Slider::new(&mut s.download_concurrency, 1..=64));
            ui.end_row();
        });
        ui.add_space(10.0);
        ui.horizontal(|ui| {
            if ui.add(egui::Button::new("Save settings").fill(ACCENT)).clicked() {
                let java = self.java_path_text.trim();
                self.settings.java_path = (!java.is_empty()).then(|| PathBuf::from(java));
                self.settings.extra_jvm_args = self.jvm_args_text.split_whitespace().map(String::from).collect();
                let id = self.client_id_text.trim();
                self.settings.microsoft_client_id = (!id.is_empty()).then(|| id.to_string());
                if self.settings.min_memory_mb > self.settings.max_memory_mb {
                    self.settings.min_memory_mb = self.settings.max_memory_mb;
                }
                match self.settings.save(&self.launcher.paths().settings_file()) {
                    Ok(()) => self.info("Settings saved. (Parallel downloads apply after restart.)"),
                    Err(e) => self.error(format!("{e:#}")),
                }
            }
            if ui.button("Open data folder").clicked() {
                open_path(&self.launcher.paths().root);
            }
            if ui.button("Refresh version list").clicked() {
                self.fetch_manifest(true);
            }
        });
        ui.weak(format!("Data folder: {}", self.launcher.paths().root.display()));
        if let Some((is_err, msg)) = &self.notice {
            let color = if *is_err { Color32::from_rgb(255, 120, 120) } else { Color32::from_rgb(140, 220, 160) };
            ui.colored_label(color, msg.as_str());
        }
    }
}

impl eframe::App for VortexApp {
    fn ui(&mut self, ui: &mut egui::Ui, _frame: &mut eframe::Frame) {
        while let Ok(ev) = self.rx.try_recv() {
            self.handle(ev);
        }

        egui::Panel::top("top").show(ui, |ui| {
            ui.add_space(4.0);
            self.top_bar(ui);
            ui.add_space(4.0);
        });
        egui::Panel::left("nav").exact_size(170.0).show(ui, |ui| self.nav(ui));
        egui::Panel::bottom("console")
            .resizable(true)
            .default_size(200.0)
            .min_size(80.0)
            .show(ui, |ui| self.console(ui));
        egui::CentralPanel::default().show(ui, |ui| match self.tab {
            Tab::Play => self.play_tab(ui),
            tab => {
                // Scrollable so nothing hides behind the log panel on small windows.
                egui::ScrollArea::vertical()
                    .id_salt("tab_scroll")
                    .auto_shrink([false, false])
                    .show(ui, |ui| match tab {
                        Tab::NewInstance => self.new_instance_tab(ui),
                        Tab::Accounts => self.accounts_tab(ui),
                        _ => self.settings_tab(ui),
                    });
            }
        });
    }
}

/// Install, launch and babysit one game process.
async fn run_game(
    launcher: Arc<Launcher>,
    instance: Instance,
    mut account: Account,
    settings: Settings,
    mut kill: oneshot::Receiver<()>,
    n: Notifier,
) -> anyhow::Result<()> {
    let before = account.clone();
    let session = launcher.session_for(&mut account, &settings).await?;
    if account != before {
        n.send(Event::AccountUpdated(account.clone()));
    }
    let cmd = launcher
        .prepare_launch(&instance, &session, &settings, &n.progress())
        .await?;
    n.send(Event::Log(LogLine { level: LogLevel::Debug, text: format!("> {}", cmd.display_redacted()) }));

    let mut game = launch::spawn(&cmd)?;
    n.send(Event::Launched(instance.name.clone()));

    // A dropped kill switch (e.g. UI closing) must not kill the game.
    let mut kill_armed = true;
    let status = loop {
        tokio::select! {
            line = game.logs.recv() => match line {
                Some(l) => n.send(Event::Log(l)),
                None => break game.wait().await?,
            },
            res = &mut kill, if kill_armed => {
                if res.is_ok() {
                    let _ = game.child.kill().await;
                    break game.wait().await?;
                }
                kill_armed = false;
            }
        }
    };
    while let Ok(l) = game.logs.try_recv() {
        n.send(Event::Log(l));
    }
    n.send(Event::GameExited { instance: instance.name.clone(), code: status.code() });
    Ok(())
}

fn open_path(path: &Path) {
    let program = if cfg!(windows) {
        "explorer"
    } else if cfg!(target_os = "macos") {
        "open"
    } else {
        "xdg-open"
    };
    let _ = std::process::Command::new(program).arg(path).spawn();
}
