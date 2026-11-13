package main

import (
	"bytes"
	"context"
	"fmt"
	"os"
	"os/exec"
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
	OS           string   `json:"os"`
	WSL          bool     `json:"wsl"`
	WSLMessage   string   `json:"wslMessage"`
	Distros      []string `json:"distros"`
	DistroReady  bool     `json:"distroReady"`
	FreeGB       int      `json:"freeGB"`
	MemGB        int      `json:"memGB"`
	CPUs         int      `json:"cpus"`
	BuildState   string   `json:"buildState"` // none, setup, partial, built
	DoneSteps    []string `json:"doneSteps"`
	ImageOnPC    string   `json:"imageOnPC"`
	ImageBytes   int64    `json:"imageBytes"`
	CheckedAt    int64    `json:"checkedAt"`
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
	lines, _, _ := inWSL(ctx, s.Distro, `df -Pk /root | awk 'NR==2{print "free " int($4/1048576)}'
awk '/MemTotal/{print "mem " int($2/1048576+0.5)}' /proc/meminfo
echo "cpus $(nproc)"
for f in packages references kernel-sources fetch kernel-inputs kernel prelink image; do [ -f `+wslWork+`/.done-$f ] && echo "done $f"; done
[ -d `+wslWork+`/OneiOS/.git ] && echo "setup yes"`)
	for _, l := range lines {
		f := strings.Fields(l)
		if len(f) < 2 {
			continue
		}
		switch f[0] {
		case "free":
			st.FreeGB, _ = strconv.Atoi(f[1])
		case "mem":
			st.MemGB, _ = strconv.Atoi(f[1])
		case "cpus":
			st.CPUs, _ = strconv.Atoi(f[1])
		case "done":
			st.DoneSteps = append(st.DoneSteps, f[1])
		case "setup":
			st.BuildState = "setup"
		}
	}
	for _, d := range st.DoneSteps {
		if d == "fetch" || d == "kernel" {
			st.BuildState = "partial"
		}
		if d == "image" {
			st.BuildState = "built"
		}
	}
	if fi, err := os.Stat(joinOut(s.OutDir, imageName)); err == nil {
		st.ImageOnPC, st.ImageBytes = joinOut(s.OutDir, imageName), fi.Size()
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

// installWSL asks Windows (with an administrator prompt) to install WSL and the Ubuntu the build uses.
func installWSL(ctx context.Context, j *JobCtx, s Settings) (string, error) {
	if runtime.GOOS != "windows" {
		return "", fmt.Errorf("installing WSL is for Windows")
	}
	j.Step("Asking Windows to install WSL and " + s.Distro + " (an administrator prompt appears)")
	ps := fmt.Sprintf(`Start-Process wsl.exe -ArgumentList '--install','-d','%s' -Verb RunAs -Wait`, s.Distro)
	code, err := stream(ctx, nil, j.Line, "powershell.exe", "-NoProfile", "-Command", ps)
	if err != nil && code != 0 {
		return "", fmt.Errorf("the installer did not run (%v)", err)
	}
	return "Windows is installing Ubuntu. If it asks, restart the PC; then open Ubuntu once from the Start menu to choose a username and password, and come back here.", nil
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
	inner := fmt.Sprintf("cd /root && setsid bash -c %s & wait $!", shq(fmt.Sprintf("echo $$ > %s; exec env %s bash %s %s",
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
	if err != nil || code != 0 {
		return "", fmt.Errorf("the build stopped (exit %d). Resume continues from the step that stopped", code)
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
