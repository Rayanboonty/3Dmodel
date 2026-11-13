package main

import (
	"archive/zip"
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"time"
)

// The phone over USB: Android's adb (Studio downloads Google's platform-tools when there is none) and a root shell
// on the phone (Settings: root command, "su -c" by default).

func adbExe(s Settings) string {
	if s.ADBPath != "" {
		return s.ADBPath
	}
	if p := os.Getenv("STUDIO_ADB"); p != "" {
		return p
	}
	name := "adb"
	if runtime.GOOS == "windows" {
		name = "adb.exe"
	}
	if p := filepath.Join(dataDir(), "platform-tools", name); fileExists(p) {
		return p
	}
	if p, err := exec.LookPath(name); err == nil {
		return p
	}
	return name
}

func fileExists(p string) bool { _, err := os.Stat(p); return err == nil }

// getADB downloads Google's platform-tools for this system into Studio's folder.
func getADB(ctx context.Context, j *JobCtx) (string, error) {
	osname := map[string]string{"windows": "windows", "darwin": "darwin"}[runtime.GOOS]
	if osname == "" {
		osname = "linux"
	}
	url := "https://dl.google.com/android/repository/platform-tools-latest-" + osname + ".zip"
	j.Step("Downloading Android platform-tools (adb) from Google")
	zipPath := filepath.Join(dataDir(), "platform-tools.zip")
	if err := download(ctx, j, url, zipPath); err != nil {
		return "", err
	}
	j.Step("Unpacking")
	zr, err := zip.OpenReader(zipPath)
	if err != nil {
		return "", err
	}
	defer zr.Close()
	for _, f := range zr.File {
		dest := filepath.Join(dataDir(), filepath.FromSlash(f.Name))
		if !strings.HasPrefix(dest, filepath.Clean(dataDir())+string(os.PathSeparator)) {
			continue
		}
		if f.FileInfo().IsDir() {
			os.MkdirAll(dest, 0o755)
			continue
		}
		os.MkdirAll(filepath.Dir(dest), 0o755)
		rc, err := f.Open()
		if err != nil {
			return "", err
		}
		out, err := os.OpenFile(dest, os.O_CREATE|os.O_WRONLY|os.O_TRUNC, f.Mode()|0o600)
		if err != nil {
			rc.Close()
			return "", err
		}
		_, err = io.Copy(out, rc)
		rc.Close()
		out.Close()
		if err != nil {
			return "", err
		}
	}
	os.Remove(zipPath)
	return "adb is ready.", nil
}

// download fetches url to dest (via a .part file) with progress.
func download(ctx context.Context, j *JobCtx, url, dest string) error {
	req, _ := http.NewRequestWithContext(ctx, "GET", url, nil)
	req.Header.Set("User-Agent", "GalIOS-Studio")
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != 200 {
		return fmt.Errorf("%s answered HTTP %d", req.URL.Host, resp.StatusCode)
	}
	os.MkdirAll(filepath.Dir(dest), 0o755)
	part := dest + ".part"
	f, err := os.Create(part)
	if err != nil {
		return err
	}
	total := resp.ContentLength
	var done int64
	buf := make([]byte, 256*1024)
	last := time.Now()
	for {
		n, rerr := resp.Body.Read(buf)
		if n > 0 {
			if _, werr := f.Write(buf[:n]); werr != nil {
				f.Close()
				return werr
			}
			done += int64(n)
			if total > 0 && time.Since(last) > 300*time.Millisecond {
				j.Progress(float64(done)/float64(total), fmt.Sprintf("%s of %s", human(done), human(total)))
				last = time.Now()
			}
		}
		if rerr == io.EOF {
			break
		}
		if rerr != nil {
			f.Close()
			return rerr
		}
	}
	f.Close()
	return os.Rename(part, dest)
}

func human(n int64) string {
	switch {
	case n >= 1<<30:
		return fmt.Sprintf("%.1f GB", float64(n)/(1<<30))
	case n >= 1<<20:
		return fmt.Sprintf("%.0f MB", float64(n)/(1<<20))
	case n >= 1<<10:
		return fmt.Sprintf("%.0f KB", float64(n)/(1<<10))
	}
	return fmt.Sprintf("%d B", n)
}

type Device struct {
	Serial string `json:"serial"`
	State  string `json:"state"`
	Model  string `json:"model"`
}

func adbDevices(ctx context.Context, s Settings) ([]Device, error) {
	lines, code, err := runLines(ctx, nil, adbExe(s), "devices", "-l")
	if err != nil && code != 0 {
		return nil, fmt.Errorf("adb did not run: %v (use Get adb)", err)
	}
	var out []Device
	for _, l := range lines {
		f := strings.Fields(l)
		if len(f) < 2 || strings.HasPrefix(l, "List of devices") || strings.HasPrefix(l, "*") {
			continue
		}
		d := Device{Serial: f[0], State: f[1]}
		for _, kv := range f[2:] {
			if strings.HasPrefix(kv, "model:") {
				d.Model = strings.ReplaceAll(strings.TrimPrefix(kv, "model:"), "_", "-")
			}
		}
		out = append(out, d)
	}
	return out, nil
}

// pickSerial is the phone to talk to: the one in Settings, else the only one connected.
func pickSerial(ctx context.Context, s Settings) (string, error) {
	ds, err := adbDevices(ctx, s)
	if err != nil {
		return "", err
	}
	var ready []Device
	for _, d := range ds {
		if s.Serial != "" && d.Serial == s.Serial {
			if d.State != "device" {
				return "", fmt.Errorf("the phone is %s: unlock it and allow USB debugging on its screen", d.State)
			}
			return d.Serial, nil
		}
		if d.State == "device" {
			ready = append(ready, d)
		} else if d.State == "unauthorized" {
			return "", fmt.Errorf("on the phone, tap Allow on the USB debugging prompt")
		}
	}
	switch len(ready) {
	case 0:
		return "", fmt.Errorf("no phone found: connect it by USB with USB debugging on")
	case 1:
		return ready[0].Serial, nil
	}
	return "", fmt.Errorf("%d phones are connected: choose one in Settings", len(ready))
}

func adbArgs(serial string, args ...string) []string {
	return append([]string{"-s", serial}, args...)
}

// rootCmd is the remote command that runs cmd as root (shell -T: no terminal, so stdin and exit codes pass).
func rootRemote(s Settings, cmd string) string { return s.RootCmd + " " + shq(cmd) }

func rootRun(ctx context.Context, s Settings, serial, cmd string, stdin io.Reader) ([]string, int, error) {
	return runLines(ctx, stdin, adbExe(s), adbArgs(serial, "shell", "-T", rootRemote(s, cmd))...)
}

func rootStream(ctx context.Context, s Settings, serial, cmd string, stdin io.Reader, onLine func(string)) (int, error) {
	return stream(ctx, stdin, onLine, adbExe(s), adbArgs(serial, "shell", "-T", rootRemote(s, cmd))...)
}

type PhoneStatus struct {
	Connected  bool     `json:"connected"`
	Message    string   `json:"message"`
	Serial     string   `json:"serial"`
	Model      string   `json:"model"`
	Device     string   `json:"device"`
	SoC        string   `json:"soc"`
	Android    string   `json:"android"`
	Supported  bool     `json:"supported"`
	Label      string   `json:"label"`
	Root       bool     `json:"root"`
	Busybox    string   `json:"busybox"`
	Resetprop  string   `json:"resetprop"`
	VolKeys    bool     `json:"volKeys"`
	FreeGB     int      `json:"freeGB"`
	GPU        string   `json:"gpu"`
	OneOS      string   `json:"oneos"`
	GalIOS     string   `json:"galios"`
	Stage      Stage    `json:"stage"`
	Devices    []Device `json:"devices"`
	CheckedAt  int64    `json:"checkedAt"`
}

const checksScript = `echo "uid=$(id -u)"
for c in /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox /data/adb/magisk/busybox; do [ -x "$c" ] && { echo "busybox=$c"; break; }; done
for c in /data/adb/ksu/bin/resetprop /data/adb/ap/bin/resetprop /debug_ramdisk/resetprop /sbin/resetprop; do [ -x "$c" ] && { echo "resetprop=$c"; break; }; done
k=$(getevent -pl 2>/dev/null)
case "$k" in *KEY_VOLUMEUP*) case "$k" in *KEY_VOLUMEDOWN*) echo "volkeys=1" ;; esac ;; esac
echo "free=$(stat -f -c '%a %S' /data 2>/dev/null)"
echo "gpu=$(cat /sys/class/kgsl/kgsl-3d0/gpu_model 2>/dev/null)"`

func checkPhone(ctx context.Context, s Settings) PhoneStatus {
	st := PhoneStatus{CheckedAt: time.Now().Unix()}
	st.Devices, _ = adbDevices(ctx, s)
	serial, err := pickSerial(ctx, s)
	if err != nil {
		st.Message = err.Error()
		return st
	}
	st.Connected, st.Serial = true, serial
	// One line each, in order: the three ids OneOS's switch checks, the Android version.
	lines, _, _ := runLines(ctx, nil, adbExe(s), adbArgs(serial, "shell",
		"getprop ro.product.model; getprop ro.product.device; getprop ro.soc.model; getprop ro.build.version.release")...)
	get := func(i int) string {
		if i < len(lines) {
			return strings.TrimSpace(lines[i])
		}
		return ""
	}
	st.Model, st.Device, st.SoC, st.Android = get(0), get(1), get(2), get(3)
	st.OneOS = packageVersion(ctx, s, serial, oneosPackage)
	st.GalIOS = packageVersion(ctx, s, serial, "dev.galios")
	st.Supported, st.Label = phoneKind(st.Model, st.Device, st.SoC)
	cl, _, _ := rootRun(ctx, s, serial, checksScript, nil)
	kv := map[string]string{}
	for _, l := range cl {
		if i := strings.IndexByte(l, '='); i > 0 {
			kv[l[:i]] = strings.TrimSpace(l[i+1:])
		}
	}
	st.Root = kv["uid"] == "0"
	st.Busybox, st.Resetprop, st.GPU = kv["busybox"], kv["resetprop"], kv["gpu"]
	st.VolKeys = kv["volkeys"] == "1"
	if f := strings.Fields(kv["free"]); len(f) == 2 {
		a, _ := strconv.ParseInt(f[0], 10, 64)
		b, _ := strconv.ParseInt(f[1], 10, 64)
		st.FreeGB = int(a * b >> 30)
	}
	if st.Root {
		st.Stage = stageStatus(ctx, s, serial)
	} else {
		st.Message = "Root is not active on the phone: start your temporary root (Settings has the root command)."
	}
	return st
}

// packageVersion is an app's versionName on the phone, or "" when it is not installed.
func packageVersion(ctx context.Context, s Settings, serial, pkg string) string {
	lines, _, _ := runLines(ctx, nil, adbExe(s), adbArgs(serial, "shell", "dumpsys package "+pkg+" | grep -m1 versionName")...)
	for _, l := range lines {
		if i := strings.Index(l, "versionName="); i >= 0 {
			return strings.TrimSpace(l[i+len("versionName="):])
		}
	}
	return ""
}

func phoneKind(model, device, soc string) (bool, string) {
	switch {
	case strings.HasPrefix(model, "SM-S938") && device == "pa3q" && strings.EqualFold(soc, "SM8750"):
		return true, "Galaxy S25 Ultra"
	case strings.HasPrefix(model, "SM-S928") && device == "e3q" && strings.EqualFold(soc, "SM8650"):
		return true, "Galaxy S24 Ultra"
	}
	if model == "" {
		return false, "?"
	}
	return false, model
}

// pushFile copies a file to the phone with adb push, reporting progress from the file's size on the phone.
func pushFile(ctx context.Context, j *JobCtx, s Settings, serial, local, remote string) error {
	fi, err := os.Stat(local)
	if err != nil {
		return err
	}
	total := fi.Size()
	done := make(chan error, 1)
	go func() {
		_, code, err := runLines(ctx, nil, adbExe(s), adbArgs(serial, "push", local, remote)...)
		if err == nil && code != 0 {
			err = fmt.Errorf("adb push failed (exit %d)", code)
		}
		done <- err
	}()
	t := time.NewTicker(2 * time.Second)
	defer t.Stop()
	for {
		select {
		case err := <-done:
			if err == nil {
				j.Progress(1, human(total))
			}
			return err
		case <-t.C:
			lines, _, _ := runLines(ctx, nil, adbExe(s), adbArgs(serial, "shell", "stat -c %s "+shq(remote)+" 2>/dev/null")...)
			if len(lines) > 0 {
				if n, err := strconv.ParseInt(strings.TrimSpace(lines[0]), 10, 64); err == nil && total > 0 {
					j.Progress(float64(n)/float64(total), fmt.Sprintf("%s of %s", human(n), human(total)))
				}
			}
		}
	}
}
