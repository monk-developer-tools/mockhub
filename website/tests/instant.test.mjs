import assert from 'node:assert/strict';
import { test } from 'node:test';
import { displayPath, exportMappings, fitsForm, formToStub, graphqlRequest, isGraphql, keyHash, parseCsv, parseKey, parseRecords, resourceName, samplePath, specFormat, specFormatFromUrl, specStart, stubToForm } from '../assets/js/tools/instant.js';

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

test('reads CSV with quoted fields into typed records', () => {
  // Given
  const csv = 'id,name,price,active\r\n1,"Smith, Ada",9.50,true\n2,"Say ""hi""",10,false\n\n';

  // When
  const records = parseCsv(csv);

  // Then
  assert.deepEqual(records, [
    { id: 1, name: 'Smith, Ada', price: 9.5, active: true },
    { id: 2, name: 'Say "hi"', price: 10, active: false },
  ]);
  assert.throws(() => parseCsv('id,name'), /header row and at least one record/);
  assert.throws(() => parseCsv('id,\n1,2'), /needs a name/);
});

test('reads records from a JSON list, a wrapped list or CSV', () => {
  // When
  const list = parseRecords('[{"id": 1}]');
  const wrapped = parseRecords('{"users": [{"id": 2}], "total": 1}');
  const csv = parseRecords('id\n3');

  // Then
  assert.deepEqual(list, [{ id: 1 }]);
  assert.deepEqual(wrapped, [{ id: 2 }]);
  assert.deepEqual(csv, [{ id: 3 }]);
  assert.throws(() => parseRecords('[1, 2]'), /object with named fields/);
  assert.throws(() => parseRecords('{"a": 1}'), /JSON list of objects/);
  assert.throws(() => parseRecords('{oops'), /not valid/);
});

test('names the resource after the file and tells a WSDL from an OpenAPI spec', () => {
  // Then
  assert.equal(resourceName('Order Items.csv'), 'order-items');
  assert.equal(resourceName('__.json'), 'items');
  assert.equal(specFormat('  <?xml version="1.0"?><definitions/>'), 'wsdl');
  assert.equal(specFormat('openapi: 3.0.0'), 'openapi');
});

test('shows a CRUD stub as its resource path', () => {
  // Given
  const crud = { request: { method: 'ANY', urlPathPattern: '/users(/[^/]+)?/?' },
    response: { status: 200, transformers: ['mockhub-crud'], transformerParameters: { resource: 'users' } } };

  // Then
  assert.equal(displayPath(crud), '/users[/{id}]');
  assert.equal(samplePath(crud), '/users');
  assert.equal(fitsForm(crud), false);
});


test('starts from pasted text or a URL, never both, and guesses the format from each', () => {
  // Given
  const openapi = 'openapi: 3.0.0';
  const wsdl = '<definitions/>';

  // When
  const fromText = [specStart('spec', openapi, ''), specStart('spec', wsdl, ' '), specStart('graphql', 'type Query { a: String }', '')];
  const fromUrl = [specStart('spec', '', 'https://api.example.com/openapi.yaml'), specStart('spec', '', 'https://svc.example.com/calc?wsdl'), specStart('graphql', '', 'https://example.com/schema.graphql')];

  // Then
  assert.deepEqual(fromText.map((b) => b.from), ['openapi', 'wsdl', 'graphql']);
  assert.equal(fromText[0].text, openapi);
  assert.deepEqual(fromUrl, [
    { from: 'openapi', url: 'https://api.example.com/openapi.yaml' },
    { from: 'wsdl', url: 'https://svc.example.com/calc?wsdl' },
    { from: 'graphql', url: 'https://example.com/schema.graphql' },
  ]);
  assert.equal(specFormatFromUrl('https://x.example.com/Service.WSDL'), 'wsdl');
  assert.equal(specFormatFromUrl('https://x.example.com/wsdl-guide/openapi.json'), 'openapi');
});

test('refuses a start with nothing, both, or a URL that is not http', () => {
  // Given
  const cases = [['spec', '', ''], ['spec', 'openapi: 3.0.0', 'https://a.example.com/x'], ['graphql', '', 'ftp://a.example.com/x'], ['graphql', '  ', '']];

  // When
  const messages = cases.map(([from, text, url]) => {
    try {
      specStart(from, text, url);
      return '';
    } catch (e) {
      return e.message;
    }
  });

  // Then
  assert.deepEqual(messages, [
    'Upload, paste or link an OpenAPI or WSDL file first.',
    'Use the pasted text or the URL, not both. Clear one of them.',
    'The URL must start with http:// or https://',
    'Upload, paste or link a GraphQL schema first.',
  ]);
});

test('builds a GraphQL request from a query and optional JSON variables', () => {
  // Given
  const query = '{ launch(id: $id) { mission } }';

  // When
  const plain = graphqlRequest(query, '');
  const withVariables = graphqlRequest(query, '{"id": "7"}');
  const errors = [['', ''], [query, '{nope'], [query, '[1]']].map(([q, v]) => {
    try {
      graphqlRequest(q, v);
      return '';
    } catch (e) {
      return e.message;
    }
  });

  // Then
  assert.deepEqual(plain, { query, variables: {} });
  assert.deepEqual(withVariables.variables, { id: '7' });
  assert.deepEqual(errors, ['Write a query first, or pick an example.', 'The variables are not valid JSON.', 'The variables must be a JSON object, like {"id": "1"}.']);
  assert.equal(isGraphql({ response: { transformers: ['mockhub-graphql'] } }), true);
  assert.equal(isGraphql({ response: { transformers: ['response-template'] } }), false);
});
