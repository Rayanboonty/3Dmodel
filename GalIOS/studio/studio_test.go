package main

import (
	"archive/tar"
	"archive/zip"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"
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

// fakeIPSW writes a sparse >5 GB file ending in a zip that holds manifest (as BuildManifest.plist).
func fakeIPSW(t *testing.T, path string, manifest string, restore string) {
	f, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	f.Truncate(5<<30 + 1<<20)
	f.Seek(0, 2)
	zw := zip.NewWriter(f)
	zw.SetOffset(5<<30 + 1<<20)
	w, _ := zw.Create("BuildManifest.plist")
	w.Write([]byte(manifest))
	if restore != "" {
		w, _ = zw.Create("Restore.plist")
		w.Write([]byte(restore))
	}
	zw.Close()
	f.Close()
}

func TestCheckIPSW(t *testing.T) {
	dir := t.TempDir()
	big := "<plist><dict><key>BuildIdentities</key><array>" + strings.Repeat("<dict><key>BuildNumber</key><string>24A446</string><key>Pad</key><string>"+strings.Repeat("x", 1000)+"</string></dict>", 20000) +
		"</array><key>ProductBuildVersion</key><string>24A446</string><key>SupportedProductTypes</key><array><string>iPhone18,2</string></array></dict></plist>"
	if len(big) < 16<<20 {
		t.Fatalf("manifest only %d bytes", len(big))
	}
	renamed := filepath.Join(dir, "samonios.ipsw") // a renamed file is recognised by its contents
	fakeIPSW(t, renamed, big, "")
	if err := checkIPSW(renamed); err != nil {
		t.Fatalf("the right firmware, renamed, with a big manifest: %v", err)
	}
	other := filepath.Join(dir, "iPhone17,1_26.0_23A341_Restore.ipsw")
	fakeIPSW(t, other, "<string>23A341</string><string>iPhone17,1</string>", "")
	if err := checkIPSW(other); err == nil || !strings.Contains(err.Error(), "24A446") {
		t.Fatalf("another firmware was accepted: %v", err)
	}
	small := filepath.Join(dir, "iPhone18,2_27.0.1_24A446_Restore.ipsw")
	os.WriteFile(small, []byte("partial"), 0o644)
	if err := checkIPSW(small); err == nil || !strings.Contains(err.Error(), "did not finish") {
		t.Fatalf("a cut-short file was accepted: %v", err)
	}
	// Only the file you chose is used; nothing is looked for by itself.
	if p, _ := bestIPSW(Settings{IPSW: renamed}); p != renamed {
		t.Fatalf("the chosen file was not used: %q", p)
	}
	if p, _ := bestIPSW(Settings{}); p != "" {
		t.Fatalf("a file was picked by itself: %q", p)
	}
	// A chosen file that was moved or renamed is forgotten as soon as Studio loads its settings.
	gone := filepath.Join(dir, "gone.ipsw")
	if p, notes := bestIPSW(Settings{IPSW: gone}); p != "" || len(notes) != 1 {
		t.Fatalf("a missing file: %q %v", p, notes)
	}
	t.Setenv("STUDIO_DATA", t.TempDir())
	st := LoadStore()
	st.Set(Settings{IPSW: gone})
	if got := LoadStore().Get().IPSW; got != "" {
		t.Fatalf("the missing file is still in Settings: %q", got)
	}
}

// TestRootHelper: root works over USB (su) only; Studio lends it to GalIOS through galios-rootd.sh, which runs what
// GalIOS queues in its folder.
func TestRootHelper(t *testing.T) {
	adb := os.Getenv("STUDIO_FAKE_ADB")
	if adb == "" || os.Getenv("FAKE_NO_SU") != "" {
		t.Skip("needs the stand-in phone with su over USB")
	}
	t.Setenv("FAKE_GALIOS_CODE", "11")
	t.Setenv("STUDIO_DATA", t.TempDir())
	os.RemoveAll("/storage/emulated/0/Android/data/dev.galios")
	os.Remove(bridgeDir + "/rootd.pid")
	s := Settings{ADBPath: adb, RootCmd: "auto"}
	forgetRoot("R5CY71TEST")
	r, err := getRoot(context.Background(), s, "R5CY71TEST")
	if err != nil || r.Via != "su" {
		t.Fatalf("%+v %v", r, err)
	}
	alive := galiosQueue + "/alive"
	for i := 0; i < 100; i++ {
		if fileExists(alive) {
			break
		}
		time.Sleep(100 * time.Millisecond)
	}
	if !fileExists(alive) {
		t.Fatal("the root helper did not start")
	}
	// What GalIOS's QueueRootShell does.
	os.WriteFile(galiosQueue+"/t1.sh", []byte("echo helper-uid-$(id -u); exit 5\n"), 0o644)
	for i := 0; i < 50 && !fileExists(galiosQueue+"/t1.rc"); i++ {
		time.Sleep(100 * time.Millisecond)
	}
	rc, _ := os.ReadFile(galiosQueue + "/t1.rc")
	out, _ := os.ReadFile(galiosQueue + "/t1.out")
	if strings.TrimSpace(string(rc)) != "5" || strings.TrimSpace(string(out)) != "helper-uid-0" {
		t.Fatalf("rc %q out %q", rc, out)
	}
	// A second start leaves the running helper alone.
	forgetRoot("R5CY71TEST")
	getRoot(context.Background(), s, "R5CY71TEST")
	time.Sleep(2 * time.Second)
	os.WriteFile(bridgeDir+"/rootd.stop", nil, 0o644)
	time.Sleep(1500 * time.Millisecond)
	if fileExists(bridgeDir + "/rootd.pid") {
		t.Fatal("the helper did not stop")
	}
}

// TestReadOnlyStep runs OneOS's read-only loop as GalIOS adapts it, with a mount that refuses read-only binds of
// files (as the S25 Ultra's kernel did: "making /data/oneios_switch.sh read-only", status 70).
func TestReadOnlyStep(t *testing.T) {
	sw := os.Getenv("STUDIO_REAL_SWITCH")
	if sw == "" {
		t.Skip("needs OneOS's switch script")
	}
	b, err := os.ReadFile(sw)
	if err != nil {
		t.Fatal(err)
	}
	out, changed, err := adaptSwitch(b)
	if err != nil || !changed {
		t.Fatalf("%v %v", changed, err)
	}
	if again, ch, _ := adaptSwitch(out); ch || string(again) != string(out) {
		t.Fatal("adapting twice changed the script again")
	}
	if p := os.Getenv("STUDIO_ADAPTED_OUT"); p != "" {
		os.WriteFile(p, out, 0o644) // for the app's test: both must stage the same script
	}
	text := string(out)
	if !strings.Contains(text, roMarker) || !strings.Contains(text, "SM-S938*)") {
		t.Fatal("an adaptation is missing")
	}
	if c := exec.Command("sh", "-n", "-c", text); c.Run() != nil {
		t.Fatal("the adapted script is not valid sh")
	}
	// The loop alone, with /data and the two mounts replaced by stand-ins.
	start := strings.Index(text, "for d in runtime image control bin")
	end := strings.Index(text[start:], "\n        done") + start + len("\n        done")
	loop := text[start:end]
	run := func(failDirs bool) (string, int, string) {
		dir := t.TempDir()
		for _, d := range []string{"runtime", "image", "control", "bin"} {
			os.MkdirAll(filepath.Join(dir, "data", d), 0o755)
		}
		for _, f := range []string{"oneios_switch.sh", "oneios_return.sh"} {
			os.WriteFile(filepath.Join(dir, "data", f), []byte("#!/bin/sh\n"), 0o700)
		}
		refuse := `[ -f "$4" ] && exit 1`
		if failDirs {
			refuse = `exit 1`
		}
		mount := filepath.Join(dir, "mount")
		os.WriteFile(mount, []byte("#!/bin/sh\n[ \"$1\" = mount ] && shift\n[ \"$1\" = --bind ] && exit 0\n"+
			"case \"$2\" in *remount*) shift 2; set -- x x x \"$1\"; "+refuse+" ;; esac\nexit 0\n"), 0o755)
		body := strings.ReplaceAll(loop, "/system/bin/mount", mount+" mount")
		body = strings.ReplaceAll(body, "/data/", dir+"/data/")
		script := "BB=" + mount + "\nat() { echo \"at: $*\"; }\ndie_inner() { echo \"oneios inner: $*\"; exit 70; }\n" + body + "\necho finished\n"
		o, err := exec.Command("sh", "-c", script).CombinedOutput()
		code := 0
		if e, ok := err.(*exec.ExitError); ok {
			code = e.ExitCode()
		}
		return string(o), code, dir
	}
	o, code, dir := run(false)
	if code != 0 || !strings.Contains(o, "finished") || strings.Count(o, "warning:") != 2 {
		t.Fatalf("files refused: exit %d\n%s", code, o)
	}
	for _, f := range []string{"oneios_switch.sh", "oneios_return.sh"} {
		fi, _ := os.Stat(filepath.Join(dir, "data", f))
		if fi.Mode().Perm() != 0o500 {
			t.Fatalf("%s is %v", f, fi.Mode().Perm())
		}
	}
	t.Logf("files refused, folders fine:\n%s", o)
	if o, code, _ := run(true); code != 70 || !strings.Contains(o, "could not make") {
		t.Fatalf("a folder that cannot be made read-only must still end the session: exit %d\n%s", code, o)
	}
}

func TestRuntimeVersion(t *testing.T) {
	for in, want := range map[string]string{
		"oneios-runtime-0.1.8-arm64.tar.zst":               "0.1.8",
		`C:\x\oneios-runtime-0.1.8-galios.4-arm64.tar.zst`: "0.1.8-galios.4",
		"/d/oneios-runtime-0.1.8-test-arm64.tar.zst":       "0.1.8-test",
		"something-else.tar.zst":                           "",
	} {
		if got := runtimeVersion(filepath.FromSlash(strings.ReplaceAll(in, `\`, "/"))); got != want {
			t.Errorf("runtimeVersion(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestNewerVersion(t *testing.T) {
	for _, c := range []struct {
		a, b string
		want bool
	}{
		{"0.1.8-galios.2", "0.1.8-galios.1", true},
		{"0.1.8-galios.1", "0.1.8-galios.2", false},
		{"0.1.8-galios.10", "0.1.8-galios.9", true},
		{"0.1.8-galios.1", "0.1.8", true},
		{"0.1.9", "0.1.8-galios.5", true},
		{"0.1.8", "0.1.8", false},
	} {
		if got := newerVersion(c.a, c.b); got != c.want {
			t.Errorf("newerVersion(%q, %q) = %v, want %v", c.a, c.b, got, c.want)
		}
	}
}
