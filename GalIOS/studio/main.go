// GalIOS Studio: the Windows side of GalIOS. It builds your own iOS 27 image set in WSL (Ubuntu inside Windows)
// with OneiOS's scripts, and drives the phone over USB: it puts the runtime and the image in place with the OneOS
// boot engine already on the phone, boots iOS, brings One UI back and collects the logs. Its window is a page in
// your browser, served only to this PC (127.0.0.1).
package main

import (
	"context"
	"embed"
	"encoding/json"
	"fmt"
	"io/fs"
	"log"
	"net"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

//go:embed ui
var uiFS embed.FS

//go:embed assets
var assetFS embed.FS

const version = "1.0"

type App struct {
	hub     *Hub
	store   *Store
	mu      sync.Mutex
	pc      PCStatus
	phone   PhoneStatus
	engine  *Engine
	lastPing atomic.Int64
}

func main() {
	app := &App{hub: NewHub(), store: LoadStore()}
	app.lastPing.Store(time.Now().Unix())

	mux := http.NewServeMux()
	sub, _ := fs.Sub(uiFS, "ui")
	mux.Handle("/", http.FileServer(http.FS(sub)))
	mux.HandleFunc("/api/state", app.handleState)
	mux.HandleFunc("/api/action", app.handleAction)
	mux.HandleFunc("/api/settings", app.handleSettings)
	mux.HandleFunc("/api/events", app.handleEvents)
	mux.HandleFunc("/api/log", app.handleLog)
	mux.HandleFunc("/api/ping", func(w http.ResponseWriter, r *http.Request) { app.lastPing.Store(time.Now().Unix()); w.WriteHeader(204) })
	mux.HandleFunc("/api/quit", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(204)
		go func() { time.Sleep(300 * time.Millisecond); os.Exit(0) }()
	})

	addr := "127.0.0.1:" + envOr("STUDIO_PORT", "47827")
	ln, err := net.Listen("tcp", addr)
	if err != nil {
		// Already running: open its page instead of starting a second one.
		openBrowser("http://" + addr + "/")
		return
	}
	url := "http://" + ln.Addr().String() + "/"
	log.Printf("GalIOS Studio %s at %s", version, url)
	if os.Getenv("STUDIO_NO_BROWSER") == "" {
		openBrowser(url)
	}
	go app.idleExit()
	go func() { app.refreshPC(); app.refreshPhone() }()
	log.Fatal(http.Serve(ln, withHeaders(mux)))
}

func envOr(k, d string) string {
	if v := os.Getenv(k); v != "" {
		return v
	}
	return d
}

func withHeaders(h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Only this PC's own pages may call the API.
		if o := r.Header.Get("Origin"); o != "" && !strings.HasPrefix(o, "http://127.0.0.1:") && !strings.HasPrefix(o, "http://localhost:") {
			http.Error(w, "forbidden", 403)
			return
		}
		w.Header().Set("Cache-Control", "no-store")
		h.ServeHTTP(w, r)
	})
}

// idleExit ends Studio a while after its page was closed, unless a task is still running.
func (a *App) idleExit() {
	if os.Getenv("STUDIO_NO_IDLE_EXIT") != "" {
		return
	}
	for range time.Tick(20 * time.Second) {
		if time.Now().Unix()-a.lastPing.Load() > 120 && !a.hub.Job().Running {
			os.Exit(0)
		}
	}
}

func openBrowser(url string) {
	var c *exec.Cmd
	switch runtime.GOOS {
	case "windows":
		c = exec.Command("rundll32", "url.dll,FileProtocolHandler", url)
	case "darwin":
		c = exec.Command("open", url)
	default:
		c = exec.Command("xdg-open", url)
	}
	_ = c.Start()
}

func writeJSON(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	_ = json.NewEncoder(w).Encode(v)
}

func (a *App) refreshPC() {
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	st := checkPC(ctx, a.store.Get())
	a.mu.Lock()
	a.pc = st
	a.mu.Unlock()
}

func (a *App) refreshPhone() {
	ctx, cancel := context.WithTimeout(context.Background(), 60*time.Second)
	defer cancel()
	st := checkPhone(ctx, a.store.Get())
	a.mu.Lock()
	a.phone = st
	a.mu.Unlock()
}

func (a *App) handleState(w http.ResponseWriter, r *http.Request) {
	a.lastPing.Store(time.Now().Unix())
	a.mu.Lock()
	defer a.mu.Unlock()
	writeJSON(w, map[string]any{
		"version": version, "settings": a.store.Get(), "pc": a.pc, "phone": a.phone, "job": a.hub.Job(),
		"windows": runtime.GOOS == "windows", "dataDir": dataDir(),
	})
}

func (a *App) handleLog(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, a.hub.Lines(3000))
}

func (a *App) handleSettings(w http.ResponseWriter, r *http.Request) {
	if r.Method != "POST" {
		writeJSON(w, a.store.Get())
		return
	}
	var s Settings
	if err := json.NewDecoder(r.Body).Decode(&s); err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	if err := a.store.Set(s); err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	writeJSON(w, a.store.Get())
}

func (a *App) handleEvents(w http.ResponseWriter, r *http.Request) {
	fl, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "no streaming", 500)
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	c := a.hub.Subscribe()
	defer a.hub.Unsubscribe(c)
	ping := time.NewTicker(15 * time.Second)
	defer ping.Stop()
	for {
		select {
		case <-r.Context().Done():
			return
		case ev := <-c:
			b, _ := json.Marshal(ev)
			fmt.Fprintf(w, "data: %s\n\n", b)
			fl.Flush()
		case <-ping.C:
			fmt.Fprint(w, ": ping\n\n")
			fl.Flush()
		}
	}
}

type actionReq struct {
	Name string            `json:"name"`
	Args map[string]string `json:"args"`
}

// handleAction runs one of Studio's actions: quick ones answer at once, long ones become the running task.
func (a *App) handleAction(w http.ResponseWriter, r *http.Request) {
	var req actionReq
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		http.Error(w, err.Error(), 400)
		return
	}
	s := a.store.Get()
	reply := func(msg string, err error) {
		if err != nil {
			writeJSON(w, map[string]any{"ok": false, "message": err.Error()})
			return
		}
		writeJSON(w, map[string]any{"ok": true, "message": msg})
	}
	start := func(title string, fn func(ctx context.Context, j *JobCtx) (string, error)) {
		err := a.hub.Start(req.Name, title, func(ctx context.Context, j *JobCtx) (string, error) {
			defer func() { go a.refreshPC(); go a.refreshPhone() }()
			return fn(ctx, j)
		})
		reply("started", err)
	}
	phone := func(title string, fn func(ctx context.Context, j *JobCtx, serial string) (string, error)) {
		start(title, func(ctx context.Context, j *JobCtx) (string, error) {
			serial, err := pickSerial(ctx, s)
			if err != nil {
				return "", err
			}
			return fn(ctx, j, serial)
		})
	}
	script, _ := assetFS.ReadFile("assets/build-ios-image-wsl.sh")

	switch req.Name {
	// ---- this PC
	case "pc.refresh":
		go a.refreshPC()
		reply("checking", nil)
	case "wsl.install":
		start("Install WSL and "+s.Distro, func(ctx context.Context, j *JobCtx) (string, error) { return installWSL(ctx, j, s) })
	case "wsl.terminal":
		reply("opened", openTerminal(s))
	case "pick.ipsw":
		p, err := pickFile("Apple restore file (*.ipsw)|*.ipsw|All files (*.*)|*.*")
		if err == nil && p != "" {
			s.IPSW = p
			err = a.store.Set(s)
		}
		reply(p, err)
	case "pick.outdir":
		p, err := pickFolder()
		if err == nil && p != "" {
			s.OutDir = p
			err = a.store.Set(s)
		}
		reply(p, err)
	case "open.outdir":
		reply("opened", openFolder(s.OutDir))
	case "open.data":
		os.MkdirAll(dataDir(), 0o755)
		reply("opened", openFolder(dataDir()))

	// ---- the image build in WSL
	case "build.setup":
		start("Set up the build tools", func(ctx context.Context, j *JobCtx) (string, error) { return buildImage(ctx, j, s, "setup", script) })
	case "build.ipsw":
		start("Build the iOS 27 image from your .ipsw", func(ctx context.Context, j *JobCtx) (string, error) { return buildImage(ctx, j, s, "ipsw", script) })
	case "build.download":
		start("Build the iOS 27 image (download from Apple)", func(ctx context.Context, j *JobCtx) (string, error) {
			return buildImage(ctx, j, s, "download", script)
		})
	case "build.verify":
		start("Check the image", func(ctx context.Context, j *JobCtx) (string, error) { return verifyImage(ctx, j, s) })
	case "build.clean":
		start("Free the build's space", func(ctx context.Context, j *JobCtx) (string, error) { return cleanBuild(ctx, j, s) })
	case "job.stop":
		a.hub.Stop()
		go stopBuild(context.Background(), s)
		reply("stopping", nil)

	// ---- the phone
	case "adb.get":
		start("Get adb", getADB)
	case "phone.refresh":
		go a.refreshPhone()
		reply("checking", nil)
	case "phone.install-galios":
		phone("Install GalIOS on the phone", func(ctx context.Context, j *JobCtx, serial string) (string, error) {
			apk, _ := assetFS.ReadFile("assets/GalIOS.apk")
			tmp := filepath.Join(dataDir(), "GalIOS.apk")
			os.MkdirAll(dataDir(), 0o755)
			if err := os.WriteFile(tmp, apk, 0o644); err != nil {
				return "", err
			}
			j.Step("Installing GalIOS (" + human(int64(len(apk))) + ")")
			code, err := stream(ctx, nil, j.Line, adbExe(s), adbArgs(serial, "install", "-r", tmp)...)
			if err != nil || code != 0 {
				return "", fmt.Errorf("adb install failed (exit %d): if GalIOS was installed from elsewhere, uninstall it first", code)
			}
			return "GalIOS is installed on the phone.", nil
		})
	case "phone.push-image":
		phone("Copy the image to the phone's Download folder", func(ctx context.Context, j *JobCtx, serial string) (string, error) {
			local := joinOut(s.OutDir, imageName)
			if !fileExists(local) {
				return "", fmt.Errorf("no image at %s: build it first", local)
			}
			j.Step("Copying " + imageName + " (about 11 GB)")
			if err := pushFile(ctx, j, s, serial, local, "/sdcard/Download/"+imageName); err != nil {
				return "", err
			}
			return "The image is in the phone's Download folder: GalIOS lists it under Found on this phone.", nil
		})
	case "phone.engine":
		phone("Install the boot engine", func(ctx context.Context, j *JobCtx, serial string) (string, error) {
			e, err := pullEngine(ctx, j, s, serial)
			if err != nil {
				return "", err
			}
			a.mu.Lock()
			a.engine = e
			a.mu.Unlock()
			return installEngine(ctx, j, s, serial, e)
		})
	case "phone.runtime":
		phone("Add the OneiOS runtime", func(ctx context.Context, j *JobCtx, serial string) (string, error) {
			p, err := latestRuntime(ctx, j)
			if err != nil {
				return "", err
			}
			return importArchive(ctx, j, s, serial, "runtime", p)
		})
	case "phone.import-image":
		phone("Add the iOS 27 image to the phone", func(ctx context.Context, j *JobCtx, serial string) (string, error) {
			local := req.Args["path"]
			if local == "" {
				local = joinOut(s.OutDir, imageName)
			}
			if !fileExists(local) {
				return "", fmt.Errorf("no image at %s: build it first", local)
			}
			return importArchive(ctx, j, s, serial, "image", local)
		})
	case "phone.status":
		phone("Boot check", func(ctx context.Context, j *JobCtx, serial string) (string, error) {
			st := stageStatus(ctx, s, serial)
			j.Log("runtime: %s · image: %s · session open: %v", orNone(st.Runtime), orNone(st.Image), st.Session)
			if st.Ready {
				return "Ready to boot.", nil
			}
			return "", fmt.Errorf("not ready: %s", orNone(st.Reason))
		})
	case "phone.boot":
		phone("Boot iOS 27", func(ctx context.Context, j *JobCtx, serial string) (string, error) {
			e, err := pullEngine(ctx, j, s, serial)
			if err != nil {
				return "", err
			}
			j.Step("Staging the session scripts again (S25 Ultra check adapted)")
			if _, err := stageScripts(ctx, j, s, serial, e); err != nil {
				return "", err
			}
			j.Step("Starting iOS: One UI stops in about 40 seconds")
			r := verb(ctx, s, serial, nil, "switch", "1", "1")
			if !r.OK {
				return "", fmt.Errorf("%s", r.Message)
			}
			return "iOS is starting. To come back: Volume Up + Volume Down for 3 seconds, or Return to One UI here.", nil
		})
	case "phone.return":
		phone("Return to One UI", func(ctx context.Context, j *JobCtx, serial string) (string, error) {
			r := verb(ctx, s, serial, nil, "return")
			if !r.OK {
				return "", fmt.Errorf("%s", r.Message)
			}
			return "The return is on its way (about 45 seconds).", nil
		})
	case "phone.logs":
		phone("Collect the session logs", func(ctx context.Context, j *JobCtx, serial string) (string, error) { return pullLogs(ctx, j, s, serial) })
	case "phone.reboot":
		phone("Restart the phone", func(ctx context.Context, j *JobCtx, serial string) (string, error) {
			_, _, err := runLines(ctx, nil, adbExe(s), adbArgs(serial, "reboot")...)
			return "The phone is restarting into One UI. Start your temporary root again after it is up.", err
		})
	case "quit":
		reply("bye", nil)
		go func() { time.Sleep(300 * time.Millisecond); os.Exit(0) }()
	default:
		reply("", fmt.Errorf("unknown action %q", req.Name))
	}
}

func orNone(s string) string {
	if s == "" {
		return "none"
	}
	return s
}

// pickFile shows Windows' file dialog (PowerShell, STA) and returns the chosen path, or "" when cancelled.
func pickFile(filter string) (string, error) {
	if p := os.Getenv("STUDIO_PICK"); p != "" {
		return p, nil
	}
	if runtime.GOOS != "windows" {
		return "", fmt.Errorf("type the path in Settings")
	}
	ps := `Add-Type -AssemblyName System.Windows.Forms; $d = New-Object System.Windows.Forms.OpenFileDialog; ` +
		`$d.Filter = '` + filter + `'; $d.Title = 'GalIOS Studio'; if ($d.ShowDialog() -eq 'OK') { $d.FileName }`
	out, err := exec.Command("powershell.exe", "-NoProfile", "-STA", "-Command", ps).Output()
	return strings.TrimSpace(string(out)), err
}

func pickFolder() (string, error) {
	if p := os.Getenv("STUDIO_PICK_DIR"); p != "" {
		return p, nil
	}
	if runtime.GOOS != "windows" {
		return "", fmt.Errorf("type the folder in Settings")
	}
	ps := `Add-Type -AssemblyName System.Windows.Forms; $d = New-Object System.Windows.Forms.FolderBrowserDialog; ` +
		`$d.Description = 'Where GalIOS Studio puts the finished image'; if ($d.ShowDialog() -eq 'OK') { $d.SelectedPath }`
	out, err := exec.Command("powershell.exe", "-NoProfile", "-STA", "-Command", ps).Output()
	return strings.TrimSpace(string(out)), err
}
