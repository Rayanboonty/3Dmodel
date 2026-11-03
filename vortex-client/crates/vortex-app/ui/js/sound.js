// Tiny 8-bit sound effects, synthesised with WebAudio (no audio files).

let ctx = null;
let enabled = true;

function audio() {
  if (!enabled) return null;
  if (!ctx) {
    try {
      ctx = new (window.AudioContext || window.webkitAudioContext)();
    } catch {
      return null;
    }
  }
  if (ctx.state === "suspended") ctx.resume().catch(() => {});
  return ctx;
}

function tone(freq, start, dur, { type = "square", vol = 0.06, slide = 0 } = {}) {
  const a = audio();
  if (!a) return;
  const t0 = a.currentTime + start;
  const osc = a.createOscillator();
  const gain = a.createGain();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t0);
  if (slide) osc.frequency.exponentialRampToValueAtTime(Math.max(40, freq + slide), t0 + dur);
  gain.gain.setValueAtTime(0.0001, t0);
  gain.gain.exponentialRampToValueAtTime(vol, t0 + 0.008);
  gain.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
  osc.connect(gain).connect(a.destination);
  osc.start(t0);
  osc.stop(t0 + dur + 0.02);
}

export const sound = {
  setEnabled(on) {
    enabled = !!on;
  },
  click() {
    tone(660 + Math.random() * 60, 0, 0.05, { vol: 0.045 });
    tone(990, 0.03, 0.04, { vol: 0.025 });
  },
  hover() {
    tone(1320, 0, 0.025, { type: "triangle", vol: 0.012 });
  },
  toggle(on) {
    tone(on ? 520 : 780, 0, 0.05, { vol: 0.04 });
    tone(on ? 780 : 520, 0.05, 0.06, { vol: 0.04 });
  },
  nav() {
    tone(440, 0, 0.04, { type: "triangle", vol: 0.05, slide: 220 });
  },
  success() {
    [523, 659, 784, 1046].forEach((f, i) => tone(f, i * 0.07, 0.12, { vol: 0.045 }));
  },
  launch() {
    [392, 523, 659, 784, 1046, 1318].forEach((f, i) => tone(f, i * 0.06, 0.16, { vol: 0.05 }));
    tone(196, 0, 0.5, { type: "triangle", vol: 0.04, slide: 400 });
  },
  error() {
    tone(220, 0, 0.12, { type: "sawtooth", vol: 0.04 });
    tone(165, 0.1, 0.18, { type: "sawtooth", vol: 0.04 });
  },
  pop() {
    tone(880, 0, 0.06, { type: "triangle", vol: 0.05, slide: 600 });
  },
};
