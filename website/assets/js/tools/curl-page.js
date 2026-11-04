import { $, debounce, download, show, wireCopyButtons } from './common.js';
import { describe, toStub } from './curl.js';
import { explain } from './stubcheck.js';

const input = $('[data-curl-input]');
const headersBox = $('[data-curl-headers]');
let stub = null;
let lastHeaders = '';

function headerChoices(info) {
  const key = info.headers.map((h) => h.name).join('\n');
  if (key === lastHeaders) return;
  lastHeaders = key;
  headersBox.innerHTML = '';
  $('[data-curl-headers-box]').hidden = !info.headers.length;
  for (const h of info.headers) {
    const label = document.createElement('label');
    label.className = 'tool-check';
    const box = document.createElement('input');
    box.type = 'checkbox';
    box.value = h.name;
    box.checked = h.suggested;
    box.addEventListener('change', render);
    const text = document.createElement('span');
    text.textContent = `${h.name}: ${h.value.length > 48 ? `${h.value.slice(0, 45)}…` : h.value}`;
    label.append(box, ' ', text);
    headersBox.append(label);
  }
}

function render() {
  try {
    const info = describe(input.value);
    headerChoices(info);
    const headers = [...headersBox.querySelectorAll('input:checked')].map((b) => b.value);
    stub = toStub(info, {
      headers,
      status: $('[data-curl-status]').value || 200,
      responseBody: $('[data-curl-response]').value,
      matchBody: $('[data-curl-match-body]').checked,
    });
    $('[data-curl-output]').textContent = JSON.stringify(stub, null, 2);
    $('[data-curl-explain]').textContent = explain(stub);
    show($('[data-curl-error]'), '');
  } catch (e) {
    show($('[data-curl-error]'), e.message);
  }
}

input.value = `curl -X POST 'https://api.example.com/v1/payments?dryRun=false' \\
  -H 'Content-Type: application/json' \\
  -H 'Authorization: Bearer eyJhbGciOi...' \\
  -H 'User-Agent: Mozilla/5.0' \\
  -d '{"amount": 42, "currency": "EUR"}'`;
input.addEventListener('input', debounce(render));
document.querySelectorAll('[data-curl-status], [data-curl-response], [data-curl-match-body]').forEach((el) => el.addEventListener('input', debounce(render)));
$('[data-curl-download]').addEventListener('click', () => stub && download(`${stub.request.method.toLowerCase()}${stub.request.urlPath.replace(/[^a-z0-9]+/gi, '-')}.json`.replace(/-+\.json$/, '.json'), JSON.stringify(stub, null, 2)));
render();
wireCopyButtons();
