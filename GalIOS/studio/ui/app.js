// GalIOS Studio's page: state every few seconds, the running task and the log live over an event stream.
const $ = (s) => document.querySelector(s);
const $$ = (s) => [...document.querySelectorAll(s)];
let state = null;
let logLines = [];

// ---- navigation
$$('nav a').forEach((a) => a.addEventListener('click', () => show(a.dataset.page)));
function show(page) {
  $$('nav a').forEach((a) => a.classList.toggle('on', a.dataset.page === page));
  $$('.page').forEach((p) => p.classList.toggle('on', p.id === page));
  if (page === 'logs') renderLog(true);
  try { localStorage.setItem('page', page); } catch (_) {}
}
try { const p = localStorage.getItem('page'); if (p && document.getElementById(p)) show(p); } catch (_) {}

// ---- actions
document.addEventListener('click', async (e) => {
  const b = e.target.closest('[data-act]');
  if (!b) return;
  if (b.dataset.confirm && !(await confirmBox(b.dataset.confirm))) return;
  act(b.dataset.act);
});
async function act(name, args) {
  try {
    const r = await fetch('/api/action', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name, args: args || {} }) });
    const j = await r.json();
    if (!j.ok) toast(j.message, true);
    else if (name.startsWith('pick.') && j.message) toast('Chosen: ' + j.message);
    else if (j.message && j.message !== 'started' && j.message !== 'checking') toast(j.message);
    setTimeout(refresh, 400);
  } catch (err) { toast('Studio did not answer: ' + err, true); }
}
function confirmBox(text) {
  return new Promise((res) => {
    const d = $('#confirm');
    $('#confirmText').textContent = text;
    d.returnValue = '';
    d.querySelectorAll('button').forEach((btn) => (btn.onclick = () => d.close(btn.value)));
    d.onclose = () => res(d.returnValue === 'yes');
    d.showModal();
  });
}
let toastTimer;
function toast(text, err) {
  const t = $('#toast');
  t.textContent = text;
  t.classList.toggle('err', !!err);
  t.classList.add('on');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.remove('on'), err ? 7000 : 4000);
}

// ---- state
async function refresh() {
  try {
    const r = await fetch('/api/state');
    state = await r.json();
    render();
  } catch (_) {}
}
const esc = (s) => String(s ?? '').replace(/[&<>"]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
const row = (mark, title, value) => `<div class="row"><i class="dot ${mark}"></i><b>${esc(title)}</b><span>${esc(value)}</span></div>`;
const gb = (n) => (n ? (n / 2 ** 30).toFixed(1) + ' GB' : '—');

function render() {
  const s = state, pc = s.pc || {}, ph = s.phone || {}, st = ph.stage || {}, set = s.settings || {};
  $('#ver').textContent = 'v' + s.version;
  const built = !!pc.imageOnPC;
  const wsl = [
    row(pc.wsl ? 'ok' : 'bad', 'WSL', pc.wsl ? 'installed' : pc.wslMessage || 'not checked yet'),
    row(pc.distroReady ? 'ok' : 'wait', set.distro, pc.distroReady ? 'ready' : 'not installed'),
    row(pc.freeGB >= 75 || built ? 'ok' : pc.freeGB ? 'wait' : 'off', 'Free on the Windows drive', pc.freeGB ? pc.freeGB + ' GB (the build needs about 75)' : '—'),
    row(pc.memGB >= 8 ? 'ok' : pc.memGB ? 'wait' : 'off', 'Memory', pc.memGB ? pc.memGB + ' GB · ' + pc.cpus + ' cores' : '—'),
  ];
  $('#pcRows').innerHTML = wsl.join('') + row(built ? 'ok' : pc.buildState === 'none' ? 'off' : 'wait', 'iOS 27 image',
    built ? 'built · ' + gb(pc.imageBytes) : { none: 'not started', setup: 'tools ready', partial: 'partly built (resume)', built: 'built in Ubuntu' }[pc.buildState] || '—');
  $('#wslRows').innerHTML = wsl.join('') + row((pc.doneSteps || []).length ? 'ok' : 'off', 'Finished steps', (pc.doneSteps || []).join(', ') || 'none yet');
  $('#imageRows').innerHTML = row(built ? 'ok' : 'off', 'Image', built ? pc.imageOnPC + ' · ' + gb(pc.imageBytes) : 'not built yet') +
    row(pc.gpuPack ? 'ok' : 'wait', 'GPU acceleration', pc.gpuPack ? 'Metal plug-in in the image (' + pc.gpuPack + ')' : 'not in the image yet: iOS draws on the CPU (Do everything adds it)') +
    row('ok', 'Folder', set.outDir);
  const ipsw = set.ipsw;
  const fetched = (pc.doneSteps || []).includes('fetch');
  $('#ipswRows').innerHTML = row(ipsw || fetched ? 'ok' : 'off', 'Firmware file',
    ipsw ? ipsw : fetched ? 'already read (from Apple or your .ipsw): nothing more needed' : 'none chosen: the build downloads what it needs from Apple');

  const conn = ph.connected
    ? [row(ph.supported ? 'ok' : 'bad', 'Phone', `${ph.label} · ${ph.model} · Android ${ph.android}`), row('ok', 'Serial', ph.serial)]
    : [row('bad', 'Phone', ph.message || 'not checked yet')];
  conn.push(row(ph.galios ? 'ok' : 'off', 'GalIOS', ph.galios || 'not installed'));
  conn.push(row(ph.oneos ? 'ok' : 'bad', 'OneOS (boot engine)', ph.oneos || 'not installed: install its iOS 27 edition'));
  $('#connRows').innerHTML = conn.join('');
  const rootText = ph.root ? 'on · ' + (ph.rootVia || 'su') : ph.connected ? 'on (always) · ' + (ph.rootMessage ? 'waiting: ' + ph.rootMessage : 'checking…') : 'on (always) · connect the phone';
  const root = [
    row(ph.root ? 'ok' : 'wait', 'Root', rootText),
    row(ph.busybox ? 'ok' : 'wait', 'busybox', ph.busybox || 'missing (the boot needs it)'),
    row(ph.resetprop ? 'ok' : 'wait', 'resetprop', ph.resetprop || 'missing (the way back needs it)'),
    row(ph.volKeys ? 'ok' : 'wait', 'Volume keys', ph.volKeys ? 'found: the way back works' : '—'),
    row(ph.freeGB >= 25 ? 'ok' : ph.freeGB ? 'wait' : 'off', 'Free on the phone', ph.freeGB ? ph.freeGB + ' GB' : '—'),
  ];
  $('#rootRows').innerHTML = root.join('');
  $('#phoneRows').innerHTML = conn.slice(0, 1).join('') + row(ph.root ? 'ok' : 'wait', 'Root', ph.root ? 'on · ' + (ph.rootVia || 'su') : 'on (always)') +
    row(st.runtime ? 'ok' : 'off', 'Runtime on phone', st.runtime || 'none') + row(st.image ? 'ok' : 'off', 'Image on phone', st.image || 'none');
  $('#stageRows').innerHTML = [
    row(st.present ? 'ok' : 'off', 'Boot engine', st.present ? 'installed' : 'not installed'),
    row(st.runtime ? 'ok' : 'off', 'OneiOS runtime', st.runtime || 'not added'),
    row(st.image ? 'ok' : 'off', 'iOS 27 image', st.image || 'not added'),
    row(st.ready ? 'ok' : st.answered ? 'wait' : 'off', 'Boot check', st.ready ? 'passed' : st.reason || '—'),
  ].join('');
  const pill = $('#readyPill');
  pill.textContent = st.session ? 'iOS session open' : st.ready ? 'Ready to boot' : 'Not ready';
  pill.classList.toggle('ok', !!st.ready);
  $('#bootLine').textContent = ph.connected ? `${ph.label} · ${st.image ? 'iOS ' + st.image : 'no image yet'}` : 'Connect the phone';

  // Do everything: the checklist
  const j = s.job || {};
  const autoRunning = j.running && j.name === 'auto.all';
  const items = [
    ['Ubuntu (WSL)', pc.wsl && pc.distroReady || built, 'installed by Studio'],
    ['Firmware', built || fetched || !!ipsw, fetched ? 'already read' : ipsw ? 'your .ipsw' : 'from Apple during the build'],
    ['iOS 27 image', built, built ? gb(pc.imageBytes) : pc.buildState === 'partial' ? 'partly built' : '1–3 hours'],
    ['GPU acceleration', !!pc.gpuPack, pc.gpuPack ? 'Metal plug-in in the image' : 'added with the next image'],
    ['Phone connected', !!ph.connected, ph.connected ? ph.label : 'USB, debugging on'],
    ['Root', !!ph.root, ph.root ? ph.rootVia : 'start your temporary root'],
    ['OneOS (boot engine app)', !!ph.oneos, ph.oneos || 'install its iOS 27 edition'],
    ['Boot engine', !!st.present, 'from OneOS, S25 check adapted'],
    ['OneiOS runtime', !!st.runtime, st.runtime || 'from GitHub'],
    ['Image on the phone', !!st.image, st.image || 'copied from this PC'],
    ['Boot check', !!st.ready, st.ready ? 'passed' : st.reason || '—'],
  ];
  let cur = false;
  $('#checklist').innerHTML = items.map(([t, done, sub]) => {
    let cls = done ? 'done' : '';
    if (!done && !cur) { cur = true; cls = autoRunning ? 'now' : 'wait'; }
    return `<li class="${cls}"><i>${done ? '✓' : ''}</i><span>${esc(t)}<small>${esc(sub)}</small></span></li>`;
  }).join('');
  $('#restartBtn').classList.toggle('hidden', !s.restartNeeded);
  $('#autoBootBtn').classList.toggle('hidden', !st.ready || autoRunning);
  $('#autoBtn').classList.toggle('hidden', !!s.restartNeeded);
  $('#autoBtn').textContent = st.ready ? 'Check everything again' : built ? 'Carry on' : 'Do everything for me';
  $('#autoLine').textContent = autoRunning ? (j.step || 'Working…')
    : st.ready ? 'Everything is ready on the phone. Boot whenever you like.'
    : s.restartNeeded ? 'Windows has to restart to finish installing WSL. Studio opens by itself afterwards and carries on.'
    : 'Press once, then leave the PC on. The image build takes 1–3 hours; the rest happens by itself.';

  renderJob(s.job);
  if (!$('#settings').classList.contains('on')) fillSettings(set);
}

function renderJob(j) {
  if (!j) return;
  $('#task').classList.toggle('running', !!j.running);
  $('#taskTitle').textContent = j.title || 'Idle';
  $('#taskStep').textContent = j.running ? (j.step || 'Working…') + (j.detail ? ' · ' + j.detail : '')
    : j.error ? '✕ ' + j.error : j.result ? '✓ ' + j.result : 'Nothing running.';
  const known = j.running && j.progress >= 0;
  $('#taskPct').textContent = known ? Math.round(j.progress * 100) + '%' : '';
  $('.bar').classList.toggle('indet', j.running && !known);
  $('#taskBar').style.width = known ? j.progress * 100 + '%' : j.running ? '' : j.error ? '0' : j.result ? '100%' : '0';
  $('#stopBtn').disabled = !j.running;
  $$('[data-act]').forEach((b) => {
    if (!['job.stop', 'quit', 'open.outdir', 'open.data', 'pc.refresh', 'phone.refresh', 'wsl.terminal', 'pc.restart'].includes(b.dataset.act)) b.disabled = !!j.running;
  });
}

// ---- settings
const keys = ['distro', 'outDir', 'ipsw', 'githubToken', 'rootCmd', 'adbPath', 'serial'];
let settingsFilled = false;
function fillSettings(set) { settingsFilled = true; keys.forEach((k) => { const el = $('#s_' + k); if (el && document.activeElement !== el) el.value = set[k] || ''; }); }
$('#saveSettings').addEventListener('click', async () => {
  if (!settingsFilled) return toast('Settings are still loading: try again in a moment.', true);
  const body = {};
  keys.forEach((k) => (body[k] = $('#s_' + k).value.trim()));
  const r = await fetch('/api/settings', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  toast(r.ok ? 'Saved.' : 'Could not save.', !r.ok);
  act('pc.refresh'); act('phone.refresh');
});

// ---- log
function renderLog(scroll) {
  const el = $('#log');
  const atEnd = el.scrollTop + el.clientHeight >= el.scrollHeight - 30;
  el.textContent = logLines.join('\n');
  if (scroll || atEnd) el.scrollTop = el.scrollHeight;
  $('#tail').textContent = logLines.slice(-2).join('\n');
}
$('#copyLog').addEventListener('click', () => navigator.clipboard.writeText(logLines.join('\n')).then(() => toast('Copied.')));
$('#saveLog').addEventListener('click', () => {
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob([logLines.join('\n')], { type: 'text/plain' }));
  a.download = 'galios-studio-' + new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-') + '.log';
  a.click();
});

async function start() {
  try { logLines = (await (await fetch('/api/log')).json()) || []; } catch (_) { logLines = []; }
  renderLog(true);
  const es = new EventSource('/api/events');
  let pending = false;
  es.onmessage = (m) => {
    const ev = JSON.parse(m.data);
    if (ev.type === 'log') {
      logLines.push(...ev.line.split('\n'));
      if (logLines.length > 6000) logLines = logLines.slice(-5000);
      if (!pending) { pending = true; requestAnimationFrame(() => { pending = false; renderLog(false); }); }
    } else if (ev.type === 'job') {
      renderJob(ev.job);
      if (!ev.job.running) {
        setTimeout(refresh, 800);
        if (ev.job.name === 'auto.all' && ev.job.result && ev.job.result.startsWith('Everything is ready')) toast(ev.job.result);
      }
    }
  };
  refresh();
  setInterval(refresh, 5000);
  setInterval(() => fetch('/api/ping', { method: 'POST' }).catch(() => {}), 15000);
}
start();
