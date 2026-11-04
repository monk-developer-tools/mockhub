import { $, debounce, show, wireCopyButtons } from './common.js';
import { call, parseHeaders } from './http.js';

const EXAMPLES = [
  {
    name: 'Echo parts of the request',
    template: '{\n  "orderId": "{{request.path.[1]}}",\n  "customer": "{{request.query.customer}}",\n  "item": "{{jsonPath request.body \'$.item\'}}",\n  "trace": "{{request.headers.X-Trace-Id}}",\n  "receivedAt": "{{now}}"\n}',
    method: 'POST', url: '/orders/42?customer=ada', headers: 'Content-Type: application/json\nX-Trace-Id: abc-123', body: '{"item": "tea", "quantity": 2}',
  },
  {
    name: 'Fake user',
    template: '{\n  "id": "{{randomValue type=\'UUID\'}}",\n  "name": "{{fake \'Name.fullName\'}}",\n  "email": "{{fake \'Internet.emailAddress\'}}",\n  "city": "{{fake \'Address.city\'}}",\n  "joined": "{{now offset=\'-30 days\' format=\'yyyy-MM-dd\'}}"\n}',
    method: 'GET', url: '/users/me', headers: '', body: '',
  },
  {
    name: 'List from a loop',
    template: '{\n  "page": {{default request.query.page 1}},\n  "items": [\n    {{#each (range 1 3)}}\n    { "id": {{this}}, "name": "{{fake \'Commerce.productName\'}}" }{{#unless @last}},{{/unless}}\n    {{/each}}\n  ]\n}',
    method: 'GET', url: '/products?page=2', headers: '', body: '',
  },
  {
    name: 'Branch on the body',
    template: '{{#if (eq (jsonPath request.body \'$.amount\') 0)}}\n{"status": "REJECTED", "reason": "Amount must be above zero"}\n{{else}}\n{"status": "ACCEPTED", "amount": {{jsonPath request.body \'$.amount\'}}}\n{{/if}}',
    method: 'POST', url: '/payments', headers: 'Content-Type: application/json', body: '{"amount": 0, "currency": "EUR"}',
  },
  {
    name: 'Validate the request',
    template: '{{isEmail (jsonPath request.body \'$.email\') \'email must be an e-mail address\' status=422}}\n{"created": true, "email": "{{jsonPath request.body \'$.email\'}}"}',
    method: 'POST', url: '/signups', headers: 'Content-Type: application/json', body: '{"email": "not-an-email"}',
  },
];

const fields = {
  template: $('[data-tpl-input]'),
  method: $('[data-tpl-method]'),
  url: $('[data-tpl-url]'),
  headers: $('[data-tpl-headers]'),
  body: $('[data-tpl-body]'),
};
let sequence = 0;

function load(example) {
  fields.template.value = example.template;
  fields.method.value = example.method;
  fields.url.value = example.url;
  fields.headers.value = example.headers;
  fields.body.value = example.body;
  render();
}

async function render() {
  const id = ++sequence;
  $('[data-tpl-state]').textContent = 'Rendering…';
  const result = await call('/__public/render', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      template: fields.template.value,
      request: { method: fields.method.value, url: fields.url.value, headers: parseHeaders(fields.headers.value), body: fields.body.value },
    }),
  });
  if (id !== sequence) return;
  const json = result.json ?? {};
  const failed = result.status === 422 ? json.error : result.error;
  show($('[data-tpl-error]'), failed);
  const badge = $('[data-tpl-status]');
  badge.hidden = !json.status;
  badge.textContent = json.status ? `Answers ${json.status}` : '';
  badge.classList.toggle('warn', Boolean(json.status));
  $('[data-tpl-output]').textContent = json.status ? json.validationError : (json.output ?? (failed ? '' : $('[data-tpl-output]').textContent));
  $('[data-tpl-state]').textContent = result.status ? `${result.ms} ms` : '';
}

function insert(expression) {
  const box = fields.template;
  const start = box.selectionStart ?? box.value.length;
  const end = box.selectionEnd ?? start;
  box.value = box.value.slice(0, start) + expression + box.value.slice(end);
  box.focus();
  box.selectionStart = box.selectionEnd = start + expression.length;
  render();
}

function fillHelpers() {
  const source = document.getElementById('tool-catalogue');
  const groups = source ? JSON.parse(source.textContent) : [];
  const select = $('[data-tpl-helper]');
  for (const group of groups) {
    const optgroup = document.createElement('optgroup');
    optgroup.label = group.name;
    for (const item of group.items) {
      const option = document.createElement('option');
      option.value = item.expression;
      option.textContent = item.label;
      optgroup.append(option);
    }
    select.append(optgroup);
  }
  select.addEventListener('change', () => {
    if (select.value) insert(select.value);
    select.value = '';
  });
}

function fillExamples() {
  const select = $('[data-tpl-example]');
  const first = document.createElement('option');
  first.value = '';
  first.textContent = 'Load an example…';
  select.append(first, ...EXAMPLES.map((example, i) => {
    const option = document.createElement('option');
    option.value = String(i);
    option.textContent = example.name;
    return option;
  }));
  select.addEventListener('change', () => {
    if (select.value) load(EXAMPLES[Number(select.value)]);
    select.value = '';
  });
}

const later = debounce(render, 400);
Object.values(fields).forEach((field) => field.addEventListener('input', later));
fields.method.addEventListener('change', render);
fillHelpers();
fillExamples();
load(EXAMPLES[0]);
wireCopyButtons();
