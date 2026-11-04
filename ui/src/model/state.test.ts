import { editableText, parseStateEdit, propertySummary, validContextName } from './state';

describe('state helpers', () => {
  it('summarises properties in name order and counts the rest', () => {
    // Given
    const properties = { status: 'open', count: '2', owner: 'ada', zone: 'eu' };

    // When
    const summary = propertySummary(properties);

    // Then
    expect(summary).toBe('count: 2, owner: ada, status: open +1 more');
  });

  it('turns a context into editable JSON and back', () => {
    // Given
    const text = editableText({ name: 'cart-1', properties: { status: 'open' }, list: [{ sku: 'a' }], updateCount: 2, updatedAt: '' });

    // When
    const edit = parseStateEdit(text);

    // Then
    expect(edit).toEqual({ properties: { status: 'open' }, list: [{ sku: 'a' }] });
  });

  it('stores numbers and booleans as text and drops nulls', () => {
    // When
    const edit = parseStateEdit('{"properties": {"n": 3, "ok": true, "gone": null}, "list": [{"qty": 1}]}');

    // Then
    expect(edit).toEqual({ properties: { n: '3', ok: 'true' }, list: [{ qty: '1' }] });
  });

  it('explains what is wrong with an edit', () => {
    // Then
    expect(parseStateEdit('nope')).toHaveProperty('error');
    expect(parseStateEdit('[]')).toEqual({ error: 'Enter a JSON object with properties and list' });
    expect(parseStateEdit('{"properties": {"a": {"b": 1}}}')).toEqual({ error: 'properties.a must be text, a number or true/false' });
    expect(parseStateEdit('{"list": {}}')).toEqual({ error: 'list must be an array of objects' });
    expect(parseStateEdit('{"list": [{"a": []}]}')).toEqual({ error: 'list[0].a must be text, a number or true/false' });
  });

  it('checks context names', () => {
    // Then
    expect(validContextName('  ')).toBe('Give the context a name, e.g. cart-42');
    expect(validContextName('x'.repeat(201))).toBe('Keep the name under 200 characters');
    expect(validContextName('cart-1')).toBeNull();
  });
});
