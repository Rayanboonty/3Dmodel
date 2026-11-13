package main

import (
	"context"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"
)

// Job is the one long task that runs at a time (a build, a copy to the phone, a boot ...).
type Job struct {
	Name     string  `json:"name"`
	Title    string  `json:"title"`
	Running  bool    `json:"running"`
	Step     string  `json:"step"`
	Progress float64 `json:"progress"` // 0..1, or -1 when unknown
	Detail   string  `json:"detail"`
	Error    string  `json:"error"`
	Result   string  `json:"result"`
	Started  int64   `json:"started"`
	Ended    int64   `json:"ended"`
}

type Event struct {
	Type string `json:"type"` // "log" or "job"
	Line string `json:"line,omitempty"`
	Job  *Job   `json:"job,omitempty"`
}

// Hub holds the current job, the log and the listeners of the event stream.
type Hub struct {
	mu     sync.Mutex
	job    Job
	cancel context.CancelFunc
	lines  []string
	subs   map[chan Event]struct{}
}

func NewHub() *Hub { return &Hub{subs: map[chan Event]struct{}{}, job: Job{Progress: -1}} }

var ErrBusy = errors.New("another task is running: wait for it or stop it first")

// JobCtx is what a running job uses to report.
type JobCtx struct {
	h *Hub
}

func (j *JobCtx) Log(format string, a ...any) { j.h.log(fmt.Sprintf(format, a...)) }
func (j *JobCtx) Line(s string)               { j.h.log(s) }
func (j *JobCtx) Step(s string) {
	j.h.update(func(x *Job) { x.Step = s; x.Progress = -1; x.Detail = "" })
	j.h.log("==> " + s)
}
func (j *JobCtx) Progress(f float64, detail string) {
	j.h.update(func(x *Job) { x.Progress = f; x.Detail = detail })
}

func (h *Hub) Start(name, title string, fn func(ctx context.Context, j *JobCtx) (string, error)) error {
	h.mu.Lock()
	if h.job.Running {
		h.mu.Unlock()
		return ErrBusy
	}
	ctx, cancel := context.WithCancel(context.Background())
	h.cancel = cancel
	h.job = Job{Name: name, Title: title, Running: true, Progress: -1, Started: time.Now().Unix()}
	h.mu.Unlock()
	h.publish()
	h.log(fmt.Sprintf("━━ %s ━━", title))
	go func() {
		result, err := func() (r string, e error) {
			defer func() {
				if p := recover(); p != nil {
					e = fmt.Errorf("internal error: %v", p)
				}
			}()
			return fn(ctx, &JobCtx{h})
		}()
		h.update(func(x *Job) {
			x.Running = false
			x.Ended = time.Now().Unix()
			x.Result = result
			if err != nil {
				x.Error = err.Error()
				if ctx.Err() != nil {
					x.Error = "stopped"
				}
			}
		})
		switch {
		case err != nil && ctx.Err() != nil:
			h.log("■ Stopped.")
		case err != nil:
			h.log("✕ " + err.Error())
		case result != "":
			h.log("✓ " + result)
		default:
			h.log("✓ Done.")
		}
		cancel()
	}()
	return nil
}

func (h *Hub) Stop() {
	h.mu.Lock()
	c := h.cancel
	h.mu.Unlock()
	if c != nil {
		c()
	}
}

func (h *Hub) Job() Job {
	h.mu.Lock()
	defer h.mu.Unlock()
	return h.job
}

func (h *Hub) Lines(n int) []string {
	h.mu.Lock()
	defer h.mu.Unlock()
	if n <= 0 || n > len(h.lines) {
		n = len(h.lines)
	}
	return append([]string{}, h.lines[len(h.lines)-n:]...) // never nil: the page reads a JSON list
}

func (h *Hub) log(line string) {
	h.mu.Lock()
	for _, l := range strings.Split(line, "\n") {
		h.lines = append(h.lines, l)
	}
	if len(h.lines) > 20000 {
		h.lines = append([]string(nil), h.lines[len(h.lines)-15000:]...)
	}
	subs := h.subsCopy()
	h.mu.Unlock()
	for _, c := range subs {
		select {
		case c <- Event{Type: "log", Line: line}:
		default: // a slow listener misses lines; it can reload the log
		}
	}
}

func (h *Hub) update(f func(*Job)) {
	h.mu.Lock()
	f(&h.job)
	h.mu.Unlock()
	h.publish()
}

func (h *Hub) publish() {
	h.mu.Lock()
	j := h.job
	subs := h.subsCopy()
	h.mu.Unlock()
	for _, c := range subs {
		select {
		case c <- Event{Type: "job", Job: &j}:
		default:
		}
	}
}

func (h *Hub) subsCopy() []chan Event {
	out := make([]chan Event, 0, len(h.subs))
	for c := range h.subs {
		out = append(out, c)
	}
	return out
}

func (h *Hub) Subscribe() chan Event {
	c := make(chan Event, 512)
	h.mu.Lock()
	h.subs[c] = struct{}{}
	h.mu.Unlock()
	return c
}

func (h *Hub) Unsubscribe(c chan Event) {
	h.mu.Lock()
	delete(h.subs, c)
	h.mu.Unlock()
}
