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
			if e.IsDir() || !strings.HasSuffix(n, ".ipsw") || !strings.Contains(n, "24a446") || !strings.Contains(n, "iphone18,2") {
				continue
			}
			p := filepath.Join(d, e.Name())
			if !seen[strings.ToLower(p)] {
				seen[strings.ToLower(p)] = true
				found = append(found, p)
			}
		}
	}
	sort.Strings(found)
	return found
}

// checkIPSW opens the .ipsw (a zip) and reads Apple's BuildManifest.plist: it must be iPhone18,2, build 24A446.
func checkIPSW(path string) error {
	fi, err := os.Stat(path)
	if err != nil {
		return fmt.Errorf("%s is not there any more", path)
	}
	if fi.IsDir() {
		return fmt.Errorf("%s is a folder, not an .ipsw file", path)
	}
	if fi.Size() < 1<<30 {
		return fmt.Errorf("%s is only %s: the download did not finish (the right file is about 10.6 GB)", filepath.Base(path), human(fi.Size()))
	}
	zr, err := zip.OpenReader(path)
	if err != nil {
		return fmt.Errorf("%s is not a complete .ipsw (it cannot be opened as one: the download may be cut short)", filepath.Base(path))
	}
	defer zr.Close()
	for _, f := range zr.File {
		if f.Name != "BuildManifest.plist" {
			continue
		}
		rc, err := f.Open()
		if err != nil {
			return err
		}
		b, _ := io.ReadAll(io.LimitReader(rc, 8<<20))
		rc.Close()
		t := string(b)
		if !strings.Contains(t, "<string>"+ipswBuild+"</string>") {
			return fmt.Errorf("%s is not iOS 27.0.1 build %s: download %s", filepath.Base(path), ipswBuild, ipswName)
		}
		if !strings.Contains(t, "<string>"+ipswDevice+"</string>") {
			return fmt.Errorf("%s is not for the iPhone 17 Pro Max (%s): download %s", filepath.Base(path), ipswDevice, ipswName)
		}
		return nil
	}
	return fmt.Errorf("%s has no BuildManifest.plist: it is not an iPhone restore file", filepath.Base(path))
}

// bestIPSW is the .ipsw to build from: the one in Settings if it checks out, else the first one found that does.
func bestIPSW(s Settings) (string, []string) {
	var notes []string
	if s.IPSW != "" {
		if err := checkIPSW(s.IPSW); err == nil {
			return s.IPSW, nil
		} else {
			notes = append(notes, err.Error())
		}
	}
	for _, p := range findIPSWs() {
		if err := checkIPSW(p); err == nil {
			return p, notes
		} else {
			notes = append(notes, err.Error())
		}
	}
	return "", notes
}
