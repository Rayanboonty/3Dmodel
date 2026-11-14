package main

// Studio updates itself from the GitHub releases of GalIOS's repository: a release asset named
// GalIOS-Studio-<version>.exe whose version is newer than this one, with GalIOS-Studio-<version>.exe.sha256 beside
// it (an asset without its sum is never used). The new exe is downloaded next to the running one, checked against
// the sum, and swapped in: Windows lets a running exe be renamed, not overwritten, so this one becomes .old, the new
// one takes its name, starts with --updated and this one ends. Never while a task runs (a build in Ubuntu, a copy to
// the phone): the swap waits until Studio is idle. STUDIO_NO_UPDATE=1 turns it off.

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strings"
	"sync"
	"time"
)

const updateRepo = "Rayanboonty/3Dmodel"

var studioAsset = regexp.MustCompile(`^GalIOS-Studio-(\d+(?:\.\d+)*)\.exe$`)

// StudioUpdate is what the page shows about Studio's own update.
type StudioUpdate struct {
	Version string `json:"version"` // the newer version, "" when none is known
	State   string `json:"state"`   // "", "downloading", "waiting" (for the running task), "restarting", "failed"
	Message string `json:"message"`
}

type studioRelease struct {
	Version, URL, SumURL, Notes string
	Size                        int64
}

type updater struct {
	mu    sync.Mutex
	state StudioUpdate
}

func (u *updater) get() StudioUpdate {
	u.mu.Lock()
	defer u.mu.Unlock()
	return u.state
}

func (u *updater) set(f func(*StudioUpdate)) {
	u.mu.Lock()
	f(&u.state)
	u.mu.Unlock()
}

// newestStudio is the newest release asset newer than current that has its .sha256; nil when there is none.
func newestStudio(body []byte, current string) (*studioRelease, error) {
	var rels []struct {
		Draft  bool   `json:"draft"`
		Body   string `json:"body"`
		Assets []struct {
			Name string `json:"name"`
			URL  string `json:"browser_download_url"`
			Size int64  `json:"size"`
		} `json:"assets"`
	}
	if err := json.Unmarshal(body, &rels); err != nil {
		return nil, err
	}
	var best *studioRelease
	for _, r := range rels {
		if r.Draft {
			continue
		}
		sums := map[string]string{}
		for _, a := range r.Assets {
			if strings.HasSuffix(a.Name, ".sha256") {
				sums[strings.TrimSuffix(a.Name, ".sha256")] = a.URL
			}
		}
		for _, a := range r.Assets {
			m := studioAsset.FindStringSubmatch(a.Name)
			if m == nil || sums[a.Name] == "" || !newerVersion(m[1], current) {
				continue
			}
			if best != nil && !newerVersion(m[1], best.Version) {
				continue
			}
			notes := r.Body
			if len(notes) > 600 {
				notes = notes[:600]
			}
			best = &studioRelease{Version: m[1], URL: a.URL, SumURL: sums[a.Name], Size: a.Size, Notes: notes}
		}
	}
	return best, nil
}

func githubGet(ctx context.Context, url string) ([]byte, error) {
	req, _ := http.NewRequestWithContext(ctx, "GET", url, nil)
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("User-Agent", "GalIOS-Studio")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return nil, fmt.Errorf("%s answered HTTP %d", req.URL.Host, resp.StatusCode)
	}
	return io.ReadAll(io.LimitReader(resp.Body, 64<<20))
}

// fetchChecked downloads url to dest and checks it against the SHA-256 at sumURL; dest is removed when it does not match.
func fetchChecked(ctx context.Context, url, sumURL, dest string) error {
	sb, err := githubGet(ctx, sumURL)
	if err != nil {
		return fmt.Errorf("the update's SHA-256: %v", err)
	}
	f := strings.Fields(string(sb))
	if len(f) == 0 || len(f[0]) != 64 {
		return fmt.Errorf("the update's SHA-256 file is not a SHA-256")
	}
	want := strings.ToLower(f[0])
	req, _ := http.NewRequestWithContext(ctx, "GET", url, nil)
	req.Header.Set("User-Agent", "GalIOS-Studio")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("%s answered HTTP %d", req.URL.Host, resp.StatusCode)
	}
	out, err := os.Create(dest)
	if err != nil {
		return err
	}
	h := sha256.New()
	_, err = io.Copy(io.MultiWriter(out, h), resp.Body)
	if cerr := out.Close(); err == nil {
		err = cerr
	}
	if err != nil {
		os.Remove(dest)
		return err
	}
	if got := hex.EncodeToString(h.Sum(nil)); got != want {
		os.Remove(dest)
		return fmt.Errorf("the download did not match its SHA-256 (got %s…, want %s…)", got[:12], want[:12])
	}
	return nil
}

// swapExe puts next in place of the running exe, keeping the old one as exe.old until the next start.
func swapExe(exe, next string) error {
	old := exe + ".old"
	os.Remove(old)
	if err := os.Rename(exe, old); err != nil {
		return fmt.Errorf("could not move the running Studio aside (is its folder writable?): %v", err)
	}
	if err := os.Rename(next, exe); err != nil {
		os.Rename(old, exe)
		return fmt.Errorf("could not put the new Studio in place: %v", err)
	}
	return nil
}

// cleanOldExe removes the exe a previous update left (it may still be ending: tried for a minute).
func cleanOldExe() {
	exe, err := os.Executable()
	if err != nil {
		return
	}
	old := exe + ".old"
	for i := 0; i < 30; i++ {
		if _, err := os.Stat(old); os.IsNotExist(err) {
			return
		}
		if os.Remove(old) == nil {
			return
		}
		time.Sleep(2 * time.Second)
	}
}

// updateLoop checks for a newer Studio shortly after the start and every 6 hours, and installs it when idle.
func (a *App) updateLoop() {
	if os.Getenv("STUDIO_NO_UPDATE") != "" || runtime.GOOS != "windows" {
		return
	}
	go cleanOldExe()
	time.Sleep(15 * time.Second)
	for {
		a.checkAndUpdate()
		time.Sleep(6 * time.Hour)
	}
}

func (a *App) checkAndUpdate() {
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Minute)
	defer cancel()
	body, err := githubGet(ctx, "https://api.github.com/repos/"+updateRepo+"/releases?per_page=100")
	if err != nil {
		log.Printf("update check: %v", err)
		return
	}
	rel, err := newestStudio(body, version)
	if err != nil || rel == nil {
		return
	}
	exe, err := os.Executable()
	if err == nil {
		exe, err = filepath.EvalSymlinks(exe)
	}
	if err != nil {
		a.upd.set(func(s *StudioUpdate) { *s = StudioUpdate{rel.Version, "failed", "Studio " + rel.Version + " is out, but this Studio cannot find its own file: " + err.Error()} })
		return
	}
	next := exe + ".new"
	a.upd.set(func(s *StudioUpdate) { *s = StudioUpdate{rel.Version, "downloading", "Downloading Studio " + rel.Version + "…"} })
	if err := fetchChecked(ctx, rel.URL, rel.SumURL, next); err != nil {
		a.upd.set(func(s *StudioUpdate) { *s = StudioUpdate{rel.Version, "failed", "Studio " + rel.Version + " could not be downloaded: " + err.Error() + ". Studio tries again later."} })
		return
	}
	for a.hub.Job().Running {
		a.upd.set(func(s *StudioUpdate) {
			*s = StudioUpdate{rel.Version, "waiting", "Studio " + rel.Version + " is ready: it installs itself when the running task ends."}
		})
		time.Sleep(30 * time.Second)
	}
	a.upd.set(func(s *StudioUpdate) { *s = StudioUpdate{rel.Version, "restarting", "Installing Studio " + rel.Version + ": Studio restarts in a few seconds."} })
	time.Sleep(4 * time.Second) // the page shows the message before the window closes
	if a.hub.Job().Running {    // a task started in those seconds: wait for the next round
		a.upd.set(func(s *StudioUpdate) { *s = StudioUpdate{rel.Version, "waiting", "Studio " + rel.Version + " is ready: it installs itself when the running task ends."} })
		go func() { time.Sleep(time.Minute); a.checkAndUpdate() }()
		return
	}
	if err := swapExe(exe, next); err != nil {
		os.Remove(next)
		a.upd.set(func(s *StudioUpdate) { *s = StudioUpdate{rel.Version, "failed", "Studio " + rel.Version + " could not be installed: " + err.Error()} })
		return
	}
	cmd := exec.Command(exe, "--updated")
	cmd.Dir = filepath.Dir(exe)
	if err := cmd.Start(); err != nil {
		// The new exe is in place; it runs at the next start.
		a.upd.set(func(s *StudioUpdate) { *s = StudioUpdate{rel.Version, "failed", "Studio " + rel.Version + " is installed: close Studio and open it again."} })
		return
	}
	log.Printf("updated to %s: the new Studio is starting", rel.Version)
	os.Exit(0)
}

// listenAfterUpdate waits for the old Studio to free the port (it ends right after starting this one).
func listenAfterUpdate(listen func() error) error {
	var err error
	for i := 0; i < 40; i++ {
		if err = listen(); err == nil {
			return nil
		}
		time.Sleep(250 * time.Millisecond)
	}
	return err
}
