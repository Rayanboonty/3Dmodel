//go:build !windows

package main

import "fmt"

// Off Windows (development and tests) Studio's page opens in the browser.

func runWindow(string) bool { return false }
func focusWindow()          {}
func hasWindow() bool       { return false }

func pickPath(title string, folder bool, filterName, filter, startDir string) (string, error) {
	return "", fmt.Errorf("choosing files is for Windows: type the path in Settings")
}
