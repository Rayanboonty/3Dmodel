//go:build windows

package main

import (
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sync"
	"syscall"
	"time"

	webview2 "github.com/jchv/go-webview2"
)

// Studio's window: a real Windows window with Edge's WebView2 inside (the engine Windows' own apps use). Without the
// WebView2 runtime it opens as an Edge app window (no tabs, no address bar), and only as a last resort in the
// default browser.

func init() { runtime.LockOSThread() } // the window lives on the main thread

var (
	winMu  sync.Mutex
	mainWV webview2.WebView
)

var (
	user32                  = syscall.NewLazyDLL("user32.dll")
	procSetForegroundWindow = user32.NewProc("SetForegroundWindow")
	procShowWindow          = user32.NewProc("ShowWindow")
	procIsIconic            = user32.NewProc("IsIconic")
)

// runWindow shows the window and returns when it is closed; false when no window could be made.
func runWindow(url string) bool {
	w := webview2.NewWithOptions(webview2.WebViewOptions{
		DataPath:  filepath.Join(dataDir(), "webview"),
		AutoFocus: true,
		WindowOptions: webview2.WindowOptions{
			Title: "GalIOS Studio", Width: 1320, Height: 880, IconId: 1, Center: true,
		},
	})
	if w == nil {
		return edgeAppWindow(url)
	}
	winMu.Lock()
	mainWV = w
	winMu.Unlock()
	w.SetSize(980, 660, webview2.HintMin)
	w.Navigate(url)
	w.Run()
	w.Destroy()
	winMu.Lock()
	mainWV = nil
	winMu.Unlock()
	return true
}

// edgeAppWindow opens the page as an Edge app window and waits for it to close.
func edgeAppWindow(url string) bool {
	for _, edge := range []string{
		filepath.Join(os.Getenv("ProgramFiles(x86)"), `Microsoft\Edge\Application\msedge.exe`),
		filepath.Join(os.Getenv("ProgramFiles"), `Microsoft\Edge\Application\msedge.exe`),
	} {
		if !fileExists(edge) {
			continue
		}
		profile := filepath.Join(dataDir(), "edge-profile")
		c := exec.Command(edge, "--app="+url, "--user-data-dir="+profile, "--window-size=1320,880", "--no-first-run")
		if c.Start() == nil {
			start := time.Now()
			c.Wait()
			// Edge hands the window to an Edge already running for this profile and returns at once: then the
			// page's pings keep Studio alive instead (idleExit).
			return time.Since(start) > 5*time.Second
		}
	}
	return false
}

// onUI runs f on the window's thread (where Windows' dialogs belong) and waits for it.
func onUI(f func(owner uintptr)) {
	winMu.Lock()
	w := mainWV
	winMu.Unlock()
	if w == nil {
		done := make(chan struct{})
		go func() {
			runtime.LockOSThread()
			defer runtime.UnlockOSThread()
			coInit()
			f(0)
			close(done)
		}()
		<-done
		return
	}
	done := make(chan struct{})
	w.Dispatch(func() {
		f(uintptr(w.Window()))
		close(done)
	})
	<-done
}

// focusWindow brings Studio's window to the front (a second start of Studio asks for this).
func focusWindow() {
	winMu.Lock()
	w := mainWV
	winMu.Unlock()
	if w == nil {
		return
	}
	w.Dispatch(func() {
		h := uintptr(w.Window())
		if r, _, _ := procIsIconic.Call(h); r != 0 {
			procShowWindow.Call(h, 9) // SW_RESTORE
		}
		procSetForegroundWindow.Call(h)
	})
}

func hasWindow() bool { return true }
