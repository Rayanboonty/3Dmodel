package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
)

// Settings are kept in the user's config folder (Windows: %AppData%\GalIOS Studio\settings.json).
type Settings struct {
	Distro      string `json:"distro"`      // the WSL Ubuntu that builds
	OutDir      string `json:"outDir"`      // where the finished image is copied (a Windows folder)
	IPSW        string `json:"ipsw"`        // the .ipsw to build from; empty = download from Apple
	GitHubToken string `json:"githubToken"` // optional, for GitHub's rate limit during the kernel build
	RootCmd     string `json:"rootCmd"`     // how a root shell starts on the phone, e.g. "su -c"
	ADBPath     string `json:"adbPath"`     // empty = the one Studio downloads, else adb on PATH
	Serial      string `json:"serial"`      // the phone, when several are connected
}

type Store struct {
	mu   sync.Mutex
	path string
	s    Settings
}

func dataDir() string {
	if d := os.Getenv("STUDIO_DATA"); d != "" {
		return d
	}
	base, err := os.UserConfigDir()
	if err != nil {
		base = os.TempDir()
	}
	return filepath.Join(base, "GalIOS Studio")
}

func defaultOutDir() string {
	home, _ := os.UserHomeDir()
	return filepath.Join(home, "Downloads")
}

func LoadStore() *Store {
	st := &Store{path: filepath.Join(dataDir(), "settings.json")}
	st.s = Settings{Distro: "Ubuntu-24.04", OutDir: defaultOutDir(), RootCmd: "auto"}
	if b, err := os.ReadFile(st.path); err == nil {
		_ = json.Unmarshal(b, &st.s)
	}
	st.s = withDefaults(st.s)
	return st
}

// withDefaults fills the fields Studio cannot work without, so a blank box in Settings means "the default".
func withDefaults(s Settings) Settings {
	if s.Distro == "" {
		s.Distro = "Ubuntu-24.04"
	}
	if s.RootCmd == "" {
		s.RootCmd = "auto"
	}
	if s.OutDir == "" {
		s.OutDir = defaultOutDir()
	}
	if s.IPSW != "" && !fileExists(s.IPSW) {
		s.IPSW = "" // moved, renamed or deleted: forgotten, so it is never shown or used again
	}
	return s
}

func (st *Store) Get() Settings {
	st.mu.Lock()
	defer st.mu.Unlock()
	return st.s
}

func (st *Store) Set(s Settings) error {
	st.mu.Lock()
	defer st.mu.Unlock()
	s = withDefaults(s)
	st.s = s
	if err := os.MkdirAll(filepath.Dir(st.path), 0o755); err != nil {
		return err
	}
	b, _ := json.MarshalIndent(s, "", "  ")
	return os.WriteFile(st.path, b, 0o600)
}
