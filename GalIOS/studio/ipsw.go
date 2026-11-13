package main

import (
	"archive/zip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strings"
	"sync"
)

// The .ipsw OneiOS pins: iPhone 17 Pro Max (iPhone18,2), iOS 27.0.1, build 24A446.
const (
	ipswDevice = "iPhone18,2"
	ipswBuild  = "24A446"
	ipswName   = "iPhone18,2_27.0.1_24A446_Restore.ipsw"
)

// findIPSWs looks for the right .ipsw in the usual places: Downloads, Desktop, Documents (OneDrive's too), the
// user's folder, and the top and Downloads folder of every other drive.
func findIPSWs() []string {
	home, _ := os.UserHomeDir()
	var dirs []string
	for _, d := range []string{"Downloads", "Desktop", "Documents", "OneDrive/Downloads", "OneDrive/Desktop",
		"OneDrive/Documents", ""} {
		dirs = append(dirs, filepath.Join(home, d))
	}
	if runtime.GOOS == "windows" {
		for c := 'C'; c <= 'Z'; c++ {
			root := string(c) + `:\`
			if _, err := os.Stat(root); err != nil {
				continue
			}
			dirs = append(dirs, root, filepath.Join(root, "Downloads"), filepath.Join(root, "iOS"), filepath.Join(root, "IPSW"))
		}
	}
	if extra := os.Getenv("STUDIO_IPSW_DIRS"); extra != "" { // tests
		dirs = append(filepath.SplitList(extra), dirs...)
	}
	seen := map[string]bool{}
	var found []string
	for _, d := range dirs {
		ents, err := os.ReadDir(d)
		if err != nil {
			continue
		}
		for _, e := range ents {
			n := strings.ToLower(e.Name())
			// Any .ipsw: a renamed file is recognised by what is inside it (checkIPSW).
			if e.IsDir() || !strings.HasSuffix(n, ".ipsw") {
				continue
			}
			p := filepath.Join(d, e.Name())
			if !seen[strings.ToLower(p)] {
				seen[strings.ToLower(p)] = true
				found = append(found, p)
			}
		}
	}
	// The pinned name first, then the rest.
	sort.SliceStable(found, func(i, j int) bool {
		pi := strings.Contains(strings.ToLower(found[i]), "24a446")
		pj := strings.Contains(strings.ToLower(found[j]), "24a446")
		return pi && !pj
	})
	return found
}

// checkIPSW opens the .ipsw (a zip) and reads Apple's own description of it: it must be iPhone18,2, build 24A446.
// It refuses only what is surely wrong (a cut-short download, another build or phone); a file it cannot read
// fully but whose name and size are right is accepted, since OneiOS's own fetch checks the firmware again.
func checkIPSW(path string) error {
	if fi, err := os.Stat(path); err == nil {
		key := fmt.Sprintf("%s|%d|%d", strings.ToLower(path), fi.Size(), fi.ModTime().UnixNano())
		checkMu.Lock()
		res, ok := checkCache[key]
		checkMu.Unlock()
		if ok {
			return res
		}
		res = checkIPSWNow(path)
		checkMu.Lock()
		checkCache[key] = res
		checkMu.Unlock()
		return res
	}
	return checkIPSWNow(path)
}

var (
	checkMu    sync.Mutex
	checkCache = map[string]error{}
)

func checkIPSWNow(path string) error {
	fi, err := os.Stat(path)
	if err != nil {
		return fmt.Errorf("%s is not there any more", path)
	}
	if fi.IsDir() {
		return fmt.Errorf("%s is a folder, not an .ipsw file", path)
	}
	base := filepath.Base(path)
	if fi.Size() < 5<<30 {
		return fmt.Errorf("%s is only %s: the download did not finish (the right file is 10 GB or more)", base, human(fi.Size()))
	}
	nameOK := strings.Contains(strings.ToLower(base), "24a446") && strings.Contains(strings.ToLower(base), "iphone18,2")
	zr, err := zip.OpenReader(path)
	if err != nil {
		if nameOK {
			return nil
		}
		return fmt.Errorf("%s cannot be opened as an iPhone restore file (the download may be cut short)", base)
	}
	defer zr.Close()
	read := func(name string, limit int64) (string, bool) {
		for _, f := range zr.File {
			if f.Name == name {
				rc, err := f.Open()
				if err != nil {
					return "", false
				}
				b, _ := io.ReadAll(io.LimitReader(rc, limit))
				rc.Close()
				return string(b), true
			}
		}
		return "", false
	}
	// Restore.plist is small and names both; BuildManifest.plist names the phone only at its very end.
	text, ok := read("Restore.plist", 4<<20)
	if !ok || !strings.Contains(text, ipswDevice) {
		if t, ok2 := read("BuildManifest.plist", 256<<20); ok2 {
			text, ok = text+t, true
		}
	}
	if !ok {
		if nameOK {
			return nil
		}
		return fmt.Errorf("%s has no Restore.plist or BuildManifest.plist: it is not an iPhone restore file", base)
	}
	if !strings.Contains(text, ipswBuild) {
		return fmt.Errorf("%s is not iOS 27.0.1 build %s: download %s", base, ipswBuild, ipswName)
	}
	if !strings.Contains(text, ipswDevice) {
		return fmt.Errorf("%s is not for the iPhone 17 Pro Max (%s): download %s", base, ipswDevice, ipswName)
	}
	return nil
}

// bestIPSW is the .ipsw to build from: the one in Settings if it is still there and checks out, else the first one
// found that does. A file that was moved or renamed is simply forgotten.
func bestIPSW(s Settings) (string, []string) {
	var notes []string
	if s.IPSW != "" && fileExists(s.IPSW) {
		if err := checkIPSW(s.IPSW); err == nil {
			return s.IPSW, nil
		} else {
			notes = append(notes, err.Error())
		}
	}
	for _, p := range findIPSWs() {
		if strings.EqualFold(p, s.IPSW) {
			continue
		}
		if err := checkIPSW(p); err == nil {
			return p, notes
		} else {
			notes = append(notes, err.Error())
		}
	}
	return "", notes
}
