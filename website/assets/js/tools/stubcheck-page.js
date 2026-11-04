import { $, debounce } from './common.js';
import { check, explain, parseStubs } from './stubcheck.js';

const input = $('[data-check-input]');
const results = $('[data-check-results]');
const summary = $('[data-check-summary]');
const LABEL = { error: 'Error', warning: 'Warning', info: 'Tip' };

function el(tag, className, text) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
}

function render() {
  results.innerHTML = '';
  const parsed = parseStubs(input.value);
  if (parsed.error) {
    results.append(el('p', 'tool-error', parsed.error));
    summary.textContent = 'Invalid JSON';
    summary.className = 'tool-badge bad';
    return;
  }
  let errors = 0;
  let warnings = 0;
  parsed.stubs.forEach((stub, i) => {
    const problems = check(stub);
    errors += problems.filter((p) => p.level === 'error').length;
    warnings += problems.filter((p) => p.level === 'warning').length;
    const card = el('article', 'tool-stub');
    const title = stub?.name ? `${i + 1}. ${stub.name}` : `Stub ${i + 1}`;
    card.append(el('h4', 'tool-sub', title));
    if (stub && typeof stub === 'object' && !Array.isArray(stub)) card.append(el('p', 'tool-explain', explain(stub)));
    const list = el('ul', 'tool-problems');
    for (const p of problems) {
      const item = el('li', `tool-problem ${p.level}`);
      item.append(el('span', `tool-level ${p.level}`, LABEL[p.level]));
      if (p.path) item.append(el('code', '', p.path));
      item.append(el('span', '', p.text));
      list.append(item);
    }
    if (!problems.length) list.append(el('li', 'tool-problem ok', 'Looks good.'));
    card.append(list);
    results.append(card);
  });
  const count = parsed.stubs.length;
  summary.textContent = errors ? `${errors} error${errors === 1 ? '' : 's'}` : warnings ? `${warnings} warning${warnings === 1 ? '' : 's'}` : `${count} stub${count === 1 ? '' : 's'} OK`;
  summary.className = `tool-badge ${errors ? 'bad' : warnings ? 'warn' : 'ok'}`;
}

$('[data-check-format]').addEventListener('click', () => {
  try {
    input.value = JSON.stringify(JSON.parse(input.value), null, 2);
  } catch (e) {
    return;
  }
  render();
});

input.value = JSON.stringify({
  name: 'Order by id',
  request: { method: 'GET', url: '/orders/{id}', headers: { Accept: { equals: 'application/json' } } },
  response: { status: 200, body: '{"id": "{{request.path.id}}"}' },
}, null, 2);
input.addEventListener('input', debounce(render, 250));
render();
