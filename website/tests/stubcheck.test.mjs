import assert from 'node:assert/strict';
import { test } from 'node:test';
import { check, explain, parseStubs } from '../assets/js/tools/stubcheck.js';

test('accepts one stub, an array or a mappings file, and points at broken JSON', () => {
  // When
  const one = parseStubs('{"request":{},"response":{}}');
  const many = parseStubs('[{}, {}]');
  const file = parseStubs('{"mappings":[{}]}');
  const broken = parseStubs('{\n  "request": {\n    "method": "GET",\n  }\n}');

  // Then
  assert.equal(one.stubs.length, 1);
  assert.equal(many.stubs.length, 2);
  assert.equal(file.stubs.length, 1);
  assert.match(broken.error, /^Not valid JSON near line 4/);
});

test('finds nothing to report on a well-formed stub', () => {
  // Given
  const stub = {
    request: { method: 'GET', urlPathTemplate: '/orders/{id}', headers: { Accept: { contains: 'json' } } },
    response: { status: 200, jsonBody: { id: '{{request.path.id}}' }, transformers: ['response-template'] },
  };

  // When / Then
  assert.deepEqual(check(stub), []);
});

test('reports the mistakes people make most', () => {
  // Given
  const stub = {
    request: { method: 'GETT', url: '/orders/{id}', urlPath: '/orders', headers: { Accept: { equals: 'json' } }, bodyPatterns: { equalTo: 'x' } },
    response: { status: 700, body: 'a', jsonBody: {}, fault: 'TIMEOUT', headers: { 'X-Count': 3 } },
    requiredScenarioState: 'Started',
  };

  // When
  const texts = check(stub).map((p) => `${p.level} ${p.path}: ${p.text}`);

  // Then
  assert.deepEqual(texts, [
    'error request: Use one of url, urlPath, not several.',
    'error request.method: "GETT" is not an HTTP method.',
    'warning request.url: url matches literally. Use urlPathTemplate for /items/{id}.',
    'error request.headers.Accept: Unknown matcher "equals".',
    'error request.bodyPatterns: bodyPatterns is an array of matchers.',
    'error response: Use one of body, jsonBody, not several.',
    'error response.status: status is a whole number from 100 to 599.',
    'error response.fault: Unknown fault. Use one of EMPTY_RESPONSE, MALFORMED_RESPONSE_CHUNK, RANDOM_DATA_THEN_CLOSE, CONNECTION_RESET_BY_PEER.',
    'error response.headers.X-Count: A header value is a string or an array of strings.',
    'error scenarioName: Scenario states need a scenarioName.',
  ]);
});

test('warns when a template will not render', () => {
  // When
  const problems = check({ request: { method: 'GET', urlPath: '/x' }, response: { status: 200, body: 'Hi {{request.query.name}}' } });

  // Then
  assert.deepEqual(problems.map((p) => p.level), ['warning']);
  assert.match(problems[0].text, /response-template/);
});

test('explains a stub in plain English', () => {
  // Given
  const stub = {
    request: { method: 'POST', urlPath: '/payments', headers: { 'X-Tenant': { equalTo: 'acme' } }, bodyPatterns: [{ matchesJsonPath: '$.amount' }] },
    response: { status: 201, jsonBody: {}, fixedDelayMilliseconds: 500, transformers: ['response-template'] },
  };

  // When
  const text = explain(stub);

  // Then
  assert.equal(text, 'POST requests to /payments when header X-Tenant is "acme" and the body has "$.amount": answers 201 with a JSON body, after 500 ms, built from a template.');
});
