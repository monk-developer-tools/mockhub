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

export const DYNAMIC_VALUES = [
  { label: 'A field from the JSON body', text: "{{jsonPath request.body '$.id'}}" },
  { label: 'The request path', text: '{{request.path}}' },
  { label: 'A query parameter', text: '{{request.query.name}}' },
  { label: 'A path parameter (from a pattern like /users/:id)', text: '{{pathParams.id}}' },
  { label: 'A request header', text: '{{request.headers.[X-Request-Id]}}' },
  { label: 'The whole request body', text: '{{{request.body}}}' },
  { label: 'Current time', text: '{{now}}' },
  { label: 'Random UUID', text: "{{randomValue type='UUID'}}" },
  { label: 'Random number', text: '{{randomInt lower=1 upper=1000}}' },
  { label: 'Fake full name', text: "{{fake 'Name.fullName'}}" },
  { label: 'Fake e-mail', text: "{{fake 'Internet.emailAddress'}}" },
];

export function blankAnswer() {
  return { weight: 1, status: 200, delayMs: 0, headers: { 'Content-Type': 'application/json' }, body: '{"ok": true}', template: false };
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
  const then = answers.length > 1 ? answers.map((a) => `${a.status} (${a.weight})`).join(' or ') : String(answers[0]?.status ?? 200);
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
