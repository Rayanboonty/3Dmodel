// Liquid glass on Studio's navigation bar, with ybouane's liquidglass (vendor/liquidglass.js, MIT): a WebGL lens that
// really refracts the page names under it, with chromatic aberration, a Fresnel rim and a specular highlight.
// Touch physics as on iOS: it springs to the page you choose; while held it swells; dragged, it follows the pointer,
// stretching with its speed, and settles on the nearest page when let go. Without WebGL the sidebar keeps its CSS glass.
import { LiquidGlass } from './vendor/liquidglass.js';

const nav = document.querySelector('nav');
const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

// A critically-damped-ish spring stepped per frame: value follows target with a little overshoot.
function spring(value, { stiffness = 380, damping = 26 } = {}) {
  return { value, target: value, v: 0, stiffness, damping,
    step(dt) { const f = -this.stiffness * (this.value - this.target) - this.damping * this.v; this.v += f * dt; this.value += this.v * dt; },
    rest() { return Math.abs(this.value - this.target) < 0.05 && Math.abs(this.v) < 0.05; } };
}

async function start() {
  if (!nav) return;
  // The library refracts the root's children, not the root's own background: a backing layer as the first child gives
  // the lens the sidebar's colour to bend (without it, it shades transparency, which shows as white).
  const back = document.createElement('div');
  back.className = 'nav-back';
  back.setAttribute('aria-hidden', 'true');
  nav.prepend(back);
  const lens = document.createElement('div');
  lens.className = 'nav-lens';
  lens.setAttribute('aria-hidden', 'true');
  nav.appendChild(lens);
  const items = () => [...nav.querySelectorAll(':scope > a')];
  const current = () => nav.querySelector('a.on') || items()[0];

  const first = current();
  const y = spring(first ? first.offsetTop : 0);
  const scale = spring(1, { stiffness: 520, damping: 22 });
  let speed = 0;
  const size = () => { const a = current(); if (a) { lens.style.width = a.offsetWidth + 'px'; lens.style.height = a.offsetHeight + 'px'; } };
  size();
  lens.dataset.config = JSON.stringify({
    refraction: 0.85, chromAberration: 0.09, edgeHighlight: 0.22, specular: 0.35, fresnel: 1, blurAmount: 0.04,
    cornerRadius: (first ? first.offsetHeight : 46) / 2, zRadius: 26, shadowOpacity: 0.35, shadowSpread: 12, bevelMode: 0,
  });

  // the nav's own look (dark, the selected item clear) must be in place before the library first captures it
  nav.classList.add('lg');
  let glass;
  try {
    glass = await LiquidGlass.init({ root: nav, glassElements: [lens] });
  } catch (e) {
    console.warn('liquid glass unavailable, keeping the CSS glass:', e);
    nav.classList.remove('lg');
    lens.remove();
    return;
  }

  let raf = 0, last = 0;
  const draw = () => {
    const stretch = Math.min(Math.abs(speed) * 0.012, 0.22);
    lens.style.transform = `translateY(${y.value}px) scale(${scale.value * (1 - stretch * 0.4)}, ${scale.value * (1 + stretch)})`;
    glass.markChanged(lens);
  };
  const tick = (t) => {
    const dt = Math.min((t - (last || t)) / 1000, 1 / 30) || 1 / 60;
    last = t;
    if (!dragging) { y.step(dt); speed = y.v / 60; }
    scale.step(dt);
    draw();
    if (dragging || !y.rest() || !scale.rest()) raf = requestAnimationFrame(tick);
    else { raf = 0; last = 0; speed = 0; draw(); }
  };
  const run = () => { if (!raf) raf = requestAnimationFrame(tick); };
  const goTo = (a) => { if (!a) return; size(); y.target = a.offsetTop; if (reduce) { y.value = y.target; y.v = 0; } run(); };
  draw();

  // follow page changes (clicks, and show() called from code); the items' look changed, so they are captured again
  new MutationObserver(() => { items().forEach((a) => glass.markChanged(a)); goTo(current()); })
    .observe(nav, { subtree: true, attributes: true, attributeFilter: ['class'] });
  window.addEventListener('resize', () => goTo(current()));

  // press, drag and release
  let dragging = false, downY = 0, startY = 0, moved = false, lastY = 0;
  nav.addEventListener('pointerdown', (e) => {
    if (e.button !== 0) return;
    downY = lastY = e.clientY; startY = y.value; moved = false;
    scale.target = 1.12; run();
    const onMove = (m) => {
      if (!moved && Math.abs(m.clientY - downY) > 6) { moved = true; dragging = true; nav.setPointerCapture(e.pointerId); }
      if (!dragging) return;
      const max = Math.max(...items().map((a) => a.offsetTop));
      y.value = Math.max(-8, Math.min(max + 8, startY + m.clientY - downY));
      speed = m.clientY - lastY; lastY = m.clientY;
      run();
    };
    const onUp = () => {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      window.removeEventListener('pointercancel', onUp);
      scale.target = 1;
      if (dragging) {
        dragging = false;
        y.v = speed * 40;
        const mid = y.value + lens.offsetHeight / 2;
        const near = items().reduce((b, a) => Math.abs(a.offsetTop + a.offsetHeight / 2 - mid) < Math.abs(b.offsetTop + b.offsetHeight / 2 - mid) ? a : b);
        if (near.classList.contains('on')) goTo(near); else near.click();
      }
      run();
    };
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
    window.addEventListener('pointercancel', onUp);
  });
  // a drag ends with the page under the lens chosen: the click that follows a drag is not a second choice
  nav.addEventListener('click', (e) => { if (moved) { e.stopPropagation(); e.preventDefault(); moved = false; } }, true);
}

start();
