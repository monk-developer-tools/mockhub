import assert from 'node:assert/strict';
import { test } from 'node:test';
import { toCsv, toSql } from '../assets/js/tools/fakeformat.js';

const records = [
  { id: 1, name: 'Ada "A" Lovelace', active: true, address: { city: 'London, UK' } },
  { id: 2, name: "O'Brien", active: false, address: { city: 'Cork' }, tags: ['a', 'b'] },
];

test('writes CSV with nested fields as dotted columns and quotes only when needed', () => {
  // When
  const csv = toCsv(records);

  // Then
  assert.equal(csv, [
    'id,name,active,address.city,tags',
    '1,"Ada ""A"" Lovelace",true,"London, UK",',
    `2,O'Brien,false,Cork,"[""a"",""b""]"`,
  ].join('\n'));
});

test('writes SQL inserts with escaped strings, raw numbers and booleans, and NULL for gaps', () => {
  // When
  const sql = toSql(records, 'people');

  // Then
  assert.equal(sql, [
    `INSERT INTO "people" ("id", "name", "active", "address_city", "tags") VALUES (1, 'Ada "A" Lovelace', TRUE, 'London, UK', NULL);`,
    `INSERT INTO "people" ("id", "name", "active", "address_city", "tags") VALUES (2, 'O''Brien', FALSE, 'Cork', '["a","b"]');`,
  ].join('\n'));
});
