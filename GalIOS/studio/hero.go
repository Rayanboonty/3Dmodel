package main

// The owner's picture for the Boot card: a file they choose on this PC, copied into Studio's data folder and shown in
// place of the drawing. It stays on this PC: nothing of it is in Studio, its repository or its releases.

import (
	"fmt"
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

func handleHero(w http.ResponseWriter, r *http.Request) {
	if heroStamp() == 0 {
		http.NotFound(w, r)
		return
	}
	http.ServeFile(w, r, heroPath())
}
