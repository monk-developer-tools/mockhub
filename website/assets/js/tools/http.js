export function parseHeaders(text) {
  const headers = {};
  for (const line of String(text ?? '').split('\n')) {
    const at = line.indexOf(':');
    if (at <= 0) continue;
    const name = line.slice(0, at).trim();
    if (name) headers[name] = line.slice(at + 1).trim();
  }
  return headers;
}

const quote = (text) => `'${String(text).replace(/'/g, `'\\''`)}'`;

const SKIPPED = new Set(['host', 'content-length', 'connection', 'accept-encoding']);

export function toCurl({ method, url, headers = {}, body = '' }) {
  const parts = [`curl -X ${method} ${quote(url)}`];
  for (const [name, value] of Object.entries(headers)) {
    if (!SKIPPED.has(name.toLowerCase())) parts.push(`-H ${quote(`${name}: ${value}`)}`);
  }
  if (body) parts.push(`--data-raw ${quote(body)}`);
  return parts.join(' \\\n  ');
}

export function problem(status, json) {
  if (status === 0) return 'The demo server could not be reached. Check your connection and try again.';
  if (status === 429) return json?.error ?? 'Too many requests from your address. Wait a moment and try again.';
  if (status === 404 && !json?.error) return 'The demo server has these tools turned off right now.';
  if (status >= 500 && !json?.error) return `The demo server answered ${status}. Try again in a moment.`;
  return json?.error ?? null;
}

export async function call(path, { method = 'GET', headers = {}, body } = {}) {
  const started = performance.now();
  let response;
  try {
    response = await fetch(path, { method, headers, body: body === undefined || method === 'GET' || method === 'HEAD' ? undefined : body, cache: 'no-store' });
  } catch (e) {
    return { status: 0, ms: 0, text: '', json: null, headers: new Headers(), error: problem(0, null) };
  }
  const text = await response.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch (e) {
    json = null;
  }
  const ms = Math.round(performance.now() - started);
  const error = response.ok ? null : problem(response.status, json);
  return { status: response.status, ms, text, json, headers: response.headers, error };
}
