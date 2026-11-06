export const METHODS = ['ANY', 'GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'QUERY', 'TRACE'];

export const TARGETS = [
  { value: 'path', label: 'Path', keyed: false },
  { value: 'query', label: 'Query parameter', keyed: true, placeholder: 'name' },
  { value: 'header', label: 'Header', keyed: true, placeholder: 'X-Event-Type' },
  { value: 'body', label: 'Body text', keyed: false },
  { value: 'json', label: 'JSON field', keyed: true, placeholder: '$.type' },
  { value: 'form', label: 'Form field', keyed: true, placeholder: 'status' },
  { value: 'xml', label: 'XML field', keyed: true, placeholder: '//order/id' },
  { value: 'soap_action', label: 'SOAP action', keyed: false },
  { value: 'graphql', label: 'GraphQL operation', keyed: false },
];

export const OPS = [
  { value: 'equals', label: 'equals' },
  { value: 'not_equals', label: "doesn't equal" },
  { value: 'starts_with', label: 'starts with' },
  { value: 'contains', label: 'contains' },
  { value: 'not_contains', label: "doesn't contain" },
  { value: 'matches', label: 'matches regex' },
  { value: 'template', label: 'matches pattern', only: 'path', placeholder: '/users/:id' },
  { value: 'exists', label: 'is present', valueless: true },
  { value: 'absent', label: 'is absent', valueless: true },
];

export function opsFor(target) {
  return OPS.filter((o) => !o.only || o.only === target);
}

export function stateRows(state) {
  const counters = Object.entries(state?.counters ?? {}).map(([name, value]) => ({ kind: 'Counter', name, value: String(value) }));
  const values = Object.entries(state?.values ?? {}).map(([name, value]) => ({ kind: 'Value', name, value }));
  const lists = Object.entries(state?.lists ?? {}).map(([name, items]) => ({ kind: 'List', name, value: `${items.length} ${items.length === 1 ? 'item' : 'items'}: ${JSON.stringify(items)}` }));
  return [...counters, ...values, ...lists];
}

export function searchValues(groups, query) {
  const words = query.trim().toLowerCase().split(/\s+/).filter(Boolean);
  if (!words.length) return groups;
  return groups
    .map((g) => ({
      ...g,
      items: g.items.filter((i) => {
        const text = `${g.name} ${i.label} ${i.expression} ${i.hint ?? ''}`.toLowerCase();
        return words.every((w) => text.includes(w));
      }),
    }))
    .filter((g) => g.items.length > 0);
}

export function blankAnswer() {
  return { weight: 1, status: 200, delayMs: 0, headers: { 'Content-Type': 'application/json' }, body: '{"ok": true}', template: false };
}

export const CONTENT_TYPES = [
  { value: 'application/json', label: 'JSON' },
  { value: 'application/xml', label: 'XML' },
  { value: 'text/html', label: 'HTML' },
  { value: 'text/javascript', label: 'JavaScript' },
  { value: 'text/csv', label: 'CSV' },
  { value: 'text/plain', label: 'Plain text' },
];

export const MAX_FILE_BYTES = 16 * 1024;

export function contentTypeOf(headers = {}) {
  const key = Object.keys(headers).find((k) => k.toLowerCase() === 'content-type');
  return key ? headers[key] : '';
}

export function withContentType(headers = {}, type) {
  const out = Object.fromEntries(Object.entries(headers).filter(([k]) => k.toLowerCase() !== 'content-type'));
  return type ? { 'Content-Type': type, ...out } : out;
}

export function prettyBody(text) {
  try {
    return { text: JSON.stringify(JSON.parse(text), null, 2) };
  } catch {
    return { error: text.includes('{{') ? 'Only plain JSON can be formatted; this body has dynamic values.' : "The body isn't valid JSON." };
  }
}

export function percents(answers) {
  const total = answers.reduce((sum, a) => sum + (Number(a.weight) > 0 ? Number(a.weight) : 0), 0);
  return answers.map((a) => (total > 0 && Number(a.weight) > 0 ? Math.round((Number(a.weight) * 100) / total) : 0));
}

export function fileSize(bytes) {
  return bytes < 1024 ? `${bytes} B` : `${(bytes / 1024).toFixed(1)} KB`;
}

export function base64Size(text) {
  const clean = (text ?? '').replace(/=+$/, '');
  return Math.floor((clean.length * 3) / 4);
}

export function blankCondition() {
  return { target: 'path', key: '', op: 'starts_with', value: '/' };
}

export function blankRule() {
  return { name: '', enabled: true, method: 'POST', conditions: [blankCondition()], answers: [blankAnswer()] };
}

export const RECIPES = [
  { id: 'dynamic', label: 'Dynamic answer', hint: 'Fill the answer from each request.' },
  { id: 'fail', label: 'Fail every request with 500', hint: 'See how the sender retries.' },
  { id: 'slow', label: 'Answer after 5 seconds', hint: 'Check the sender\'s timeout.' },
  { id: 'event', label: 'Answer one event type', hint: 'Match a field like $.type.' },
  { id: 'echo', label: 'Echo the body back', hint: 'Answer with what was sent.' },
  { id: 'flaky', label: 'Fail one in four', hint: 'Weighted answers, 75% / 25%.' },
];

export function recipe(id, event = 'payment.failed') {
  const json = { 'Content-Type': 'application/json' };
  switch (id) {
    case 'dynamic':
      return {
        ...blankRule(),
        name: 'Dynamic answer',
        method: 'ANY',
        conditions: [],
        answers: [{
          ...blankAnswer(),
          status: 201,
          headers: { 'Content-Type': 'application/json', 'X-Request-Id': "{{randomValue type='UUID'}}" },
          body: '{\n  "received": "{{jsonPath request.body \'$.id\'}}",\n  "path": "{{request.path}}",\n  "method": "{{request.method}}",\n  "at": "{{now}}"\n}',
          template: true,
        }],
      };
    case 'fail':
      return { ...blankRule(), name: 'Fail with 500', method: 'ANY', conditions: [], answers: [{ ...blankAnswer(), status: 500, body: '{"error": "Something broke on purpose"}' }] };
    case 'slow':
      return { ...blankRule(), name: 'Slow answer', method: 'ANY', conditions: [], answers: [{ ...blankAnswer(), delayMs: 5000 }] };
    case 'event':
      return {
        ...blankRule(),
        name: `When the event is ${event}`,
        conditions: [{ target: 'json', key: '$.type', op: 'equals', value: event }],
        answers: [{ ...blankAnswer(), status: 202, headers: json, body: `{"received": "${event}", "id": "{{jsonPath request.body '$.id'}}"}`, template: true }],
      };
    case 'echo':
      return { ...blankRule(), name: 'Echo the body', method: 'ANY', conditions: [], answers: [{ ...blankAnswer(), headers: json, body: '{{{request.body}}}', template: true }] };
    case 'flaky':
      return {
        ...blankRule(),
        name: 'Fail one in four',
        method: 'ANY',
        conditions: [],
        answers: [{ ...blankAnswer(), weight: 3 }, { ...blankAnswer(), weight: 1, status: 503, body: '{"error": "Try again"}' }],
      };
    default:
      return blankRule();
  }
}

function describeCondition(c) {
  const target = TARGETS.find((t) => t.value === c.target) ?? TARGETS[0];
  const op = OPS.find((o) => o.value === c.op) ?? OPS[0];
  const subject = target.keyed ? `${c.target === 'json' ? '' : `${target.label.toLowerCase()} `}${c.key}` : target.label.toLowerCase();
  return op.valueless ? `${subject} ${op.label}` : `${subject} ${op.label} ${c.value}`;
}

export function summary(rule) {
  const when = rule.conditions?.length ? rule.conditions.map(describeCondition).join(' and ') : 'any request';
  const method = rule.method === 'ANY' ? '' : `${rule.method} `;
  const answers = rule.answers ?? [];
  const shares = percents(answers);
  const then = answers.length > 1 ? answers.map((a, i) => `${a.status} (${shares[i]}%)`).join(' or ') : `${answers[0]?.status ?? 200}${answers[0]?.fileName ? ` with ${answers[0].fileName}` : ''}`;
  const delay = answers.some((a) => a.delayMs > 0) ? ` after ${Math.max(...answers.map((a) => a.delayMs)) / 1000}s` : '';
  return `${method}${when} → ${then}${delay}`;
}

export function move(list, from, to) {
  if (from === to || from < 0 || to < 0 || from >= list.length || to >= list.length) return list.slice();
  const out = list.slice();
  const [item] = out.splice(from, 1);
  out.splice(to, 0, item);
  return out;
}

export function duplicate(list, index) {
  const copy = structuredClone(list[index]);
  delete copy.id;
  copy.name = copy.name ? `${copy.name} (copy)` : '';
  const out = list.slice();
  out.splice(index + 1, 0, copy);
  return out;
}

export function parseHeaderLines(text) {
  const out = {};
  for (const line of String(text ?? '').split('\n')) {
    const at = line.indexOf(':');
    if (at <= 0) continue;
    const name = line.slice(0, at).trim();
    if (name) out[name] = line.slice(at + 1).trim();
  }
  return out;
}

export function headerLines(headers) {
  return Object.entries(headers ?? {}).map(([k, v]) => `${k}: ${v}`).join('\n');
}

const BY = {
  rule: 'Rule',
  default: 'Default answer',
  forward: 'Forwarded',
  tunnel: 'Tunnel',
  'forward-error': 'Forward failed',
  'tunnel-error': 'Tunnel failed',
  error: 'Rule failed',
  auth: 'Missing required header',
  limit: 'Rate limited',
  validation: 'Refused by a check',
};

export function outcomeTone(outcome) {
  if (!outcome) return '';
  if (outcome.by?.endsWith('error') || outcome.status >= 500) return 'bad';
  if (outcome.status >= 400) return 'warn';
  return 'ok';
}

export function outcomeText(outcome) {
  if (!outcome) return '';
  const by = BY[outcome.by] ?? outcome.by;
  const named = outcome.rule ? `${by}: ${outcome.rule}` : by;
  return `${outcome.status} · ${named} · ${outcome.millis} ms`;
}

export function mergeRequests(existing, fresh) {
  const bySeq = new Map(existing.map((r) => [r.seq, r]));
  for (const r of fresh) bySeq.set(r.seq, r);
  return [...bySeq.values()].sort((a, b) => b.seq - a.seq);
}

export function snippets(url) {
  const body = '{"type": "payment.succeeded", "id": "pi_1", "amount": 4200}';
  return {
    curl: `curl -X POST '${url}/payments' \\\n  -H 'Content-Type: application/json' \\\n  -d '${body}'`,
    js: `await fetch('${url}/payments', {\n  method: 'POST',\n  headers: { 'Content-Type': 'application/json' },\n  body: JSON.stringify(${body}),\n});`,
    python: `import requests\n\nrequests.post('${url}/payments', json=${body})`,
    java: `HttpClient.newHttpClient().send(\n    HttpRequest.newBuilder(URI.create("${url}/payments"))\n        .header("Content-Type", "application/json")\n        .POST(HttpRequest.BodyPublishers.ofString("""\n            ${body}"""))\n        .build(),\n    HttpResponse.BodyHandlers.ofString());`,
  };
}

export function tunnelCommand(origin, id, token, to = 'http://localhost:3000') {
  return `java -jar mockhub.jar tunnel --server ${origin} --token bin_${id}_${token} --name bin-${id} --to ${to}`;
}

export function flow(routing, rulesOn, ruleCount) {
  const steps = ['Sender', 'Your bin'];
  if (rulesOn && ruleCount > 0) steps.push(`${ruleCount} rule${ruleCount === 1 ? '' : 's'}`);
  if (routing.mode === 'forward') steps.push(routing.target ? hostOf(routing.target) + (routing.pathPrefix || '') : 'Target not set');
  else if (routing.mode === 'tunnel') steps.push('Your machine');
  else steps.push('Default answer');
  return steps;
}

export function routeSummary(routing, rulesOn, ruleCount, tunnelOnline) {
  const rulesPart = rulesOn && ruleCount > 0 ? `${ruleCount} rule${ruleCount === 1 ? '' : 's'}, then ` : '';
  if (routing.mode === 'forward') return rulesPart + (routing.target ? hostOf(routing.target) + (routing.pathPrefix || '') : 'a target not set yet');
  if (routing.mode === 'tunnel') return `${rulesPart}your machine (${tunnelOnline ? 'tunnel connected' : 'tunnel not connected'})`;
  return `${rulesPart}default answer`;
}

function hostOf(target) {
  try {
    return new URL(target).host;
  } catch (e) {
    return target;
  }
}

export function routingFromForm(form) {
  return {
    mode: form.mode,
    target: (form.target ?? '').trim(),
    pathPrefix: (form.pathPrefix ?? '').trim(),
    insecure: Boolean(form.insecure),
    requestHeaders: parseHeaderLines(form.requestHeaders),
    responseHeaders: parseHeaderLines(form.responseHeaders),
  };
}

const SAMPLE = { method: 'POST', path: '/orders', query: 'currency=EUR', headers: { 'Content-Type': 'application/json' }, body: '{"id": "ord_1", "type": "order.created"}' };

export function previewRequest(captured) {
  const r = captured ?? SAMPLE;
  return { method: r.method, url: `${r.path || '/'}${r.query ? `?${r.query}` : ''}`, headers: r.headers ?? {}, body: r.body ?? '' };
}

export function binLink(origin, pathname, id, key, viewOnly = false) {
  return `${origin}${pathname}#${viewOnly ? 'view' : 'bin'}=${id}.${key}`;
}

export function parseBinHash(hash) {
  const m = /^#(bin|view)=([a-z0-9]{12})\.([a-z0-9]{32})$/.exec(hash ?? '');
  return m ? { id: m[2], token: m[3], viewOnly: m[1] === 'view' } : null;
}

export function optionsFromForm(form) {
  const name = (form.requiredName ?? '').trim();
  const rate = String(form.rateRequests ?? '').trim();
  const latency = Number(form.latencyMs || 0);
  if (name && !(form.requiredValue ?? '')) return { error: `Give ${name} a value, or clear its name.` };
  if (!Number.isInteger(latency) || latency < 0 || latency > 15000) return { error: 'Default latency goes from 0 to 15000 ms.' };
  if (rate && !(Number.isInteger(Number(rate)) && Number(rate) >= 1 && Number(rate) <= 10000)) return { error: 'A rate limit allows 1 to 10000 requests.' };
  return {
    options: {
      cors: Boolean(form.cors),
      latencyMs: latency,
      rateLimit: rate ? { requests: Number(rate), per: form.per || 'minute' } : null,
      requiredHeader: name ? { name, value: form.requiredValue } : null,
    },
  };
}

export function optionsSummary(options) {
  if (!options) return '';
  const parts = [];
  if (options.requiredHeader) parts.push(`needs ${options.requiredHeader.name}`);
  if (options.rateLimit) parts.push(`${options.rateLimit.requests} a ${options.rateLimit.per}`);
  if (options.latencyMs > 0) parts.push(`+${options.latencyMs} ms`);
  if (options.cors === false) parts.push('no CORS');
  return parts.join(' · ');
}

export function parseShareHash(hash) {
  const m = /^#request=([a-z0-9]{12})\.(\d{1,12})\.(s[a-f0-9]{31})$/.exec(hash ?? '');
  return m ? { id: m[1], seq: Number(m[2]), key: m[3] } : null;
}

export function shareLink(origin, pathname, id, seq, key) {
  return `${origin}${pathname}#request=${id}.${seq}.${key}`;
}

export function filterQuery(filter = {}) {
  const params = new URLSearchParams();
  for (const name of ['q', 'method', 'status']) {
    const value = (filter[name] ?? '').trim();
    if (value) params.set(name, value);
  }
  const text = params.toString();
  return text ? `&${text}` : '';
}

export function filtered(filter = {}) {
  return filterQuery(filter) !== '';
}

export function ruleFromRequest(r) {
  const answer = r.outcome && !r.outcome.by?.endsWith('error')
    ? { ...blankAnswer(), status: r.outcome.status, headers: { ...(r.outcome.headers ?? {}) }, body: r.outcome.body ?? '' }
    : blankAnswer();
  delete answer.headers['Content-Length'];
  delete answer.headers['X-MockHub-Bin'];
  const conditions = [{ target: 'path', key: '', op: 'equals', value: r.path || '/' }];
  if (r.event) conditions.push({ target: 'body', key: '', op: 'contains', value: r.event });
  return { ...blankRule(), name: `${r.method} ${r.path}`.slice(0, 80), method: r.method, conditions, answers: [answer] };
}

export function requestBytes(r) {
  if (r.raw) return Uint8Array.from(atob(r.raw), (c) => c.charCodeAt(0));
  return new TextEncoder().encode(r.body ?? '');
}

function indexOf(bytes, needle, from) {
  outer: for (let i = from; i <= bytes.length - needle.length; i += 1) {
    for (let j = 0; j < needle.length; j += 1) if (bytes[i + j] !== needle[j]) continue outer;
    return i;
  }
  return -1;
}

export function multipartParts(bytes, contentType) {
  const boundary = /boundary="?([^";]+)"?/i.exec(contentType ?? '')?.[1];
  if (!/^multipart\//i.test(contentType ?? '') || !boundary) return [];
  const enc = new TextEncoder();
  const dec = new TextDecoder();
  const marker = enc.encode(`--${boundary}`);
  const blank = enc.encode('\r\n\r\n');
  const parts = [];
  let at = indexOf(bytes, marker, 0);
  while (at >= 0) {
    const start = at + marker.length;
    if (bytes[start] === 45 && bytes[start + 1] === 45) break;
    const next = indexOf(bytes, marker, start);
    if (next < 0) break;
    const headEnd = indexOf(bytes, blank, start);
    if (headEnd < 0 || headEnd > next) break;
    const head = dec.decode(bytes.subarray(start, headEnd));
    const disposition = /content-disposition:([^\r\n]*)/i.exec(head)?.[1] ?? '';
    const end = bytes[next - 2] === 13 && bytes[next - 1] === 10 ? next - 2 : next;
    parts.push({
      name: /\bname="([^"]*)"/i.exec(disposition)?.[1] ?? '',
      filename: /filename="([^"]*)"/i.exec(disposition)?.[1] ?? '',
      type: /content-type:\s*([^\r\n]*)/i.exec(head)?.[1]?.trim() ?? '',
      bytes: bytes.subarray(headEnd + blank.length, end),
    });
    at = next;
  }
  return parts;
}

export const MAX_SAVED_BINS = 50;

export function rememberBin(list, entry, now = Date.now()) {
  const known = list.find((b) => b.id === entry.id);
  if (known) return list.map((b) => (b.id === entry.id ? { ...b, token: entry.token } : b));
  const taken = list.map((b) => /^Bin (\d+)$/.exec(b.name ?? '')).filter(Boolean).map((m) => Number(m[1]));
  const name = `Bin ${taken.length ? Math.max(...taken) + 1 : list.length + 1}`;
  return [...list, { id: entry.id, token: entry.token, name, added: now }].slice(-MAX_SAVED_BINS);
}

export function forgetBin(list, id) {
  return list.filter((b) => b.id !== id);
}

export function renameBin(list, id, name) {
  const clean = (name ?? '').trim().slice(0, 40);
  if (!clean) return list;
  return list.map((b) => (b.id === id ? { ...b, name: clean } : b));
}
