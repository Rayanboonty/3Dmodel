package main

import (
	"bufio"
	"bytes"
	"context"
	"io"
	"os/exec"
	"regexp"
	"strings"
	"unicode/utf16"
)

var ansi = regexp.MustCompile(`\x1b\[[0-9;?]*[A-Za-z]`)

// cleanLine removes terminal colours and carriage-return progress noise from one line of output.
func cleanLine(s string) string {
	s = ansi.ReplaceAllString(s, "")
	if i := strings.LastIndexByte(s, '\r'); i >= 0 {
		s = s[i+1:]
	}
	return strings.TrimRight(s, " \t")
}

// command makes an exec.Cmd that shows no console window on Windows (see exec_windows.go).
func command(ctx context.Context, name string, args ...string) *exec.Cmd {
	c := exec.CommandContext(ctx, name, args...)
	hideWindow(c)
	return c
}

// runLines runs a command and returns its output (stdout and stderr), cleaned, split into lines.
func runLines(ctx context.Context, stdin io.Reader, name string, args ...string) ([]string, int, error) {
	c := command(ctx, name, args...)
	c.Stdin = stdin
	var out bytes.Buffer
	c.Stdout, c.Stderr = &out, &out
	err := c.Run()
	code := 0
	if c.ProcessState != nil {
		code = c.ProcessState.ExitCode()
	}
	text := decodeMaybeUTF16(out.Bytes())
	var lines []string
	for _, l := range strings.Split(strings.ReplaceAll(text, "\r\n", "\n"), "\n") {
		if l = cleanLine(l); l != "" {
			lines = append(lines, l)
		}
	}
	if err != nil && code == 0 {
		code = -1
	}
	return lines, code, err
}

// stream runs a command and hands each line of its output (stdout and stderr) to onLine as it comes.
func stream(ctx context.Context, stdin io.Reader, onLine func(string), name string, args ...string) (int, error) {
	c := command(ctx, name, args...)
	c.Stdin = stdin
	pr, pw := io.Pipe()
	c.Stdout, c.Stderr = pw, pw
	if err := c.Start(); err != nil {
		return -1, err
	}
	done := make(chan struct{})
	go func() {
		defer close(done)
		sc := bufio.NewScanner(pr)
		sc.Buffer(make([]byte, 64*1024), 4*1024*1024)
		sc.Split(scanLinesCR)
		for sc.Scan() {
			if l := cleanLine(sc.Text()); l != "" {
				onLine(l)
			}
		}
		io.Copy(io.Discard, pr)
	}()
	err := c.Wait()
	pw.Close()
	<-done
	code := 0
	if c.ProcessState != nil {
		code = c.ProcessState.ExitCode()
	}
	return code, err
}

// scanLinesCR splits on \n and on a lone \r, so tools that redraw a progress line still produce lines.
func scanLinesCR(data []byte, atEOF bool) (int, []byte, error) {
	for i, b := range data {
		if b == '\n' {
			return i + 1, bytes.TrimRight(data[:i], "\r"), nil
		}
		if b == '\r' && i+1 < len(data) && data[i+1] != '\n' {
			return i + 1, data[:i], nil
		}
	}
	if atEOF && len(data) > 0 {
		return len(data), data, nil
	}
	return 0, nil, nil
}

// decodeMaybeUTF16 turns wsl.exe's UTF-16LE output (its list and status commands) into text; UTF-8 passes through.
func decodeMaybeUTF16(b []byte) string {
	if len(b) >= 2 && (bytes.Count(b, []byte{0}) > len(b)/3) {
		if len(b)%2 == 1 {
			b = b[:len(b)-1]
		}
		u := make([]uint16, 0, len(b)/2)
		for i := 0; i+1 < len(b); i += 2 {
			u = append(u, uint16(b[i])|uint16(b[i+1])<<8)
		}
		s := string(utf16.Decode(u))
		return strings.TrimPrefix(s, "\ufeff")
	}
	return string(b)
}

// shq quotes one word for a POSIX shell.
func shq(s string) string { return "'" + strings.ReplaceAll(s, "'", `'\''`) + "'" }
