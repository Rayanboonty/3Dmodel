package main

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

const releasesJSON = `[
 {"tag_name":"vortex-v0.12.0","draft":false,"body":"other project","assets":[
   {"name":"Vortex.exe","browser_download_url":"https://x/vortex.exe","size":1}]},
 {"tag_name":"galios-app-1.20","draft":true,"body":"draft","assets":[
   {"name":"GalIOS-Studio-2.0.exe","browser_download_url":"https://x/2.0.exe","size":1},
   {"name":"GalIOS-Studio-2.0.exe.sha256","browser_download_url":"https://x/2.0.exe.sha256","size":1}]},
 {"tag_name":"galios-app-1.18","draft":false,"body":"no sum","assets":[
   {"name":"GalIOS-Studio-1.11.exe","browser_download_url":"https://x/1.11.exe","size":1}]},
 {"tag_name":"galios-app-1.17","draft":false,"body":"notes","assets":[
   {"name":"GalIOS-Studio-1.10.exe","browser_download_url":"https://x/1.10.exe","size":5},
   {"name":"GalIOS-Studio-1.10.exe.sha256","browser_download_url":"https://x/1.10.exe.sha256","size":1},
   {"name":"GalIOS-17.apk","browser_download_url":"https://x/17.apk","size":1}]},
 {"tag_name":"galios-app-1.16","draft":false,"body":"old","assets":[
   {"name":"GalIOS-Studio-1.9.exe","browser_download_url":"https://x/1.9.exe","size":1},
   {"name":"GalIOS-Studio-1.9.exe.sha256","browser_download_url":"https://x/1.9.exe.sha256","size":1}]}
]`

func TestNewestStudioSkipsDraftsAndAssetsWithoutASum(t *testing.T) {
	r, err := newestStudio([]byte(releasesJSON), "1.8")
	if err != nil || r == nil {
		t.Fatalf("got %v, %v", r, err)
	}
	// 2.0 is a draft and 1.11 has no .sha256: 1.10 is the newest usable one (1.10 > 1.9 as numbers, not text).
	if r.Version != "1.10" || r.URL != "https://x/1.10.exe" || r.SumURL != "https://x/1.10.exe.sha256" || r.Size != 5 {
		t.Fatalf("picked %+v", r)
	}
	if r, _ := newestStudio([]byte(releasesJSON), "1.10"); r != nil {
		t.Fatalf("nothing is newer than 1.10, got %+v", r)
	}
	if r, _ := newestStudio([]byte(`[]`), "1.8"); r != nil {
		t.Fatalf("no releases, got %+v", r)
	}
	if _, err := newestStudio([]byte(`{"message":"rate limited"}`), "1.8"); err == nil {
		t.Fatalf("an object is not a release list")
	}
}

func TestFetchCheckedKeepsOnlyAMatchingDownload(t *testing.T) {
	body := []byte("the new studio")
	sum := sha256.Sum256(body)
	good := hex.EncodeToString(sum[:])
	mux := http.NewServeMux()
	mux.HandleFunc("/exe", func(w http.ResponseWriter, r *http.Request) { w.Write(body) })
	mux.HandleFunc("/good.sha256", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte(good + "  GalIOS-Studio-1.9.exe\n")) })
	mux.HandleFunc("/bad.sha256", func(w http.ResponseWriter, r *http.Request) {
		w.Write([]byte("0000000000000000000000000000000000000000000000000000000000000000  x\n"))
	})
	mux.HandleFunc("/junk.sha256", func(w http.ResponseWriter, r *http.Request) { w.Write([]byte("<html>not found</html>")) })
	srv := httptest.NewServer(mux)
	defer srv.Close()
	dir := t.TempDir()
	dest := filepath.Join(dir, "next.exe")

	if err := fetchChecked(context.Background(), srv.URL+"/exe", srv.URL+"/good.sha256", dest); err != nil {
		t.Fatalf("good: %v", err)
	}
	if b, _ := os.ReadFile(dest); string(b) != string(body) {
		t.Fatalf("good: wrote %q", b)
	}
	os.Remove(dest)
	if err := fetchChecked(context.Background(), srv.URL+"/exe", srv.URL+"/bad.sha256", dest); err == nil {
		t.Fatalf("bad sum accepted")
	}
	if _, err := os.Stat(dest); !os.IsNotExist(err) {
		t.Fatalf("bad sum: the download was kept")
	}
	if err := fetchChecked(context.Background(), srv.URL+"/exe", srv.URL+"/junk.sha256", dest); err == nil {
		t.Fatalf("a sum file that is not a sum was accepted")
	}
	if err := fetchChecked(context.Background(), srv.URL+"/exe", srv.URL+"/missing.sha256", dest); err == nil {
		t.Fatalf("a missing sum was accepted")
	}
}

func TestSwapExeKeepsTheOldOneAside(t *testing.T) {
	dir := t.TempDir()
	exe, next := filepath.Join(dir, "GalIOS-Studio.exe"), filepath.Join(dir, "GalIOS-Studio.exe.new")
	os.WriteFile(exe, []byte("old"), 0o755)
	os.WriteFile(next, []byte("new"), 0o755)
	if err := swapExe(exe, next); err != nil {
		t.Fatal(err)
	}
	if b, _ := os.ReadFile(exe); string(b) != "new" {
		t.Fatalf("exe holds %q", b)
	}
	if b, _ := os.ReadFile(exe + ".old"); string(b) != "old" {
		t.Fatalf("exe.old holds %q", b)
	}
	// A missing new file leaves the running exe where it was.
	if err := swapExe(exe, filepath.Join(dir, "absent")); err == nil {
		t.Fatalf("swap with a missing file succeeded")
	}
	if b, _ := os.ReadFile(exe); string(b) != "new" {
		t.Fatalf("after a failed swap the exe holds %q", b)
	}
}
