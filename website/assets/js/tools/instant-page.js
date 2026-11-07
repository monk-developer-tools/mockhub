import { $, download, show, wireCopyButtons } from './common.js';
import { call, toCurl } from './http.js';
import { displayPath, exportMappings, fitsForm, formToStub, graphqlRequest, isGraphql, keyHash, MAX_RECORDS, parseKey, parseRecords, resourceName, samplePath, specStart, stubToForm } from './instant.js';

const KEY = 'mockhub-instant-mock';
const POLL_MS = 3000;
let key = null;
let mock = null;
let editing = null;
let jsonMode = false;
let timer = null;
let graphqlPath = null;
let graphqlExamples = [];

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
  loadGraphql();
  return true;
}

async function loadGraphql() {
  const card = $('[data-mock-graphql]');
  const stub = mock.stubs.find(isGraphql);
  if (!stub) {
    card.hidden = true;
    graphqlPath = null;
    return;
  }
  const path = stub.request?.urlPath ?? '/graphql';
  card.hidden = false;
  $('[data-mock-graphql-url]').textContent = `${base()}${path}`;
  if ($('[data-mock-try-path]').value === '/hello') {
    $('[data-mock-try-method]').value = 'POST';
    $('[data-mock-try-path]').value = path;
    $('[data-mock-try-body]').value = '{"query": "{ __typename }"}';
  }
  if (path === graphqlPath && graphqlExamples.length) return;
  graphqlPath = path;
  const result = await call(api('/graphql'), { headers: auth() });
  const endpoint = (result.json?.endpoints ?? []).find((e) => e.path === path);
  graphqlExamples = endpoint?.operations ?? [];
  $('[data-mock-graphql-meta]').textContent = endpoint?.error ?? (result.error ? 'Examples are not available right now' : '');
  const select = $('[data-mock-graphql-example]');
  select.replaceChildren(el('option', { value: '' }, graphqlExamples.length ? 'Pick an example…' : 'No examples'),
    ...graphqlExamples.map((op, i) => el('option', { value: String(i) }, `${op.kind} ${op.field}${op.description ? ` — ${op.description}` : ''}`)));
  if (!$('[data-mock-graphql-query]').value.trim() && graphqlExamples.length) pickExample(0);
}

function pickExample(index) {
  const op = graphqlExamples[index];
  if (!op) return;
  $('[data-mock-graphql-example]').value = String(index);
  $('[data-mock-graphql-query]').value = op.query ?? '';
  const variables = op.variables ?? {};
  $('[data-mock-graphql-variables]').value = Object.keys(variables).length ? JSON.stringify(variables, null, 2) : '';
  renderGraphqlCurl();
}

function graphqlBody() {
  return graphqlRequest($('[data-mock-graphql-query]').value, $('[data-mock-graphql-variables]').value);
}

function renderGraphqlCurl() {
  const pre = $('[data-mock-graphql-curl]');
  try {
    pre.textContent = toCurl({ method: 'POST', url: `${base()}${graphqlPath}`, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(graphqlBody()) });
  } catch (e) {
    pre.textContent = '';
  }
}

async function runGraphql() {
  let body;
  try {
    body = graphqlBody();
  } catch (e) {
    show($('[data-mock-graphql-error]'), e.message);
    return;
  }
  show($('[data-mock-graphql-error]'), '');
  const button = $('[data-mock-graphql-run]');
  button.disabled = true;
  const result = await call(`/${key.slug}${graphqlPath}`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  button.disabled = false;
  $('[data-mock-graphql-meta]').textContent = result.status ? `${result.status} in ${result.ms} ms` : '';
  $('[data-mock-graphql-out]').textContent = result.json ? JSON.stringify(result.json, null, 2) : (result.text || result.error || '(empty body)');
  pollRequests();
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
  graphqlPath = null;
  graphqlExamples = [];
  $('[data-mock-graphql-query]').value = '';
  $('[data-mock-graphql-variables]').value = '';
  $('[data-mock-graphql-out]').textContent = 'Run a query to see the answer.';
  $('[data-mock-try-method]').value = 'GET';
  $('[data-mock-try-path]').value = '/hello';
  $('[data-mock-try-body]').value = '';
  store.write(null);
  history.replaceState(null, '', location.pathname);
  $('[data-mock-live]').hidden = true;
  $('[data-mock-start]').hidden = false;
  $('[data-mock-skipped]').hidden = true;
  show($('[data-mock-create-error]'), message ?? '');
}

function starterBody(kind) {
  if (kind === 'spec' || kind === 'graphql') return specStart(kind, $(`[data-start-text="${kind}"]`).value, $(`[data-start-url="${kind}"]`).value);
  if (kind === 'records') {
    const records = parseRecords($('[data-start-text="records"]').value);
    if (records.length > MAX_RECORDS) throw new Error(`Start with up to ${MAX_RECORDS} records. This file has ${records.length}.`);
    return { from: 'records', resource: $('[data-start-resource]').value.trim(), records };
  }
  if (kind === 'example') {
    const id = $('[data-start-example]').value;
    if (!id) throw new Error('Pick an example first.');
    return { from: 'example', id };
  }
  return null;
}

function showSkipped(skipped) {
  const box = $('[data-mock-skipped]');
  const list = $('[data-mock-skipped-list]');
  list.replaceChildren(...(skipped ?? []).map((reason) => el('li', {}, reason)));
  $('[data-mock-skipped-title]').textContent = 'Some of what you started from was left out';
  box.hidden = !skipped?.length;
}

async function create(e) {
  const button = e.currentTarget;
  show($('[data-mock-create-error]'), '');
  let body;
  try {
    body = starterBody(button.dataset.mockCreate);
  } catch (err) {
    show($('[data-mock-create-error]'), err.message);
    return;
  }
  button.disabled = true;
  const result = await call('/__public/mocks', body
    ? { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }
    : { method: 'POST' });
  button.disabled = false;
  if (result.error || !result.json?.slug) {
    show($('[data-mock-create-error]'), result.error ?? 'The mock could not be created.');
    return;
  }
  await open({ slug: result.json.slug, token: result.json.token });
  showSkipped(result.json.skipped);
}

function readFile(input, kind) {
  const file = input.files?.[0];
  if (!file) return;
  if (file.size > 500 * 1024) {
    show($('[data-mock-create-error]'), 'Keep the file under 500 KB.');
    input.value = '';
    return;
  }
  file.text().then((text) => {
    $(`[data-start-text="${kind}"]`).value = text;
    const url = $(`[data-start-url="${kind}"]`);
    if (url) url.value = '';
    if (kind === 'records') $('[data-start-resource]').value = resourceName(file.name);
    show($('[data-mock-create-error]'), '');
  });
}

async function loadExamples() {
  const select = $('[data-start-example]');
  const result = await call('/__public/mocks/examples');
  const examples = result.json?.examples ?? [];
  if (result.error || !examples.length) {
    select.replaceChildren(el('option', { value: '' }, 'The examples are not available right now'));
    return;
  }
  select.replaceChildren(...examples.map((ex) => el('option', { value: ex.id }, `${ex.name} (${ex.stubs} stubs)`)));
  const about = () => {
    const chosen = examples.find((ex) => ex.id === select.value);
    $('[data-start-example-about]').textContent = chosen?.description ?? '';
  };
  select.addEventListener('change', about);
  about();
}

document.querySelectorAll('[data-mock-create]').forEach((button) => button.addEventListener('click', create));
document.querySelectorAll('[data-start-file]').forEach((input) => input.addEventListener('change', () => readFile(input, input.dataset.startFile)));
document.querySelectorAll('[data-start-url]').forEach((input) => {
  const text = $(`[data-start-text="${input.dataset.startUrl}"]`);
  const sample = text.value;
  input.addEventListener('input', () => {
    if (input.value.trim() && text.value === sample) text.value = '';
  });
});
loadExamples();
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
$('[data-mock-graphql-run]').addEventListener('click', runGraphql);
$('[data-mock-graphql-example]').addEventListener('change', (e) => {
  if (e.target.value !== '') pickExample(Number(e.target.value));
});
['[data-mock-graphql-query]', '[data-mock-graphql-variables]'].forEach((sel) => $(sel).addEventListener('input', renderGraphqlCurl));
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
