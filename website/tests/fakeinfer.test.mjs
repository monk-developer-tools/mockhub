import assert from 'node:assert/strict';
import { test } from 'node:test';
import { expressionFor, inferFields, SEPARATOR, splitRendered, templateWrapper } from '../assets/js/tools/fakeinfer.js';

test('imitates a sample record from its field names and values', () => {
  // Given
  const sample = {
    id: 17,
    firstName: 'Ada',
    last_name: 'Lovelace',
    email: 'ada@example.com',
    active: true,
    price: 12.5,
    quantity: 42,
    createdAt: '2026-01-04T10:15:00Z',
    birthday: '1815-12-10',
    address: { city: 'London', postcode: 'N1 9GU', geo: { lat: 51.5 } },
    tags: ['a', 'b'],
    bio: 'Wrote the first published algorithm for a machine',
    plan: 'pro',
  };

  // When
  const { fields, notes } = inferFields(sample);

  // Then
  assert.deepEqual(Object.fromEntries(fields.map((f) => [f.name, f.expression])), {
    id: 'sequence',
    firstName: 'Name.firstName',
    last_name: 'Name.lastName',
    email: 'Internet.emailAddress',
    active: 'Bool.bool',
    price: "Number.randomDouble '2','1','1000'",
    quantity: "Number.numberBetween '0','100'",
    createdAt: "TimeAndDate.past '365','DAYS'",
    birthday: "Date.birthday 'yyyy-MM-dd'",
    'address.city': 'Address.city',
    'address.postcode': 'Address.zipCode',
    'address.geo.lat': 'Address.latitude',
    bio: 'Lorem.sentence',
    plan: 'Lorem.word',
  });
  assert.deepEqual(notes, ["tags: lists aren't made from fields; use a template for them"]);
});

test('falls back on the value when the name says nothing', () => {
  // Given
  const values = [['ref', '3f2b8c1e-9d4a-4b6e-8f00-123456789abc'], ['contact', 'x@y.io'], ['home', 'https://a.example'], ['score', 7], ['ratio', 0.4], ['x', 'hello']];

  // When
  const expressions = values.map(([name, value]) => expressionFor(name, value));

  // Then
  assert.deepEqual(expressions, ['uuid', 'Internet.emailAddress', 'Internet.url', "Number.numberBetween '0','10'", "Number.randomDouble '2','1','1000'", 'Lorem.word']);
});

test('takes the first record of a list and refuses what it cannot imitate', () => {
  // Given
  const list = [{ name: 'Ada' }, { name: 'Alan' }];

  // When
  const fromList = inferFields(list);

  // Then
  assert.deepEqual(fromList.fields, [{ name: 'name', expression: 'Name.fullName' }]);
  assert.throws(() => inferFields('text'), /Paste one JSON object/);
  assert.throws(() => inferFields({ tags: [] }), /No fields to imitate/);
  const wide = Object.fromEntries(Array.from({ length: 22 }, (_, i) => [`f${i}`, i]));
  assert.equal(inferFields(wide).fields.length, 20);
  assert.equal(inferFields(wide).notes.length, 2);
});

test('wraps a template to render once per record and splits the output back into records', () => {
  // Given
  const template = '{"n": {{this}}, "name": "{{fake \'Name.firstName\'}}"}';

  // When
  const wrapped = templateWrapper(template, 3);
  const records = splitRendered(`${SEPARATOR}{"n": 1}${SEPARATOR}{"n": 2}`);

  // Then
  assert.equal(wrapped, `{{#each (range 1 3)}}${SEPARATOR}${template}{{/each}}`);
  assert.deepEqual(records, [{ n: 1 }, { n: 2 }]);
  assert.throws(() => splitRendered(`${SEPARATOR}{"n": 1}${SEPARATOR}{oops`), /^Error: Record 2 isn't valid JSON/);
});
