package main

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"unicode/utf16"

	"github.com/klauspost/compress/zstd"
)

// A switch script shaped like OneOS's around its phone check (written for this test, not OneOS's text).
const fakeSwitch = `#!/system/bin/sh
P_MODEL=$(getprop ro.product.model)
P_DEVICE=$(getprop ro.product.device)
P_SOC=$(getprop ro.soc.model)
case "$P_MODEL" in SM-S928*) ;; *) refuse "S24 Ultra only; this is ${P_MODEL:-unknown}" ;; esac
[ "$P_DEVICE" = e3q ] && [ "$P_SOC" = SM8650 ] ||
    refuse "S24 Ultra (e3q, SM8650) only; this is ${P_DEVICE:-?}, ${P_SOC:-?}"
# The stage next (SM8650 named in a comment is fine).
stage_checks
`

func TestAdaptSwitch(t *testing.T) {
	out, adapted, err := adaptSwitch([]byte(fakeSwitch))
	if err != nil || !adapted {
		t.Fatalf("adapt: %v %v", adapted, err)
	}
	s := string(out)
	for _, want := range []string{`SM-S938*) [ "$P_DEVICE" = pa3q ] && [ "$P_SOC" = SM8750 ] ||`, `SM-S928*) [ "$P_DEVICE" = e3q ] && [ "$P_SOC" = SM8650 ] ||`, "stage_checks"} {
		if !strings.Contains(s, want) {
			t.Errorf("missing %q", want)
		}
	}
	if strings.Contains(s, "SM-S928*) ;; *)") {
		t.Error("the old S24-only line is still there")
	}
	if _, again, _ := adaptSwitch(out); again {
		t.Error("adapting twice changed it again")
	}
	if _, _, err := adaptSwitch([]byte("echo hi\n")); err == nil {
		t.Error("an unknown script was not refused")
	}
	twice := fakeSwitch + `[ "$(getprop ro.soc.model)" = SM8650 ] || refuse again` + "\n"
	if _, _, err := adaptSwitch([]byte(twice)); err == nil || !strings.Contains(err.Error(), "line") {
		t.Errorf("a second S24 check was not refused: %v", err)
	}
	// OneOS's real switch script, when given (never part of Studio).
	if p := os.Getenv("STUDIO_REAL_SWITCH"); p != "" {
		b, _ := os.ReadFile(p)
		if _, _, err := adaptSwitch(b); err != nil {
			t.Errorf("OneOS's real switch script: %v", err)
		}
	}
}

func TestParseReplyAndPaths(t *testing.T) {
	r := parseReply([]string{"@@RESULT ready 0 no busybox", "@@REFUSED an iOS session is running: return to One UI first"}, 2)
	if r.OK || r.Message != "an iOS session is running: return to One UI first" || len(r.Results) != 1 {
		t.Errorf("%+v", r)
	}
	if r := parseReply([]string{"@@OK status"}, 0); !r.OK {
		t.Error("OK not read")
	}
	for in, want := range map[string]string{
		`C:\Users\Rayane\Downloads`:            "/mnt/c/Users/Rayane/Downloads",
		`D:/iOS/iPhone18,2_27.0.1_24A446.ipsw`: "/mnt/d/iOS/iPhone18,2_27.0.1_24A446.ipsw",
		"/home/me/x":                           "/home/me/x",
	} {
		if got := toWSLPath(in); got != want {
			t.Errorf("toWSLPath(%q) = %q", in, got)
		}
	}
	// wsl.exe -l -q answers in UTF-16LE with a BOM.
	u := utf16.Encode([]rune("\ufeffUbuntu-24.04\r\nDebian\r\n"))
	var b bytes.Buffer
	for _, c := range u {
		b.WriteByte(byte(c))
		b.WriteByte(byte(c >> 8))
	}
	if got := decodeMaybeUTF16(b.Bytes()); !strings.HasPrefix(got, "Ubuntu-24.04\r\nDebian") {
		t.Errorf("utf16: %q", got)
	}
	if ok, label := phoneKind("SM-S938B", "pa3q", "SM8750"); !ok || label != "Galaxy S25 Ultra" {
		t.Error("S25 Ultra not known")
	}
	if ok, _ := phoneKind("SM-S938B", "pa3s", "s5e9955"); ok {
		t.Error("an Exynos phone passed")
	}
	if got := cleanLine("\x1b[1;36m==> Building\x1b[0m"); got != "==> Building" {
		t.Errorf("clean: %q", got)
	}
}

// ---- archives shaped like OneiOS's (manifest first), for the pipeline test and the UI test -----------------------

func writeArchive(t testing.TB, path, kind string, files map[string][]byte, facts map[string]any, folder string) {
	man := map[string]any{}
	for k, v := range facts {
		man[k] = v
	}
	var list []map[string]any
	for p, b := range files {
		e := map[string]any{"path": p, "size": len(b), "sha256": sha256hex(b)}
		if strings.HasPrefix(p, "bin/") {
			e["mode"] = "0755"
		}
		list = append(list, e)
	}
	man["files"] = list
	raw, _ := json.Marshal(man)
	f, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	zw, _ := zstd.NewWriter(f)
	tw := tar.NewWriter(zw)
	put := func(name string, b []byte) {
		tw.WriteHeader(&tar.Header{Name: folder + name, Mode: 0o644, Size: int64(len(b)), Typeflag: tar.TypeReg})
		tw.Write(b)
	}
	put(map[string]string{"runtime": "runtime.json", "image": "manifest.json"}[kind], raw)
	for p, b := range files {
		put(p, b)
	}
	tw.Close()
	zw.Close()
	f.Close()
}

// armELF is a stand-in xnuhost with an arm64 ELF header (what OneOS's boot check reads), and nothing else.
func armELF() []byte {
	b := make([]byte, 64)
	copy(b, []byte{0x7f, 'E', 'L', 'F', 2, 1, 1})
	b[16], b[18] = 2, 0xb7
	return b
}

func makeFixtures(t testing.TB, dir string) (runtimeArc, imageArc string) {
	os.MkdirAll(dir, 0o755)
	runtimeArc = filepath.Join(dir, "oneios-runtime-0.1.8-test-arm64.tar.zst")
	writeArchive(t, runtimeArc, "runtime", map[string][]byte{"bin/xnuhost": armELF(), "lib/readme.txt": []byte("x\n")},
		map[string]any{"format": 1, "version": "0.1.8-test", "session_abi": 1, "xnuhost": "bin/xnuhost", "image_formats": []int{1}}, "")
	img := make([]byte, 300_000)
	for i := range img {
		img[i] = byte(i * 7)
	}
	imageArc = filepath.Join(dir, imageName)
	writeArchive(t, imageArc, "image", map[string][]byte{"os.img": img, "overlay/userland/xnuhost.conf": []byte("test=1\n")},
		map[string]any{"format": 1, "name": "oneios-image", "os": "iOS", "version": "27.0.1", "build": "24A446", "device": "iPhone18,2"}, "OneiOS_iOS27/")
	return
}

// TestFixtures writes the two archives for the UI test (STUDIO_FIXTURES=<dir>).
func TestFixtures(t *testing.T) {
	dir := os.Getenv("STUDIO_FIXTURES")
	if dir == "" {
		t.Skip("STUDIO_FIXTURES not set")
	}
	makeFixtures(t, dir)
}

// TestBootFromPC runs the whole PC side of a boot against a stand-in phone: an adb that runs the phone's commands on
// this machine (su, getprop, getevent stand-ins report an S25 Ultra), OneOS's real root script and switch script in
// a OneOS APK, and the stage at /data/local/tmp/oneios. Needs STUDIO_FAKE_ADB, STUDIO_ONEOS_APK_DIR.
func TestBootFromPC(t *testing.T) {
	adb, apkDir := os.Getenv("STUDIO_FAKE_ADB"), os.Getenv("STUDIO_ONEOS_APK_DIR")
	root, sw, ret := os.Getenv("STUDIO_REAL_ROOT"), os.Getenv("STUDIO_REAL_SWITCH"), os.Getenv("STUDIO_REAL_RETURN")
	if adb == "" || apkDir == "" || root == "" || sw == "" || ret == "" {
		t.Skip("needs the stand-in phone")
	}
	// The OneOS APK as Studio reads it: its assets.
	os.MkdirAll(apkDir, 0o755)
	zf, _ := os.Create(filepath.Join(apkDir, "base.apk"))
	zw := zip.NewWriter(zf)
	add := func(name string, b []byte) { w, _ := zw.Create("assets/" + name); w.Write(b) }
	for name, path := range map[string]string{"root/oneios_root.sh": root, "oneios/oneios_switch.sh": sw, "oneios/oneios_return.sh": ret} {
		b, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		add(name, b)
	}
	for _, a := range []string{"oneios/boot_status.sh", "oneios/OneOSBootStatus.dex", "oneios/oneos_boot_icon.png"} {
		add(a, []byte("boot screen stand-in: "+a+"\n"))
	}
	zw.Close()
	zf.Close()

	t.Setenv("STUDIO_DATA", t.TempDir())
	s := Settings{ADBPath: adb, RootCmd: "su -c"}
	ctx := context.Background()
	h := NewHub()
	j := &JobCtx{h}
	serial, err := pickSerial(ctx, s)
	if err != nil {
		t.Fatal(err)
	}
	ph := checkPhone(ctx, s)
	if !ph.Supported || ph.Label != "Galaxy S25 Ultra" || !ph.Root || ph.OneOS == "" {
		t.Fatalf("phone: %+v", ph)
	}
	// A clean stage, then the engine as Studio installs it.
	e, err := pullEngine(ctx, j, s, serial)
	if err != nil {
		t.Fatal(err)
	}
	if msg, err := installEngine(ctx, j, s, serial, e); err != nil || !strings.Contains(msg, "S25") {
		t.Fatalf("engine: %q %v", msg, err)
	}
	verb(ctx, s, serial, "", "remove")
	if _, err := installEngine(ctx, j, s, serial, e); err != nil {
		t.Fatal(err)
	}
	rt, im := makeFixtures(t, t.TempDir())
	if msg, err := importArchive(ctx, j, s, serial, "runtime", rt); err != nil {
		t.Fatalf("runtime: %v", err)
	} else {
		t.Log(msg)
	}
	if msg, err := importArchive(ctx, j, s, serial, "image", im); err != nil {
		t.Fatalf("image: %v", err)
	} else {
		t.Log(msg)
	}
	st := stageStatus(ctx, s, serial)
	if st.Runtime != "0.1.8-test" || st.Image != "27.0.1 (24A446)" {
		t.Fatalf("stage: %+v", st)
	}
	// Past the phone check (adapted for the S25 Ultra) and the arm64 check: the preflight stops at the stand-in's
	// busybox, which lacks applets the session needs.
	if st.Ready || !strings.Contains(st.Reason, "busybox lacks the applets") {
		t.Fatalf("boot check: %+v", st)
	}
	// A damaged image is refused by root and the staged one stays.
	bad := filepath.Join(t.TempDir(), "bad.tar.zst")
	writeArchive(t, bad, "image", map[string][]byte{"os.img": []byte("not what the manifest says")},
		map[string]any{"format": 1, "version": "27.0.2", "build": "24A999"}, "")
	// make the manifest lie: rewrite with a wrong sha
	if err := corruptManifest(bad); err != nil {
		t.Fatal(err)
	}
	if _, err := importArchive(ctx, j, s, serial, "image", bad); err == nil {
		t.Fatal("a damaged image was accepted")
	} else {
		t.Log("refused as expected:", err)
	}
	if st := stageStatus(ctx, s, serial); st.Image != "27.0.1 (24A446)" {
		t.Fatalf("the staged image changed: %+v", st)
	}
	if msg, err := pullLogs(ctx, j, s, serial); err != nil {
		t.Fatalf("logs: %v", err)
	} else {
		t.Log(msg)
	}
	fmt.Println(strings.Join(h.Lines(40), "\n"))
}

// corruptManifest rewrites an archive so its manifest names a wrong SHA-256 for every file.
func corruptManifest(path string) error {
	f, err := os.Open(path)
	if err != nil {
		return err
	}
	zr, _ := zstd.NewReader(f)
	tr := tar.NewReader(zr)
	type ent struct {
		h *tar.Header
		b []byte
	}
	var es []ent
	for {
		h, err := tr.Next()
		if err != nil {
			break
		}
		var buf bytes.Buffer
		buf.ReadFrom(tr)
		es = append(es, ent{h, buf.Bytes()})
	}
	zr.Close()
	f.Close()
	var m map[string]any
	json.Unmarshal(es[0].b, &m)
	for _, x := range m["files"].([]any) {
		x.(map[string]any)["sha256"] = strings.Repeat("0", 64)
	}
	es[0].b, _ = json.Marshal(m)
	es[0].h.Size = int64(len(es[0].b))
	out, _ := os.Create(path)
	zw, _ := zstd.NewWriter(out)
	tw := tar.NewWriter(zw)
	for _, e := range es {
		tw.WriteHeader(e.h)
		tw.Write(e.b)
	}
	tw.Close()
	zw.Close()
	return out.Close()
}

// TestRootUnreachable: su refused over USB and GalIOS without root: a clear message, nothing else done.
func TestRootUnreachable(t *testing.T) {
	adb := os.Getenv("STUDIO_FAKE_ADB")
	if adb == "" {
		t.Skip("needs the stand-in phone")
	}
	t.Setenv("FAKE_NO_SU", "1")
	t.Setenv("FAKE_GALIOS_CODE", "10")
	t.Setenv("FAKE_BRIDGE_NOROOT", "1")
	t.Setenv("STUDIO_DATA", t.TempDir())
	forgetRoot("R5CY71TEST")
	ph := checkPhone(context.Background(), Settings{ADBPath: adb, RootCmd: "auto"})
	if !ph.Connected || ph.Root || !strings.Contains(ph.RootMessage, "allow GalIOS") {
		t.Fatalf("%+v", ph)
	}
	t.Log(ph.RootMessage)
}
