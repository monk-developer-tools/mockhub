import assert from 'node:assert/strict';
import { test } from 'node:test';
import { parseHeaders, problem, toCurl } from '../assets/js/tools/http.js';
import { describe } from '../assets/js/tools/curl.js';

test('reads headers written one per line and skips lines without a name', () => {
  // Given
  const text = 'Content-Type: application/json\nX-Trace:  abc:def \n: nothing\nbroken line\n';

  // When
  const headers = parseHeaders(text);

  // Then
  assert.deepEqual(headers, { 'Content-Type': 'application/json', 'X-Trace': 'abc:def' });
});

test('builds a cURL command that the cURL to stub tool reads back', () => {
  // Given
  const request = { method: 'POST', url: 'https://mockhub.devops-monk.com/__public/bins/abc/hook?x=1', headers: { 'Content-Type': 'application/json', Host: 'x', 'X-Note': "it's" }, body: '{"ok":true}' };

  // When
  const command = toCurl(request);
  const back = describe(command);

  // Then
  assert.equal(back.method, 'POST');
  assert.equal(back.path, '/__public/bins/abc/hook');
  assert.deepEqual(back.query, [['x', '1']]);
  assert.deepEqual(back.headers.map((h) => [h.name, h.value]), [['Content-Type', 'application/json'], ['X-Note', "it's"]]);
  assert.equal(back.body, '{"ok":true}');
});

test('explains failures in words people can act on', () => {
  // Then
  assert.match(problem(0, null), /could not be reached/);
  assert.equal(problem(429, { error: 'Too many renders' }), 'Too many renders');
  assert.match(problem(404, null), /turned off/);
  assert.equal(problem(400, { error: 'Use at most 20 fields' }), 'Use at most 20 fields');
  assert.match(problem(502, null), /answered 502/);
});
