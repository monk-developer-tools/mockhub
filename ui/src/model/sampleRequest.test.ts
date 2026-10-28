import { emptyForm } from './stubForm';
import { sampleRequest, samplePath } from './sampleRequest';

describe('samplePath', () => {
  it('fills path template variables and equal query parameters', () => {
    // Given
    const form = {
      ...emptyForm(),
      urlMatch: 'urlPathTemplate' as const,
      url: '/users/{id}/orders/{orderId}',
      query: [
        { key: 'page', operator: 'equalTo' as const, value: '2', caseInsensitive: false },
        { key: 'debug', operator: 'absent' as const, value: '', caseInsensitive: false },
      ],
    };

    // When / Then
    expect(samplePath(form)).toBe('/users/1/orders/1?page=2');
  });

  it('turns regexes into a concrete example', () => {
    expect(samplePath({ ...emptyForm(), urlMatch: 'urlPathPattern', url: '^/files/[0-9]+/.*$' })).toBe('/files/1/sample');
  });

  it('uses the base path for any-URL stubs', () => {
    expect(samplePath({ ...emptyForm(), urlMatch: 'any' })).toBe('/');
  });
});

describe('sampleRequest', () => {
  it('sends the project API key when the viewer may see it', () => {
    // Given
    const form = { ...emptyForm(), url: '/ping' };

    // When
    const request = sampleRequest(form, { header: 'X-API-Key', value: 'k-123' });

    // Then
    expect(request.headers).toContainEqual(['X-API-Key', 'k-123']);
  });

  it('uses a placeholder when only admins may see the key', () => {
    // Given
    const form = { ...emptyForm(), url: '/ping' };

    // When
    const request = sampleRequest(form, { header: 'X-API-Key', set: true });

    // Then
    expect(request.headers).toContainEqual(['X-API-Key', '{{apiKey}}']);
  });
});
