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
	phoneHeroTemp = "/data/local/tmp/galios-picture.tmp"
)

// cardVersions are the iOS versions with a card on GalaxyIOS's Boot tab; each can have the owner's own wallpaper.
var cardVersions = []string{"16", "18", "26", "27"}

// wallpaperPath is the owner's wallpaper for an iOS card, chosen on this PC (it stays on this PC and the phone).
func wallpaperPath(version string) string { return filepath.Join(dataDir(), "wallpaper-"+version) }

// pictureStamps says which pictures Studio has (hero and each card's wallpaper) and when each changed.
func pictureStamps() map[string]int64 {
	m := map[string]int64{}
	for _, v := range cardVersions {
		if fi, err := os.Stat(wallpaperPath(v)); err == nil && fi.Size() > 0 {
			m[v] = fi.ModTime().UnixNano()
		}
	}
	return m
}

// pictureToPhoneScript (run as root) puts the picture pushed to phoneHeroTemp in the GalaxyIOS app's storage as name,
// owned by the app and with its storage's SELinux label, so the app shows it the next time it refreshes.
func pictureToPhoneScript(name string) string {
	return fmt.Sprintf(`app=%[1]s; src=%[2]s; dst=$app/files/%[3]s
[ -d "$app" ] || { rm -f "$src"; echo '@@FAILED GalaxyIOS is not installed on the phone'; exit 4; }
[ -s "$src" ] || { echo '@@FAILED the picture did not reach the phone'; exit 4; }
u=$(stat -c %%u "$app"); label=$(ls -Zd "$app" | awk '{print $1}')
mkdir -p "$app/files" && chown "$u:$u" "$app/files" && chmod 0771 "$app/files"
cp "$src" "$dst.tmp" && chown "$u:$u" "$dst.tmp" && chmod 0600 "$dst.tmp" || { rm -f "$src" "$dst.tmp"; echo '@@FAILED could not copy the picture'; exit 4; }
[ -n "$label" ] && { chcon "$label" "$app/files" 2>/dev/null; chcon "$label" "$dst.tmp" 2>/dev/null; }
mv -f "$dst.tmp" "$dst"; rm -f "$src"
echo '@@OK picture'`, phoneAppDir, phoneHeroTemp, name)
}

// pushPictureToPhone sends one picture file to the GalaxyIOS app on the phone as name (needs root).
func pushPictureToPhone(ctx context.Context, j *JobCtx, s Settings, serial, local, name string) error {
	if err := pushFile(ctx, j, s, serial, local, phoneHeroTemp); err != nil {
		return err
	}
	lines, code, err := rootRun(ctx, s, serial, pictureToPhoneScript(name))
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

// pushPicturesToPhone sends every picture Studio has (the Boot-card photo and the cards' wallpapers) to GalaxyIOS on
// the phone, and says how many went.
func pushPicturesToPhone(ctx context.Context, j *JobCtx, s Settings, serial string) (int, error) {
	n := 0
	if heroStamp() != 0 {
		if err := pushPictureToPhone(ctx, j, s, serial, heroPath(), "hero-picture"); err != nil {
			return n, err
		}
		n++
	}
	for v := range pictureStamps() {
		if err := pushPictureToPhone(ctx, j, s, serial, wallpaperPath(v), "wallpaper-"+v); err != nil {
			return n, err
		}
		n++
	}
	if n == 0 {
		return 0, fmt.Errorf("no pictures chosen in Studio yet: click the phone on the Boot card, or choose the cards' wallpapers")
	}
	return n, nil
}

func handleWallpaper(w http.ResponseWriter, r *http.Request) {
	v := r.URL.Query().Get("v")
	if _, ok := pictureStamps()[v]; !ok {
		http.NotFound(w, r)
		return
	}
	http.ServeFile(w, r, wallpaperPath(v))
}

func handleHero(w http.ResponseWriter, r *http.Request) {
	if heroStamp() == 0 {
		http.NotFound(w, r)
		return
	}
	http.ServeFile(w, r, heroPath())
}
