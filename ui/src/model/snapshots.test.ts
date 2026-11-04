import type { SnapshotDiff } from '../api/types';
import { contentsLabel, diffHeadline } from './snapshots';

const diff = (over: Partial<SnapshotDiff>): SnapshotDiff => ({
  from: 'v1', to: 'Now', identical: false,
  counts: { added: 0, removed: 0, changed: 0, unchanged: 3 },
  stubs: [],
  runtime: { changed: false, before: null, after: null },
  apiKeyHeader: { changed: false, before: null, after: null },
  files: { added: [], removed: [], changed: [] },
  ...over,
});

describe('snapshot model', () => {
  it('sums up a comparison in words', () => {
    // Given
    const changes = diff({
      counts: { added: 2, removed: 1, changed: 2, unchanged: 0 },
      stubs: [
        { id: 'a', status: 'moved', name: '', method: 'GET', url: '/a', before: null, after: null },
        { id: 'b', status: 'changed', name: '', method: 'GET', url: '/b', before: null, after: null },
      ],
      files: { added: ['x.csv'], removed: [], changed: ['y.csv'] },
      runtime: { changed: true, before: null, after: null },
    });

    // When
    const words = diffHeadline(changes);

    // Then
    expect(words).toEqual(['2 stubs added', '1 stub changed', '1 stub moved in the matching order', '1 stub removed', '2 files different', 'runtime behaviour different']);
    expect(diffHeadline(diff({ identical: true }))).toEqual(['No differences']);
  });

  it('describes what a snapshot holds', () => {
    // Then
    expect(contentsLabel(1, 0, [])).toBe('1 stub');
    expect(contentsLabel(7, 2, ['customers', 'orders'])).toBe('7 stubs · 2 files · CRUD: customers, orders');
  });
});
