// Doorman – Scanner-Seite
import QrScanner from '/vendor/qr-scanner/qr-scanner.min.js';

const root = document.getElementById('scanner');
const checkpointId = Number(root.dataset.checkpointId);
const eventCode = root.dataset.eventCode;
const video = document.getElementById('video');
const startOverlay = document.getElementById('start');
const startBtn = document.getElementById('start-btn');
const startHint = document.getElementById('start-hint');
const resultEl = document.getElementById('result');
const countEl = document.getElementById('count');
const totalEl = document.getElementById('total');
const history = document.getElementById('history');

let scanner = null;
let busy = false;
let lastCode = null;
let lastCodeAt = 0;
let hideTimer = null;
let audio = null;

const TEXT = {
  OK: { cls: 'ok', icon: '✓', title: 'Gültig' },
  ALREADY: { cls: 'warn', icon: '!', title: 'Bereits gescannt' },
  NOT_FOUND: { cls: 'err', icon: '✕', title: 'QR-Code nicht gefunden' },
  WRONG_EVENT: { cls: 'err', icon: '✕', title: 'Falsche Veranstaltung' },
  SESSION: { cls: 'err', icon: '🔒', title: 'Bitte neu anmelden' },
  ERROR: { cls: 'err', icon: '⚠', title: 'Verbindungsfehler' },
};

function time(iso) {
  return iso ? new Date(iso).toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) : '';
}

function beep(kind) {
  try {
    audio = audio || new (window.AudioContext || window.webkitAudioContext)();
    const tones = kind === 'ok' ? [[880, 0.12]] : kind === 'warn' ? [[660, 0.1], [660, 0.1]] : [[220, 0.35]];
    let t = audio.currentTime;
    for (const [freq, dur] of tones) {
      const osc = audio.createOscillator();
      const gain = audio.createGain();
      osc.frequency.value = freq;
      osc.type = kind === 'err' ? 'square' : 'sine';
      gain.gain.setValueAtTime(0.25, t);
      gain.gain.exponentialRampToValueAtTime(0.001, t + dur);
      osc.connect(gain).connect(audio.destination);
      osc.start(t);
      osc.stop(t + dur);
      t += dur + 0.08;
    }
  } catch (_) { /* kein Ton möglich */ }
  if (navigator.vibrate) navigator.vibrate(kind === 'ok' ? 80 : [120, 80, 120]);
}

function show(status, data) {
  const t = TEXT[status] || TEXT.ERROR;
  resultEl.className = 'result ' + t.cls;
  document.getElementById('result-icon').textContent = t.icon;
  document.getElementById('result-title').textContent = t.title;
  document.getElementById('result-name').textContent = (data && data.name) || '';
  let detail = '';
  if (status === 'ALREADY') detail = 'um ' + time(data.time) + ' Uhr';
  else if (status === 'WRONG_EVENT') detail = 'Code gehört zu: ' + data.otherEvent;
  else if (status === 'OK' && data.manual) detail = 'Ohne QR-Code · Personalien geprüft' + (data.note ? ' · ' + data.note : '');
  else if (status === 'OK' && data.note) detail = data.note;
  else if (status === 'SESSION') detail = 'Sitzung abgelaufen – hier tippen';
  else if (status === 'ERROR') detail = 'Bitte erneut versuchen';
  document.getElementById('result-detail').textContent = detail;
  beep(t.cls);

  if (data && typeof data.checkedIn === 'number') {
    countEl.textContent = data.checkedIn;
    totalEl.textContent = data.total;
  }
  const li = document.createElement('li');
  li.className = t.cls;
  li.textContent = new Date().toLocaleTimeString('de-DE', { hour: '2-digit', minute: '2-digit' }) + '  ' +
    t.title + ((data && data.name) ? ' – ' + data.name : '');
  history.prepend(li);
  while (history.children.length > 8) history.lastChild.remove();

  clearTimeout(hideTimer);
  if (status !== 'SESSION') hideTimer = setTimeout(hide, t.cls === 'ok' ? 2000 : 3500);
}

function hide() {
  resultEl.className = 'result hidden';
  busy = false;
}

resultEl.addEventListener('click', () => {
  if (document.getElementById('result-title').textContent === TEXT.SESSION.title) {
    location.reload(); // führt zum Login und danach zurück auf diese Seite
    return;
  }
  clearTimeout(hideTimer);
  hide();
});

async function check(code) {
  if (busy) return;
  const now = Date.now();
  // Derselbe Code direkt nochmal vor der Kamera → ignorieren
  if (code === lastCode && now - lastCodeAt < 4000) return;
  lastCode = code;
  lastCodeAt = now;
  busy = true;
  try {
    const r = await fetch('/api/scan', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify({ token: code, eventCode, checkpointId }),
    });
    const type = r.headers.get('content-type') || '';
    if (r.status === 401 || r.status === 403 || r.redirected || !type.includes('json')) {
      show('SESSION');
      return;
    }
    if (!r.ok) throw new Error('HTTP ' + r.status);
    const data = await r.json();
    show(data.status, data);
  } catch (e) {
    console.error(e);
    show('ERROR');
  }
}

async function start() {
  startBtn.disabled = true;
  startHint.textContent = '';
  try {
    // AudioContext muss in einer Nutzeraktion entstehen (iOS)
    audio = audio || new (window.AudioContext || window.webkitAudioContext)();
  } catch (_) { /* egal */ }
  try {
    scanner = new QrScanner(video, (res) => check(res.data), {
      preferredCamera: 'environment',
      maxScansPerSecond: 8,
      highlightScanRegion: true,
      highlightCodeOutline: true,
      returnDetailedScanResult: true,
    });
    await scanner.start();
    startOverlay.hidden = true;
  } catch (e) {
    console.error(e);
    startBtn.disabled = false;
    startHint.textContent = window.isSecureContext
      ? 'Kamera nicht verfügbar. Bitte Kamerazugriff erlauben (iPhone: Einstellungen → Safari → Kamera).'
      : 'Kamera benötigt HTTPS.';
  }
}

startBtn.addEventListener('click', start);

document.getElementById('manual').addEventListener('submit', (e) => {
  e.preventDefault();
  const input = document.getElementById('manual-code');
  const code = input.value.trim();
  if (!code) return;
  lastCode = null;
  input.value = '';
  input.blur();
  check(code);
});

// Kamera pausieren, wenn die App im Hintergrund ist (spart Akku)
document.addEventListener('visibilitychange', () => {
  if (!scanner) return;
  if (document.hidden) scanner.stop();
  else scanner.start().catch(() => { startOverlay.hidden = false; startBtn.disabled = false; });
});

// ---------- Ohne QR-Code: Teilnehmer suchen und nach Prüfung der Personalien einchecken ----------
const lookup = document.getElementById('lookup');
const lookupQ = document.getElementById('lookup-q');
const lookupResults = document.getElementById('lookup-results');
const verify = document.getElementById('verify');
const verifyOk = document.getElementById('verify-ok');
const verifySubmit = document.getElementById('verify-submit');
let searchTimer = null;
let searchSeq = 0;
let selected = null;

function openLookup() {
  busy = true; // Kamera-Treffer ignorieren, solange gesucht wird
  lookup.hidden = false;
  lookupQ.value = '';
  lookupResults.replaceChildren();
  lookupQ.focus();
}

function closeLookup() {
  lookup.hidden = true;
  busy = false;
  lastCode = null;
}

function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text) e.textContent = text;
  return e;
}

function renderHits(hits, q) {
  lookupResults.replaceChildren();
  if (!hits.length) {
    lookupResults.append(el('li', 'lookup-empty', 'Niemand gefunden für „' + q + '“'));
    return;
  }
  for (const h of hits) {
    const li = el('li', 'lookup-item');
    const info = el('div', 'lookup-info');
    info.append(el('div', 'lookup-name', h.name || '(ohne Namen)'));
    if (h.email) info.append(el('div', 'lookup-meta', h.email));
    if (h.note) info.append(el('div', 'lookup-meta', h.note));
    li.append(info);
    if (h.checkedInAt) {
      li.append(el('span', 'lookup-done', '✓ ' + time(h.checkedInAt)));
    } else {
      const btn = el('button', 'btn primary small', 'Einchecken');
      btn.type = 'button';
      btn.addEventListener('click', () => openVerify(h));
      li.append(btn);
    }
    lookupResults.append(li);
  }
}

async function search() {
  const q = lookupQ.value.trim();
  const seq = ++searchSeq;
  if (q.length < 2) {
    lookupResults.replaceChildren();
    return;
  }
  try {
    const url = '/api/scan/search?' + new URLSearchParams({ q, eventCode, checkpointId: String(checkpointId) });
    const r = await fetch(url, { credentials: 'same-origin', headers: { 'Accept': 'application/json' } });
    const type = r.headers.get('content-type') || '';
    if (seq !== searchSeq) return; // veraltete Antwort
    if (r.redirected || !type.includes('json')) {
      closeLookup();
      show('SESSION');
      return;
    }
    renderHits(await r.json(), q);
  } catch (e) {
    console.error(e);
    lookupResults.replaceChildren(el('li', 'lookup-empty', 'Verbindungsfehler – bitte erneut versuchen'));
  }
}

function openVerify(hit) {
  selected = hit;
  document.getElementById('verify-name').textContent = hit.name;
  document.getElementById('verify-email').textContent = hit.email || '';
  document.getElementById('verify-note').textContent = hit.note || '';
  verifyOk.checked = false;
  verifySubmit.disabled = true;
  verify.showModal();
}

verifyOk.addEventListener('change', () => { verifySubmit.disabled = !verifyOk.checked; });
document.getElementById('verify-cancel').addEventListener('click', () => verify.close());

document.getElementById('verify-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  if (!selected || !verifyOk.checked) return;
  verifySubmit.disabled = true;
  try {
    const r = await fetch('/api/scan/manual', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
      body: JSON.stringify({ participantId: selected.id, eventCode, checkpointId, verified: true }),
    });
    const type = r.headers.get('content-type') || '';
    verify.close();
    closeLookup();
    if (r.redirected || !type.includes('json')) { show('SESSION'); return; }
    if (!r.ok) throw new Error('HTTP ' + r.status);
    busy = true;
    const data = await r.json();
    show(data.status, data);
  } catch (err) {
    console.error(err);
    verify.close();
    closeLookup();
    busy = true;
    show('ERROR');
  }
});

document.getElementById('lookup-open').addEventListener('click', openLookup);
document.getElementById('lookup-close').addEventListener('click', closeLookup);
lookupQ.addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(search, 250);
});
