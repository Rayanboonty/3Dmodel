package main

import (
	"context"
	"fmt"
	"os"
	"os/exec"
	"runtime"
	"time"
)

// The steps Studio's buttons run, and "Do everything", which runs them in order: each one is skipped when its work
// is already done, and the ones that need you (plug in the phone, start root) wait for you instead of failing.

func (a *App) setEngine(e *Engine) {
	a.mu.Lock()
	a.engine = e
	a.mu.Unlock()
}

func stepEngine(ctx context.Context, j *JobCtx, s Settings, serial string) (string, error) {
	e, err := pullEngine(ctx, j, s, serial)
	if err != nil {
		return "", err
	}
	return installEngine(ctx, j, s, serial, e)
}

func stepRuntime(ctx context.Context, j *JobCtx, s Settings, serial string) (string, error) {
	p, err := latestRuntime(ctx, j)
	if err != nil {
		return "", err
	}
	return importArchive(ctx, j, s, serial, "runtime", p)
}

func stepImage(ctx context.Context, j *JobCtx, s Settings, serial, local string) (string, error) {
	if local == "" {
		local = joinOut(s.OutDir, imageName)
	}
	if !fileExists(local) {
		return "", fmt.Errorf("no image at %s yet: build it first (Build image, or Do everything)", local)
	}
	return importArchive(ctx, j, s, serial, "image", local)
}

func stepBoot(ctx context.Context, j *JobCtx, s Settings, serial string) (string, error) {
	e, err := pullEngine(ctx, j, s, serial)
	if err != nil {
		return "", err
	}
	j.Step("Staging the session scripts again (S25 Ultra check adapted)")
	if _, err := stageScripts(ctx, j, s, serial, e); err != nil {
		return "", err
	}
	j.Step("Starting iOS: One UI stops in about 40 seconds")
	r := verb(ctx, s, serial, "", "switch", "1", "1")
	if !r.OK {
		return "", fmt.Errorf("%s", r.Message)
	}
	return "iOS is starting. To come back: hold Volume Up + Volume Down for 3 seconds, or press Return to One UI here.", nil
}

// sleepCtx waits, or returns false when the task was stopped.
func sleepCtx(ctx context.Context, d time.Duration) bool {
	select {
	case <-ctx.Done():
		return false
	case <-time.After(d):
		return true
	}
}

// waitPhone waits until one phone is connected and allowed, telling you what it waits for.
func waitPhone(ctx context.Context, j *JobCtx, s Settings) (string, error) {
	last := ""
	for {
		serial, err := pickSerial(ctx, s)
		if err == nil {
			return serial, nil
		}
		if ctx.Err() != nil {
			return "", ctx.Err()
		}
		msg := "Waiting for the phone: plug it in by USB, with USB debugging on (" + err.Error() + ")"
		if msg != last {
			j.Step(msg)
			last = msg
		}
		if !sleepCtx(ctx, 3*time.Second) {
			return "", ctx.Err()
		}
	}
}

// waitRoot waits until root answers (over USB or through GalIOS).
func waitRoot(ctx context.Context, j *JobCtx, s Settings, serial string) (Root, error) {
	last := ""
	for {
		forgetRoot(serial)
		r, err := detectRoot(ctx, s, serial, j)
		if err == nil {
			rootMu.Lock()
			rootCache[serial] = r
			rootMu.Unlock()
			j.Log("root: %s", r.Detail)
			startRootHelper(ctx, j, s, serial) // lends root to GalIOS on the phone when only USB has it
			return r, nil
		}
		if ctx.Err() != nil {
			return Root{}, ctx.Err()
		}
		msg := "Waiting for root: start your temporary root on the phone, and allow GalIOS in your root app"
		if msg != last {
			j.Step(msg)
			j.Log("%v", err)
			last = msg
		}
		if !sleepCtx(ctx, 10*time.Second) {
			return Root{}, ctx.Err()
		}
	}
}

// autoAll is "Do everything": this PC, the image, the phone, until the boot check passes.
func (a *App) autoAll(ctx context.Context, j *JobCtx, script []byte) (string, error) {
	s := a.store.Get()
	head := func(t string) { j.Log("━━ %s", t) }

	// 1. WSL and Ubuntu.
	head("This PC")
	// The build runs every time: with nothing new it only checks (a minute), and it picks up a newer GPU pack
	// (the Metal plug-in built on macOS), which makes a new image.
	built := checkPC(ctx, s).BuildState == "built"
	if msg, stop, err := a.autoBuild(ctx, j, s, script, built); err != nil {
		if !built {
			return "", err
		}
		j.Log("the build could not run (%v): using the image already built", err)
	} else if stop {
		return msg, nil
	}

	// 3. The phone.
	head("The phone")
	serial, err := waitPhone(ctx, j, s)
	if err != nil {
		return "", err
	}
	ph := checkPhone(ctx, s)
	j.Log("phone: %s (%s, %s, %s)", ph.Label, ph.Model, ph.Device, ph.SoC)
	if !ph.Supported {
		return "", fmt.Errorf("this phone is %s: iOS 27 here is for the Galaxy S25 Ultra (SM-S938, pa3q, SM8750) and S24 Ultra", ph.Model)
	}
	if _, err := waitRoot(ctx, j, s, serial); err != nil {
		return "", err
	}
	for packageVersion(ctx, s, serial, oneosPackage) == "" {
		j.Step("Waiting for OneOS (iOS 27 edition) on the phone: install it, and Studio carries on by itself")
		if !sleepCtx(ctx, 10*time.Second) {
			return "", ctx.Err()
		}
	}
	head("Boot engine")
	msg, err := stepEngine(ctx, j, s, serial)
	if err != nil {
		return "", err
	}
	j.Log("%s", msg)
	st := stageStatus(ctx, s, serial)
	head("OneiOS runtime")
	if rt, rerr := latestRuntime(ctx, j); rerr != nil {
		if st.Runtime == "" {
			return "", rerr
		}
		j.Log("could not look up a newer runtime (%v): keeping %s", rerr, st.Runtime)
	} else if want := runtimeVersion(rt); want != "" && st.Runtime == want {
		j.Log("already on the phone: %s", st.Runtime)
	} else {
		if st.Runtime != "" {
			j.Log("the phone has runtime %s; putting %s in its place", st.Runtime, want)
		}
		if msg, err = importArchive(ctx, j, s, serial, "runtime", rt); err != nil {
			return "", err
		}
		j.Log("%s", msg)
	}
	head("iOS 27 image")
	local := joinOut(s.OutDir, imageName)
	if st.Image == "27.0.1 (24A446)" && a.store.Get().LastImage == imageIdentity(local) {
		j.Log("already on the phone: iOS %s (this image)", st.Image)
	} else {
		if st.Image != "" {
			j.Log("the image on this PC is newer than the one on the phone (a GPU pack or a rebuild): copying it again")
		}
		if msg, err = stepImage(ctx, j, s, serial, ""); err != nil {
			return "", err
		}
		a.markImported(local)
		j.Log("%s", msg)
	}
	head("Boot check")
	st = stageStatus(ctx, s, serial)
	if !st.Ready {
		return "", fmt.Errorf("everything is on the phone, but OneOS's boot check says: %s", orNone(st.Reason))
	}
	return "Everything is ready. Press Boot iOS 27.", nil
}

// setAutoResume makes Studio open by itself after the next Windows sign-in and carry on (or stops that).
func setAutoResume(on bool) {
	if runtime.GOOS != "windows" {
		return
	}
	key := `HKCU\Software\Microsoft\Windows\CurrentVersion\RunOnce`
	if on {
		exe, err := os.Executable()
		if err != nil {
			return
		}
		c := exec.Command("reg.exe", "add", key, "/v", "GalIOS Studio", "/t", "REG_SZ", "/d", `"`+exe+`" --resume`, "/f")
		hideWindow(c)
		c.Run()
		return
	}
	c := exec.Command("reg.exe", "delete", key, "/v", "GalIOS Studio", "/f")
	hideWindow(c)
	c.Run()
}

func restartPC() error {
	if runtime.GOOS != "windows" {
		return fmt.Errorf("restarting is for Windows")
	}
	c := exec.Command("shutdown.exe", "/r", "/t", "5", "/c", "GalIOS Studio: restarting to finish installing WSL")
	hideWindow(c)
	return c.Run()
}

// imageIdentity names one build of the image file (its size and time), so a rebuilt image is copied again.
func imageIdentity(path string) string {
	fi, err := os.Stat(path)
	if err != nil {
		return ""
	}
	return fmt.Sprintf("%d-%d", fi.Size(), fi.ModTime().Unix())
}

func (a *App) markImported(path string) {
	s := a.store.Get()
	s.LastImage = imageIdentity(path)
	a.store.Set(s)
}

// autoBuild is Do everything's PC part: WSL and Ubuntu, then the image (built, or checked and brought up to date).
// stop is true when Windows must restart first; msg then says so.
func (a *App) autoBuild(ctx context.Context, j *JobCtx, s Settings, script []byte, built bool) (msg string, stop bool, err error) {
	restart, err := ensureWSL(ctx, j, s)
	if err != nil {
		return "", false, err
	}
	if restart {
		setAutoResume(true)
		a.restartNeeded.Store(true)
		return "Windows needs to restart to finish installing WSL. Press Restart the PC: Studio opens by itself " +
			"afterwards and carries on.", true, nil
	}
	setAutoResume(false)
	j.Log("WSL and %s are ready", s.Distro)
	ensureWSLConfig(ctx, j)

	j.Log("━━ The iOS 27 image")
	pc := checkPC(ctx, s)
	need := 75
	switch {
	case built:
		need = 20 // at most a new image with a new GPU pack
	case pc.BuildState == "partial":
		need = 30
	}
	if pc.FreeGB > 0 && pc.FreeGB < need {
		return "", false, fmt.Errorf("only %d GB free on the drive where Ubuntu lives; the build needs about %d GB more. "+
			"Free some space (empty the Recycle Bin, move big files), then press Do everything again", pc.FreeGB, need)
	}
	mode := "download"
	if ipsw, notes := bestIPSW(s); ipsw != "" {
		j.Log("using the .ipsw you chose: %s", ipsw)
		mode = "ipsw"
	} else {
		for _, n := range notes {
			j.Log("%s", n)
		}
		if !built {
			j.Log("no .ipsw chosen: the build reads the parts it needs straight from Apple (once; later runs reuse them)")
		}
	}
	if _, err := buildImage(ctx, j, s, mode, script); err != nil {
		return "", false, err
	}
	if !fileExists(joinOut(s.OutDir, imageName)) {
		return "", false, fmt.Errorf("the build finished but %s is not in %s", imageName, s.OutDir)
	}
	return "", false, nil
}
