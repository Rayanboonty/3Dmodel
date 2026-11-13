//go:build !windows

package main

import (
	"os"
	"syscall"
)

func diskFree(path string) int64 {
	var st syscall.Statfs_t
	if syscall.Statfs(path, &st) != nil {
		return 0
	}
	return int64(st.Bavail) * int64(st.Bsize)
}

func hostMemGB() int { return 16 }

func wslDiskFree() int64 {
	if g := os.Getenv("STUDIO_FREE_GB"); g != "" { // tests
		var n int64
		for _, c := range g {
			n = n*10 + int64(c-'0')
		}
		return n << 30
	}
	if d := os.Getenv("STUDIO_WSL_FREE_DIR"); d != "" {
		return diskFree(d)
	}
	return diskFree("/")
}
