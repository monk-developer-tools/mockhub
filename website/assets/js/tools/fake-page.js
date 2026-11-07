import { $, download, show, wireCopyButtons } from './common.js';
import { call } from './http.js';
import { toCsv, toSql } from './fakeformat.js';
import { inferFields, MAX_FIELDS, splitRendered, templateWrapper } from './fakeinfer.js';
import { keyHash, MAX_RECORDS, resourceName } from './instant.js';

const SESSION_KEY = 'mockhub-bin-session';
const STARTER = [
  ['id', 'sequence'],
  ['name', 'Name.fullName'],
  ['email', 'Internet.emailAddress'],
  ['address.city', 'Address.city'],
  ['company', 'Company.name'],
];
let records = null;
let format = 'json';

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

const mode = () => document.querySelector('[data-fake-mode][aria-selected="true"]')?.dataset.fakeMode ?? 'fields';
const showMode = (name) => document.querySelector(`[data-fake-mode="${name}"]`).click();

const catalogue = () => {
  const source = document.getElementById('tool-catalogue');
  return source ? JSON.parse(source.textContent) : [];
};

function fillExpressions() {
  const list = $('[data-fake-expressions]');
  for (const group of catalogue()) {
    for (const item of group.items) {
      const option = document.createElement('option');
      option.value = item.expression;
      option.label = `${group.group}: ${item.label}`;
      list.append(option);
    }
  }
}

function addField(name = '', expression = '') {
  const box = $('[data-fake-fields]');
  if (box.children.length >= MAX_FIELDS) return;
  const n = box.children.length + 1;
  const row = document.createElement('div');
  row.className = 'tool-field';
  row.innerHTML = `<label class="sr-only" for="fake-name-${n}">Field ${n} name</label>
    <input id="fake-name-${n}" class="tool-input" type="text" spellcheck="false" autocomplete="off" placeholder="name" data-name>
    <label class="sr-only" for="fake-expr-${n}">Field ${n} type</label>
    <input id="fake-expr-${n}" class="tool-input" type="text" spellcheck="false" autocomplete="off" list="fake-expressions" placeholder="type, e.g. Name.fullName" data-expression>
    <button type="button" class="btn ghost small" aria-label="Remove field ${n}" data-remove>×</button>`;
  row.querySelector('[data-name]').value = name;
  row.querySelector('[data-expression]').value = expression;
  row.querySelector('[data-remove]').addEventListener('click', () => {
    row.remove();
    $('[data-fake-add]').disabled = $('[data-fake-fields]').children.length >= MAX_FIELDS;
  });
  box.append(row);
  $('[data-fake-add]').disabled = box.children.length >= MAX_FIELDS;
}

function output() {
  if (!records) return;
  const text = format === 'csv' ? toCsv(records) : format === 'sql' ? toSql(records, $('[data-fake-table]').value) : JSON.stringify(records, null, 2);
  $('[data-fake-output]').textContent = text;
}

function setFields(fields, note = '') {
  $('[data-fake-fields]').replaceChildren();
  fields.forEach((f) => addField(f.name, f.expression));
  $('[data-fake-add]').disabled = $('[data-fake-fields]').children.length >= MAX_FIELDS;
  $('[data-fake-fields-note]').textContent = note;
  $('[data-fake-fields-note]').hidden = !note;
}

async function fromFields(count) {
  const fields = [...document.querySelectorAll('.tool-field')]
    .map((row) => ({ name: row.querySelector('[data-name]').value.trim(), expression: row.querySelector('[data-expression]').value.trim() }))
    .filter((f) => f.name || f.expression);
  const result = await call('/__public/fake', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ count, locale: $('[data-fake-locale]').value, fields }),
  });
  return { error: result.error, records: result.json?.records, ms: result.ms };
}

async function fromTemplate(count) {
  const template = $('[data-fake-template]').value.trim();
  if (!template) return { error: 'Write a template first.' };
  const result = await call('/__public/render', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ template: templateWrapper(template, count), request: { method: 'GET', url: '/' } }),
  });
  if (result.error) return { error: result.error };
  if (result.json?.error) return { error: result.json.error };
  try {
    return { records: splitRendered(result.json.output), ms: result.ms };
  } catch (e) {
    return { error: e.message };
  }
}

$('[data-fake-form]').addEventListener('submit', async (e) => {
  e.preventDefault();
  show($('[data-fake-error]'), '');
  const count = Math.max(1, Math.min(100, Number($('[data-fake-count]').value) || 10));
  const made = mode() === 'template' ? await fromTemplate(count) : await fromFields(count);
  show($('[data-fake-error]'), made.error);
  if (made.error) return;
  records = made.records;
  $('[data-fake-meta]').textContent = `${records.length} records in ${made.ms} ms`;
  output();
});

$('[data-fake-infer]').addEventListener('click', () => {
  let sample;
  try {
    sample = JSON.parse($('[data-fake-sample]').value);
  } catch (e) {
    show($('[data-fake-sample-error]'), `That isn't valid JSON: ${e.message}`);
    return;
  }
  try {
    const { fields, notes } = inferFields(sample);
    show($('[data-fake-sample-error]'), '');
    setFields(fields, notes.length ? `Left out: ${notes.join('; ')}.` : `${fields.length} fields from your sample. Check the types, then generate.`);
    showMode('fields');
  } catch (e) {
    show($('[data-fake-sample-error]'), e.message);
  }
});

function renderSignIn(message = '') {
  const signedIn = session.read();
  $('[data-fake-signin]').hidden = Boolean(signedIn);
  $('[data-fake-describe]').hidden = !signedIn;
  $('[data-fake-signed-in]').textContent = signedIn ? `Signed in as ${signedIn.name}` : '';
  $('[data-fake-signin-note]').textContent = message;
}

async function signIn() {
  const note = $('[data-fake-signin-note]');
  const username = $('[data-fake-username]').value.trim();
  note.textContent = 'Signing in…';
  const result = await call('/__admin/mockhub/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password: $('[data-fake-password]').value }),
  });
  $('[data-fake-password]').value = '';
  if (result.error || !result.json?.token) {
    note.textContent = result.status === 401 ? 'Wrong username or password.' : result.error ?? 'Signing in failed.';
    return;
  }
  const user = result.json.user ?? {};
  session.write({ token: result.json.token, name: user.displayName || user.username || username });
  renderSignIn();
}

async function draftFields() {
  const signedIn = session.read();
  const note = $('[data-fake-draft-note]');
  const button = $('[data-fake-draft]');
  const description = $('[data-fake-describe-text]').value.trim();
  if (!description) {
    note.textContent = 'Describe the records first.';
    return;
  }
  button.disabled = true;
  note.textContent = 'Asking the AI…';
  const result = await call('/__public/fake/draft', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${signedIn?.token ?? ''}` },
    body: JSON.stringify({ description }),
  });
  button.disabled = false;
  if (result.status === 401) {
    session.write(null);
    renderSignIn('Your sign-in has ended. Sign in again.');
    return;
  }
  if (result.error || !result.json?.fields) {
    note.textContent = result.error ?? 'No fields came back. Try describing the records differently.';
    return;
  }
  $('[data-fake-count]').value = String(result.json.count);
  $('[data-fake-locale]').value = result.json.locale ?? '';
  const language = $('[data-fake-locale]').selectedOptions[0]?.textContent ?? 'English';
  const skipped = result.json.skipped ?? [];
  setFields(result.json.fields, `AI picked ${result.json.fields.length} fields, ${result.json.count} records in ${language}.${skipped.length ? ` Left out: ${skipped.join('; ')}.` : ''} Check them, then generate.`);
  note.textContent = 'You check the fields before generating.';
  showMode('fields');
}

async function probeAi() {
  const result = await call('/__public/fake/draft');
  $('[data-fake-mode="ai"]').hidden = !result.json?.available;
}

$('[data-fake-signin-go]').addEventListener('click', signIn);
$('[data-fake-password]').addEventListener('keydown', (e) => {
  if (e.key === 'Enter') {
    e.preventDefault();
    signIn();
  }
});
$('[data-fake-signout]').addEventListener('click', () => {
  session.write(null);
  renderSignIn();
});
$('[data-fake-draft]').addEventListener('click', draftFields);
document.querySelectorAll('[data-fake-mode]').forEach((button) => button.addEventListener('click', () => setTimeout(() => {
  $('[data-fake-locale]').closest('div').hidden = mode() === 'template';
})));

$('[data-fake-formats]').addEventListener('click', (e) => {
  const tab = e.target.closest('[data-format]');
  if (!tab) return;
  format = tab.dataset.format;
  $('[data-fake-table-box]').hidden = format !== 'sql';
  output();
});
$('[data-fake-table]').addEventListener('input', output);
$('[data-fake-add]').addEventListener('click', () => addField());
$('[data-fake-download]').addEventListener('click', () => {
  if (!records) return;
  download(`records.${format}`, $('[data-fake-output]').textContent);
});

$('[data-fake-host]').addEventListener('click', async (e) => {
  const button = e.currentTarget;
  if (!records) {
    show($('[data-fake-host-error]'), 'Press Generate first.');
    return;
  }
  button.disabled = true;
  const result = await call('/__public/mocks', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ from: 'records', resource: resourceName($('[data-fake-table]').value), records: records.slice(0, MAX_RECORDS) }),
  });
  button.disabled = false;
  show($('[data-fake-host-error]'), result.error);
  if (!result.error && result.json?.slug) location.assign(`/tools/instant-mock/${keyHash(result.json)}`);
});

fillExpressions();
STARTER.forEach(([name, expression]) => addField(name, expression));
renderSignIn();
probeAi();
wireCopyButtons();
