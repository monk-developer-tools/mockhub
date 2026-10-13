import { fuzzyFilter, fuzzyScore } from './fuzzy';

describe('fuzzy search', () => {
  it('prefers direct substring matches over scattered ones', () => {
    expect(fuzzyScore('pets', 'GET /pets/{id}')).toBeGreaterThan(fuzzyScore('pets', 'Purchase entry test set'));
  });

  it('matches characters in order and rejects missing ones', () => {
    expect(fuzzyScore('gpi', 'Get pet by id')).toBeGreaterThan(0);
    expect(fuzzyScore('xyz', 'Get pet by id')).toBe(0);
  });

  it('filters and ranks a list', () => {
    // Given
    const items = ['Server', 'Petstore — REST basics', 'Projects', 'Examples'];

    // When
    const result = fuzzyFilter(items, 'pro', (s) => s);

    // Then
    expect(result[0]).toBe('Projects');
  });
});
