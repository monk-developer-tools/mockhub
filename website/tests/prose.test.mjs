import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { PAGES } from '../site-src/pages.mjs';
import { changelogSections, faqSections, inline, longDate, parseChangelog } from '../site-src/prose.mjs';

test('reads the changelog into dated releases, newest first', () => {
  // Given
  const markdown = '# Changelog\n\nIntro.\n\n## 2026-11-08\n\n- Fixed `x` < y.\n- Second.\n\n## 2026-11-07\n\n- Older.\n\n## 2026-11-01\n';

  // When
  const releases = parseChangelog(markdown);
  const sections = changelogSections(markdown);

  // Then
  assert.deepEqual(releases.map((r) => [r.date, r.items.length]), [['2026-11-08', 2], ['2026-11-07', 1]]);
  assert.equal(sections[0].title, '8 November 2026');
  assert.equal(sections[0].html, '<ul><li>Fixed <code>x</code> &lt; y.</li><li>Second.</li></ul>');
  assert.equal(longDate('2026-10-13'), '13 October 2026');
});

test('the real changelog is dated, newest first, and every release has entries', () => {
  // Given
  const markdown = readFileSync(new URL('../../CHANGELOG.md', import.meta.url), 'utf8');

  // When
  const releases = parseChangelog(markdown);

  // Then
  assert.ok(releases.length > 5);
  const dates = releases.map((r) => r.date);
  assert.deepEqual(dates, [...dates].sort().reverse());
  assert.equal((markdown.match(/^## /gm) ?? []).length, releases.length);
});

test('the FAQ page gathers every page\'s questions once each, with a link back', () => {
  // Given
  const pages = [
    { slug: 'a', nav: ['Page A'], faq: [['Is it free?', 'Yes.'], ['Why <b>?', 'Because.']] },
    { slug: 'tools/b', nav: ['Page B'], faq: [['is it free? ', 'Again.'], ['Other?', 'No.']] },
    { slug: 'c', nav: ['Page C'], faq: [['Is it free?', 'Dup.']] },
    { slug: 'd', nav: ['Page D'] },
  ];

  // When
  const sections = faqSections(pages);

  // Then
  assert.deepEqual(sections.map((s) => [s.id, s.count]), [['a', 2], ['tools-b', 1]]);
  assert.match(sections[0].html, /Why &lt;b&gt;\?/);
  assert.match(sections[1].html, /href="\/tools\/b\/"/);
  assert.equal(inline('a `b`'), 'a <code>b</code>');
  const faq = PAGES.find((p) => p.slug === 'faq');
  assert.ok(faq.sections.length > 10);
});
