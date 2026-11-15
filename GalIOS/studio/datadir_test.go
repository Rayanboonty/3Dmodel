package main

import (
	"os"
	"path/filepath"
	"testing"
)

// What Studio 1.15 to 1.18 saved under the renamed folder comes back, without overwriting what the real folder has.
func TestAdoptRenamedData(t *testing.T) {
	base := t.TempDir()
	from := filepath.Join(base, "GalaxyIOS Studio")
	to := filepath.Join(base, "GalIOS Studio")
	os.MkdirAll(from, 0o755)
	os.MkdirAll(to, 0o755)
	os.WriteFile(filepath.Join(from, "settings.json"), []byte(`{"ipsw":"new"}`), 0o644)
	os.WriteFile(filepath.Join(from, "hero-picture"), []byte("png"), 0o644)
	os.WriteFile(filepath.Join(to, "settings.json"), []byte(`{"ipsw":"kept"}`), 0o644)
	adoptRenamedData(from, to)
	if b, _ := os.ReadFile(filepath.Join(to, "settings.json")); string(b) != `{"ipsw":"kept"}` {
		t.Fatalf("settings overwritten: %s", b)
	}
	if b, _ := os.ReadFile(filepath.Join(to, "hero-picture")); string(b) != "png" {
		t.Fatalf("picture not adopted: %q", b)
	}
	// the renamed folder still holds the older settings.json (not overwritten), so it stays
	if _, err := os.Stat(filepath.Join(from, "settings.json")); err != nil {
		t.Fatal("the renamed folder's settings.json was lost")
	}
}
