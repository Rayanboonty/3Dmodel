package main

import (
	"archive/zip"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
)

// The .ipsw OneiOS pins: iPhone 17 Pro Max (iPhone18,2), iOS 27.0.1, build 24A446.
const (
	ipswDevice = "iPhone18,2"
	ipswBuild  = "24A446"
	ipswName   = "iPhone18,2_27.0.1_24A446_Restore.ipsw"
)

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

// bestIPSW is the .ipsw you chose, when it is still there. Studio does not look for one by itself any more: you
// choose the file (or, with none chosen, the build reads what it needs from Apple).
func bestIPSW(s Settings) (string, []string) {
	if s.IPSW == "" {
		return "", nil
	}
	if !fileExists(s.IPSW) {
		return "", []string{s.IPSW + " is not there any more: choose the file again"}
	}
	return s.IPSW, nil
}
