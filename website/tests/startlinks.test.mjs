import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PAGES } from '../site-src/pages.mjs';

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const starts = readFileSync(join(root, 'ui', 'src', 'model', 'starts.ts'), 'utf8');
const kinds = /export type StartKind =([^;]+);/.exec(starts)[1].match(/'([a-z]+)'/g).map((k) => k.slice(1, -1));

test('feature pages open the matching start screen in the app', () => {
  // Given
  const linked = PAGES.filter((p) => p.app);

  // When
  const wrong = linked.filter((p) => p.app !== 'new' && !kinds.includes(p.app.replace(/^new\//, '')));

  // Then
  assert.ok(linked.length >= 10);
  assert.deepEqual(wrong.map((p) => p.slug), []);
  assert.equal(PAGES.find((p) => p.slug === 'openapi-mock-server').app, 'new/openapi');
});
