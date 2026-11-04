import assert from 'node:assert/strict';
import { test } from 'node:test';
import { exportMappings, fitsForm, formToStub, keyHash, parseKey, samplePath, stubToForm } from '../assets/js/tools/instant.js';

test('reads and writes the private manage key in the page address', () => {
  // Given
  const key = { slug: 'm-abc234defg', token: 'a'.repeat(32) };

  // When
  const hash = keyHash(key);

  // Then
  assert.deepEqual(parseKey(hash), key);
  assert.equal(parseKey('#m-abc.short'), null);
  assert.equal(parseKey(''), null);
});

test('turns the form into a stub with a JSON body, a path template and templating', () => {
  // Given
  const form = { name: 'Order', method: 'GET', path: 'orders/{id}', status: '200', contentType: 'application/json', body: '{"id": "{{request.path.id}}"}', templating: true, delay: '250' };

  // When
  const stub = formToStub(form);

  // Then
  assert.deepEqual(stub, {
    name: 'Order',
    request: { method: 'GET', urlPathTemplate: '/orders/{id}' },
    response: { status: 200, headers: { 'Content-Type': 'application/json' }, body: '{"id": "{{request.path.id}}"}', fixedDelayMilliseconds: 250, transformers: ['response-template'] },
  });
  assert.equal(samplePath(stub), '/orders/42');
});

test('stores plain JSON as jsonBody and round-trips through the form', () => {
  // Given
  const form = { name: '', method: 'POST', path: '/payments', status: 201, contentType: 'application/json', body: '{"id": "p1"}', templating: false, delay: 0 };

  // When
  const stub = formToStub(form);
  const back = stubToForm({ id: 'x', ...stub });

  // Then
  assert.deepEqual(stub.response.jsonBody, { id: 'p1' });
  assert.equal(back.method, 'POST');
  assert.equal(back.path, '/payments');
  assert.equal(back.status, 201);
  assert.equal(JSON.parse(back.body).id, 'p1');
  assert.equal(fitsForm({ id: 'x', ...stub }), true);
});

test('sends stubs the form cannot show to the JSON editor', () => {
  // Then
  assert.equal(fitsForm({ request: { method: 'GET', urlPath: '/a', queryParameters: { q: { equalTo: '1' } } }, response: { status: 200 } }), false);
  assert.equal(fitsForm({ request: { urlPath: '/a' }, response: { status: 200, headers: { 'X-Id': '1' } } }), false);
  assert.equal(fitsForm({ request: { urlPath: '/a' }, response: { status: 200, transformers: ['mockhub-weighted'] } }), false);
});

test('exports stubs as WireMock mappings without their ids', () => {
  // Then
  assert.deepEqual(exportMappings([{ id: '1', request: { urlPath: '/a' }, response: { status: 200 } }]), { mappings: [{ request: { urlPath: '/a' }, response: { status: 200 } }] });
});
