import { $, show, wireCopyButtons } from './common.js';
import { call, toCurl } from './http.js';
import {
  DYNAMIC_VALUES, METHODS, OPS, RECIPES, TARGETS, binLink, blankAnswer, blankCondition, blankRule, duplicate, flow, headerLines, mergeRequests, move,
  outcomeText, outcomeTone, parseBinHash, parseHeaderLines, previewRequest, recipe, routingFromForm, snippets, summary, tunnelCommand,
} from './bin.js';

const KEY = 'mockhub-bin';
const SESSION_KEY = 'mockhub-bin-session';
const POLL_MS = 2000;
const SETTINGS_MS = 10000;
let bin = null;
let after = 0;
let requests = [];
let selected = null;
let timer = null;
let settingsTimer = null;
let settingsRetry = null;
let oldestKept = 1;
let settings = null;
let rules = [];
let rulesOn = true;
let editing = null;
let draft = null;
let polled = false;
let noteTimer = null;

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

const session = {
  read() {
    try {
      return JSON.parse(sessionStorage.getItem(SESSION_KEY) ?? 'null');
    } catch (e) {
      return null;
    }
  },
  write(value) {
    try {
      if (value) sessionStorage.setItem(SESSION_KEY, JSON.stringify(value));
      else sessionStorage.removeItem(SESSION_KEY);
    } catch (e) {
      return;
    }
  },
};

const binUrl = () => `${location.origin}/__public/bins/${bin.id}`;
const auth = (json) => (json ? { 'X-Bin-Token': bin.token, 'Content-Type': 'application/json' } : { 'X-Bin-Token': bin.token });
const inspect = (part = '') => `/__public/inspect/${bin.id}${part}`;

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === undefined || v === null || v === false) continue;
    if (k === 'class') node.className = v;
    else if (k.startsWith('on')) node.addEventListener(k.slice(2), v);
    else if (k === 'value') node.value = v;
    else if (k === 'checked') node.checked = Boolean(v);
    else node.setAttribute(k, v === true ? '' : v);
  }
  node.append(...children.filter((c) => c !== null && c !== undefined && c !== ''));
  return node;
}

function options(list, current) {
  return list.map((o) => {
    const value = typeof o === 'string' ? o : o.value;
    const label = typeof o === 'string' ? o : o.label;
    const option = el('option', { value }, label);
    option.selected = value === current;
    return option;
  });
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

function headerList(headers) {
  const list = el('dl', { class: 'tool-headers' });
  const entries = Object.entries(headers ?? {});
  if (!entries.length) return el('p', { class: 'tool-hint' }, 'No headers.');
  for (const [name, value] of entries) list.append(el('dt', {}, name), el('dd', {}, value));
  return list;
}

function renderList() {
  const list = $('[data-bin-list]');
  list.replaceChildren(...requests.map((r) => {
    const tone = outcomeTone(r.outcome);
    const button = el('button', { type: 'button', class: 'tool-request', 'aria-pressed': String(selected === r.seq) },
      el('span', { class: `jack ${r.method.toLowerCase()}` }, r.method),
      el('span', { class: 'tool-request-path' }, r.path + (r.query ? `?${r.query}` : '')),
      r.event ? el('span', { class: 'bin-event', title: 'Event' }, r.event) : null,
      r.outcome ? el('span', { class: `tool-badge ${tone}`, title: outcomeText(r.outcome) }, String(r.outcome.status)) : null,
      el('span', { class: 'tool-request-time' }, time(r.at)));
    button.addEventListener('click', () => {
      selected = r.seq;
      renderList();
      renderDetail();
    });
    return el('li', {}, button);
  }));
  $('[data-bin-empty]').hidden = requests.length > 0;
  const oldestShown = requests.length ? requests[requests.length - 1].seq : 0;
  $('[data-bin-older]').hidden = !requests.length || oldestShown <= oldestKept;
}

function setCount(total) {
  $('[data-bin-count]').textContent = String(Math.max(0, total - oldestKept + 1));
}

function renderDetail() {
  const r = requests.find((x) => x.seq === selected);
  const box = $('[data-bin-detail]');
  const curl = $('[data-bin-curl]');
  $('[data-bin-curl-copy]').hidden = !r;
  if (!r) {
    box.replaceChildren(el('p', { class: 'tool-hint' }, 'Pick a request to see what was sent and what the bin answered.'));
    curl.hidden = true;
    return;
  }
  const cut = r.truncated ? el('p', { class: 'tool-hint' }, `The body was ${r.size} bytes; the first 64 KB are kept.`) : null;
  const sent = el('div', { class: 'bin-side' },
    el('h4', { class: 'tool-sub' }, 'Request'),
    el('p', { class: 'tool-request-line' }, el('span', { class: `jack ${r.method.toLowerCase()}` }, r.method), ` ${r.path}${r.query ? `?${r.query}` : ''}`),
    el('p', { class: 'tool-hint' }, new Date(r.at).toLocaleString(), r.event ? ' · event ' : '', r.event ? el('span', { class: 'bin-event' }, r.event) : null),
    headerList(r.headers),
    el('pre', { class: 'tool-out tool-wrap' }, r.body ? pretty(r.body) : '(empty body)'), cut);
  const o = r.outcome;
  const answered = o
    ? el('div', { class: 'bin-side' },
      el('h4', { class: 'tool-sub' }, 'Answer'),
      el('p', {}, el('span', { class: `tool-badge ${outcomeTone(o)}` }, outcomeText(o))),
      headerList(o.headers),
      el('pre', { class: 'tool-out tool-wrap' }, o.body ? pretty(o.body) : '(empty body)'),
      o.truncated ? el('p', { class: 'tool-hint' }, 'The answer was longer; the first 16 KB are shown.') : null)
    : el('div', { class: 'bin-side' }, el('h4', { class: 'tool-sub' }, 'Answer'), el('p', { class: 'tool-hint' }, 'Not recorded for this request.'));
  box.replaceChildren(el('div', { class: 'bin-pair' }, sent, answered));
  curl.textContent = toCurl({ method: r.method, url: `${binUrl()}${r.path === '/' ? '' : r.path}${r.query ? `?${r.query}` : ''}`, headers: r.headers, body: r.body });
}

async function poll() {
  clearTimeout(timer);
  if (!bin) return;
  if (document.hidden && polled) {
    timer = setTimeout(poll, POLL_MS);
    return;
  }
  const result = await call(`${inspect()}?after=${after}`, { headers: auth() });
  if (!bin) return;
  polled = true;
  if (result.status === 404) {
    forget('This bin has expired. Create a new one.');
    return;
  }
  show($('[data-bin-error]'), result.error);
  if (result.json) {
    oldestKept = result.json.oldestKept ?? 1;
    const fresh = result.json.requests ?? [];
    if (fresh.length) {
      const gap = after > 0 && fresh[fresh.length - 1].seq > after + 1;
      requests = gap ? fresh : mergeRequests(requests, fresh);
      after = Math.max(after, ...fresh.map((r) => r.seq));
      if (selected === null) selected = requests[0].seq;
      renderDetail();
    }
    requests = requests.filter((r) => r.seq >= oldestKept);
    setCount(result.json.total ?? 0);
    renderList();
  }
  $('[data-bin-polling]').textContent = result.error ? 'Retrying…' : 'Listening for requests';
  timer = setTimeout(poll, result.status === 429 ? POLL_MS * 5 : POLL_MS);
}

async function loadOlder() {
  const before = requests[requests.length - 1]?.seq;
  if (!before) return;
  const button = $('[data-bin-older]');
  button.disabled = true;
  const result = await call(`${inspect()}?before=${before}`, { headers: auth() });
  button.disabled = false;
  show($('[data-bin-error]'), result.error);
  if (!result.json) return;
  requests = mergeRequests(requests, result.json.requests ?? []);
  oldestKept = result.json.oldestKept ?? oldestKept;
  renderList();
}

function applySettings(value) {
  settings = value;
  rules = value.rules ?? [];
  rulesOn = value.rulesEnabled !== false;
  $('[data-bin-keeps]').textContent = String(value.limits?.keepsMb ?? 50);
  $('[data-bin-ai]').hidden = !value.ai;
  const form = $('[data-bin-routing]');
  const routing = value.routing ?? { mode: 'mock' };
  form.querySelector(`input[name="mode"][value="${routing.mode}"]`).checked = true;
  form.elements.target.value = routing.mode === 'forward' ? routing.target ?? '' : '';
  form.elements.pathPrefix.value = routing.mode === 'forward' ? routing.pathPrefix ?? '' : '';
  form.elements.tunnelPrefix.value = routing.mode === 'tunnel' ? routing.pathPrefix ?? '' : '';
  form.elements.insecure.checked = Boolean(routing.insecure);
  form.elements.requestHeaders.value = headerLines(routing.requestHeaders);
  form.elements.responseHeaders.value = headerLines(routing.responseHeaders);
  $('[data-bin-status]').value = value.reply?.status ?? 200;
  $('[data-bin-type]').value = value.reply?.contentType ?? 'application/json';
  $('[data-bin-body]').value = value.reply?.body ?? '';
  $('[data-bin-template]').checked = Boolean(value.reply?.template);
  renderTunnel(value.tunnel);
  renderRules();
  renderMode();
}

function renderTunnel(tunnel) {
  const badge = $('[data-bin-tunnel-state]');
  const mode = currentMode();
  badge.textContent = mode === 'tunnel' ? (tunnel?.online ? 'Tunnel connected' : 'Tunnel not connected') : '';
  badge.className = `tool-badge ${tunnel?.online ? 'ok' : 'warn'}`;
}

async function loadSettings(attempt = 0) {
  clearTimeout(settingsRetry);
  if (!bin) return;
  const result = await call(inspect('/settings'), { headers: auth() });
  if (!bin) return;
  if (result.json && !result.error) {
    show($('[data-bin-rules-error]'), '');
    applySettings(result.json);
    return;
  }
  if (result.status === 404) return;
  show($('[data-bin-rules-error]'), `${result.error ?? 'The bin settings could not be loaded.'} Retrying…`);
  const wait = (result.json?.retryAfter ? result.json.retryAfter * 1000 : 0) || Math.min(30000, 2000 * 2 ** attempt);
  settingsRetry = setTimeout(() => loadSettings(attempt + 1), wait);
}

async function refreshTunnel() {
  clearTimeout(settingsTimer);
  if (!bin) return;
  if (!document.hidden && currentMode() === 'tunnel') {
    const result = await call(inspect('/settings'), { headers: auth() });
    if (result.json && !result.error) renderTunnel(result.json.tunnel);
  }
  settingsTimer = setTimeout(refreshTunnel, SETTINGS_MS);
}

function renderRules() {
  $('[data-bin-rules-on]').checked = rulesOn;
  $('[data-bin-rule-count]').textContent = rules.length ? String(rules.length) : '';
  $('[data-bin-rules-empty]').hidden = rules.length > 0;
  const list = $('[data-bin-rules]');
  list.classList.toggle('off', !rulesOn);
  $('[data-bin-rules-off]').hidden = rulesOn || rules.length === 0;
  list.replaceChildren(...rules.map((rule, i) => {
    const item = el('li', { class: `bin-rule${rule.enabled === false ? ' off' : ''}`, draggable: 'true', 'data-index': String(i) },
      el('span', { class: 'bin-grip', 'aria-hidden': 'true' }, '⋮⋮'),
      el('input', { type: 'checkbox', checked: rule.enabled !== false, 'aria-label': `Use ${rule.name || `rule ${i + 1}`}`, onchange: (e) => saveRules(rules.map((r, j) => (j === i ? { ...r, enabled: e.target.checked } : r))) }),
      el('span', { class: 'bin-rule-text' },
        el('strong', {}, rule.name || `Rule ${i + 1}`),
        el('span', { class: 'tool-hint' }, summary(rule))),
      el('span', { class: 'bin-rule-actions' },
        el('button', { type: 'button', class: 'btn ghost small', 'aria-label': 'Move up', disabled: i === 0, onclick: () => saveRules(move(rules, i, i - 1)) }, '↑'),
        el('button', { type: 'button', class: 'btn ghost small', 'aria-label': 'Move down', disabled: i === rules.length - 1, onclick: () => saveRules(move(rules, i, i + 1)) }, '↓'),
        el('button', { type: 'button', class: 'btn ghost small', onclick: () => openEditor(structuredClone(rule), i) }, 'Edit'),
        el('button', { type: 'button', class: 'btn ghost small', onclick: () => saveRules(duplicate(rules, i)) }, 'Duplicate'),
        el('button', { type: 'button', class: 'btn ghost small', onclick: () => saveRules(rules.filter((_, j) => j !== i)) }, 'Delete')));
    item.addEventListener('dragstart', (e) => {
      e.dataTransfer.setData('text/plain', String(i));
      e.dataTransfer.effectAllowed = 'move';
      item.classList.add('dragging');
    });
    item.addEventListener('dragend', () => item.classList.remove('dragging'));
    item.addEventListener('dragover', (e) => {
      e.preventDefault();
      item.classList.add('drop');
    });
    item.addEventListener('dragleave', () => item.classList.remove('drop'));
    item.addEventListener('drop', (e) => {
      e.preventDefault();
      item.classList.remove('drop');
      const from = Number(e.dataTransfer.getData('text/plain'));
      if (!Number.isNaN(from) && from !== i) saveRules(move(rules, from, i));
    });
    return item;
  }));
  renderFlow();
}

async function saveRules(next, enabled = rulesOn, message = 'Rules saved. New requests use them.') {
  const result = await call(inspect('/rules'), { method: 'PUT', headers: auth(true), body: JSON.stringify({ rulesEnabled: enabled, rules: next }) });
  show($('[data-bin-rules-error]'), result.error);
  if (result.json && !result.error) {
    applySettings(result.json);
    flashNote(message);
  }
  return result;
}

function flashNote(message) {
  const note = $('[data-bin-rules-note]');
  clearTimeout(noteTimer);
  note.textContent = message;
  note.hidden = false;
  noteTimer = setTimeout(() => {
    note.hidden = true;
  }, 8000);
}

async function preview(text, dynamic, out, note) {
  out.hidden = false;
  if (!dynamic) {
    out.textContent = text || '(empty body)';
    note.textContent = 'Sent exactly as written. Tick "Fill in dynamic values" to use {{…}}.';
    return;
  }
  const latest = requests[0];
  note.textContent = 'Building…';
  const result = await call('/__public/render', { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ template: text || ' ', request: previewRequest(latest) }) });
  if (result.json?.output !== undefined && !result.json?.error) {
    out.textContent = pretty(result.json.output);
    note.textContent = latest ? `Built from your latest request, ${latest.method} ${latest.path}.` : 'Built from a sample request. Send one to the bin to preview with your own.';
  } else {
    out.textContent = result.json?.error ?? result.error ?? 'The preview failed.';
    note.textContent = 'The template has a problem; requests would get a 500 until it is fixed.';
  }
}

function renderRecipes() {
  $('[data-bin-recipes]').replaceChildren(...RECIPES.map((r) => el('button', { type: 'button', class: 'bin-recipe', onclick: () => openEditor(recipe(r.id), -1) },
    el('strong', {}, r.label), el('span', {}, r.hint))));
}

function currentMode() {
  return $('[data-bin-routing]').querySelector('input[name="mode"]:checked')?.value ?? 'mock';
}

function renderMode() {
  const mode = currentMode();
  document.querySelectorAll('[data-bin-mode]').forEach((panel) => {
    panel.hidden = panel.dataset.binMode !== mode;
  });
  renderTunnel(settings?.tunnel);
  renderFlow();
}

function renderFlow() {
  const form = $('[data-bin-routing]');
  const routing = { mode: currentMode(), target: form.elements.target.value, pathPrefix: form.elements.pathPrefix.value };
  const active = rules.filter((r) => r.enabled !== false).length;
  $('[data-bin-flow]').replaceChildren(...flow(routing, rulesOn, active).map((step) => el('li', {}, step)));
}

function openEditor(rule, index) {
  draft = rule;
  editing = index;
  $('#bin-editor-title').textContent = index < 0 ? 'New rule' : 'Edit rule';
  $('[data-rule-name]').value = draft.name ?? '';
  $('[data-rule-method]').replaceChildren(...options(METHODS, draft.method ?? 'ANY'));
  show($('[data-rule-error]'), '');
  renderConditions();
  renderAnswers();
  $('[data-bin-editor]').showModal();
}

function closeEditor() {
  $('[data-bin-editor]').close();
  draft = null;
  editing = null;
}

function renderConditions() {
  $('[data-rule-conditions]').replaceChildren(...draft.conditions.map((c, i) => {
    const target = TARGETS.find((t) => t.value === c.target) ?? TARGETS[0];
    return el('li', { class: 'bin-condition' },
      el('select', { class: 'tool-input', 'aria-label': 'Look at', onchange: (e) => { c.target = e.target.value; renderConditions(); } }, ...options(TARGETS, c.target)),
      target.keyed ? el('input', { class: 'tool-input', type: 'text', spellcheck: 'false', 'aria-label': 'Name', placeholder: target.placeholder, value: c.key ?? '', oninput: (e) => { c.key = e.target.value; } }) : null,
      el('select', { class: 'tool-input', 'aria-label': 'Test', onchange: (e) => { c.op = e.target.value; renderConditions(); } }, ...options(OPS, c.op)),
      c.op === 'exists' ? null : el('input', { class: 'tool-input', type: 'text', spellcheck: 'false', 'aria-label': 'Value', value: c.value ?? '', oninput: (e) => { c.value = e.target.value; } }),
      el('button', { type: 'button', class: 'btn ghost small', 'aria-label': 'Remove condition', onclick: () => { draft.conditions.splice(i, 1); renderConditions(); } }, 'Remove'));
  }));
  if (!draft.conditions.length) $('[data-rule-conditions]').append(el('li', { class: 'tool-hint' }, 'No conditions: every request with this method matches.'));
}

function insertAt(textarea, text) {
  const start = textarea.selectionStart ?? textarea.value.length;
  const end = textarea.selectionEnd ?? start;
  textarea.value = textarea.value.slice(0, start) + text + textarea.value.slice(end);
  textarea.selectionStart = textarea.selectionEnd = start + text.length;
  textarea.dispatchEvent(new Event('input'));
  textarea.focus();
}

function previewBlock(text, dynamic) {
  const out = el('pre', { class: 'tool-out tool-wrap', hidden: true });
  const note = el('span', { class: 'tool-hint' });
  const button = el('button', { type: 'button', class: 'btn ghost small', onclick: () => preview(text(), dynamic(), out, note) }, 'Preview');
  return el('div', { class: 'bin-preview' }, el('div', { class: 'bin-preview-row' }, button, note), out);
}

function renderAnswers() {
  const weighted = draft.answers.length > 1;
  $('[data-rule-answers]').replaceChildren(...draft.answers.map((a, i) => {
    const body = el('textarea', { class: 'tool-input', rows: '5', spellcheck: 'false', 'aria-label': 'Body', value: a.body ?? '', oninput: (e) => { a.body = e.target.value; } });
    const template = el('input', { type: 'checkbox', checked: a.template, onchange: (e) => { a.template = e.target.checked; } });
    const dynamic = el('select', { class: 'tool-input bin-dynamic', 'aria-label': 'Insert a dynamic value', onchange: (e) => {
      if (!e.target.value) return;
      insertAt(body, e.target.value);
      a.template = true;
      template.checked = true;
      e.target.value = '';
    } }, el('option', { value: '' }, 'Insert a dynamic value…'), ...DYNAMIC_VALUES.map((d) => el('option', { value: d.text }, d.label)));
    return el('li', { class: 'bin-answer' },
      el('div', { class: 'tool-row' },
        weighted ? el('div', {}, el('label', { class: 'tool-label', for: `ans-w-${i}` }, 'Weight'), el('input', { id: `ans-w-${i}`, class: 'tool-input', type: 'number', min: '1', max: '100', value: a.weight, oninput: (e) => { a.weight = Number(e.target.value); } })) : null,
        el('div', {}, el('label', { class: 'tool-label', for: `ans-s-${i}` }, 'Status'), el('input', { id: `ans-s-${i}`, class: 'tool-input', type: 'number', min: '100', max: '599', value: a.status, oninput: (e) => { a.status = Number(e.target.value); } })),
        el('div', {}, el('label', { class: 'tool-label', for: `ans-d-${i}` }, 'Delay, ms'), el('input', { id: `ans-d-${i}`, class: 'tool-input', type: 'number', min: '0', max: '15000', step: '100', value: a.delayMs, oninput: (e) => { a.delayMs = Number(e.target.value); } })),
        weighted ? el('div', { class: 'bin-answer-remove' }, el('button', { type: 'button', class: 'btn ghost small', onclick: () => { draft.answers.splice(i, 1); renderAnswers(); } }, 'Remove')) : null),
      el('label', { class: 'tool-label', for: `ans-h-${i}` }, 'Headers, one per line'),
      el('textarea', { id: `ans-h-${i}`, class: 'tool-input', rows: '2', spellcheck: 'false', value: headerLines(a.headers), oninput: (e) => { a.headers = parseHeaderLines(e.target.value); } }),
      el('div', { class: 'bin-body-head' }, el('span', { class: 'tool-label' }, 'Body'), dynamic),
      body,
      el('label', { class: 'bin-check' }, template, ' Fill in dynamic values ({{…}}) from each request'),
      previewBlock(() => a.body, () => a.template));
  }));
}

async function saveDraft(e) {
  e.preventDefault();
  draft.name = $('[data-rule-name]').value.trim();
  draft.method = $('[data-rule-method]').value;
  const next = editing < 0 ? [...rules, draft] : rules.map((r, i) => (i === editing ? draft : r));
  const button = $('[data-rule-save]');
  button.disabled = true;
  const result = await saveRules(next, rulesOn, `Saved "${draft.name || 'Rule'}". ${rulesOn ? 'It answers new requests that match.' : 'Rules are off; tick Rules on to use it.'}`);
  button.disabled = false;
  if (result.error) {
    show($('[data-rule-error]'), result.error);
    show($('[data-bin-rules-error]'), '');
    return;
  }
  closeEditor();
}

async function saveRouting(e) {
  e.preventDefault();
  const form = e.target;
  const mode = currentMode();
  const routing = routingFromForm({
    mode,
    target: mode === 'forward' ? form.elements.target.value : '',
    pathPrefix: mode === 'forward' ? form.elements.pathPrefix.value : mode === 'tunnel' ? form.elements.tunnelPrefix.value : '',
    insecure: form.elements.insecure.checked,
    requestHeaders: form.elements.requestHeaders.value,
    responseHeaders: form.elements.responseHeaders.value,
  });
  const note = $('[data-bin-routing-note]');
  if (mode === 'mock') {
    const reply = JSON.stringify({ status: Number($('[data-bin-status]').value), contentType: $('[data-bin-type]').value, body: $('[data-bin-body]').value, template: $('[data-bin-template]').checked });
    const saved = await call(inspect('/response'), { method: 'PUT', headers: auth(true), body: reply });
    if (saved.error) {
      note.textContent = saved.error;
      return;
    }
  }
  const result = await call(inspect('/routing'), { method: 'PUT', headers: auth(true), body: JSON.stringify(routing) });
  note.textContent = result.error ?? 'Saved. New requests follow this route.';
  if (result.json && !result.error) applySettings(result.json);
}

function renderSignIn(message = '') {
  const signedIn = session.read();
  $('[data-bin-signin]').hidden = Boolean(signedIn);
  $('[data-bin-describe]').hidden = !signedIn;
  $('[data-bin-signed-in]').textContent = signedIn ? `Signed in as ${signedIn.name}` : '';
  $('[data-bin-signin-note]').textContent = message;
}

async function signIn(e) {
  e.preventDefault();
  const form = e.target;
  const note = $('[data-bin-signin-note]');
  note.textContent = 'Signing in…';
  const body = JSON.stringify({ username: form.elements.username.value.trim(), password: form.elements.password.value });
  const result = await call('/__admin/mockhub/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body });
  form.elements.password.value = '';
  if (result.error || !result.json?.token) {
    note.textContent = result.status === 401 ? 'Wrong username or password.' : result.error ?? 'Signing in failed.';
    return;
  }
  const user = result.json.user ?? {};
  session.write({ token: result.json.token, name: user.displayName || user.username || form.elements.username.value.trim() });
  renderSignIn();
}

async function draftWithAi(e) {
  e.preventDefault();
  const text = $('[data-bin-ai-text]').value.trim();
  const note = $('[data-bin-ai-note]');
  if (!text) {
    note.textContent = 'Describe what the rule should do first.';
    return;
  }
  const button = $('[data-bin-ai-go]');
  button.disabled = true;
  note.textContent = 'Drafting…';
  const signedIn = session.read();
  const result = await call(inspect('/draft'), { method: 'POST', headers: { ...auth(true), Authorization: `Bearer ${signedIn?.token ?? ''}` }, body: JSON.stringify({ description: text }) });
  button.disabled = false;
  if (result.status === 401) {
    session.write(null);
    renderSignIn('Your sign-in has ended. Sign in again.');
    return;
  }
  if (result.error || !result.json?.rule) {
    note.textContent = result.error ?? 'No rule came back. Try describing it differently.';
    return;
  }
  note.textContent = 'Check the draft, then save it.';
  const rule = result.json.rule;
  delete rule.id;
  openEditor({ ...blankRule(), ...rule }, -1);
}

function open(value) {
  bin = value;
  after = 0;
  requests = [];
  selected = null;
  oldestKept = 1;
  polled = false;
  $('[data-bin-start]').hidden = true;
  $('[data-bin-live]').hidden = false;
  $('[data-bin-url]').textContent = binUrl();
  $('[data-bin-link]').textContent = binLink(location.origin, location.pathname, bin.id, bin.token);
  const code = snippets(binUrl());
  document.querySelectorAll('[data-bin-code]').forEach((pre) => {
    pre.textContent = code[pre.dataset.binCode];
  });
  $('[data-bin-tunnel-cmd]').textContent = tunnelCommand(location.origin, bin.id, bin.token);
  setCount(0);
  renderList();
  renderDetail();
  loadSettings();
  poll();
  refreshTunnel();
}

function forget(message) {
  clearTimeout(timer);
  clearTimeout(settingsTimer);
  clearTimeout(settingsRetry);
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
  const result = await call(inspect(), { method: 'DELETE', headers: auth() });
  show($('[data-bin-error]'), result.error);
  if (result.error) return;
  requests = [];
  selected = null;
  oldestKept = after + 1;
  setCount(after);
  renderList();
  renderDetail();
});

$('[data-bin-older]').addEventListener('click', loadOlder);
$('[data-bin-rules-on]').addEventListener('change', (e) => saveRules(rules, e.target.checked, e.target.checked ? 'Rules are on.' : 'Rules are off. Every request goes to the routing.'));
$('[data-bin-rule-new]').addEventListener('click', () => openEditor(blankRule(), -1));
$('[data-rule-add-condition]').addEventListener('click', () => {
  if (draft.conditions.length >= 10) return;
  draft.conditions.push(blankCondition());
  renderConditions();
});
$('[data-rule-add-answer]').addEventListener('click', () => {
  if (draft.answers.length >= 5) return;
  draft.answers.push(blankAnswer());
  renderAnswers();
});
$('[data-bin-editor-form]').addEventListener('submit', saveDraft);
$('[data-bin-editor-close]').addEventListener('click', closeEditor);
$('[data-bin-editor-cancel]').addEventListener('click', closeEditor);
$('[data-bin-routing]').addEventListener('submit', saveRouting);
$('[data-bin-routing]').addEventListener('change', (e) => {
  if (e.target.name === 'mode') renderMode();
});
$('[data-bin-routing]').addEventListener('input', (e) => {
  if (e.target.name === 'target' || e.target.name === 'pathPrefix') renderFlow();
});
$('[data-bin-describe]').addEventListener('submit', draftWithAi);
$('[data-bin-signin]').addEventListener('submit', signIn);
$('[data-bin-signout]').addEventListener('click', () => {
  session.write(null);
  renderSignIn();
});

document.addEventListener('visibilitychange', () => {
  if (!document.hidden && bin) poll();
});

renderRecipes();
renderSignIn();
$('[data-bin-insert]').replaceChildren(el('option', { value: '' }, 'Insert a dynamic value…'), ...DYNAMIC_VALUES.map((d) => el('option', { value: d.text }, d.label)));
$('[data-bin-insert]').addEventListener('change', (e) => {
  if (!e.target.value) return;
  insertAt($('[data-bin-body]'), e.target.value);
  $('[data-bin-template]').checked = true;
  e.target.value = '';
});
$('[data-bin-preview]').addEventListener('click', () => preview($('[data-bin-body]').value, $('[data-bin-template]').checked, $('[data-bin-preview-out]'), $('[data-bin-preview-note]')));

function takeLinked() {
  const linked = parseBinHash(location.hash);
  if (linked) history.replaceState(null, '', location.pathname + location.search);
  return linked;
}

window.addEventListener('hashchange', () => {
  const linked = takeLinked();
  if (!linked) return;
  forget();
  store.write(linked);
  open(linked);
});
const fromLink = takeLinked();
if (fromLink) store.write(fromLink);
const saved = store.read();
if (saved?.id && saved?.token) open(saved);
wireCopyButtons();
