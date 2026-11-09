import { environmentChanges } from './snapshots';
import type { ProjectEnvironment, Stub } from '../api/types';
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

describe('environments when comparing', () => {
  it('names what was added, removed or changed without showing values', () => {
    // Given
    const before: ProjectEnvironment[] = [{ name: 'dev', variables: { bank: 'a', tenant: 't' } }, { name: 'old', variables: {} }];
    const after: ProjectEnvironment[] = [{ name: 'dev', variables: { bank: 'b', tenant: 't', extra: 'x' } }, { name: 'qa', variables: {} }];

    // When
    const lines = environmentChanges(before, after);

    // Then
    expect(lines).toEqual(['dev: bank, extra different', 'qa: added', 'old: removed']);
    expect(environmentChanges(null, after.slice(1))).toEqual(['qa: added']);
  });
});
