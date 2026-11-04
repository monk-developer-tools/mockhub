import assert from 'node:assert/strict';
import { test } from 'node:test';
import { CurlError, describe, toStub, tokenize } from '../assets/js/tools/curl.js';

test('splits a command like a shell, with quotes, escapes and line continuations', () => {
  // Given
  const command = `curl -X POST 'https://api.example.com/orders' \\
  -H "Content-Type: application/json" -d '{"name":"Ada"}'`;

  // When
  const tokens = tokenize(command);

  // Then
  assert.deepEqual(tokens, ['curl', '-X', 'POST', 'https://api.example.com/orders', '-H', 'Content-Type: application/json', '-d', '{"name":"Ada"}']);
});

test('reads the method, path, query, headers and body', () => {
  // Given
  const command = `curl 'https://api.example.com/orders?status=paid&page=2' -H 'Authorization: Bearer abc' -H 'User-Agent: x' --data-raw '{"total":42}'`;

  // When
  const info = describe(command);

  // Then
  assert.equal(info.method, 'POST');
  assert.equal(info.path, '/orders');
  assert.deepEqual(info.query, [['status', 'paid'], ['page', '2']]);
  assert.deepEqual(info.headers.map((h) => [h.name, h.suggested]), [['Authorization', true], ['User-Agent', false]]);
  assert.equal(info.body, '{"total":42}');
});

test('moves data into the query with -G and adds headers for --json and -u', () => {
  // When
  const get = describe(`curl -G https://x.test/search -d q=books -d page=2`);
  const json = describe(`curl --json '{"a":1}' -u ada:secret https://x.test/items`);

  // Then
  assert.equal(get.method, 'GET');
  assert.deepEqual(get.query, [['q', 'books'], ['page', '2']]);
  assert.equal(get.body, '');
  assert.equal(json.method, 'POST');
  assert.deepEqual(json.headers.map((h) => `${h.name}: ${h.value}`), ['Content-Type: application/json', 'Accept: application/json', `Authorization: Basic ${btoa('ada:secret')}`]);
});

test('builds a stub with the chosen header matchers, a JSON body matcher and a JSON answer', () => {
  // Given
  const info = describe(`curl -X PUT 'https://x.test/orders/7?dry=true' -H 'X-Tenant: acme' -H 'Accept: */*' -d '{"status":"PAID"}'`);

  // When
  const stub = toStub(info, { headers: ['X-Tenant'], status: 201, responseBody: '{"id":7}' });

  // Then
  assert.deepEqual(stub, {
    name: 'PUT /orders/7',
    request: {
      method: 'PUT',
      urlPath: '/orders/7',
      queryParameters: { dry: { equalTo: 'true' } },
      headers: { 'X-Tenant': { equalTo: 'acme' } },
      bodyPatterns: [{ equalToJson: '{"status":"PAID"}', ignoreExtraElements: true }],
    },
    response: { status: 201, jsonBody: { id: 7 }, headers: { 'Content-Type': 'application/json' } },
  });
});

test('says what is wrong with a command it cannot read', () => {
  // When / Then
  assert.throws(() => describe('wget https://x.test'), new CurlError('Paste a command that starts with curl.'));
  assert.throws(() => describe('curl -H "X: 1"'), new CurlError('The command has no URL.'));
  assert.throws(() => describe(`curl 'https://x.test`), new CurlError('A quote is not closed.'));
});
