import { $, show, wireCopyButtons } from './common.js';
import { call, parseHeaders, toCurl } from './http.js';

const form = $('[data-echo-form]');

function request() {
  const raw = $('[data-echo-path]').value.trim();
  const path = `/__public/echo${raw.startsWith('/') ? raw : `/${raw}`}`;
  const method = $('[data-echo-method]').value;
  const body = method === 'GET' ? '' : $('[data-echo-body]').value;
  return { method, path, headers: parseHeaders($('[data-echo-headers]').value), body };
}

function curl() {
  const r = request();
  $('[data-echo-curl]').textContent = toCurl({ ...r, url: `${location.origin}${r.path}` });
}

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  const r = request();
  const button = $('[data-echo-send]');
  button.disabled = true;
  const result = await call(r.path, { method: r.method, headers: r.headers, body: r.body });
  button.disabled = false;
  show($('[data-echo-error]'), result.error);
  $('[data-echo-meta]').textContent = result.status ? `${result.status} in ${result.ms} ms` : '';
  if (!result.error) $('[data-echo-output]').textContent = result.json ? JSON.stringify(result.json, null, 2) : result.text;
});

form.addEventListener('input', curl);
$('[data-echo-method]').addEventListener('change', () => {
  $('[data-echo-body]').disabled = $('[data-echo-method]').value === 'GET';
  curl();
});
curl();
wireCopyButtons();
