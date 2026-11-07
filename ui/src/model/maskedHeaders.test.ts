import { COMMON_SECRET_HEADERS, parseHeaderNames, withCommonSecrets } from './maskedHeaders';

describe('parseHeaderNames', () => {
  it('reads names from lines or commas, lower-cases them and drops repeats', () => {
    // Given
    const text = 'Authorization\nX-Api-Key, authorization ,\n\n';

    // When
    const parsed = parseHeaderNames(text);

    // Then
    expect(parsed).toEqual({ names: ['authorization', 'x-api-key'], invalid: [] });
  });

  it('reports names that are not header names', () => {
    // Given
    // When / Then
    expect(parseHeaderNames('not a header\nx-ok').invalid).toEqual(['not a header']);
  });
});

describe('withCommonSecrets', () => {
  it('adds the usual secret headers after the ones already listed', () => {
    // Given
    // When
    const text = withCommonSecrets('x-tenant\nCookie');

    // Then
    expect(text.split('\n')).toEqual(['x-tenant', 'cookie', ...COMMON_SECRET_HEADERS.filter((h) => h !== 'cookie')]);
  });
});
