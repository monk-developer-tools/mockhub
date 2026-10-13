import type { Json, JsonObject, Stub } from '../api/types';

export type UrlMatchType = 'url' | 'urlPath' | 'urlPathTemplate' | 'urlPattern' | 'urlPathPattern' | 'any';
export type ValueOperator = 'equalTo' | 'contains' | 'matches' | 'doesNotMatch' | 'doesNotContain' | 'absent' | 'present';
export type BodyOperator =
  | 'equalToJson'
  | 'matchesJsonPath'
  | 'equalToXml'
  | 'matchesXPath'
  | 'equalTo'
  | 'contains'
  | 'matches'
  | 'doesNotMatch';
export type BodyType = 'json' | 'text' | 'xml' | 'file' | 'base64' | 'none';
export type DelayType = 'none' | 'fixed' | 'uniform' | 'lognormal' | 'chunked';
export type ResponseMode = 'direct' | 'proxy' | 'fault';
export type StubKind = 'rest' | 'soap';
export type Fault = 'CONNECTION_RESET_BY_PEER' | 'EMPTY_RESPONSE' | 'MALFORMED_RESPONSE_CHUNK' | 'RANDOM_DATA_THEN_CLOSE';

export interface ValueMatcher {
  key: string;
  operator: ValueOperator;
  value: string;
  caseInsensitive: boolean;
}

export interface BodyMatcher {
  operator: BodyOperator;
  value: string;
  ignoreArrayOrder: boolean;
  ignoreExtraElements: boolean;
  expectedValue: string;
}

export interface KeyValue {
  key: string;
  value: string;
}

export interface StubForm {
  kind: StubKind;
  name: string;
  description: string;
  folder: string;
  priority: string;
  method: string;
  urlMatch: UrlMatchType;
  url: string;
  query: ValueMatcher[];
  headers: ValueMatcher[];
  cookies: ValueMatcher[];
  body: BodyMatcher[];
  scenarioName: string;
  requiredState: string;
  newState: string;
  responseMode: ResponseMode;
  status: string;
  statusMessage: string;
  responseHeaders: KeyValue[];
  bodyType: BodyType;
  responseBody: string;
  templating: boolean;
  delayType: DelayType;
  fixedDelay: string;
  delayLower: string;
  delayUpper: string;
  delayMedian: string;
  delaySigma: string;
  chunks: string;
  chunkDuration: string;
  fault: Fault;
  proxyBaseUrl: string;
  proxyRemovePrefix: string;
  soapVersion: '1.1' | '1.2';
  soapAction: string;
  soapOperation: string;
  sampleBody: string;
}

export interface Conversion {
  form: StubForm;
  unsupported: string[];
}

export const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'ANY'];
export const VALUE_OPERATORS: ValueOperator[] = ['equalTo', 'contains', 'matches', 'doesNotMatch', 'doesNotContain', 'absent', 'present'];
export const BODY_OPERATORS: BodyOperator[] = [
  'equalToJson',
  'matchesJsonPath',
  'equalToXml',
  'matchesXPath',
  'equalTo',
  'contains',
  'matches',
  'doesNotMatch',
];
export const FAULTS: Fault[] = ['CONNECTION_RESET_BY_PEER', 'EMPTY_RESPONSE', 'MALFORMED_RESPONSE_CHUNK', 'RANDOM_DATA_THEN_CLOSE'];
const URL_FIELDS: Exclude<UrlMatchType, 'any'>[] = ['url', 'urlPath', 'urlPathTemplate', 'urlPattern', 'urlPathPattern'];
const TEMPLATE_TRANSFORMER = 'response-template';

export const SOAP_ENVELOPES = {
  '1.1': 'http://schemas.xmlsoap.org/soap/envelope/',
  '1.2': 'http://www.w3.org/2003/05/soap-envelope',
} as const;

export function soapContentType(version: '1.1' | '1.2') {
  return version === '1.2' ? 'application/soap+xml; charset=utf-8' : 'text/xml; charset=utf-8';
}

export function soapOperationXPath(operation: string) {
  return `//*[local-name()='Body']/*[local-name()='${operation}']`;
}

export function emptyForm(kind: StubKind = 'rest'): StubForm {
  const soap = kind === 'soap';
  return {
    kind,
    name: '',
    description: '',
    folder: '',
    priority: '',
    method: soap ? 'POST' : 'GET',
    urlMatch: soap ? 'urlPath' : 'urlPath',
    url: soap ? '/ws/service' : '/',
    query: [],
    headers: [],
    cookies: [],
    body: [],
    scenarioName: '',
    requiredState: '',
    newState: '',
    responseMode: 'direct',
    status: '200',
    statusMessage: '',
    responseHeaders: [{ key: 'Content-Type', value: soap ? soapContentType('1.1') : 'application/json' }],
    bodyType: soap ? 'xml' : 'json',
    responseBody: soap ? soapEnvelope('1.1', '<ns:OperationResponse xmlns:ns="http://example.com/">\n      <ns:result>ok</ns:result>\n    </ns:OperationResponse>') : '{\n  "message": "Hello from MockHub"\n}',
    templating: false,
    delayType: 'none',
    fixedDelay: '500',
    delayLower: '100',
    delayUpper: '800',
    delayMedian: '200',
    delaySigma: '0.4',
    chunks: '5',
    chunkDuration: '1000',
    fault: 'CONNECTION_RESET_BY_PEER',
    proxyBaseUrl: '',
    proxyRemovePrefix: '',
    soapVersion: '1.1',
    soapAction: '',
    soapOperation: soap ? 'Operation' : '',
    sampleBody: '',
  };
}

export function soapEnvelope(version: '1.1' | '1.2', body: string) {
  return `<soap:Envelope xmlns:soap="${SOAP_ENVELOPES[version]}">\n  <soap:Header/>\n  <soap:Body>\n    ${body}\n  </soap:Body>\n</soap:Envelope>`;
}

export function soapFault(version: '1.1' | '1.2', message: string) {
  const fault =
    version === '1.2'
      ? `<soap:Fault>\n      <soap:Code><soap:Value>soap:Receiver</soap:Value></soap:Code>\n      <soap:Reason><soap:Text xml:lang="en">${message}</soap:Text></soap:Reason>\n    </soap:Fault>`
      : `<soap:Fault>\n      <faultcode>soap:Server</faultcode>\n      <faultstring>${message}</faultstring>\n    </soap:Fault>`;
  return soapEnvelope(version, fault);
}

const str = (v: Json | undefined) => (v === undefined || v === null ? '' : String(v));
const obj = (v: Json | undefined): JsonObject => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});

function readValueMatchers(source: Json | undefined, unsupported: string[], label: string): ValueMatcher[] {
  return Object.entries(obj(source)).map(([key, raw]) => {
    const m = obj(raw);
    const caseInsensitive = m.caseInsensitive === true;
    if ('absent' in m) return { key, operator: m.absent ? 'absent' : 'present', value: '', caseInsensitive };
    const operator = VALUE_OPERATORS.find((op) => op in m && op !== 'absent' && op !== 'present');
    if (!operator) {
      unsupported.push(`${label} "${key}" uses ${Object.keys(m).join(', ') || 'an empty matcher'}`);
      return { key, operator: 'equalTo', value: JSON.stringify(m), caseInsensitive };
    }
    return { key, operator, value: str(m[operator]), caseInsensitive };
  });
}

function writeValueMatchers(matchers: ValueMatcher[]): JsonObject | undefined {
  const out: JsonObject = {};
  for (const m of matchers) {
    if (!m.key.trim()) continue;
    if (m.operator === 'absent') out[m.key] = { absent: true };
    else if (m.operator === 'present') out[m.key] = { matches: '.*' };
    else {
      const matcher: JsonObject = { [m.operator]: m.value };
      if (m.caseInsensitive && m.operator === 'equalTo') matcher.caseInsensitive = true;
      out[m.key] = matcher;
    }
  }
  return Object.keys(out).length ? out : undefined;
}

function readBodyMatchers(source: Json | undefined, unsupported: string[]): BodyMatcher[] {
  if (!Array.isArray(source)) return [];
  return source.map((raw) => {
    const m = obj(raw);
    const operator = BODY_OPERATORS.find((op) => op in m);
    const base = { ignoreArrayOrder: m.ignoreArrayOrder === true, ignoreExtraElements: m.ignoreExtraElements === true, expectedValue: '' };
    if (!operator) {
      unsupported.push(`Body matcher ${Object.keys(m).join(', ')}`);
      return { operator: 'equalTo' as BodyOperator, value: JSON.stringify(m), ...base };
    }
    const value = m[operator];
    if ((operator === 'matchesJsonPath' || operator === 'matchesXPath') && value && typeof value === 'object') {
      const inner = obj(value);
      const expected = inner.equalTo ?? inner.contains ?? inner.matches;
      if (inner.expression === undefined || Object.keys(inner).some((k) => !['expression', 'equalTo', 'xPathNamespaces'].includes(k))) {
        unsupported.push(`${operator} with a nested ${Object.keys(inner).filter((k) => k !== 'expression').join(', ')} matcher`);
      }
      return { operator, value: str(inner.expression), ...base, expectedValue: str(expected) };
    }
    if (operator === 'equalToJson' && value !== null && typeof value === 'object') {
      return { operator, value: JSON.stringify(value, null, 2), ...base };
    }
    return { operator, value: str(value), ...base };
  });
}

function writeBodyMatchers(matchers: BodyMatcher[]): Json[] | undefined {
  const out = matchers
    .filter((m) => m.value.trim() !== '')
    .map((m): JsonObject => {
      if ((m.operator === 'matchesJsonPath' || m.operator === 'matchesXPath') && m.expectedValue) {
        return { [m.operator]: { expression: m.value, equalTo: m.expectedValue } };
      }
      const matcher: JsonObject = { [m.operator]: m.value };
      if (m.operator === 'equalToJson') {
        if (m.ignoreArrayOrder) matcher.ignoreArrayOrder = true;
        if (m.ignoreExtraElements) matcher.ignoreExtraElements = true;
      }
      return matcher;
    });
  return out.length ? out : undefined;
}

export function fromStub(stub: Stub): Conversion {
  const unsupported: string[] = [];
  const request = obj(stub.request);
  const response = obj(stub.response);
  const meta = obj(obj(stub.metadata).mockhub);
  const kind: StubKind = meta.type === 'soap' ? 'soap' : 'rest';
  const form = emptyForm(kind);

  form.name = str(stub.name);
  form.priority = str(stub.priority);
  form.description = str(meta.description);
  form.folder = str(meta.folder);
  form.method = str(request.method) || 'ANY';
  const urlField = URL_FIELDS.find((f) => request[f] !== undefined);
  form.urlMatch = urlField ?? 'any';
  form.url = urlField ? str(request[urlField]) : '';
  form.query = readValueMatchers(request.queryParameters, unsupported, 'Query parameter');
  form.headers = readValueMatchers(request.headers, unsupported, 'Header');
  form.cookies = readValueMatchers(request.cookies, unsupported, 'Cookie');
  form.body = readBodyMatchers(request.bodyPatterns, unsupported);
  for (const key of ['basicAuthCredentials', 'formParameters', 'multipartPatterns', 'customMatcher', 'host', 'port', 'scheme', 'pathParameters']) {
    if (request[key] !== undefined) unsupported.push(`Request ${key}`);
  }

  form.scenarioName = str(stub.scenarioName);
  form.requiredState = str(stub.requiredScenarioState);
  form.newState = str(stub.newScenarioState);

  form.responseMode = response.fault ? 'fault' : response.proxyBaseUrl ? 'proxy' : 'direct';
  form.status = str(response.status) || '200';
  form.statusMessage = str(response.statusMessage);
  form.responseHeaders = Object.entries(obj(response.headers)).map(([key, value]) => ({
    key,
    value: Array.isArray(value) ? value.join(', ') : str(value),
  }));
  if (response.jsonBody !== undefined) {
    form.bodyType = 'json';
    form.responseBody = JSON.stringify(response.jsonBody, null, 2);
  } else if (response.bodyFileName !== undefined) {
    form.bodyType = 'file';
    form.responseBody = str(response.bodyFileName);
  } else if (response.base64Body !== undefined) {
    form.bodyType = 'base64';
    form.responseBody = str(response.base64Body);
  } else if (response.body !== undefined) {
    const body = str(response.body);
    form.bodyType = body.trimStart().startsWith('<') ? 'xml' : looksLikeJson(body) ? 'json' : 'text';
    form.responseBody = body;
  } else {
    form.bodyType = 'none';
    form.responseBody = '';
  }
  const transformers = Array.isArray(response.transformers) ? response.transformers.map(String) : [];
  form.templating = transformers.includes(TEMPLATE_TRANSFORMER);
  if (transformers.some((t) => t !== TEMPLATE_TRANSFORMER)) unsupported.push(`Transformers ${transformers.join(', ')}`);

  if (response.fixedDelayMilliseconds !== undefined) {
    form.delayType = 'fixed';
    form.fixedDelay = str(response.fixedDelayMilliseconds);
  } else if (response.delayDistribution) {
    const d = obj(response.delayDistribution);
    if (d.type === 'uniform') {
      form.delayType = 'uniform';
      form.delayLower = str(d.lower);
      form.delayUpper = str(d.upper);
    } else if (d.type === 'lognormal') {
      form.delayType = 'lognormal';
      form.delayMedian = str(d.median);
      form.delaySigma = str(d.sigma);
    } else {
      unsupported.push(`Delay distribution ${str(d.type)}`);
    }
  } else if (response.chunkedDribbleDelay) {
    const c = obj(response.chunkedDribbleDelay);
    form.delayType = 'chunked';
    form.chunks = str(c.numberOfChunks);
    form.chunkDuration = str(c.totalDuration);
  }
  if (response.fault) form.fault = str(response.fault) as Fault;
  form.proxyBaseUrl = str(response.proxyBaseUrl);
  form.proxyRemovePrefix = str(response.proxyUrlPrefixToRemove);
  for (const key of ['additionalProxyRequestHeaders', 'removeProxyRequestHeaders', 'transformerParameters']) {
    if (response[key] !== undefined) unsupported.push(`Response ${key}`);
  }
  if (stub.postServeActions !== undefined || stub.serveEventListeners !== undefined) unsupported.push('Webhooks / serve event listeners');

  form.soapVersion = meta.soapVersion === '1.2' ? '1.2' : '1.1';
  form.soapAction = str(meta.soapAction);
  form.soapOperation = str(meta.operation);
  form.sampleBody = str(obj(meta.sampleRequest).body);
  if (kind === 'soap' && !form.soapOperation) {
    const xpath = form.body.find((b) => b.operator === 'matchesXPath')?.value ?? '';
    form.soapOperation = /local-name\(\)='([^']+)'\]$/.exec(xpath)?.[1] ?? '';
  }
  return { form, unsupported };
}

export function toStub(form: StubForm, original: Stub = {}): Stub {
  const stub: Stub = structuredClone(original);
  const request: JsonObject = { ...obj(stub.request) };
  for (const key of [...URL_FIELDS, 'method', 'queryParameters', 'headers', 'cookies', 'bodyPatterns']) delete request[key];
  request.method = form.method;
  if (form.urlMatch !== 'any') request[form.urlMatch] = form.url || '/';
  setIfDefined(request, 'queryParameters', writeValueMatchers(form.query));
  setIfDefined(request, 'headers', writeValueMatchers(form.headers));
  setIfDefined(request, 'cookies', writeValueMatchers(form.cookies));
  setIfDefined(request, 'bodyPatterns', writeBodyMatchers(form.body));
  stub.request = request;

  setOrDelete(stub, 'name', form.name.trim());
  setOrDelete(stub, 'priority', form.priority.trim() ? Number(form.priority) : undefined);
  setOrDelete(stub, 'scenarioName', form.scenarioName.trim());
  setOrDelete(stub, 'requiredScenarioState', form.scenarioName.trim() ? form.requiredState.trim() || 'Started' : '');
  setOrDelete(stub, 'newScenarioState', form.scenarioName.trim() ? form.newState.trim() : '');

  const previous = obj(stub.response);
  const response: JsonObject = {};
  for (const key of ['additionalProxyRequestHeaders', 'removeProxyRequestHeaders', 'transformerParameters']) {
    if (previous[key] !== undefined) response[key] = previous[key];
  }
  if (form.responseMode === 'fault') {
    response.fault = form.fault;
  } else if (form.responseMode === 'proxy') {
    response.proxyBaseUrl = form.proxyBaseUrl;
    if (form.proxyRemovePrefix) response.proxyUrlPrefixToRemove = form.proxyRemovePrefix;
  } else {
    response.status = Number(form.status) || 200;
    if (form.statusMessage) response.statusMessage = form.statusMessage;
    const headers: JsonObject = {};
    for (const h of form.responseHeaders) if (h.key.trim()) headers[h.key.trim()] = h.value;
    if (Object.keys(headers).length) response.headers = headers;
    writeResponseBody(response, form);
  }
  if (form.responseMode !== 'fault') writeDelay(response, form);
  if (form.templating && form.responseMode !== 'fault') response.transformers = [TEMPLATE_TRANSFORMER];
  stub.response = response;

  const metadata = obj(stub.metadata);
  const meta: JsonObject = { ...obj(metadata.mockhub), type: form.kind };
  setOrDelete(meta, 'description', form.description.trim());
  setOrDelete(meta, 'folder', form.folder.trim());
  if (form.kind === 'soap') {
    meta.soapVersion = form.soapVersion;
    setOrDelete(meta, 'soapAction', form.soapAction.trim());
    setOrDelete(meta, 'operation', form.soapOperation.trim());
  } else {
    for (const key of ['soapVersion', 'soapAction', 'operation']) delete meta[key];
  }
  const sample = { ...obj(meta.sampleRequest) };
  setOrDelete(sample, 'body', form.sampleBody);
  if (Object.keys(sample).length) meta.sampleRequest = sample;
  else delete meta.sampleRequest;
  stub.metadata = { ...metadata, mockhub: meta };
  return stub;
}

function writeResponseBody(response: JsonObject, form: StubForm) {
  switch (form.bodyType) {
    case 'json': {
      const parsed = tryParseJson(form.responseBody);
      if (parsed.ok && !form.templating) response.jsonBody = parsed.value;
      else if (form.responseBody) response.body = form.responseBody;
      break;
    }
    case 'file':
      if (form.responseBody) response.bodyFileName = form.responseBody;
      break;
    case 'base64':
      if (form.responseBody) response.base64Body = form.responseBody;
      break;
    case 'none':
      break;
    default:
      if (form.responseBody) response.body = form.responseBody;
  }
}

function writeDelay(response: JsonObject, form: StubForm) {
  switch (form.delayType) {
    case 'fixed':
      response.fixedDelayMilliseconds = Number(form.fixedDelay) || 0;
      break;
    case 'uniform':
      response.delayDistribution = { type: 'uniform', lower: Number(form.delayLower) || 0, upper: Number(form.delayUpper) || 0 };
      break;
    case 'lognormal':
      response.delayDistribution = { type: 'lognormal', median: Number(form.delayMedian) || 0, sigma: Number(form.delaySigma) || 0 };
      break;
    case 'chunked':
      response.chunkedDribbleDelay = { numberOfChunks: Number(form.chunks) || 1, totalDuration: Number(form.chunkDuration) || 0 };
      break;
    default:
      break;
  }
}

export function applySoapDefaults(form: StubForm): StubForm {
  const next = { ...form, method: 'POST', urlMatch: form.urlMatch === 'any' ? 'urlPath' : form.urlMatch } as StubForm;
  const operationXPath = soapOperationXPath(form.soapOperation || 'Operation');
  const others = form.body.filter((b) => !(b.operator === 'matchesXPath' && /local-name\(\)='Body'\]/.test(b.value)));
  next.body = form.soapOperation
    ? [{ operator: 'matchesXPath', value: operationXPath, ignoreArrayOrder: false, ignoreExtraElements: false, expectedValue: '' }, ...others]
    : others;
  const contentType = soapContentType(form.soapVersion);
  const withoutType = form.responseHeaders.filter((h) => h.key.toLowerCase() !== 'content-type');
  next.responseHeaders = [{ key: 'Content-Type', value: contentType }, ...withoutType];
  next.bodyType = 'xml';
  return next;
}

export function validate(form: StubForm): string[] {
  const errors: string[] = [];
  if (form.urlMatch !== 'any' && !form.url.trim()) errors.push('Enter a URL or choose "Any URL".');
  if ((form.urlMatch === 'urlPattern' || form.urlMatch === 'urlPathPattern') && !isValidRegex(form.url)) {
    errors.push('The URL pattern is not a valid regular expression.');
  }
  if (form.responseMode === 'direct') {
    const status = Number(form.status);
    if (!Number.isInteger(status) || status < 100 || status > 599) errors.push('Status must be a number between 100 and 599.');
    if (form.bodyType === 'json' && form.responseBody.trim() && !form.templating && !tryParseJson(form.responseBody).ok) {
      errors.push('The JSON body is not valid JSON. Fix it, switch to Text, or turn on templating.');
    }
  }
  if (form.responseMode === 'proxy' && !/^https?:\/\//.test(form.proxyBaseUrl)) errors.push('Proxy target must start with http:// or https://');
  if (form.priority.trim() && !/^\d+$/.test(form.priority.trim())) errors.push('Priority must be a whole number (1 is highest).');
  for (const b of form.body) {
    if (b.operator === 'equalToJson' && b.value.trim() && !tryParseJson(b.value).ok) errors.push('A JSON body matcher is not valid JSON.');
  }
  return errors;
}

export function describeUrl(stub: Stub): string {
  const request = obj(stub.request);
  const field = URL_FIELDS.find((f) => request[f] !== undefined);
  return field ? str(request[field]) : '*';
}

export function isPatternUrl(stub: Stub): boolean {
  const request = obj(stub.request);
  return request.urlPattern !== undefined || request.urlPathPattern !== undefined;
}

function setIfDefined(target: JsonObject, key: string, value: Json | undefined) {
  if (value !== undefined) target[key] = value;
}

function setOrDelete(target: JsonObject, key: string, value: Json | undefined) {
  if (value === undefined || value === '') delete target[key];
  else target[key] = value;
}

function tryParseJson(text: string): { ok: true; value: Json } | { ok: false } {
  try {
    return { ok: true, value: JSON.parse(text) };
  } catch {
    return { ok: false };
  }
}

function looksLikeJson(text: string) {
  const t = text.trim();
  return (t.startsWith('{') || t.startsWith('[')) && tryParseJson(t).ok;
}

function isValidRegex(pattern: string) {
  try {
    new RegExp(pattern);
    return true;
  } catch {
    return false;
  }
}
