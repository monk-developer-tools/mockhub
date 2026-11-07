import { $, show, wireCopyButtons } from './common.js';
import { call } from './http.js';
import { cell, columnsOf, csvPreview, orderRoutes, parseJsonDoc, planRoutes, recordBody, routeLines, routesFromStubs } from './dataapi.js';
import { keyHash, resourceName } from './instant.js';

const tool = $('[data-api-start]').dataset.apiTool;
const KEY = `mockhub-api-${tool}`;
let key = null;
let routes = [];
let editing = null;

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

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (k === 'class') node.className = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else node.setAttribute(k, v);
  }
  node.append(...children.filter((c) => c !== null && c !== undefined));
  return node;
}

const base = () => `${location.origin}/${key.slug}`;
const resourceUrl = (suffix = '') => `/${key.slug}/${$('[data-api-resource]').value}${suffix}`;

function routeItem(method, path) {
  return el('li', {}, el('span', { class: `jack ${method.toLowerCase()}` }, method), el('code', {}, path));
}

function table(target, records, actions) {
  const columns = columnsOf(records);
  const head = el('tr', {}, ...columns.map((c) => el('th', { scope: 'col' }, c)), actions ? el('th', { scope: 'col' }, '') : null);
  target.replaceChildren(el('thead', {}, head), el('tbody', {}, ...records.map((r) => el('tr', {},
    ...columns.map((c) => el('td', {}, cell(r[c]))),
    actions ? el('td', { class: 'api-row-actions' }, ...actions(r)) : null))));
}

function renderPlan() {
  const box = $('[data-api-plan]');
  const error = $('[data-api-create-error]');
  show(error, '');
  try {
    const plan = planRoutes(parseJsonDoc($('[data-api-text]').value), $('[data-api-root]').value.trim() || 'items');
    box.replaceChildren(...plan.routes.map((r) => el('li', {},
      el('code', {}, `/${r.name}`),
      el('span', { class: 'tool-hint' }, r.kind === 'crud' ? ` REST resource · ${r.count} record${r.count === 1 ? '' : 's'}` : ' GET only'))),
    ...plan.skipped.map((s) => el('li', { class: 'tool-hint' }, `Left out: ${s}`)));
    return true;
  } catch (e) {
    box.replaceChildren(el('li', { class: 'tool-hint' }, e.message));
    return false;
  }
}

function renderPreview() {
  const target = $('[data-api-preview]');
  try {
    const preview = csvPreview($('[data-api-text]').value);
    table(target, preview.rows);
    $('[data-api-preview-title]').textContent = `Preview: ${preview.total} row${preview.total === 1 ? '' : 's'}, ${preview.columns.length} column${preview.columns.length === 1 ? '' : 's'}${preview.total > preview.rows.length ? `, first ${preview.rows.length} shown` : ''}`;
    show($('[data-api-create-error]'), '');
    return preview;
  } catch (e) {
    target.replaceChildren();
    $('[data-api-preview-title]').textContent = 'Preview';
    show($('[data-api-create-error]'), e.message);
    return null;
  }
}

function startBody() {
  const root = $('[data-api-root]').value.trim();
  if (tool === 'json') {
    const data = parseJsonDoc($('[data-api-text]').value);
    const plan = planRoutes(data, root || 'items');
    return { body: { from: 'json', data, resource: root || 'items' }, order: plan.routes.map((r) => r.name) };
  }
  const preview = csvPreview($('[data-api-text]').value);
  return { body: { from: 'records', resource: root, records: preview.records }, order: [root] };
}

async function create(e) {
  const button = e.currentTarget;
  let start;
  try {
    start = startBody();
  } catch (err) {
    show($('[data-api-create-error]'), err.message);
    return;
  }
  button.disabled = true;
  const result = await call('/__public/mocks', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(start.body) });
  button.disabled = false;
  if (result.error || !result.json?.slug) {
    show($('[data-api-create-error]'), result.error ?? 'The API could not be created.');
    return;
  }
  await open({ slug: result.json.slug, token: result.json.token, order: start.order }, result.json.skipped ?? []);
}

function showSkipped(skipped) {
  $('[data-api-skipped-list]').replaceChildren(...skipped.map((s) => el('li', {}, s)));
  $('[data-api-skipped-title]').textContent = `${skipped.length} left out`;
  $('[data-api-skipped]').hidden = !skipped.length;
}

async function open(value, skipped = []) {
  key = value;
  const result = await call(`/__public/mocks/${key.slug}`, { headers: { 'X-Mock-Token': key.token } });
  if (result.status === 404) {
    forget('That API no longer exists. They are deleted 7 days after their last change.');
    return;
  }
  if (result.error) {
    show($('[data-api-create-error]'), result.error);
    return;
  }
  store.write(key);
  routes = orderRoutes(routesFromStubs(result.json.stubs), key.order ?? []);
  $('[data-api-start]').hidden = true;
  $('[data-api-live]').hidden = false;
  $('[data-api-base]').textContent = base();
  $('[data-api-edit]').href = `/tools/instant-mock/${keyHash(key)}`;
  showSkipped(skipped);
  $('[data-api-routes]').replaceChildren(...routes.flatMap((r) => routeLines(r).map(([m, p]) => routeItem(m, p))));
  const crud = routes.filter((r) => r.kind === 'crud');
  $('[data-api-records-card]').hidden = !crud.length;
  $('[data-api-resource]').replaceChildren(...crud.map((r) => el('option', { value: r.name }, `/${r.name}`)));
  if (crud.length) {
    resetEditor();
    await loadRecords();
  }
}

function forget(message) {
  key = null;
  routes = [];
  store.write(null);
  $('[data-api-live]').hidden = true;
  $('[data-api-start]').hidden = false;
  show($('[data-api-create-error]'), message ?? '');
}

async function loadRecords() {
  const result = await call(resourceUrl());
  const records = Array.isArray(result.json) ? result.json : [];
  $('[data-api-count]').textContent = result.error ? result.error : `${records.length} record${records.length === 1 ? '' : 's'} at ${base()}/${$('[data-api-resource]').value}`;
  table($('[data-api-table]'), records, (r) => [
    el('button', { type: 'button', class: 'btn ghost small', 'data-api-row-edit': String(r.id), onclick: () => startEdit(r) }, 'Edit'),
    el('button', { type: 'button', class: 'btn ghost small', 'data-api-row-delete': String(r.id), onclick: () => removeRecord(r) }, 'Delete'),
  ]);
}

function resetEditor() {
  editing = null;
  $('[data-api-editor-title]').textContent = 'Add a record';
  $('[data-api-save]').textContent = 'Add record';
  $('[data-api-cancel]').hidden = true;
  const heads = [...$('[data-api-table]').querySelectorAll('thead th')].map((th) => th.textContent).filter((h) => h && h !== 'id');
  $('[data-api-record]').value = JSON.stringify(Object.fromEntries((heads.length ? heads : ['name']).map((h) => [h, ''])), null, 2);
  show($('[data-api-error]'), '');
}

function startEdit(record) {
  editing = record;
  const { id, ...rest } = record;
  $('[data-api-editor-title]').textContent = `Change record ${id}`;
  $('[data-api-save]').textContent = 'Save changes';
  $('[data-api-cancel]').hidden = false;
  $('[data-api-record]').value = JSON.stringify(rest, null, 2);
  $('[data-api-record]').focus();
}

function showLast(method, url, result) {
  const pre = $('[data-api-last]');
  pre.hidden = false;
  pre.textContent = `${method} ${url} → ${result.status}\n${result.json ? JSON.stringify(result.json, null, 2) : result.text ?? ''}`;
}

async function saveRecord(e) {
  e.preventDefault();
  let body;
  try {
    body = recordBody($('[data-api-record]').value);
  } catch (err) {
    show($('[data-api-error]'), err.message);
    return;
  }
  const method = editing ? 'PUT' : 'POST';
  const url = editing ? resourceUrl(`/${encodeURIComponent(editing.id)}`) : resourceUrl();
  const result = await call(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  showLast(method, url, result);
  show($('[data-api-error]'), result.error);
  if (result.error) return;
  $('[data-api-note]').textContent = editing ? `Record ${editing.id} changed.` : `Record ${result.json?.id ?? ''} added.`;
  await loadRecords();
  resetEditor();
}

async function removeRecord(record) {
  const url = resourceUrl(`/${encodeURIComponent(record.id)}`);
  const result = await call(url, { method: 'DELETE' });
  showLast('DELETE', url, result);
  show($('[data-api-error]'), result.error);
  if (result.error) return;
  $('[data-api-note]').textContent = `Record ${record.id} deleted.`;
  if (editing?.id === record.id) resetEditor();
  await loadRecords();
}

function readFile(input) {
  const file = input.files?.[0];
  if (!file) return;
  if (file.size > 600 * 1024) {
    show($('[data-api-create-error]'), 'Keep the file under 600 KB.');
    input.value = '';
    return;
  }
  file.text().then((text) => {
    $('[data-api-text]').value = text;
    if (tool === 'csv') $('[data-api-root]').value = resourceName(file.name);
    refreshStart();
  });
}

function refreshStart() {
  if (tool === 'json') renderPlan();
  else renderPreview();
}

$('[data-api-text]').addEventListener('input', refreshStart);
$('[data-api-root]').addEventListener('input', refreshStart);
$('[data-api-file]').addEventListener('change', (e) => readFile(e.target));
$('[data-api-create]').addEventListener('click', create);
$('[data-api-editor]').addEventListener('submit', saveRecord);
$('[data-api-cancel]').addEventListener('click', resetEditor);
$('[data-api-refresh]').addEventListener('click', loadRecords);
$('[data-api-resource]').addEventListener('change', async () => {
  $('[data-api-note]').textContent = '';
  $('[data-api-last]').hidden = true;
  await loadRecords();
  resetEditor();
});
$('[data-api-delete]').addEventListener('click', async () => {
  if (!key) return;
  if (!confirm('Delete this API and all its records? This cannot be undone.')) return;
  const result = await call(`/__public/mocks/${key.slug}`, { method: 'DELETE', headers: { 'X-Mock-Token': key.token } });
  if (result.error && result.status !== 404) {
    show($('[data-api-error]'), result.error);
    return;
  }
  forget();
  refreshStart();
});

refreshStart();
const saved = store.read();
if (saved?.slug && saved?.token) open(saved);
wireCopyButtons();
