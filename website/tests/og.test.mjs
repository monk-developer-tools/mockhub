import test from 'node:test';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { GROUPS, topicPath } from '../docs-src/docs.mjs';
import { PAGES } from '../site-src/pages.mjs';
import { ogImage, ogKey, ogPages } from '../site-src/og.mjs';
import { ogMeta } from '../site-src/render.mjs';

const website = join(dirname(fileURLToPath(import.meta.url)), '..');

test('every page of the site has a preview card', () => {
  // Given
  const site = ['/', '/tutorials/', '/docs/', ...GROUPS.map(topicPath), ...GROUPS.flatMap((g) => g.pages.map((p) => `/docs/${p.slug}/`)), ...PAGES.map((p) => `/${p.slug}/`)];

  // When
  const covered = ogPages().map((p) => p.path);

  // Then
  assert.deepEqual([...covered].sort(), [...site].sort());
});

test('every preview card has an image file; run scripts/build-og.mjs after adding a page', () => {
  // Given
  const pages = ogPages();

  // When
  const missing = pages.filter((p) => !existsSync(join(website, ogImage(p.path)))).map((p) => p.path);

  // Then
  assert.deepEqual(missing, []);
});

test('preview cards have unique names and a title', () => {
  // Given
  const pages = ogPages();

  // When
  const keys = new Set(pages.map((p) => ogKey(p.path)));

  // Then
  assert.equal(keys.size, pages.length);
  assert.ok(pages.every((p) => p.title && p.eyebrow && p.description));
});

test('page headers point at the page own preview image', () => {
  // Given
  const path = '/tools/jwt/';

  // When
  const meta = ogMeta(path, 'JWT decoder');

  // Then
  assert.match(meta, /og:image" content="https:\/\/mockhub\.devops-monk\.com\/assets\/img\/og\/tools-jwt\.jpg"/);
  assert.match(meta, /twitter:image/);
  assert.match(meta, /og:image:alt" content="JWT decoder"/);
});
