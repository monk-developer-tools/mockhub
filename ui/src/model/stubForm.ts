import { isTunnelUrl } from './tunnels';
import type { Json, JsonObject, RatePeriod, Stub } from '../api/types';
import { applyGrpc, readGrpc, validateGrpc } from './grpc';

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
export type ResponseMode = 'direct' | 'proxy' | 'fault' | 'callout' | 'graphql' | 'echo';
export type StubKind = 'rest' | 'soap' | 'graphql' | 'grpc';
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

export interface Webhook {
  method: string;
  url: string;
  headers: KeyValue[];
  body: string;
  delay: string;
  forward: boolean;
  connection?: string;
  retries?: string;
  retryBackoff?: string;
  retryStatuses?: string;
}

export interface Alternative {
  weight: string;
  status: string;
  headers: KeyValue[];
  body: string;
}

export const WEBHOOK_METHODS = ['POST', 'PUT', 'PATCH', 'GET', 'DELETE'];
export const CALLOUT_LISTENER = 'mockhub-callout';
export const SYNC_CALLOUT_TRANSFORMER = 'mockhub-sync-callout';
export const WEIGHTED_TRANSFORMER = 'mockhub-weighted';
export const CONNECTION_TRANSFORMER = 'mockhub-connection';
export const GRAPHQL_TRANSFORMER = 'mockhub-graphql';
export const ECHO_TRANSFORMER = 'mockhub-echo';
const GRAPHQL_OPERATION = /^\$\[\?\(@\.operationName == '([A-Za-z_][A-Za-z0-9_]*)'\)\]$/;

export function graphqlOperationPattern(operation: string): string {
  return `$[?(@.operationName == '${operation}')]`;
}
export const MAX_RETRIES = 5;
const CALLOUT_LISTENERS = new Set(['webhook', CALLOUT_LISTENER]);

export function emptyWebhook(): Webhook {
  return {
    method: 'POST',
    url: '',
    headers: [{ key: 'Content-Type', value: 'application/json' }],
    body: '{\n  "event": "created",\n  "request": {{{originalRequest.body}}}\n}',
    delay: '',
    forward: false,
  };
}

export function emptyAlternative(): Alternative {
  return { weight: '1', status: '503', headers: [], body: '{\n  "error": "Service unavailable"\n}' };
}

export function emptySyncCallout(): Webhook {
  return { method: 'POST', url: '', headers: [], body: '{{{originalRequest.body}}}', delay: '', forward: true };
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
  proxyConnection: string;
  graphqlSchema: string;
  graphqlOperation: string;
  graphqlVariables: string;
  grpcService: string;
  grpcMethod: string;
  grpcStatus: string;
  grpcReason: string;
  soapVersion: '1.1' | '1.2';
  soapAction: string;
  soapOperation: string;
  sampleBody: string;
  webhooks: Webhook[];
  syncCallout: Webhook;
  errorPercent: string;
  errorMode: 'status' | 'fault';
  errorStatus: string;
  errorFault: Fault;
  errorBody: string;
  rateRequests: string;
  ratePer: RatePeriod;
  mainWeight: string;
  alternatives: Alternative[];
}

export interface Conversion {
  form: StubForm;
  unsupported: string[];
}

export const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'QUERY', 'ANY'];
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
  const graphql = kind === 'graphql';
  const grpc = kind === 'grpc';
  return {
    kind,
    name: '',
    description: '',
    folder: '',
    priority: '',
    method: soap || graphql || grpc ? 'POST' : 'GET',
    urlMatch: soap ? 'urlPath' : 'urlPath',
    url: soap ? '/ws/service' : graphql ? '/graphql' : grpc ? '' : '/',
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
    responseHeaders: grpc ? [] : [{ key: 'Content-Type', value: soap ? soapContentType('1.1') : 'application/json' }],
    bodyType: soap ? 'xml' : 'json',
    responseBody: soap ? soapEnvelope('1.1', '<ns:OperationResponse xmlns:ns="http://example.com/">\n      <ns:result>ok</ns:result>\n    </ns:OperationResponse>') : graphql ? '{\n  "data": {\n    "thing": null\n  }\n}' : grpc ? '{}' : '{\n  "message": "Hello from MockHub"\n}',
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
    proxyConnection: '',
    graphqlSchema: '',
    graphqlOperation: graphql ? 'GetThing' : '',
    graphqlVariables: '',
    grpcService: '',
    grpcMethod: '',
    grpcStatus: 'OK',
    grpcReason: '',
    soapVersion: '1.1',
    soapAction: '',
    soapOperation: soap ? 'Operation' : '',
    sampleBody: '',
    webhooks: [],
    syncCallout: emptySyncCallout(),
    errorPercent: '',
    errorMode: 'status',
    errorStatus: soap ? '500' : '503',
    errorFault: 'CONNECTION_RESET_BY_PEER',
    errorBody: '',
    rateRequests: '',
    ratePer: 'minute',
    mainWeight: '1',
    alternatives: [],
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
  const kind: StubKind = meta.type === 'soap' ? 'soap' : meta.type === 'graphql' ? 'graphql' : meta.type === 'grpc' ? 'grpc' : 'rest';
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
  form.body = readBodyMatchers(kind === 'graphql' ? readGraphqlMatch(form, request.bodyPatterns) : request.bodyPatterns, unsupported);
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
  const waitsForCallout = transformers.includes(SYNC_CALLOUT_TRANSFORMER) && !response.fault && !response.proxyBaseUrl;
  const known = new Set([TEMPLATE_TRANSFORMER, SYNC_CALLOUT_TRANSFORMER, WEIGHTED_TRANSFORMER, CONNECTION_TRANSFORMER, GRAPHQL_TRANSFORMER, ECHO_TRANSFORMER]);
  if (transformers.some((t) => !known.has(t))) unsupported.push(`Transformers ${transformers.join(', ')}`);
  const transformerParameters = obj(response.transformerParameters);
  const weighted = transformers.includes(WEIGHTED_TRANSFORMER) && form.responseMode === 'direct';
  if (weighted) readAlternatives(form, transformerParameters.responses);
  const fromSchema = transformers.includes(GRAPHQL_TRANSFORMER) && !response.fault && !response.proxyBaseUrl;
  if (fromSchema) {
    form.responseMode = 'graphql';
    form.templating = false;
    form.graphqlSchema = str(transformerParameters.schema);
  }
  if (transformers.includes(ECHO_TRANSFORMER) && !response.fault && !response.proxyBaseUrl) {
    form.responseMode = 'echo';
    form.templating = false;
  }
  if (waitsForCallout) {
    form.responseMode = 'callout';
    form.templating = false;
    form.syncCallout = readCallout(obj(transformerParameters.callout));
  }

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
  const signsProxy = form.responseMode === 'proxy' && transformers.includes(CONNECTION_TRANSFORMER);
  if (signsProxy) form.proxyConnection = str(transformerParameters.connection);
  for (const key of ['additionalProxyRequestHeaders', 'removeProxyRequestHeaders']) {
    if (response[key] !== undefined) unsupported.push(`Response ${key}`);
  }
  const otherParameters = Object.keys(transformerParameters).filter((k) => !(waitsForCallout && k === 'callout') && !(weighted && k === 'responses') && !(signsProxy && k === 'connection') && !(fromSchema && k === 'schema'));
  if (otherParameters.length) unsupported.push(`Response transformerParameters ${otherParameters.join(', ')}`);
  form.webhooks = [];
  for (const listener of Array.isArray(stub.serveEventListeners) ? stub.serveEventListeners : []) {
    const l = obj(listener);
    if (!CALLOUT_LISTENERS.has(str(l.name))) {
      unsupported.push(`Serve event listener ${str(l.name)}`);
      continue;
    }
    const p = obj(l.parameters);
    form.webhooks.push(readCallout(p));
    if (p.delay !== undefined && obj(p.delay).type !== 'fixed') unsupported.push('Webhook delay that is not fixed');
  }
  if (stub.postServeActions !== undefined) unsupported.push('Post-serve actions');

  const errorRate = obj(meta.errorRate);
  if (Number(errorRate.percent) > 0) {
    form.errorPercent = str(errorRate.percent);
    form.errorMode = errorRate.fault ? 'fault' : 'status';
    if (errorRate.status !== undefined) form.errorStatus = str(errorRate.status);
    if (errorRate.fault) form.errorFault = str(errorRate.fault) as Fault;
    form.errorBody = str(errorRate.body);
  }
  const rateLimit = obj(meta.rateLimit);
  if (Number(rateLimit.requests) > 0) {
    form.rateRequests = str(rateLimit.requests);
    form.ratePer = (['second', 'minute', 'hour'].includes(str(rateLimit.per)) ? str(rateLimit.per) : 'minute') as RatePeriod;
  }

  form.soapVersion = meta.soapVersion === '1.2' ? '1.2' : '1.1';
  form.soapAction = str(meta.soapAction);
  form.soapOperation = str(meta.operation);
  form.sampleBody = str(obj(meta.sampleRequest).body);
  if (kind === 'soap' && !form.soapOperation) {
    const xpath = form.body.find((b) => b.operator === 'matchesXPath')?.value ?? '';
    form.soapOperation = /local-name\(\)='([^']+)'\]$/.exec(xpath)?.[1] ?? '';
  }
  return { form: kind === 'grpc' ? readGrpc(form) : form, unsupported };
}

export function toStub(input: StubForm, original: Stub = {}): Stub {
  const form = input.kind === 'grpc' ? applyGrpc(input) : input;
  const stub: Stub = structuredClone(original);
  const request: JsonObject = { ...obj(stub.request) };
  for (const key of [...URL_FIELDS, 'method', 'queryParameters', 'headers', 'cookies', 'bodyPatterns']) delete request[key];
  request.method = form.method;
  if (form.urlMatch !== 'any') request[form.urlMatch] = form.url || '/';
  setIfDefined(request, 'queryParameters', writeValueMatchers(form.query));
  setIfDefined(request, 'headers', writeValueMatchers(form.headers));
  setIfDefined(request, 'cookies', writeValueMatchers(form.cookies));
  setIfDefined(request, 'bodyPatterns', form.kind === 'graphql' ? withGraphqlMatch(form, writeBodyMatchers(form.body)) : writeBodyMatchers(form.body));
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
  const parameters = { ...obj(response.transformerParameters) };
  delete parameters.callout;
  delete parameters.responses;
  delete parameters.connection;
  delete parameters.schema;
  if (form.responseMode === 'graphql') parameters.schema = form.graphqlSchema;
  const signsProxy = form.responseMode === 'proxy' && Boolean(form.proxyConnection.trim());
  if (signsProxy) parameters.connection = form.proxyConnection.trim();
  if (form.responseMode === 'callout') parameters.callout = writeCallout(form.syncCallout, false);
  if (Object.keys(parameters).length) response.transformerParameters = parameters;
  else delete response.transformerParameters;
  if (form.responseMode === 'callout') {
    response.status = 200;
    response.transformers = [SYNC_CALLOUT_TRANSFORMER];
  } else if (form.responseMode === 'graphql') {
    response.status = 200;
    const headers: JsonObject = {};
    for (const h of form.responseHeaders) if (h.key.trim()) headers[h.key.trim()] = h.value;
    if (Object.keys(headers).length) response.headers = headers;
    response.transformers = [GRAPHQL_TRANSFORMER];
  } else if (form.responseMode === 'echo') {
    response.status = Number(form.status) || 200;
    const headers: JsonObject = {};
    for (const h of form.responseHeaders) if (h.key.trim() && h.key.trim().toLowerCase() !== 'content-type') headers[h.key.trim()] = h.value;
    if (Object.keys(headers).length) response.headers = headers;
    response.transformers = [ECHO_TRANSFORMER];
  } else if (form.responseMode === 'fault') {
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
  if (form.templating && (form.responseMode === 'direct' || form.responseMode === 'proxy')) response.transformers = [TEMPLATE_TRANSFORMER];
  if (signsProxy) response.transformers = [...(Array.isArray(response.transformers) ? response.transformers : []), CONNECTION_TRANSFORMER];
  if (form.responseMode === 'direct' && form.alternatives.length) writeAlternatives(response, form);
  stub.response = response;

  const otherListeners = (Array.isArray(stub.serveEventListeners) ? stub.serveEventListeners : []).filter((l) => !CALLOUT_LISTENERS.has(str(obj(l).name)));
  const webhooks = form.webhooks
    .filter((w) => w.url.trim())
    .map((w) => ({ name: CALLOUT_LISTENER, parameters: writeCallout(w, true) }));
  const listeners = [...otherListeners, ...webhooks];
  if (listeners.length) stub.serveEventListeners = listeners;
  else delete stub.serveEventListeners;

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
  const percent = Number(form.errorPercent);
  if (form.responseMode !== 'fault' && percent > 0) {
    const errorRate: JsonObject = { percent: Math.min(100, percent) };
    if (form.errorMode === 'fault') errorRate.fault = form.errorFault;
    else {
      errorRate.status = Number(form.errorStatus) || 500;
      if (form.errorBody.trim()) errorRate.body = form.errorBody;
    }
    meta.errorRate = errorRate;
  } else {
    delete meta.errorRate;
  }
  if (Number(form.rateRequests) > 0) meta.rateLimit = { requests: Number(form.rateRequests), per: form.ratePer };
  else delete meta.rateLimit;
  const sample = { ...obj(meta.sampleRequest) };
  setOrDelete(sample, 'body', form.sampleBody);
  if (Object.keys(sample).length) meta.sampleRequest = sample;
  else delete meta.sampleRequest;
  stub.metadata = { ...metadata, mockhub: meta };
  return stub;
}

function readAlternatives(form: StubForm, source: Json | undefined) {
  const entries = (Array.isArray(source) ? source : []).map(obj);
  const first = entries[0];
  const isMain = first && first.body === undefined && first.jsonBody === undefined && first.headers === undefined;
  form.mainWeight = isMain ? str(first.weight ?? 1) : '0';
  form.alternatives = (isMain ? entries.slice(1) : entries).map((e) => ({
    weight: str(e.weight ?? 1),
    status: str(e.status ?? 200),
    headers: Object.entries(obj(e.headers)).map(([key, value]) => ({ key, value: str(value) })),
    body: e.jsonBody !== undefined ? JSON.stringify(e.jsonBody, null, 2) : str(e.body),
  }));
}

function writeAlternatives(response: JsonObject, form: StubForm) {
  const responses: Json[] = [{ weight: Number(form.mainWeight) || 0, status: Number(form.status) || 200 }];
  for (const a of form.alternatives) {
    const entry: JsonObject = { weight: Number(a.weight) || 0, status: Number(a.status) || 200 };
    const headers: JsonObject = {};
    for (const h of a.headers) if (h.key.trim()) headers[h.key.trim()] = h.value;
    if (Object.keys(headers).length) entry.headers = headers;
    if (a.body.trim()) entry.body = a.body;
    responses.push(entry);
  }
  response.transformerParameters = { ...obj(response.transformerParameters), responses };
  response.transformers = [...(Array.isArray(response.transformers) ? response.transformers : []), WEIGHTED_TRANSFORMER];
}

export function weightShares(form: StubForm): number[] {
  const weights = [form.mainWeight, ...form.alternatives.map((a) => a.weight)].map((w) => Math.max(0, Number(w) || 0));
  const total = weights.reduce((a, b) => a + b, 0);
  return weights.map((w) => (total ? Math.round((w / total) * 1000) / 10 : 0));
}

function readCallout(p: JsonObject): Webhook {
  return {
    method: str(p.method) || 'POST',
    url: str(p.url),
    headers: Object.entries(obj(p.headers)).map(([key, value]) => ({ key, value: Array.isArray(value) ? value.join(', ') : str(value) })),
    body: typeof p.body === 'string' ? p.body : p.body !== undefined ? JSON.stringify(p.body, null, 2) : '',
    delay: str(obj(p.delay).milliseconds),
    forward: p.forward === true,
    ...(p.connection ? { connection: str(p.connection) } : {}),
    ...readRetry(p.retry),
  };
}

function readGraphqlMatch(form: StubForm, source: Json | undefined): Json | undefined {
  form.graphqlOperation = '';
  if (!Array.isArray(source)) return source;
  return source.filter((raw) => {
    const m = obj(raw);
    const op = typeof m.matchesJsonPath === 'string' ? GRAPHQL_OPERATION.exec(m.matchesJsonPath) : null;
    if (op && Object.keys(m).length === 1) {
      form.graphqlOperation = op[1];
      return false;
    }
    const inner = obj(m.matchesJsonPath);
    if (inner.expression === '$.variables' && typeof inner.equalToJson === 'string' && Object.keys(m).length === 1) {
      form.graphqlVariables = inner.equalToJson;
      return false;
    }
    return true;
  });
}

function withGraphqlMatch(form: StubForm, rest: Json[] | undefined): Json[] | undefined {
  const out: Json[] = [];
  if (form.graphqlOperation.trim()) out.push({ matchesJsonPath: graphqlOperationPattern(form.graphqlOperation.trim()) });
  if (form.graphqlVariables.trim()) {
    out.push({ matchesJsonPath: { expression: '$.variables', equalToJson: form.graphqlVariables.trim(), ignoreExtraElements: true, ignoreArrayOrder: true } });
  }
  const all = [...out, ...(rest ?? [])];
  return all.length ? all : undefined;
}

function readRetry(raw: JsonObject[string]): Partial<Webhook> {
  if (typeof raw === 'number' || typeof raw === 'string') return Number(raw) > 0 ? { retries: str(raw) } : {};
  const r = obj(raw);
  if (!(Number(r.count) > 0)) return {};
  return {
    retries: str(r.count),
    ...(r.backoffMs !== undefined ? { retryBackoff: str(r.backoffMs) } : {}),
    ...(r.statuses !== undefined ? { retryStatuses: Array.isArray(r.statuses) ? r.statuses.join(',') : str(r.statuses) } : {}),
  };
}

function writeCallout(w: Webhook, withDelay: boolean): JsonObject {
  const parameters: JsonObject = w.forward ? { url: w.url.trim(), forward: true } : { method: w.method, url: w.url.trim() };
  const headers: JsonObject = {};
  for (const h of w.headers) if (h.key.trim()) headers[h.key.trim()] = h.value;
  if (Object.keys(headers).length) parameters.headers = headers;
  if (!w.forward && w.body.trim() && w.method !== 'GET') parameters.body = w.body;
  if (withDelay && Number(w.delay) > 0) parameters.delay = { type: 'fixed', milliseconds: Number(w.delay) };
  if (w.connection?.trim()) parameters.connection = w.connection.trim();
  const retries = Math.min(MAX_RETRIES, Math.floor(Number(w.retries) || 0));
  if (retries > 0) {
    const retry: JsonObject = { count: retries };
    if (Number(w.retryBackoff) > 0) retry.backoffMs = Number(w.retryBackoff);
    if (w.retryStatuses?.trim()) retry.statuses = w.retryStatuses.trim();
    parameters.retry = retry;
  }
  return parameters;
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

export function validate(input: StubForm): string[] {
  const form = input.kind === 'grpc' ? applyGrpc(input) : input;
  const errors: string[] = input.kind === 'grpc' ? validateGrpc(input) : [];
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
  if (form.responseMode === 'direct' && form.alternatives.length) {
    const weights = [form.mainWeight, ...form.alternatives.map((a) => a.weight)];
    if (weights.some((w) => !(Number(w) >= 0))) errors.push('Each response weight must be zero or a positive number.');
    else if (!weights.some((w) => Number(w) > 0)) errors.push('At least one response needs a weight above zero.');
    for (const a of form.alternatives) {
      const status = Number(a.status);
      if (!Number.isInteger(status) || status < 100 || status > 599) errors.push('Each other response needs a status between 100 and 599.');
    }
  }
  if (form.responseMode === 'proxy' && !/^https?:\/\//.test(form.proxyBaseUrl) && !isTunnelUrl(form.proxyBaseUrl)) errors.push('Proxy target must start with http://, https:// or tunnel://');
  if (form.responseMode === 'callout' && !form.syncCallout.url.trim()) errors.push('Enter the URL to call, e.g. https://partner.example.com/authorize');
  if (form.responseMode === 'graphql' && !form.graphqlSchema.trim()) errors.push('Paste the GraphQL schema the answers come from.');
  if (form.kind === 'graphql' && form.graphqlOperation.trim() && !/^[A-Za-z_][A-Za-z0-9_]*$/.test(form.graphqlOperation.trim())) {
    errors.push('The operation name can only hold letters, digits and underscores, e.g. GetUser.');
  }
  if (form.kind === 'graphql' && form.graphqlVariables.trim()) {
    const parsed = tryParseJson(form.graphqlVariables);
    if (!parsed.ok || parsed.value === null || typeof parsed.value !== 'object' || Array.isArray(parsed.value)) errors.push('Variables must be a JSON object, e.g. {"id": "42"}.');
  }
  for (const w of form.webhooks) {
    if (w.delay.trim() && !(Number(w.delay) >= 0)) errors.push('A webhook delay must be a number of milliseconds.');
  }
  if (form.priority.trim() && !/^\d+$/.test(form.priority.trim())) errors.push('Priority must be a whole number (1 is highest).');
  if (form.errorPercent.trim()) {
    const percent = Number(form.errorPercent);
    if (!Number.isFinite(percent) || percent < 0 || percent > 100) errors.push('The failure rate must be between 0 and 100 percent.');
    const status = Number(form.errorStatus);
    if (form.errorMode === 'status' && (!Number.isInteger(status) || status < 100 || status > 599)) {
      errors.push('The failure status must be a number between 100 and 599.');
    }
  }
  if (form.rateRequests.trim()) {
    const requests = Number(form.rateRequests);
    if (!Number.isInteger(requests) || requests < 0 || requests > 1_000_000) errors.push('The rate limit must be a whole number of requests up to 1,000,000.');
  }
  for (const b of form.body) {
    if (b.operator === 'equalToJson' && b.value.trim() && !tryParseJson(b.value).ok) errors.push('A JSON body matcher is not valid JSON.');
  }
  return errors;
}

export function isDisabled(stub: Stub): boolean {
  return obj(obj(stub.metadata).mockhub).disabled === true;
}

export function errorPercentOf(stub: Stub): number {
  return Number(obj(obj(obj(stub.metadata).mockhub).errorRate).percent) || 0;
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
