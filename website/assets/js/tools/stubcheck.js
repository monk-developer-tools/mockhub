const URL_KEYS = ['url', 'urlPath', 'urlPattern', 'urlPathPattern', 'urlPathTemplate'];
const METHODS = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS', 'TRACE', 'ANY']);
const BODY_KEYS = ['body', 'jsonBody', 'base64Body', 'bodyFileName'];
const OPERATORS = new Set(['equalTo', 'binaryEqualTo', 'contains', 'doesNotContain', 'matches', 'doesNotMatch', 'absent', 'equalToJson', 'matchesJsonPath', 'matchesJsonSchema', 'equalToXml', 'matchesXPath', 'before', 'after', 'equalToDateTime', 'and', 'or', 'hasExactly', 'includes', 'not']);
const MODIFIERS = new Set(['caseInsensitive', 'ignoreArrayOrder', 'ignoreExtraElements', 'expression', 'xPathNamespaces', 'enablePlaceholders', 'placeholderOpeningDelimiterRegex', 'placeholderClosingDelimiterRegex', 'exemptedComparisons', 'namespaceAwareness', 'actualFormat', 'truncateExpected', 'truncateActual', 'expectedOffset', 'expectedOffsetUnit', 'schemaVersion', 'submatch']);
const FAULTS = new Set(['EMPTY_RESPONSE', 'MALFORMED_RESPONSE_CHUNK', 'RANDOM_DATA_THEN_CLOSE', 'CONNECTION_RESET_BY_PEER']);
const REQUEST_KEYS = new Set([...URL_KEYS, 'method', 'headers', 'queryParameters', 'pathParameters', 'formParameters', 'cookies', 'bodyPatterns', 'basicAuthCredentials', 'multipartPatterns', 'customMatcher', 'host', 'port', 'scheme', 'clientIp']);
const RESPONSE_KEYS = new Set([...BODY_KEYS, 'status', 'statusMessage', 'headers', 'additionalProxyRequestHeaders', 'removeProxyRequestHeaders', 'proxyBaseUrl', 'proxyUrlPrefixToRemove', 'fixedDelayMilliseconds', 'delayDistribution', 'chunkedDribbleDelay', 'fault', 'transformers', 'transformerParameters', 'fromConfiguredStub']);
const STUB_KEYS = new Set(['id', 'uuid', 'name', 'request', 'response', 'priority', 'persistent', 'scenarioName', 'requiredScenarioState', 'newScenarioState', 'postServeActions', 'serveEventListeners', 'metadata', 'insertionIndex']);

export function locate(text, error) {
  const match = /position (\d+)/.exec(error.message) ?? /column (\d+)/.exec(error.message);
  const lineCol = /line (\d+) column (\d+)/.exec(error.message);
  if (lineCol) return { line: Number(lineCol[1]), column: Number(lineCol[2]) };
  if (!match) return null;
  const before = text.slice(0, Number(match[1]));
  const lines = before.split('\n');
  return { line: lines.length, column: lines[lines.length - 1].length + 1 };
}

export function parseStubs(text) {
  let value;
  try {
    value = JSON.parse(text);
  } catch (e) {
    const at = locate(text, e);
    return { error: at ? `Not valid JSON near line ${at.line}, column ${at.column}.` : 'Not valid JSON.' };
  }
  if (Array.isArray(value)) return { stubs: value };
  if (value && Array.isArray(value.mappings)) return { stubs: value.mappings };
  if (value && typeof value === 'object') return { stubs: [value] };
  return { error: 'Expected a stub object, an array of stubs, or {"mappings": [...]}.' };
}

function checkMatcher(path, matcher, problems) {
  if (matcher === null || typeof matcher !== 'object' || Array.isArray(matcher)) {
    problems.push({ level: 'error', path, text: 'A matcher must be an object such as {"equalTo": "value"}.' });
    return;
  }
  const keys = Object.keys(matcher);
  const ops = keys.filter((k) => OPERATORS.has(k));
  const unknown = keys.filter((k) => !OPERATORS.has(k) && !MODIFIERS.has(k));
  if (unknown.length) problems.push({ level: 'error', path, text: `Unknown matcher ${unknown.map((k) => `"${k}"`).join(', ')}.` });
  if (!ops.length && !unknown.length) problems.push({ level: 'error', path, text: 'The matcher has no operator such as equalTo or matches.' });
  if (ops.length > 1) problems.push({ level: 'error', path, text: `Use one operator per matcher, not ${ops.join(' and ')}. Wrap several in "and".` });
  for (const op of ['and', 'or']) {
    if (matcher[op] !== undefined) {
      if (!Array.isArray(matcher[op])) problems.push({ level: 'error', path: `${path}.${op}`, text: `"${op}" takes an array of matchers.` });
      else matcher[op].forEach((m, i) => checkMatcher(`${path}.${op}[${i}]`, m, problems));
    }
  }
  if (typeof matcher.matches === 'string' || typeof matcher.doesNotMatch === 'string') {
    try {
      new RegExp(matcher.matches ?? matcher.doesNotMatch);
    } catch (e) {
      problems.push({ level: 'error', path, text: 'The regular expression does not compile.' });
    }
  }
}

function checkMatcherMap(path, map, problems) {
  if (map === undefined) return;
  if (map === null || typeof map !== 'object' || Array.isArray(map)) {
    problems.push({ level: 'error', path, text: 'Expected an object of name → matcher.' });
    return;
  }
  for (const [name, matcher] of Object.entries(map)) checkMatcher(`${path}.${name}`, matcher, problems);
}

function checkRequest(request, problems) {
  if (!request || typeof request !== 'object') {
    problems.push({ level: 'error', path: 'request', text: 'Every stub needs a request object.' });
    return;
  }
  for (const key of Object.keys(request)) if (!REQUEST_KEYS.has(key)) problems.push({ level: 'warning', path: `request.${key}`, text: `"${key}" is not a WireMock request field; it is ignored.` });
  const urls = URL_KEYS.filter((k) => request[k] !== undefined);
  if (urls.length > 1) problems.push({ level: 'error', path: 'request', text: `Use one of ${urls.join(', ')}, not several.` });
  if (!urls.length) problems.push({ level: 'warning', path: 'request', text: 'No URL matcher, so the stub matches every path.' });
  if (request.method === undefined) problems.push({ level: 'warning', path: 'request.method', text: 'No method, so it matches any method. Set "ANY" to make that explicit.' });
  else if (!METHODS.has(String(request.method).toUpperCase())) problems.push({ level: 'error', path: 'request.method', text: `"${request.method}" is not an HTTP method.` });
  if (typeof request.url === 'string' && request.url.includes('{')) problems.push({ level: 'warning', path: 'request.url', text: 'url matches literally. Use urlPathTemplate for /items/{id}.' });
  if (typeof request.urlPath === 'string' && request.urlPath.includes('?')) problems.push({ level: 'warning', path: 'request.urlPath', text: 'urlPath never includes the query string. Use queryParameters or url.' });
  if (typeof request.urlPathTemplate === 'string' && !request.urlPathTemplate.includes('{')) problems.push({ level: 'info', path: 'request.urlPathTemplate', text: 'No {parameters} in the template. urlPath would do.' });
  for (const key of ['urlPattern', 'urlPathPattern']) {
    if (typeof request[key] === 'string') {
      try {
        new RegExp(request[key]);
      } catch (e) {
        problems.push({ level: 'error', path: `request.${key}`, text: 'The regular expression does not compile.' });
      }
    }
  }
  for (const key of ['headers', 'queryParameters', 'pathParameters', 'formParameters', 'cookies']) checkMatcherMap(`request.${key}`, request[key], problems);
  if (request.pathParameters && !request.urlPathTemplate) problems.push({ level: 'error', path: 'request.pathParameters', text: 'pathParameters only work with urlPathTemplate.' });
  if (request.bodyPatterns !== undefined) {
    if (!Array.isArray(request.bodyPatterns)) problems.push({ level: 'error', path: 'request.bodyPatterns', text: 'bodyPatterns is an array of matchers.' });
    else request.bodyPatterns.forEach((m, i) => checkMatcher(`request.bodyPatterns[${i}]`, m, problems));
  }
}

function checkResponse(response, problems) {
  if (!response || typeof response !== 'object') {
    problems.push({ level: 'error', path: 'response', text: 'Every stub needs a response object.' });
    return;
  }
  for (const key of Object.keys(response)) if (!RESPONSE_KEYS.has(key)) problems.push({ level: 'warning', path: `response.${key}`, text: `"${key}" is not a WireMock response field; it is ignored.` });
  const bodies = BODY_KEYS.filter((k) => response[k] !== undefined);
  if (bodies.length > 1) problems.push({ level: 'error', path: 'response', text: `Use one of ${bodies.join(', ')}, not several.` });
  if (response.status !== undefined && (!Number.isInteger(response.status) || response.status < 100 || response.status > 599)) problems.push({ level: 'error', path: 'response.status', text: 'status is a whole number from 100 to 599.' });
  if (response.fault !== undefined && !FAULTS.has(response.fault)) problems.push({ level: 'error', path: 'response.fault', text: `Unknown fault. Use one of ${[...FAULTS].join(', ')}.` });
  if (response.headers !== undefined) {
    for (const [name, value] of Object.entries(response.headers ?? {})) {
      if (typeof value !== 'string' && !Array.isArray(value)) problems.push({ level: 'error', path: `response.headers.${name}`, text: 'A header value is a string or an array of strings.' });
    }
  }
  if (response.transformers !== undefined && !Array.isArray(response.transformers)) problems.push({ level: 'error', path: 'response.transformers', text: 'transformers is an array of names.' });
  if (typeof response.body === 'string' && response.headers === undefined && /^\s*[[{]/.test(response.body)) problems.push({ level: 'info', path: 'response.body', text: 'Looks like JSON but no Content-Type header is set.' });
  const text = JSON.stringify([response.body, response.jsonBody, response.headers]);
  if (text.includes('{{') && !(response.transformers ?? []).includes('response-template')) problems.push({ level: 'warning', path: 'response', text: 'Uses {{ }} but does not list the response-template transformer, so the braces are sent as plain text unless the server templates every stub. Add "transformers": ["response-template"].' });
  if (response.fixedDelayMilliseconds !== undefined && (!Number.isInteger(response.fixedDelayMilliseconds) || response.fixedDelayMilliseconds < 0)) problems.push({ level: 'error', path: 'response.fixedDelayMilliseconds', text: 'The delay is a whole number of milliseconds.' });
}

export function check(stub) {
  const problems = [];
  if (!stub || typeof stub !== 'object' || Array.isArray(stub)) return [{ level: 'error', path: '', text: 'A stub is a JSON object.' }];
  for (const key of Object.keys(stub)) if (!STUB_KEYS.has(key)) problems.push({ level: 'warning', path: key, text: `"${key}" is not a WireMock stub field; it is ignored.` });
  checkRequest(stub.request, problems);
  checkResponse(stub.response, problems);
  if (stub.priority !== undefined && (!Number.isInteger(stub.priority) || stub.priority < 1)) problems.push({ level: 'error', path: 'priority', text: 'priority is a whole number, 1 being the highest.' });
  if ((stub.requiredScenarioState || stub.newScenarioState) && !stub.scenarioName) problems.push({ level: 'error', path: 'scenarioName', text: 'Scenario states need a scenarioName.' });
  return problems;
}

const OP_TEXT = {
  equalTo: 'is', contains: 'contains', doesNotContain: 'does not contain', matches: 'matches', doesNotMatch: 'does not match',
  absent: 'is absent', equalToJson: 'equals the JSON', matchesJsonPath: 'has', equalToXml: 'equals the XML', matchesXPath: 'has',
};

function matcherText(matcher) {
  if (!matcher || typeof matcher !== 'object') return 'matches';
  const op = Object.keys(matcher).find((k) => OPERATORS.has(k));
  if (!op) return 'has a matcher this checker does not know';
  if (op === 'absent') return 'is absent';
  const value = matcher[op];
  const shown = typeof value === 'object' ? (value.expression ?? JSON.stringify(value)) : String(value);
  const short = shown.length > 60 ? `${shown.slice(0, 57)}…` : shown;
  return `${OP_TEXT[op] ?? op} "${short}"${matcher.caseInsensitive ? ' (any case)' : ''}`;
}

export function explain(stub) {
  const r = stub.request ?? {};
  const s = stub.response ?? {};
  const method = r.method && r.method !== 'ANY' ? `${String(r.method).toUpperCase()} requests` : 'Requests with any method';
  const urlKey = URL_KEYS.find((k) => r[k] !== undefined);
  const where = {
    url: `to exactly ${r.url}`, urlPath: `to ${r.urlPath}`, urlPathTemplate: `to ${r.urlPathTemplate}`,
    urlPattern: `whose URL matches ${r.urlPattern}`, urlPathPattern: `whose path matches ${r.urlPathPattern}`,
  }[urlKey] ?? 'to any path';
  const conditions = [];
  const describeMap = (map, label) => Object.entries(map ?? {}).forEach(([k, m]) => conditions.push(`${label} ${k} ${matcherText(m)}`));
  describeMap(r.queryParameters, 'query');
  describeMap(r.headers, 'header');
  describeMap(r.cookies, 'cookie');
  describeMap(r.pathParameters, 'path parameter');
  (r.bodyPatterns ?? []).forEach((m) => conditions.push(`the body ${matcherText(m)}`));
  let answer;
  if (s.fault) answer = `drops the connection (${s.fault})`;
  else if (s.proxyBaseUrl) answer = `forwards it to ${s.proxyBaseUrl}`;
  else {
    const kind = s.jsonBody !== undefined ? 'a JSON body' : s.bodyFileName ? `the file ${s.bodyFileName}` : s.base64Body ? 'a binary body' : s.body ? 'a text body' : 'no body';
    answer = `answers ${s.status ?? 200} with ${kind}`;
  }
  const extras = [];
  if (s.fixedDelayMilliseconds) extras.push(`after ${s.fixedDelayMilliseconds} ms`);
  if (s.delayDistribution) extras.push('after a random delay');
  if ((s.transformers ?? []).includes('response-template')) extras.push('built from a template');
  if (stub.scenarioName) extras.push(`in scenario "${stub.scenarioName}"${stub.requiredScenarioState ? ` when it is "${stub.requiredScenarioState}"` : ''}${stub.newScenarioState ? `, then moves it to "${stub.newScenarioState}"` : ''}`);
  const when = conditions.length ? ` when ${conditions.join(' and ')}` : '';
  return `${method} ${where}${when}: ${answer}${extras.length ? `, ${extras.join(', ')}` : ''}.`;
}
