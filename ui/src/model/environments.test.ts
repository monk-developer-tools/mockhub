import type { Stub } from '../api/types';
import { fromStub, splitEnvironmentHeader, toStub, withEnvironmentHeader } from './stubForm';

describe('environments in the stub form', () => {
  it('reads the environment header as checkboxes and writes it back', () => {
    // Given
    const stub = {
      request: { method: 'POST', urlPath: '/charges', headers: { 'X-MockHub-Environment': { matches: 'qa|staging' }, 'X-Tenant': { equalTo: 'acme' } } },
      response: { status: 402 },
    } as unknown as Stub;

    // When
    const { form } = fromStub(stub);
    const back = toStub({ ...form, environments: ['qa'] }, stub);

    // Then
    expect(form.environments).toEqual(['qa', 'staging']);
    expect(form.headers.map((h) => h.key)).toEqual(['X-Tenant']);
    expect((back.request as Record<string, unknown>).headers).toEqual({ 'X-Tenant': { equalTo: 'acme' }, 'X-MockHub-Environment': { equalTo: 'qa' } });
  });

  it('leaves header matchers it cannot show as checkboxes alone', () => {
    // Given
    const odd = [{ key: 'X-MockHub-Environment', operator: 'contains' as const, value: 'qa', caseInsensitive: false }];

    // When / Then
    expect(splitEnvironmentHeader(odd)).toEqual({ headers: odd, environments: [] });
    expect(withEnvironmentHeader([], [])).toEqual([]);
    expect(withEnvironmentHeader([], ['dev', 'qa'])[0]).toMatchObject({ operator: 'matches', value: 'dev|qa' });
  });
});
