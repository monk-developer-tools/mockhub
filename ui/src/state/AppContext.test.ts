import { atLeast, readSsoHash } from './AppContext';

describe('AppContext helpers', () => {
  it('reads the single sign-on result from the URL fragment', () => {
    // Then
    expect(readSsoHash('#sso_code=abc')).toEqual({ code: 'abc', error: undefined });
    expect(readSsoHash('#sso_error=Not+allowed')).toEqual({ code: undefined, error: 'Not allowed' });
    expect(readSsoHash('')).toEqual({ code: undefined, error: undefined });
  });

  it('orders roles from none to admin', () => {
    // Then
    expect(atLeast('owner', 'editor')).toBe(true);
    expect(atLeast('admin', 'owner')).toBe(true);
    expect(atLeast('viewer', 'editor')).toBe(false);
    expect(atLeast(undefined, 'viewer')).toBe(false);
  });
});
