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

const crudResource = (stub) => (stub.response?.transformers ?? []).includes('mockhub-crud') ? stub.response?.transformerParameters?.resource : null;

export function displayPath(stub) {
  const resource = crudResource(stub);
  if (resource) return `/${resource}[/{id}]`;
  const r = stub.request ?? {};
  return r.urlPathTemplate ?? r.urlPath ?? r.url ?? '(any path)';
}

export function samplePath(stub) {
  const resource = crudResource(stub);
  if (resource) return `/${resource}`;
  const path = displayPath(stub);
  if (path === '(any path)') return '/';
  return path.replace(/\{[^}]+\}/g, '42');
}

export const MAX_RECORDS = 50;

export function resourceName(fileName) {
  const base = String(fileName ?? '').replace(/\.[^.]*$/, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
  return /^[a-z0-9]/.test(base) ? base.slice(0, 60) : 'items';
}

export function specFormat(text) {
  const start = String(text ?? '').trimStart();
  return start.startsWith('<') ? 'wsdl' : 'openapi';
}

function csvRows(text) {
  const rows = [];
  let row = [];
  let field = '';
  let quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') {
        field += '"';
        i++;
      } else if (c === '"') {
        quoted = false;
      } else {
        field += c;
      }
    } else if (c === '"') {
      quoted = true;
    } else if (c === ',') {
      row.push(field);
      field = '';
    } else if (c === '\n' || c === '\r') {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(field);
      rows.push(row);
      row = [];
      field = '';
    } else {
      field += c;
    }
  }
  if (field !== '' || row.length) {
    row.push(field);
    rows.push(row);
  }
  return rows.filter((r) => r.some((v) => v.trim() !== ''));
}

function csvValue(raw) {
  const v = raw.trim();
  if (/^-?(0|[1-9]\d{0,14})(\.\d+)?$/.test(v)) return Number(v);
  if (v === 'true' || v === 'false') return v === 'true';
  return v;
}

export function parseCsv(text) {
  const rows = csvRows(String(text ?? ''));
  if (rows.length < 2) throw new Error('The CSV needs a header row and at least one record.');
  const headers = rows[0].map((h) => h.trim());
  if (headers.some((h) => !h)) throw new Error('Every column in the header row needs a name.');
  return rows.slice(1).map((r) => Object.fromEntries(headers.map((h, i) => [h, csvValue(r[i] ?? '')])));
}

export function parseRecords(text) {
  const trimmed = String(text ?? '').trim();
  if (!trimmed) throw new Error('Paste or upload some records first.');
  let records;
  if (trimmed.startsWith('[') || trimmed.startsWith('{')) {
    let parsed;
    try {
      parsed = JSON.parse(trimmed);
    } catch (e) {
      throw new Error('That JSON is not valid.');
    }
    const lists = !Array.isArray(parsed) && parsed && typeof parsed === 'object' ? Object.values(parsed).filter(Array.isArray) : [];
    records = Array.isArray(parsed) ? parsed : lists.length === 1 ? lists[0] : null;
    if (!records) throw new Error('Use a JSON list of objects, like [{"id": 1, "name": "Ada"}].');
  } else {
    records = parseCsv(trimmed);
  }
  if (!records.length || !records.every((r) => r && typeof r === 'object' && !Array.isArray(r))) {
    throw new Error('Each record must be an object with named fields.');
  }
  return records;
}

export function exportMappings(stubs) {
  return { mappings: stubs.map(({ id, ...rest }) => rest) };
}

export const GRAPHQL = 'mockhub-graphql';

export const isGraphql = (stub) => (stub?.response?.transformers ?? []).includes(GRAPHQL);

export function specFormatFromUrl(url) {
  return /\.wsdl(\?|$)|[?&]wsdl(=|&|$)/i.test(String(url ?? '')) ? 'wsdl' : 'openapi';
}

export function specStart(from, text, url) {
  const pasted = String(text ?? '');
  const link = String(url ?? '').trim();
  const what = from === 'graphql' ? 'a GraphQL schema' : 'an OpenAPI or WSDL file';
  if (pasted.trim() && link) throw new Error('Use the pasted text or the URL, not both. Clear one of them.');
  if (link) {
    if (!/^https?:\/\/[^\s/]+/i.test(link)) throw new Error('The URL must start with http:// or https://');
    return { from: from === 'graphql' ? 'graphql' : specFormatFromUrl(link), url: link };
  }
  if (!pasted.trim()) throw new Error(`Upload, paste or link ${what} first.`);
  return { from: from === 'graphql' ? 'graphql' : specFormat(pasted), text: pasted };
}

export function graphqlRequest(query, variablesText) {
  const text = String(query ?? '').trim();
  if (!text) throw new Error('Write a query first, or pick an example.');
  const raw = String(variablesText ?? '').trim();
  if (!raw) return { query: text, variables: {} };
  let variables;
  try {
    variables = JSON.parse(raw);
  } catch (e) {
    throw new Error('The variables are not valid JSON.');
  }
  if (variables === null || typeof variables !== 'object' || Array.isArray(variables)) throw new Error('The variables must be a JSON object, like {"id": "1"}.');
  return { query: text, variables };
}
