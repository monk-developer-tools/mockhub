import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { GROUPS, topicPath } from '../docs-src/docs.mjs';

const website = join(dirname(fileURLToPath(import.meta.url)), '..');
const built = (path) => readFileSync(join(website, path, 'index.html'), 'utf8');

test('every docs group has a page that lists its guides as cards; run scripts/build-site.mjs first', () => {
  for (const group of GROUPS) {
    // Given
    const path = topicPath(group);

    // When
    const html = built(path);

    // Then
    assert.match(html, new RegExp(`<h1>${group.title.replace(/[.*+?^${}()|[\]\\]/g, '\\$&').replace(/&/g, '&amp;')}</h1>`));
    for (const page of group.pages) assert.ok(html.includes(`<a class="doc-card" href="/docs/${page.slug}/">`), `${path} links ${page.slug}`);
  }
});

test('a guide links back to its group in the breadcrumb and the landing page links every group', () => {
  // Given
  const group = GROUPS[1];
  const page = group.pages[0];

  // When
  const guide = built(`docs/${page.slug}`);
  const landing = built('docs');

  // Then
  assert.ok(guide.includes(`<a href="${topicPath(group)}">`));
  for (const g of GROUPS) assert.ok(landing.includes(`<a href="${topicPath(g)}">`), `landing links ${g.id}`);
});
