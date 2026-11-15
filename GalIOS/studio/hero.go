package main

// The owner's picture for the Boot card: a file they choose on this PC, copied into Studio's data folder and shown in
// place of the drawing. It stays on this PC: nothing of it is in Studio, its repository or its releases.

import (
	"context"
	"fmt"
	"strings"
	"io"
	"net/http"
	"os"
	"path/filepath"
)

func heroPath() string { return filepath.Join(dataDir(), "hero-picture") }

// heroStamp is the saved picture's modification time (the page's cache key), 0 when there is none.
func heroStamp() int64 {
	if fi, err := os.Stat(heroPath()); err == nil && fi.Size() > 0 {
		return fi.ModTime().UnixNano()
	}
	return 0
}

// isPicture says whether b starts as a PNG, a JPEG or a WebP file does.
func isPicture(b []byte) bool {
	switch {
	case len(b) >= 8 && string(b[:8]) == "\x89PNG\r\n\x1a\n":
		return true
	case len(b) >= 3 && b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF:
		return true
	case len(b) >= 12 && string(b[:4]) == "RIFF" && string(b[8:12]) == "WEBP":
		return true
	}
	return false
}

// saveHero copies src to the picture's place (via a .tmp file) after checking it is a picture of at most 20 MB.
func saveHero(src, dest string) error {
	f, err := os.Open(src)
	if err != nil {
		return err
	}
	defer f.Close()
	fi, err := f.Stat()
	if err != nil {
		return err
	}
	if fi.Size() > 20<<20 {
		return fmt.Errorf("that picture is over 20 MB: choose a smaller one")
	}
	head := make([]byte, 12)
	n, _ := io.ReadFull(f, head)
	if !isPicture(head[:n]) {
		return fmt.Errorf("that file is not a PNG, JPEG or WebP picture")
	}
	if _, err := f.Seek(0, io.SeekStart); err != nil {
		return err
	}
	os.MkdirAll(filepath.Dir(dest), 0o755)
	tmp := dest + ".tmp"
	out, err := os.Create(tmp)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, f); err != nil {
		out.Close()
		os.Remove(tmp)
		return err
	}
	if err := out.Close(); err != nil {
		os.Remove(tmp)
		return err
	}
	return os.Rename(tmp, dest)
}

// The GalaxyIOS app keeps its Boot-card picture in its own storage under this name (Controller.heroFile).
const (
	phoneAppDir   = "/data/data/dev.galios"
	phoneHeroTemp = "/data/local/tmp/galios-hero.tmp"
)

// heroToPhoneScript (run as root) puts the picture pushed to phoneHeroTemp in the GalaxyIOS app's storage, owned by
// the app and with its storage's SELinux label, so the app shows it on its Boot card the next time it refreshes.
func heroToPhoneScript() string {
	return fmt.Sprintf(`app=%[1]s; src=%[2]s; dst=$app/files/hero-picture
[ -d "$app" ] || { rm -f "$src"; echo '@@FAILED GalaxyIOS is not installed on the phone'; exit 4; }
[ -s "$src" ] || { echo '@@FAILED the picture did not reach the phone'; exit 4; }
u=$(stat -c %%u "$app"); label=$(ls -Zd "$app" | awk '{print $1}')
mkdir -p "$app/files" && chown "$u:$u" "$app/files" && chmod 0771 "$app/files"
cp "$src" "$dst.tmp" && chown "$u:$u" "$dst.tmp" && chmod 0600 "$dst.tmp" || { rm -f "$src" "$dst.tmp"; echo '@@FAILED could not copy the picture'; exit 4; }
[ -n "$label" ] && { chcon "$label" "$app/files" 2>/dev/null; chcon "$label" "$dst.tmp" 2>/dev/null; }
mv -f "$dst.tmp" "$dst"; rm -f "$src"
echo '@@OK picture'`, phoneAppDir, phoneHeroTemp)
}

// pushHeroToPhone sends the Boot-card picture chosen in Studio to the GalaxyIOS app on the phone (needs root).
func pushHeroToPhone(ctx context.Context, j *JobCtx, s Settings, serial string) error {
	if heroStamp() == 0 {
		return fmt.Errorf("no picture chosen in Studio yet: click the phone on Studio's Boot card first")
	}
	if err := pushFile(ctx, j, s, serial, heroPath(), phoneHeroTemp); err != nil {
		return err
	}
	lines, code, err := rootRun(ctx, s, serial, heroToPhoneScript())
	if err != nil {
		return err
	}
	for i := len(lines) - 1; i >= 0; i-- {
		l := strings.TrimSpace(lines[i])
		if strings.HasPrefix(l, "@@OK") {
			return nil
		}
		if strings.HasPrefix(l, "@@FAILED ") {
			return fmt.Errorf("%s", strings.TrimPrefix(l, "@@FAILED "))
		}
	}
	return fmt.Errorf("no answer from root (exit %d)", code)
}

func handleHero(w http.ResponseWriter, r *http.Request) {
	if heroStamp() == 0 {
		http.NotFound(w, r)
		return
	}
	http.ServeFile(w, r, heroPath())
}
