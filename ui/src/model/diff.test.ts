import { collapse, diffLines } from './diff';

describe('diffLines', () => {
  it('marks added and removed lines', () => {
    // When
    const diff = diffLines('a\nb\nc', 'a\nB\nc\nd');

    // Then
    expect(diff).toEqual([
      { kind: 'same', text: 'a' },
      { kind: 'remove', text: 'b' },
      { kind: 'add', text: 'B' },
      { kind: 'same', text: 'c' },
      { kind: 'add', text: 'd' },
    ]);
  });

  it('treats a missing side as all added or all removed', () => {
    // Then
    expect(diffLines('', 'x\ny').every((l) => l.kind === 'add')).toBe(true);
    expect(diffLines('x', '').every((l) => l.kind === 'remove')).toBe(true);
  });

  it('collapses long unchanged stretches', () => {
    // Given
    const before = Array.from({ length: 20 }, (_, i) => `l${i}`).join('\n');
    const after = before.replace('l10', 'changed');

    // When
    const collapsed = collapse(diffLines(before, after), 2);

    // Then
    expect(collapsed[0]).toEqual({ kind: 'gap', count: 8 });
    expect(collapsed.filter((l) => l.kind === 'add')).toHaveLength(1);
    expect(collapsed[collapsed.length - 1]).toEqual({ kind: 'gap', count: 7 });
  });
});
