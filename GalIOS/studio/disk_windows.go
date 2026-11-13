//go:build windows

package main

import (
	"os"
	"syscall"
	"unsafe"
)

var procGetDiskFreeSpaceExW = syscall.NewLazyDLL("kernel32.dll").NewProc("GetDiskFreeSpaceExW")

// diskFree is the space free for this user on the drive holding path, or 0 when unknown.
func diskFree(path string) int64 {
	p, err := syscall.UTF16PtrFromString(path)
	if err != nil {
		return 0
	}
	var free, total, all uint64
	r, _, _ := procGetDiskFreeSpaceExW.Call(uintptr(unsafe.Pointer(p)), uintptr(unsafe.Pointer(&free)),
		uintptr(unsafe.Pointer(&total)), uintptr(unsafe.Pointer(&all)))
	if r == 0 {
		return 0
	}
	return int64(free)
}

var procGlobalMemoryStatusEx = syscall.NewLazyDLL("kernel32.dll").NewProc("GlobalMemoryStatusEx")

// hostMemGB is this PC's memory in GB.
func hostMemGB() int {
	var m struct {
		Length, MemoryLoad                                 uint32
		TotalPhys, AvailPhys, TotalPageFile, AvailPageFile uint64
		TotalVirtual, AvailVirtual, AvailExtendedVirtual   uint64
	}
	m.Length = uint32(unsafe.Sizeof(m))
	if r, _, _ := procGlobalMemoryStatusEx.Call(uintptr(unsafe.Pointer(&m))); r == 0 {
		return 8
	}
	return int((m.TotalPhys + 1<<29) >> 30)
}

// wslDiskFree is the free space where WSL keeps Ubuntu's disk (the user's local app data, on drive C: usually).
func wslDiskFree() int64 { return diskFree(os.Getenv("LOCALAPPDATA")) }
