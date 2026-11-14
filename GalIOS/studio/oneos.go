package main

import (
	"archive/tar"
	"archive/zip"
	"bufio"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"github.com/klauspost/compress/zstd"
)

// Booting from the PC: the same machinery GalIOS uses on the phone, driven over adb. OneOS's boot engine (its root
// script and session scripts) is read from the OneOS app installed on the phone; Studio ships none of it.

const (
	oneosPackage = "com.fahadali.oneos"
	stageDir     = "/data/local/tmp/oneios"
	rootScript   = stageDir + "/bin/oneios_root.sh"
)

type Stage struct {
	Present  bool   `json:"present"`
	Runtime  string `json:"runtime"`
	Image    string `json:"image"`
	Session  bool   `json:"session"`
	Ready    bool   `json:"ready"`
	Reason   string `json:"reason"`
	Outcome  string `json:"outcome"`
	Answered bool   `json:"answered"`
}

type Reply struct {
	OK      bool
	Message string
	Results []string
	Lines   []string
}

func parseReply(lines []string, code int) Reply {
	r := Reply{Lines: lines}
	last := ""
	for _, l := range lines {
		switch {
		case strings.HasPrefix(l, "@@RESULT "):
			r.Results = append(r.Results, strings.TrimPrefix(l, "@@RESULT "))
		case strings.HasPrefix(l, "@@"):
			last = l
		}
	}
	r.OK = strings.HasPrefix(last, "@@OK")
	switch {
	case last != "":
		if i := strings.IndexByte(last, ' '); i > 0 {
			r.Message = last[i+1:]
		} else {
			r.Message = last
		}
	case len(lines) > 0:
		r.Message = lines[len(lines)-1]
	default:
		r.Message = fmt.Sprintf("no answer from root (exit %d)", code)
	}
	return r
}

// verb runs one of OneOS's root-script verbs. in, when given, is a file already on the phone that becomes the
// verb's input (receive, script).
func verb(ctx context.Context, s Settings, serial, in string, args ...string) Reply {
	cmd := "sh " + shq(rootScript)
	for _, a := range args {
		cmd += " " + shq(a)
	}
	if in != "" {
		cmd += " < " + shq(in)
	}
	lines, code, err := rootRun(ctx, s, serial, cmd)
	if err != nil && len(lines) == 0 {
		return Reply{Message: err.Error()}
	}
	return parseReply(lines, code)
}

// verbBytes copies data to the phone and runs a verb with it as input.
func verbBytes(ctx context.Context, s Settings, serial string, data []byte, args ...string) Reply {
	remote, err := pushBytes(ctx, s, serial, newID()+".in", data)
	if err != nil {
		return Reply{Message: err.Error()}
	}
	defer runLines(context.Background(), nil, adbExe(s), adbArgs(serial, "shell", "rm -f "+remote)...)
	return verb(ctx, s, serial, remote, args...)
}

func stageStatus(ctx context.Context, s Settings, serial string) Stage {
	r := verb(ctx, s, serial, "", "status")
	st := Stage{Answered: len(r.Results) > 0}
	for _, l := range r.Results {
		f := strings.Fields(l)
		if len(f) == 0 {
			continue
		}
		switch f[0] {
		case "stage":
			st.Present = len(f) > 1 && f[1] == "present"
		case "runtime":
			if len(f) > 1 && f[1] != "none" {
				st.Runtime = f[1]
			}
		case "image":
			if len(f) > 3 && f[1] != "none" {
				st.Image = f[2] + " (" + f[3] + ")"
			}
		case "session":
			st.Session = len(f) > 1 && f[1] == "open"
		case "outcome":
			if o := strings.TrimPrefix(l, "outcome "); o != "none" {
				st.Outcome = o
			}
		case "ready":
			st.Ready = len(f) > 1 && f[1] == "1"
			if !st.Ready {
				st.Reason = strings.TrimSpace(strings.TrimPrefix(l, "ready 0"))
			}
		}
	}
	return st
}

// ---- The engine, from the OneOS app on the phone ---------------------------------------------------------------

type Engine struct{ files map[string][]byte }

var engineAssets = []string{
	"root/oneios_root.sh", "oneios/oneios_switch.sh", "oneios/oneios_return.sh",
	"oneios/boot_status.sh", "oneios/OneOSBootStatus.dex", "oneios/oneos_boot_icon.png",
}

func pullEngine(ctx context.Context, j *JobCtx, s Settings, serial string) (*Engine, error) {
	j.Step("Reading OneOS's boot engine from the phone")
	lines, _, _ := runLines(ctx, nil, adbExe(s), adbArgs(serial, "shell", "pm path "+oneosPackage)...)
	apk := ""
	for _, l := range lines {
		if strings.HasPrefix(l, "package:") && strings.HasSuffix(l, "base.apk") {
			apk = strings.TrimPrefix(l, "package:")
		}
	}
	if apk == "" {
		return nil, fmt.Errorf("OneOS (iOS 27 edition) is not installed on the phone: it is the boot engine")
	}
	local := filepath.Join(dataDir(), "oneos.apk")
	os.MkdirAll(dataDir(), 0o755)
	if _, code, err := runLines(ctx, nil, adbExe(s), adbArgs(serial, "pull", apk, local)...); err != nil || code != 0 {
		return nil, fmt.Errorf("could not copy OneOS from the phone (exit %d)", code)
	}
	zr, err := zip.OpenReader(local)
	if err != nil {
		return nil, err
	}
	defer zr.Close()
	e := &Engine{files: map[string][]byte{}}
	for _, f := range zr.File {
		for _, want := range engineAssets {
			if f.Name == "assets/"+want {
				rc, err := f.Open()
				if err != nil {
					return nil, err
				}
				b, err := io.ReadAll(rc)
				rc.Close()
				if err != nil {
					return nil, err
				}
				e.files[want] = b
			}
		}
	}
	for _, want := range engineAssets {
		if _, ok := e.files[want]; !ok {
			return nil, fmt.Errorf("this OneOS build has no %s: install OneOS's iOS 27 edition", want)
		}
	}
	return e, nil
}

// adaptSwitch makes OneOS's switch script work on the S25 Ultra: its phone check accepts the S25 Ultra
// (adaptPhoneCheck), and a script file that the S25 Ultra's kernel will not bind read-only no longer ends the
// session (adaptReadOnly). Each change is made once and only where the script is the one Studio knows.
func adaptSwitch(script []byte) ([]byte, bool, error) {
	out, phone, err := adaptPhoneCheck(script)
	if err != nil {
		return nil, false, err
	}
	out, ro := adaptReadOnly(out)
	return out, phone || ro, nil
}

// roMarker marks a script whose read-only step GalIOS already adapted.
const roMarker = "# GalIOS (S25 Ultra): busybox's read-only bind, then Android's own mount;"

var roBlock = []string{
	roMarker,
	"# a script file that still cannot be made read-only here is left to root alone (0500) with a warning",
	"# instead of ending the session (the S25 Ultra's kernel refused it: status 70). Folders stay strict.",
	`if "$BB" mount --bind "/data/$d" "/data/$d" 2>&1; then`,
	`    "$BB" mount -o remount,bind,ro "/data/$d" 2>&1 || /system/bin/mount -o remount,ro,bind "/data/$d" 2>&1 || {`,
	`        [ -f "/data/$d" ] || die_inner "could not make /data/$d read-only"`,
	`        chmod 0500 "/data/$d"`,
	`        echo "oneios inner: $(date +%T) warning: /data/$d could not be made read-only here; kept root's alone"`,
	`    }`,
	`else`,
	`    [ -f "/data/$d" ] || die_inner "could not make /data/$d read-only"`,
	`    chmod 0500 "/data/$d"`,
	`    echo "oneios inner: $(date +%T) warning: /data/$d could not be bound here; kept root's alone"`,
	`fi`,
}

// adaptReadOnly replaces OneOS's two-line read-only step (bind, remount read-only, else die) with roBlock. A script
// without those exact lines is left as it is.
func adaptReadOnly(script []byte) ([]byte, bool) {
	text := string(script)
	if strings.Contains(text, roMarker) {
		return script, false
	}
	lines := strings.Split(text, "\n")
	for i := 0; i+1 < len(lines); i++ {
		if strings.TrimSpace(lines[i]) != `"$BB" mount --bind "/data/$d" "/data/$d" && "$BB" mount -o remount,bind,ro "/data/$d" ||` ||
			strings.TrimSpace(lines[i+1]) != `die_inner "could not make /data/$d read-only"` {
			continue
		}
		indent := lines[i][:len(lines[i])-len(strings.TrimLeft(lines[i], " \t"))]
		var out []string
		out = append(out, lines[:i]...)
		for _, b := range roBlock {
			out = append(out, indent+b)
		}
		out = append(out, lines[i+2:]...)
		return []byte(strings.Join(out, "\n")), true
	}
	return script, false
}

// adaptPhoneCheck rewrites OneOS's S24-only phone check so it accepts the S25 Ultra too (as GalIOS does on the phone).
func adaptPhoneCheck(script []byte) ([]byte, bool, error) {
	text := string(script)
	if regexp.MustCompile(`(?m)^\s*SM-S938\*\)`).MatchString(text) {
		return script, false, nil
	}
	lines := strings.Split(text, "\n")
	model := -1
	for i, l := range lines {
		t := strings.TrimSpace(l)
		if strings.HasPrefix(t, `case "$P_MODEL" in SM-S928*)`) && strings.HasSuffix(t, "esac") && strings.Contains(t, "refuse") {
			model = i
			break
		}
	}
	if model < 0 || model+2 >= len(lines) {
		return nil, false, fmt.Errorf("OneOS's phone check is not the one Studio knows (no SM-S928 model line)")
	}
	soc := strings.TrimSpace(lines[model+1])
	if !(strings.HasPrefix(soc, `[ "$P_DEVICE" = e3q ]`) && strings.Contains(soc, "SM8650") && strings.HasSuffix(soc, "||")) ||
		!strings.HasPrefix(strings.TrimSpace(lines[model+2]), "refuse ") {
		return nil, false, fmt.Errorf("OneOS's codename and SoC check is not the one Studio knows")
	}
	for i, l := range lines {
		if i >= model && i <= model+2 || strings.HasPrefix(strings.TrimSpace(l), "#") {
			continue
		}
		for _, tok := range []string{"SM-S928", `"e3q"`, "= e3q", "SM8650"} {
			if strings.Contains(l, tok) {
				return nil, false, fmt.Errorf("OneOS also checks for the S24 Ultra at its line %d", i+1)
			}
		}
	}
	indent := lines[model][:len(lines[model])-len(strings.TrimLeft(lines[model], " \t"))]
	check := []string{
		"# GalIOS: this phone check accepts the Galaxy S25 Ultra beside the S24 Ultra.",
		`case "$P_MODEL" in`,
		`    SM-S928*) [ "$P_DEVICE" = e3q ] && [ "$P_SOC" = SM8650 ] ||`,
		`        refuse "iOS on the Galaxy S24 Ultra needs e3q, SM8650; this phone is ${P_DEVICE:-?}, ${P_SOC:-?}" ;;`,
		`    SM-S938*) [ "$P_DEVICE" = pa3q ] && [ "$P_SOC" = SM8750 ] ||`,
		`        refuse "iOS on the Galaxy S25 Ultra needs pa3q, SM8750; this phone is ${P_DEVICE:-?}, ${P_SOC:-?}" ;;`,
		`    *) refuse "iOS runs on the Galaxy S24 Ultra and S25 Ultra only; this phone is ${P_MODEL:-unknown}" ;;`,
		"esac",
	}
	var out []string
	out = append(out, lines[:model]...)
	for _, c := range check {
		out = append(out, indent+c)
	}
	out = append(out, lines[model+3:]...)
	return []byte(strings.Join(out, "\n")), true, nil
}

func sha256hex(b []byte) string { h := sha256.Sum256(b); return hex.EncodeToString(h[:]) }

// installEngine puts the root script in the stage (as OneOS's launcher does) and stages the session scripts.
func installEngine(ctx context.Context, j *JobCtx, s Settings, serial string, e *Engine) (string, error) {
	j.Step("Installing the boot engine in the phone's stage")
	rs := e.files["root/oneios_root.sh"]
	tmpl := fmt.Sprintf(`umask 077
S=%[1]s; F="$S/bin/oneios_root.sh"
if awk -v s="$S" '$5 == s || index($5, s "/") == 1 { f = 1 } END { exit !f }' /proc/1/mountinfo /proc/self/mountinfo; then
  echo "@@REFUSED something is mounted in the stage: restart the phone first"; exit 2
fi
[ -d "$S" ] || mkdir "$S" || { echo "@@FAILED could not make the stage"; exit 4; }
chown 0:0 "$S" && chmod 0700 "$S" || exit 4
[ -d "$S/bin" ] || mkdir "$S/bin" || exit 4
chown 0:0 "$S/bin" && chmod 0700 "$S/bin" || exit 4
rm -f "$F.new"
cp %[4]s "$F.new" && [ "$(wc -c < "$F.new")" -eq %[2]d ] || { rm -f "$F.new"; echo "@@FAILED could not write the root script"; exit 4; }
[ "$(sha256sum "$F.new" 2>/dev/null | cut -d' ' -f1)" = %[3]s ] || { rm -f "$F.new"; echo "@@REFUSED the root script did not arrive intact"; exit 2; }
chown 0:0 "$F.new" && chmod 0700 "$F.new" && mv -f "$F.new" "$F" || exit 4
echo "@@OK install"`, shq(stageDir), len(rs), sha256hex(rs), "%[4]s")
	remote, err := pushBytes(ctx, s, serial, "oneios_root.sh", rs)
	if err != nil {
		return "", err
	}
	tmpl = strings.ReplaceAll(tmpl, "%[4]s", shq(remote))
	lines, code, err := rootRun(ctx, s, serial, tmpl)
	if err != nil && len(lines) == 0 {
		return "", err
	}
	if r := parseReply(lines, code); !r.OK {
		return "", fmt.Errorf("installing the root script: %s", r.Message)
	}
	adapted, err := stageScripts(ctx, j, s, serial, e)
	if err != nil {
		return "", err
	}
	if adapted {
		return "Boot engine installed; OneOS's phone check now accepts the S25 Ultra.", nil
	}
	return "Boot engine installed.", nil
}

func stageScripts(ctx context.Context, j *JobCtx, s Settings, serial string, e *Engine) (bool, error) {
	sw, adapted, err := adaptSwitch(e.files["oneios/oneios_switch.sh"])
	if err != nil {
		return false, err
	}
	files := []struct {
		name string
		b    []byte
	}{
		{"oneios_switch.sh", sw}, {"oneios_return.sh", e.files["oneios/oneios_return.sh"]},
		{"boot_status.sh", e.files["oneios/boot_status.sh"]}, {"OneOSBootStatus.dex", e.files["oneios/OneOSBootStatus.dex"]},
		{"oneos_boot_icon.png", e.files["oneios/oneos_boot_icon.png"]},
	}
	for _, f := range files {
		r := verbBytes(ctx, s, serial, f.b, "script", f.name, fmt.Sprint(len(f.b)), sha256hex(f.b))
		if !r.OK {
			return false, fmt.Errorf("staging %s: %s", f.name, r.Message)
		}
		j.Log("staged %s", f.name)
	}
	return adapted, nil
}

// ---- Importing a runtime package or an image set, streamed from the PC into root's receive ---------------------

type manifestFile struct {
	Path   string `json:"path"`
	Size   int64  `json:"size"`
	SHA256 string `json:"sha256"`
	Mode   string `json:"mode"`
}

type manifest struct {
	Format       json.Number    `json:"format"`
	Version      string         `json:"version"`
	Build        string         `json:"build"`
	SessionABI   json.Number    `json:"session_abi"`
	XNUHost      string         `json:"xnuhost"`
	ImageFormats []int          `json:"image_formats"`
	Placeholder  bool           `json:"placeholder"`
	Files        []manifestFile `json:"files"`
}

var safePath = regexp.MustCompile(`^[A-Za-z0-9._+,-]+(/[A-Za-z0-9._+,-]+)*$`)

// importArchive streams a .tar.zst (manifest first, as OneiOS packs both) into the phone's stage, file by file:
// root checks each file's size and SHA-256 as it writes it; finish puts the whole set in place, or nothing.
func importArchive(ctx context.Context, j *JobCtx, s Settings, serial, kind, path string) (string, error) {
	f, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer f.Close()
	zr, err := zstd.NewReader(bufio.NewReaderSize(f, 1<<20))
	if err != nil {
		return "", err
	}
	defer zr.Close()
	tr := tar.NewReader(zr)
	manName := map[string]string{"runtime": "runtime.json", "image": "manifest.json"}[kind]
	j.Step("Reading " + filepath.Base(path))
	var first *tar.Header
	for {
		h, err := tr.Next()
		if err != nil {
			return "", fmt.Errorf("the archive is empty or damaged: %v", err)
		}
		if h.Typeflag == tar.TypeReg {
			first = h
			break
		}
	}
	name := strings.TrimPrefix(first.Name, "./")
	prefix := ""
	switch {
	case name == manName:
	case strings.HasSuffix(name, "/"+manName) && strings.Count(name, "/") == 1:
		prefix = name[:strings.IndexByte(name, '/')+1]
	default:
		return "", fmt.Errorf("this is not a OneiOS %s archive: its first file is %s", kind, name)
	}
	raw, err := io.ReadAll(io.LimitReader(tr, 4<<20))
	if err != nil {
		return "", err
	}
	var m manifest
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.UseNumber()
	if err := dec.Decode(&m); err != nil {
		return "", fmt.Errorf("%s is not valid JSON", manName)
	}
	want := map[string]manifestFile{}
	var total int64
	for _, mf := range m.Files {
		if !safePath.MatchString(mf.Path) || strings.Contains("/"+mf.Path+"/", "/../") {
			return "", fmt.Errorf("%s: not a plain path", mf.Path)
		}
		if mf.Mode == "" {
			mf.Mode = "0644"
		}
		want[mf.Path] = mf
		total += mf.Size
	}
	if err := phoneDir(ctx, s, serial); err != nil {
		return "", err
	}
	tmpDir := filepath.Join(dataDir(), "tmp")
	os.MkdirAll(tmpDir, 0o755)
	var biggest int64
	for _, mf := range m.Files {
		biggest = max(biggest, mf.Size)
	}
	if free := diskFree(tmpDir); free > 0 && free < biggest+(1<<30) {
		return "", fmt.Errorf("not enough free space on this PC to pass the files through (%s needed in %s, %s free)",
			human(biggest+(1<<30)), tmpDir, human(free))
	}
	if r := verb(ctx, s, serial, "", "begin", kind); !r.OK {
		return "", fmt.Errorf("begin: %s", r.Message)
	}
	label := map[string]string{"runtime": "OneiOS runtime " + m.Version, "image": "iOS " + m.Version + " (" + m.Build + ")"}[kind]
	j.Step("Copying " + label + " into the phone's stage")
	var done int64
	seen := map[string]bool{}
	for {
		if ctx.Err() != nil {
			return "", ctx.Err()
		}
		h, err := tr.Next()
		if err == io.EOF {
			break
		}
		if err != nil {
			return "", err
		}
		if h.Typeflag != tar.TypeReg {
			continue
		}
		p := strings.TrimPrefix(strings.TrimPrefix(h.Name, "./"), prefix)
		mf, ok := want[p]
		if !ok {
			return "", fmt.Errorf("%s is in the archive but not in %s", p, manName)
		}
		if h.Size != mf.Size {
			return "", fmt.Errorf("%s: %d bytes in the archive, %d in %s", p, h.Size, mf.Size, manName)
		}
		seen[p] = true
		// The file goes to the PC's temp folder, then to the phone with adb push, then root takes it in (it checks
		// the size and SHA-256 as it copies it into the stage).
		local := filepath.Join(tmpDir, "part")
		if err := extractTo(ctx, tr, local, mf.Size, func(n int64) {
			j.Progress(float64(done+n/2)/float64(max(total, 1)), fmt.Sprintf("%s · unpacking %s of %s", p, human(n), human(mf.Size)))
		}); err != nil {
			os.Remove(local)
			return "", fmt.Errorf("%s: %v", p, err)
		}
		remote := bridgeDir + "/part"
		base := done
		err = pushFileProgress(ctx, s, serial, local, remote, func(n int64) {
			j.Progress(float64(base+mf.Size/2+n/2)/float64(max(total, 1)), fmt.Sprintf("%s · copying %s of %s", p, human(n), human(mf.Size)))
		})
		os.Remove(local)
		if err != nil {
			return "", fmt.Errorf("%s: %v", p, err)
		}
		j.Progress(float64(base+mf.Size)/float64(max(total, 1)), p+" · the phone is checking it")
		r := verb(ctx, s, serial, remote, "receive", kind, p, fmt.Sprint(mf.Size), mf.SHA256, mf.Mode)
		runLines(context.Background(), nil, adbExe(s), adbArgs(serial, "shell", "rm -f "+remote)...)
		if !r.OK {
			return "", fmt.Errorf("%s: %s", p, r.Message)
		}
		done = base + mf.Size
		j.Log("received %s (%s)", p, human(mf.Size))
	}
	for p := range want {
		if !seen[p] {
			return "", fmt.Errorf("the archive ended without %s", p)
		}
	}
	if r := verbBytes(ctx, s, serial, raw, "receive", kind, manName, fmt.Sprint(len(raw)), sha256hex(raw), "0644"); !r.OK {
		return "", fmt.Errorf("%s: %s", manName, r.Message)
	}
	j.Step("Checking and putting it in place")
	args := []string{"finish", kind, fmt.Sprint(len(m.Files) + 1)}
	if kind == "runtime" {
		fm := make([]string, len(m.ImageFormats))
		for i, x := range m.ImageFormats {
			fm[i] = fmt.Sprint(x)
		}
		ph := "0"
		if m.Placeholder {
			ph = "1"
		}
		args = append(args, m.Version, m.SessionABI.String(), m.XNUHost, strings.Join(fm, ","), ph)
	} else {
		args = append(args, m.Format.String(), m.Version, m.Build)
	}
	if r := verb(ctx, s, serial, "", args...); !r.OK {
		return "", fmt.Errorf("finish: %s", r.Message)
	}
	return label + " is on the phone.", nil
}

// extractTo writes the current tar entry to a file, reporting progress.
func extractTo(ctx context.Context, r io.Reader, path string, size int64, onBytes func(int64)) error {
	f, err := os.Create(path)
	if err != nil {
		return err
	}
	pr := &progressReader{r: r, onRead: onBytes}
	n, err := io.Copy(f, &ctxReader{ctx, pr})
	if cerr := f.Close(); err == nil {
		err = cerr
	}
	if err == nil && n != size {
		err = fmt.Errorf("the archive ended early (%d of %d bytes)", n, size)
	}
	return err
}

type ctxReader struct {
	ctx context.Context
	r   io.Reader
}

func (c *ctxReader) Read(b []byte) (int, error) {
	if err := c.ctx.Err(); err != nil {
		return 0, err
	}
	return c.r.Read(b)
}

type progressReader struct {
	r      io.Reader
	n      int64
	last   time.Time
	onRead func(int64)
}

func (p *progressReader) Read(b []byte) (int, error) {
	n, err := p.r.Read(b)
	p.n += int64(n)
	if time.Since(p.last) > 400*time.Millisecond || err == io.EOF {
		p.onRead(p.n)
		p.last = time.Now()
	}
	return n, err
}

// latestRuntime downloads the newest OneiOS runtime package from OneiOS's releases (checked against its .sha256).
// runtimeRepos are where runtime packages come from, best first: the GalIOS GPU pack's releases (OneiOS's runtime
// built from the same commit as the kernel GalIOS builds), then OneiOS's own releases.
var runtimeRepos = []struct{ repo, tagPrefix string }{
	{"Rayanboonty/3Dmodel", "galios-gpu-"},
	{"akshaynexus/OneiOS", ""},
}

var runtimeName = regexp.MustCompile(`^oneios-runtime-(.+)-arm64\.tar\.zst$`)

// runtimeVersion is the version in a runtime package's file name ("" when it is not one).
func runtimeVersion(path string) string {
	if m := runtimeName.FindStringSubmatch(filepath.Base(path)); m != nil {
		return m[1]
	}
	return ""
}

// latestRuntime downloads (or reuses) the newest runtime package, checked against its .sha256.
func latestRuntime(ctx context.Context, j *JobCtx) (string, error) {
	if p := os.Getenv("STUDIO_RUNTIME"); p != "" { // tests: a local runtime package instead of GitHub
		return p, nil
	}
	var last error
	for _, src := range runtimeRepos {
		p, err := runtimeFrom(ctx, j, src.repo, src.tagPrefix)
		if err == nil {
			return p, nil
		}
		last = err
		j.Log("runtime from %s: %v", src.repo, err)
	}
	return "", last
}

func runtimeFrom(ctx context.Context, j *JobCtx, repo, tagPrefix string) (string, error) {
	j.Step("Looking up the newest runtime on GitHub (" + repo + ")")
	req, _ := http.NewRequestWithContext(ctx, "GET", "https://api.github.com/repos/"+repo+"/releases?per_page=40", nil)
	req.Header.Set("Accept", "application/vnd.github+json")
	req.Header.Set("User-Agent", "GalIOS-Studio")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("could not reach GitHub: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return "", fmt.Errorf("GitHub answered HTTP %d", resp.StatusCode)
	}
	var rels []struct {
		Tag    string `json:"tag_name"`
		Draft  bool   `json:"draft"`
		Assets []struct {
			Name string `json:"name"`
			URL  string `json:"browser_download_url"`
		} `json:"assets"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&rels); err != nil {
		return "", err
	}
	for _, r := range rels {
		if r.Draft || !strings.HasPrefix(r.Tag, tagPrefix) {
			continue
		}
		var url, shaURL, name string
		for _, a := range r.Assets {
			if runtimeName.MatchString(a.Name) {
				url, name = a.URL, a.Name
			}
		}
		for _, a := range r.Assets {
			if a.Name == name+".sha256" {
				shaURL = a.URL
			}
		}
		if url == "" {
			continue
		}
		dest := filepath.Join(dataDir(), "runtime", name)
		wantSum := ""
		if shaURL != "" {
			tmp := dest + ".sha256"
			if err := download(ctx, j, shaURL, tmp); err == nil {
				b, _ := os.ReadFile(tmp)
				if f := strings.Fields(string(b)); len(f) > 0 {
					wantSum = strings.ToLower(f[0])
				}
			}
		}
		if got, err := fileSHA(dest); err == nil && wantSum != "" && got == wantSum {
			j.Log("%s: already downloaded and checked", name)
			return dest, nil
		}
		j.Step("Downloading " + name)
		if err := download(ctx, j, url, dest); err != nil {
			return "", err
		}
		if wantSum != "" {
			if got, _ := fileSHA(dest); got != wantSum {
				os.Remove(dest)
				return "", fmt.Errorf("the runtime did not match its SHA-256: try again")
			}
			j.Log("SHA-256 checked")
		}
		return dest, nil
	}
	return "", fmt.Errorf("no release with a runtime package")
}

func fileSHA(p string) (string, error) {
	f, err := os.Open(p)
	if err != nil {
		return "", err
	}
	defer f.Close()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", err
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

// pullLogs saves the session logs from the phone's stage into Studio's folder and prints their ends.
func pullLogs(ctx context.Context, j *JobCtx, s Settings, serial string) (string, error) {
	j.Step("Reading the session logs from the phone")
	r := verb(ctx, s, serial, "", "logs")
	if !r.OK {
		return "", fmt.Errorf("%s", r.Message)
	}
	dir := filepath.Join(dataDir(), "logs", time.Now().Format("2006-01-02_15-04-05"))
	os.MkdirAll(dir, 0o755)
	n := 0
	for _, res := range r.Results {
		f := strings.Fields(res)
		if len(f) < 2 || f[0] != "log" {
			continue
		}
		id := f[1]
		lr := verb(ctx, s, serial, "", "logfile", id, "0", "1048576")
		var b strings.Builder
		for _, l := range lr.Lines {
			if strings.HasPrefix(l, "| ") || l == "|" {
				b.WriteString(strings.TrimPrefix(strings.TrimPrefix(l, "|"), " ") + "\n")
			}
		}
		os.WriteFile(filepath.Join(dir, id+".log"), []byte(b.String()), 0o644)
		lines := strings.Split(strings.TrimRight(b.String(), "\n"), "\n")
		j.Log("── %s (%d lines) ──", id, len(lines))
		for _, l := range lines[max(0, len(lines)-8):] {
			j.Line("  " + l)
		}
		n++
	}
	if n == 0 {
		return "No session logs on the phone yet.", nil
	}
	return fmt.Sprintf("%d logs saved to %s", n, dir), nil
}
