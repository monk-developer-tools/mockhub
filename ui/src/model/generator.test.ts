import { fieldNameFor, isKnownType, wrapRecords } from './generator';

describe('generator model', () => {
  it('suggests unique field names', () => {
    expect(fieldNameFor('Internet.emailAddress', [])).toBe('email');
    expect(fieldNameFor('Internet.emailAddress', ['email', 'email2'])).toBe('email3');
  });

  it('knows the built-in types', () => {
    expect(isKnownType('sequence')).toBe(true);
    expect(isKnownType('Animal.name')).toBe(false);
  });

  it('wraps records in a key with a total when asked', () => {
    expect(JSON.parse(wrapRecords([{ id: 1 }], 'items'))).toEqual({ items: [{ id: 1 }], total: 1 });
    expect(JSON.parse(wrapRecords([{ id: 1 }], ' '))).toEqual([{ id: 1 }]);
  });
});
