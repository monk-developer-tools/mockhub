import type { Connection } from '../api/types';
import { calloutExample, connectionForm, connectionInput, connectionProblem, describeTest, slugKey, withType } from './connections';

const bank: Connection = {
  key: 'bank', name: 'Bank API', type: 'oauth2', tokenUrl: 'https://login.bank.test/token', clientId: 'mockhub', scope: 'accounts',
  audience: '', clientAuth: 'body', headerName: 'Authorization', headerPrefix: 'Bearer ', hasSecret: true,
  createdAt: '2026-11-04T10:00:00Z', updatedAt: '2026-11-04T10:00:00Z',
};

describe('connections', () => {
  it('asks for the key, token URL, client id and secret of a new OAuth connection', () => {
    // Given
    const empty = connectionForm();

    // When
    const problems = [
      connectionProblem({ ...empty, key: 'Bank API' }, false),
      connectionProblem({ ...empty, key: 'bank', tokenUrl: 'login.bank.test' }, false),
      connectionProblem({ ...empty, key: 'bank', tokenUrl: 'https://login.bank.test/token' }, false),
      connectionProblem({ ...empty, key: 'bank', tokenUrl: 'https://login.bank.test/token', clientId: 'id' }, false),
      connectionProblem({ ...empty, key: 'bank', tokenUrl: 'https://login.bank.test/token', clientId: 'id', secret: 's' }, false),
    ];

    // Then
    expect(problems).toEqual([
      expect.stringMatching(/^The key must be/),
      expect.stringMatching(/^The token URL/),
      'Enter the client id',
      'Enter the client secret',
      null,
    ]);
  });

  it('keeps the stored secret when an edit leaves the secret empty', () => {
    // Given
    const form = connectionForm(bank);

    // When
    const input = connectionInput(form, true);
    const problem = connectionProblem(form, true);

    // Then
    expect(problem).toBeNull();
    expect(input).not.toHaveProperty('secret');
    expect(input).not.toHaveProperty('key');
    expect(input).toMatchObject({ tokenUrl: 'https://login.bank.test/token', clientAuth: 'body', scope: 'accounts' });
  });

  it('switches to an API key header without OAuth settings', () => {
    // Given
    const form = { ...connectionForm(), key: 'maps', secret: 'k-123' };

    // When
    const header = withType(form, 'header');
    const input = connectionInput(header, false);

    // Then
    expect(header).toMatchObject({ headerName: 'X-Api-Key', headerPrefix: '' });
    expect(input).toEqual({ key: 'maps', name: 'maps', type: 'header', headerName: 'X-Api-Key', headerPrefix: '', secret: 'k-123' });
  });

  it('keeps a header name the user typed when the type changes', () => {
    // Given
    const form = { ...connectionForm(), headerName: 'X-Token' };

    // When
    const header = withType(form, 'header');

    // Then
    expect(header.headerName).toBe('X-Token');
  });

  it('makes keys from names and describes a test token', () => {
    // When
    const key = slugKey('  Bank API (sandbox) ');
    const text = describeTest({ ok: true, headerName: 'Authorization', preview: 'Bearer eyJh…x9Q2', expiresIn: 3600, scope: 'accounts' });

    // Then
    expect(key).toBe('bank-api-sandbox');
    expect(text).toBe('Authorization: Bearer eyJh…x9Q2 · expires in 60 minutes · scope accounts');
    expect(JSON.parse(calloutExample('bank')).parameters.connection).toBe('bank');
  });
});
