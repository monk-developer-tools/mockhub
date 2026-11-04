import { describe, expect, it } from 'vitest';
import { dynamicValues, searchDynamicValues } from './dynamicValues';

describe('dynamic values', () => {
  it('uses the request in responses and the original request plus response in callouts', () => {
    // When
    const response = dynamicValues('response');
    const callout = dynamicValues('callout');

    // Then
    expect(response.map((g) => g.id)).not.toContain('response');
    expect(callout.map((g) => g.id)).toContain('response');
    expect(response[0].items.every((i) => !i.expression.includes('originalRequest'))).toBe(true);
    expect(callout[0].items.find((i) => i.label === 'Header')?.expression).toBe('{{originalRequest.headers.X-Request-Id}}');
  });

  it('offers every category from the Beeceptor-style picker with distinct labels', () => {
    // When
    const groups = dynamicValues('callout');

    // Then
    expect(groups.map((g) => g.name)).toEqual([
      'Request components', 'Response components', 'Control flow', 'Data manipulation', 'Stateful helpers', 'Comparison operators',
      'String operators', 'Arithmetic operators', 'Date operators', 'Array helpers', 'JWT & OAuth', 'Random values', 'Faker',
    ]);
    for (const g of groups) expect(new Set(g.items.map((i) => i.label)).size).toBe(g.items.length);
  });

  it('offers validation only in responses, where a failed rule answers 400', () => {
    // When
    const response = dynamicValues('response').map((g) => g.name);
    const callout = dynamicValues('callout').map((g) => g.name);

    // Then
    expect(response).toContain('Validation');
    expect(callout).not.toContain('Validation');
  });

  it('fills in the request variable everywhere', () => {
    // When
    const expressions = [...dynamicValues('response'), ...dynamicValues('callout')].flatMap((g) => g.items.map((i) => i.expression));

    // Then
    expect(expressions.filter((e) => e.includes('$R.'))).toEqual([]);
    expect(expressions).toContain('{{jwtDecode originalRequest.headers.Authorization \'sub\'}}');
  });

  it('searches across group names, labels and expressions with every word', () => {
    // Given
    const groups = dynamicValues('response');

    // When
    const uuid = searchDynamicValues(groups, 'uuid');
    const dateFormat = searchDynamicValues(groups, 'date format');
    const none = searchDynamicValues(groups, 'zzz-nothing');

    // Then
    expect(uuid.flatMap((g) => g.items.map((i) => i.expression))).toContain("{{randomValue type='UUID'}}");
    expect(dateFormat.every((g) => g.name === 'Date operators')).toBe(true);
    expect(none).toEqual([]);
    expect(searchDynamicValues(groups, '  ')).toBe(groups);
  });
});
