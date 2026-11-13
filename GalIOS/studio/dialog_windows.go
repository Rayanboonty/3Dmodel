//go:build windows

package main

import (
	"fmt"
	"runtime"
	"syscall"
	"unsafe"
)

// Windows' own Open dialog (IFileOpenDialog), for a file or a folder, owned by Studio's window.

var (
	ole32                           = syscall.NewLazyDLL("ole32.dll")
	shell32                         = syscall.NewLazyDLL("shell32.dll")
	procCoInitializeEx              = ole32.NewProc("CoInitializeEx")
	procCoCreateInstance            = ole32.NewProc("CoCreateInstance")
	procCoTaskMemFree               = ole32.NewProc("CoTaskMemFree")
	procSHCreateItemFromParsingName = shell32.NewProc("SHCreateItemFromParsingName")
)

type guid struct {
	d1     uint32
	d2, d3 uint16
	d4     [8]byte
}

var (
	clsidFileOpenDialog = guid{0xDC1C5A9C, 0xE88A, 0x4DDE, [8]byte{0xA5, 0xA1, 0x60, 0xF8, 0x2A, 0x20, 0xAE, 0xF7}}
	iidIFileOpenDialog  = guid{0xD57C7288, 0xD4AD, 0x4768, [8]byte{0xBE, 0x02, 0x9D, 0x96, 0x95, 0x32, 0xD9, 0x60}}
	iidIShellItem       = guid{0x43826D1E, 0xE718, 0x42EE, [8]byte{0xBC, 0x55, 0xA1, 0xE2, 0x61, 0xC3, 0x7B, 0xFE}}
)

const (
	fosPickFolders     = 0x20
	fosForceFileSystem = 0x40
	fosPathMustExist   = 0x800
	fosFileMustExist   = 0x1000
	sigdnFileSysPath   = 0x80058000
	hrCancelled        = 0x800704C7
)

func coInit() { procCoInitializeEx.Call(0, 2) } // COINIT_APARTMENTTHREADED

// vcall calls method idx of a COM object.
func vcall(obj unsafe.Pointer, idx int, args ...uintptr) uintptr {
	vtbl := *(*unsafe.Pointer)(obj)
	fn := *(*uintptr)(unsafe.Add(vtbl, uintptr(idx)*unsafe.Sizeof(uintptr(0))))
	r, _, _ := syscall.SyscallN(fn, append([]uintptr{uintptr(obj)}, args...)...)
	return r
}

func u16(s string) *uint16 { p, _ := syscall.UTF16PtrFromString(s); return p }

func wstr(p *uint16) string {
	if p == nil {
		return ""
	}
	var u []uint16
	for ptr := unsafe.Pointer(p); ; ptr = unsafe.Add(ptr, 2) {
		c := *(*uint16)(ptr)
		if c == 0 {
			break
		}
		u = append(u, c)
	}
	return syscall.UTF16ToString(u)
}

type filterSpec struct{ name, spec *uint16 }

func openDialog(owner uintptr, title string, folder bool, filterName, filter, startDir string) (string, error) {
	var dlg unsafe.Pointer
	hr, _, _ := procCoCreateInstance.Call(uintptr(unsafe.Pointer(&clsidFileOpenDialog)), 0, 1,
		uintptr(unsafe.Pointer(&iidIFileOpenDialog)), uintptr(unsafe.Pointer(&dlg)))
	if int32(hr) < 0 || dlg == nil {
		return "", fmt.Errorf("Windows' file dialog did not open (0x%08X)", uint32(hr))
	}
	defer vcall(dlg, 2) // Release
	var opts uint32
	vcall(dlg, 10, uintptr(unsafe.Pointer(&opts))) // GetOptions
	opts |= fosForceFileSystem | fosPathMustExist
	if folder {
		opts |= fosPickFolders
	} else {
		opts |= fosFileMustExist
	}
	vcall(dlg, 9, uintptr(opts)) // SetOptions
	t := u16(title)
	vcall(dlg, 17, uintptr(unsafe.Pointer(t))) // SetTitle
	var specs []filterSpec
	if !folder && filter != "" {
		specs = []filterSpec{{u16(filterName), u16(filter)}, {u16("All files"), u16("*.*")}}
		vcall(dlg, 4, uintptr(len(specs)), uintptr(unsafe.Pointer(&specs[0]))) // SetFileTypes
	}
	if startDir != "" && fileExists(startDir) {
		var item unsafe.Pointer
		sd := u16(startDir)
		r, _, _ := procSHCreateItemFromParsingName.Call(uintptr(unsafe.Pointer(sd)), 0,
			uintptr(unsafe.Pointer(&iidIShellItem)), uintptr(unsafe.Pointer(&item)))
		runtime.KeepAlive(sd)
		if int32(r) >= 0 && item != nil {
			vcall(dlg, 12, uintptr(item)) // SetFolder
			vcall(item, 2)
		}
	}
	hr = vcall(dlg, 3, owner) // Show
	runtime.KeepAlive(t)
	runtime.KeepAlive(specs)
	if uint32(hr) == hrCancelled {
		return "", nil
	}
	if int32(hr) < 0 {
		return "", fmt.Errorf("Windows' file dialog failed (0x%08X)", uint32(hr))
	}
	var item unsafe.Pointer
	if r := vcall(dlg, 20, uintptr(unsafe.Pointer(&item))); int32(r) < 0 || item == nil { // GetResult
		return "", fmt.Errorf("no file was chosen")
	}
	defer vcall(item, 2)
	var p *uint16
	if r := vcall(item, 5, sigdnFileSysPath, uintptr(unsafe.Pointer(&p))); int32(r) < 0 || p == nil { // GetDisplayName
		return "", fmt.Errorf("that is not a file on this PC")
	}
	path := wstr(p)
	procCoTaskMemFree.Call(uintptr(unsafe.Pointer(p)))
	return path, nil
}

// pickPath shows the Open dialog on the window's thread.
func pickPath(title string, folder bool, filterName, filter, startDir string) (string, error) {
	var path string
	var err error
	onUI(func(owner uintptr) { path, err = openDialog(owner, title, folder, filterName, filter, startDir) })
	return path, err
}
