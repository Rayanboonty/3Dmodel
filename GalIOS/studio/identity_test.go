package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
)

// The iPhone screen features script: on copies a device-tree-sized file into the stage, refuses others; off removes it.
func TestIdentityScript(t *testing.T) {
	on := identityScript(true)
	for _, want := range []string{"/sdcard/Download/" + identityName, identityInStage, "16384", "@@OK on"} {
		if !strings.Contains(on, want) {
			t.Fatalf("on script lacks %q:\n%s", want, on)
		}
	}
	if off := identityScript(false); !strings.Contains(off, "rm -f '"+identityInStage+"'") || !strings.Contains(off, "@@OK off") {
		t.Fatalf("off script: %s", off)
	}
	if runtime.GOOS == "windows" {
		return
	}
	sh, err := exec.LookPath("sh")
	if err != nil {
		return
	}
	// Run it for real against a temporary phone layout.
	dir := t.TempDir()
	stage := filepath.Join(dir, "stage")
	dl := filepath.Join(dir, "dl")
	os.MkdirAll(stage, 0o755)
	os.MkdirAll(dl, 0o755)
	script := strings.NewReplacer(stageDir, stage, "/sdcard/Download", dl).Replace(on)
	run := func() string {
		out, _ := exec.Command(sh, "-c", script).CombinedOutput()
		return string(out)
	}
	os.WriteFile(filepath.Join(dl, identityName), make([]byte, 100), 0o644)
	if out := run(); !strings.Contains(out, "@@FAILED that file is not an iPhone device tree") {
		t.Fatalf("a 100-byte file was taken: %s", out)
	}
	os.WriteFile(filepath.Join(dl, identityName), make([]byte, 64*1024), 0o644)
	if out := run(); !strings.Contains(out, "@@OK on") {
		t.Fatalf("not turned on: %s", out)
	}
	if fi, err := os.Stat(filepath.Join(stage, "galios-identity.adt")); err != nil || fi.Size() != 64*1024 {
		t.Fatalf("not in the stage: %v", err)
	}
}
