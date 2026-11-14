package main

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"
	"time"
)

// The Ubuntu side of the build runs as root inside WSL: no password prompts, and the script's own steps
// (packages, tools, OneiOS's build) are the same ones a person would run by hand.

const (
	wslScript  = "/root/galios-build.sh"
	wslWork    = "/root/oneios-build"
	wslPidFile = "/root/.galios-build.pid"
	imageName  = "OneiOS_iOS27.0.1_24A446_iPhone18-2.tar.zst"
)

func wslExe() string {
	if p := os.Getenv("STUDIO_WSL"); p != "" {
		return p
	}
	return "wsl.exe"
}

type PCStatus struct {
	OS          string   `json:"os"`
	WSL         bool     `json:"wsl"`
	WSLMessage  string   `json:"wslMessage"`
	Distros     []string `json:"distros"`
	DistroReady bool     `json:"distroReady"`
	FreeGB      int      `json:"freeGB"`
	MemGB       int      `json:"memGB"`
	WSLMemGB    int      `json:"wslMemGB"`
	GPUPack     string   `json:"gpuPack"` // the GPU pack (Metal plug-in) the image was built with
	CPUs        int      `json:"cpus"`
	BuildState  string   `json:"buildState"` // none, setup, partial, built
	DoneSteps   []string `json:"doneSteps"`
	ImageOnPC   string   `json:"imageOnPC"`
	ImageBytes  int64    `json:"imageBytes"`
	CheckedAt   int64    `json:"checkedAt"`
}

func wslList(ctx context.Context) ([]string, error) {
	lines, code, err := runLines(ctx, nil, wslExe(), "-l", "-q")
	if err != nil && code != 0 {
		return nil, err
	}
	var out []string
	for _, l := range lines {
		if l = strings.TrimSpace(strings.Trim(l, "\x00")); l != "" {
			out = append(out, l)
		}
	}
	return out, nil
}

// inWSL runs a bash command as root in the build's Ubuntu and returns its output lines.
func inWSL(ctx context.Context, distro, script string) ([]string, int, error) {
	return runLines(ctx, nil, wslExe(), "-d", distro, "-u", "root", "--", "bash", "-c", script)
}

func checkPC(ctx context.Context, s Settings) PCStatus {
	st := PCStatus{OS: runtime.GOOS + "/" + runtime.GOARCH, CheckedAt: time.Now().Unix(), BuildState: "none"}
	// Ubuntu's disk grows on the Windows drive, so that drive's free space is what counts (not Ubuntu's df).
	st.FreeGB = int(wslDiskFree() >> 30)
	st.MemGB = hostMemGB()
	imageFile, imageSize := "", int64(0)
	if fi, err := os.Stat(joinOut(s.OutDir, imageName)); err == nil && !fi.IsDir() {
		imageFile, imageSize = joinOut(s.OutDir, imageName), fi.Size()
		if imageSize > 1<<30 { // a finished image is about 11 GB; a smaller file is a copy cut short
			st.ImageOnPC, st.ImageBytes, st.BuildState = imageFile, imageSize, "built"
		}
	}
	ds, err := wslList(ctx)
	if err != nil {
		st.WSLMessage = "WSL is not installed (or not turned on). Use Install Ubuntu."
		return st
	}
	st.WSL, st.Distros = true, ds
	for _, d := range ds {
		if strings.EqualFold(d, s.Distro) {
			st.DistroReady = true
		}
	}
	if !st.DistroReady {
		st.WSLMessage = fmt.Sprintf("%s is not installed in WSL. Use Install Ubuntu.", s.Distro)
		return st
	}
	lines, _, _ := inWSL(ctx, s.Distro, `awk '/MemTotal/{print "mem " int($2/1048576+0.5)}' /proc/meminfo
echo "cpus $(nproc)"
for f in packages references kernel-sources fetch kernel-inputs kernel prelink image; do [ -f `+wslWork+`/.done-$f ] && echo "done $f"; done
[ -d `+wslWork+`/OneiOS/.git ] && echo "setup yes"
[ -f `+wslWork+`/.gpu-pack ] && echo "gpu $(cat `+wslWork+`/.gpu-pack)"`)
	for _, l := range lines {
		f := strings.Fields(l)
		if len(f) < 2 {
			continue
		}
		switch f[0] {
		case "mem":
			if n, _ := strconv.Atoi(f[1]); runtime.GOOS != "windows" || n > 0 {
				st.WSLMemGB = n
			}
		case "cpus":
			st.CPUs, _ = strconv.Atoi(f[1])
		case "done":
			st.DoneSteps = append(st.DoneSteps, f[1])
		case "setup":
			st.BuildState = "setup"
		case "gpu":
			st.GPUPack = f[1]
		}
	}
	for _, d := range st.DoneSteps {
		if d == "image" && imageFile != "" { // the build says it finished and copied it
			st.ImageOnPC, st.ImageBytes, st.BuildState = imageFile, imageSize, "built"
		}
		if st.BuildState == "built" {
			break
		}
		if d == "fetch" || d == "kernel" || d == "kernel-inputs" || d == "prelink" {
			st.BuildState = "partial"
		}
	}
	return st
}

func joinOut(dir, name string) string {
	if strings.Contains(dir, `\`) {
		return strings.TrimRight(dir, `\`) + `\` + name
	}
	return strings.TrimRight(dir, "/") + "/" + name
}

var drivePath = regexp.MustCompile(`^([A-Za-z]):[\\/](.*)$`)

// toWSLPath turns C:\Users\x\Downloads into /mnt/c/Users/x/Downloads (WSL's default mount); other paths pass.
func toWSLPath(p string) string {
	if m := drivePath.FindStringSubmatch(p); m != nil {
		return "/mnt/" + strings.ToLower(m[1]) + "/" + strings.ReplaceAll(m[2], `\`, "/")
	}
	return p
}

// ensureWSL makes sure WSL and the build's Ubuntu are installed and working, installing them when needed. Ubuntu is
// registered with root as its user, so there is no username or password to choose. It reports when Windows must
// restart first (the first WSL install turns on a Windows feature).
func ensureWSL(ctx context.Context, j *JobCtx, s Settings) (restart bool, err error) {
	ready := func() bool {
		ds, err := wslList(ctx)
		if err != nil {
			return false
		}
		for _, d := range ds {
			if strings.EqualFold(d, s.Distro) {
				l, code, _ := inWSL(ctx, s.Distro, "echo galios-ok")
				return code == 0 && strings.Contains(strings.Join(l, " "), "galios-ok")
			}
		}
		return false
	}
	if ready() {
		return false, nil
	}
	if runtime.GOOS != "windows" {
		return false, fmt.Errorf("WSL is for Windows")
	}
	j.Step("Installing WSL and " + s.Distro + " (Windows asks for permission once)")
	j.Log("Windows downloads Ubuntu (about 500 MB). This window may look still for a few minutes.")
	ps := fmt.Sprintf(`$p = Start-Process wsl.exe -ArgumentList '--install','-d','%s','--no-launch','--web-download' -Verb RunAs -Wait -PassThru -WindowStyle Hidden; exit $p.ExitCode`, s.Distro)
	code, err := stream(ctx, nil, j.Line, "powershell.exe", "-NoProfile", "-NonInteractive", "-Command", ps)
	if err != nil && code != 0 && code != 1 {
		if strings.Contains(err.Error(), "canceled") || code == -1 {
			return false, fmt.Errorf("Windows' permission prompt was declined: press Do everything again and choose Yes")
		}
	}
	j.Log("wsl --install finished (exit %d)", code)
	// An Ubuntu from the Store is registered by its own launcher; --root makes root its user (no questions).
	if !ready() {
		launcher := strings.ToLower(strings.ReplaceAll(strings.ReplaceAll(s.Distro, "-", ""), ".", "")) + ".exe" // ubuntu2404.exe
		j.Log("registering %s", s.Distro)
		cctx, cancel := context.WithTimeout(ctx, 15*time.Minute)
		stream(cctx, nil, j.Line, launcher, "install", "--root")
		cancel()
	}
	if ready() {
		return false, nil
	}
	// WSL itself was just turned on: Windows must restart before Ubuntu can run.
	return true, nil
}

// ensureWSLConfig gives Ubuntu enough memory for the kernel build (only when the user has no .wslconfig yet).
func ensureWSLConfig(ctx context.Context, j *JobCtx) {
	if runtime.GOOS != "windows" {
		return
	}
	home, _ := os.UserHomeDir()
	p := filepath.Join(home, ".wslconfig")
	if fileExists(p) {
		return
	}
	ram := hostMemGB()
	mem := ram * 3 / 4
	if mem < 4 {
		mem = 4
	}
	text := fmt.Sprintf("# Written by GalIOS Studio for the iOS image build: more memory and swap for Ubuntu.\r\n[wsl2]\r\nmemory=%dGB\r\nswap=16GB\r\n", mem)
	if os.WriteFile(p, []byte(text), 0o644) == nil {
		j.Log("gave Ubuntu %d GB of memory and 16 GB of swap (%s)", mem, p)
		runLines(ctx, nil, wslExe(), "--shutdown")
	}
}

func openTerminal(s Settings) error {
	if runtime.GOOS != "windows" {
		return fmt.Errorf("opening a terminal is for Windows")
	}
	c := exec.Command("cmd.exe", "/c", "start", "", wslExe(), "-d", s.Distro, "--cd", "~")
	return c.Start()
}

// putBuildScript writes Studio's copy of the build script into WSL.
func putBuildScript(ctx context.Context, s Settings, script []byte) error {
	_, code, err := runLines(ctx, bytes.NewReader(script), wslExe(), "-d", s.Distro, "-u", "root", "--",
		"bash", "-c", "cat > "+wslScript+".new && chmod 755 "+wslScript+".new && mv -f "+wslScript+".new "+wslScript)
	if err != nil || code != 0 {
		return fmt.Errorf("could not copy the build script into %s (exit %d): %v", s.Distro, code, err)
	}
	return nil
}

// buildImage runs the build in WSL: mode is "setup" (tools only), "ipsw" (from s.IPSW) or "download" (from Apple).
func buildImage(ctx context.Context, j *JobCtx, s Settings, mode string, script []byte) (string, error) {
	j.Step("Preparing " + s.Distro)
	stopBuild(ctx, s) // a build left running by an earlier Studio window: this one resumes it instead
	if err := putBuildScript(ctx, s, script); err != nil {
		return "", err
	}
	var arg string
	switch mode {
	case "setup":
		arg = "--setup-only"
	case "download":
		arg = "--download"
	default:
		if strings.TrimSpace(s.IPSW) == "" {
			return "", fmt.Errorf("choose your .ipsw first, or use Download from Apple")
		}
		arg = s.IPSW
	}
	env := []string{"GALIOS_WORK=" + wslWork, "GALIOS_OUT_DIR=" + toWSLPath(s.OutDir), "GALIOS_PROGRESS=/root/.galios-progress"}
	if s.GitHubToken != "" {
		env = append(env, "GITHUB_TOKEN="+s.GitHubToken)
	}
	// setsid: the build gets its own process group, whose id is written down so Stop can end all of it.
	// (quoted as one word, so folders with spaces or quotes in their names pass through intact)
	// setsid --wait: without it setsid may fork and return 0 at once, and the build's exit code is lost.
	inner := fmt.Sprintf("cd /root && rm -f /root/.galios-progress && setsid --wait bash -c %s & wait $!", shq(fmt.Sprintf("echo $$ > %s; exec env %s bash %s %s",
		wslPidFile, strings.Join(quoteAll(env), " "), wslScript, shq(arg))))
	steps := []string{}
	code, err := stream(ctx, nil, func(l string) {
		j.Line(l)
		if strings.HasPrefix(l, "==> ") {
			step := strings.TrimPrefix(l, "==> ")
			steps = append(steps, step)
			j.Progress(-1, "")
			jh := j.h
			jh.update(func(x *Job) { x.Step = step })
		}
	}, wslExe(), "-d", s.Distro, "-u", "root", "--", "bash", "-c", inner)
	if ctx.Err() != nil {
		stopBuild(context.Background(), s)
		return "", ctx.Err()
	}
	// The script writes "done" or "failed: <step>" to its progress file: that, not only the exit code, is the result.
	pl, _, _ := inWSL(context.Background(), s.Distro, "cat /root/.galios-progress 2>/dev/null")
	progress := strings.TrimSpace(strings.Join(pl, " "))
	if err != nil || code != 0 || progress != "done" {
		if strings.HasPrefix(progress, "failed") {
			return "", fmt.Errorf("the build stopped (%s). Press the button again to continue from that step; if it stops at the same place, send the Logs page", strings.TrimSpace(strings.TrimPrefix(progress, "failed:")))
		}
		return "", fmt.Errorf("the build stopped (exit %d). Press the button again to continue from the step that stopped", code)
	}
	if mode != "setup" && !fileExists(joinOut(s.OutDir, imageName)) {
		return "", fmt.Errorf("the build says it finished, but %s is not in %s", imageName, s.OutDir)
	}
	switch mode {
	case "setup":
		return "The build tools are ready.", nil
	default:
		return "Your iOS 27 image is built: " + joinOut(s.OutDir, imageName), nil
	}
}

func quoteAll(xs []string) []string {
	out := make([]string, len(xs))
	for i, x := range xs {
		out[i] = shq(x)
	}
	return out
}

// stopBuild ends the build's whole process group in WSL.
func stopBuild(ctx context.Context, s Settings) {
	_, _, _ = inWSL(ctx, s.Distro, fmt.Sprintf(`p=$(cat %[1]s 2>/dev/null); [ -n "$p" ] || exit 0
kill -TERM -"$p" 2>/dev/null; for i in 1 2 3 4 5 6 7 8 9 10; do kill -0 -"$p" 2>/dev/null || break; sleep 1; done
kill -KILL -"$p" 2>/dev/null; rm -f %[1]s`, wslPidFile))
}

func verifyImage(ctx context.Context, j *JobCtx, s Settings) (string, error) {
	j.Step("Checking the image set (every file's SHA-256)")
	code, err := stream(ctx, nil, j.Line, wslExe(), "-d", s.Distro, "-u", "root", "--", "bash", "-c",
		"cd "+wslWork+"/OneiOS && scripts/image/verify.sh out/image/"+imageName)
	if err != nil || code != 0 {
		return "", fmt.Errorf("the check failed (exit %d)", code)
	}
	return "The image checks out.", nil
}

// cleanBuild removes the large intermediate files (the IPSW's decrypted parts and the image folder); the finished
// archive in the output folder is kept. The next build downloads or reads the IPSW again.
func cleanBuild(ctx context.Context, j *JobCtx, s Settings) (string, error) {
	j.Step("Removing the build's large files from " + s.Distro)
	lines, code, err := inWSL(ctx, s.Distro, `cd `+wslWork+` 2>/dev/null || exit 0
before=$(du -sk . 2>/dev/null | cut -f1)
rm -rf OneiOS/inputs OneiOS/out/image *.ipsw .done-fetch .done-image
after=$(du -sk . 2>/dev/null | cut -f1)
echo "freed $(( (before - after) / 1048576 )) GB"`)
	for _, l := range lines {
		j.Line(l)
	}
	if err != nil || code != 0 {
		return "", fmt.Errorf("could not clean (exit %d)", code)
	}
	return "Build files removed. Tools and the kernel are kept.", nil
}

func openFolder(p string) error {
	switch runtime.GOOS {
	case "windows":
		return exec.Command("explorer.exe", p).Start()
	case "darwin":
		return exec.Command("open", p).Start()
	default:
		return exec.Command("xdg-open", p).Start()
	}
}
