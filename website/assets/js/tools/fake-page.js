import { $, download, show, wireCopyButtons } from './common.js';
import { call } from './http.js';
import { toCsv, toSql } from './fakeformat.js';

const MAX_FIELDS = 20;
const STARTER = [
  ['id', 'sequence'],
  ['name', 'Name.fullName'],
  ['email', 'Internet.emailAddress'],
  ['address.city', 'Address.city'],
  ['company', 'Company.name'],
];
let records = null;
let format = 'json';

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
  row.querySelector('[data-remove]').addEventListener('click', () => row.remove());
  box.append(row);
  $('[data-fake-add]').disabled = box.children.length >= MAX_FIELDS;
}

function output() {
  if (!records) return;
  const text = format === 'csv' ? toCsv(records) : format === 'sql' ? toSql(records, $('[data-fake-table]').value) : JSON.stringify(records, null, 2);
  $('[data-fake-output]').textContent = text;
}

$('[data-fake-form]').addEventListener('submit', async (e) => {
  e.preventDefault();
  const fields = [...document.querySelectorAll('.tool-field')]
    .map((row) => ({ name: row.querySelector('[data-name]').value.trim(), expression: row.querySelector('[data-expression]').value.trim() }))
    .filter((f) => f.name || f.expression);
  const result = await call('/__public/fake', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ count: Number($('[data-fake-count]').value) || 10, locale: $('[data-fake-locale]').value, fields }),
  });
  show($('[data-fake-error]'), result.error);
  if (result.error) return;
  records = result.json.records;
  $('[data-fake-meta]').textContent = `${records.length} records in ${result.ms} ms`;
  output();
});

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

fillExpressions();
STARTER.forEach(([name, expression]) => addField(name, expression));
wireCopyButtons();
