const FORM_REQUEST = new Set(['method', 'urlPath', 'urlPathTemplate']);
const FORM_RESPONSE = new Set(['status', 'headers', 'body', 'jsonBody', 'fixedDelayMilliseconds', 'transformers']);
const FORM_TOP = new Set(['id', 'name', 'request', 'response']);

export const TEMPLATE = 'response-template';

export function parseKey(hash) {
  const match = /^#?(m-[a-z0-9]{10})\.([a-z0-9]{32})$/.exec(String(hash ?? ''));
  return match ? { slug: match[1], token: match[2] } : null;
}

export const keyHash = ({ slug, token }) => `#${slug}.${token}`;

export function fitsForm(stub) {
  const request = stub.request ?? {};
  const response = stub.response ?? {};
  const headers = Object.keys(response.headers ?? {});
  const transformers = response.transformers ?? [];
  return Object.keys(stub).every((k) => FORM_TOP.has(k))
    && Object.keys(request).every((k) => FORM_REQUEST.has(k))
    && Object.keys(response).every((k) => FORM_RESPONSE.has(k))
    && headers.every((h) => h.toLowerCase() === 'content-type')
    && transformers.every((t) => t === TEMPLATE);
}

export function stubToForm(stub) {
  const request = stub.request ?? {};
  const response = stub.response ?? {};
  const contentType = Object.entries(response.headers ?? {}).find(([k]) => k.toLowerCase() === 'content-type')?.[1] ?? '';
  const body = response.jsonBody !== undefined ? JSON.stringify(response.jsonBody, null, 2) : (response.body ?? '');
  return {
    name: stub.name ?? '',
    method: request.method ?? 'ANY',
    path: request.urlPathTemplate ?? request.urlPath ?? '/',
    status: response.status ?? 200,
    contentType,
    body,
    templating: (response.transformers ?? []).includes(TEMPLATE),
    delay: response.fixedDelayMilliseconds ?? 0,
  };
}

export function formToStub(form) {
  const path = form.path.trim().startsWith('/') ? form.path.trim() : `/${form.path.trim()}`;
  const request = { method: form.method || 'ANY', [/\{[^}]+\}/.test(path) ? 'urlPathTemplate' : 'urlPath']: path };
  const response = { status: Number(form.status) || 200 };
  if (form.contentType.trim()) response.headers = { 'Content-Type': form.contentType.trim() };
  if (form.body) {
    let json;
    try {
      json = !form.templating && /json/i.test(form.contentType) ? JSON.parse(form.body) : undefined;
    } catch (e) {
      json = undefined;
    }
    if (json !== undefined && json !== null && typeof json === 'object') response.jsonBody = json;
    else response.body = form.body;
  }
  if (Number(form.delay) > 0) response.fixedDelayMilliseconds = Number(form.delay);
  if (form.templating) response.transformers = [TEMPLATE];
  const stub = { request, response };
  if (form.name.trim()) stub.name = form.name.trim();
  return stub;
}

export function displayPath(stub) {
  const r = stub.request ?? {};
  return r.urlPathTemplate ?? r.urlPath ?? r.url ?? '(any path)';
}

export function samplePath(stub) {
  const path = displayPath(stub);
  if (path === '(any path)') return '/';
  return path.replace(/\{[^}]+\}/g, '42');
}

export function exportMappings(stubs) {
  return { mappings: stubs.map(({ id, ...rest }) => rest) };
}
