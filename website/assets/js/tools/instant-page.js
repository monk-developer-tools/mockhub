import { $, download, show, wireCopyButtons } from './common.js';
import { call } from './http.js';
import { displayPath, exportMappings, fitsForm, formToStub, keyHash, parseKey, samplePath, stubToForm } from './instant.js';

const KEY = 'mockhub-instant-mock';
const POLL_MS = 3000;
let key = null;
let mock = null;
let editing = null;
let jsonMode = false;
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

const api = (suffix = '') => `/__public/mocks/${key.slug}${suffix}`;
const auth = (extra = {}) => ({ 'X-Mock-Token': key.token, ...extra });
const base = () => `${location.origin}/${key.slug}`;

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') node.className = v;
    else node.setAttribute(k, v);
  }
  node.append(...children);
  return node;
}

const methodClass = (m) => `jack ${String(m ?? 'any').toLowerCase()}`;

function renderStubs() {
  const list = $('[data-mock-stubs]');
  list.replaceChildren(...mock.stubs.map((stub) => {
    const edit = el('button', { type: 'button', class: 'tool-request', 'aria-pressed': String(editing?.id === stub.id) },
      el('span', { class: methodClass(stub.request?.method) }, stub.request?.method ?? 'ANY'),
      el('span', { class: 'tool-request-path' }, `${displayPath(stub)}${stub.name ? ` · ${stub.name}` : ''}`),
      el('span', { class: 'tool-request-time' }, String(stub.response?.status ?? 200)));
    edit.addEventListener('click', () => openEditor(stub));
    return el('li', {}, edit);
  }));
  $('[data-mock-count]').textContent = String(mock.stubs.length);
  $('[data-mock-add]').disabled = mock.stubs.length >= mock.limits.stubs;
}

function renderMeta() {
  $('[data-mock-base]').textContent = base();
  $('[data-mock-manage]').textContent = `${location.origin}${location.pathname}${keyHash(key)}`;
  const expires = new Date(mock.expiresAt).toLocaleDateString([], { day: 'numeric', month: 'long' });
  $('[data-mock-meta]').textContent = `${mock.requestsToday} of ${mock.limits.requestsPerDay} requests used today. Up to ${mock.limits.stubs} stubs. Deleted on ${expires} unless you change it before then.`;
}

function fillForm(values) {
  document.querySelectorAll('[data-mock-form] [data-field]').forEach((input) => {
    const value = values[input.dataset.field];
    if (input.type === 'checkbox') input.checked = Boolean(value);
    else input.value = value ?? '';
  });
}

function readForm() {
  const values = {};
  document.querySelectorAll('[data-mock-form] [data-field]').forEach((input) => {
    values[input.dataset.field] = input.type === 'checkbox' ? input.checked : input.value;
  });
  return values;
}

function setMode(json) {
  jsonMode = json;
  $('[data-mock-form]').hidden = json;
  $('[data-mock-json]').hidden = !json;
  $('[data-mock-mode]').textContent = json ? 'Edit as form' : 'Edit as JSON';
}

function openEditor(stub) {
  editing = stub;
  $('[data-mock-editor-title]').textContent = stub ? 'Edit stub' : 'Add a stub';
  $('[data-mock-cancel]').hidden = !stub;
  $('[data-mock-delete-stub]')?.remove();
  if (stub) {
    const remove = el('button', { type: 'button', class: 'btn ghost small', 'data-mock-delete-stub': '' }, 'Delete stub');
    remove.addEventListener('click', deleteStub);
    $('[data-mock-cancel]').after(remove);
  }
  show($('[data-mock-save-error]'), '');
  $('[data-mock-saved]').textContent = '';
  if (stub && !fitsForm(stub)) {
    $('[data-mock-json-input]').value = JSON.stringify(withoutId(stub), null, 2);
    setMode(true);
  } else {
    fillForm(stub ? stubToForm(stub) : { name: '', method: 'GET', path: '/orders/{id}', status: 200, contentType: 'application/json', body: '{"id": "{{request.path.id}}", "status": "PAID"}', templating: true, delay: 0 });
    setMode(false);
  }
  if (stub) {
    $('[data-mock-try-method]').value = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'].includes(stub.request?.method) ? stub.request.method : 'GET';
    $('[data-mock-try-path]').value = samplePath(stub);
  }
  renderStubs();
}

const withoutId = ({ id, ...rest }) => rest;

function currentStub() {
  if (!jsonMode) return formToStub(readForm());
  return JSON.parse($('[data-mock-json-input]').value);
}

async function refresh() {
  const result = await call(api(), { headers: auth() });
  if (result.status === 404) {
    forget('This mock no longer exists. Mocks are deleted 7 days after their last change.');
    return false;
  }
  show($('[data-mock-error]'), result.error);
  if (result.error) return false;
  mock = result.json;
  renderMeta();
  renderStubs();
  return true;
}

async function save(e) {
  e.preventDefault();
  let stub;
  try {
    stub = currentStub();
  } catch (err) {
    show($('[data-mock-save-error]'), 'The stub JSON is not valid JSON.');
    return;
  }
  const result = await call(editing ? api(`/stubs/${editing.id}`) : api('/stubs'), {
    method: editing ? 'PUT' : 'POST',
    headers: auth({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(stub),
  });
  show($('[data-mock-save-error]'), result.error);
  if (result.error) return;
  await refresh();
  openEditor(mock.stubs.find((s) => s.id === result.json.id) ?? null);
  $('[data-mock-saved]').textContent = 'Saved. It answers right away.';
}

async function deleteStub() {
  if (!editing) return;
  const result = await call(api(`/stubs/${editing.id}`), { method: 'DELETE', headers: auth() });
  show($('[data-mock-save-error]'), result.error);
  if (result.error) return;
  await refresh();
  openEditor(null);
}

async function tryIt() {
  const method = $('[data-mock-try-method]').value;
  const raw = $('[data-mock-try-path]').value.trim();
  const path = `/${key.slug}${raw.startsWith('/') ? raw : `/${raw}`}`;
  const body = method === 'GET' ? undefined : $('[data-mock-try-body]').value;
  const result = await call(path, { method, headers: body ? { 'Content-Type': 'application/json' } : {}, body });
  $('[data-mock-try-meta]').textContent = result.status ? `${result.status} in ${result.ms} ms` : '';
  $('[data-mock-try-out]').textContent = result.status === 0 ? result.error : (result.json ? JSON.stringify(result.json, null, 2) : result.text || '(empty body)');
  pollRequests();
}

async function pollRequests() {
  clearTimeout(timer);
  if (!key) return;
  if (!document.hidden) {
    const result = await call(api('/requests'), { headers: auth() });
    if (result.json?.requests) {
      const items = result.json.requests;
      $('[data-mock-requests]').replaceChildren(...items.map((r) => el('li', {}, el('div', { class: 'tool-request' },
        el('span', { class: methodClass(r.method) }, r.method),
        el('span', { class: 'tool-request-path' }, `${r.url} → ${r.status}${r.matched ? '' : ' · no stub matched'}`),
        el('span', { class: 'tool-request-time' }, new Date(r.at).toLocaleTimeString())))));
      $('[data-mock-requests-empty]').hidden = items.length > 0;
      $('[data-mock-polling]').textContent = 'Live';
    }
  }
  timer = setTimeout(pollRequests, POLL_MS);
}

async function open(value) {
  key = value;
  store.write(value);
  if (location.hash !== keyHash(value)) history.replaceState(null, '', keyHash(value));
  $('[data-mock-start]').hidden = true;
  $('[data-mock-live]').hidden = false;
  if (await refresh()) {
    openEditor(null);
    pollRequests();
  }
}

function forget(message) {
  clearTimeout(timer);
  key = null;
  mock = null;
  store.write(null);
  history.replaceState(null, '', location.pathname);
  $('[data-mock-live]').hidden = true;
  $('[data-mock-start]').hidden = false;
  show($('[data-mock-create-error]'), message ?? '');
}

$('[data-mock-create]').addEventListener('click', async (e) => {
  e.target.disabled = true;
  const result = await call('/__public/mocks', { method: 'POST' });
  e.target.disabled = false;
  if (result.error || !result.json?.slug) {
    show($('[data-mock-create-error]'), result.error ?? 'The mock could not be created.');
    return;
  }
  open({ slug: result.json.slug, token: result.json.token });
});
$('[data-mock-editor]').addEventListener('submit', save);
$('[data-mock-add]').addEventListener('click', () => openEditor(null));
$('[data-mock-cancel]').addEventListener('click', () => openEditor(null));
$('[data-mock-mode]').addEventListener('click', () => {
  if (!jsonMode) {
    $('[data-mock-json-input]').value = JSON.stringify(formToStub(readForm()), null, 2);
    setMode(true);
    return;
  }
  try {
    const stub = JSON.parse($('[data-mock-json-input]').value);
    if (!fitsForm(stub)) {
      show($('[data-mock-save-error]'), 'This stub uses options the form can\'t show. Keep editing it as JSON.');
      return;
    }
    fillForm(stubToForm(stub));
    setMode(false);
  } catch (err) {
    show($('[data-mock-save-error]'), 'The stub JSON is not valid JSON.');
  }
});
$('[data-mock-try]').addEventListener('click', tryIt);
$('[data-mock-export]').addEventListener('click', () => {
  if (mock) download(`${key.slug}-mappings.json`, JSON.stringify(exportMappings(mock.stubs), null, 2));
});
$('[data-mock-delete]').addEventListener('click', async () => {
  if (!confirm('Delete this mock and all its stubs? This cannot be undone.')) return;
  const result = await call(api(), { method: 'DELETE', headers: auth() });
  if (result.error && result.status !== 404) {
    show($('[data-mock-error]'), result.error);
    return;
  }
  forget();
});
document.addEventListener('visibilitychange', () => {
  if (!document.hidden && key) pollRequests();
});

const fromLink = parseKey(location.hash);
const saved = store.read();
if (fromLink) open(fromLink);
else if (saved?.slug && saved?.token) open(saved);
wireCopyButtons();
