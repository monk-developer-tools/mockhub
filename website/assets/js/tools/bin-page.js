import { $, show, wireCopyButtons } from './common.js';
import { call, toCurl } from './http.js';

const KEY = 'mockhub-bin';
const POLL_MS = 2000;
let bin = null;
let after = 0;
let requests = [];
let selected = null;
let timer = null;

const store = {
  read() {
    try {
      return JSON.parse(localStorage.getItem(KEY) ?? 'null');
    } catch (e) {
      return null;
    }
  },
  write(value) {
    try {
      if (value) localStorage.setItem(KEY, JSON.stringify(value));
      else localStorage.removeItem(KEY);
    } catch (e) {
      return;
    }
  },
};

const binUrl = () => `${location.origin}/__public/bins/${bin.id}`;
const auth = () => ({ 'X-Bin-Token': bin.token });

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') node.className = v;
    else node.setAttribute(k, v);
  }
  node.append(...children);
  return node;
}

function time(at) {
  return new Date(at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function pretty(body) {
  try {
    return JSON.stringify(JSON.parse(body), null, 2);
  } catch (e) {
    return body;
  }
}

function renderList() {
  const list = $('[data-bin-list]');
  list.replaceChildren(...requests.map((r) => {
    const button = el('button', { type: 'button', class: 'tool-request', 'aria-pressed': String(selected === r.seq) },
      el('span', { class: `jack ${r.method.toLowerCase()}` }, r.method),
      el('span', { class: 'tool-request-path' }, r.path + (r.query ? `?${r.query}` : '')),
      el('span', { class: 'tool-request-time' }, time(r.at)));
    button.addEventListener('click', () => {
      selected = r.seq;
      renderList();
      renderDetail();
    });
    return el('li', {}, button);
  }));
  $('[data-bin-count]').textContent = String(requests.length);
  $('[data-bin-empty]').hidden = requests.length > 0;
}

function renderDetail() {
  const r = requests.find((x) => x.seq === selected);
  const box = $('[data-bin-detail]');
  const curl = $('[data-bin-curl]');
  $('[data-bin-curl-copy]').hidden = !r;
  if (!r) {
    box.replaceChildren(el('p', { class: 'tool-hint' }, 'Pick a request to see its headers and body.'));
    curl.hidden = true;
    return;
  }
  const headers = el('dl', { class: 'tool-headers' });
  for (const [name, value] of Object.entries(r.headers)) headers.append(el('dt', {}, name), el('dd', {}, value));
  const note = r.truncated ? el('p', { class: 'tool-hint' }, `The body was ${r.size} bytes; the first 10 KB are kept.`) : '';
  box.replaceChildren(
    el('p', { class: 'tool-request-line' }, el('span', { class: `jack ${r.method.toLowerCase()}` }, r.method), ` ${r.path}${r.query ? `?${r.query}` : ''} · ${new Date(r.at).toLocaleString()}`),
    el('h4', { class: 'tool-sub' }, 'Headers'), headers,
    el('h4', { class: 'tool-sub' }, `Body (${r.size} bytes)`), el('pre', { class: 'tool-out' }, r.body ? pretty(r.body) : '(empty)'), note);
  curl.textContent = toCurl({ method: r.method, url: `${binUrl()}${r.path === '/' ? '' : r.path}${r.query ? `?${r.query}` : ''}`, headers: r.headers, body: r.body });
}

async function poll() {
  clearTimeout(timer);
  if (!bin) return;
  if (document.hidden) {
    timer = setTimeout(poll, POLL_MS);
    return;
  }
  const result = await call(`/__public/inspect/${bin.id}?after=${after}`, { headers: auth() });
  if (result.status === 404) {
    forget('This bin has expired. Create a new one.');
    return;
  }
  show($('[data-bin-error]'), result.error);
  if (result.json?.requests?.length) {
    const fresh = result.json.requests;
    after = Math.max(after, ...fresh.map((r) => r.seq));
    requests = [...fresh, ...requests].slice(0, 50);
    if (selected === null) selected = requests[0].seq;
    renderList();
    renderDetail();
  }
  $('[data-bin-polling]').textContent = result.error ? 'Retrying…' : 'Listening for requests';
  timer = setTimeout(poll, result.status === 429 ? POLL_MS * 5 : POLL_MS);
}

async function loadReply() {
  const result = await call(`/__public/inspect/${bin.id}/response`, { headers: auth() });
  if (!result.json || result.error) return;
  $('[data-bin-status]').value = result.json.status;
  $('[data-bin-type]').value = result.json.contentType;
  $('[data-bin-body]').value = result.json.body;
}

function open(value) {
  bin = value;
  after = 0;
  requests = [];
  selected = null;
  $('[data-bin-start]').hidden = true;
  $('[data-bin-live]').hidden = false;
  $('[data-bin-url]').textContent = binUrl();
  $('[data-bin-try]').textContent = toCurl({ method: 'POST', url: `${binUrl()}/payments`, headers: { 'Content-Type': 'application/json' }, body: '{"event":"payment.succeeded","amount":4200}' });
  renderList();
  renderDetail();
  loadReply();
  poll();
}

function forget(message) {
  clearTimeout(timer);
  bin = null;
  store.write(null);
  $('[data-bin-live]').hidden = true;
  $('[data-bin-start]').hidden = false;
  show($('[data-bin-create-error]'), message ?? '');
}

$('[data-bin-create]').addEventListener('click', async (e) => {
  e.target.disabled = true;
  const result = await call('/__public/bins', { method: 'POST' });
  e.target.disabled = false;
  if (result.error || !result.json?.id) {
    show($('[data-bin-create-error]'), result.error ?? 'The bin could not be created.');
    return;
  }
  const created = { id: result.json.id, token: result.json.token };
  store.write(created);
  open(created);
});

$('[data-bin-forget]').addEventListener('click', () => forget());

$('[data-bin-clear]').addEventListener('click', async () => {
  const result = await call(`/__public/inspect/${bin.id}`, { method: 'DELETE', headers: auth() });
  show($('[data-bin-error]'), result.error);
  if (result.error) return;
  requests = [];
  selected = null;
  renderList();
  renderDetail();
});

$('[data-bin-reply]').addEventListener('submit', async (e) => {
  e.preventDefault();
  const body = JSON.stringify({ status: Number($('[data-bin-status]').value), contentType: $('[data-bin-type]').value, body: $('[data-bin-body]').value });
  const result = await call(`/__public/inspect/${bin.id}/response`, { method: 'PUT', headers: { ...auth(), 'Content-Type': 'application/json' }, body });
  $('[data-bin-reply-note]').textContent = result.error ?? 'Saved. New requests get this answer.';
});

document.addEventListener('visibilitychange', () => {
  if (!document.hidden && bin) poll();
});

const saved = store.read();
if (saved?.id && saved?.token) open(saved);
wireCopyButtons();
