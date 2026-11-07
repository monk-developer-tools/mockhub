import { $, show, wireCopyButtons } from './common.js';
import { call, toCurl } from './http.js';
import {
  blankCrud, checkCrud, crudRoutes, normalCrudPath,
  AUTH_TYPES, CALLOUT_METHODS, blankCallout, calloutLost, calloutText, calloutTone, checkCallout,
  CONTENT_TYPES, MAX_FILE_BYTES, METHODS, OPS, RECIPES, base64Size, contentTypeOf, fileSize, opsFor, percents, prettyBody, searchValues, stateRows, withContentType, TARGETS, binLink, blankAnswer, blankCondition, blankRule, duplicate, flow, headerLines, mergeRequests, move,
  outcomeText, outcomeTone, parseBinHash, parseShareHash, filterQuery, optionsFromForm, optionsSummary, filtered, multipartParts, requestBytes, ruleFromRequest, shareLink, routeSummary, rememberBin, forgetBin, renameBin, parseHeaderLines, previewRequest, recipe, routingFromForm, snippets, summary, tunnelCommand,
} from './bin.js';

const KEY = 'mockhub-bin';
const LIST_KEY = 'mockhub-bins';
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
let stashedCallout = null;
let stashedCrud = null;
let polled = false;
let noteTimer = null;
let filter = { q: '', method: '', status: '' };
let deletedCount = 0;
let noOlder = false;
let generation = 0;
let savedProject = null;
const plain = { request: false, answer: false };

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

const savedBins = {
  read() {
    try {
      const list = JSON.parse(localStorage.getItem(LIST_KEY) ?? '[]');
      return Array.isArray(list) ? list.filter((b) => b?.id && b?.token) : [];
    } catch (e) {
      return [];
    }
  },
  write(list) {
    try {
      localStorage.setItem(LIST_KEY, JSON.stringify(list));
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
      r.callout ? el('span', { class: `tool-badge ${calloutTone(r.callout, r.at)}`, title: calloutText(r.callout, r.at), 'data-request-callout-mark': '' },
        `↗ ${r.callout.state === 'sent' ? r.callout.status : r.callout.state === 'scheduled' && !calloutLost(r.callout, r.at) ? '…' : '✕'}`) : null,
      el('span', { class: 'tool-request-time' }, time(r.at)));
    button.addEventListener('click', () => {
      selected = r.seq;
      renderList();
      renderDetail();
    });
    return el('li', {}, button);
  }));
  const narrowed = filtered(filter);
  $('[data-bin-empty]').hidden = requests.length > 0 || narrowed;
  $('[data-bin-log]').hidden = requests.length === 0 && !narrowed;
  $('[data-bin-no-match]').hidden = !(narrowed && requests.length === 0);
  const oldestShown = requests.length ? requests[requests.length - 1].seq : 0;
  $('[data-bin-older]').hidden = !requests.length || oldestShown <= oldestKept || noOlder;
}

function setCount(total) {
  $('[data-bin-count]').textContent = filtered(filter)
    ? `${requests.length} found`
    : String(Math.max(0, total - oldestKept + 1 - deletedCount));
}

function download(name, data, type) {
  const url = URL.createObjectURL(new Blob([data], { type: type || 'application/octet-stream' }));
  const link = el('a', { href: url, download: name });
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function headerOf(headers, name) {
  const key = Object.keys(headers ?? {}).find((k) => k.toLowerCase() === name.toLowerCase());
  return key ? headers[key] : '';
}

function bodyBlock(side, text, extra = []) {
  const shown = text ? (plain[side] ? text : pretty(text)) : '(empty body)';
  const toggle = text && pretty(text) !== text
    ? el('button', { type: 'button', class: 'btn ghost small', 'data-body-toggle': side, onclick: () => {
      plain[side] = !plain[side];
      renderDetail();
    } }, plain[side] ? 'Pretty' : 'Raw')
    : null;
  const copy = text ? el('button', { type: 'button', class: 'btn ghost small', 'data-body-copy': side, onclick: async (e) => {
    try {
      await navigator.clipboard.writeText(text);
      e.currentTarget.textContent = 'Copied';
    } catch {
      e.currentTarget.textContent = 'Copy failed';
    }
  } }, 'Copy body') : null;
  return [
    el('div', { class: 'bin-body-bar' }, el('span', { class: 'tool-label' }, 'Body'), el('span', { class: 'bin-body-tools' }, toggle, copy, ...extra)),
    el('pre', { class: 'tool-out tool-wrap', 'data-body': side }, shown)];
}

function requestBody(r) {
  const type = headerOf(r.headers, 'Content-Type');
  const bytes = r.raw || /^multipart\//i.test(type) ? requestBytes(r) : null;
  const parts = bytes ? multipartParts(bytes, type) : [];
  if (parts.length) {
    return [
      el('span', { class: 'tool-label' }, `Multipart body, ${parts.length} ${parts.length === 1 ? 'part' : 'parts'}`),
      el('ul', { class: 'bin-parts' }, ...parts.map((part) => el('li', {},
        el('code', {}, part.name || '(no name)'),
        part.filename
          ? el('span', {}, ` ${part.filename} · ${part.bytes.length} bytes `, el('button', { type: 'button', class: 'btn ghost small', 'data-part-download': part.filename, onclick: () => download(part.filename, part.bytes, part.type) }, 'Download'))
          : el('span', { class: 'bin-part-value' }, ` = ${new TextDecoder().decode(part.bytes).slice(0, 200)}`))))];
  }
  if (r.raw) {
    return [el('div', { class: 'bin-body-bar' },
      el('span', { class: 'tool-label' }, `Binary body, ${bytes.length} bytes`),
      el('button', { type: 'button', class: 'btn ghost small', 'data-body-download': '', onclick: () => download(`request-${r.seq}.bin`, bytes, type) }, 'Download'))];
  }
  return bodyBlock('request', r.body);
}

function detailView(r, shared = false) {
  const cut = r.truncated ? el('p', { class: 'tool-hint' }, `The body was ${r.size} bytes; the first 64 KB are kept.`) : null;
  const sent = el('div', { class: 'bin-side' },
    el('h4', { class: 'tool-sub' }, 'Request'),
    el('p', { class: 'tool-request-line' }, el('span', { class: `jack ${r.method.toLowerCase()}` }, r.method), ` ${r.path}${r.query ? `?${r.query}` : ''}`),
    el('p', { class: 'tool-hint' }, new Date(r.at).toLocaleString(), r.event ? ' · event ' : '', r.event ? el('span', { class: 'bin-event' }, r.event) : null),
    headerList(r.headers),
    ...requestBody(r), cut);
  const o = r.outcome;
  const ruleLink = o && !shared && !bin?.viewOnly && ['rule', 'validation', 'error', 'callout', 'callout-error', 'crud'].includes(o.by) && o.rule
    ? el('button', { type: 'button', class: 'bin-link', 'data-open-rule': o.rule, onclick: () => openRuleNamed(o.rule) }, `Open rule "${o.rule}"`)
    : null;
  const answered = o
    ? el('div', { class: 'bin-side' },
      el('h4', { class: 'tool-sub' }, 'Answer'),
      el('p', {}, el('span', { class: `tool-badge ${outcomeTone(o)}` }, outcomeText(o)), ruleLink ? ' ' : null, ruleLink),
      ...calloutBlock(r),
      headerList(o.headers),
      ...bodyBlock('answer', o.body),
      o.truncated ? el('p', { class: 'tool-hint' }, 'The answer was longer; the first 16 KB are shown.') : null)
    : el('div', { class: 'bin-side' }, el('h4', { class: 'tool-sub' }, 'Answer'), el('p', { class: 'tool-hint' }, 'Not recorded for this request.'));
  return el('div', { class: 'bin-pair' }, sent, answered);
}

function calloutBlock(r) {
  const c = r.callout;
  if (!c) return [];
  const reply = c.mode === 'async' && c.response
    ? [el('span', { class: 'tool-label' }, 'Callout response'), el('pre', { class: 'tool-out tool-wrap', 'data-callout-response': '' }, pretty(c.response))]
    : [];
  return [el('p', { 'data-request-callout': '' }, el('span', { class: `tool-badge ${calloutTone(c, r.at)}` }, calloutText(c, r.at))), ...reply];
}

function curlFor(r, id) {
  return toCurl({ method: r.method, url: `${location.origin}/__public/bins/${id}${r.path === '/' ? '' : r.path}${r.query ? `?${r.query}` : ''}`, headers: r.headers, body: r.raw ? undefined : r.body });
}

function renderDetail() {
  const r = requests.find((x) => x.seq === selected);
  const box = $('[data-bin-detail]');
  const curl = $('[data-bin-curl]');
  const actions = $('[data-bin-detail-actions]');
  $('[data-bin-curl-copy]').hidden = !r;
  if (!r) {
    box.replaceChildren(el('p', { class: 'tool-hint' }, 'Pick a request to see what was sent and what the bin answered.'));
    curl.hidden = true;
    actions.hidden = true;
    return;
  }
  actions.hidden = Boolean(bin.viewOnly);
  actions.replaceChildren(
    el('button', { type: 'button', class: 'btn ghost small', 'data-request-share': '', onclick: () => shareRequest(r) }, 'Share this request'),
    el('button', { type: 'button', class: 'btn ghost small', 'data-request-rule': '', onclick: () => {
      openDialog('rules');
      openEditor(ruleFromRequest(r), -1);
    } }, 'Make a rule from it'),
    el('button', { type: 'button', class: 'btn ghost small', 'data-request-delete': '', onclick: () => deleteRequest(r) }, 'Delete'));
  box.replaceChildren(detailView(r));
  curl.textContent = curlFor(r, bin.id);
}

function detailNote(text) {
  const note = $('[data-bin-detail-note]');
  note.textContent = text;
  note.hidden = !text;
}

async function shareRequest(r) {
  const result = await call(inspect(`/requests/${r.seq}/share`), { headers: auth() });
  if (result.error) {
    detailNote(result.error);
    return;
  }
  const link = shareLink(location.origin, location.pathname, bin.id, r.seq, result.json.key);
  const say = (copied) => detailNote(`${copied ? 'Link copied' : 'Link'}: ${link} . Anyone with it sees this request and its answer, nothing else, while the bin lives.`);
  say(false);
  navigator.clipboard?.writeText(link).then(() => say(true), () => {});
}

async function deleteRequest(r) {
  const result = await call(inspect(`/requests/${r.seq}`), { method: 'DELETE', headers: auth() });
  if (result.error) {
    detailNote(result.error);
    return;
  }
  const index = requests.findIndex((x) => x.seq === r.seq);
  requests = requests.filter((x) => x.seq !== r.seq);
  deletedCount += 1;
  selected = requests[Math.min(index, requests.length - 1)]?.seq ?? null;
  detailNote(`Deleted request ${r.method} ${r.path}.`);
  renderList();
  renderDetail();
  setCount(after);
}

function openRuleNamed(label) {
  const index = rules.findIndex((rule) => (rule.name || `${rule.method} rule`) === label);
  openDialog('rules');
  if (index < 0) {
    flashNote(`The rule "${label}" was renamed or deleted since this request.`);
    return;
  }
  openEditor(structuredClone(rules[index]), index);
}

async function poll() {
  clearTimeout(timer);
  if (!bin) return;
  if (document.hidden && polled) {
    timer = setTimeout(poll, POLL_MS);
    return;
  }
  const asked = generation;
  const result = await call(`${inspect()}?after=${after}${filterQuery(filter)}`, { headers: auth() });
  if (!bin || asked !== generation) return;
  polled = true;
  if (result.status === 404) {
    forget(bin.viewOnly ? 'The shared bin has expired.' : 'That bin expired after 24 hours without a request, so it was removed from My bins.');
    return;
  }
  show($('[data-bin-error]'), result.error);
  if (result.json) {
    oldestKept = result.json.oldestKept ?? 1;
    deletedCount = result.json.deleted ?? 0;
    const fresh = result.json.requests ?? [];
    if (fresh.length) {
      const gap = after > 0 && fresh.length >= 100;
      requests = gap ? fresh : mergeRequests(requests, fresh);
      after = Math.max(after, ...fresh.map((r) => r.seq));
      if (selected === null) selected = requests[0].seq;
      renderDetail();
    }
    requests = requests.filter((r) => r.seq >= oldestKept);
    setCount(result.json.total ?? 0);
    renderList();
    await refreshCallouts(asked);
  }
  $('[data-bin-polling]').textContent = result.error ? 'Retrying…' : 'Listening for requests';
  timer = setTimeout(poll, result.status === 429 ? POLL_MS * 5 : POLL_MS);
}

async function refreshCallouts(asked) {
  const waiting = requests.filter((r) => r.callout?.state === 'scheduled' && !calloutLost(r.callout, r.at)).slice(0, 5);
  if (!waiting.length) return;
  const fetched = await Promise.all(waiting.map((r) => call(inspect(`/requests/${r.seq}`), { headers: auth() })));
  if (!bin || asked !== generation) return;
  const done = fetched.map((res) => res.json).filter((r) => r?.callout && r.callout.state !== 'scheduled');
  if (!done.length) return;
  requests = mergeRequests(requests, done);
  renderList();
  if (done.some((r) => r.seq === selected)) renderDetail();
}

async function loadOlder() {
  const before = requests[requests.length - 1]?.seq;
  if (!before) return;
  const button = $('[data-bin-older]');
  button.disabled = true;
  const asked = generation;
  const result = await call(`${inspect()}?before=${before}${filterQuery(filter)}`, { headers: auth() });
  button.disabled = false;
  if (asked !== generation) return;
  show($('[data-bin-error]'), result.error);
  if (!result.json) return;
  noOlder = (result.json.requests ?? []).length === 0;
  requests = mergeRequests(requests, result.json.requests ?? []);
  setCount(result.json.total ?? 0);
  oldestKept = result.json.oldestKept ?? oldestKept;
  renderList();
}

function applySettings(value) {
  settings = value;
  rules = value.rules ?? [];
  rulesOn = value.rulesEnabled !== false;
  $('[data-bin-view-copy]').hidden = !value.viewKey;
  $('[data-bin-view-link]').textContent = value.viewKey ? binLink(location.origin, location.pathname, bin.id, value.viewKey, true) : '';
  $('[data-bin-keeps]').textContent = String(value.limits?.keepsMb ?? 50);
  $('[data-bin-ai]').hidden = !value.ai;
  $('[data-bin-open="ai"]').hidden = !value.ai;
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
  fillOptions(value.options);
  renderTunnel(value.tunnel);
  renderRules();
  renderMode();
  renderRouteTo();
}

function renderRouteTo() {
  if (!settings) return;
  const active = rules.filter((r) => r.enabled !== false).length;
  const extra = optionsSummary(settings.options);
  $('[data-bin-route-to]').textContent = routeSummary(settings.routing ?? { mode: 'mock' }, rulesOn, active, settings.tunnel?.online) + (extra ? ` (${extra})` : '');
}

function openDialog(name) {
  const dialog = $(`[data-bin-dialog="${name === 'ai' ? 'rules' : name}"]`);
  if (name === 'bins') renderBins();
  if (name === 'save') renderSave();
  if ((name === 'rules' || name === 'ai') && !bin.viewOnly) {
    loadState();
    loadData();
  }
  if (!dialog.open) dialog.showModal();
  if (name === 'ai') {
    const box = $('[data-bin-ai]');
    box.scrollIntoView({ block: 'nearest' });
    (session.read() ? $('[data-bin-ai-text]') : $('#bin-user')).focus();
  }
  if (name === 'save' && !savedProject) (session.read() ? $('[data-bin-save-name]') : $('#bin-save-user')).focus();
}

function closeDialog(dialog) {
  if (!dialog?.open) return;
  dialog.close();
  if ((dialog.dataset.binDialog === 'routing' || dialog.dataset.binDialog === 'settings') && settings) applySettings(settings);
}

function flashConsole(message) {
  const note = $('[data-bin-console-note]');
  note.textContent = message;
  note.hidden = false;
  setTimeout(() => {
    note.hidden = true;
  }, 6000);
}

async function sendSample(noteSelector) {
  const note = $(noteSelector);
  const body = JSON.stringify({ type: 'payment.succeeded', id: `pi_${Math.random().toString(36).slice(2, 8)}`, amount: 4200, currency: 'EUR' });
  if (note) note.textContent = 'Sending…';
  const result = await call(`${binUrl()}/payments?source=test`, { method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Webhook-Event': 'payment.succeeded' }, body });
  if (note) note.textContent = result.status ? `Sent. The bin answered ${result.status}.` : result.error ?? 'Could not send.';
  poll();
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
    if (result.json && !result.error) {
    renderTunnel(result.json.tunnel);
    if (settings) settings.tunnel = result.json.tunnel;
    renderRouteTo();
  }
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
        el('span', { class: 'tool-hint' }, summary(rule)),
        rule.description ? el('span', { class: 'bin-rule-note' }, rule.description) : null),
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

async function loadState() {
  const list = $('[data-bin-state-list]');
  const result = await call(inspect('/state'), { headers: auth(false) });
  if (result.error) {
    list.replaceChildren(el('li', { class: 'tool-hint' }, result.error));
    return;
  }
  const rows = stateRows(result.json);
  $('[data-bin-state-clear]').disabled = rows.length === 0;
  list.replaceChildren(...(rows.length
    ? rows.map((r) => el('li', {}, el('span', { class: 'bin-state-kind' }, r.kind), el('code', {}, r.name), el('span', { class: 'bin-state-value' }, r.value)))
    : [el('li', { class: 'tool-hint' }, 'Nothing saved yet.')]));
}

async function loadData() {
  const list = $('[data-bin-data-list]');
  const result = await call(inspect('/data'), { headers: auth(false) });
  if (result.error) {
    list.replaceChildren(el('li', { class: 'tool-hint' }, result.error));
    return;
  }
  const collections = result.json.collections ?? [];
  $('[data-bin-data-clear]').disabled = collections.length === 0;
  list.replaceChildren(...(collections.length
    ? collections.map((c) => el('li', { 'data-bin-collection': c.path },
      el('div', { class: 'bin-data-head' },
        el('code', {}, c.path),
        el('span', { class: 'tool-badge' }, `${c.count} ${c.count === 1 ? 'record' : 'records'}`),
        bin.viewOnly ? null : el('button', { type: 'button', class: 'btn ghost small', onclick: () => clearData(c.name) }, 'Clear')),
      el('pre', { class: 'tool-out tool-wrap bin-data-records' }, JSON.stringify(c.records.slice(-20), null, 2)),
      c.count > 20 ? el('p', { class: 'tool-hint' }, `Showing the newest 20 of ${c.count}. GET ${c.path} lists them all.`) : null))
    : [el('li', { class: 'tool-hint' }, 'No records yet. Add a CRUD route and POST to it.')]));
}

async function clearData(name) {
  const result = await call(inspect(`/data${name ? `?collection=${encodeURIComponent(name)}` : ''}`), { method: 'DELETE', headers: auth(false) });
  if (!result.error) flashNote(name ? `Cleared /${name}.` : 'Cleared every stored record.');
  loadData();
}

async function clearState() {
  const result = await call(inspect('/state'), { method: 'DELETE', headers: auth(false) });
  if (!result.error) flashNote('Saved state cleared. Counters start again from 1.');
  loadState();
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

async function preview(text, dynamic, out, note, apply = null) {
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
    const error = result.json?.error ?? result.error ?? 'The preview failed.';
    out.textContent = error;
    note.textContent = 'The template has a problem; requests would get a 500 until it is fixed.';
    if (apply && settings?.ai) {
      note.append(' ', el('button', { type: 'button', class: 'btn ghost small', 'data-fix-template': '', onclick: () => fixTemplate(text, error, out, note, apply) }, 'Fix with AI'));
    }
  }
}

async function fixTemplate(text, error, out, note, apply) {
  const signedIn = session.read();
  if (!signedIn) {
    note.textContent = 'Sign in under AI in the Rules dialog to fix templates with AI.';
    return;
  }
  note.textContent = 'Asking the AI…';
  const latest = requests[0];
  const sample = latest ? `${latest.method} ${latest.path}${latest.query ? `?${latest.query}` : ''}\n${latest.raw ? '' : latest.body ?? ''}` : '';
  const result = await call(inspect('/draft/fix'), { method: 'POST', headers: { ...auth(true), Authorization: `Bearer ${signedIn.token}` }, body: JSON.stringify({ template: text, error, request: sample }) });
  if (result.status === 401) {
    session.write(null);
    note.textContent = 'Your sign-in has ended. Sign in again under AI.';
    return;
  }
  if (result.error || typeof result.json?.template !== 'string') {
    note.textContent = result.error ?? 'No template came back. Try again.';
    return;
  }
  apply(result.json.template);
  await preview(result.json.template, true, out, note, apply);
  note.prepend('Fixed by AI; check it before saving. ');
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
  $('[data-rule-description]').value = draft.description ?? '';
  $('[data-rule-method]').replaceChildren(...options(METHODS, draft.method ?? 'ANY'));
  show($('[data-rule-error]'), '');
  stashedCallout = null;
  stashedCrud = null;
  renderConditions();
  fillCallout();
  fillCrud();
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
    const op = OPS.find((o) => o.value === c.op) ?? OPS[0];
    return el('li', { class: 'bin-condition' },
      el('select', { class: 'tool-input', 'aria-label': 'Look at', onchange: (e) => {
        c.target = e.target.value;
        if (!opsFor(c.target).some((o) => o.value === c.op)) c.op = 'equals';
        renderConditions();
      } }, ...options(TARGETS, c.target)),
      target.keyed ? el('input', { class: 'tool-input', type: 'text', spellcheck: 'false', 'aria-label': 'Name', placeholder: target.placeholder, value: c.key ?? '', oninput: (e) => { c.key = e.target.value; } }) : null,
      el('select', { class: 'tool-input', 'aria-label': 'Test', onchange: (e) => { c.op = e.target.value; renderConditions(); } }, ...options(opsFor(c.target), c.op)),
      op.valueless ? null : el('input', { class: 'tool-input', type: 'text', spellcheck: 'false', 'aria-label': 'Value', placeholder: op.placeholder ?? '', value: c.value ?? '', oninput: (e) => { c.value = e.target.value; } }),
      el('button', { type: 'button', class: 'btn ghost small', 'aria-label': 'Remove condition', onclick: () => { draft.conditions.splice(i, 1); renderConditions(); } }, 'Remove'));
  }));
  if (!draft.conditions.length) $('[data-rule-conditions]').append(el('li', { class: 'tool-hint' }, 'No conditions: every request with this method matches.'));
}

const valueGroups = (() => {
  const source = document.getElementById('tool-catalogue');
  try {
    return source ? JSON.parse(source.textContent) : [];
  } catch {
    return [];
  }
})();
let valueTarget = null;

function openValues(textarea, picked) {
  valueTarget = { textarea, picked };
  $('[data-bin-values-search]').value = '';
  renderValues();
  $('[data-bin-values]').showModal();
  $('[data-bin-values-search]').focus();
}

function renderValues() {
  const groups = searchValues(valueGroups, $('[data-bin-values-search]').value);
  $('[data-bin-values-groups]').replaceChildren(...groups.map((g) => el('button', { type: 'button', class: 'bin-values-jump', onclick: () => {
    document.getElementById(`bin-values-${g.id}`)?.scrollIntoView({ block: 'start' });
  } }, g.name)));
  $('[data-bin-values-list]').replaceChildren(
    ...groups.map((g) => el('section', { class: 'bin-values-group', id: `bin-values-${g.id}` },
      el('h4', { class: 'tool-sub' }, g.name),
      el('ul', {}, ...g.items.map((item) => el('li', {},
        el('button', { type: 'button', class: 'bin-value', 'data-bin-value': item.expression, onclick: () => pickValue(item.expression) },
          el('span', { class: 'bin-value-label' }, item.label),
          el('code', {}, item.expression),
          item.hint ? el('span', { class: 'tool-hint' }, item.hint) : null)))))),
    groups.length ? '' : el('p', { class: 'tool-hint' }, 'Nothing matches. Try fewer words.'));
}

function pickValue(expression) {
  $('[data-bin-values]').close();
  if (!valueTarget) return;
  insertAt(valueTarget.textarea, expression);
  valueTarget.picked();
  valueTarget = null;
}

function insertAt(textarea, text) {
  const start = textarea.selectionStart ?? textarea.value.length;
  const end = textarea.selectionEnd ?? start;
  textarea.value = textarea.value.slice(0, start) + text + textarea.value.slice(end);
  textarea.selectionStart = textarea.selectionEnd = start + text.length;
  textarea.dispatchEvent(new Event('input'));
  textarea.focus();
}

function previewBlock(text, dynamic, apply = null) {
  const out = el('pre', { class: 'tool-out tool-wrap', hidden: true });
  const note = el('span', { class: 'tool-hint' });
  const button = el('button', { type: 'button', class: 'btn ghost small', onclick: () => preview(text(), dynamic(), out, note, apply) }, 'Preview');
  return el('div', { class: 'bin-preview' }, el('div', { class: 'bin-preview-row' }, button, note), out);
}

function renderAnswerMode() {
  const weighted = draft.answers.length > 1;
  for (const radio of document.querySelectorAll('[data-rule-mode]')) radio.checked = radio.value === (weighted ? 'weighted' : 'single');
  $('[data-rule-add-answer]').hidden = !weighted || draft.answers.length >= 5;
}

function setAnswerMode(mode) {
  if (mode === 'weighted' && draft.answers.length < 2) draft.answers.push({ ...blankAnswer(), status: 503, body: '{"error": "Try again later"}' });
  if (mode === 'single') draft.answers = draft.answers.slice(0, 1);
  renderAnswers();
}

function readFile(a, file, note) {
  if (file.size > MAX_FILE_BYTES) {
    show(note, `${file.name} is ${fileSize(file.size)}; a file answer can be up to ${fileSize(MAX_FILE_BYTES)}.`);
    return;
  }
  const reader = new FileReader();
  reader.onload = () => {
    const bytes = new Uint8Array(reader.result);
    let binary = '';
    for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
    a.body = btoa(binary);
    a.fileName = file.name;
    a.template = false;
    a.headers = withContentType(a.headers, file.type || 'application/octet-stream');
    renderAnswers();
  };
  reader.readAsArrayBuffer(file);
}

function typePicker(a, headers) {
  const current = contentTypeOf(a.headers);
  const known = CONTENT_TYPES.some((t) => t.value === current);
  return el('select', { class: 'tool-input bin-type', 'aria-label': 'Content type', onchange: (e) => {
    a.headers = withContentType(a.headers, e.target.value);
    headers.value = headerLines(a.headers);
  } },
  el('option', { value: '' }, 'No Content-Type'),
  ...CONTENT_TYPES.map((t) => el('option', { value: t.value, selected: t.value === current }, t.label)),
  current && !known ? el('option', { value: current, selected: true }, current) : null);
}

function answerBody(a, i, headers) {
  const note = el('p', { class: 'tool-hint', hidden: true, 'data-answer-note': '' });
  const upload = el('input', { type: 'file', hidden: true, 'data-answer-file': '', onchange: (e) => { if (e.target.files[0]) readFile(a, e.target.files[0], note); } });
  if (a.fileName) {
    return [
      el('div', { class: 'bin-body-head' }, el('span', { class: 'tool-label' }, 'Body'), typePicker(a, headers)),
      el('div', { class: 'bin-file' },
        el('span', {}, el('strong', {}, a.fileName), ` · ${fileSize(base64Size(a.body))}`),
        el('button', { type: 'button', class: 'btn ghost small', onclick: () => upload.click() }, 'Replace'),
        el('button', { type: 'button', class: 'btn ghost small', 'data-answer-text': '', onclick: () => { a.fileName = ''; a.body = ''; renderAnswers(); } }, 'Use text instead')),
      el('p', { class: 'tool-hint' }, 'The file is sent exactly as uploaded, so it has no dynamic values.'),
      upload, note];
  }
  const body = el('textarea', { id: `ans-b-${i}`, class: 'tool-input', rows: '5', spellcheck: 'false', 'aria-label': 'Body', value: a.body ?? '', oninput: (e) => { a.body = e.target.value; } });
  const template = el('input', { type: 'checkbox', checked: a.template, onchange: (e) => { a.template = e.target.checked; } });
  const dynamic = el('button', { type: 'button', class: 'btn ghost small', 'data-answer-dynamic': '', onclick: () => openValues(body, () => {
    a.template = true;
    template.checked = true;
  }) }, 'Dynamic value');
  const pretty = el('button', { type: 'button', class: 'btn ghost small', title: 'Format JSON', 'aria-label': 'Format JSON', 'data-answer-pretty': '', onclick: () => {
    const result = prettyBody(body.value);
    if (result.error) {
      show(note, result.error);
      return;
    }
    body.value = result.text;
    a.body = result.text;
    show(note, '');
  } }, '{ }');
  const file = el('button', { type: 'button', class: 'btn ghost small', 'data-answer-upload': '', onclick: () => upload.click() }, 'Send a file');
  return [
    el('div', { class: 'bin-body-head' }, el('label', { class: 'tool-label', for: `ans-b-${i}` }, 'Body'), el('span', { class: 'bin-body-tools' }, typePicker(a, headers), pretty, file, dynamic)),
    body,
    note,
    upload,
    el('label', { class: 'bin-check' }, template, ' Fill in dynamic values ({{…}}) from each request'),
    previewBlock(() => a.body, () => a.template, (fixed) => {
      a.body = fixed;
      body.value = fixed;
    })];
}

function renderAnswers() {
  const weighted = draft.answers.length > 1;
  const shares = percents(draft.answers);
  const shareLabels = [];
  const refreshShares = () => percents(draft.answers).forEach((p, j) => { if (shareLabels[j]) shareLabels[j].textContent = `${p}% of requests`; });
  $('[data-rule-answers]').replaceChildren(...draft.answers.map((a, i) => {
    const headers = el('textarea', { id: `ans-h-${i}`, class: 'tool-input', rows: '2', spellcheck: 'false', value: headerLines(a.headers), oninput: (e) => {
      a.headers = parseHeaderLines(e.target.value);
      const picker = e.target.closest('.bin-answer').querySelector('.bin-type');
      const type = contentTypeOf(a.headers);
      if (picker && [...picker.options].some((o) => o.value === type)) picker.value = type;
    } });
    shareLabels[i] = el('span', { class: 'bin-share', 'data-answer-share': '' }, `${shares[i]}% of requests`);
    return el('li', { class: 'bin-answer' },
      weighted ? el('div', { class: 'bin-answer-head' },
        el('input', { class: 'tool-input', type: 'text', maxlength: '120', 'aria-label': 'Answer description', placeholder: `Answer ${i + 1}, like "Usually succeeds"`, value: a.description ?? '', oninput: (e) => { a.description = e.target.value; } }),
        shareLabels[i]) : null,
      el('div', { class: 'tool-row' },
        weighted ? el('div', {}, el('label', { class: 'tool-label', for: `ans-w-${i}` }, 'Weight'), el('input', { id: `ans-w-${i}`, class: 'tool-input', type: 'number', min: '1', max: '100', value: a.weight, oninput: (e) => { a.weight = Number(e.target.value); refreshShares(); } })) : null,
        el('div', {}, el('label', { class: 'tool-label', for: `ans-s-${i}` }, 'Status'), el('input', { id: `ans-s-${i}`, class: 'tool-input', type: 'number', min: '100', max: '599', value: a.status, oninput: (e) => { a.status = Number(e.target.value); } })),
        el('div', {}, el('label', { class: 'tool-label', for: `ans-d-${i}` }, 'Delay, ms'), el('input', { id: `ans-d-${i}`, class: 'tool-input', type: 'number', min: '0', max: '15000', step: '100', value: a.delayMs, oninput: (e) => { a.delayMs = Number(e.target.value); } })),
        weighted ? el('div', { class: 'bin-answer-remove' }, el('button', { type: 'button', class: 'btn ghost small', onclick: () => { draft.answers.splice(i, 1); renderAnswers(); } }, 'Remove')) : null),
      el('label', { class: 'tool-label', for: `ans-h-${i}` }, 'Headers, one per line'),
      headers,
      ...answerBody(a, i, headers));
  }));
  renderAnswerMode();
}

function calloutMode() {
  return draft.callout ? (draft.callout.mode === 'async' ? 'async' : 'sync') : 'none';
}

function fillCallout() {
  const mode = calloutMode();
  for (const radio of document.querySelectorAll('[data-callout-mode]')) radio.checked = radio.value === mode;
  const c = draft.callout ?? blankCallout();
  $('[data-callout-method]').replaceChildren(...options(CALLOUT_METHODS, c.method ?? 'same'));
  $('[data-callout-url]').value = c.url ?? '';
  for (const radio of document.querySelectorAll('[data-callout-payload]')) radio.checked = radio.value === (c.payload ?? 'original');
  $('[data-callout-body]').value = c.body ?? '';
  $('[data-callout-headers]').value = headerLines(c.headers);
  $('[data-callout-auth]').replaceChildren(...options(AUTH_TYPES, c.auth?.type ?? 'none'));
  $('#callout-auth-name').value = c.auth?.name ?? '';
  $('#callout-auth-value').value = c.auth?.value ?? '';
  $('[data-callout-min]').value = c.minDelayMs ?? 0;
  $('[data-callout-max]').value = c.maxDelayMs ?? 0;
  renderCalloutParts();
}

function readCallout() {
  if (!draft.callout) return;
  const kind = $('[data-callout-auth]').value;
  draft.callout = {
    mode: draft.callout.mode,
    method: $('[data-callout-method]').value,
    url: $('[data-callout-url]').value.trim(),
    payload: document.querySelector('[data-callout-payload]:checked')?.value ?? 'original',
    body: $('[data-callout-body]').value,
    headers: parseHeaderLines($('[data-callout-headers]').value),
    auth: { type: kind, name: kind === 'bearer' || kind === 'none' ? '' : $('#callout-auth-name').value.trim(), value: kind === 'none' ? '' : $('#callout-auth-value').value },
    minDelayMs: draft.callout.mode === 'async' ? Number($('[data-callout-min]').value || 0) : 0,
    maxDelayMs: draft.callout.mode === 'async' ? Number($('[data-callout-max]').value || 0) : 0,
  };
}

const AUTH_FIELDS = {
  bearer: [null, 'Token'],
  basic: ['User', 'Password'],
  header: ['Header', 'Value'],
};

function renderCalloutParts() {
  const mode = document.querySelector('[data-callout-mode]:checked')?.value ?? 'none';
  $('[data-callout]').hidden = mode === 'none';
  $('[data-rule-answer-section]').hidden = mode === 'sync';
  $('[data-callout-wait]').hidden = mode !== 'async';
  $('[data-callout-custom]').hidden = (document.querySelector('[data-callout-payload]:checked')?.value ?? 'original') !== 'custom';
  const fields = AUTH_FIELDS[$('[data-callout-auth]').value];
  $('[data-callout-auth-name]').hidden = !fields?.[0];
  $('[data-callout-auth-value]').hidden = !fields;
  if (fields) {
    $('[data-callout-auth-name-label]').textContent = fields[0] ?? '';
    $('[data-callout-auth-value-label]').textContent = fields[1];
  }
  $('[data-callout-hint]').textContent = mode === 'sync'
    ? 'The bin waits up to 15 seconds and answers with the URL\'s status, headers and body. If the call fails, the sender gets a 502. Use {{…}} in the URL, body and headers. Private and local addresses are refused.'
    : 'The sender gets the answer below at once. The call goes out after a random wait between the two times, and its result shows on the request in the log. Use {{…}} in the URL, body and headers. Private and local addresses are refused.';
}

function setCalloutMode(mode) {
  readCallout();
  if (mode === 'none') {
    if (draft.callout) stashedCallout = draft.callout;
    delete draft.callout;
  } else {
    draft.callout = { ...(draft.callout ?? stashedCallout ?? blankCallout()), mode };
    if (mode === 'async' && !draft.answers.length) draft.answers.push(blankAnswer());
  }
  if (mode !== 'sync' && !draft.answers.length) draft.answers.push(blankAnswer());
  fillCallout();
  renderAnswers();
}

function fillCrud() {
  const kind = draft.crud ? 'crud' : 'answer';
  for (const radio of document.querySelectorAll('[data-rule-kind]')) radio.checked = radio.value === kind;
  const c = draft.crud ?? stashedCrud ?? blankCrud();
  $('[data-crud-path]').value = c.path ?? '';
  $('[data-crud-id-field]').value = c.idField ?? 'id';
  $('[data-crud-id-type]').value = c.idType ?? 'number';
  renderCrudParts();
}

function readCrud() {
  if (!draft.crud) return;
  draft.crud = { path: normalCrudPath($('[data-crud-path]').value), idField: $('[data-crud-id-field]').value.trim() || 'id', idType: $('[data-crud-id-type]').value };
}

function renderCrudParts() {
  const crud = Boolean(draft?.crud);
  $('[data-crud]').hidden = !crud;
  $('[data-rule-answer-kind]').hidden = crud;
  if (crud) {
    const shown = { path: $('[data-crud-path]').value, idField: $('[data-crud-id-field]').value };
    $('[data-crud-routes]').replaceChildren(...crudRoutes(shown).map(([m, p, what]) => el('li', {}, el('span', { class: `jack ${m.toLowerCase()}` }, m), ' ', el('code', {}, p), ` ${what}`)));
  }
}

function setRuleKind(kind) {
  if (kind === 'crud') {
    readCallout();
    if (draft.callout) stashedCallout = draft.callout;
    delete draft.callout;
    draft.crud = stashedCrud ?? blankCrud();
    draft.method = 'ANY';
    $('[data-rule-method]').value = 'ANY';
  } else {
    readCrud();
    stashedCrud = draft.crud;
    delete draft.crud;
    if (!draft.answers.length) draft.answers.push(blankAnswer());
    fillCallout();
    renderAnswers();
  }
  fillCrud();
}

async function saveDraft(e) {
  e.preventDefault();
  readCrud();
  const crudError = checkCrud(draft.crud);
  if (crudError) {
    show($('[data-rule-error]'), crudError);
    return;
  }
  if (draft.crud) {
    delete draft.callout;
    draft.answers = [];
  }
  readCallout();
  const calloutError = checkCallout(draft.callout);
  if (calloutError) {
    show($('[data-rule-error]'), calloutError);
    return;
  }
  draft.name = $('[data-rule-name]').value.trim();
  draft.description = $('[data-rule-description]').value.trim();
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

function fillOptions(options = {}) {
  const form = $('[data-bin-options]');
  form.elements.cors.checked = options.cors !== false;
  form.elements.requiredName.value = options.requiredHeader?.name ?? '';
  form.elements.requiredValue.value = options.requiredHeader?.value ?? '';
  form.elements.rateRequests.value = options.rateLimit?.requests ?? '';
  form.elements.per.value = options.rateLimit?.per ?? 'minute';
  form.elements.latencyMs.value = options.latencyMs ?? 0;
  show($('[data-bin-options-error]'), '');
}

async function saveOptions(e) {
  e.preventDefault();
  const f = e.target.elements;
  const read = optionsFromForm({ cors: f.cors.checked, requiredName: f.requiredName.value, requiredValue: f.requiredValue.value, rateRequests: f.rateRequests.value, per: f.per.value, latencyMs: f.latencyMs.value });
  if (read.error) {
    show($('[data-bin-options-error]'), read.error);
    return;
  }
  const result = await call(inspect('/options'), { method: 'PUT', headers: auth(true), body: JSON.stringify(read.options) });
  if (result.error) {
    show($('[data-bin-options-error]'), result.error);
    return;
  }
  applySettings(result.json);
  closeDialog($('[data-bin-dialog="settings"]'));
  const summary = optionsSummary(result.json.options);
  flashConsole(`Settings saved. ${summary ? `This bin now ${summary.replace(/ · /g, ', ')}.` : 'No limits, CORS on.'}`);
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
  note.textContent = result.error ?? '';
  if (result.json && !result.error) {
    applySettings(result.json);
    $('[data-bin-dialog="routing"]').close();
    flashConsole(`Routing saved. New requests go to ${$('[data-bin-route-to]').textContent}.`);
  }
}

function renderSignIn(message = '') {
  const signedIn = session.read();
  $('[data-bin-signin]').hidden = Boolean(signedIn);
  $('[data-bin-describe]').hidden = !signedIn;
  $('[data-bin-signed-in]').textContent = signedIn ? `Signed in as ${signedIn.name}` : '';
  $('[data-bin-signin-note]').textContent = message;
  renderSave(message);
}

async function logIn(form, note) {
  note.textContent = 'Signing in…';
  const body = JSON.stringify({ username: form.elements.username.value.trim(), password: form.elements.password.value });
  const result = await call('/__admin/mockhub/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body });
  form.elements.password.value = '';
  if (result.error || !result.json?.token) {
    note.textContent = result.status === 401 ? 'Wrong username or password.' : result.error ?? 'Signing in failed.';
    return false;
  }
  const user = result.json.user ?? {};
  session.write({ token: result.json.token, name: user.displayName || user.username || form.elements.username.value.trim() });
  renderSignIn();
  return true;
}

async function signIn(e) {
  e.preventDefault();
  await logIn(e.target, e.target.querySelector('[data-bin-signin-note]'));
}

function renderSave(message = '') {
  const signedIn = session.read();
  $('[data-bin-save-signin]').hidden = Boolean(signedIn);
  $('[data-bin-save-form]').hidden = !signedIn || Boolean(savedProject);
  $('[data-bin-save-result]').hidden = !savedProject;
  $('[data-bin-save-signin-note]').textContent = message;
  if (savedProject) {
    $('[data-bin-save-link]').href = savedProject.ui;
    $('[data-bin-save-link]').textContent = savedProject.name;
    $('[data-bin-save-notes]').replaceChildren(...savedProject.notes.map((n) => el('p', { class: 'tool-hint' }, n)));
    $('[data-bin-save-notes]').hidden = !savedProject.notes.length;
  }
}

async function signInForSave(e) {
  e.preventDefault();
  await logIn(e.target, e.target.querySelector('[data-bin-save-signin-note]'));
}

async function saveToProject(e) {
  e.preventDefault();
  const signedIn = session.read();
  if (!signedIn) return;
  const button = $('[data-bin-save-go]');
  const note = $('[data-bin-save-note]');
  button.disabled = true;
  note.textContent = 'Saving…';
  const result = await call(inspect('/project'), {
    method: 'POST',
    headers: { ...auth(true), Authorization: `Bearer ${signedIn.token}` },
    body: JSON.stringify({ name: $('[data-bin-save-name]').value.trim() }),
  });
  button.disabled = false;
  if (result.status === 401) {
    session.write(null);
    renderSignIn('Your sign-in has ended. Sign in again.');
    return;
  }
  if (result.error || !result.json?.projectId) {
    note.textContent = result.error ?? 'Saving failed.';
    return;
  }
  note.textContent = '';
  savedProject = { name: result.json.name, ui: result.json.ui, notes: result.json.notes ?? [] };
  try {
    sessionStorage.setItem('mockhub.auth', `Bearer ${signedIn.token}`);
  } catch (err) {
    // ignore
  }
  savedBins.write(savedBins.read().map((b) => (b.id === bin.id ? { ...b, project: { name: result.json.name, url: result.json.url, ui: result.json.ui } } : b)));
  renderSave();
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
  const drafted = (result.json?.rules ?? (result.json?.rule ? [result.json.rule] : [])).map(({ id, ...rule }) => ({ ...blankRule(), ...rule }));
  if (result.error || !drafted.length) {
    note.textContent = result.error ?? 'No rule came back. Try describing it differently.';
    renderDrafts([], []);
    return;
  }
  const skipped = result.json.skipped ?? [];
  if (drafted.length === 1 && !skipped.length) {
    note.textContent = 'Check the draft, then save it.';
    renderDrafts([], []);
    openEditor(drafted[0], -1);
    return;
  }
  note.textContent = `${drafted.length} ${drafted.length === 1 ? 'rule' : 'rules'} drafted. Check each, then add them.`;
  renderDrafts(drafted, skipped);
}

function renderDrafts(drafted, skipped) {
  const box = $('[data-bin-ai-drafts]');
  box.hidden = !drafted.length && !skipped.length;
  const add = async (list) => {
    const result = await saveRules([...rules, ...list], rulesOn, `Added ${list.length === 1 ? `"${list[0].name || 'Rule'}"` : `${list.length} rules`}.`);
    if (!result.error) renderDrafts(drafted.filter((d) => !list.includes(d)), skipped);
  };
  box.replaceChildren(
    ...drafted.map((rule) => el('div', { class: 'bin-rule', 'data-bin-ai-draft': '' },
      el('span', { class: 'bin-rule-text' }, el('strong', {}, rule.name || 'Rule'), el('span', { class: 'tool-hint' }, summary(rule))),
      el('span', { class: 'bin-rule-actions' },
        el('button', { type: 'button', class: 'btn ghost small', onclick: () => {
          renderDrafts(drafted.filter((d) => d !== rule), skipped);
          openEditor(rule, -1);
        } }, 'Edit'),
        el('button', { type: 'button', class: 'btn ghost small', 'data-bin-ai-add': '', onclick: () => add([rule]) }, 'Add')))),
    drafted.length > 1 ? el('div', { class: 'tool-actions-row' }, el('button', { type: 'button', class: 'btn secondary small', 'data-bin-ai-add-all': '', onclick: () => add(drafted) }, `Add all ${drafted.length}`)) : '',
    ...skipped.map((s) => el('p', { class: 'tool-hint', 'data-bin-ai-skipped': '' }, `Left out: ${s}`)));
}

function resetLog() {
  generation += 1;
  after = 0;
  requests = [];
  selected = null;
  noOlder = false;
  detailNote('');
}

function readFilter() {
  const form = $('[data-bin-filter]');
  filter = { q: form.elements.q.value, method: form.elements.method.value, status: form.elements.status.value };
  resetLog();
  renderList();
  renderDetail();
  setCount(0);
  poll();
}

async function exportLog(format) {
  const result = await call(`${inspect('/export')}?format=${format}${filterQuery(filter)}`, { headers: auth() });
  if (result.error) {
    show($('[data-bin-error]'), result.error);
    return;
  }
  download(`bin-${bin.id}.${format}`, JSON.stringify(result.json, null, 2), 'application/json');
  const count = format === 'har' ? result.json.log.entries.length : result.json.exported;
  flashConsole(`Exported ${count} ${count === 1 ? 'request' : 'requests'}${count >= 1000 ? ', the newest 1,000' : ''} as ${format.toUpperCase()}.`);
}

async function showShared(link) {
  stop();
  $('[data-bin-start]').hidden = true;
  $('[data-bin-live]').hidden = true;
  $('[data-bin-shared]').hidden = false;
  const result = await call(`/__public/inspect/${link.id}/requests/${link.seq}`, { headers: { 'X-Bin-Token': link.key } });
  show($('[data-bin-shared-error]'), result.error);
  if (result.error) {
    $('[data-bin-shared-detail]').replaceChildren();
    $('[data-bin-shared-note]').textContent = '';
    return;
  }
  const r = result.json;
  $('[data-bin-shared-note]').textContent = `Request ${r.seq} from webhook bin ${r.bin}. This link shows only this request and its answer, and works until the bin expires${r.expiresAt ? `, ${new Date(r.expiresAt).toLocaleString()} unless it gets more requests` : ''}.`;
  $('[data-bin-shared-detail]').replaceChildren(detailView(r, true));
  $('[data-bin-shared-curl]').textContent = curlFor(r, r.bin);
}

function open(value) {
  bin = value;
  $('[data-bin-shared]').hidden = true;
  filter = { q: '', method: '', status: '' };
  $('[data-bin-filter]').reset();
  resetLog();
  deletedCount = 0;
  oldestKept = 1;
  polled = false;
  $('[data-bin-start]').hidden = true;
  $('[data-bin-live]').hidden = false;
  $('[data-bin-live]').toggleAttribute('data-view-only', Boolean(bin.viewOnly));
  if (!bin.viewOnly) {
    store.write({ id: bin.id, token: bin.token });
    savedBins.write(rememberBin(savedBins.read(), bin));
  }
  renderTitle();
  $('[data-bin-view-copy]').hidden = true;
  $('[data-bin-url]').textContent = binUrl();
  $('[data-bin-share-url]').textContent = binUrl();
  $('[data-bin-route-to]').textContent = bin.viewOnly ? 'the owner\'s rules and routing' : 'default answer';
  $('[data-bin-log]').hidden = true;
  $('[data-bin-empty]').hidden = false;
  settings = null;
  savedProject = null;
  $('[data-bin-link]').textContent = binLink(location.origin, location.pathname, bin.id, bin.token);
  const code = snippets(binUrl());
  document.querySelectorAll('[data-bin-code]').forEach((pre) => {
    pre.textContent = code[pre.dataset.binCode];
  });
  $('[data-bin-tunnel-cmd]').textContent = tunnelCommand(location.origin, bin.id, bin.token);
  setCount(0);
  renderList();
  renderDetail();
  poll();
  if (bin.viewOnly) return;
  loadSettings();
  refreshTunnel();
}

function renderTitle() {
  const list = savedBins.read();
  const mine = bin && !bin.viewOnly ? list.find((b) => b.id === bin.id) : null;
  $('[data-bin-title]').textContent = bin?.viewOnly ? 'Shared bin, view only' : mine?.name ?? 'Your bin';
  $('[data-bin-saved-count]').textContent = list.length > 1 ? String(list.length) : '';
}

async function describeBin(entry) {
  const headers = { 'X-Bin-Token': entry.token };
  const [set, page] = await Promise.all([
    call(`/__public/inspect/${entry.id}/settings`, { headers }),
    call(`/__public/inspect/${entry.id}?limit=1`, { headers }),
  ]);
  if (set.status === 404) return { expired: true, text: 'Expired: no request for 24 hours.' };
  if (!set.json || set.error) return { text: set.error ?? 'Could not load.' };
  const rules = set.json.rules ?? [];
  const active = rules.filter((r) => r.enabled !== false).length;
  const where = routeSummary(set.json.routing ?? { mode: 'mock' }, set.json.rulesEnabled !== false, active, set.json.tunnel?.online);
  const total = page.json ? Math.max(0, (page.json.total ?? 0) - (page.json.oldestKept ?? 1) + 1) : 0;
  const off = rules.length && (set.json.rulesEnabled === false || !active) ? ` (${rules.length} rule${rules.length === 1 ? '' : 's'} off)` : '';
  return { text: `Goes to ${where}${off} · ${total} request${total === 1 ? '' : 's'} kept` };
}

function renderBins() {
  const list = savedBins.read().slice().reverse();
  $('[data-bin-saved-list]').replaceChildren(...list.map((entry) => {
    const current = bin && !bin.viewOnly && bin.id === entry.id;
    const summary = el('span', { class: 'tool-hint bin-saved-summary' }, 'Loading…');
    const name = el('input', {
      class: 'bin-saved-name', type: 'text', maxlength: '40', value: entry.name, 'aria-label': `Name of ${entry.name}`,
      onchange: (e) => {
        savedBins.write(renameBin(savedBins.read(), entry.id, e.target.value));
        e.target.value = savedBins.read().find((b) => b.id === entry.id)?.name ?? entry.name;
        renderTitle();
      },
    });
    const row = el('li', { class: `bin-saved-item${current ? ' current' : ''}` },
      el('div', { class: 'bin-saved-main' },
        name,
        el('code', { class: 'bin-saved-url' }, `${location.origin}/__public/bins/${entry.id}`),
        entry.project ? el('a', { class: 'bin-saved-project', href: entry.project.ui, target: '_blank', rel: 'noopener' }, `MockHub project: ${entry.project.name}`) : '',
        summary),
      el('div', { class: 'bin-saved-actions' },
        current ? el('span', { class: 'tool-badge ok' }, 'Current') : el('button', { type: 'button', class: 'btn secondary small', onclick: () => switchTo(entry) }, 'Open'),
        el('button', { type: 'button', class: 'btn ghost small', onclick: () => removeBin(entry), title: 'Remove from this list. The bin itself stays until it expires.' }, 'Remove')));
    describeBin(entry).then((d) => {
      summary.textContent = d.text;
      row.classList.toggle('expired', Boolean(d.expired));
    });
    return row;
  }));
  $('[data-bin-saved-note]').textContent = list.length ? `${list.length} bin${list.length === 1 ? '' : 's'} in this browser` : '';
}

function switchTo(entry) {
  history.replaceState(null, '', location.pathname + location.search);
  stop();
  $('[data-bin-dialog="bins"]').close();
  open({ id: entry.id, token: entry.token });
  flashConsole(`Switched to ${entry.name}.`);
}

function removeBin(entry) {
  const list = forgetBin(savedBins.read(), entry.id);
  savedBins.write(list);
  if (bin && !bin.viewOnly && bin.id === entry.id) {
    stop();
    const next = list.at(-1);
    if (next) open({ id: next.id, token: next.token });
    else {
      store.write(null);
      $('[data-bin-dialog="bins"]').close();
      $('[data-bin-live]').hidden = true;
      $('[data-bin-start]').hidden = false;
      return;
    }
  }
  renderTitle();
  renderBins();
}

async function createBin(button) {
  button.disabled = true;
  const result = await call('/__public/bins', { method: 'POST' });
  button.disabled = false;
  if (result.error || !result.json?.id) {
    const message = result.error ?? 'The bin could not be created.';
    show($('[data-bin-create-error]'), message);
    if (bin) flashConsole(message);
    return;
  }
  history.replaceState(null, '', location.pathname + location.search);
  stop();
  document.querySelectorAll('[data-bin-dialog]').forEach((d) => d.open && d.close());
  open({ id: result.json.id, token: result.json.token });
  flashConsole(`Created ${savedBins.read().at(-1)?.name ?? 'a new bin'}. Your other bins are under My bins.`);
}

function stop() {
  clearTimeout(timer);
  clearTimeout(settingsTimer);
  clearTimeout(settingsRetry);
  bin = null;
}

function forget(message) {
  const leaving = bin;
  stop();
  history.replaceState(null, '', location.pathname + location.search);
  if (leaving && !leaving.viewOnly) savedBins.write(forgetBin(savedBins.read(), leaving.id));
  const list = savedBins.read();
  const own = store.read();
  const next = list.find((b) => b.id === own?.id) ?? list.at(-1);
  if (next) {
    open({ id: next.id, token: next.token });
    if (message) flashConsole(message);
    return;
  }
  store.write(null);
  $('[data-bin-live]').hidden = true;
  $('[data-bin-start]').hidden = false;
  show($('[data-bin-create-error]'), message ?? '');
}

$('[data-bin-create]').addEventListener('click', (e) => createBin(e.currentTarget));
document.querySelectorAll('[data-bin-new]').forEach((b) => b.addEventListener('click', () => createBin(b)));
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
let filterTimer = null;
$('[data-bin-filter]').addEventListener('input', (e) => {
  clearTimeout(filterTimer);
  filterTimer = setTimeout(readFilter, e.target.name === 'q' ? 350 : 0);
});
$('[data-bin-filter]').addEventListener('submit', (e) => {
  e.preventDefault();
  clearTimeout(filterTimer);
  readFilter();
});
$('[data-bin-export]').addEventListener('change', (e) => {
  const format = e.target.value;
  e.target.value = '';
  if (format) exportLog(format);
});
document.querySelectorAll('[data-bin-open]').forEach((b) => b.addEventListener('click', () => openDialog(b.dataset.binOpen)));
document.querySelectorAll('[data-bin-close]').forEach((b) => b.addEventListener('click', () => closeDialog(b.closest('dialog'))));
document.querySelectorAll('[data-bin-dialog]').forEach((d) => {
  d.addEventListener('click', (e) => {
    if (e.target === d) closeDialog(d);
  });
  d.addEventListener('cancel', (e) => {
    e.preventDefault();
    closeDialog(d);
  });
});
$('[data-bin-sample]').addEventListener('click', () => sendSample('[data-bin-sample-note]'));
$('[data-bin-sample-again]').addEventListener('click', () => sendSample(null));
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
for (const radio of document.querySelectorAll('[data-rule-mode]')) radio.addEventListener('change', (e) => setAnswerMode(e.target.value));
for (const radio of document.querySelectorAll('[data-callout-mode]')) radio.addEventListener('change', (e) => setCalloutMode(e.target.value));
for (const radio of document.querySelectorAll('[data-callout-payload]')) radio.addEventListener('change', renderCalloutParts);
$('[data-callout-auth]').addEventListener('change', renderCalloutParts);
$('[data-callout-url-value]').addEventListener('click', () => openValues($('[data-callout-url]'), () => {}));
$('[data-callout-body-value]').addEventListener('click', () => openValues($('[data-callout-body]'), () => {}));
$('[data-bin-editor-form]').addEventListener('submit', saveDraft);
$('[data-bin-editor-close]').addEventListener('click', closeEditor);
$('[data-bin-editor-cancel]').addEventListener('click', closeEditor);
$('[data-bin-routing]').addEventListener('submit', saveRouting);
$('[data-bin-options]').addEventListener('submit', saveOptions);
$('[data-bin-routing]').addEventListener('change', (e) => {
  if (e.target.name === 'mode') renderMode();
});
$('[data-bin-routing]').addEventListener('input', (e) => {
  if (e.target.name === 'target' || e.target.name === 'pathPrefix') renderFlow();
});
$('[data-bin-describe]').addEventListener('submit', draftWithAi);
$('[data-bin-signin]').addEventListener('submit', signIn);
$('[data-bin-save-signin]').addEventListener('submit', signInForSave);
$('[data-bin-save-form]').addEventListener('submit', saveToProject);
$('[data-bin-signout]').addEventListener('click', () => {
  session.write(null);
  renderSignIn();
});

document.addEventListener('visibilitychange', () => {
  if (!document.hidden && bin) poll();
});

renderRecipes();
renderSignIn();
$('[data-bin-insert]').addEventListener('click', () => openValues($('[data-bin-body]'), () => {
  $('[data-bin-template]').checked = true;
}));
$('[data-bin-values-search]').addEventListener('input', renderValues);
$('[data-bin-state-refresh]').addEventListener('click', loadState);
$('[data-bin-state-clear]').addEventListener('click', clearState);
$('[data-bin-data-refresh]').addEventListener('click', loadData);
$('[data-bin-data-clear]').addEventListener('click', () => clearData(''));
for (const radio of document.querySelectorAll('[data-rule-kind]')) radio.addEventListener('change', (e) => setRuleKind(e.target.value));
for (const input of ['[data-crud-path]', '[data-crud-id-field]']) $(input).addEventListener('input', renderCrudParts);
$('[data-bin-values]').addEventListener('click', (e) => {
  if (e.target === e.currentTarget) e.currentTarget.close();
});
$('[data-bin-preview]').addEventListener('click', () => preview($('[data-bin-body]').value, $('[data-bin-template]').checked, $('[data-bin-preview-out]'), $('[data-bin-preview-note]'), (fixed) => {
  $('[data-bin-body]').value = fixed;
}));

function takeLinked() {
  const linked = parseBinHash(location.hash);
  if (linked && !linked.viewOnly) history.replaceState(null, '', location.pathname + location.search);
  return linked;
}

function openLinked(linked) {
  if (!linked.viewOnly) store.write({ id: linked.id, token: linked.token });
  open(linked.viewOnly ? linked : { id: linked.id, token: linked.token });
}

window.addEventListener('hashchange', () => {
  const shared = parseShareHash(location.hash);
  if (shared) {
    showShared(shared);
    return;
  }
  const linked = takeLinked();
  if (!linked) return;
  stop();
  openLinked(linked);
});
const fromLink = takeLinked();
const current = store.read();
const known = savedBins.read();
const sharedLink = parseShareHash(location.hash);
if (sharedLink) showShared(sharedLink);
else if (fromLink) openLinked(fromLink);
else if (current?.id && current?.token) open(current);
else if (known.length) open(known.at(-1));
wireCopyButtons();
