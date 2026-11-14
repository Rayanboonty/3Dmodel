package main

import (
	"context"
	"os"
	"strings"
	"testing"
)

// TestRuntimeFromPack (network): the newest runtime comes from the GalIOS GPU pack release first.
func TestRuntimeFromPack(t *testing.T) {
	if os.Getenv("STUDIO_NET") == "" {
		t.Skip("network test")
	}
	t.Setenv("STUDIO_DATA", t.TempDir())
	t.Setenv("STUDIO_RUNTIME", "")
	h := NewHub()
	p, err := latestRuntime(context.Background(), &JobCtx{h})
	if err != nil {
		t.Fatal(err)
	}
	t.Logf("%s -> version %s", p, runtimeVersion(p))
	if v := runtimeVersion(p); !strings.Contains(v, "-galios.") {
		t.Fatalf("not the pack's runtime: %s", v)
	}
	p2, _ := latestRuntime(context.Background(), &JobCtx{h})
	if p2 != p {
		t.Fatal("second lookup differs")
	}
}
