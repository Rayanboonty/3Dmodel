package main

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

// Root on the phone, found by itself. Temporary root apps differ in where their su lives and in whether they let
// adb's shell use it, so Studio tries each usual su over USB and, when none answers, asks GalIOS (which a root app
// grants like any other app) to run the command with its root: the GalIOS bridge (RootBridge.kt in the app).
//
// Nothing is ever piped into a root command: every script and every file is first copied to the phone with adb
// (a plain file copy, the most reliable thing adb does), and root reads it from there.

const bridgeDir = "/data/local/tmp/galios-bridge"

type Root struct {
	Via    string // "su" (over USB) or "galios" (the GalIOS bridge)
	Cmd    string // for "su": how su is started, e.g. "su -c"
	Detail string // for the UI
}

var (
	rootMu    sync.Mutex
	rootCache = map[string]Root{}
)

func forgetRoot(serial string) {
	rootMu.Lock()
	delete(rootCache, serial)
	rootMu.Unlock()
}

// suCandidates are the ways su is usually started; the first is the one most root apps use.
var suCandidates = []string{
	"su -c", "/system/bin/su -c", "/system/xbin/su -c", "/data/adb/ksu/bin/su -c", "/data/adb/ap/bin/su -c",
	"/sbin/su -c", "/debug_ramdisk/su -c", "/data/local/tmp/su -c", "/su/bin/su -c", "su 0 sh -c", "su root sh -c",
}

// getRoot finds a working root for this phone once and keeps it for the session.
func getRoot(ctx context.Context, s Settings, serial string) (Root, error) {
	rootMu.Lock()
	r, ok := rootCache[serial]
	rootMu.Unlock()
	if ok {
		return r, nil
	}
	r, err := detectRoot(ctx, s, serial, nil)
	if err == nil {
		rootMu.Lock()
		rootCache[serial] = r
		rootMu.Unlock()
		if r.Via == "su" {
			go startRootHelper(context.Background(), nil, s, serial)
		}
	}
	return r, err
}

const galiosQueue = "/storage/emulated/0/Android/data/dev.galios/files/rootq"

// startRootHelper lends root to the GalIOS app when root works over USB but not for apps (some temporary roots):
// it starts galios-rootd.sh as root, detached, which runs what GalIOS queues in its own folder until the phone
// restarts. GalIOS uses it by itself when its own su gives no root. Quietly does nothing when it cannot.
func startRootHelper(ctx context.Context, j *JobCtx, s Settings, serial string) {
	say := func(f string, a ...any) {
		if j != nil {
			j.Log(f, a...)
		}
	}
	rootMu.Lock()
	r, ok := rootCache[serial]
	rootMu.Unlock()
	if !ok || r.Via != "su" {
		return
	}
	if packageVersionCode(ctx, s, serial, "dev.galios") < bundledGalIOSCode {
		say("installing GalIOS %s on the phone", bundledGalIOSVersion)
		if err := installGalIOS(ctx, j, s, serial); err != nil {
			say("GalIOS could not be installed: %v", err)
			return
		}
	}
	// GalIOS makes its queue folder when it starts: open it once if the folder is not there yet.
	hasQ := func() bool {
		l, _, _ := runLines(ctx, nil, adbExe(s), adbArgs(serial, "shell", "[ -d "+galiosQueue+" ] && echo yes")...)
		return strings.Contains(strings.Join(l, " "), "yes")
	}
	if !hasQ() {
		runLines(ctx, nil, adbExe(s), adbArgs(serial, "shell", "am start -n dev.galios/.MainActivity")...)
		for i := 0; i < 20 && !hasQ(); i++ {
			time.Sleep(500 * time.Millisecond)
		}
	}
	script, err := assetFS.ReadFile("assets/galios-rootd.sh")
	if err != nil || len(script) == 0 {
		return
	}
	remote, err := pushBytes(ctx, s, serial, "galios-rootd.sh", script)
	if err != nil {
		say("root helper: %v", err)
		return
	}
	// One helper at a time: a running one is left alone.
	l, _, _ := rootRun(ctx, s, serial, `p=$(cat `+bridgeDir+`/rootd.pid 2>/dev/null)
if [ -n "$p" ] && kill -0 "$p" 2>/dev/null; then echo running; exit 0; fi
setsid sh `+remote+` </dev/null >/dev/null 2>&1 &
echo started`)
	say("root helper for GalIOS: %s", strings.TrimSpace(strings.Join(l, " ")))
}

func detectRoot(ctx context.Context, s Settings, serial string, j *JobCtx) (Root, error) {
	say := func(f string, a ...any) {
		if j != nil {
			j.Log(f, a...)
		}
	}
	// adb itself may already be root.
	if l, _, _ := runLines(ctx, nil, adbExe(s), adbArgs(serial, "shell", "id -u")...); len(l) > 0 && strings.TrimSpace(l[len(l)-1]) == "0" {
		return Root{Via: "su", Cmd: "sh -c", Detail: "adb is root"}, nil
	}
	mode := strings.ToLower(strings.TrimSpace(s.RootCmd))
	cands := suCandidates
	switch mode {
	case "", "auto":
	case "galios":
		cands = nil
	default:
		cands = append([]string{s.RootCmd}, suCandidates...)
	}
	for i, c := range cands {
		wait := 8 * time.Second
		if i == 0 {
			wait = 25 * time.Second // the first one may show a Grant prompt on the phone
		}
		cctx, cancel := context.WithTimeout(ctx, wait)
		l, _, _ := runLines(cctx, nil, adbExe(s), adbArgs(serial, "shell", "-T", c+" "+shq("echo galios-root-$(id -u)"))...)
		cancel()
		if strings.Contains(strings.Join(l, "\n"), "galios-root-0") {
			say("root over USB: %s", c)
			return Root{Via: "su", Cmd: c, Detail: "su over USB (" + strings.TrimSuffix(c, " -c") + ")"}, nil
		}
	}
	// The GalIOS bridge.
	say("su does not answer over USB; trying root through GalIOS")
	if v := packageVersionCode(ctx, s, serial, "dev.galios"); v < bundledGalIOSCode {
		say("installing GalIOS %s (its root bridge)", bundledGalIOSVersion)
		if err := installGalIOS(ctx, nil, s, serial); err != nil {
			return Root{}, fmt.Errorf("root: su does not answer over USB, and GalIOS could not be installed: %v", err)
		}
	}
	rc, msg := bridgeCall(ctx, s, serial, "ping", "")
	if rc == 0 {
		return Root{Via: "galios", Detail: "through GalIOS"}, nil
	}
	return Root{}, fmt.Errorf("root is not reachable: su does not answer over USB, and GalIOS has no root yet (%s). "+
		"Start your temporary root, then in your root app allow GalIOS (and Shell), or tap Grant on the phone, and press Check again", msg)
}

var bundleInt = regexp.MustCompile(`\b(rc|code)=(-?\d+)`)
var bundleMsg = regexp.MustCompile(`\bmsg=([^,}]*)`)

// bridgeCall asks GalIOS's RootBridge to do something; it answers with rc (0 = fine) and a message.
func bridgeCall(ctx context.Context, s Settings, serial, method, arg string) (int, string) {
	args := []string{"shell", "content", "call", "--uri", "content://dev.galios.bridge", "--method", method}
	if arg != "" {
		args = append(args, "--arg", arg)
	}
	cctx, cancel := context.WithTimeout(ctx, 90*time.Second)
	defer cancel()
	l, _, err := runLines(cctx, nil, adbExe(s), adbArgs(serial, args...)...)
	text := strings.Join(l, " ")
	m := bundleInt.FindStringSubmatch(text)
	if m == nil {
		if err != nil && text == "" {
			text = err.Error()
		}
		return -1, "GalIOS did not answer: " + strings.TrimSpace(text)
	}
	rc, _ := strconv.Atoi(m[2])
	msg := ""
	if mm := bundleMsg.FindStringSubmatch(text); mm != nil {
		msg = strings.TrimSpace(mm[1])
	}
	return rc, msg
}

func newID() string {
	b := make([]byte, 6)
	rand.Read(b)
	return "j" + strconv.FormatInt(time.Now().Unix(), 36) + "-" + hex.EncodeToString(b)
}

// phoneDir makes the phone folder Studio copies scripts and files into (adb's shell owns it; root reads it).
func phoneDir(ctx context.Context, s Settings, serial string) error {
	_, code, err := runLines(ctx, nil, adbExe(s), adbArgs(serial, "shell", "mkdir -p "+bridgeDir+" && chmod 0777 "+bridgeDir)...)
	if err != nil || code != 0 {
		return fmt.Errorf("could not use %s on the phone (exit %d): is the phone still connected?", bridgeDir, code)
	}
	return nil
}

// pushBytes copies small data to the phone as a file and returns its path there.
func pushBytes(ctx context.Context, s Settings, serial, name string, b []byte) (string, error) {
	if err := phoneDir(ctx, s, serial); err != nil {
		return "", err
	}
	tmpDir := filepath.Join(dataDir(), "tmp")
	os.MkdirAll(tmpDir, 0o755)
	local := filepath.Join(tmpDir, name)
	if err := os.WriteFile(local, b, 0o644); err != nil {
		return "", err
	}
	defer os.Remove(local)
	remote := bridgeDir + "/" + name
	l, code, err := runLines(ctx, nil, adbExe(s), adbArgs(serial, "push", local, remote)...)
	if err != nil || code != 0 {
		return "", fmt.Errorf("adb push failed (exit %d): %s", code, strings.Join(l, " "))
	}
	return remote, nil
}

// rootRun runs a shell script as root on the phone and returns its output lines and exit code.
func rootRun(ctx context.Context, s Settings, serial, script string) ([]string, int, error) {
	r, err := getRoot(ctx, s, serial)
	if err != nil {
		return nil, -1, err
	}
	id := newID()
	remote, err := pushBytes(ctx, s, serial, id+".sh", []byte(script+"\n"))
	if err != nil {
		return nil, -1, err
	}
	if r.Via == "su" {
		defer runLines(context.Background(), nil, adbExe(s), adbArgs(serial, "shell", "rm -f "+remote)...)
		return runLines(ctx, nil, adbExe(s), adbArgs(serial, "shell", "-T", r.Cmd+" "+shq("sh "+remote+" </dev/null"))...)
	}
	// Through GalIOS: started detached; its exit code arrives in <id>.rc.
	if rc, msg := bridgeCall(ctx, s, serial, "run", id); rc != 0 {
		forgetRoot(serial)
		return nil, -1, fmt.Errorf("GalIOS could not start it as root: %s", msg)
	}
	base := bridgeDir + "/" + id
	wait := 300 * time.Millisecond
	for {
		select {
		case <-ctx.Done():
			return nil, -1, ctx.Err()
		case <-time.After(wait):
		}
		if wait < 1500*time.Millisecond {
			wait += 200 * time.Millisecond
		}
		l, _, _ := runLines(ctx, nil, adbExe(s), adbArgs(serial, "shell", "cat "+base+".rc 2>/dev/null")...)
		if len(l) == 0 {
			continue
		}
		code, perr := strconv.Atoi(strings.TrimSpace(l[len(l)-1]))
		if perr != nil {
			continue
		}
		out, _, _ := runLines(ctx, nil, adbExe(s), adbArgs(serial, "shell", "cat "+base+".out 2>/dev/null")...)
		runLines(context.Background(), nil, adbExe(s), adbArgs(serial, "shell", "rm -f "+base+".sh "+base+".out "+base+".rc")...)
		return out, code, nil
	}
}
