package main

import (
	"os"
	"path/filepath"
	"testing"
)

func TestTheBootPictureMustBeAPicture(t *testing.T) {
	png := []byte("\x89PNG\r\n\x1a\n\x00\x00\x00\x0dIHDR")
	jpeg := []byte{0xFF, 0xD8, 0xFF, 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1}
	webp := []byte("RIFF\x10\x00\x00\x00WEBPVP8 ")
	for name, b := range map[string][]byte{"png": png, "jpeg": jpeg, "webp": webp} {
		if !isPicture(b) {
			t.Errorf("%s not taken", name)
		}
	}
	for name, b := range map[string][]byte{"text": []byte("hello world!"), "empty": nil, "exe": []byte("MZ\x90\x00\x03\x00\x00\x00\x04\x00\x00\x00")} {
		if isPicture(b) {
			t.Errorf("%s taken as a picture", name)
		}
	}

	dir := t.TempDir()
	src, dest := filepath.Join(dir, "phone.png"), filepath.Join(dir, "data", "hero-picture")
	os.WriteFile(src, append(png, make([]byte, 100)...), 0o644)
	if err := saveHero(src, dest); err != nil {
		t.Fatal(err)
	}
	if b, _ := os.ReadFile(dest); len(b) != len(png)+100 {
		t.Fatalf("saved %d bytes", len(b))
	}
	// Not a picture: refused, and the saved one stays.
	bad := filepath.Join(dir, "notes.txt")
	os.WriteFile(bad, []byte("not a picture at all"), 0o644)
	if err := saveHero(bad, dest); err == nil {
		t.Fatalf("a text file was saved as the picture")
	}
	if b, _ := os.ReadFile(dest); len(b) != len(png)+100 {
		t.Fatalf("the saved picture changed after a refusal")
	}
}
