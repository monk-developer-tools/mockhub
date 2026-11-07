import assert from 'node:assert/strict';
import { test } from 'node:test';
import { cell, columnsOf, csvPreview, orderRoutes, parseJsonDoc, planRoutes, recordBody, routeLines, routesFromStubs } from '../assets/js/tools/dataapi.js';

const messageOf = (fn) => {
  try {
    fn();
    return '';
  } catch (e) {
    return e.message;
  }
};

test('plans a route per key: CRUD for lists of records, GET for everything else', () => {
  // Given
  const db = { posts: [{ id: 1 }, { id: 2 }], comments: [], profile: { name: 'typicode' }, tags: ['a'], 'Bad Key': [] };

  // When
  const plan = planRoutes(db);
  const list = planRoutes([{ id: 1 }], 'books');

  // Then
  assert.deepEqual(plan.routes, [
    { name: 'posts', kind: 'crud', count: 2 },
    { name: 'comments', kind: 'crud', count: 0 },
    { name: 'profile', kind: 'get' },
    { name: 'tags', kind: 'get' },
  ]);
  assert.deepEqual(plan.skipped, ['Bad Key: route names are lowercase letters, digits and dashes, like posts or order-items']);
  assert.deepEqual(list.routes, [{ name: 'books', kind: 'crud', count: 1 }]);
});

test('refuses JSON the server would refuse, with the same reasons', () => {
  // Given
  const many = { items: Array.from({ length: 101 }, (_, n) => ({ n })) };
  const wide = Object.fromEntries(Array.from({ length: 21 }, (_, n) => [`k${n}`, 1]));

  // When
  const messages = [() => parseJsonDoc(''), () => parseJsonDoc('{nope'), () => planRoutes(42), () => planRoutes({}), () => planRoutes(many), () => planRoutes(wide), () => planRoutes([1, 2])].map(messageOf);

  // Then
  assert.equal(messages[0], 'Paste some JSON first.');
  assert.match(messages[1], /^That isn't valid JSON: /);
  assert.match(messages[2], /keys become routes/);
  assert.match(messages[3], /keys become routes/);
  assert.equal(messages[4], 'Start with up to 100 records in all.');
  assert.equal(messages[5], 'Keep it to 20 top-level keys; each becomes a route.');
  assert.equal(messages[6], 'A top-level list must hold JSON objects, one per record.');
});

test('lists the endpoints of a route and reads routes back from a mock', () => {
  // Given
  const stubs = [
    { response: { transformers: ['mockhub-crud'], transformerParameters: { resource: 'posts' } } },
    { name: 'GET /profile', request: { method: 'GET', urlPath: '/profile' }, response: { status: 200 } },
    { request: { method: 'POST', urlPath: '/hook' }, response: { status: 202 } },
  ];

  // When
  const routes = routesFromStubs(stubs);

  // Then
  assert.deepEqual(routes, [{ name: 'posts', kind: 'crud' }, { name: 'profile', kind: 'get' }]);
  assert.deepEqual(routeLines(routes[1]), [['GET', '/profile']]);
  assert.equal(routeLines(routes[0]).map(([m, p]) => `${m} ${p}`).join(', '), 'GET /posts, POST /posts, GET /posts/{id}, PUT /posts/{id}, PATCH /posts/{id}, DELETE /posts/{id}');
});

test('previews a CSV as columns and the first rows, and shows records as table cells', () => {
  // Given
  const csv = 'id,name,price\n1,Pen,1.5\n2,Ink,"3,25"\n3,Pad,4';

  // When
  const preview = csvPreview(csv, 2);

  // Then
  assert.deepEqual(preview.columns, ['id', 'name', 'price']);
  assert.equal(preview.rows.length, 2);
  assert.equal(preview.total, 3);
  assert.equal(preview.records[1].name, 'Ink');
  assert.deepEqual(columnsOf([{ name: 'a', id: 1 }, { extra: true }]), ['id', 'name', 'extra']);
  assert.deepEqual([cell(null), cell(3), cell({ a: 1 }), cell('x')], ['', '3', '{"a":1}', 'x']);
  assert.deepEqual(recordBody('{"title": "Hello"}'), { title: 'Hello' });
  assert.equal(messageOf(() => recordBody('[1]')), 'A record is one JSON object, like {"title": "Hello"}.');
});

test('puts routes back in the order of the pasted document, unknown ones last', () => {
  // Given
  const fromServer = [{ name: 'profile', kind: 'get' }, { name: 'comments', kind: 'crud' }, { name: 'extra', kind: 'get' }, { name: 'posts', kind: 'crud' }];

  // When
  const ordered = orderRoutes(fromServer, ['posts', 'comments', 'profile']);
  const untouched = orderRoutes(fromServer, []);

  // Then
  assert.deepEqual(ordered.map((r) => r.name), ['posts', 'comments', 'profile', 'extra']);
  assert.deepEqual(untouched.map((r) => r.name), ['profile', 'comments', 'extra', 'posts']);
});
