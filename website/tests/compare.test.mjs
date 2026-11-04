import test from 'node:test';
import assert from 'node:assert/strict';
import { PAGES } from '../site-src/pages.mjs';
import { renderFeature } from '../site-src/render.mjs';

const comparisons = PAGES.filter((p) => p.kind === 'compare');
const STATES = new Set(['', 'yes', 'part', 'no']);

test('every comparison page is dated and cites its sources', () => {
  // Given
  const pages = comparisons;

  // When
  const missing = pages.filter((p) => !p.compare.checked || p.compare.sources.length === 0 || p.compare.sources.some(([, href]) => !href.startsWith('https://')));

  // Then
  assert.equal(pages.length, 3);
  assert.deepEqual(missing.map((p) => p.slug), []);
});

test('every comparison row has one valid cell per column', () => {
  // Given
  const rows = comparisons.flatMap((p) => p.compare.groups.flatMap(([, list]) => list.map((row) => ({ page: p, row }))));

  // When
  const broken = rows.filter(({ page, row: [, ...cells] }) => cells.length !== page.compare.columns.length || cells.some(([state]) => !STATES.has(state)));

  // Then
  assert.deepEqual(broken.map(({ row }) => row[0]), []);
});

test('a comparison page renders the table, the check date and the sources', () => {
  // Given
  const page = comparisons.find((p) => p.slug === 'vs-wiremock');
  const ctx = { header: '', footer: '', icon: () => '' };

  // When
  const html = renderFeature(page, ctx);

  // Then
  assert.match(html, /<table class="cmp/);
  assert.match(html, /Checked on 4 November 2026/);
  assert.match(html, /href="https:\/\/wiremock\.org\/docs\/"/);
  assert.match(html, /<span class="sr-only">Yes\. <\/span>/);
});
