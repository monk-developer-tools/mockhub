import { describe, expect, it } from 'vitest';
import { moveItem, sameOrder } from './order';

describe('stub order', () => {
  it('moves an item down and up without touching the original list', () => {
    // Given
    const list = ['a', 'b', 'c', 'd'];

    // When
    const down = moveItem(list, 0, 2);
    const up = moveItem(list, 3, 1);

    // Then
    expect(down).toEqual(['b', 'c', 'a', 'd']);
    expect(up).toEqual(['a', 'd', 'b', 'c']);
    expect(list).toEqual(['a', 'b', 'c', 'd']);
  });

  it('keeps moves inside the list', () => {
    // When
    const past = moveItem(['a', 'b'], 0, 9);
    const before = moveItem(['a', 'b'], 1, -3);

    // Then
    expect(past).toEqual(['b', 'a']);
    expect(before).toEqual(['b', 'a']);
  });

  it('compares orders item by item', () => {
    // When / Then
    expect(sameOrder(['a', 'b'], ['a', 'b'])).toBe(true);
    expect(sameOrder(['a', 'b'], ['b', 'a'])).toBe(false);
  });
});
